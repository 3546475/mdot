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

    /** 元字符串 → 分；非法输入返回 null。支持 "5000" / "5000.5" / "5,000.50" */
    fun parseYuanToCents(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return runCatching {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).toLong()
        }.getOrNull()
    }

    /** 时薪（分/小时 → 元文本），仅展示用：底薪 ÷ 21.75 ÷ 8，保留 2 位 */
    fun hourlyRateText(baseSalaryCents: Long): String =
        yuanText(BigDecimal(baseSalaryCents).divide(BigDecimal("174"), 2, RoundingMode.HALF_UP).toLong())

    /** 倍率显示：1.5 / 2 / 3.0 → "1.5" "2" "3" */
    fun multiplierText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
