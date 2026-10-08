package com.mdot.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 统计页「时间段」粒度（非工地四选一）：考勤周期 / 自然月 / 自然年 / 自定义区间。
 *
 * 与 `domain/` 其它文件一样**零 Android 依赖**，故不带 `labelRes`；
 * 界面侧的 `StatsDimension`（在 feature/stats）持有文案资源，映射见 `StatsDimension.rangeKind`。
 * 工地制度不用本枚举——它的口径是「本期待结 / 项目全周期 / 自定义」，由 `SiteRanges` 承担。
 */
enum class StatsRangeKind { CYCLE, MONTH, YEAR, CUSTOM }

/**
 * 统计页区间推导（纯函数，可测）。**UI 与 ViewModel 都不许自己算日期**——
 * 步进/平移的口径集中在这里，否则「‹ 一次走多远」会在两处漂移。
 *
 * 与 [CycleCalculator] 的分工：本文件只处理「统计页维度 → 区间」这一层，
 * 考勤周期内部的推演仍全部委托 [CycleCalculator]（复用 `periodOffsetFrom` / `cycleOffsetToMonth`）。
 */
object StatsRanges {

    /**
     * 各粒度的「当前区间」（offset = 0 的含义）：
     * - CYCLE：[CycleCalculator.periodContaining]（尊重考勤周期起始日设置）
     * - MONTH：今天所在自然月；YEAR：今天所在自然年
     * - CUSTOM：用户选的起止；**未选全**时给 [fallback]（首次点进「自定义」不至于空区间），
     *   选了但起 > 止视为无效返回 null
     */
    fun current(
        kind: StatsRangeKind,
        today: LocalDate,
        anchorDay: Int,
        customFrom: LocalDate?,
        customTo: LocalDate?,
        fallback: CycleCalculator.Period?,
    ): CycleCalculator.Period? = when (kind) {
        StatsRangeKind.CYCLE -> CycleCalculator.periodContaining(today, anchorDay)
        StatsRangeKind.MONTH -> CycleCalculator.naturalMonth(YearMonth.from(today))
        StatsRangeKind.YEAR -> CycleCalculator.yearPeriod(today.year)
        StatsRangeKind.CUSTOM -> {
            when {
                customFrom == null || customTo == null -> fallback
                customFrom.isAfter(customTo) -> null
                else -> CycleCalculator.Period(customFrom, customTo)
            }
        }
    }

    /**
     * 从 [from] 所在区间步进 [steps] 次（负数=往过去），返回目标区间；无法步进返回 null。
     *
     * - CYCLE / MONTH / YEAR：走 [CycleCalculator.periodOffsetFrom] 的「平移锚点日再取所属周期」，
     *   保证每步恰好一个完整区间、首尾相接不跳格；
     * - CUSTOM：**整段平移，平移量 = 区间自身天数**（用户 2026-10-08 拍板：如 10/1–10/5 前后各移 5 天，
     *   即「挪到下一条同样长的窗口」）；单日区间按 1 天走。
     *
     * ⚠️ 自定义**不设上限**：往过去可任意走，但只有在 `customTo < today` 时才能真正往过去平移
     * （见 [maxForwardSteps]）——否则「往前」会越过今天，与全 App「不越今天」口径打架。
     * 天数的整数运算不会因月末天数不同而漂移，故无需额外归一。
     */
    fun step(
        kind: StatsRangeKind,
        from: CycleCalculator.Period,
        anchorDay: Int,
        steps: Int,
        today: LocalDate,
    ): CycleCalculator.Period? = when (kind) {
        StatsRangeKind.CYCLE -> CycleCalculator.periodOffsetFrom(from.from, anchorDay, steps)
        StatsRangeKind.MONTH ->
            CycleCalculator.periodOffsetFrom(from.from.plusDays(15), 1, steps)
        StatsRangeKind.YEAR -> {
            val year = from.from.year + steps
            // 年粒度同样不越今天：未来年份没有数据可看
            if (year > today.year) null else CycleCalculator.yearPeriod(year)
        }
        StatsRangeKind.CUSTOM -> {
            val days = ChronoUnit.DAYS.between(from.from, from.to) + 1
            val shift = steps.toLong() * days
            val maxForward = ChronoUnit.DAYS.between(from.to, today)
            if (shift > 0 && shift > maxForward) null
            else CycleCalculator.Period(from.from.plusDays(shift), from.to.plusDays(shift))
        }
    }

    /**
     * 「往前还能走几步」。0 = 已经贴到今天，前进方向应置灰。
     *
     * 注意各粒度的已到边界判定不同：CYCLE/MONTH 看**区间是否已含今天**（含则再往前就会越过今天）；
     * YEAR 看年份；CUSTOM 看末日离今天还有几天（整段平移的余量，不是 0/1 两态）。
     */
    fun maxForwardSteps(kind: StatsRangeKind, current: CycleCalculator.Period, today: LocalDate): Int =
        when (kind) {
            StatsRangeKind.CYCLE, StatsRangeKind.MONTH ->
                if (current.contains(today)) 0 else 1
            StatsRangeKind.YEAR ->
                if (current.to.year >= today.year) 0 else 1
            StatsRangeKind.CUSTOM ->
                ChronoUnit.DAYS.between(current.to, today).coerceAtLeast(0)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
}
