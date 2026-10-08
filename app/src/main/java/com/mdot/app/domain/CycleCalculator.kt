package com.mdot.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 考勤周期区间计算（anchorDay 1–31，默认 1 = 自然月） */
object CycleCalculator {

    /** 一个出勤日的工时（分钟） */
    const val MINUTES_PER_ATTENDANCE_DAY = 8 * 60

    /** 闭区间周期 */
    data class Period(val from: LocalDate, val to: LocalDate) {
        fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
        override fun toString(): String = "${from.year}年${from.monthValue}月${from.dayOfMonth}日 – ${to.year}年${to.monthValue}月${to.dayOfMonth}日"
    }

    /** date 所在的考勤周期 */
    fun periodContaining(date: LocalDate, anchorDay: Int): Period {
        require(anchorDay in 1..31) { "anchorDay 必须在 1–31" }
        if (anchorDay == 1) {
            val ym = YearMonth.from(date)
            return Period(ym.atDay(1), ym.atEndOfMonth())
        }
        // 本月起始日；本月天数不足 anchorDay 时取月末
        val ym = YearMonth.from(date)
        val thisStart = with(ym) { atDay(kotlin.math.min(anchorDay, lengthOfMonth())) }
        return if (!date.isBefore(thisStart)) {
            // 落在 [本月起始日, 次月起始日前)
            val nextStart = with(ym.plusMonths(1)) { atDay(kotlin.math.min(anchorDay, lengthOfMonth())) }
            Period(thisStart, nextStart.minusDays(1))
        } else {
            // 落在上月周期内：[上月起始日, 本月起始日前)
            val prevStart = with(ym.minusMonths(1)) { atDay(kotlin.math.min(anchorDay, lengthOfMonth())) }
            Period(prevStart, thisStart.minusDays(1))
        }
    }

    /** 某自然月对应的完整考勤周期（跨自然月时首尾不在同月） */
    fun periodOfMonth(month: YearMonth, anchorDay: Int): Period {
        require(anchorDay in 1..31) { "anchorDay 必须在 1–31" }
        if (anchorDay == 1) return Period(month.atDay(1), month.atEndOfMonth())
        val anchorDate = with(month) { atDay(kotlin.math.min(anchorDay, lengthOfMonth())) }
        return periodContaining(anchorDate, anchorDay)
    }

    /**
     * 相对基准日的**考勤周期步进**（明细页「切换展示时间」）：offset 0 = 含 [date] 的本周期，
     * -1 = 上一个完整周期，+1 = 下一个周期，依此类推（正数可推到未来，调用方自行封顶到今天）。
     *
     * 实现要点：把基准日平移到「offset 个月后的同一天」再求所属周期。注意符号约定——
     * offset 是**周期序号增量**（负=往过去），直接喂给 `plusMonths` 恰好同号；若图省事写成
     * `minusMonths(offset)`，方向会整个反过来（-1 跑到未来），症状是「点‹反而前进一期、点›反而后退一期」，
     * 而 offset=0 照常正确，极难从现象反推。故此处用 `plusMonths` 让代码与文档同号，
     * 并由单测 `周期步进 方向单调 - offset 越小日期越早` 锁死方向。
     *
     * 方向正确后，每推一次恰好是**一个完整周期**：区间首尾相接、不重叠、不跳格
     * （`periodOfMonth` 做不到这点：跨月周期只能按自然月锚，会与基准日脱节）。
     * 短月由 `periodContaining` 内既有逻辑回退到月末，与全 App 口径一致。
     */
    fun periodOffsetFrom(date: LocalDate, anchorDay: Int, offset: Int): Period =
        periodContaining(date.plusMonths(offset.toLong()), anchorDay)

    /**
     * 含 [date] 的考勤周期所落在的**锚点自然月**（跨自然月周期的起始月）——月份选择器回显用。
     * 与 [periodOffsetFrom] 配套：由周期反推「用户点的是哪个月」。
     */
    fun cycleStartMonth(date: LocalDate, anchorDay: Int): YearMonth =
        YearMonth.from(periodContaining(date, anchorDay).from)

    /**
     * 月份选择器可选上限（含）：**今天所在的自然月**。
     *
     * ⚠️ 这是**产品决策**（用户 2026-10-08 拍板），不是实现细节，改动前先读 docs/11 074：
     * 曾按「今天所在**周期**的锚点月」封顶，理由是"同周期内的月份没必要跳"；但锚点 26 日时
     * 10 月已到来却仍属 9/26–10/25 那一期，于是 10 月在选择器里被置灰——用户看到的是
     * **「本月都还没过完怎么就点不了」**，比「跳到本月落在同一期」更不可接受。故取更宽松的自然月口径。
     *
     * 两个方向的后果都已确认接受：
     * ① 点「本月」（及任何被 `coerceAtMost(0)` 夹住的月份）会**落在今天所在的那一期**（offset 0）；
     * ② 锚点 26 日、今天 10 月时「11 月」仍置灰（超上限），而它对应的 10/26–11/25 期其实已开始——
     *    要如期看到它请用 `›` 或等进入 11 月。
     *
     * 不取 `anchorDay` 是有意的：上限是**自然月**概念，与考勤周期起始日无关（少一个参数少一处错配）。
     */
    fun maxSelectableCycleMonth(date: LocalDate): YearMonth = YearMonth.from(date)

    /**
     * 月份选择器的「点某月 → 周期步进 offset」反解：**基准是「今天所在周期」的锚点月**。
     *
     * 语义：**点「n 月」= 跳到以 n 月为锚点月的那一期**（即始于 n 月 [anchorDay] 日的周期）。
     * anchor 26 时点「8 月」看到的就是 8/26–9/25，点「9 月」是本期 9/26–10/25 — 与用户对
     * 「按周期记账」的直觉一致（也是 `periodOffsetFrom` 的直接逆运算）。故 offset = 锚点月之差；
     * 上限封到今天所在周期（`coerceAtMost(0)`，不允许跳到未来周期）。
     *
     * ⚠️ 容易写错的两点（本函数第一版两条都踩了，被单测逐条抓出）：
     * ① **基准必须传 [cycleStartMonth]（今天的锚点月）而非「今天所在自然月」**——跨自然月周期下
     *    两者不同（anchor 26、今天 10/08 ⇒ 锚点月是 9 月），混用会让点 9 月得到 -1 而不是 0；
     * ② **不要用「含目标月 1 日的周期」去反推目标期**：以 26 日为锚时 1 日落在**上一期**里
     *    （8/1 属于 7/26–8/25），据此反推会整体错开一期。
     */
    fun cycleOffsetToMonth(baseCycleMonth: YearMonth, target: YearMonth): Int =
        java.time.temporal.ChronoUnit.MONTHS
            .between(baseCycleMonth, target)
            .toInt()
            .coerceAtMost(0)

    /** 自然月区间 */
    fun naturalMonth(month: YearMonth): Period = Period(month.atDay(1), month.atEndOfMonth())

    /** 自然年区间 */
    fun yearPeriod(year: Int): Period = Period(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    /**
     * 综合工时：区间内应出勤天数 × 8h 的标准工时分钟数（10 文档 §4，纯函数可测）。
     * 出勤日 = （周几在工作日设定 或 补班日）且 非法定放假日；
     * 法定放假日与补班日来自节假日库（HOLIDAY / WORKDAY），由调用方展开成日期集合注入。
     * from > to 视为空区间返回 0。季/年周期后续复用本函数（period 即区间参数）。
     */
    fun standardMinutesFor(
        from: LocalDate,
        to: LocalDate,
        workdays: Set<DayOfWeek>,
        holidays: Set<LocalDate>,
        makeupDays: Set<LocalDate> = emptySet(),
    ): Int {
        if (from.isAfter(to)) return 0
        var days = 0
        var d = from
        while (!d.isAfter(to)) {
            val attend = (d.dayOfWeek in workdays || d in makeupDays) && d !in holidays
            if (attend) days++
            d = d.plusDays(1)
        }
        return days * MINUTES_PER_ATTENDANCE_DAY
    }
}
