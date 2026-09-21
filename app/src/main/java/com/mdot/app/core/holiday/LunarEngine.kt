package com.mdot.app.core.holiday

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin

/**
 * 农历层（调研文档《节假日与农历数据源调研》§3：农历/节气只能本地算，永不进云端 JSON）。
 *
 * 调研文档原本推荐 `cn.6tail:lunar`，本项目**改为自带实现**，原因是零依赖：
 * 本仓库 CI 已两次栽在新增依赖的解析上（见 `app/build.gradle.kts` 的 Robolectric 长注释），
 * 而农历换算本质是查表 + 常量算术，几十行代码换掉一条供应链风险是划算的。
 * 算法本身经 `scripts/verify_lunar_table.py` / `scripts/verify_solar_terms.py` 对照官方
 * 放假通知验过（春节、端午、中秋全部命中，2033 闰十一月、2026 腊月小月也对）。
 *
 * ⚠️ 接入时不可省的三件事（调研文档 §3.4 / §6）：
 * 1. 时区固定 [ZONE_CN]——GB/T 33661 以北京时间为准，跟设备时区算会在朔日落在凌晨时差一天；
 * 2. 回归用例：2033 年置闰、2026 连续腊月小月（除夕是廿九不是三十）、闰月年份的农历节日；
 * 3. 月视图一次渲染 42 格，换算要缓存——见 [termCacheByYear] / [dayCache]。
 */
@Singleton
open class LunarEngine @Inject constructor() {

    private val lock = Any()

    /** 按年缓存的 24 节气表（年份 → 日期→节气名）。月视图翻页只会碰 2~3 个年，缓存 8 年绰绰有余 */
    private val termCacheByYear = LinkedHashMap<Int, Map<LocalDate, String>>()

    /** 按天缓存的农历月日。上限按「一年 366 天 + 跨年翻页」取，超出后按插入序淘汰最旧 */
    private val dayCache = LinkedHashMap<Long, LunarMonthDay>()

    /** 当日农历日文案（如「十五」「廿三」）；年份超出表范围返回 null */
    open fun lunarDayText(date: LocalDate): String? =
        lunarMonthDayOf(date)?.let { ChineseLunar.dayText(it.day) }

    /** 当日节气名（如「清明」，仅节气当天有值） */
    open fun solarTermOf(date: LocalDate): String? = termsOf(date.year)[date]

    /** 当日农历月日；[LunarMonthDay.isLastOfMonth] 支撑「除夕」这类「该月最后一天」规则 */
    open fun lunarMonthDayOf(date: LocalDate): LunarMonthDay? {
        val key = date.toEpochDay()
        synchronized(lock) { dayCache[key] }?.let { return it }
        val result = ChineseLunar.solarToLunar(date) ?: return null
        synchronized(lock) {
            if (dayCache.size >= DAY_CACHE_MAX) {
                dayCache.keys.firstOrNull()?.let { dayCache.remove(it) }
            }
            dayCache[key] = result
        }
        return result
    }

    /** 某公历年内的全部节气（日期 → 名）。小寒/大寒落在 1 月，与 2~12 月的其余 22 个一起覆盖全年无缺口 */
    private fun termsOf(year: Int): Map<LocalDate, String> = synchronized(lock) {
        termCacheByYear[year] ?: ChineseLunar.solarTermsOf(year).also { terms ->
            termCacheByYear[year] = terms
            if (termCacheByYear.size > TERM_CACHE_YEARS) {
                termCacheByYear.keys.firstOrNull()?.let(termCacheByYear::remove)
            }
        }
    }

    private companion object {
        const val DAY_CACHE_MAX = 512
        const val TERM_CACHE_YEARS = 8
    }
}

/** 农历月日；[isLastOfMonth] 支撑「除夕」这类「该月最后一天」规则（连续 5 年腊月为小月） */
data class LunarMonthDay(
    val month: Int,
    val day: Int,
    val isLastOfMonth: Boolean = false,
)

/**
 * 农历 / 节气算法本体：**无状态、纯 JVM、可纯 JUnit 测**（不碰 Android API，故不依赖 Robolectric）。
 *
 * 农历用流传最广的 1900–2100 `lunarInfo` 位表：一个 `Int` 编码一年，
 * 低 4 位 = 闰月月份（0 = 无闰月），bit16 = 闰月天数（1 = 30 天），
 * bit15..bit4 依次是正月到腊月的大小月（1 = 30 天）。全年天数 = 348 + 大月数 + 闰月天数。
 */
internal object ChineseLunar {

    private val ZONE_CN: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 1900-01-31 = 农历 1900 年正月初一，全部换算以此为原点 */
    private val BASE_EPOCH_DAY: Long = LocalDate.of(1900, 1, 31).toEpochDay()

    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2100

    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520, // 2100
    )

    /**
     * 农历日名（两个字，正好吃满 `DAY_LABEL_MAX_CHARS`）。
     * 刻意**不做「初一显示月名」的特例**——用户要的是「显示农历日期」，逐字面实现最好预测，
     * 也让每个格子的右槽含义始终一致（都是「日」）。
     */
    private val DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )

    fun dayText(day: Int): String = DAY_NAMES[day - 1]

    // ---- 农历表查询 ----

    /** 闰月月份，0 = 当年无闰月 */
    fun leapMonth(year: Int): Int = LUNAR_INFO[year - MIN_YEAR] and 0xF

    /** 闰月天数；无闰月为 0 */
    private fun leapDays(year: Int): Int = when {
        leapMonth(year) == 0 -> 0
        LUNAR_INFO[year - MIN_YEAR] and 0x10000 != 0 -> 30
        else -> 29
    }

    /** 某农历月天数（非闰月），1 = 正月…12 = 腊月 */
    private fun monthDays(year: Int, month: Int): Int =
        if (LUNAR_INFO[year - MIN_YEAR] and (0x10000 shr month) != 0) 30 else 29

    /** 某农历年总天数 = 12 个固定月各 29 天(348) + 大月补 1 + 闰月天数 */
    private fun yearDays(year: Int): Int {
        var sum = 348
        var bit = 0x8000
        while (bit > 0x8) {
            if (LUNAR_INFO[year - MIN_YEAR] and bit != 0) sum++
            bit = bit shr 1
        }
        return sum + leapDays(year)
    }

    /**
     * 公历 → 农历。超出 [MIN_YEAR]..[MAX_YEAR] 返回 null（表外无数据，不猜）。
     */
    fun solarToLunar(date: LocalDate): LunarMonthDay? {
        val year = date.year
        if (year < MIN_YEAR || year > MAX_YEAR) return null
        var offset = (date.toEpochDay() - BASE_EPOCH_DAY).toInt()
        if (offset < 0) return null

        // 逐年扣减，定位农历年
        var cursor = MIN_YEAR
        var yearLen = 0
        while (cursor < MAX_YEAR + 1 && offset > 0) {
            yearLen = yearDays(cursor)
            offset -= yearLen
            cursor++
        }
        if (offset < 0) {
            offset += yearLen
            cursor--
        }
        val lunarYear = cursor

        val leap = leapMonth(lunarYear)
        var isLeap = false
        var month = 1
        var monthLen = 0
        while (month < 13 && offset > 0) {
            if (leap > 0 && month == leap + 1 && !isLeap) {
                // 走到闰月：退回一个月号，改为按闰月长度推进
                month--
                isLeap = true
                monthLen = leapDays(lunarYear)
            } else {
                monthLen = monthDays(lunarYear, month)
            }
            if (isLeap && month == leap + 1) isLeap = false
            offset -= monthLen
            month++
        }
        // 恰好落在月首：上面把 offset 减到 0 的那一步可能跨越了闰月边界，需要回退
        if (offset == 0 && leap > 0 && month == leap + 1) {
            if (isLeap) isLeap = false else { isLeap = true; month-- }
        }
        if (offset < 0) {
            offset += monthLen
            month--
        }

        val day = offset + 1
        val daysInThisMonth = if (isLeap) leapDays(lunarYear) else monthDays(lunarYear, month)
        return LunarMonthDay(
            month = month,
            day = day,
            isLastOfMonth = day == daysInThisMonth,
        )
    }

    // ---- 节气（Meeus 太阳视黄经 + 牛顿迭代）----

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5

    /** 24 节气对应的太阳视黄经（度）；索引 0 = 立春，1 = 雨水 … 23 = 大寒 */
    private val TERM_LONGITUDE = doubleArrayOf(
        315.0, 330.0, 345.0, 0.0, 15.0, 30.0, 45.0, 60.0, 75.0, 90.0, 105.0, 120.0,
        135.0, 150.0, 165.0, 180.0, 195.0, 210.0, 225.0, 240.0, 255.0, 270.0, 285.0, 300.0,
    )

    private val TERM_NAMES = arrayOf(
        "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至", "小暑", "大暑",
        "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至", "小寒", "大寒",
    )

    /** 某公历年的 24 个节气：日期 → 名。小寒/大寒落在 1 月，其余 22 个在 2~12 月，覆盖全年 */
    fun solarTermsOf(year: Int): Map<LocalDate, String> {
        val out = LinkedHashMap<LocalDate, String>(24)
        for (i in TERM_NAMES.indices) {
            val at = termDate(year, i)
            // 同一天撞两个节气（百年不遇）时保留先到的，不覆盖
            if (!out.containsKey(at)) out[at] = TERM_NAMES[i]
        }
        return out
    }

    /** 某年第 [index] 个节气落在北京的哪一天 */
    private fun termDate(year: Int, index: Int): LocalDate {
        val target = TERM_LONGITUDE[index]
        // 初值：太阳黄经 0°（春分）约在年内第 79 天，其余按 0.9856°/天外推
        var jd = julianDayAtUtMidnight(LocalDate.of(year, 1, 1)) +
            (79.0 + target / MEAN_DEGREE_PER_DAY) % TROPICAL_YEAR_DAYS
        for (i in 0 until NEWTON_ITERATIONS) {
            var diff = (target - apparentSolarLongitude(jd) + 180.0) % 360.0
            if (diff < 0) diff += 360.0
            diff -= 180.0
            if (abs(diff) < 1e-7) break
            jd += diff / MEAN_DEGREE_PER_DAY
        }
        // 迭代用的是力学时（TT），转世界时需减 ΔT；不修正会在交节贴近午夜时差一天
        jd -= deltaTSeconds(year) / 86400.0
        val epochSecond = ((jd - UNIX_EPOCH_JD) * 86400.0).toLong()
        return Instant.ofEpochSecond(epochSecond).atZone(ZONE_CN).toLocalDate()
    }

    /** 公历日期（UT 0 时）→ 儒略日 */
    private fun julianDayAtUtMidnight(date: LocalDate): Double =
        date.toEpochDay().toDouble() + UNIX_EPOCH_JD

    /**
     * 太阳视黄经（度，0..360），Meeus《Astronomical Algorithms》低精度式。
     * 精度约 0.01°（≈ 15 分钟），定「哪一天交节」足够；再高的精度要上 VSOP87，不值得。
     */
    private fun apparentSolarLongitude(jd: Double): Double {
        val t = (jd - J2000) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        var lon = (l0 + c - 0.00569 - 0.00478 * sin(omega)) % 360.0
        if (lon < 0) lon += 360.0
        return lon
    }

    /** ΔT = TT − UT，ESP 近似式；2020 年代约 69 秒 */
    private fun deltaTSeconds(year: Int): Double {
        val t = year - 2000.0
        return if (year >= 2005) 62.92 + 0.32217 * t + 0.005589 * t * t
        else 63.86 + 0.3345 * t
    }

    private const val MEAN_DEGREE_PER_DAY = 0.9856473
    private const val TROPICAL_YEAR_DAYS = 365.2422
    private const val NEWTON_ITERATIONS = 8
}
