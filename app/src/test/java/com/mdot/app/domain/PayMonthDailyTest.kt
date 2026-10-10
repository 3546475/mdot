package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记月「按日计算」（用户 2026-10-08 定：口径可自选、半天请假算 0.5 天）。全勤奖同轮撤回自动化、降为手填。
 * 重点是 **0.5 天这类小数**与**分位舍入**——浮点在这里出错就是真金白银的错账。
 */
class PayMonthDailyTest {

    /** 一个普通月：22 个应出勤日、加班记了 5 天、请假 1 天(480 分)、周期 30 天 */
    private val typical = PayMonthDayCounts(
        standardMinutes = 22 * 480,
        recordDayCount = 5,
        calendarDayCount = 30,
        leaveMinutes = 480,
    )

    // ---- 天数口径 ----

    @Test
    fun `天数 - 应出勤口径取工作日推算的天数`() {
        assertEquals(22.0, typical.days(PayMonthDayBasis.STANDARD, deductLeave = false), 1e-9)
    }

    @Test
    fun `天数 - 记录口径只数有加班记录的天`() {
        assertEquals(5.0, typical.days(PayMonthDayBasis.RECORD, deductLeave = false), 1e-9)
    }

    @Test
    fun `天数 - 自然日口径取周期跨度`() {
        assertEquals(30.0, typical.days(PayMonthDayBasis.CALENDAR, deductLeave = false), 1e-9)
    }

    @Test
    fun `天数 - 扣请假时半天按半数天算（用户拍板）`() {
        val half = typical.copy(leaveMinutes = 240)
        // 22 − 0.5 = 21.5
        assertEquals(21.5, half.days(PayMonthDayBasis.STANDARD, deductLeave = true), 1e-9)
        assertEquals(0.5, half.leaveDays, 1e-9)
    }

    @Test
    fun `天数 - 扣请假结果不为负`() {
        val heavyLeave = typical.copy(standardMinutes = 480, leaveMinutes = 960)
        assertEquals(0.0, heavyLeave.days(PayMonthDayBasis.STANDARD, deductLeave = true), 1e-9)
    }

    @Test
    fun `天数 - 不扣请假时请假不影响任何口径`() {
        for (b in PayMonthDayBasis.entries) {
            assertEquals(typical.days(b, false), typical.copy(leaveMinutes = 0).days(b, false), 1e-9)
        }
    }

    // ---- 金额 ----

    @Test
    fun `按日金额 - 整数天直接乘`() {
        // 50 元/天 × 22 天 = 1100 元 = 110000 分
        assertEquals(110_000L, PayMonthDaily.dailyCents(5_000, 22.0))
    }

    @Test
    fun `按日金额 - 半天精确到分不丢钱`() {
        // 50 元/天 × 21.5 天 = 1075 元
        assertEquals(107_500L, PayMonthDaily.dailyCents(5_000, 21.5))
    }

    @Test
    fun `按日金额 - 非整除时 HALF_UP 到分`() {
        // 33.33 元/天（3333 分）× 1 天 = 3333 分（整除）
        assertEquals(3_333L, PayMonthDaily.dailyCents(3_333, 1.0))
        // 3333 分 × 0.5 天 = 1666.5 → HALF_UP = 1667
        assertEquals(1_667L, PayMonthDaily.dailyCents(3_333, 0.5))
    }

    @Test
    fun `按日金额 - 浮点陷阱：十分之一天仍精确`() {
        // 直接浮点乘会得到 500.00000000000006 → 500 分；这里必须仍是 500
        assertEquals(500L, PayMonthDaily.dailyCents(5_000, 0.1))
    }

    @Test
    fun `按日金额 - 单价或天数为 0 时归零不报错`() {
        assertEquals(0L, PayMonthDaily.dailyCents(0, 22.0))
        assertEquals(0L, PayMonthDaily.dailyCents(5_000, 0.0))
        assertEquals(0L, PayMonthDaily.dailyCents(5_000, -3.0))
    }

    @Test
    fun `按日金额 - 极小天数不产生负数或溢出`() {
        assertEquals(0L, PayMonthDaily.dailyCents(5_000, 0.0001))
    }

    // 注：全勤奖 2026-10-08 已由自动降为手动填，相关的 fullAttendanceCents / isFullAttendance
    // 与用例一并删除（曾做过"满勤发、缺勤归零"，但真实全勤条件需要迟到/早退/漏打卡等
    // App 拿不到的数据，硬判会给出用户不认可的结论）。

    // ---- 不变量 ----

    @Test
    fun `不变量 - 一天的钱 = 480 分钟的钱（口径互转自洽）`() {
        val oneDay = PayMonthDayCounts(standardMinutes = 480)
        assertEquals(1.0, oneDay.days(PayMonthDayBasis.STANDARD, false), 1e-9)
        assertEquals(5_000L, PayMonthDaily.dailyCents(5_000, oneDay.days(PayMonthDayBasis.STANDARD, false)))
    }

    @Test
    fun `不变量 - 单调性：请假越多，按日补贴越少`() {
        var prev = Long.MAX_VALUE
        for (minutes in listOf(0, 120, 240, 480, 960)) {
            val days = typical.days(PayMonthDayBasis.STANDARD, deductLeave = true)
            val cents = PayMonthDaily.dailyCents(5_000, days)
            assertTrue("请假 $minutes 分钟 → $cents 应 ≤ 上一个", cents <= prev)
            prev = cents
        }
    }

    @Test
    fun `不变量 - 任意输入下天数都不为负`() {
        val cases = listOf(
            PayMonthDayCounts(),
            typical,
            typical.copy(standardMinutes = 1, leaveMinutes = 99_999),
            PayMonthDayCounts(standardMinutes = -480, calendarDayCount = -3),
        )
        for (c in cases) {
            for (b in PayMonthDayBasis.entries) {
                for (deduct in listOf(true, false)) {
                    assertTrue("counts=$c basis=$b deduct=$deduct", c.days(b, deduct) >= 0.0)
                }
            }
        }
    }

    @Test
    fun `不变量 - 记录日天然不超过自然日（同区间去重的必然结果）`() {
        val consistent = PayMonthDayCounts(recordDayCount = 5, calendarDayCount = 30)
        assertTrue(consistent.days(PayMonthDayBasis.RECORD, false) <= consistent.days(PayMonthDayBasis.CALENDAR, false))
    }

    // ---- counts：两个 ViewModel 共用的组装函数 ----

    @Test
    fun `counts - 自然月区间组装四个计数`() {
        val weekdays = setOf(
            java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
        )
        val c = PayMonthDaily.counts(
            from = java.time.LocalDate.parse("2026-10-01"),
            to = java.time.LocalDate.parse("2026-10-31"),
            workdays = weekdays,
            holidays = emptySet(),
            makeupDays = emptySet(),
            recordDates = listOf(
                java.time.LocalDate.parse("2026-10-06"),
                java.time.LocalDate.parse("2026-10-06"),   // 同日两条：必须去重
                java.time.LocalDate.parse("2026-10-08"),
            ),
            leaveMinutes = 240,
        )
        assertEquals("2026-10 共 31 天", 31, c.calendarDayCount)
        assertEquals("10/6 与 10/8 两天（去重后）", 2, c.recordDayCount)
        assertEquals(240, c.leaveMinutes)
        assertEquals(0.5, c.leaveDays, 1e-9)
        assertTrue("应出勤应落在 20~23 天之间", c.standardMinutes / 480 in 20..23)
    }

    @Test
    fun `counts - 法定假日不计入应出勤、补班日计入`() {
        val weekdays = setOf(
            java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
        )
        val thu = java.time.LocalDate.parse("2026-10-01")   // 周四
        assertEquals(480, PayMonthDaily.counts(thu, thu, weekdays, emptySet(), emptySet(), emptyList(), 0).standardMinutes)
        assertEquals(0, PayMonthDaily.counts(thu, thu, weekdays, setOf(thu), emptySet(), emptyList(), 0).standardMinutes)
        val sat = java.time.LocalDate.parse("2026-10-10")   // 周六
        assertEquals(0, PayMonthDaily.counts(sat, sat, weekdays, emptySet(), emptySet(), emptyList(), 0).standardMinutes)
        assertEquals(480, PayMonthDaily.counts(sat, sat, weekdays, emptySet(), setOf(sat), emptyList(), 0).standardMinutes)
    }

    @Test
    fun `counts - 区间颠倒时自然日为 0 不产生负数`() {
        val c = PayMonthDaily.counts(
            from = java.time.LocalDate.parse("2026-10-31"),
            to = java.time.LocalDate.parse("2026-10-01"),
            workdays = setOf(java.time.DayOfWeek.MONDAY),
            holidays = emptySet(), makeupDays = emptySet(),
            recordDates = emptyList(), leaveMinutes = 0,
        )
        assertEquals(0, c.calendarDayCount)
        assertEquals(0, c.standardMinutes)
    }

    @Test
    fun `counts - 请假分钟为负时夹到 0`() {
        val c = PayMonthDaily.counts(
            java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"),
            emptySet(), emptySet(), emptySet(), emptyList(), -600,
        )
        assertEquals(0, c.leaveMinutes)
    }
}
