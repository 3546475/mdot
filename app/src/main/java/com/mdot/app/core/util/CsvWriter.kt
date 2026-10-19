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
        WorkSystem.SITE ->
            // 工地记工不写 daily_record（12 文档 §3.1），此 header 仅兜底防御
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

    /**
     * 工地记工明细 CSV（Phase 2）：按日期合并出勤/包工/借支，尾部汇总行。
     * 金额直接取记录快照（分→元），与首页/结算口径一致。
     */
    fun buildSiteCsv(
        range: CycleCalculator.Period,
        projectName: String,
        attendance: List<com.mdot.app.domain.model.SiteAttendance>,
        pieceWorks: List<com.mdot.app.domain.model.SitePieceWork>,
        advances: List<com.mdot.app.domain.model.SiteAdvance>,
        summary: com.mdot.app.domain.SitePayCalculator.Output,
    ): String {
        val nl = "\r\n"
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("结算周期,${escape(range.toString())}").append(nl)
        sb.append("项目,${escape(projectName)}").append(nl)
        sb.append("导出时间,${escape(java.time.LocalDateTime.now().toString())}").append(nl)
        sb.append("日期,星期,上班(小时),加班(小时),日价(元),点工工钱(元),工作项,数量,单位,包工工钱(元),借支(元),借支用途,备注").append(nl)
        val dates = (attendance.map { java.time.LocalDate.parse(it.date) } +
            pieceWorks.map { java.time.LocalDate.parse(it.date) } +
            advances.map { java.time.LocalDate.parse(it.date) }).distinct().sorted()
        val attBy = attendance.groupBy { it.date }
        val pieceBy = pieceWorks.groupBy { it.date }
        val advBy = advances.groupBy { it.date }
        dates.forEach { d ->
            val key = d.toString()
            val weekday = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)
            val a = attBy[key]?.firstOrNull()
            val pc = pieceBy[key]?.firstOrNull()
            val v = advBy[key]?.firstOrNull()
            val qty = pc?.let {
                if (it.quantityMilli > 0) {
                    val q = it.quantityMilli / 1000.0
                    if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
                } else ""
            } ?: ""
            val cells = listOf(
                excelSafeDate(d), weekday,
                a?.let { TimeUtils.hoursDecimal(it.workMinutes) } ?: "",
                a?.let { TimeUtils.hoursDecimal(it.otMinutes) } ?: "",
                a?.let { Money.yuanText(it.rateCents) } ?: "",
                a?.let { Money.yuanText(it.workPayCents + it.otPayCents) } ?: "",
                pc?.itemName ?: "",
                qty,
                pc?.unit ?: "",
                pc?.let { Money.yuanText(it.amountCents) } ?: "",
                v?.let { Money.yuanText(it.amountCents) } ?: "",
                v?.purpose ?: "",
                listOfNotNull(a?.note, pc?.note, v?.note).filter { it.isNotBlank() }.joinToString(" / "),
            )
            sb.append(cells.joinToString(",") { escape(it) }).append(nl)
        }
        sb.append("合计," + (summary.totalWorksMilli / 1000.0) + "工,加班" + TimeUtils.hoursDecimal(summary.otMinutes) +
            ",," + Money.yuanText(summary.workPayCents) + ",点工,,,," + Money.yuanText(summary.piecePayCents) +
            "," + Money.yuanText(summary.advanceTotalCents) + ",,待结" + Money.yuanText(summary.pendingCents)).append(nl)
        return sb.toString()
    }

    /**
     * **记月工资单段**（v0.7.9.0）：组别 / 名称 / 金额(元) / 来源 四列，**记录所有行（含 0 值）**。
     *
     * 与长图的分工（用户 2026-10-20 定）：**图只显示有数的项目，CSV 记录全部**——CSV 是给机器/对账的，
     * 少一行就会让"某项目本月到底有没有"变成猜。
     * 扣款与其他组写**负数** ⇒ 整列求和 = 实发，表格里可直接核；金额用**无千分位**纯数字
     * （带千分位会被 CSV 规则加引号、表格软件也不当数字求和）。
     *
     * ⚠️ 传入的 [sheet] 应是**实时预览单据**（`domain/previewSheet` 的产物），与记月页同一份。
     * CSV 内容允许硬编码中文（硬规则 10 的例外：导出文件内容）。
     */
    fun buildPayMonthSection(monthLabel: String, sheet: com.mdot.app.domain.model.PayMonthSheet): String {
        val nl = "\r\n"
        val sb = StringBuilder()
        // 段落标记：导入侧见到这一行就停止（工资单不走 CSV 导入，它在备份包里）
        sb.append("工资单").append(nl)
        sb.append("月份,").append(escape(monthLabel)).append(nl)
        sb.append("组别,名称,金额(元),来源").append(nl)

        /** 一行：扣款/其他写负号（列求和 = 实发）；金额为 0 时**不写负号**（别出现 `-0.00`，同 docs/11 083） */
        fun row(group: String, item: com.mdot.app.domain.model.PayMonthItem, negate: Boolean) {
            val amount = if (negate && item.amountCents != 0L) "-" + yuanPlain(item.amountCents)
            else yuanPlain(item.amountCents)
            sb.append(
                listOf(group, item.name, amount, sourceText(item.source)).joinToString(",") { escape(it) }
            ).append(nl)
        }

        sheet.basic.forEach { row("基本项目", it, negate = false) }
        sheet.subsidy.forEach { row("补贴项目", it, negate = false) }
        sheet.deduction.forEach { row("扣款项目", it, negate = true) }
        sheet.other.forEach { row("其他项目", it, negate = true) }

        // 汇总行（与记月汇总卡同口径：应发 − 扣款 − 其他 = 实发）；0 同样不加负号
        fun minus(cents: Long): String = if (cents == 0L) yuanPlain(0) else "-" + yuanPlain(cents)
        sb.append("应发合计,,${yuanPlain(sheet.incomeCents)},").append(nl)
        sb.append("扣款合计,,${minus(sheet.deductionCents)},").append(nl)
        sb.append("其他合计,,${minus(sheet.otherCents)},").append(nl)
        sb.append("实发,,${yuanPlain(sheet.netCents)},").append(nl)
        return sb.toString()
    }

    /**
     * **一份 CSV 装全部数据**（v0.7.9.0 用户拍板：导出/导入是给机器用的，不必给人看）：
     * 前半段 = 逐日考勤明细（可直接再导入，格式与旧版完全一致）；后半段 = 记月工资单段。
     *
     * 为什么合一份：两段本来就是同一份数据的不同切面（考勤⇒加班的计算依据），拆成两个按钮/两个文件
     * 只会让用户每次要点两次、还要自己记住哪个是哪个。导入侧只读前半段（见 `CsvReader.parse` 的段落截断）。
     *
     * @param sheet 记月工资单（可空：工地制度没有记月，或不做工资单段时传 null）
     */
    fun buildFullCsv(
        range: CycleCalculator.Period,
        records: List<com.mdot.app.domain.model.DailyRecord>,
        salary: SalaryConfig,
        tierOf: (LocalDate) -> com.mdot.app.domain.model.RateTier,
        standardMinutes: Int? = null,
        monthLabel: String? = null,
        sheet: com.mdot.app.domain.model.PayMonthSheet? = null,
    ): String {
        val base = buildRangeCsv(range, records, salary, tierOf, standardMinutes)
        return if (sheet == null || monthLabel == null) base
        else base + "\r\n" + buildPayMonthSection(monthLabel, sheet)
    }

    /**
     * 金额（分）→ CSV 里的**纯数字**元文本：`5000.00`。
     * ⚠️ 不用 [Money.yuanText]——它带千分位（`5,000.00`），在 CSV 里会被迫加引号、
     * 也没法被表格软件当数字求和（本表的核心用途就是"整列求和 = 实发"）。
     */
    private fun yuanPlain(cents: Long): String =
        String.format(Locale.US, "%.2f", cents / 100.0)

    /** 记月行的来源戳 → 与记月页文案同义的纯文本（导出文件里不带「· 21.5 天 × 50」这类推导细节） */
    private fun sourceText(source: com.mdot.app.domain.model.PayMonthSource?): String = when (source) {
        com.mdot.app.domain.model.PayMonthSource.SYNCED -> "来自考勤 · 自动计算"
        com.mdot.app.domain.model.PayMonthSource.EDITED -> "已手改"
        com.mdot.app.domain.model.PayMonthSource.ESTIMATED -> "来自个税估算"
        null -> ""
    }

    private fun escape(cell: String): String =
        if (cell.contains(',') || cell.contains('"') || cell.contains('\n')) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else cell
}
