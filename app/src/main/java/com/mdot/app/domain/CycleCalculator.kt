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
