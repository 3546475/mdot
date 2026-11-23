package com.mdot.app.domain.model

import kotlinx.serialization.Serializable

/** 加班计价方式：单档（现行）/ 时长阶梯 / 钟点时段 */
@Serializable
enum class OtPricingMode {
    FLAT,
    DURATION_BANDS,
    CLOCK_BANDS,
}

/** 分段费率的表达方式：倍率（相对基准时薪）或固定每小时单价 */
@Serializable
enum class OtRateUnit {
    MULTIPLIER,
    FIXED_HOURLY,
}

/**
 * 时长阶梯的一段。
 * [endMinute] 为累计加班时长上限（分钟，从开始加班算起）；null = 最后一段「以上」。
 */
@Serializable
data class OtDurationBand(
    val endMinute: Int? = null,
    val multiplier: Double = 1.5,
    val hourlyCents: Long = 0,
)

/**
 * 钟点时段的一段，按一天内的分钟数表示（0..1440）。
 * [endMinute] <= [startMinute] 表示跨零点：22:00–02:00 => start=1320, end=120。
 * endMinute = 1440 表示 24:00。
 */
@Serializable
data class OtClockBand(
    val startMinute: Int = 17 * 60,
    val endMinute: Int = 19 * 60,
    val multiplier: Double = 1.5,
    val hourlyCents: Long = 0,
)

/**
 * 加班分段计价配置。挂在 [SalaryConfig.otPricing] 上，老配置缺省即 [OtPricingMode.FLAT]。
 *
 * 约定：
 * - 只有 [tiers] 里的档位参与分段；其余档位继续按单档倍率/单价计算。
 * - 倍率是**最终倍率**，替代该档的单档倍率，不叠乘。
 * - 钟点时段未覆盖的时间按单档计算；时段之间不允许重叠。
 */
@Serializable
data class OtPricingConfig(
    val mode: OtPricingMode = OtPricingMode.FLAT,
    val rateUnit: OtRateUnit = OtRateUnit.MULTIPLIER,
    val tiers: Set<RateTier> = setOf(RateTier.WEEKDAY),
    val durationBands: List<OtDurationBand> = DEFAULT_DURATION_BANDS,
    val clockBands: List<OtClockBand> = DEFAULT_CLOCK_BANDS,
    /** 记录弹层在钟点时段模式下的开始时间预填值（分钟，0..1439） */
    val defaultStartMinute: Int = 17 * 60,
) {
    val isEnabled: Boolean get() = mode != OtPricingMode.FLAT && tiers.isNotEmpty()

    fun appliesTo(tier: RateTier): Boolean = isEnabled && tier in tiers

    companion object {
        val DEFAULT_DURATION_BANDS = listOf(
            OtDurationBand(endMinute = 120, multiplier = 1.5),
            OtDurationBand(endMinute = null, multiplier = 2.0),
        )
        val DEFAULT_CLOCK_BANDS = listOf(
            OtClockBand(startMinute = 17 * 60, endMinute = 19 * 60, multiplier = 1.5),
            OtClockBand(startMinute = 19 * 60, endMinute = 24 * 60, multiplier = 2.0),
        )
    }
}

/** 配置校验失败的类别。UI 负责映射成文案；domain 不持有 Android 资源。 */
enum class OtPricingIssue {
    NO_TIER,
    TOO_FEW_DURATION_BANDS,
    TOO_MANY_DURATION_BANDS,
    DURATION_BAND_INVALID,
    CLOCK_BAND_INVALID,
    CLOCK_BAND_OVERLAP,
    DEFAULT_START_INVALID,
}

/** 校验当前模式真正用到的部分；未启用或未选中档位时不做限制。 */
fun OtPricingConfig.validate(): OtPricingIssue? {
    if (mode == OtPricingMode.FLAT) return null
    if (tiers.isEmpty()) return OtPricingIssue.NO_TIER
    if (defaultStartMinute !in 0 until 24 * 60) return OtPricingIssue.DEFAULT_START_INVALID
    return when (mode) {
        OtPricingMode.FLAT -> null
        OtPricingMode.DURATION_BANDS -> validateDurationBands()
        OtPricingMode.CLOCK_BANDS -> validateClockBands()
    }
}

private fun OtPricingConfig.validateDurationBands(): OtPricingIssue? {
    if (durationBands.size < 2) return OtPricingIssue.TOO_FEW_DURATION_BANDS
    if (durationBands.size > 5) return OtPricingIssue.TOO_MANY_DURATION_BANDS
    var prev = 0
    durationBands.forEachIndexed { index, band ->
        val last = index == durationBands.lastIndex
        if (last != (band.endMinute == null)) return OtPricingIssue.DURATION_BAND_INVALID
        band.endMinute?.let {
            if (it <= prev || it > 24 * 60) return OtPricingIssue.DURATION_BAND_INVALID
            prev = it
        }
        if (rateUnit == OtRateUnit.MULTIPLIER) {
            if (band.multiplier !in 0.1..10.0) return OtPricingIssue.DURATION_BAND_INVALID
        } else if (band.hourlyCents <= 0) {
            return OtPricingIssue.DURATION_BAND_INVALID
        }
    }
    return null
}

private fun OtPricingConfig.validateClockBands(): OtPricingIssue? {
    if (clockBands.isEmpty() || clockBands.size > 6) return OtPricingIssue.CLOCK_BAND_INVALID
    val occupied = BooleanArray(24 * 60)
    clockBands.forEach { band ->
        if (band.startMinute !in 0 until 24 * 60) return OtPricingIssue.CLOCK_BAND_INVALID
        if (band.endMinute !in 0..24 * 60) return OtPricingIssue.CLOCK_BAND_INVALID
        if (band.startMinute == band.endMinute) return OtPricingIssue.CLOCK_BAND_INVALID
        if (rateUnit == OtRateUnit.MULTIPLIER) {
            if (band.multiplier !in 0.1..10.0) return OtPricingIssue.CLOCK_BAND_INVALID
        } else if (band.hourlyCents <= 0) {
            return OtPricingIssue.CLOCK_BAND_INVALID
        }
        for (minute in clockIntervals(band)) {
            for (m in minute) {
                if (occupied[m]) return OtPricingIssue.CLOCK_BAND_OVERLAP
                occupied[m] = true
            }
        }
    }
    return null
}

/** 把一段（可能跨零点）摊平成一天内的多个半开区间 [start, end)。 */
internal fun clockIntervals(band: OtClockBand): List<IntRange> =
    if (band.endMinute > band.startMinute) {
        listOf(band.startMinute until band.endMinute)
    } else {
        buildList {
            if (band.startMinute < 24 * 60) add(band.startMinute until 24 * 60)
            if (band.endMinute > 0) add(0 until band.endMinute)
        }
    }

/** 供记录弹层/首页展示的简短摘要（domain 只给事实，不拼 UI 文案）。 */
fun OtPricingConfig.bandCount(): Int = when (mode) {
    OtPricingMode.FLAT -> 0
    OtPricingMode.DURATION_BANDS -> durationBands.size
    OtPricingMode.CLOCK_BANDS -> clockBands.size
}
