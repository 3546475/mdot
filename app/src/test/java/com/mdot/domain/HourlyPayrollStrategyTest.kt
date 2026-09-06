package com.mdot.app.domain

import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.model.WorkSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 小时工工资引擎单测（08 文档 §4.2）。
 * 纯时薪制：工作收入 = 档位时薪 × 工时；请假扣款 = 平时时薪 × 系数 × 工时；无底薪无调休。
 */
class HourlyPayrollStrategyTest {

    // 三档时薪：平时25元/小时、周末50元/小时、法定75元/小时
    private val hourly25 = SalaryConfig(
        workSystem = WorkSystem.HOURLY,
        hourlyRatesCents = mapOf(
            RateTier.WEEKDAY to 2500L,
            RateTier.WEEKEND to 5000L,
            RateTier.STATUTORY to 7500L,
        ),
    )

    private fun work(
        date: String,
        minutes: Int,
        tier: RateTier? = null,
        source: TierSource? = null,
    ) = PayrollCalculator.DailyRecordLite(
        date = LocalDate.parse(date),
        type = RecordType.OT, // 小时工模式下 OT 语义为"工作"
        durationMinutes = minutes,
        tier = tier,
        tierSource = source ?: if (tier != null) TierSource.MANUAL else TierSource.AUTO,
    )

    private fun leave(date: String, minutes: Int, type: LeaveType) =
        PayrollCalculator.DailyRecordLite(
            date = LocalDate.parse(date),
            type = RecordType.LEAVE,
            durationMinutes = minutes,
            leaveType = type,
        )

    private val weekdayOf: (LocalDate) -> RateTier = { RateTier.WEEKDAY }

    // ---- workCents 单条计算 ----

    @Test
    fun `workCents 平时25元 2小时 = 5000分`() {
        assertEquals(5000L, HourlyPayrollStrategy.workCents(hourly25, RateTier.WEEKDAY, 120))
    }

    @Test
    fun `workCents 周末50元 3小时 = 15000分`() {
        assertEquals(15000L, HourlyPayrollStrategy.workCents(hourly25, RateTier.WEEKEND, 180))
    }

    @Test
    fun `workCents 法定75元 1小时 = 7500分`() {
        assertEquals(7500L, HourlyPayrollStrategy.workCents(hourly25, RateTier.STATUTORY, 60))
    }

    @Test
    fun `workCents 0分钟 = 0`() {
        assertEquals(0L, HourlyPayrollStrategy.workCents(hourly25, RateTier.WEEKDAY, 0))
    }

    @Test
    fun `workCents 非整小时 平时25元 90分钟 = 3750分`() {
        // 2500分 × 90/60 = 3750
        assertEquals(3750L, HourlyPayrollStrategy.workCents(hourly25, RateTier.WEEKDAY, 90))
    }

    @Test
    fun `workCents 分钟级舍入 平时25元 1分钟 = 42分`() {
        // 2500 × 1/60 = 41.666... → HALF_UP 42
        assertEquals(42L, HourlyPayrollStrategy.workCents(hourly25, RateTier.WEEKDAY, 1))
    }

    // ---- leaveDeductCents 请假扣款 ----

    @Test
    fun `leaveDeductCents 事假 平时时薪25元 8小时 系数1_0 = 20000分`() {
        // 2500 × 1.0 × 480/60 = 20000
        assertEquals(20000L, HourlyPayrollStrategy.leaveDeductCents(hourly25, LeaveType.PERSONAL, 480))
    }

    @Test
    fun `leaveDeductCents 病假 系数0_5 = 10000分`() {
        // 2500 × 0.5 × 480/60 = 10000
        assertEquals(10000L, HourlyPayrollStrategy.leaveDeductCents(hourly25, LeaveType.SICK, 480))
    }

    @Test
    fun `leaveDeductCents 年假 系数0 = 0`() {
        assertEquals(0L, HourlyPayrollStrategy.leaveDeductCents(hourly25, LeaveType.ANNUAL, 480))
    }

    @Test
    fun `leaveDeductCents 0分钟 = 0`() {
        assertEquals(0L, HourlyPayrollStrategy.leaveDeductCents(hourly25, LeaveType.PERSONAL, 0))
    }

    // ---- summarize 汇总计算 ----

    @Test
    fun `summarize 纯工作 两天平时各8小时 = 40000分`() {
        val output = HourlyPayrollStrategy.summarize(
            PayrollCalculator.Input(
                salary = hourly25,
                records = listOf(
                    work("2026-09-01", 480),
                    work("2026-09-02", 480),
                ),
                tierOf = weekdayOf,
            )
        )
        assertEquals(960, output.otMinutes)       // 工作分钟
        assertEquals(960, output.paidOtMinutes)
        assertEquals(40000L, output.otPayCents)   // 2500 × 16 = 40000
        assertEquals(40000L, output.incomeCents)
        assertEquals(0L, output.baseIncludedCents) // 无底薪
        assertEquals(0, output.compBalanceMinutes) // 无调休
        assertEquals(0, output.leaveMinutes)
    }

    @Test
    fun `summarize 多档位工作 平时+周末+法定`() {
        val output = HourlyPayrollStrategy.summarize(
            PayrollCalculator.Input(
                salary = hourly25,
                records = listOf(
                    work("2026-09-01", 480, RateTier.WEEKDAY, TierSource.MANUAL),
                    work("2026-09-05", 240, RateTier.WEEKEND, TierSource.MANUAL),
                    work("2026-10-01", 120, RateTier.STATUTORY, TierSource.MANUAL),
                ),
                tierOf = weekdayOf,
            )
        )
        // 平时: 2500×8=20000; 周末: 5000×4=20000; 法定: 7500×2=15000; 合计 55000
        assertEquals(55000L, output.incomeCents)
        assertEquals(20000L, output.otPayByTier[RateTier.WEEKDAY])
        assertEquals(20000L, output.otPayByTier[RateTier.WEEKEND])
        assertEquals(15000L, output.otPayByTier[RateTier.STATUTORY])
    }

    @Test
    fun `summarize 工作+请假 收入=工作收入-扣款`() {
        val output = HourlyPayrollStrategy.summarize(
            PayrollCalculator.Input(
                salary = hourly25,
                records = listOf(
                    work("2026-09-01", 480),
                    leave("2026-09-02", 240, LeaveType.PERSONAL),
                ),
                tierOf = weekdayOf,
            )
        )
        // 工作: 2500×8=20000; 事假扣款: 2500×1.0×4=10000; 收入=10000
        assertEquals(20000L, output.otPayCents)
        assertEquals(10000L, output.leaveDeductCents)
        assertEquals(10000L, output.incomeCents)
        assertEquals(240, output.leaveMinutes)
    }

    @Test
    fun `summarize 空记录 全零`() {
        val output = HourlyPayrollStrategy.summarize(
            PayrollCalculator.Input(
                salary = hourly25,
                records = emptyList(),
                tierOf = weekdayOf,
            )
        )
        assertEquals(0, output.otMinutes)
        assertEquals(0L, output.incomeCents)
        assertEquals(0L, output.baseIncludedCents)
        assertEquals(0, output.compBalanceMinutes)
        assertTrue(output.breakdowns.isEmpty())
    }

    @Test
    fun `strategyFor HOURLY 返回 HourlyPayrollStrategy`() {
        assertTrue(PayrollCalculator.strategyFor(WorkSystem.HOURLY) is HourlyPayrollStrategy)
        assertTrue(PayrollCalculator.strategyFor(WorkSystem.STANDARD) is StandardPayrollStrategy)
    }

    @Test
    fun `summarize 门面自动选择小时工策略`() {
        // PayrollCalculator.summarize 门面根据 workSystem 自动选择策略
        val output = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = hourly25,
                records = listOf(work("2026-09-01", 120)),
                tierOf = weekdayOf,
            )
        )
        // 小时工: 2500×2=5000, 无底薪
        assertEquals(5000L, output.incomeCents)
        assertEquals(0L, output.baseIncludedCents)
    }
}
