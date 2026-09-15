package com.mdot.app.domain

import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.TierSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PayrollCalculatorTest {

    private val base2400 = SalaryConfig(baseSalaryCents = 240_000) // 月薪 2400 元

    private fun ot(
        date: String,
        minutes: Int,
        tier: RateTier? = null,
        source: TierSource? = null,
        toComp: Int = 0,
    ) = PayrollCalculator.DailyRecordLite(
        date = LocalDate.parse(date),
        type = RecordType.OT,
        durationMinutes = minutes,
        tier = tier,
        tierSource = source ?: if (tier != null) TierSource.MANUAL else TierSource.AUTO,
        toCompMinutes = toComp,
    )

    private fun leave(date: String, minutes: Int, type: LeaveType) =
        PayrollCalculator.DailyRecordLite(
            date = LocalDate.parse(date),
            type = RecordType.LEAVE,
            durationMinutes = minutes,
            leaveType = type,
        )

    private val weekdayOf: (LocalDate) -> RateTier = { RateTier.WEEKDAY }

    @Test
    fun `底薪折算 平时1_5倍 2_5小时`() {
        // 时薪 = 240000分 / 174 = 1379.3103…分；×1.5×150/60 = 5172.41… → 5172 分 = 51.72 元
        val cents = StandardPayrollStrategy.overtimeCents(base2400, RateTier.WEEKDAY, 150)
        assertEquals(5172L, cents)
    }

    @Test
    fun `底薪折算 周末2倍 8小时`() {
        val cents = StandardPayrollStrategy.overtimeCents(base2400, RateTier.WEEKEND, 480)
        // 1379.3103…×2×8 = 22068.965… → 22069
        assertEquals(22069L, cents)
    }

    @Test
    fun `底薪折算 法定3倍 1小时`() {
        val cents = StandardPayrollStrategy.overtimeCents(base2400, RateTier.STATUTORY, 60)
        // 1379.3103…×3 = 4137.93… → 4138
        assertEquals(4138L, cents)
    }

    @Test
    fun `手动单价模式 按档位单价计酬`() {
        val manual = SalaryConfig(
            mode = SalaryMode.MANUAL,
            baseSalaryCents = 0,
            includeBase = false,
            manualRatesCents = mapOf(RateTier.WEEKDAY to 2000L, RateTier.WEEKEND to 3000L, RateTier.STATUTORY to 4000L),
        )
        assertEquals(3000L, StandardPayrollStrategy.overtimeCents(manual, RateTier.WEEKDAY, 90))
        assertEquals(0L, StandardPayrollStrategy.overtimeCents(manual, RateTier.WEEKDAY, 0))
    }

    @Test
    fun `转调休部分不计加班费`() {
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = base2400,
                records = listOf(ot("2026-08-10", 120, toComp = 60)),
                tierOf = weekdayOf,
            )
        )
        // 只按 60 分钟计酬：1379.3103×1.5×1 = 2068.9655 → 2069
        assertEquals(60, out.paidOtMinutes)
        assertEquals(120, out.otMinutes)
        assertEquals(2069L, out.otPayCents)
    }

    @Test
    fun `请假扣款 病假半薪 事假全薪 零系数免扣`() {
        assertEquals(5517L, StandardPayrollStrategy.leaveDeductCents(base2400, LeaveType.SICK, 480))   // 日薪×0.5 = 5517.24
        assertEquals(11034L, StandardPayrollStrategy.leaveDeductCents(base2400, LeaveType.PERSONAL, 480))
        assertEquals(0L, StandardPayrollStrategy.leaveDeductCents(base2400, LeaveType.ANNUAL, 480))
        // 半天事假：日薪×0.5 → 5517
        assertEquals(5517L, StandardPayrollStrategy.leaveDeductCents(base2400, LeaveType.PERSONAL, 240))
    }

    @Test
    fun `汇总 收入与调休余额`() {
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = base2400,
                records = listOf(
                    ot("2026-08-10", 150),                       // 平时 5172
                    ot("2026-08-15", 120, toComp = 60),          // 计酬 60 分
                    leave("2026-08-12", 240, LeaveType.PERSONAL),// 扣 5517
                    leave("2026-08-13", 60, LeaveType.COMP),     // 消耗调休 60
                ),
                compAdjustments = listOf(PayrollCalculator.CompAdjustmentLite(-30)),
                tierOf = weekdayOf,
            )
        )
        val otPay = 5172L + 2069L
        assertEquals(otPay, out.otPayCents)
        assertEquals(5517L, out.leaveDeductCents)
        assertEquals(240_000L + otPay - 5517L, out.incomeCents)
        // 调休余额 = 60(转) − 60(调休请假) − 30(手动) = -30
        assertEquals(-30, out.compBalanceMinutes)
    }

    @Test
    fun `手动补改档位优先于自动判定`() {
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = base2400,
                records = listOf(
                    ot("2026-08-10", 60, tier = RateTier.STATUTORY),  // 周一强改为法定
                    ot("2026-08-11", 60),                             // 自动平时
                ),
                tierOf = weekdayOf,
            )
        )
        assertEquals(4138L + 2069L, out.otPayCents)
        // 明细倒序：08-11（自动平时）在前，08-10（手动法定）在后
        assertEquals(RateTier.WEEKDAY, out.breakdowns[0].tier)
        assertEquals(RateTier.STATUTORY, out.breakdowns[1].tier)
    }

    @Test
    fun `未计底薪时收入不含底薪`() {
        val manual = SalaryConfig(
            mode = SalaryMode.MANUAL,
            baseSalaryCents = 240_000,
            includeBase = false,
            manualRatesCents = mapOf(RateTier.WEEKDAY to 2000L),
        )
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = manual,
                records = listOf(ot("2026-08-10", 60)),
                tierOf = weekdayOf,
            )
        )
        assertEquals(0L, out.baseIncludedCents)
        // 手动单价 20 元/小时 × 1 小时 = 2000 分，底薪不计入
        assertEquals(2000L, out.incomeCents)
    }

    @Test
    fun `明细按日期倒序（最新在前）`() {
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = base2400,
                records = listOf(ot("2026-08-10", 60), ot("2026-08-12", 60)),
                tierOf = weekdayOf,
            )
        )
        // 明细页口径：最新日期在最上（输入顺序无关，均按日期倒序）
        assertEquals(LocalDate.parse("2026-08-12"), out.breakdowns[0].record.date)
        assertEquals(LocalDate.parse("2026-08-10"), out.breakdowns[1].record.date)
    }

    @Test
    fun `明细倒序对三种策略一致`() {
        val records = listOf(ot("2026-08-10", 60), ot("2026-08-12", 60))
        val standard = PayrollCalculator.summarize(
            PayrollCalculator.Input(salary = base2400, records = records, tierOf = weekdayOf)
        )
        val hourly = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = com.mdot.app.domain.model.SalaryConfig(
                    mode = com.mdot.app.domain.model.SalaryMode.MANUAL,
                    workSystem = com.mdot.app.domain.model.WorkSystem.HOURLY,
                    hourlyRatesCents = mapOf(com.mdot.app.domain.model.RateTier.WEEKDAY to 2500L),
                ),
                records = records,
                tierOf = weekdayOf,
            )
        )
        val comprehensive = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = com.mdot.app.domain.model.SalaryConfig(
                    mode = com.mdot.app.domain.model.SalaryMode.BASE,
                    workSystem = com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE,
                    baseSalaryCents = 240000L,
                ),
                records = records,
                tierOf = weekdayOf,
                standardMinutes = 60,
            )
        )
        listOf(standard, hourly, comprehensive).forEach { out ->
            assertEquals(LocalDate.parse("2026-08-12"), out.breakdowns.first().record.date)
        }
    }
}
