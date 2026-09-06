package com.mdot.app.domain.model

import java.time.LocalDate

/**
 * 每日记录（加班/请假各每天最多 1 条，(date,type) 唯一）。
 * 金额不入库：仅存时长（分钟）与档位快照，金额由 PayrollCalculator 实时算。
 */
data class DailyRecord(
    val id: Long = 0,
    val date: LocalDate,
    val type: RecordType,
    /** 工时制度隔离（STANDARD/HOURLY） */
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    val shiftId: Long? = null,
    val shiftName: String? = null,
    val durationMinutes: Int,
    /** 加班档位（仅 OT）；LEAVE 为 null */
    val tier: RateTier? = null,
    val tierSource: TierSource? = null,
    /** 转调休分钟（仅 OT），≤ durationMinutes */
    val toCompMinutes: Int = 0,
    /** 请假类型（仅 LEAVE） */
    val leaveType: LeaveType? = null,
    /** 备注 ≤200 字 */
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 班次 */
data class Shift(
    val id: Long = 0,
    val name: String,
    val sort: Int,
    val builtin: Boolean = false,
    val hidden: Boolean = false,
    /** 是否休息类班次（"休息"） */
    val rest: Boolean = false,
)

/** 调休手动调整（正=补入，负=扣减） */
data class CompAdjustment(
    val id: Long = 0,
    val date: LocalDate,
    val deltaMinutes: Int,
    val note: String? = null,
    val createdAt: Long,
)

/** 节假日信息（日历角标展示用） */
data class HolidayInfo(
    val date: LocalDate,
    val name: String,
    val kind: HolidayKind,
)
