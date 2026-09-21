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

/**
 * 节假日信息（日历角标 + 档位判定用）。
 *
 * [name] 是当天所属节日的名称（如「春节」），[kind] 区分「法定 / 调休休息日 / 补班」；
 * [makeupFor] 仅补班日有值 = 被调的节日名——**不得据此渲染节日角标**：
 * 2026-02-14 是春节的补班日而不是春节假期，直接显示「春节 + 上班」逻辑自相矛盾
 * （调研文档 §2.4 坑一），节日角标统一由 [kind] 决定。
 */
data class HolidayInfo(
    val date: LocalDate,
    val name: String,
    val kind: HolidayKind,
    val makeupFor: String? = null,
)
