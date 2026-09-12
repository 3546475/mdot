package com.mdot.app.core.repository

import androidx.room.withTransaction
import com.mdot.app.core.database.AppDatabase
import com.mdot.app.core.database.SiteAdvanceDao
import com.mdot.app.core.database.SiteAdvanceEntity
import com.mdot.app.core.database.SiteAttendanceDao
import com.mdot.app.core.database.SiteAttendanceEntity
import com.mdot.app.core.database.SiteProjectDao
import com.mdot.app.core.database.SiteProjectEntity
import com.mdot.app.core.database.SiteSettlementDao
import com.mdot.app.core.database.SiteSettlementEntity
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.SiteAttendance
import com.mdot.app.domain.model.SitePieceWork
import com.mdot.app.domain.model.SiteProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工地记工仓库（12 文档；site_* 表门面）。写后 touch 同步锚点。
 * 结算 = 快照 + 事务回填 settlement_id 锁定；撤销 = 删单 + 解锁（快照留存可追溯，D6）。
 */
@Singleton
class SiteRepository @Inject constructor(
    private val db: AppDatabase,
    private val projectDao: SiteProjectDao,
    private val attDao: SiteAttendanceDao,
    private val advanceDao: SiteAdvanceDao,
    private val pieceDao: com.mdot.app.core.database.SitePieceWorkDao,
    private val settlementDao: SiteSettlementDao,
    private val settings: SettingsDataSource,
) {

    /** currentProjectId 并发互斥（切换制度与首页/统计数据流可能同时触发自动建项） */
    private val currentProjectMutex = Mutex()

    companion object {
        const val MAX_PROJECTS = 15
        const val MAX_DAY_MINUTES = 1440

        /** 新建项目默认点工标准：8 小时 = 1 个工 = 260 元（v0.6.2） */
        const val DEFAULT_BASE_MINUTES = 480
        const val DEFAULT_DAILY_RATE_CENTS = 26_000L
    }

    // ---- 项目 ----

    fun observeActiveProjects(): Flow<List<SiteProject>> =
        projectDao.observeActive().map { list -> list.map { it.toDomain() } }

    /** 已归档项目（管理页归档区展示） */
    fun observeArchivedProjects(): Flow<List<SiteProject>> =
        projectDao.observeArchived().map { list -> list.map { it.toDomain() } }

    /** 恢复归档项目；与现有项目（含归档）重名时自动加序号后缀，避免重名歧义 */
    suspend fun restoreProject(id: Long) {
        val entity = projectDao.getById(id) ?: return
        if (!entity.archived) return
        var name = entity.name
        var n = 2
        while (projectDao.getByName(name) != null) {
            name = "${entity.name} $n"
            n++
        }
        if (name == entity.name) projectDao.unarchive(id)
        else projectDao.update(entity.copy(name = name, archived = false))
        settings.touch()
    }

    /** 彻底删除已归档项目及其全部数据（出勤/包工/借支/结算单快照），不可恢复；仅允许对归档项目使用 */
    suspend fun purgeProject(id: Long) {
        val entity = projectDao.getById(id) ?: return
        if (!entity.archived) return
        attDao.deleteAllForProject(id)
        pieceDao.deleteAllForProject(id)
        advanceDao.deleteAllForProject(id)
        settlementDao.deleteAllForProject(id)
        projectDao.deleteById(id)
        settings.touch()
    }

    suspend fun getProject(id: Long): SiteProject? = projectDao.getById(id)?.toDomain()

    fun observeProject(id: Long): Flow<SiteProject?> =
        projectDao.observeById(id).map { it?.toDomain() }

    suspend fun currentProjectId(): Long = currentProjectMutex.withLock {
        // 指针有效且项目仍活跃 → 直接用；指针悬空（项目已删/已归档，历史 bug 遗留）→ 视同丢失走下方兑底（自愈）
        val saved = settings.salaryFlow.first().siteCurrentProjectId
        if (saved != 0L) {
            projectDao.getById(saved)?.takeIf { !it.archived }?.let { return it.id }
        }
        // 指针丢失但项目仍在（切换竞态/异常清零）：接管首个活跃项目，不重建
        projectDao.getAll().firstOrNull { !it.archived }?.let {
            setCurrentProject(it.id)
            return it.id
        }
        // 未初始化：建默认项目并持久化（D1-rev，竞品同款零上手成本）。
        // ⚠️ 不走 createProject：它有重名校验，若库里残留归档的同名项目会永久 Failure → 兜底全链失败 → 0；
        // 改为直接插入默认项目（重名自动加序号后缀），绕过面向用户输入的重名/上限校验
        val id = insertDefaultProject()
        if (id != 0L) setCurrentProject(id)
        id
    }

    /** 插入默认点工标准的新项目；重名自动「xxx 2」「xxx 3」…后缀（兜底/自愈专用，不受用户重名校验限制） */
    private suspend fun insertDefaultProject(): Long {
        val base = DEFAULT_PROJECT_NAME
        val name = if (projectDao.getByName(base) == null) base
        else generateSequence(2) { it + 1 }
            .map { "$base $it" }
            .first { projectDao.getByName(it) == null }
        val now = System.currentTimeMillis()
        return projectDao.insert(
            SiteProjectEntity(
                name = name, sort = projectDao.count(), createdAt = now, updatedAt = now,
                baseMinutes = DEFAULT_BASE_MINUTES, dailyRateCents = DEFAULT_DAILY_RATE_CENTS,
            )
        )
    }

    suspend fun setCurrentProject(id: Long) {
        val salary = settings.salaryFlow.first()
        settings.setSalary(salary.copy(siteCurrentProjectId = id))
    }

    suspend fun createProject(name: String): AppResult<Long> {
        if (name.isBlank()) return Failure(AppError.InvalidMessage("项目名不能为空"))
        if (projectDao.getByName(name.trim()) != null) return Failure(AppError.InvalidMessage("项目名称已存在"))
        if (projectDao.count() >= MAX_PROJECTS) return Failure(AppError.InvalidMessage("最多可创建 $MAX_PROJECTS 个项目"))
        val now = System.currentTimeMillis()
        val id = projectDao.insert(
            SiteProjectEntity(
                name = name.trim(), sort = projectDao.count(), createdAt = now, updatedAt = now,
                baseMinutes = DEFAULT_BASE_MINUTES, dailyRateCents = DEFAULT_DAILY_RATE_CENTS,
            )
        )
        settings.touch()
        return Success(id)
    }

    suspend fun updateProject(project: SiteProject): AppResult<Unit> {
        if (project.name.isBlank()) return Failure(AppError.InvalidMessage("项目名不能为空"))
        projectDao.getByName(project.name.trim())?.takeIf { it.id != project.id }?.let {
            return Failure(AppError.InvalidMessage("项目名称已存在"))
        }
        projectDao.update(
            SiteProjectEntity(
                id = project.id, name = project.name.trim(), sort = project.sort, archived = project.archived,
                baseMinutes = project.baseMinutes.coerceIn(60, 24 * 60),
                dailyRateCents = project.dailyRateCents.coerceAtLeast(0),
                otMode = project.otMode, otBaseMinutes = project.otBaseMinutes.coerceAtLeast(1),
                otHourlyCents = project.otHourlyCents.coerceAtLeast(0),
                note = project.note, createdAt = project.createdAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
        settings.touch()
        return Success(Unit)
    }

    /** 拖拽排序提交 */
    suspend fun reorderProjects(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { i, id ->
            projectDao.getAll().firstOrNull { it.id == id }?.let { p ->
                projectDao.update(p.copy(sort = i, updatedAt = System.currentTimeMillis()))
            }
        }
        settings.touch()
    }

    /** 有记录的项目只能归档（防误删钱账）；空项目直接删除；删/归档后无活跃项目时自动新建默认项目并选中（工地模式永无「无项目」态） */
    suspend fun archiveOrDeleteProject(id: Long): AppResult<Unit> {
        val hasData = attDao.countForProject(id) > 0 || advanceDao.countForProject(id) > 0
        if (hasData) {
            projectDao.archive(id)
        } else {
            projectDao.deleteById(id)
        }
        settings.touch()
        // 删/归档的是当前项目：回退到剩余第一个活跃项目；一个都不剩则自动新建默认项目并选中
        val salary = settings.salaryFlow.first()
        if (salary.siteCurrentProjectId == id) {
            val next = projectDao.getAll().firstOrNull { !it.archived }
            if (next != null) {
                settings.setSalary(salary.copy(siteCurrentProjectId = next.id))
            } else {
                // ⚠️ 必须先清零指针再兑底：currentProjectId() 开头 takeIf{it!=0L} 会短路返回
                // 已删/归档项目的旧指针，不清零永远走不到「无活跃项目 → 建默认项目」分支
                settings.setSalary(salary.copy(siteCurrentProjectId = 0L))
                currentProjectId()
            }
        }
        return Success(Unit)
    }

    // ---- 出勤 ----

    fun observeAttendance(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteAttendance>> =
        attDao.observeRange(projectId, from, to).map { list -> list.map { it.toDomain() } }

    /** 全项目区间出勤（统计页「项目工时」饼图聚合用） */
    fun observeAttendanceAllProjects(from: LocalDate, to: LocalDate): Flow<List<SiteAttendance>> =
        attDao.observeAllRange(from, to).map { list -> list.map { it.toDomain() } }

    /** 全项目区间包工/工量（我的页 SITE 模式年工钱聚合用） */
    fun observePieceAllProjects(from: LocalDate, to: LocalDate): Flow<List<SitePieceWork>> =
        pieceDao.observeAllRange(from, to).map { list -> list.map { it.toDomain() } }

    /** 全部项目（含归档；饼图项目取名用） */
    fun observeAllProjects(): Flow<List<SiteProject>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 保存出勤：按当日标准快照算金额（链 1）；REST=显式休息（工钱 0）；已结算记录拒绝覆盖 */
    suspend fun saveAttendance(draft: SiteAttendance): AppResult<Unit> {
        if (draft.settlementId != null) return Failure(AppError.InvalidMessage("该日已结算，请先撤销结算单"))
        if (draft.workMinutes !in 0..MAX_DAY_MINUTES || draft.otMinutes !in 0..MAX_DAY_MINUTES) {
            return Failure(AppError.InvalidDuration)
        }
        if (draft.dayStatus == "REST") {
            if (draft.workMinutes > 0 || draft.otMinutes > 0) return Failure(AppError.InvalidDuration)
        }
        val project = projectDao.getById(draft.projectId) ?: return Failure(AppError.InvalidMessage("项目不存在"))
        val std = SitePayCalculator.DayStandard(
            rateCents = draft.rateCents, baseMinutes = draft.baseMinutes,
            otMode = draft.otMode, otBaseMinutes = draft.otBaseMinutes, otHourlyCents = draft.otHourlyCents,
        )
        val workPay = SitePayCalculator.workPayCents(std, draft.workMinutes)
        val otPay = SitePayCalculator.otPayCents(std, draft.otMinutes)
        val now = System.currentTimeMillis()
        val existing = attDao.getByDate(draft.projectId, LocalDate.parse(draft.date))
        attDao.upsert(
            SiteAttendanceEntity(
                id = existing?.id ?: 0,
                projectId = draft.projectId,
                date = LocalDate.parse(draft.date),
                dayStatus = draft.dayStatus,
                halfOfDay = draft.halfOfDay,
                workMinutes = draft.workMinutes,
                otMinutes = draft.otMinutes,
                rateCents = draft.rateCents, baseMinutesSnapshot = draft.baseMinutes,
                otModeSnapshot = draft.otMode, otBaseMinutesSnapshot = draft.otBaseMinutes,
                otHourlyCentsSnapshot = draft.otHourlyCents,
                workPayCents = workPay, otPayCents = otPay,
                note = draft.note, photos = draft.photos,
                settlementId = existing?.settlementId,
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
        )
        settings.touch()
        return Success(Unit)
    }

    suspend fun deleteAttendance(id: Long): AppResult<Unit> {
        val deleted = attDao.deleteUnsettled(id)
        if (deleted == 0) return Failure(AppError.InvalidMessage("该记录已结算，请先撤销结算单"))
        settings.touch()
        return Success(Unit)
    }

    // ---- 借支 ----

    fun observeAdvances(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteAdvance>> =
        advanceDao.observeRange(projectId, from, to).map { list -> list.map { it.toDomain() } }

    suspend fun saveAdvance(
        projectId: Long, date: LocalDate, amountCents: Long,
        purpose: AdvancePurpose, note: String?, photos: String? = null,
    ): AppResult<Unit> {
        if (amountCents <= 0) return Failure(AppError.InvalidMessage("借支金额需大于 0"))
        advanceDao.insert(
            SiteAdvanceEntity(
                projectId = projectId, date = date, amountCents = amountCents,
                purpose = purpose.name, note = note?.trim()?.ifEmpty { null }, photos = photos,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
        settings.touch()
        return Success(Unit)
    }

    suspend fun deleteAdvance(id: Long): AppResult<Unit> {
        val deleted = advanceDao.deleteUnsettled(id)
        if (deleted == 0) return Failure(AppError.InvalidMessage("该借支已结算，请先撤销结算单"))
        settings.touch()
        return Success(Unit)
    }

    // ---- 包工/工量（Phase 2） ----

    fun observePieceWorks(projectId: Long, from: LocalDate, to: LocalDate):
        Flow<List<com.mdot.app.domain.model.SitePieceWork>> =
        pieceDao.observeRange(projectId, from, to).map { list -> list.map { it.toDomain() } }

    /** 保存包工：量价齐按 量×单价 HALF_UP 自动算钱，否则直填金额（D4-rev） */
    suspend fun savePieceWork(
        projectId: Long, date: LocalDate, itemName: String, unit: String,
        quantityMilli: Long, unitPriceCents: Long, directAmountCents: Long,
        note: String?, photos: String? = null,
    ): AppResult<Unit> {
        if (itemName.isBlank() && quantityMilli == 0L && directAmountCents <= 0L) {
            return Failure(AppError.InvalidMessage("工作项、工程量或工钱至少填一项"))
        }
        val amount = SitePayCalculator.pieceAmountCents(quantityMilli, unitPriceCents, directAmountCents)
        if (amount <= 0L) return Failure(AppError.InvalidMessage("工钱需大于 0"))
        val now = System.currentTimeMillis()
        pieceDao.insert(
            com.mdot.app.core.database.SitePieceWorkEntity(
                projectId = projectId, date = date, itemName = itemName.trim(), unit = unit,
                quantityMilli = quantityMilli.coerceAtLeast(0), unitPriceCents = unitPriceCents.coerceAtLeast(0),
                amountCents = amount, note = note?.trim()?.ifEmpty { null }, photos = photos,
                createdAt = now, updatedAt = now,
            )
        )
        settings.touch()
        return Success(Unit)
    }

    suspend fun deletePieceWork(id: Long): AppResult<Unit> {
        val deleted = pieceDao.deleteUnsettled(id)
        if (deleted == 0) return Failure(AppError.InvalidMessage("该包工记录已结算，请先撤销结算单"))
        settings.touch()
        return Success(Unit)
    }

    // ---- 汇总与结算 ----

    /** 未结算区间 = 最近结算单 period_end 次日 → today（无结算单则不限起点） */
    /** 未结算区间：上次结算次日 → 今天；无结算时从项目首笔记录日期起（而非硬编码 2000-01-01） */
    suspend fun unsettledRange(projectId: Long, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
        val start = settlementDao.lastSettlementEnd(projectId)?.plusDays(1)
            ?: listOfNotNull(attDao.minDate(projectId), advanceDao.minDate(projectId), pieceDao.minDate(projectId)).minOrNull()
            ?: today
        return start to maxOf(start, today)
    }

    /** 未结算包工（一次性读，结算页流水用） */
    suspend fun unsettledPieces(projectId: Long, from: LocalDate, to: LocalDate):
        List<com.mdot.app.domain.model.SitePieceWork> =
        pieceDao.getUnsettledRange(projectId, from, to).map { it.toDomain() }

    /** 未结算借支（一次性读，VM 刷新用） */
    suspend fun unsettledAdvances(projectId: Long, from: LocalDate, to: LocalDate): List<SiteAdvance> =
        advanceDao.getUnsettledRange(projectId, from, to).map { it.toDomain() }

    suspend fun summarizeUnsettled(projectId: Long): SitePayCalculator.Output {
        val (from, to) = unsettledRange(projectId)
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(
                attendance = attDao.getUnsettledRange(projectId, from, to).map { it.toDomain() },
                pieceWorks = pieceDao.getUnsettledRange(projectId, from, to).map { it.toDomain() },
                advances = advanceDao.getUnsettledRange(projectId, from, to).map { it.toDomain() },
            )
        )
        // 待结余额再扣减未结清的部分结算（本次结算金额，见 settlePartial）
        return out.copy(pendingCents = out.pendingCents - partialSettledTotal(projectId))
    }

    /** 预览结算单（不落库） */
    suspend fun previewSettlement(projectId: Long, from: LocalDate, to: LocalDate): SiteSettlementPreview {
        val atts = attDao.getUnsettledRange(projectId, from, to)
        val pieces = pieceDao.getUnsettledRange(projectId, from, to)
        val advs = advanceDao.getUnsettledRange(projectId, from, to)
        val out = SitePayCalculator.summarize(
            SitePayCalculator.Input(
                attendance = atts.map { it.toDomain() },
                pieceWorks = pieces.map { it.toDomain() },
                advances = advs.map { it.toDomain() },
            )
        )
        val partial = settlementDao.partialTotal(projectId)
        return SiteSettlementPreview(
            from = from, to = to,
            workPayCents = out.workPayCents, piecePayCents = out.piecePayCents,
            advanceTotalCents = out.advanceTotalCents,
            // 结清实付 = 应结 − 已借支 − 未结清的部分结算（部分结算单保留为拿钱流水）
            netCents = out.receivableCents - out.advanceTotalCents - partial,
            partialSettledCents = partial,
            attCount = atts.size, advanceCount = advs.size,
            attendance = atts.map { it.toDomain() }, advances = advs.map { it.toDomain() },
            pieceWorks = pieces.map { it.toDomain() },
        )
    }

    /** 未结清的部分结算合计（本项目，供首页/统计等待结计算扣减） */
    suspend fun partialSettledTotal(projectId: Long): Long = settlementDao.partialTotal(projectId)

    /** 部分结算（本次结算金额）：从待结余额中拿走一笔（不锁记录、无快照；撤销即删除该笔） */
    suspend fun settlePartial(projectId: Long, amountCents: Long): AppResult<Unit> {
        if (amountCents <= 0) return Failure(AppError.InvalidMessage("结算金额需大于 0"))
        val summary = summarizeUnsettled(projectId)
        if (amountCents > summary.pendingCents) return Failure(AppError.InvalidMessage("结算金额不能超过待结余额"))
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        settlementDao.insert(
            SiteSettlementEntity(
                projectId = projectId, periodStart = today, periodEnd = today,
                workPayCents = 0, piecePayCents = 0, advanceTotalCents = 0,
                netCents = amountCents, attCount = 0, advanceCount = 0,
                snapshotJson = "{}", isPartial = true, createdAt = now,
            )
        )
        settings.touch()
        return Success(Unit)
    }

    /** 确认结算（结清）：事务内 建结算单（含明细快照）→ 回填区间记录 settlement_id（D6）；net 已扣未结清部分结算 */
    suspend fun confirmSettlement(preview: SiteSettlementPreview, note: String?): AppResult<Long> {
        val projectId = preview.attendance.firstOrNull()?.projectId
            ?: preview.advances.firstOrNull()?.projectId
            ?: settings.salaryFlow.first().siteCurrentProjectId
        val partialSettled = settlementDao.getPartials(projectId).map {
            SettledPartialEntry(it.periodStart.toString(), it.periodEnd.toString(), it.netCents, it.note, it.createdAt)
        }
        val snapshot = SettlementSnapshot(
            attendance = preview.attendance, pieceWorks = preview.pieceWorks, advances = preview.advances,
            partialSettled = partialSettled,
        )
        val now = System.currentTimeMillis()
        var settlementId = 0L
        db.withTransaction {
            settlementId = settlementDao.insert(
                SiteSettlementEntity(
                    projectId = projectId, periodStart = preview.from, periodEnd = preview.to,
                    workPayCents = preview.workPayCents, piecePayCents = preview.piecePayCents,
                    advanceTotalCents = preview.advanceTotalCents, netCents = preview.netCents,
                    attCount = preview.attCount, advanceCount = preview.advanceCount,
                    snapshotJson = jsonOf(snapshot), note = note?.trim()?.ifEmpty { null },
                    createdAt = now,
                )
            )
            attDao.lockRange(projectId, preview.from, preview.to, settlementId)
            pieceDao.lockRange(projectId, preview.from, preview.to, settlementId)
            advanceDao.lockRange(projectId, preview.from, preview.to, settlementId)
            // 部分结算单已并入结清实付（net = 应结 − 已借支 − 部分结算），清零扣减防重复
            settlementDao.deletePartials(projectId)
        }
        settings.touch()
        return Success(settlementId)
    }

    /** 撤销结算：删结算单 + 解锁区间记录（快照随单删除，未结算金额恢复实时口径） */
    suspend fun revertSettlement(settlementId: Long): AppResult<Unit> {
        val st = settlementDao.getById(settlementId) ?: return Failure(AppError.InvalidMessage("结算单不存在"))
        db.withTransaction {
            attDao.unlockBySettlement(settlementId)
            pieceDao.unlockBySettlement(settlementId)
            advanceDao.unlockBySettlement(settlementId)
            settlementDao.delete(settlementId)
            // 结清单撤销：将并入实付的部分结算拿钱记录原样重建（保持待结余额连续性）
            if (!st.isPartial && st.snapshotJson.contains("partialSettled")) {
                runCatching {
                    json.decodeFromString(SettlementSnapshot.serializer(), st.snapshotJson).partialSettled
                        .filter { e -> e.netCents != 0L }
                        .forEach { e ->
                            settlementDao.insert(
                                SiteSettlementEntity(
                                    projectId = st.projectId,
                                    periodStart = java.time.LocalDate.parse(e.periodStart),
                                    periodEnd = java.time.LocalDate.parse(e.periodEnd),
                                    workPayCents = 0, piecePayCents = 0, advanceTotalCents = 0,
                                    netCents = e.netCents, attCount = 0, advanceCount = 0,
                                    snapshotJson = "{}", isPartial = true,
                                    note = e.note, createdAt = if (e.createdAt > 0) e.createdAt else System.currentTimeMillis(),
                                )
                            )
                        }
                }
            }
        }
        settings.touch()
        return Success(Unit)
    }

    fun observeSettlements(projectId: Long): Flow<List<SiteSettlementEntity>> =
        settlementDao.observeForProject(projectId)

    /** 区间内部分结算单（明细页流水用） */
    fun observePartialSettlements(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteSettlementEntity>> =
        settlementDao.observePartialsRange(projectId, from, to)

    suspend fun getSettlement(id: Long): SiteSettlementEntity? = settlementDao.getById(id)

    // ---- 快照 JSON ----

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true; encodeDefaults = true
    }

    private fun jsonOf(snapshot: SettlementSnapshot): String =
        json.encodeToString(SettlementSnapshot.serializer(), snapshot)

    private fun SiteProjectEntity.toDomain() = SiteProject(
        id = id, name = name, sort = sort, archived = archived,
        baseMinutes = baseMinutes, dailyRateCents = dailyRateCents,
        otMode = otMode, otBaseMinutes = otBaseMinutes, otHourlyCents = otHourlyCents,
        note = note, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun SiteAttendanceEntity.toDomain() = SiteAttendance(
        id = id, projectId = projectId, date = date.toString(), dayStatus = dayStatus,
        halfOfDay = halfOfDay, workMinutes = workMinutes, otMinutes = otMinutes,
        rateCents = rateCents, baseMinutes = baseMinutesSnapshot, otMode = otModeSnapshot,
        otBaseMinutes = otBaseMinutesSnapshot, otHourlyCents = otHourlyCentsSnapshot,
        workPayCents = workPayCents, otPayCents = otPayCents, note = note, photos = photos,
        settlementId = settlementId, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun com.mdot.app.core.database.SitePieceWorkEntity.toDomain() =
        com.mdot.app.domain.model.SitePieceWork(
            id = id, projectId = projectId, date = date.toString(),
            itemName = itemName, unit = unit, quantityMilli = quantityMilli,
            unitPriceCents = unitPriceCents, amountCents = amountCents,
            note = note, settlementId = settlementId, createdAt = createdAt, updatedAt = updatedAt,
        )

    private fun SiteAdvanceEntity.toDomain() = SiteAdvance(
        id = id, projectId = projectId, date = date.toString(),
        amountCents = amountCents, purpose = purpose, note = note, photos = photos,
        settlementId = settlementId, createdAt = createdAt, updatedAt = updatedAt,
    )
}

/** 结算单预览（不落库） */
data class SiteSettlementPreview(
    val from: LocalDate,
    val to: LocalDate,
    /** 未结清的部分结算合计（结清实付 = 应结 − 已借支 − 该值） */
    val partialSettledCents: Long = 0,
    val workPayCents: Long,
    val piecePayCents: Long,
    val advanceTotalCents: Long,
    val netCents: Long,
    val attCount: Int,
    val advanceCount: Int,
    val attendance: List<SiteAttendance>,
    val pieceWorks: List<com.mdot.app.domain.model.SitePieceWork> = emptyList(),
    val advances: List<SiteAdvance>,
)

/** 结算单明细快照（snapshot_json 载荷，复现当时口径） */
@kotlinx.serialization.Serializable
data class SettlementSnapshot(
    val attendance: List<SiteAttendance> = emptyList(),
    val pieceWorks: List<com.mdot.app.domain.model.SitePieceWork> = emptyList(),
    val advances: List<SiteAdvance> = emptyList(),
    /** 结清时并入的部分结算单（实付已包含；撤销结清时重建这些拿钱记录） */
    val partialSettled: List<SettledPartialEntry> = emptyList(),
)

/** 结清单快照中的部分结算拿水记录（撤销结清时按此重建部分结算单） */
@kotlinx.serialization.Serializable
data class SettledPartialEntry(
    val periodStart: String,
    val periodEnd: String,
    val netCents: Long,
    val note: String? = null,
    val createdAt: Long = 0,
)

private const val DEFAULT_PROJECT_NAME = "默认项目"
