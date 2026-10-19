package com.mdot.app.core.util

import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.model.DailyRecord
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 记月工资单段 + 「一份 CSV 装全部数据」（v0.7.9.0，用户 2026-10-20 重做导出页）。
 *
 * 守四件事：
 * 1. 记月段四列固定 = 组别 / 名称 / 金额(元) / 来源；扣款与其他写负数 ⇒ 整列求和 = 实发；
 * 2. 金额是**纯数字**（不带千分位，否则被加引号、表格软件也不当数字）；
 * 3. 记月段**记录所有行含 0 值**（图才过滤）；
 * 4. 合并后的 CSV 仍能**原样导入**：`CsvReader` 读到「工资单」段就停，不会把工资单行算成"跳过"。
 */
class CsvWriterPayMonthTest {

    private val sheet = PayMonthSheet(
        basic = listOf(
            PayMonthItem(id = 1, name = "基本工资", amountCents = 500_000, builtin = true, source = PayMonthSource.SYNCED),
            PayMonthItem(id = 2, name = "加班工资", amountCents = 12_345, builtin = true, source = PayMonthSource.SYNCED),
        ),
        subsidy = listOf(
            PayMonthItem(id = 4, name = "其它补贴", amountCents = 10_000, builtin = true),
            PayMonthItem(id = 101, name = "季度奖", amountCents = 3_000, source = PayMonthSource.EDITED),
        ),
        deduction = listOf(
            PayMonthItem(id = 11, name = "事假扣款", amountCents = 2_000, builtin = true),
            PayMonthItem(id = 12, name = "全勤奖", amountCents = 0, builtin = true), // 0 值也要出现在 CSV 里
        ),
        other = listOf(
            PayMonthItem(id = 21, name = "个人所得税", amountCents = 1_500, source = PayMonthSource.ESTIMATED),
        ),
    )

    private fun lines(csv: String) = csv.trimEnd().split("\r\n")

    // ---- 记月段 ----

    @Test
    fun `记月段 段落标记与四列表头`() {
        val ls = lines(CsvWriter.buildPayMonthSection("2026年10月", sheet))
        assertEquals("工资单", ls[0])
        assertEquals("月份,2026年10月", ls[1])
        assertEquals("组别,名称,金额(元),来源", ls[2])
    }

    @Test
    fun `记月段 行按 基本 补贴 扣款 其他 顺序且带组别名`() {
        val ls = lines(CsvWriter.buildPayMonthSection("2026年10月", sheet))
        assertEquals("基本项目,基本工资,5000.00,来自考勤 · 自动计算", ls[3])
        assertEquals("补贴项目,季度奖,30.00,已手改", ls[6])
        assertEquals("扣款项目,事假扣款,-20.00,", ls[7])
        assertEquals("扣款项目,全勤奖,0.00,", ls[8])   // 0 值行保留（图才过滤）
        assertEquals("其他项目,个人所得税,-15.00,来自个税估算", ls[9])
    }

    @Test
    fun `记月段 扣款与其他写负数 使整列求和等于实发`() {
        val ls = lines(CsvWriter.buildPayMonthSection("2026年10月", sheet))
        val amounts = ls.drop(3).dropLast(4)
            .mapNotNull { it.split(",").getOrNull(2)?.takeIf { c -> c.isNotBlank() }?.toBigDecimalOrNull() }
        assertEquals(7, amounts.size)
        val sum = amounts.fold(java.math.BigDecimal.ZERO) { a, b -> a + b }
        // 列是**元**，netCents 是**分** ⇒ 移两位后比较
        assertEquals(java.math.BigDecimal.valueOf(sheet.netCents).movePointLeft(2), sum)
    }

    @Test
    fun `记月段 汇总四行与记月汇总卡同口径`() {
        val tail = lines(CsvWriter.buildPayMonthSection("2026年10月", sheet)).takeLast(4)
        assertEquals("应发合计,,5253.45,", tail[0])   // 5000 + 123.45 + 100 + 30
        assertEquals("扣款合计,,-20.00,", tail[1])
        assertEquals("其他合计,,-15.00,", tail[2])
        assertEquals("实发,,5218.45,", tail[3])
    }

    @Test
    fun `记月段 名称含逗号时按 CSV 规则加引号`() {
        val s = PayMonthSheet(basic = listOf(PayMonthItem(id = 1, name = "工资,补发", amountCents = 100)))
        val line = lines(CsvWriter.buildPayMonthSection("2026年10月", s))[3]
        assertEquals("基本项目,\"工资,补发\",1.00,", line)
    }

    // ---- 合并后的整份 CSV：仍可导入 ----

    @Test
    fun `合一份 CSV 时 考勤段在前 记月段在后 且仍可原样导入`() {
        val day = LocalDate.parse("2026-10-08")
        val records = listOf(
            DailyRecord(
                id = 1, date = day, type = RecordType.OT, shiftName = "白班",
                durationMinutes = 150, tier = RateTier.WEEKDAY,
                createdAt = 0, updatedAt = 0,
            )
        )
        val range = CycleCalculator.Period(day, day)
        val csv = CsvWriter.buildFullCsv(
            range = range,
            records = records,
            salary = SalaryConfig(),
            tierOf = { RateTier.WEEKDAY },
            standardMinutes = null,
            monthLabel = "2026年10月",
            sheet = sheet,
        )
        // 顺序：考勤段 … 然后「工资单」段
        assertTrue(csv.contains("工资单\r\n月份,2026年10月"))
        assertTrue(csv.indexOf("日期,星期") < csv.indexOf("工资单\r\n月份"))

        // 导入：只读考勤段，且工资单段的行不会被算成「跳过」
        // （判据 = 有没有工资单段，skipped 完全一致；考勤段自身的元信息/合计行照旧计入）
        val withoutSheet = CsvWriter.buildFullCsv(range, records, SalaryConfig(), { RateTier.WEEKDAY })
        val parsed = CsvReader.parse(csv)
        assertEquals(1, parsed.ot.size)
        assertEquals(day, parsed.ot.first().date)
        assertEquals(150, parsed.ot.first().durationMinutes)
        assertTrue(parsed.leave.isEmpty())
        assertEquals(CsvReader.parse(withoutSheet).skipped, parsed.skipped)
    }

    @Test
    fun `不给记月段时 输出与旧版逐日 CSV 完全一致`() {
        val day = LocalDate.parse("2026-10-08")
        val records = listOf(
            DailyRecord(
                id = 1, date = day, type = RecordType.OT, durationMinutes = 60,
                tier = RateTier.WEEKDAY, createdAt = 0, updatedAt = 0,
            )
        )
        val range = CycleCalculator.Period(day, day)
        val full = CsvWriter.buildFullCsv(range, records, SalaryConfig(), { RateTier.WEEKDAY })
        val legacy = CsvWriter.buildRangeCsv(range, records, SalaryConfig(), { RateTier.WEEKDAY })
        // 「导出时间」行含当前时刻，两次调用必然不同 —— 比较时剔掉它
        fun stable(csv: String) = csv.lines().filterNot { it.startsWith("导出时间") }.joinToString("\n")
        assertEquals(stable(legacy), stable(full))
        assertFalse(full.contains("工资单"))
    }
}
