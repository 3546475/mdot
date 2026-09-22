package com.mdot.app.domain

import com.mdot.app.domain.PayrollCalculator.DailyRecordLite
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.TierSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 「调休折现」自动折算（docs/20 设计原则：能自动的就不要让用户手动）。
 *
 * 口径：转调休的加班分钟，按**引擎自己的算法**（时薪 × 档位倍率 × 小时）折成钱——
 * 即这段时间若计酬应得的加班费。逐条用该记录的实际档位算、HALF_UP 到分后求和。
 */
class CompCashTest {

    private val salary = SalaryConfig(baseSalaryCents = 230_000) // ¥2300 → 时薪 = 2300/174

    private fun ot(
        daysAgo: Long,
        minutes: Int,
        toComp: Int,
        tier: RateTier = RateTier.WEEKDAY,
    ) = DailyRecordLite(
        date = LocalDate.of(2026, 9, 22).minusDays(daysAgo),
        type = RecordType.OT,
        durationMinutes = minutes,
        tier = tier,
        tierSource = TierSource.MANUAL,
        toCompMinutes = toComp,
    )

    private fun calc(records: List<DailyRecordLite>) = PayrollCalculator.summarize(
        PayrollCalculator.Input(
            salary = salary,
            records = records,
            tierOf = { RateTier.WEEKDAY },
        )
    )

    @Test
    fun `折现金额等于这些分钟若计酬应得的加班费`() {
        val withComp = calc(listOf(ot(0, minutes = 120, toComp = 90)))
        // 不转调休的同一笔（其余条件不变）应得多少
        val noComp = calc(listOf(ot(0, minutes = 120, toComp = 0)))
        val delta = noComp.otPayCents - withComp.otPayCents
        val cash = PayrollCalculator.compCashCents(salary, withComp)
        // 引擎口径是「逐笔算完 HALF_UP 到分再求和」，两次独立舍入最多差 1 分
        assertEquals(1L, kotlin.math.abs(delta - cash))
    }

    @Test
    fun `工作日 90 分钟转调休折现 2974 分`() {
        val out = calc(listOf(ot(0, minutes = 120, toComp = 90)))
        assertEquals(90, PayrollCalculator.compFromOtMinutes(out))
        assertEquals(2974L, PayrollCalculator.compCashCents(salary, out))
    }

    @Test
    fun `档位按记录本身算_周末倍率高则折现更高`() {
        val weekday = calc(listOf(ot(0, minutes = 60, toComp = 60, tier = RateTier.WEEKDAY)))
        val weekend = calc(listOf(ot(0, minutes = 60, toComp = 60, tier = RateTier.WEEKEND)))
        assertEquals(1983L, PayrollCalculator.compCashCents(salary, weekday))
        assertEquals(2644L, PayrollCalculator.compCashCents(salary, weekend))
    }

    @Test
    fun `没有转调休时折现为 0`() {
        val out = calc(listOf(ot(0, minutes = 120, toComp = 0)))
        assertEquals(0, PayrollCalculator.compFromOtMinutes(out))
        assertEquals(0L, PayrollCalculator.compCashCents(salary, out))
    }

    @Test
    fun `多笔转调休逐条算完再求和`() {
        val out = calc(
            listOf(
                ot(0, minutes = 60, toComp = 60, tier = RateTier.WEEKDAY),
                ot(1, minutes = 60, toComp = 60, tier = RateTier.WEEKEND),
            )
        )
        assertEquals(120, PayrollCalculator.compFromOtMinutes(out))
        assertEquals(1983L + 2644L, PayrollCalculator.compCashCents(salary, out))
    }
}
