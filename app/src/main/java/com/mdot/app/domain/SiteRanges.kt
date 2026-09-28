package com.mdot.app.domain

import java.time.LocalDate

/**
 * 工地记工区间推导（21 文档 B1a）：工地的「周期」= 结算区间 / 项目工期，
 * **不是**考勤周期（[CycleCalculator]）或自然月——后者是其它三种模式的固定周期口径。
 * 纯函数（domain 零 Android 依赖，硬规则 1）；数据进出由 `SiteRepository` 负责。
 */
object SiteRanges {

    /** 工地区间口径（21 文档 B1/B2） */
    enum class Kind {
        /** 本期待结：上次结算单 period_end 次日 → 今天（无结算单则从项目首条记录日起） */
        UNSETTLED,

        /** 项目全周期：项目首条记录日 → 末条记录日（自动推导，项目无工期字段，Q1 拍板） */
        PROJECT_SPAN,
    }

    /**
     * 区间推导纯函数。
     *
     * - [Kind.UNSETTLED]：起点 = lastSettlementEnd+1 ?: firstRecord ?: today；终点 = max(起点, today)。
     *   （与 `SiteRepository.unsettledRange` 同一语义；终点不越过今天——今天之后没有可记的工。）
     * - [Kind.PROJECT_SPAN]：起点 = firstRecord ?: today；终点 = max(lastRecord ?: today, 起点)。
     *   无记录项目退化为今天单日。
     */
    fun resolve(
        kind: Kind,
        lastSettlementEnd: LocalDate?,
        firstRecord: LocalDate?,
        lastRecord: LocalDate?,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> = when (kind) {
        Kind.UNSETTLED -> {
            val start = lastSettlementEnd?.plusDays(1) ?: firstRecord ?: today
            start to maxOf(start, today)
        }
        Kind.PROJECT_SPAN -> {
            val start = firstRecord ?: today
            start to maxOf(lastRecord ?: today, start)
        }
    }

    /** 柱状图数据桶（21 文档 B3）：date=桶起始日（日桶=当天，周桶=周一），label=null 表示走组件自动日标签 */
    data class Bar(val date: LocalDate, val value: Float, val label: String?)

    /**
     * 柱状图分桶（21 文档 B3）：区间 ≤ [maxBars] 天按日出柱（label=null，组件自动 1/5/10/15/20/25/30 日标签）；
     * 超长按周聚合（ISO 周一起，date=周一、value=周合计），标签「M/d」稀疏约 6 个防拥挤。
     * [dayValues] 只需含区间内有数据的日期，缺日按 0。
     */
    fun bucketize(
        from: LocalDate,
        to: LocalDate,
        dayValues: Map<LocalDate, Float>,
        maxBars: Int = 31,
    ): List<Bar> {
        if (from.isAfter(to)) return emptyList()
        val days = (to.toEpochDay() - from.toEpochDay()).toInt() + 1
        if (days <= maxBars) {
            return (0 until days).map { off ->
                val d = from.plusDays(off.toLong())
                Bar(d, dayValues[d] ?: 0f, null)
            }
        }
        val monday = from.minusDays(((from.dayOfWeek.value + 6) % 7).toLong())
        val weeks = mutableListOf<Bar>()
        var w = monday
        while (!w.isAfter(to)) {
            val v = (0L..6L).mapNotNull { dayValues[w.plusDays(it)] }.sum()
            weeks.add(Bar(w, v, "${w.monthValue}/${w.dayOfMonth}"))
            w = w.plusDays(7)
        }
        return weeks
    }
}
