package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CycleCalculatorTest {

    private val monFri = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    @Test
    fun `anchor为1即自然月`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-08-29"), 1)
        assertEquals(LocalDate.parse("2026-08-01"), p.from)
        assertEquals(LocalDate.parse("2026-08-31"), p.to)
    }

    @Test
    fun `anchor 26 月末落在下周期`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-08-29"), 26)
        assertEquals(LocalDate.parse("2026-08-26"), p.from)
        assertEquals(LocalDate.parse("2026-09-25"), p.to)
    }

    @Test
    fun `anchor 26 月初落在上周期`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-08-10"), 26)
        assertEquals(LocalDate.parse("2026-07-26"), p.from)
        assertEquals(LocalDate.parse("2026-08-25"), p.to)
    }

    @Test
    fun `anchor 26 跨年边界`() {
        val p1 = CycleCalculator.periodContaining(LocalDate.parse("2026-01-05"), 26)
        assertEquals(LocalDate.parse("2025-12-26"), p1.from)
        assertEquals(LocalDate.parse("2026-01-25"), p1.to)

        val p2 = CycleCalculator.periodContaining(LocalDate.parse("2025-12-30"), 26)
        assertEquals(LocalDate.parse("2025-12-26"), p2.from)
        assertEquals(LocalDate.parse("2026-01-25"), p2.to)
    }

    @Test
    fun `anchor 28 二月`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-02-05"), 28)
        assertEquals(LocalDate.parse("2026-01-28"), p.from)
        assertEquals(LocalDate.parse("2026-02-27"), p.to)
    }

    @Test
    fun `自然月的周期视图`() {
        val p = CycleCalculator.periodOfMonth(YearMonth.of(2026, 2), 26)
        assertEquals(LocalDate.parse("2026-02-26"), p.from)
        assertEquals(LocalDate.parse("2026-03-25"), p.to)
    }

    @Test
    fun `周期包含判断`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-08-29"), 26)
        assertEquals(true, p.contains(LocalDate.parse("2026-08-26")))
        assertEquals(true, p.contains(LocalDate.parse("2026-09-25")))
        assertEquals(false, p.contains(LocalDate.parse("2026-09-26")))
        assertEquals(false, p.contains(LocalDate.parse("2026-08-25")))
    }

    @Test
    fun `anchor 29 在二月回退月末`() {
        // 平年 2 月只有 28 天：起始日落到 2/28
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-02-20"), 29)
        assertEquals(LocalDate.parse("2026-01-29"), p.from)
        assertEquals(LocalDate.parse("2026-02-27"), p.to)
    }

    @Test
    fun `anchor 29 大月正常`() {
        val p = CycleCalculator.periodContaining(LocalDate.parse("2026-03-30"), 29)
        assertEquals(LocalDate.parse("2026-03-29"), p.from)
        assertEquals(LocalDate.parse("2026-04-28"), p.to)
    }

    @Test
    fun `anchor 31 小月月末补齐`() {
        // anchor=31：11 月为 11/30，12 月为 12/31
        val inPrev = CycleCalculator.periodContaining(LocalDate.parse("2026-11-30"), 31)
        assertEquals(LocalDate.parse("2026-11-30"), inPrev.from)
        assertEquals(LocalDate.parse("2026-12-30"), inPrev.to)
        val inDec = CycleCalculator.periodContaining(LocalDate.parse("2026-12-31"), 31)
        assertEquals(LocalDate.parse("2026-12-31"), inDec.from)
        assertEquals(LocalDate.parse("2027-01-30"), inDec.to)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `anchor 越界拒绝`() {
        CycleCalculator.periodContaining(LocalDate.parse("2026-08-29"), 32)
    }

    // ---- standardMinutesFor（综合工时周期标准工时，10 文档 §4）----

    @Test
    fun `标准工时 九月自然月22个工作日`() {
        // 2026-09-01 周二；周一至周五共 22 天 → 22 × 480 = 10560
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), monFri, emptySet(),
        )
        assertEquals(22 * 480, m)
    }

    @Test
    fun `标准工时 扣除法定放假日`() {
        // 9/7（周一）放假 → 21 天
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
            monFri, setOf(LocalDate.parse("2026-09-07")),
        )
        assertEquals(21 * 480, m)
    }

    @Test
    fun `标准工时 补班日计入出勤`() {
        // 9/5（周六）补班 → 22 + 1 = 23 天
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
            monFri, emptySet(), makeupDays = setOf(LocalDate.parse("2026-09-05")),
        )
        assertEquals(23 * 480, m)
    }

    @Test
    fun `标准工时 补班日逢放假日仍不出勤`() {
        // 同一天既标放假又标补班 → 以放假优先
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
            monFri, setOf(LocalDate.parse("2026-09-05")), makeupDays = setOf(LocalDate.parse("2026-09-05")),
        )
        assertEquals(22 * 480, m)
    }

    @Test
    fun `标准工时 跨月考勤周期`() {
        // 8/26（周三）– 9/25（周五）：8 月 4 天（26/27/28/31）+ 9 月 19 天 = 23 天
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-08-26"), LocalDate.parse("2026-09-25"), monFri, emptySet(),
        )
        assertEquals(23 * 480, m)
    }

    @Test
    fun `标准工时 全周出勤`() {
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
            DayOfWeek.entries.toSet(), emptySet(),
        )
        assertEquals(30 * 480, m)
    }

    @Test
    fun `标准工时 单日出勤判定`() {
        val workday = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-02"), monFri, emptySet(),
        )
        assertEquals(480, workday)
        val saturday = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-05"), monFri, emptySet(),
        )
        assertEquals(0, saturday)
    }

    @Test
    fun `标准工时 倒序区间返回0`() {
        val m = CycleCalculator.standardMinutesFor(
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-01"), monFri, emptySet(),
        )
        assertEquals(0, m)
    }
}
