package com.mdot.app.domain

import com.mdot.app.domain.model.SalaryConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 社保 / 公积金自动计算（docs/20「自动 > 选项 > 手动输入」）。
 *
 * 口径：金额 = 缴费基数 × 个人比例；**基数 0 = 跟随底薪**（自动，多数人如此），
 * 比例 0 = 不自动算（对应行保持手填）。比例以**基点**存（1 基点 = 0.01%，1050 = 10.5%）。
 */
class InsuranceTest {

    private fun salary(
        base: Long = 500_000, // ¥5000
        socialRate: Int = 1_050, // 10.5%
        socialBase: Long = 0,
        fundRate: Int = 1_200, // 12%
        fundBase: Long = 0,
    ) = SalaryConfig(
        baseSalaryCents = base,
        socialInsuranceRateBp = socialRate,
        socialInsuranceBaseCents = socialBase,
        housingFundRateBp = fundRate,
        housingFundBaseCents = fundBase,
    )

    @Test
    fun `基数跟随底薪时按底薪算`() {
        val s = salary()
        assertEquals(500_000, PayrollCalculator.socialInsuranceBaseCents(s))
        assertEquals(52_500, PayrollCalculator.socialInsuranceCents(s)) // 5000 × 10.5% = 525
        assertEquals(60_000, PayrollCalculator.housingFundCents(s))     // 5000 × 12% = 600
    }

    @Test
    fun `单独填了基数就用单独的`() {
        val s = salary(socialBase = 400_000, fundBase = 300_000)
        assertEquals(400_000, PayrollCalculator.socialInsuranceBaseCents(s))
        assertEquals(42_000, PayrollCalculator.socialInsuranceCents(s)) // 4000 × 10.5%
        assertEquals(36_000, PayrollCalculator.housingFundCents(s))     // 3000 × 12%
    }

    @Test
    fun `比例 0 表示不自动算`() {
        val s = salary(socialRate = 0, fundRate = 0)
        assertEquals(0, PayrollCalculator.socialInsuranceCents(s))
        assertEquals(0, PayrollCalculator.housingFundCents(s))
    }

    @Test
    fun `没有底薪且未填基数时为 0`() {
        val s = salary(base = 0)
        assertEquals(0, PayrollCalculator.socialInsuranceCents(s))
        assertEquals(0, PayrollCalculator.housingFundCents(s))
    }

    @Test
    fun `除不尽时 HALF_UP 到分`() {
        // 1234.56 × 10.5% = 129.6288 元 = 12962.88 分 → 12963
        val s = salary(base = 123_456)
        assertEquals(12_963, PayrollCalculator.socialInsuranceCents(s))
    }

    @Test
    fun `八个百分点_仅养老的常见选法`() {
        val s = salary(socialRate = 800)
        assertEquals(40_000, PayrollCalculator.socialInsuranceCents(s)) // 5000 × 8%
    }
}
