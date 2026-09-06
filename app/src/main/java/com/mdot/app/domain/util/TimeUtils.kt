package com.mdot.app.domain.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 时间与时长展示工具（03 文档 §9：时长 <1h 用分钟，≥1h 一位小数） */
object TimeUtils {

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val MD = DateTimeFormatter.ofPattern("M/d")
    private val MD_CN = DateTimeFormatter.ofPattern("M月d日")
    private val YM_CN = DateTimeFormatter.ofPattern("yyyy年M月")

    fun isoDate(date: LocalDate): String = date.format(ISO)

    fun parseIsoDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    fun md(date: LocalDate): String = date.format(MD)

    fun mdCn(date: LocalDate): String = date.format(MD_CN)

    fun ymCn(month: YearMonth): String = month.format(YM_CN)

    private val WEEKDAY_NAMES =
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayCn(date: LocalDate): String = WEEKDAY_NAMES[date.dayOfWeek.value - 1]

    /** "今天 周三 08/29" 风格 */
    fun dateLabel(date: LocalDate, today: LocalDate): String {
        val prefix = when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> null
        }
        val wd = weekdayCn(date)
        return if (prefix != null) "$prefix $wd" else "$wd ${md(date)}"
    }

    /** 时长展示：<1h → "45分"；≥1h → "2.5小时" / "1小时" */
    fun prettyDuration(minutes: Int): String = when {
        minutes < 60 -> "${minutes}分"
        minutes % 60 == 0 -> "${minutes / 60}小时"
        else -> {
            val text = String.format(Locale.US, "%.1f", minutes / 60.0)
            "${text}小时"
        }
    }

    /** 小数小时文本（大数字/表格用）："48.5" / "48" */
    fun hoursDecimal(minutes: Int): String {
        if (minutes <= 0) return "0"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) h.toString() else String.format(Locale.US, "%.1f", minutes / 60.0)
    }

    /** 分钟数 → 0.5 步进的合法值（截断到 30 分钟） */
    fun snapToHalfHour(minutes: Int): Int = (minutes / 30) * 30
}
