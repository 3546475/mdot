package com.mdot.app.core.repository

import com.mdot.app.core.database.ShiftDao
import com.mdot.app.core.database.ShiftEntity
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.AppResult.Failure
import com.mdot.app.core.util.AppResult.Success
import com.mdot.app.domain.model.Shift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 班次仓库：预置班次仅可改名/隐藏/排序；自定义班次可增删改、排序（05 文档 §6.2） */
@Singleton
class ShiftRepository @Inject constructor(
    private val dao: ShiftDao,
    private val settings: SettingsDataSource,
) {

    fun observeAll(): Flow<List<Shift>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Shift> = dao.getAll().map { it.toDomain() }

    /** 仅自定义班次可删除 */
    suspend fun delete(id: Long): AppResult<Unit> {
        val target = dao.getById(id) ?: return Failure(AppError.Unexpected)
        if (target.builtin) return Failure(AppError.Unexpected)
        dao.deleteById(id)
        settings.touch()
        return Success(Unit)
    }

    suspend fun create(name: String): AppResult<Shift> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 10) return Failure(AppError.InvalidName)
        if (dao.getByName(trimmed) != null) return Failure(AppError.DuplicateName)
        val sort = (dao.getAll().maxOfOrNull { it.sort } ?: -1) + 1
        val entity = ShiftEntity(name = trimmed, sort = sort, builtin = false, hidden = false, rest = false)
        val id = dao.insert(entity)
        settings.touch()
        return Success(Shift(id, trimmed, sort, builtin = false, hidden = false, rest = false))
    }

    suspend fun rename(id: Long, name: String): AppResult<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 10) return Failure(AppError.InvalidName)
        val existing = dao.getByName(trimmed)
        if (existing != null && existing.id != id) return Failure(AppError.DuplicateName)
        val target = dao.getById(id) ?: return Failure(AppError.Unexpected)
        dao.update(target.copy(name = trimmed))
        settings.touch()
        return Success(Unit)
    }

    suspend fun setHidden(id: Long, hidden: Boolean): AppResult<Unit> {
        val target = dao.getById(id) ?: return Failure(AppError.Unexpected)
        dao.update(target.copy(hidden = hidden))
        settings.touch()
        return Success(Unit)
    }

    suspend fun reorder(orderedIds: List<Long>): AppResult<Unit> = try {
        orderedIds.forEachIndexed { index, id -> dao.updateSort(id, index) }
        settings.touch()
        Success(Unit)
    } catch (e: Exception) {
        Failure(AppError.Storage(e.message ?: "排序失败"))
    }
}

fun ShiftEntity.toDomain() = Shift(
    id = id, name = name, sort = sort, builtin = builtin, hidden = hidden, rest = rest,
)
