package com.mdot.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 工地记工 DAO 族（12 文档 §3.2；所有区间查询按项目过滤，project_id 贯穿） */
@Dao
interface SiteProjectDao {

    @Query("SELECT * FROM site_project WHERE archived = 0 ORDER BY sort, id")
    fun observeActive(): Flow<List<SiteProjectEntity>>

    /** 已归档项目（管理页归档区，可恢复） */
    @Query("SELECT * FROM site_project WHERE archived = 1 ORDER BY sort, id")
    fun observeArchived(): Flow<List<SiteProjectEntity>>

    @Query("UPDATE site_project SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)

    @Query("SELECT * FROM site_project ORDER BY sort, id")
    suspend fun getAll(): List<SiteProjectEntity>

    @Query("SELECT * FROM site_project WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SiteProjectEntity?

    @Query("SELECT * FROM site_project WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SiteProjectEntity?>

    @Query("SELECT * FROM site_project WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SiteProjectEntity?

    @Query("SELECT * FROM site_project ORDER BY sort, id")
    suspend fun getAllForImport(): List<SiteProjectEntity>

    /** 全部项目（含归档；统计页项目工时饼图取名用） */
    @Query("SELECT * FROM site_project ORDER BY sort, id")
    fun observeAll(): Flow<List<SiteProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SiteProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SiteProjectEntity>)

    @Update
    suspend fun update(entity: SiteProjectEntity)

    @Query("UPDATE site_project SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COUNT(*) FROM site_project")
    suspend fun count(): Int

    @Query("DELETE FROM site_project WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM site_project")
    suspend fun deleteAllForImport()
}

@Dao
interface SiteAttendanceDao {

    @Query("SELECT * FROM site_attendance WHERE project_id = :projectId AND date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteAttendanceEntity>>

    /** 全项目区间出勤（统计页「项目工时」饼图聚合用） */
    @Query("SELECT * FROM site_attendance WHERE date BETWEEN :from AND :to ORDER BY date, project_id")
    fun observeAllRange(from: LocalDate, to: LocalDate): Flow<List<SiteAttendanceEntity>>

    @Query("SELECT * FROM site_attendance WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL ORDER BY date")
    suspend fun getUnsettledRange(projectId: Long, from: LocalDate, to: LocalDate): List<SiteAttendanceEntity>

    @Query("SELECT * FROM site_attendance WHERE project_id = :projectId AND date = :date LIMIT 1")
    suspend fun getByDate(projectId: Long, date: LocalDate): SiteAttendanceEntity?

    /** 项目首笔出勤日期（未结算区间默认起点） */
    @Query("SELECT MIN(date) FROM site_attendance WHERE project_id = :projectId")
    suspend fun minDate(projectId: Long): LocalDate?

    @Query("SELECT * FROM site_attendance WHERE project_id = :projectId ORDER BY date")
    suspend fun getAllForProject(projectId: Long): List<SiteAttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SiteAttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SiteAttendanceEntity>)

    @Query("DELETE FROM site_attendance WHERE id = :id AND settlement_id IS NULL")
    suspend fun deleteUnsettled(id: Long): Int

    @Query("UPDATE site_attendance SET settlement_id = :settlementId WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL")
    suspend fun lockRange(projectId: Long, from: LocalDate, to: LocalDate, settlementId: Long)

    @Query("UPDATE site_attendance SET settlement_id = NULL WHERE settlement_id = :settlementId")
    suspend fun unlockBySettlement(settlementId: Long)

    @Query("SELECT COUNT(*) FROM site_attendance WHERE project_id = :projectId")
    suspend fun countForProject(projectId: Long): Int

    /** 彻底删除项目的全部出勤（含已结算；仅随项目 purge 使用） */
    @Query("DELETE FROM site_attendance WHERE project_id = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Query("SELECT * FROM site_attendance ORDER BY date, project_id")
    suspend fun getAll(): List<SiteAttendanceEntity>

    @Query("DELETE FROM site_attendance")
    suspend fun deleteAllForImport()
}

@Dao
interface SiteAdvanceDao {

    @Query("SELECT * FROM site_advance WHERE project_id = :projectId AND date BETWEEN :from AND :to ORDER BY date, id")
    fun observeRange(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteAdvanceEntity>>

    @Query("SELECT * FROM site_advance WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL ORDER BY date, id")
    suspend fun getUnsettledRange(projectId: Long, from: LocalDate, to: LocalDate): List<SiteAdvanceEntity>

    /** 项目首笔借支日期（未结算区间默认起点） */
    @Query("SELECT MIN(date) FROM site_advance WHERE project_id = :projectId")
    suspend fun minDate(projectId: Long): LocalDate?

    @Query("SELECT * FROM site_advance WHERE project_id = :projectId ORDER BY date, id")
    suspend fun getAllForProject(projectId: Long): List<SiteAdvanceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SiteAdvanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SiteAdvanceEntity>)

    @Update
    suspend fun update(entity: SiteAdvanceEntity)

    @Query("DELETE FROM site_advance WHERE id = :id AND settlement_id IS NULL")
    suspend fun deleteUnsettled(id: Long): Int

    @Query("UPDATE site_advance SET settlement_id = :settlementId WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL")
    suspend fun lockRange(projectId: Long, from: LocalDate, to: LocalDate, settlementId: Long)

    @Query("UPDATE site_advance SET settlement_id = NULL WHERE settlement_id = :settlementId")
    suspend fun unlockBySettlement(settlementId: Long)

    @Query("SELECT COUNT(*) FROM site_advance WHERE project_id = :projectId")
    suspend fun countForProject(projectId: Long): Int

    /** 彻底删除项目的全部借支（含已结算；仅随项目 purge 使用） */
    @Query("DELETE FROM site_advance WHERE project_id = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Query("SELECT * FROM site_advance ORDER BY date, project_id, id")
    suspend fun getAll(): List<SiteAdvanceEntity>

    @Query("DELETE FROM site_advance")
    suspend fun deleteAllForImport()
}

@Dao
interface SitePieceWorkDao {

    @Query("SELECT * FROM site_piece_work WHERE project_id = :projectId AND date BETWEEN :from AND :to ORDER BY date, id")
    fun observeRange(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SitePieceWorkEntity>>

    /** 全项目区间包工/工量（我的页 SITE 模式年工钱聚合用） */
    @Query("SELECT * FROM site_piece_work WHERE date BETWEEN :from AND :to ORDER BY date, project_id, id")
    fun observeAllRange(from: LocalDate, to: LocalDate): Flow<List<SitePieceWorkEntity>>

    @Query("SELECT * FROM site_piece_work WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL ORDER BY date, id")
    suspend fun getUnsettledRange(projectId: Long, from: LocalDate, to: LocalDate): List<SitePieceWorkEntity>

    /** 项目首笔包工日期（未结算区间默认起点） */
    @Query("SELECT MIN(date) FROM site_piece_work WHERE project_id = :projectId")
    suspend fun minDate(projectId: Long): LocalDate?

    /** 彻底删除项目的全部包工（含已结算；仅随项目 purge 使用） */
    @Query("DELETE FROM site_piece_work WHERE project_id = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SitePieceWorkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SitePieceWorkEntity>)

    @Query("DELETE FROM site_piece_work WHERE id = :id AND settlement_id IS NULL")
    suspend fun deleteUnsettled(id: Long): Int

    @Query("UPDATE site_piece_work SET settlement_id = :settlementId WHERE project_id = :projectId AND date BETWEEN :from AND :to AND settlement_id IS NULL")
    suspend fun lockRange(projectId: Long, from: LocalDate, to: LocalDate, settlementId: Long)

    @Query("UPDATE site_piece_work SET settlement_id = NULL WHERE settlement_id = :settlementId")
    suspend fun unlockBySettlement(settlementId: Long)

    @Query("SELECT * FROM site_piece_work ORDER BY date, project_id, id")
    suspend fun getAll(): List<SitePieceWorkEntity>

    @Query("DELETE FROM site_piece_work")
    suspend fun deleteAllForImport()
}

@Dao
interface SiteSettlementDao {

    @Query("SELECT * FROM site_settlement WHERE project_id = :projectId ORDER BY period_end DESC, id DESC")
    fun observeForProject(projectId: Long): Flow<List<SiteSettlementEntity>>

    @Query("SELECT * FROM site_settlement WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SiteSettlementEntity?

    /** 未结算起点 = 该项目最近（非部分）结算单 period_end 的次日；无结算单返回 null；部分结算单不锁区间，不参与 */
    @Query("SELECT MAX(period_end) FROM site_settlement WHERE project_id = :projectId AND is_partial = 0")
    suspend fun lastSettlementEnd(projectId: Long): LocalDate?

    /** 未结清的部分结算合计（从待结余额中扣减） */
    @Query("SELECT COALESCE(SUM(net_cents), 0) FROM site_settlement WHERE project_id = :projectId AND is_partial = 1")
    suspend fun partialTotal(projectId: Long): Long

    /** 全部部分结算单（结清时并入实付并清零；撤销结清时按快照重建） */
    @Query("SELECT * FROM site_settlement WHERE project_id = :projectId AND is_partial = 1 ORDER BY created_at")
    suspend fun getPartials(projectId: Long): List<SiteSettlementEntity>

    /** 区间内部分结算单（明细页流水用） */
    @Query("SELECT * FROM site_settlement WHERE project_id = :projectId AND is_partial = 1 AND period_start BETWEEN :from AND :to ORDER BY period_start DESC, id DESC")
    fun observePartialsRange(projectId: Long, from: LocalDate, to: LocalDate): Flow<List<SiteSettlementEntity>>

    @Query("DELETE FROM site_settlement WHERE project_id = :projectId AND is_partial = 1")
    suspend fun deletePartials(projectId: Long)

    @Insert
    suspend fun insert(entity: SiteSettlementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SiteSettlementEntity>)

    @Query("SELECT * FROM site_settlement ORDER BY project_id, period_end")
    suspend fun getAll(): List<SiteSettlementEntity>

    @Query("DELETE FROM site_settlement")
    suspend fun deleteAllForImport()

    @Query("DELETE FROM site_settlement WHERE id = :id")
    suspend fun delete(id: Long)

    /** 彻底删除项目的全部结算单（仅随项目 purge 使用；快照一并删除） */
    @Query("DELETE FROM site_settlement WHERE project_id = :projectId")
    suspend fun deleteAllForProject(projectId: Long)
}
