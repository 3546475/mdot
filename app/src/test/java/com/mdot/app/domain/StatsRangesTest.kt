package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计页区间推导（方案 C：胶囊 + 弹窗选维度）纯函数。
 * 重点是 **`‹ ›` 每步走多远**与**不越今天**两条口径——它们是 UI 直接拿去做箭头可用性的依据。
 */
class StatsRangesTest {

    private val today: LocalDate = LocalDate.parse("2026-10-08")

    // ---- current（各粒度的"当前区间"含义）----

    @Test
    fun `当前区间 - 考勤周期尊重起始日设置`() {
        val p = StatsRanges.current(
            StatsRangeKind.CYCLE, today, anchorDay = 26,
            customFrom = null, customTo = null, fallback = null,
        )
        assertEquals(LocalDate.parse("2026-09-26"), p!!.from)
        assertEquals(LocalDate.parse("2026-10-25"), p.to)
    }

    @Test
    fun `当前区间 - 自然月与自然年`() {
        val m = StatsRanges.current(StatsRangeKind.MONTH, today, 1, null, null, null)!!
        assertEquals(LocalDate.parse("2026-10-01"), m.from)
        assertEquals(LocalDate.parse("2026-10-31"), m.to)

        val y = StatsRanges.current(StatsRangeKind.YEAR, today, 1, null, null, null)!!
        assertEquals(LocalDate.parse("2026-01-01"), y.from)
        assertEquals(LocalDate.parse("2026-12-31"), y.to)
    }

    @Test
    fun `当前区间 - 自定义未选全时用回退区间`() {
        val fb = CycleCalculator.Period(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-10-25"))
        val half = StatsRanges.current(StatsRangeKind.CUSTOM, today, 26, customFrom = null, customTo = null, fallback = fb)
        assertEquals(fb, half)
        val onlyFrom = StatsRanges.current(
            StatsRangeKind.CUSTOM, today, 26,
            customFrom = LocalDate.parse("2026-08-01"), customTo = null, fallback = fb,
        )
        assertEquals(fb, onlyFrom)
    }

    @Test
    fun `当前区间 - 自定义起晚于止视为无效`() {
        val p = StatsRanges.current(
            StatsRangeKind.CUSTOM, today, 26,
            customFrom = LocalDate.parse("2026-10-05"), customTo = LocalDate.parse("2026-10-01"),
            fallback = null,
        )
        assertNull(p)
    }

    // ---- step：每种粒度"一次走多远" ----

    @Test
    fun `步进 - 考勤周期一次一整期`() {
        val base = CycleCalculator.Period(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-10-25"))
        val prev = StatsRanges.step(StatsRangeKind.CYCLE, base, 26, -1, today)!!
        assertEquals(LocalDate.parse("2026-08-26"), prev.from)
        assertEquals(LocalDate.parse("2026-09-25"), prev.to)
    }

    @Test
    fun `步进 - 自然月一次整月`() {
        val base = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        val prev = StatsRanges.step(StatsRangeKind.MONTH, base, 1, -1, today)!!
        assertEquals(LocalDate.parse("2026-09-01"), prev.from)
        assertEquals(LocalDate.parse("2026-09-30"), prev.to)
        val prev3 = StatsRanges.step(StatsRangeKind.MONTH, base, 1, -3, today)!!
        assertEquals(LocalDate.parse("2026-07-01"), prev3.from)
        assertEquals(LocalDate.parse("2026-07-31"), prev3.to)
    }

    @Test
    fun `步进 - 自然月跨年`() {
        val jan = CycleCalculator.Period(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        val prev = StatsRanges.step(StatsRangeKind.MONTH, jan, 1, -1, today)!!
        assertEquals(LocalDate.parse("2025-12-01"), prev.from)
        assertEquals(LocalDate.parse("2025-12-31"), prev.to)
    }

    @Test
    fun `步进 - 年一次整年且不越今天`() {
        val y26 = CycleCalculator.Period(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
        val y25 = StatsRanges.step(StatsRangeKind.YEAR, y26, 1, -1, today)!!
        assertEquals(2025, y25.from.year)
        // 往未来拒绝（今天 2026，没有 2027 可看）
        assertNull(StatsRanges.step(StatsRangeKind.YEAR, y26, 1, 1, today))
    }

    @Test
    fun `步进 - 自定义按区间天数整段平移`() {
        // 10/1–10/5 共 5 天 ⇒ 往过去平移 5 天 = 9/26–9/30（用户拍板的口径）
        val p = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        val prev = StatsRanges.step(StatsRangeKind.CUSTOM, p, 1, -1, today)!!
        assertEquals(LocalDate.parse("2026-09-26"), prev.from)
        assertEquals(LocalDate.parse("2026-09-30"), prev.to)
        // 区间长度守恒
        assertEquals(5L, java.time.temporal.ChronoUnit.DAYS.between(prev.from, prev.to) + 1)
    }

    @Test
    fun `步进 - 自定义单日区间按一天走`() {
        val p = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"))
        val prev = StatsRanges.step(StatsRangeKind.CUSTOM, p, 1, -1, today)!!
        assertEquals(LocalDate.parse("2026-09-30"), prev.from)
        assertEquals(LocalDate.parse("2026-09-30"), prev.to)
    }

    @Test
    fun `步进 - 自定义连续回退保持等长且不重叠`() {
        val base = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        val p1 = StatsRanges.step(StatsRangeKind.CUSTOM, base, 1, -1, today)!!
        val p2 = StatsRanges.step(StatsRangeKind.CUSTOM, p1, 1, -1, today)!!
        assertEquals(LocalDate.parse("2026-09-21"), p2.from)
        assertEquals(LocalDate.parse("2026-09-25"), p2.to)
        // 首尾相接（前一段末日 = 后一段首日 - 1）
        assertEquals(p1.from.minusDays(1), p2.to)
    }

    @Test
    fun `步进 - 自定义往未来越过今天被拒绝`() {
        val p = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        // 区间末日 10/5，今天 10/8 ⇒ 最多再往未来 3 天，走 5 天越界 ⇒ 拒绝
        assertNull(StatsRanges.step(StatsRangeKind.CUSTOM, p, 1, 1, today))
    }

    // ---- maxForwardSteps：箭头是否该置灰 ----

    @Test
    fun `前进上限 - 区间含今天则为 0（箭头置灰）`() {
        val cur = CycleCalculator.Period(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-10-25"))
        assertEquals(0, StatsRanges.maxForwardSteps(StatsRangeKind.CYCLE, cur, today))
        val m = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals(0, StatsRanges.maxForwardSteps(StatsRangeKind.MONTH, m, today))
    }

    @Test
    fun `前进上限 - 历史区间可以前进`() {
        val prev = CycleCalculator.Period(LocalDate.parse("2026-08-26"), LocalDate.parse("2026-09-25"))
        assertEquals(1, StatsRanges.maxForwardSteps(StatsRangeKind.CYCLE, prev, today))
        val m = CycleCalculator.Period(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))
        assertEquals(1, StatsRanges.maxForwardSteps(StatsRangeKind.MONTH, m, today))
    }

    @Test
    fun `前进上限 - 年看年份`() {
        val y25 = CycleCalculator.yearPeriod(2025)
        assertEquals(1, StatsRanges.maxForwardSteps(StatsRangeKind.YEAR, y25, today))
        val y26 = CycleCalculator.yearPeriod(2026)
        assertEquals(0, StatsRanges.maxForwardSteps(StatsRangeKind.YEAR, y26, today))
        // 在未来年份上也不该允许"再往前"（防御性：正常操作走不到，因为上限已挡住）
        val y27 = CycleCalculator.yearPeriod(2027)
        assertEquals(0, StatsRanges.maxForwardSteps(StatsRangeKind.YEAR, y27, today))
    }

    @Test
    fun `前进上限 - 自定义是还差几天而非两态`() {
        // 区间末日 10/5、今天 10/8 ⇒ 还能往前 3 天；策略是整段平移，故 5 天的区间走不动，但上限确实是 3
        val p = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        assertEquals(3, StatsRanges.maxForwardSteps(StatsRangeKind.CUSTOM, p, today))
        // 已含今天 ⇒ 0
        val covering = CycleCalculator.Period(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-08"))
        assertEquals(0, StatsRanges.maxForwardSteps(StatsRangeKind.CUSTOM, covering, today))
    }

    // ---- 不变量：多点交叉验证 ----

    @Test
    fun `不变量 - 各粒度回退再前进必回到原区间`() {
        val cases = listOf(
            StatsRangeKind.CYCLE to CycleCalculator.Period(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-10-25")),
            StatsRangeKind.MONTH to CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")),
            StatsRangeKind.YEAR to CycleCalculator.yearPeriod(2026),
        )
        cases.forEach { (kind, base) ->
            val back = StatsRanges.step(kind, base, 26, -1, today)!!
            val forth = StatsRanges.step(kind, back, 26, 1, today)!!
            assertEquals("kind=$kind", base, forth)
        }
    }

    @Test
    fun `不变量 - 自定义回退再前进必回到原区间`() {
        val base = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        val back = StatsRanges.step(StatsRangeKind.CUSTOM, base, 1, -1, today)!!
        val forth = StatsRanges.step(StatsRangeKind.CUSTOM, back, 1, 1, today)!!
        assertEquals(base, forth)
    }

    @Test
    fun `不变量 - 步进区间不晚于今天所在区间`() {
        val today = LocalDate.parse("2026-10-08")
        listOf(StatsRangeKind.CYCLE, StatsRangeKind.MONTH, StatsRangeKind.YEAR).forEach { kind ->
            val base = StatsRanges.current(kind, today, 26, null, null, null)!!
            (-6..0).forEach { steps ->
                val p = StatsRanges.step(kind, base, 26, steps, today)!!
                assertEquals(
                    "kind=$kind steps=$steps p=$p",
                    true,
                    !p.from.isAfter(base.from),
                )
            }
        }
    }

    @Test
    fun `不变量 - 自定义回退区间不晚于今天`() {
        // 10/1–10/8 共 8 天 ⇒ 往过去平移 8 天 = 9/23–9/30
        val fb = CycleCalculator.Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08"))
        val cur = StatsRanges.current(StatsRangeKind.CUSTOM, today, 1, null, null, fb)!!
        val prev = StatsRanges.step(StatsRangeKind.CUSTOM, cur, 1, -1, today)!!
        assertEquals(true, !prev.to.isAfter(today))
        assertEquals(LocalDate.parse("2026-09-23"), prev.from)
        assertEquals(LocalDate.parse("2026-09-30"), prev.to)
    }
}
