package com.mdot.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 记月「按日计算」的**天数口径**——用户可自选（2026-10-08 用户拍板"可以自己选"）。
 *
 * 三种口径各有取舍，UI 上要如实说明（`paymonth_day_basis_*`）：
 * - [STANDARD] 应出勤日：按工作日设定 + 节假日调休修正算，**不依赖用户记不记加班**，最稳。
 * - [RECORD] 实际记录日：区间内有加班记录的去重日期数。贴近"我实际干几天"，
 *   但用户没记加班的日子根本不在库里，会偏少（记月页的出勤摘要已在用同一口径）。
 * - [CALENDAR] 周期自然日：考勤周期首末日之差。完全不受记录影响，
 *   但锚点周期（如 26 日起算）只有 30 天、自然月 31 天，**每月金额会跳**。
 */
enum class PayMonthDayBasis {
    STANDARD,
    RECORD,
    CALENDAR,
}

/**
 * 记月一个周期内的**四个计数字段**（纯数据，无行为）。
 *
 * 天数一律以"分钟 ÷ 480"换算，保留小数——用户 2026-10-08 定：**半天请假算 0.5 天**。
 * 金额侧由 [PayMonthDaily.dailyCents] 逐行 HALF_UP 到分（硬规则 1）。
 *
 * @property standardMinutes 应出勤工时（[CycleCalculator.standardMinutesFor] 的输出）
 * @property recordDayCount 区间内有加班记录的**去重日期数**
 * @property calendarDayCount 考勤周期的自然日数（含首尾）
 * @property leaveMinutes 请假总分钟（事假 + 病假）
 */
data class PayMonthDayCounts(
    val standardMinutes: Int = 0,
    val recordDayCount: Int = 0,
    val calendarDayCount: Int = 0,
    val leaveMinutes: Int = 0,
) {
    /** 请假日数（可为小数：半天 = 0.5） */
    val leaveDays: Double get() = leaveMinutes.toDouble() / CycleCalculator.MINUTES_PER_ATTENDANCE_DAY

    /**
     * 按口径取天数；[deductLeave] 为真时扣掉请假。
     *
     * 结果恒 **≥ 0**：扣请假后可能为负（请假超过应出勤），基数为负则是调用方把区间算反了
     * （`from > to`）——两种都不该让补贴变成负数，一律夹到 0。
     * 区间算反属于调用方的 bug，本函数只保证**出不错账**。
     *
     * ⚠️ 扣请假**不会**同时取消事假/病假两行的金额扣减——那是两笔不同的账：
     * 这里扣的是"补贴按出勤发"，那里扣的是"请假不发工资"。是否启用由用户开关决定。
     */
    fun days(basis: PayMonthDayBasis, deductLeave: Boolean): Double {
        val base = when (basis) {
            PayMonthDayBasis.STANDARD -> standardMinutes.toDouble() / CycleCalculator.MINUTES_PER_ATTENDANCE_DAY
            PayMonthDayBasis.RECORD -> recordDayCount.toDouble()
            PayMonthDayBasis.CALENDAR -> calendarDayCount.toDouble()
        }
        val raw = if (deductLeave) base - leaveDays else base
        return raw.coerceAtLeast(0.0)
    }
}

/** 记月「按日计算」的推导（纯函数，可测；UI 与 ViewModel 都不得自己算金额） */
object PayMonthDaily {

    /**
     * 组装一个区间的四个天数计数（**两个 ViewModel 共用这一处推导**）。
     *
     * 记月页（[com.mdot.app.feature.stats.PayMonthViewModel]）与首页数据区
     * （[com.mdot.app.feature.home.HomeViewModel]）都要给 [livePreviewSheet] 传同一份计数——
     * 各写一套必然漂移，首页 hero 与记月页汇总卡就会对不上账（硬规则 12）。
     *
     * @param from/to 这张工资单覆盖的区间（记月 = **自然月**：单据按月存，
     *   `previewFlow` 也是按 `atDay(1)..atEndOfMonth()` 取数的，故这里同款，不用考勤周期）
     * @param recordDates 有**加班记录**的日期（可重复，函数内去重）
     * @param leaveMinutes 请假总分钟，取**引擎算出来的** `Output.leaveMinutes`，别在这里重数一遍
     */
    fun counts(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        workdays: Set<java.time.DayOfWeek>,
        holidays: Set<java.time.LocalDate>,
        makeupDays: Set<java.time.LocalDate>,
        recordDates: List<java.time.LocalDate>,
        leaveMinutes: Int,
    ): PayMonthDayCounts = PayMonthDayCounts(
        standardMinutes = CycleCalculator.standardMinutesFor(from, to, workdays, holidays, makeupDays),
        recordDayCount = recordDates.distinct().size,
        calendarDayCount = if (from.isAfter(to)) 0
        else (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt(),
        leaveMinutes = leaveMinutes.coerceAtLeast(0),
    )

    /**
     * 按日金额（分）= 日单价 × 天数。
     *
     * 舍入：先回到**分钟**整数（`days × 480` 四舍五入到分，即把 0.5 天这类小数固定成
     * 可复现的分钟数），再做 `单价 × 分钟 ÷ 480` 的 HALF_UP 到分。
     * 走 [BigDecimal] 而非浮点直接乘——浮点会把 50 × 0.1 这类算成 5.000000000000001（硬规则 1）。
     *
     * 单价 ≤ 0 或天数 ≤ 0 一律 0（"没配单价"不该出现负数或天文数字）。
     */
    fun dailyCents(unitCents: Long, days: Double): Long {
        if (unitCents <= 0 || days <= 0.0) return 0
        val minutes = kotlin.math.round(days * CycleCalculator.MINUTES_PER_ATTENDANCE_DAY).toLong()
        if (minutes <= 0) return 0
        return BigDecimal.valueOf(unitCents)
            .multiply(BigDecimal.valueOf(minutes))
            .divide(BigDecimal.valueOf(CycleCalculator.MINUTES_PER_ATTENDANCE_DAY.toLong()))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}