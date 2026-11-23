package com.mdot.app.domain

import com.mdot.app.domain.model.OtClockBand
import com.mdot.app.domain.model.OtDurationBand
import com.mdot.app.domain.model.OtPricingConfig
import com.mdot.app.domain.model.OtPricingIssue
import com.mdot.app.domain.model.OtPricingMode
import com.mdot.app.domain.model.OtRateUnit
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.model.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OtPricingCalculatorTest {

    /** 底薪 8700 => 基准时薪 50 元（5000 分） */
    private val salary = SalaryConfig(
        mode = SalaryMode.BASE,
        baseSalaryCents = 870_000,
        multipliers = mapOf(
            RateTier.WEEKDAY to 1.5,
            RateTier.WEEKEND to 2.0,
            RateTier.STATUTORY to 3.0,
        ),
    )

    private fun config(
        mode: OtPricingMode,
        durationBands: List<OtDurationBand> = OtPricingConfig.DEFAULT_DURATION_BANDS,
        clockBands: List<OtClockBand> = OtPricingConfig.DEFAULT_CLOCK_BANDS,
        tiers: Set<RateTier> = setOf(RateTier.WEEKDAY),
        unit: OtRateUnit = OtRateUnit.MULTIPLIER,
    ) = OtPricingConfig(
        mode = mode,
        rateUnit = unit,
        tiers = tiers,
        durationBands = durationBands,
        clockBands = clockBands,
    )

    private fun SalaryConfig.withOt(config: OtPricingConfig) = copy(otPricing = config)

    @Test
    fun `未启用时整段按单档`() {
        val result = OtPricingCalculator.compute(salary, RateTier.WEEKDAY, 180, null)
        assertEquals(22_500, result.cents) // 50 × 1.5 × 3
        assertFalse(result.applied)
        assertEquals(1, result.segments.size)
        assertTrue(result.segments.single().flat)
    }

    @Test
    fun `时长阶梯 2 段`() {
        val s = salary.withOt(config(OtPricingMode.DURATION_BANDS))
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, null)
        assertEquals(25_000, result.cents) // 2h×1.5 + 1h×2.0
        assertTrue(result.applied)
        assertEquals(2, result.segments.size)
        assertEquals(15_000, result.segments[0].cents)
        assertEquals(10_000, result.segments[1].cents)
    }

    @Test
    fun `时长阶梯只到第二档上限`() {
        val s = salary.withOt(config(OtPricingMode.DURATION_BANDS))
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 240, null)
        assertEquals(35_000, result.cents) // 2h×1.5 + 2h×2.0
    }

    @Test
    fun `钟点时段按实际开始时间切分`() {
        val s = salary.withOt(config(OtPricingMode.CLOCK_BANDS))
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, 18 * 60)
        assertEquals(27_500, result.cents) // 18–19 1h×1.5 + 19–21 2h×2.0
        assertEquals(2, result.segments.size)
        assertEquals(60, result.segments[0].minutes)
        assertEquals(120, result.segments[1].minutes)
    }

    @Test
    fun `钟点时段缺开始时间按单档`() {
        val s = salary.withOt(config(OtPricingMode.CLOCK_BANDS))
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, null)
        assertEquals(22_500, result.cents)
        assertFalse(result.applied)
    }

    @Test
    fun `钟点时段空洞按单档补`() {
        val s = salary.withOt(
            config(
                OtPricingMode.CLOCK_BANDS,
                clockBands = listOf(
                    OtClockBand(17 * 60, 18 * 60, multiplier = 1.5),
                    OtClockBand(20 * 60, 21 * 60, multiplier = 2.0),
                ),
            )
        )
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 240, 17 * 60)
        assertEquals(32_500, result.cents) // 1h×1.5 + 2h 单档×1.5 + 1h×2.0
        assertEquals(3, result.segments.size)
        assertTrue(result.segments[1].flat)
    }

    @Test
    fun `钟点时段跨零点`() {
        val s = salary.withOt(
            config(
                OtPricingMode.CLOCK_BANDS,
                clockBands = listOf(OtClockBand(22 * 60, 2 * 60, multiplier = 2.0)),
            )
        )
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, 23 * 60)
        assertEquals(30_000, result.cents) // 23–24 1h + 00–02 2h，均 2.0
    }

    @Test
    fun `未选中的档位仍按单档`() {
        val s = salary.withOt(config(OtPricingMode.DURATION_BANDS, tiers = setOf(RateTier.WEEKDAY)))
        val result = OtPricingCalculator.compute(s, RateTier.WEEKEND, 180, null)
        assertEquals(30_000, result.cents) // 50 × 2.0 × 3
        assertFalse(result.applied)
    }

    @Test
    fun `转调休按后段计价`() {
        val s = salary.withOt(config(OtPricingMode.DURATION_BANDS))
        val paid = OtPricingCalculator.computeRange(s, RateTier.WEEKDAY, 180, null, 0, 120)
        val comp = OtPricingCalculator.computeRange(s, RateTier.WEEKDAY, 180, null, 120, 180)
        assertEquals(15_000, paid.cents)
        assertEquals(10_000, comp.cents)
        assertEquals(25_000, paid.cents + comp.cents)
    }

    @Test
    fun `固定单价不乘档位倍率`() {
        val s = salary.withOt(
            config(
                OtPricingMode.DURATION_BANDS,
                unit = OtRateUnit.FIXED_HOURLY,
                durationBands = listOf(
                    OtDurationBand(120, multiplier = 1.0, hourlyCents = 3_000),
                    OtDurationBand(null, multiplier = 1.0, hourlyCents = 4_000),
                ),
            )
        )
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, null)
        assertEquals(10_000, result.cents) // 2h×30 + 1h×40
    }

    @Test
    fun `手动单价模式倍率以该档单价为基准`() {
        val s = SalaryConfig(
            mode = SalaryMode.MANUAL,
            manualRatesCents = mapOf(RateTier.WEEKDAY to 4_000),
            otPricing = config(OtPricingMode.DURATION_BANDS),
        )
        val result = OtPricingCalculator.compute(s, RateTier.WEEKDAY, 180, null)
        assertEquals(20_000, result.cents) // 2h×40×1.5 + 1h×40×2.0
    }

    @Test
    fun `汇总按时长阶梯计算加班费`() {
        val s = salary.withOt(config(OtPricingMode.DURATION_BANDS))
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = s,
                records = listOf(
                    PayrollCalculator.DailyRecordLite(
                        date = LocalDate.parse("2026-08-10"),
                        type = RecordType.OT,
                        durationMinutes = 180,
                        tier = RateTier.WEEKDAY,
                        tierSource = TierSource.AUTO,
                    )
                ),
                tierOf = { RateTier.WEEKDAY },
            )
        )
        assertEquals(25_000L, out.otPayCents)
        assertEquals(2, out.breakdowns.single().segments.size)
    }

    @Test
    fun `汇总按记录开始时间切钟点时段`() {
        val s = salary.withOt(config(OtPricingMode.CLOCK_BANDS))
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary = s,
                records = listOf(
                    PayrollCalculator.DailyRecordLite(
                        date = LocalDate.parse("2026-08-10"),
                        type = RecordType.OT,
                        durationMinutes = 180,
                        tier = RateTier.WEEKDAY,
                        tierSource = TierSource.AUTO,
                        otStartMinute = 18 * 60,
                    )
                ),
                tierOf = { RateTier.WEEKDAY },
            )
        )
        assertEquals(27_500L, out.otPayCents)
    }

    @Test
    fun `时长阶梯校验`() {
        val ok = config(OtPricingMode.DURATION_BANDS)
        assertNull(ok.validate())
        val overlap = ok.copy(
            durationBands = listOf(
                OtDurationBand(180, 1.5),
                OtDurationBand(120, 2.0),
            )
        )
        assertEquals(OtPricingIssue.DURATION_BAND_INVALID, overlap.validate())
        val noLastOpen = ok.copy(durationBands = listOf(OtDurationBand(120, 1.5)))
        assertEquals(OtPricingIssue.TOO_FEW_DURATION_BANDS, noLastOpen.validate())
    }

    @Test
    fun `钟点时段校验重叠`() {
        val overlapping = config(
            OtPricingMode.CLOCK_BANDS,
            clockBands = listOf(
                OtClockBand(17 * 60, 19 * 60, 1.5),
                OtClockBand(18 * 60, 20 * 60, 2.0),
            ),
        )
        assertEquals(OtPricingIssue.CLOCK_BAND_OVERLAP, overlapping.validate())
    }

    @Test
    fun `钟点跨零点不算重叠`() {
        val config = config(
            OtPricingMode.CLOCK_BANDS,
            clockBands = listOf(
                OtClockBand(22 * 60, 2 * 60, 2.0),
                OtClockBand(17 * 60, 19 * 60, 1.5),
            ),
        )
        assertNull(config.validate())
    }
}
