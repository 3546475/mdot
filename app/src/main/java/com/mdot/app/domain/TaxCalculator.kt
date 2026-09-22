package com.mdot.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 个人所得税估算（**累计预扣法**，国家税务总局公告 2018 年第 61 号）。
 *
 * 本期应预扣预缴税额 = (累计预扣预缴应纳税所得额 × 预扣率 − 速算扣除数) − 累计减免税额 − 累计已预扣预缴税额
 * 累计预扣预缴应纳税所得额 = 累计收入 − 累计免税收入 − 累计减除费用 − 累计专项扣除 − 累计专项附加扣除 − 累计其他扣除
 * 累计减除费用 = 5000 元/月 × 当年截至本月的计税月份数
 *
 * ⚠️ 这是**估算**：各单位的专项扣除口径、年终奖计税方式、减免税额都可能不同，
 * 页面必须写明「仅供参考，以单位实际代扣为准」（docs/20 P3-5）。
 * 金额全程「分」（Long），逐笔 HALF_UP 到分（硬规则 1）。
 */
object TaxCalculator {

    /** 基本减除费用：5000 元/月 */
    const val BASIC_DEDUCTION_MONTHLY_CENTS = 500_000L

    /** 预扣率表一（累计应纳税所得额，单位：分） */
    private data class Bracket(
        val upperCents: Long,
        val ratePercent: Int,
        val quickDeductionCents: Long,
    )

    private val BRACKETS = listOf(
        Bracket(3_600_000L, 3, 0L),              // ≤ 36,000
        Bracket(14_400_000L, 10, 252_000L),      // ≤ 144,000，速算扣除 2,520
        Bracket(30_000_000L, 20, 1_692_000L),    // ≤ 300,000，16,920
        Bracket(42_000_000L, 25, 3_192_000L),    // ≤ 420,000，31,920
        Bracket(66_000_000L, 30, 5_292_000L),    // ≤ 660,000，52,920
        Bracket(96_000_000L, 35, 8_592_000L),    // ≤ 960,000，85,920
        Bracket(Long.MAX_VALUE, 45, 18_192_000L), // > 960,000，181,920
    )

    data class Input(
        /** 本年累计收入（1 月至本月） */
        val cumulativeIncomeCents: Long,
        /** 本年累计专项扣除（社保 + 公积金个人部分） */
        val cumulativeSpecialCents: Long,
        /** 本年累计专项附加扣除（月合计 × 月数） */
        val cumulativeAdditionalCents: Long,
        /** 本年累计已预扣预缴税额（1 月至上月，从记月历史取） */
        val cumulativePaidCents: Long,
        /** 当年截至本月的计税月份数（1–12） */
        val months: Int,
    )

    data class Output(
        /** 累计预扣预缴应纳税所得额（不足 0 按 0） */
        val taxableCents: Long,
        val ratePercent: Int,
        val quickDeductionCents: Long,
        /** 累计应纳税额 */
        val cumulativeTaxCents: Long,
        /** 本期应预扣预缴税额（不足 0 按 0：多缴不自动退税，由年度汇算处理） */
        val currentTaxCents: Long,
    )

    fun estimate(input: Input): Output {
        val months = input.months.coerceIn(1, 12)
        val taxable = (
            input.cumulativeIncomeCents -
                BASIC_DEDUCTION_MONTHLY_CENTS * months -
                input.cumulativeSpecialCents -
                input.cumulativeAdditionalCents
            ).coerceAtLeast(0L)

        val bracket = BRACKETS.first { taxable <= it.upperCents }
        val cumulativeTax = (
            BigDecimal(taxable)
                .multiply(BigDecimal(bracket.ratePercent))
                .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
                .toLong() - bracket.quickDeductionCents
            ).coerceAtLeast(0L)
        val current = (cumulativeTax - input.cumulativePaidCents).coerceAtLeast(0L)

        return Output(
            taxableCents = taxable,
            ratePercent = bracket.ratePercent,
            quickDeductionCents = bracket.quickDeductionCents,
            cumulativeTaxCents = cumulativeTax,
            currentTaxCents = current,
        )
    }
}
