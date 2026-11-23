package com.mdot.app.domain.model

import java.time.LocalDate

/** 保存加班记录的输入（05 文档 §6.1） */
data class OtDraft(
    val date: LocalDate,
    val shiftId: Long?,
    /** 班次名快照（班次被删后仍可显示） */
    val shiftName: String? = null,
    val durationMinutes: Int,
    val tier: RateTier,
    val tierSource: TierSource,
    val toCompMinutes: Int = 0,
    /** 钟点时段计价的开始时间（当天分钟数）；null = 该条记录按单档计算 */
    val otStartMinute: Int? = null,
    val note: String? = null,
)

/** 保存请假记录的输入 */
data class LeaveDraft(
    val date: LocalDate,
    val shiftId: Long?,
    val shiftName: String? = null,
    val durationMinutes: Int,
    val leaveType: LeaveType,
    val note: String? = null,
)
