package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 个税累计预扣法（docs/20 P3-5）。金额全程「分」。
 *
 * 用例覆盖：各档税率、档位边界、减除费用与专项扣除、累计已预缴的抵扣、多缴不退税（汇算处理）。
 */
class TaxCalculatorTest {

    private fun input(
        income: Long,
        special: Long = 0,
        additional: Long = 0,
        paid: Long = 0,
        months: Int = 1,
    ) = TaxCalculator.Input(
        cumulativeIncomeCents = income,
        cumulativeSpecialCents = special,
        cumulativeAdditionalCents = additional,
        cumulativePaidCents = paid,
        months = months,
    )

    @Test
    fun `月薪一万一个月_应纳税所得额 5000 按 3 个点`() {
        // 10000 − 5000×1 = 5000 → 3% = 150 元
        val out = TaxCalculator.estimate(input(income = 1_000_000, months = 1))
        assertEquals(500_000, out.taxableCents)
        assertEquals(3, out.ratePercent)
        assertEquals(15_000, out.currentTaxCents)
    }

    @Test
    fun `累计九个月十万_落在 10 个点档并扣速算扣除数`() {
        // 100000 − 5000×9 = 55000 → 10% − 2520 = 2980 元
        val out = TaxCalculator.estimate(input(income = 10_000_000, months = 9))
        assertEquals(5_500_000, out.taxableCents)
        assertEquals(10, out.ratePercent)
        assertEquals(252_000, out.quickDeductionCents)
        assertEquals(298_000, out.cumulativeTaxCents)
    }

    @Test
    fun `档位边界 36000 仍按 3 个点_多一分进 10 个点`() {
        // 收入 = 36000 + 5000 = 41000 → 应纳税所得额恰为 36000
        val atEdge = TaxCalculator.estimate(input(income = 4_100_000, months = 1))
        assertEquals(3, atEdge.ratePercent)
        assertEquals(108_000, atEdge.currentTaxCents) // 36000 × 3% = 1080

        // 再多一元：应纳税所得额 36,001 → 10% − 2520 = 1080.10
        val overEdge = TaxCalculator.estimate(input(income = 4_100_100, months = 1))
        assertEquals(3_600_100, overEdge.taxableCents)
        assertEquals(10, overEdge.ratePercent)
        assertEquals(108_010, overEdge.currentTaxCents)
    }

    @Test
    fun `专项扣除与专项附加扣除先减掉再算`() {
        // 10000 − 5000 − 1500(社保公积金) − 2000(附加) = 1500 → 3% = 45 元
        val out = TaxCalculator.estimate(
            input(income = 1_000_000, special = 150_000, additional = 200_000, months = 1),
        )
        assertEquals(150_000, out.taxableCents)
        assertEquals(4_500, out.currentTaxCents)
    }

    @Test
    fun `累计已预缴要抵掉`() {
        // 9 月累计应纳税额 2980 元，1–8 月已预缴 2400 元 → 本期 580 元
        val out = TaxCalculator.estimate(
            input(income = 10_000_000, months = 9, paid = 240_000),
        )
        assertEquals(298_000, out.cumulativeTaxCents)
        assertEquals(58_000, out.currentTaxCents)
    }

    @Test
    fun `已预缴超过应缴时本期为 0_不出现负数`() {
        val out = TaxCalculator.estimate(input(income = 1_000_000, months = 1, paid = 99_999))
        assertEquals(0, out.currentTaxCents)
    }

    @Test
    fun `收入低于减除费用时应纳税所得额为 0`() {
        val out = TaxCalculator.estimate(input(income = 400_000, months = 1)) // 4000 < 5000
        assertEquals(0, out.taxableCents)
        assertEquals(0, out.currentTaxCents)
    }

    @Test
    fun `高收入进 45 个点档`() {
        // 累计 120 万，9 个月：1200000 − 45000 = 1155000 → 45% − 181920 = 337830
        val out = TaxCalculator.estimate(input(income = 120_000_000, months = 9))
        assertEquals(45, out.ratePercent)
        assertEquals(33_783_000, out.cumulativeTaxCents)
    }

    @Test
    fun `月份数夹取在 1 到 12`() {
        val zero = TaxCalculator.estimate(input(income = 1_000_000, months = 0))
        assertEquals(500_000, zero.taxableCents) // 按 1 个月算，不是 0 个月
        val thirteen = TaxCalculator.estimate(input(income = 10_000_000, months = 13))
        assertEquals(10, thirteen.ratePercent) // 按 12 个月算
    }
}
