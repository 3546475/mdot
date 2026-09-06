package com.mdot.app.core.repository

import com.mdot.app.core.database.CompAdjustmentDao
import com.mdot.app.core.database.CompAdjustmentEntity
import com.mdot.app.core.database.DailyRecordEntity
import com.mdot.app.core.database.RecordDao
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.domain.model.CompAdjustment
import com.mdot.app.domain.model.DailyRecord
import com.mdot.app.domain.model.LeaveDraft
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.OtDraft
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.TierSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 记录仓库：Room 的类型化门面（05 文档 §6.1），写后 touch 同步锚点 */
@Singleton
class RecordRepository @Inject constructor(
    private val recordDao: RecordDao,
    private val adjustDao: CompAdjustmentDao,
    private val settings: SettingsDataSource,
) {

    fun observeByDate(date: LocalDate): Flow<List<DailyRecord>> =
        settings.salaryFlow.flatMapLatest { salary ->
            recordDao.observeByDate(date, salary.workSystem.name).map { list -> list.map { it.toDomain() } }
        }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyRecord>> =
        settings.salaryFlow.flatMapLatest { salary ->
            recordDao.observeRange(from, to, salary.workSystem.name).map { list -> list.map { it.toDomain() } }
        }

    suspend fun getRange(from: LocalDate, to: LocalDate): List<DailyRecord> {
        val workSystem = settings.salaryFlow.first().workSystem.name
        return recordDao.getRange(from, to, workSystem).map { it.toDomain() }
    }

    suspend fun saveOt(draft: OtDraft): AppResult<Unit> {
        if (draft.date.isAfter(LocalDate.now())) return Failure(AppError.FutureDate)
        if (draft.durationMinutes !in 1..MAX_MINUTES) return Failure(AppError.InvalidDuration)
        if (draft.toCompMinutes !in 0..draft.durationMinutes) return Failure(AppError.InvalidComp)
        val workSystem = settings.salaryFlow.first().workSystem.name
        val existing = recordDao.getByDateAndType(draft.date, RecordType.OT.name, workSystem)
        val now = System.currentTimeMillis()
        val entity = DailyRecordEntity(
            id = existing?.id ?: 0,
            date = draft.date,
            type = RecordType.OT.name,
            workSystem = workSystem,
            shiftId = draft.shiftId,
            shiftName = draft.shiftName,
            durationMinutes = draft.durationMinutes,
            tier = draft.tier.name,
            tierSource = draft.tierSource.name,
            toCompMinutes = draft.toCompMinutes,
            leaveType = null,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return try {
            if (existing == null) recordDao.insert(entity) else recordDao.update(entity)
            settings.touch()
            Success(Unit)
        } catch (e: Exception) {
            Failure(AppError.Storage(e.message ?: "保存失败"))
        }
    }

    suspend fun saveLeave(draft: LeaveDraft): AppResult<Unit> {
        if (draft.date.isAfter(LocalDate.now())) return Failure(AppError.FutureDate)
        if (draft.durationMinutes !in 1..MAX_MINUTES) return Failure(AppError.InvalidDuration)
        val workSystem = settings.salaryFlow.first().workSystem.name
        val existing = recordDao.getByDateAndType(draft.date, RecordType.LEAVE.name, workSystem)
        val now = System.currentTimeMillis()
        val entity = DailyRecordEntity(
            id = existing?.id ?: 0,
            date = draft.date,
            type = RecordType.LEAVE.name,
            workSystem = workSystem,
            shiftId = draft.shiftId,
            shiftName = draft.shiftName,
            durationMinutes = draft.durationMinutes,
            tier = null,
            tierSource = null,
            toCompMinutes = 0,
            leaveType = draft.leaveType.name,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return try {
            if (existing == null) recordDao.insert(entity) else recordDao.update(entity)
            settings.touch()
            Success(Unit)
        } catch (e: Exception) {
            Failure(AppError.Storage(e.message ?: "保存失败"))
        }
    }

    suspend fun delete(date: LocalDate, type: RecordType): AppResult<Unit> = try {
        val workSystem = settings.salaryFlow.first().workSystem.name
        recordDao.delete(date, type.name, workSystem)
        settings.touch()
        Success(Unit)
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "删除失败"))
    }

    /** 调休余额（分钟，可负） */
    fun observeCompBalance(): Flow<Int> = recordDao.observeCompBalance()

    fun observeAdjustments(): Flow<List<CompAdjustment>> =
        adjustDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun adjustComp(date: LocalDate, deltaMinutes: Int, note: String?): AppResult<Unit> = try {
        adjustDao.insert(
            CompAdjustmentEntity(
                date = date,
                deltaMinutes = deltaMinutes,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = System.currentTimeMillis(),
            )
        )
        settings.touch()
        Success(Unit)
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "调整失败"))
    }

    suspend fun deleteAdjustment(id: Long): AppResult<Unit> = try {
        adjustDao.delete(id)
        settings.touch()
        Success(Unit)
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "删除失败"))
    }

    /** 切换到小时工制度时调用：清零转调休 + 删除所有调休手动调整记录 */
    suspend fun clearCompData() {
        recordDao.clearAllCompMinutes()
        adjustDao.deleteAll()
        settings.touch()
    }

    companion object {
        const val MAX_MINUTES = 1440
    }
}

fun DailyRecordEntity.toDomain() = DailyRecord(
    id = id,
    date = date,
    type = RecordType.valueOf(type),
    workSystem = com.mdot.app.domain.model.WorkSystem.valueOf(workSystem),
    shiftId = shiftId,
    shiftName = shiftName,
    durationMinutes = durationMinutes,
    tier = tier?.let { RateTier.valueOf(it) },
    tierSource = tierSource?.let { TierSource.valueOf(it) },
    toCompMinutes = toCompMinutes,
    leaveType = leaveType?.let { LeaveType.valueOf(it) },
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CompAdjustmentEntity.toDomain() = CompAdjustment(
    id = id, date = date, deltaMinutes = deltaMinutes, note = note, createdAt = createdAt,
)
