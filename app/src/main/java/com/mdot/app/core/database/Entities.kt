package com.mdot.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** 每日记录（(date,type) 唯一，08 文档 §3.1）。CHECK 约束在 Repository 层校验。 */
@Entity(
    tableName = "daily_record",
    indices = [
        Index(value = ["date", "type", "work_system"], unique = true),
        Index(value = ["date"]),
        Index(value = ["work_system"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class DailyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** OT | LEAVE */
    val type: String,
    /** STANDARD | HOURLY — 工时制度隔离（08 文档 §3.1） */
    @ColumnInfo(name = "work_system", defaultValue = "STANDARD") val workSystem: String,
    @ColumnInfo(name = "shift_id") val shiftId: Long?,
    @ColumnInfo(name = "shift_name") val shiftName: String?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    /** WEEKDAY | WEEKEND | STATUTORY（仅 OT） */
    val tier: String?,
    /** AUTO | MANUAL */
    @ColumnInfo(name = "tier_source") val tierSource: String?,
    @ColumnInfo(name = "to_comp_minutes") val toCompMinutes: Int,
    /** PERSONAL|SICK|ANNUAL|COMP|ABSENT|OTHER（仅 LEAVE） */
    @ColumnInfo(name = "leave_type") val leaveType: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 班次（预置 1–6 不可删，08 文档 §3.2） */
@Entity(tableName = "shift", indices = [Index(value = ["name"], unique = true)])
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int,
    val builtin: Boolean,
    val hidden: Boolean,
    val rest: Boolean,
)

/** 调休手动调整（08 文档 §3.3） */
@Entity(tableName = "comp_adjustment", indices = [Index(value = ["date"])])
data class CompAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    @ColumnInfo(name = "delta_minutes") val deltaMinutes: Int,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 节假日库缓存：整年 JSON 单行存储（08 文档 §3.4） */
@Entity(tableName = "holiday_cache")
data class HolidayCacheEntity(
    @PrimaryKey val year: Int,
    val json: String,
    @ColumnInfo(name = "data_version") val dataVersion: String,
    /** BUILTIN | REMOTE */
    val source: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
