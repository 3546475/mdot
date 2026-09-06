package com.mdot.app.core.util

import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** CSV 明细导出（01 文档 F5-1 / 04 文档 §6.5）：UTF-8 BOM、\r\n、Excel 防自动转义 */
object CsvWriter {

    private fun headerFor(workSystem: WorkSystem): String = when (workSystem) {
        WorkSystem.HOURLY ->
            "日期,星期,班次,工时(小时),档位,时薪(元/小时),工作收入(元),请假类型,请假时长(小时),请假扣款(元),备注"
        WorkSystem.COMPREHENSIVE ->
            // 综合工时：普通上班即时计酬为 0（周期末统一结算），仅法定节假日即时（10 文档 F-Z6）
            "日期,星期,班次,上班工时(小时),档位,即时计酬(元),请假类型,请假时长(小时),请假扣款(元),备注"
        WorkSystem.STANDARD ->
            "日期,星期,班次,加班时长(小时),档位,倍率,加班费(元),转调休(小时),请假类型,请假时长(小时),请假扣款(元),备注"
    }

    /**
     * 生成区间内逐日 CSV 文本（含 BOM）。
     * 金额/时长由 PayrollCalculator 现算，与页面口径一致。
     * standardMinutes：综合工时区间标准（应出勤天数 × 8h），影响合计区的超时行。
     */
    fun buildRangeCsv(
        range: CycleCalculator.Period,
        records: List<com.mdot.app.domain.model.DailyRecord>,
        salary: SalaryConfig,
        tierOf: (LocalDate) -> com.mdot.app.domain.model.RateTier,
        standardMinutes: Int? = null,
    ): String {
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary, records.map { it.toCalcLite() }, tierOf = tierOf,
                standardMinutes = standardMinutes,
            )
        )
        val amountByDateType = out.breakdowns.associate {
            (it.record.date to it.record.type) to it.amountCents
        }

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("考勤周期,${escape(range.toString())}\r\n")
        sb.append("导出时间,${escape(java.time.LocalDateTime.now().toString())}\r\n")
        sb.append(headerFor(salary.workSystem)).append("\r\n")

        records.groupBy { it.date }.toSortedMap().forEach { (date, dayRecords) ->
            val ot = dayRecords.firstOrNull { it.type == RecordType.OT }
            val leave = dayRecords.firstOrNull { it.type == RecordType.LEAVE }
            val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.CHINA)
            val isHourly = salary.workSystem == WorkSystem.HOURLY
            val isComp = salary.workSystem == WorkSystem.COMPREHENSIVE
            val cells = when {
                isHourly -> listOf(
                    excelSafeDate(date),
                    weekday,
                    ot?.shiftName ?: leave?.shiftName ?: "",
                    ot?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    ot?.let { it.tier?.displayName ?: "" } ?: "",
                    ot?.let { Money.yuanText(salary.hourlyRatesCents[it.tier ?: com.mdot.app.domain.model.RateTier.WEEKDAY] ?: 0L) } ?: "",
                    ot?.let { Money.yuanText(amountByDateType[date to RecordType.OT] ?: 0L) } ?: "",
                    leave?.leaveType?.displayName ?: "",
                    leave?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    leave?.let { Money.yuanText(amountByDateType[date to RecordType.LEAVE] ?: 0L) } ?: "",
                    ot?.note ?: leave?.note ?: "",
                )

                isComp -> listOf(
                    excelSafeDate(date),
                    weekday,
                    ot?.shiftName ?: leave?.shiftName ?: "",
                    ot?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    ot?.let { it.tier?.displayName ?: "" } ?: "",
                    ot?.let { Money.yuanText(amountByDateType[date to RecordType.OT] ?: 0L) } ?: "",
                    leave?.leaveType?.displayName ?: "",
                    leave?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    leave?.let { Money.yuanText(amountByDateType[date to RecordType.LEAVE] ?: 0L) } ?: "",
                    ot?.note ?: leave?.note ?: "",
                )

                else -> listOf(
                    excelSafeDate(date),
                    weekday,
                    ot?.shiftName ?: leave?.shiftName ?: "",
                    ot?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    ot?.let { it.tier?.displayName ?: "" } ?: "",
                    ot?.let { Money.multiplierText(salary.multipliers[it.tier ?: com.mdot.app.domain.model.RateTier.WEEKDAY] ?: 1.5) } ?: "",
                    ot?.let { Money.yuanText(amountByDateType[date to RecordType.OT] ?: 0L) } ?: "",
                    ot?.let { TimeUtils.hoursDecimal(it.toCompMinutes) } ?: "",
                    leave?.leaveType?.displayName ?: "",
                    leave?.let { TimeUtils.hoursDecimal(it.durationMinutes) } ?: "",
                    leave?.let { Money.yuanText(amountByDateType[date to RecordType.LEAVE] ?: 0L) } ?: "",
                    ot?.note ?: leave?.note ?: "",
                )
            }
            sb.append(cells.joinToString(",") { escape(it) }).append("\r\n")
        }

        when (salary.workSystem) {
            WorkSystem.HOURLY -> {
                sb.append("合计,,,${TimeUtils.hoursDecimal(out.otMinutes)},,,${Money.yuanText(out.otPayCents)},,${TimeUtils.hoursDecimal(out.leaveMinutes)},${Money.yuanText(out.leaveDeductCents)},\r\n")
            }

            WorkSystem.COMPREHENSIVE -> {
                sb.append("合计,,,${TimeUtils.hoursDecimal(out.otMinutes)},,${Money.yuanText(out.otPayCents)},,${TimeUtils.hoursDecimal(out.leaveMinutes)},${Money.yuanText(out.leaveDeductCents)},\r\n")
                sb.append("周期标准(小时),${TimeUtils.hoursDecimal(out.periodStandardMinutes)}\r\n")
                sb.append("超时工时(小时),${TimeUtils.hoursDecimal(out.overtimeMinutes)}\r\n")
                sb.append("超时加班费(元),${Money.yuanText(out.otPayByTier[com.mdot.app.domain.model.RateTier.WEEKDAY] ?: 0L)}\r\n")
            }

            else -> {
                sb.append("合计,,,${TimeUtils.hoursDecimal(out.otMinutes)},,,${Money.yuanText(out.otPayCents)},,,${TimeUtils.hoursDecimal(out.leaveMinutes)},${Money.yuanText(out.leaveDeductCents)},\r\n")
                sb.append("调休余额(小时),${TimeUtils.hoursDecimal(out.compBalanceMinutes)}\r\n")
            }
        }
        return sb.toString()
    }

    /** 日期写为 ="yyyy-MM-dd" 防 Excel 自动转换（04 文档 §6.5） */
    private fun excelSafeDate(date: LocalDate): String = "\"=\"\"$date\"\"\""

    private fun escape(cell: String): String =
        if (cell.contains(',') || cell.contains('"') || cell.contains('\n')) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else cell
}
