package com.mdot.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecordDao {

    @Query("SELECT * FROM daily_record WHERE date = :date AND work_system = :workSystem")
    fun observeByDate(date: LocalDate, workSystem: String): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_record WHERE date BETWEEN :from AND :to AND work_system = :workSystem ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate, workSystem: String): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_record WHERE date BETWEEN :from AND :to AND work_system = :workSystem ORDER BY date")
    suspend fun getRange(from: LocalDate, to: LocalDate, workSystem: String): List<DailyRecordEntity>

    @Query("SELECT * FROM daily_record ORDER BY date, type")
    suspend fun getAll(): List<DailyRecordEntity>

    @Query("SELECT COUNT(*) FROM daily_record")
    suspend fun count(): Int

    @Query("DELETE FROM daily_record")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DailyRecordEntity>)

    @Query("SELECT * FROM daily_record WHERE date = :date AND type = :type AND work_system = :workSystem LIMIT 1")
    suspend fun getByDateAndType(date: LocalDate, type: String, workSystem: String): DailyRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DailyRecordEntity): Long

    @Update
    suspend fun update(entity: DailyRecordEntity)

    @Query("DELETE FROM daily_record WHERE date = :date AND type = :type AND work_system = :workSystem")
    suspend fun delete(date: LocalDate, type: String, workSystem: String)

    /** 调休余额（08 文档 §7 口径 SQL） */
    @Query(
        """
        SELECT
          (SELECT IFNULL(SUM(to_comp_minutes),0) FROM daily_record WHERE type = 'OT' AND work_system = 'STANDARD')
        + (SELECT IFNULL(SUM(-duration_minutes),0) FROM daily_record WHERE type = 'LEAVE' AND leave_type = 'COMP' AND work_system = 'STANDARD')
        + (SELECT IFNULL(SUM(delta_minutes),0) FROM comp_adjustment)
        """
    )
    fun observeCompBalance(): Flow<Int>

    /** 切换到小时工制度时调用：清零所有加班记录的转调休分钟数 */
    @Query("UPDATE daily_record SET to_comp_minutes = 0 WHERE type = 'OT' AND work_system = 'STANDARD'")
    suspend fun clearAllCompMinutes()
}

@Dao
interface ShiftDao {

    @Query("SELECT * FROM shift ORDER BY sort, id")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shift ORDER BY sort, id")
    suspend fun getAll(): List<ShiftEntity>

    @Query("SELECT * FROM shift WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShiftEntity?

    @Query("SELECT * FROM shift WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ShiftEntity>)

    @Query("DELETE FROM shift")
    suspend fun deleteAll()

    @Update
    suspend fun update(entity: ShiftEntity)

    @Query("UPDATE shift SET sort = :sort WHERE id = :id")
    suspend fun updateSort(id: Long, sort: Int)

    @Query("DELETE FROM shift WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM shift")
    suspend fun count(): Int
}

@Dao
interface CompAdjustmentDao {

    @Query("SELECT * FROM comp_adjustment ORDER BY date")
    fun observeAll(): Flow<List<CompAdjustmentEntity>>

    @Query("SELECT * FROM comp_adjustment ORDER BY date")
    suspend fun getAll(): List<CompAdjustmentEntity>

    @Insert
    suspend fun insert(entity: CompAdjustmentEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CompAdjustmentEntity>)

    @Query("DELETE FROM comp_adjustment")
    suspend fun deleteAll()

    @Query("DELETE FROM comp_adjustment WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HolidayDao {

    @Query("SELECT * FROM holiday_cache")
    fun observeAll(): Flow<List<HolidayCacheEntity>>

    @Query("SELECT * FROM holiday_cache")
    suspend fun getAll(): List<HolidayCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HolidayCacheEntity>)

    @Query("SELECT MAX(data_version) FROM holiday_cache")
    fun observeDataVersion(): Flow<String?>
}
