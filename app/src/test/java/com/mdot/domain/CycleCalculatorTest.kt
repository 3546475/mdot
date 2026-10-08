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

    // ---- periodOffsetFrom / cycleStartMonth（明细页「切换展示时间」周期步进，v0.7.x）----

    @Test
    fun `周期步进 offset 0 即本周期`() {
        val p = CycleCalculator.periodOffsetFrom(LocalDate.parse("2026-10-08"), 26, 0)
        assertEquals(LocalDate.parse("2026-09-26"), p.from)
        assertEquals(LocalDate.parse("2026-10-25"), p.to)
    }

    @Test
    fun `周期步进 往前一步是紧邻的完整周期`() {
        val base = LocalDate.parse("2026-10-08")
        val cur = CycleCalculator.periodOffsetFrom(base, 26, 0)
        val prev = CycleCalculator.periodOffsetFrom(base, 26, -1)
        assertEquals(LocalDate.parse("2026-08-26"), prev.from)
        assertEquals(LocalDate.parse("2026-09-25"), prev.to)
        // 首尾相接、不重叠、不跳格：上一周期的末日 = 本周期首日 - 1
        assertEquals(cur.from.minusDays(1), prev.to)
    }

    @Test
    fun `周期步进 连续回退不跳格`() {
        val base = LocalDate.parse("2026-10-08")
        // -1 → -2 → -3 每步都恰好一个周期，且链式首尾相接
        val offsets = listOf(0, -1, -2, -3).map { CycleCalculator.periodOffsetFrom(base, 26, it) }
        offsets.zipWithNext { newer, older ->
            assertEquals(newer.from.minusDays(1), older.to)
        }
        assertEquals(LocalDate.parse("2026-06-26"), offsets.last().from)
        assertEquals(LocalDate.parse("2026-07-25"), offsets.last().to)
    }

    @Test
    fun `周期步进 方向单调 - offset 越小日期越早`() {
        // 方向守门：曾经实现写成 minusMonths(offset)，导致 offset=-1 反而**前进一期**
        // （offset=0 仍正确，故只测 offset 0 发现不了）。此处显式锁死方向。
        val base = LocalDate.parse("2026-10-08")
        val starts = (-3..1).map { CycleCalculator.periodOffsetFrom(base, 26, it).from }
        assertEquals(starts.sorted(), starts)      // 单调不减：offset 递增 ⇒ 周期起点越晚
        assertEquals(starts.distinct().size, starts.size) // 每期不同，无重复
        val zero = CycleCalculator.periodOffsetFrom(base, 26, 0)
        assertEquals(zero, CycleCalculator.periodContaining(base, 26))
        // 往过去必须严格早于本周期；往未来必须严格晚于本周期
        assertEquals(true, CycleCalculator.periodOffsetFrom(base, 26, -1).from.isBefore(zero.from))
        assertEquals(true, CycleCalculator.periodOffsetFrom(base, 26, 1).from.isAfter(zero.from))
    }

    @Test
    fun `周期步进 往前推跨年正确`() {
        // 基准日 2026-01-05 落在 2025-12-26–2026-01-25；再回退一期 = 2025-11-26–2025-12-25
        val prev = CycleCalculator.periodOffsetFrom(LocalDate.parse("2026-01-05"), 26, -1)
        assertEquals(LocalDate.parse("2025-11-26"), prev.from)
        assertEquals(LocalDate.parse("2025-12-25"), prev.to)
    }

    @Test
    fun `周期步进 anchor 1 等于自然月回退`() {
        val p = CycleCalculator.periodOffsetFrom(LocalDate.parse("2026-03-15"), 1, -1)
        assertEquals(LocalDate.parse("2026-02-01"), p.from)
        assertEquals(LocalDate.parse("2026-02-28"), p.to)
    }

    @Test
    fun `周期步进 anchor 29 遇短月回退月末且仍首尾相接`() {
        val base = LocalDate.parse("2026-04-10") // 落在 2026-03-29–2026-04-28
        val prev = CycleCalculator.periodOffsetFrom(base, 29, -1)
        assertEquals(LocalDate.parse("2026-02-28"), prev.from) // 2 月无 29 日 → 2/28
        assertEquals(LocalDate.parse("2026-03-28"), prev.to)
        val cur = CycleCalculator.periodOffsetFrom(base, 29, 0)
        assertEquals(cur.from.minusDays(1), prev.to)
    }

    @Test
    fun `周期步进 落在周期起始日当天也取本周期而非上一期`() {
        // 边界：起始日当天必须归本期（否则步进会与「本月」判断打架）
        val p = CycleCalculator.periodOffsetFrom(LocalDate.parse("2026-09-26"), 26, 0)
        assertEquals(LocalDate.parse("2026-09-26"), p.from)
        assertEquals(LocalDate.parse("2026-10-25"), p.to)
    }

    @Test
    fun `锚点自然月 - 跨自然月周期取起始月`() {
        assertEquals(
            YearMonth.of(2026, 9),
            CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 26),
        )
        // anchor=1 时就是本月
        assertEquals(
            YearMonth.of(2026, 10),
            CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 1),
        )
    }

    @Test
    fun `锚点自然月 加 offset 与周期步进一致`() {
        // 明细页月份选择器回显用：cycleStartMonth + offset 必须等于 periodOffsetFrom(offset).from 所在月
        val today = LocalDate.parse("2026-10-08")
        val startMonth = CycleCalculator.cycleStartMonth(today, 26)
        listOf(0, -1, -2, -5, -12).forEach { offset ->
            val expected = YearMonth.from(CycleCalculator.periodOffsetFrom(today, 26, offset).from)
            assertEquals("offset=$offset", expected, startMonth.plusMonths(offset.toLong()))
        }
    }

    // ---- cycleOffsetToMonth（月份选择器点某月 → 周期步进，明细页）----

    @Test
    fun `点月 - anchor 1 时月差即步进`() {
        val base = CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 1) // 2026-10
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 10)))
        assertEquals(-1, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 9)))
        assertEquals(-9, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 1)))
        assertEquals(-12, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2025, 10)))
    }

    @Test
    fun `点月 - anchor 26 基准取锚点月而非今天所在月`() {
        // 今天 2026-10-08，周期 9/26–10/25 ⇒ **锚点月 = 2026-09**（不是 10 月）
        val base = CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 26)
        assertEquals(YearMonth.of(2026, 9), base)
        // 点锚点月本身（9 月）＝本周期 ⇒ 0
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 9)))
        // 点 8 月 ⇒ 8/26–9/25 ⇒ 退 1 期
        assertEquals(-1, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 8)))
        // 点 11 月 ⇒ 10/26–11/25 —— 比本期**晚**，封顶到本周期 0（未来周期不开放）
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 11)))
        // 点 12 月同理封顶
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 12)))
        // 点 8 月推出的周期确实是 8/26–9/25，且 8/1 **不在**其中（正是「含 1 日反推」会踩的坑）
        val aug = CycleCalculator.periodOffsetFrom(LocalDate.parse("2026-10-08"), 26, -1)
        assertEquals(LocalDate.parse("2026-08-26"), aug.from)
        assertEquals(false, aug.contains(LocalDate.parse("2026-08-01")))
    }

    @Test
    fun `点月 - 不早于本周期的月份一律封顶到 0`() {
        // 上限语义：未来周期无数据可看 ⇒ 点任何「不比本周期早」的月都停在 0。
        // 注意封顶**只对上限**生效：历史月份照常给负数
        val base26 = CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 26) // 2026-09
        listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2027, 5)).forEach { m ->
            assertEquals("anchor26 target=$m", 0, CycleCalculator.cycleOffsetToMonth(base26, m))
        }
        val base1 = CycleCalculator.cycleStartMonth(LocalDate.parse("2026-10-08"), 1) // 2026-10
        listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 12), YearMonth.of(2027, 1)).forEach { m ->
            assertEquals("anchor1 target=$m", 0, CycleCalculator.cycleOffsetToMonth(base1, m))
        }
        // 历史月照常给负数（封顶不误伤过去）
        assertEquals(-1, CycleCalculator.cycleOffsetToMonth(base26, YearMonth.of(2026, 8)))
        assertEquals(-1, CycleCalculator.cycleOffsetToMonth(base1, YearMonth.of(2026, 9)))
    }

    @Test
    fun `点月 与周期步进互为逆运算`() {
        // 关键不变量：点「某周期的锚点月」得到的 offset，必须正好推出那一期
        val today = LocalDate.parse("2026-10-08")
        listOf(26, 1, 15, 29).forEach { anchor ->
            val base = CycleCalculator.cycleStartMonth(today, anchor)
            (-14..0).forEach { offset ->
                val target = base.plusMonths(offset.toLong())
                val back = CycleCalculator.cycleOffsetToMonth(base, target)
                assertEquals(
                    "anchor=$anchor offset=$offset",
                    CycleCalculator.periodOffsetFrom(today, anchor, offset),
                    CycleCalculator.periodOffsetFrom(today, anchor, back),
                )
            }
        }
    }

    @Test
    fun `点月 选中的月份就是该周期的锚点月`() {
        // 语义守门：点「n 月」⇒ 目标周期始于 n 月 anchorDay 日（区间起始月 == n 月）
        val today = LocalDate.parse("2026-10-08")
        listOf(26, 1, 15, 29).forEach { anchor ->
            val base = CycleCalculator.cycleStartMonth(today, anchor)
            (-10..0).forEach { m ->
                val target = base.plusMonths(m.toLong())
                val offset = CycleCalculator.cycleOffsetToMonth(base, target)
                val period = CycleCalculator.periodOffsetFrom(today, anchor, offset)
                assertEquals(
                    "anchor=$anchor target=$target period=$period",
                    target,
                    YearMonth.from(period.from),
                )
                // 且永远不晚于本期（未来周期不开放）
                assertEquals(
                    "anchor=$anchor target=$target",
                    true,
                    offset <= 0,
                )
            }
        }
    }

    @Test
    fun `点月 - 历史月份绝不被上限误伤`() {
        // 上限只在「往未来」方向收敛，历史月一律照常可选（防回归：夹取写成双向会把过去也砍掉）
        val today = LocalDate.parse("2026-10-08")
        listOf(26, 1, 15, 29).forEach { anchor ->
            val base = CycleCalculator.cycleStartMonth(today, anchor)
            (-24..-1).forEach { m ->
                val target = base.plusMonths(m.toLong())
                assertEquals(
                    "anchor=$anchor target=$target",
                    m,
                    CycleCalculator.cycleOffsetToMonth(base, target),
                )
            }
        }
    }

    @Test
    fun `可选上限 - 取今天所在自然月（产品决策，非周期锚点月）`() {
        // 用户 2026-10-08 拍板：上限用**自然月**。曾按「今天所在周期的锚点月」封顶，
        // anchor 26 时把已到来的 10 月置灰，用户看到的是「本月都没过完怎么就点不了」。
        // 若有人改回周期口径，本用例必须连同 docs/11 074 一起改。
        val today = LocalDate.parse("2026-10-08")
        assertEquals(YearMonth.of(2026, 10), CycleCalculator.maxSelectableCycleMonth(today))
        // 与考勤周期起始日无关：换 anchor 上限不变
        assertEquals(
            CycleCalculator.maxSelectableCycleMonth(today),
            YearMonth.from(today),
        )
        // 月末/跨年边界也只取自然月
        assertEquals(
            YearMonth.of(2026, 12),
            CycleCalculator.maxSelectableCycleMonth(LocalDate.parse("2026-12-31")),
        )
        assertEquals(
            YearMonth.of(2027, 1),
            CycleCalculator.maxSelectableCycleMonth(LocalDate.parse("2027-01-01")),
        )
    }

    @Test
    fun `点月 - anchor 26 时本月可选且落在本期`() {
        // 自然月上限的配套行为：点「10 月」（已到来）可以点，且它落在今天所在的那一期
        // （10/26–11/25 那一期尚未成为"今天所在周期"，故被 coerceAtMost(0) 夹回 0）
        val today = LocalDate.parse("2026-10-08")
        val base = CycleCalculator.cycleStartMonth(today, 26) // 2026-09
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 10)))
        // 于是点 10 月看到的仍是本期 9/26–10/25
        val period = CycleCalculator.periodOffsetFrom(today, 26, 0)
        assertEquals(LocalDate.parse("2026-09-26"), period.from)
        assertEquals(LocalDate.parse("2026-10-25"), period.to)
        // 而 11 月超上限（自然月 10 月），仍是 0 —— 已与用户确认接受该取舍
        assertEquals(0, CycleCalculator.cycleOffsetToMonth(base, YearMonth.of(2026, 11)))
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
