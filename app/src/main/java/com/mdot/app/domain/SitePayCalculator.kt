package com.mdot.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 工地记工工资计算器（12 文档 §4，domain 纯函数）。
 * 两链分工：
 *  链 1 单条出勤 → 金额（保存/编辑记录时调用，结果快照进记录）；
 *  链 2 区间汇总（首页/结算预览/结算归档），输入金额已快照的记录。
 * 金额全程「分」，逐条 HALF_UP 到分再求和；工数以毫工（×1000）承载避免浮点。
 */
object SitePayCalculator {

    /** 当日点工标准（= 项目标准或记录级快照） */
    data class DayStandard(
        val rateCents: Long,
        val baseMinutes: Int,
        /** "BY_DAY" | "BY_HOUR" */
        val otMode: String,
        val otBaseMinutes: Int,
        val otHourlyCents: Long,
    )

    // ---------- 链 1：单条出勤 → 金额 ----------

    /** 上班工钱（分）= round(上班分钟 ÷ 基准分钟 × 日价)；REST 或 0 分钟 = 0 */
    fun workPayCents(standard: DayStandard, workMinutes: Int): Long {
        if (workMinutes <= 0 || standard.baseMinutes <= 0 || standard.rateCents <= 0) return 0
        return BigDecimal(workMinutes)
            .multiply(BigDecimal(standard.rateCents))
            .divide(BigDecimal(standard.baseMinutes), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** 加班时薪（分/h）：BY_HOUR 手动值 >0 用之；否则自动 = 日价 × 60 ÷ 上班基准（HALF_UP） */
    fun otHourlyCentsPerHour(standard: DayStandard): Long {
        if (standard.otMode == "BY_HOUR" && standard.otHourlyCents > 0) return standard.otHourlyCents
        if (standard.baseMinutes <= 0 || standard.rateCents <= 0) return 0
        return BigDecimal(standard.rateCents)
            .multiply(BigDecimal(60))
            .divide(BigDecimal(standard.baseMinutes), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** 加班工钱（分）：BY_DAY = 加班分钟 ÷ 加班基准 × 日价；BY_HOUR = 加班分钟 × 时薪 ÷ 60；逐条 HALF_UP */
    fun otPayCents(standard: DayStandard, otMinutes: Int): Long {
        if (otMinutes <= 0 || standard.rateCents <= 0) return 0
        return if (standard.otMode == "BY_HOUR") {
            val hourly = otHourlyCentsPerHour(standard)
            if (hourly <= 0) 0
            else BigDecimal(otMinutes)
                .multiply(BigDecimal(hourly))
                .divide(BigDecimal(60), 0, RoundingMode.HALF_UP)
                .toLong()
        } else {
            if (standard.otBaseMinutes <= 0) 0
            else BigDecimal(otMinutes)
                .multiply(BigDecimal(standard.rateCents))
                .divide(BigDecimal(standard.otBaseMinutes), 0, RoundingMode.HALF_UP)
                .toLong()
        }
    }

    // ---------- 链 2：区间汇总 ----------

    /** 区间输入（金额已快照的记录 + 包工 + 借支） */
    data class Input(
        val attendance: List<com.mdot.app.domain.model.SiteAttendance>,
        val pieceWorks: List<com.mdot.app.domain.model.SitePieceWork> = emptyList(),
        val advances: List<com.mdot.app.domain.model.SiteAdvance>,
    )

    data class Output(
        /** 出勤（WORK 且 workMinutes>0）/ 显式休息 天数 */
        val daysWork: Int,
        val daysRest: Int,
        /** 工数合计 ×1000（18.5 工 = 18500；休息/REST 不计） */
        val totalWorksMilli: Long,
        /** 加班分钟合计 */
        val otMinutes: Int,
        /** 点工工资 = Σ(work_pay + ot_pay) */
        val workPayCents: Long,
        /** 包工工资 = Σ piece amount */
        val piecePayCents: Long,
        /** 借支合计（正数） */
        val advanceTotalCents: Long,
        /** 应得 = 点工 + 包工 */
        val receivableCents: Long,
        /** 待结 = 应得 − 借支（可为负=借超了） */
        val pendingCents: Long,
    )

    fun summarize(input: Input): Output {
        var daysWork = 0
        var daysRest = 0
        var totalWorksMilli = 0L
        var otMinutes = 0
        var workPay = 0L
        for (att in input.attendance) {
            if (att.dayStatus == "REST") {
                daysRest += 1
                continue
            }
            if (att.workMinutes > 0) {
                daysWork += 1
                totalWorksMilli += att.workMinutes.toLong() * 1000 / att.baseMinutes.coerceAtLeast(1)
            }
            otMinutes += att.otMinutes
            workPay += att.workPayCents + att.otPayCents
        }
        val piecePay = input.pieceWorks.sumOf { it.amountCents.coerceAtLeast(0) }
        val advanceTotal = input.advances.sumOf { it.amountCents.coerceAtLeast(0) }
        return Output(
            daysWork = daysWork,
            daysRest = daysRest,
            totalWorksMilli = totalWorksMilli,
            otMinutes = otMinutes,
            workPayCents = workPay,
            piecePayCents = piecePay,
            advanceTotalCents = advanceTotal,
            receivableCents = workPay + piecePay,
            pendingCents = workPay + piecePay - advanceTotal,
        )
    }

    /** 包工工钱（分）：手填工钱优先（工钱数字可直接编辑）；否则量价齐 → round(数量milli × 单价 ÷ 1000)；否则 0 */
    fun pieceAmountCents(quantityMilli: Long, unitPriceCents: Long, directCents: Long): Long =
        when {
            directCents > 0 -> directCents
            quantityMilli > 0 && unitPriceCents > 0 -> BigDecimal(quantityMilli)
                .multiply(BigDecimal(unitPriceCents))
                .divide(BigDecimal(1000), 0, RoundingMode.HALF_UP)
                .toLong()
            else -> 0
        }
}
