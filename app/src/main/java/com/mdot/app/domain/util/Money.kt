package com.mdot.app.domain.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** 金额工具：全程「分」（Long），仅在展示层转「元」 */
object Money {

    /** 分 → "5,586.20"（含千分位） */
    fun yuanText(cents: Long): String = String.format(Locale.US, "%,.2f", cents / 100.0)

    /** 分 → "¥5,586.20" */
    fun yuanWithSign(cents: Long): String = "¥${yuanText(cents)}"

    /**
     * 分 → 去尾零元文本（记月条目用）：0→"0"，230000→"2,300"，951730→"951.73"，-10→"-0.1"。
     * **带千分位**：金额上万是常态（12345.6 读不出来），与 [yuanText] 一致。
     */
    fun yuanTrimText(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val text = when {
            abs % 100L == 0L -> String.format(Locale.US, "%,d", abs / 100)
            abs % 10L == 0L -> String.format(Locale.US, "%,.1f", abs / 100.0)
            else -> String.format(Locale.US, "%,.2f", abs / 100.0)
        }
        return if (cents < 0) "-$text" else text
    }

    /** 基点 → 百分点文本：1050 → "10.5"，800 → "8"（去尾零；比例以基点存，1 基点 = 0.01%） */
    fun ratePercentText(bp: Int): String =
        if (bp % 100 == 0) {
            (bp / 100).toString()
        } else {
            String.format(Locale.US, "%.2f", bp / 100.0).trimEnd('0').trimEnd('.')
        }

    /**
     * 天数 → 文本（记月「按日计算」的推导依据用）：22 → "22"，21.5 → "21.5"，21.25 → "21.25"。
     *
     * 半天请假是常态（用户 2026-10-08 定「算 0.5 天」），所以**必须保留 .5**；
     * 但整数天不该显示成 "22.0"（像精度溢出的机器话）。最多两位小数，够表达 1/4 天。
     */
    fun dayCountText(days: Double): String = when {
        days == kotlin.math.floor(days) -> days.toLong().toString()
        else -> String.format(Locale.US, "%.2f", days).trimEnd('0').trimEnd('.')
    }

    /** 元字符串 → 分；非法输入返回 null。支持 "5000" / "5000.5" / "5,000.50"；负数拒绝（金额域非负，13 文档 B4-04） */
    fun parseYuanToCents(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return runCatching {
            val bd = BigDecimal(cleaned)
            if (bd.signum() < 0) return null
            bd.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).toLong()
        }.getOrNull()
    }

    /** 时薪（分/小时 → 元文本），仅展示用：底薪 ÷ 21.75 ÷ 8，保留 2 位。
     *  复用 [com.mdot.app.domain.PayrollCalculator.HOURS_PER_MONTH]（B4-05：原双写 "174" 有口径分叉风险） */
    fun hourlyRateText(baseSalaryCents: Long): String =
        yuanText(
            BigDecimal(baseSalaryCents)
                .divide(com.mdot.app.domain.PayrollCalculator.HOURS_PER_MONTH, 2, RoundingMode.HALF_UP)
                .toLong()
        )

    /** 倍率显示：1.5 / 2 / 3.0 → "1.5" "2" "3" */
    fun multiplierText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
