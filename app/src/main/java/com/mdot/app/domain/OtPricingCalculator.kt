package com.mdot.app.domain

import com.mdot.app.domain.model.OtClockBand
import com.mdot.app.domain.model.OtPricingConfig
import com.mdot.app.domain.model.OtPricingMode
import com.mdot.app.domain.model.OtRateUnit
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.clockIntervals
import java.math.BigDecimal
import java.math.RoundingMode

/** 一段加班费明细。时间偏移相对「本次加班开始」。 */
data class OtSegment(
    val startOffsetMinute: Int,
    val endOffsetMinute: Int,
    val minutes: Int,
    val hourlyCents: Long,
    val multiplier: Double?,
    val flat: Boolean,
    val cents: Long,
)

data class OtPricingResult(
    val cents: Long,
    val segments: List<OtSegment>,
    val applied: Boolean,
)

/**
 * 加班分段计价引擎（纯函数）。
 *
 * - 未启用、档位不在 [OtPricingConfig.tiers]、或钟点模式缺开始时间 => 整段按单档算。
 * - 钟点时段之间的空洞按单档算（不报错；配置页负责提示）。
 * - 每一段独立 HALF_UP 到分后求和，与「整段一次算」可能差 1 分。
 */
object OtPricingCalculator {

    private val HOURS_PER_MONTH = BigDecimal("174")
    private val MINUTES_PER_HOUR = BigDecimal(60)
    private val MAX_DAY_MINUTES = 24 * 60

    /** 单档加班费，与原 [PayrollCalculator.overtimeCents] 口径一致。 */
    fun flatCents(salary: SalaryConfig, tier: RateTier, minutes: Int): Long {
        if (minutes <= 0) return 0
        val minutesBd = BigDecimal(minutes)
        return when (salary.mode) {
            SalaryMode.BASE -> baseHourly(salary, tier)
                .multiply(BigDecimal.valueOf(salary.multipliers[tier] ?: 1.5))
                .multiply(minutesBd)
                .divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
                .toLong()

            SalaryMode.MANUAL -> BigDecimal(salary.manualRatesCents[tier] ?: 0L)
                .multiply(minutesBd)
                .divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
                .toLong()
        }
    }

    /** 单档的等效时薪，仅用于展示。 */
    fun flatHourlyCents(salary: SalaryConfig, tier: RateTier): Long = when (salary.mode) {
        SalaryMode.BASE -> baseHourly(salary, tier)
            .multiply(BigDecimal.valueOf(salary.multipliers[tier] ?: 1.5))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

        SalaryMode.MANUAL -> salary.manualRatesCents[tier] ?: 0L
    }

    /**
     * 计算 [fromMinute, toMinute) 这一段的加班费。两个偏移都相对本次加班开始。
     * [totalMinutes] 用来识别 C1 最后一段的「以上」以及 C2 的结束时刻。
     */
    fun computeRange(
        salary: SalaryConfig,
        tier: RateTier,
        totalMinutes: Int,
        startMinute: Int?,
        fromMinute: Int,
        toMinute: Int,
    ): OtPricingResult {
        val total = totalMinutes.coerceIn(0, MAX_DAY_MINUTES)
        val from = fromMinute.coerceIn(0, total)
        val to = toMinute.coerceIn(from, total)
        if (to <= from) return OtPricingResult(0, emptyList(), applied = false)

        val config = salary.otPricing
        val flatOnly = !config.appliesTo(tier) ||
            (config.mode == OtPricingMode.CLOCK_BANDS && startMinute == null)
        if (flatOnly) {
            val cents = flatCents(salary, tier, to - from)
            return OtPricingResult(
                cents = cents,
                segments = listOf(
                    OtSegment(
                        startOffsetMinute = from,
                        endOffsetMinute = to,
                        minutes = to - from,
                        hourlyCents = flatHourlyCents(salary, tier),
                        multiplier = null,
                        flat = true,
                        cents = cents,
                    )
                ),
                applied = false,
            )
        }

        val pieces = when (config.mode) {
            OtPricingMode.DURATION_BANDS -> durationPieces(config, total, from, to)
            OtPricingMode.CLOCK_BANDS -> clockPieces(config, startMinute ?: 0, total, from, to)
            OtPricingMode.FLAT -> emptyList()
        }
        val segments = pieces.mapNotNull { piece -> piece.toSegment(salary, tier) }
        return OtPricingResult(
            cents = segments.sumOf { it.cents },
            segments = segments,
            applied = segments.any { !it.flat },
        )
    }

    /** 整段（不区分转调休） */
    fun compute(
        salary: SalaryConfig,
        tier: RateTier,
        totalMinutes: Int,
        startMinute: Int?,
    ): OtPricingResult = computeRange(
        salary = salary,
        tier = tier,
        totalMinutes = totalMinutes,
        startMinute = startMinute,
        fromMinute = 0,
        toMinute = totalMinutes,
    )

    private fun durationPieces(
        config: OtPricingConfig,
        total: Int,
        from: Int,
        to: Int,
    ): List<Piece> {
        val out = mutableListOf<Piece>()
        var cursor = 0
        config.durationBands.forEach { band ->
            val end = (band.endMinute ?: total).coerceAtMost(total)
            if (end > cursor) {
                val s = maxOf(cursor, from)
                val e = minOf(end, to)
                if (e > s) {
                    out += Piece(
                        start = s,
                        end = e,
                        multiplier = if (config.rateUnit == OtRateUnit.MULTIPLIER) band.multiplier else null,
                        fixedHourlyCents = if (config.rateUnit == OtRateUnit.FIXED_HOURLY) band.hourlyCents else null,
                    )
                }
            }
            cursor = maxOf(cursor, end)
        }
        return mergeAdjacent(out)
    }

    private fun clockPieces(
        config: OtPricingConfig,
        startMinute: Int,
        total: Int,
        from: Int,
        to: Int,
    ): List<Piece> {
        val absStart = startMinute.coerceIn(0, MAX_DAY_MINUTES - 1)
        val absFrom = absStart + from
        val absTo = absStart + to
        val hits = mutableListOf<Piece>()
        config.clockBands.forEach { band ->
            for (dayOffset in listOf(0, MAX_DAY_MINUTES)) {
                for (range in clockIntervals(band)) {
                    val s = maxOf(range.first + dayOffset, absFrom)
                    val e = minOf(range.last + 1 + dayOffset, absTo)
                    if (e > s) {
                        hits += Piece(
                            start = s,
                            end = e,
                            multiplier = if (config.rateUnit == OtRateUnit.MULTIPLIER) band.multiplier else null,
                            fixedHourlyCents = if (config.rateUnit == OtRateUnit.FIXED_HOURLY) band.hourlyCents else null,
                        )
                    }
                }
            }
        }
        hits.sortBy { it.start }

        val out = mutableListOf<Piece>()
        var cursor = absFrom
        hits.forEach { hit ->
            if (hit.start > cursor) out += Piece.flat(cursor, hit.start)
            out += hit
            cursor = maxOf(cursor, hit.end)
        }
        if (cursor < absTo) out += Piece.flat(cursor, absTo)

        // 转成「相对本次加班开始」的偏移，并合并相邻同费率段
        return mergeAdjacent(
            out.map { piece ->
                piece.copy(
                    start = piece.start - absStart,
                    end = piece.end - absStart,
                )
            }
        )
    }

    private fun mergeAdjacent(pieces: List<Piece>): List<Piece> {
        if (pieces.isEmpty()) return pieces
        val out = mutableListOf<Piece>()
        pieces.forEach { piece ->
            val last = out.lastOrNull()
            if (last != null && last.end == piece.start &&
                last.multiplier == piece.multiplier &&
                last.fixedHourlyCents == piece.fixedHourlyCents
            ) {
                out[out.lastIndex] = last.copy(end = piece.end)
            } else {
                out += piece
            }
        }
        return out
    }

    private fun Piece.toSegment(
        salary: SalaryConfig,
        tier: RateTier,
    ): OtSegment? {
        val minutes = end - start
        if (minutes <= 0) return null
        val rate = when {
            fixedHourlyCents != null -> BigDecimal(fixedHourlyCents)
            multiplier != null -> baseHourly(salary, tier).multiply(BigDecimal.valueOf(multiplier))
            else -> BigDecimal(flatHourlyCents(salary, tier))
        }
        val cents = rate.multiply(BigDecimal(minutes))
            .divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
            .toLong()
        return OtSegment(
            startOffsetMinute = start,
            endOffsetMinute = end,
            minutes = minutes,
            hourlyCents = rate.setScale(0, RoundingMode.HALF_UP).toLong(),
            multiplier = multiplier,
            flat = multiplier == null && fixedHourlyCents == null,
            cents = cents,
        )
    }

    private fun baseHourly(salary: SalaryConfig, tier: RateTier): BigDecimal = when (salary.mode) {
        SalaryMode.BASE -> BigDecimal(salary.baseSalaryCents)
            .divide(HOURS_PER_MONTH, 10, RoundingMode.HALF_UP)

        SalaryMode.MANUAL -> BigDecimal(salary.manualRatesCents[tier] ?: 0L)
    }

    private data class Piece(
        val start: Int,
        val end: Int,
        val multiplier: Double?,
        val fixedHourlyCents: Long?,
    ) {
        companion object {
            fun flat(start: Int, end: Int) = Piece(start, end, null, null)
        }
    }
}
