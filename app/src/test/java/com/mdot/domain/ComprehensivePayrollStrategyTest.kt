package com.mdot.app.domain

import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.model.WorkSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 综合工时工资引擎单测（10 文档 §4）。
 * 口径：法定节假日 300% 即时计酬且不计入周期工时（D3）；其余工时累计、超标准部分按
 * 平时倍率周期末结算；无转调休（D4）；请假扣款与底薪结构同标准工时。
 * 基准底薪 3480.00 元（348000 分）→ 时薪 = 348000/174 = 20.00 元/小时（整除便于口算）。
 */
class ComprehensivePayrollStrategyTest {

    private val base348000 = SalaryConfig(
        workSystem = WorkSystem.COMPREHENSIVE,
        baseSalaryCents = 348_000L,
    )

    private fun cfg(
        baseCents: Long = 348_000L,
        mode: SalaryMode = SalaryMode.BASE,
        includeBase: Boolean = true,
        stdMinutes: Int = 0,
    ) = SalaryConfig(
        mode = mode,
        baseSalaryCents = baseCents,
        includeBase = includeBase,
        workSystem = WorkSystem.COMPREHENSIVE,
        comprehensiveStandardMinutes = stdMinutes,
        manualRatesCents = mapOf(
            RateTier.WEEKDAY to 3_000L,
            RateTier.WEEKEND to 0L,
            RateTier.STATUTORY to 9_000L,
        ),
    )

    /** 2026-10-01 为法定节假日（AUTO 判定走 tierOf），其余为平时档 */
    private val tierOf: (LocalDate) -> RateTier = {
        if (it == LocalDate.parse("2026-10-01")) RateTier.STATUTORY else RateTier.WEEKDAY
    }

    private fun work(
        date: String,
        minutes: Int,
        tier: RateTier? = null,
        source: TierSource? = null,
        toComp: Int = 0,
    ) = PayrollCalculator.DailyRecordLite(
        date = LocalDate.parse(date),
        type = RecordType.OT,
        durationMinutes = minutes,
        tier = tier,
        tierSource = source ?: TierSource.AUTO,
        toCompMinutes = toComp,
    )

    private fun leave(date: String, minutes: Int, type: LeaveType) =
        PayrollCalculator.DailyRecordLite(
            date = LocalDate.parse(date),
            type = RecordType.LEAVE,
            durationMinutes = minutes,
            leaveType = type,
        )

    private fun summarize(
        salary: SalaryConfig = base348000,
        records: List<PayrollCalculator.DailyRecordLite> = emptyList(),
        standardMinutes: Int? = null,
        adjustments: List<PayrollCalculator.CompAdjustmentLite> = emptyList(),
    ) = ComprehensivePayrollStrategy.summarize(
        PayrollCalculator.Input(salary, records, adjustments, tierOf, standardMinutes)
    )

    // ---- 注册 ----

    @Test
    fun `strategyFor 注册综合工时策略`() {
        assertTrue(PayrollCalculator.strategyFor(WorkSystem.COMPREHENSIVE) is ComprehensivePayrollStrategy)
    }

    // ---- 周期标准与超时判定 ----

    @Test
    fun `零工时 只有底薪`() {
        val out = summarize(standardMinutes = 9600)
        assertEquals(0, out.otMinutes)
        assertEquals(0L, out.otPayCents)
        assertEquals(348_000L, out.incomeCents)
        assertEquals(9600, out.periodStandardMinutes)
        assertEquals(0, out.overtimeMinutes)
        assertEquals(0, out.holidayWorkMinutes)
    }

    @Test
    fun `不超时 无加班费`() {
        val out = summarize(records = listOf(work("2026-09-07", 480)), standardMinutes = 9600)
        assertEquals(480, out.otMinutes)
        assertEquals(0, out.overtimeMinutes)
        assertEquals(0L, out.otPayCents)
        assertEquals(348_000L, out.incomeCents)
    }

    @Test
    fun `恰好等于标准 无超时`() {
        val out = summarize(
            records = List(20) { work("2026-09-%02d".format(it + 1), 480) },
            standardMinutes = 9600,
        )
        assertEquals(9600, out.otMinutes)
        assertEquals(0, out.overtimeMinutes)
        assertEquals(0L, out.otPayCents)
    }

    @Test
    fun `超时部分按平时倍率周期结算`() {
        // 22 天 × 8h = 176h，标准 160h → 超时 16h × 20元 × 1.5 = 480.00 元
        val out = summarize(
            records = List(22) { work("2026-09-%02d".format(it + 1), 480) },
            standardMinutes = 9600,
        )
        assertEquals(10560, out.otMinutes)
        assertEquals(960, out.overtimeMinutes)
        assertEquals(48_000L, out.otPayCents)
        assertEquals(348_000L + 48_000L, out.incomeCents)
        assertEquals(48_000L, out.otPayByTier[RateTier.WEEKDAY])
        assertEquals(960, out.paidOtMinutes)
    }

    @Test
    fun `手动标准覆盖注入的自动值`() {
        // 配置手填 4800（80h）优先于注入的 9600：工时 5280 → 超时 480min × 30元/min系数 = 24000分
        val out = summarize(
            salary = cfg(stdMinutes = 4800),
            records = listOf(work("2026-09-07", 5280)),
            standardMinutes = 9600,
        )
        assertEquals(4800, out.periodStandardMinutes)
        assertEquals(480, out.overtimeMinutes)
        assertEquals(24_000L, out.otPayCents)
    }

    @Test
    fun `配置为0时使用注入的自动标准`() {
        val out = summarize(
            records = listOf(work("2026-09-07", 5280)),
            standardMinutes = 5280,
        )
        assertEquals(5280, out.periodStandardMinutes)
        assertEquals(0, out.overtimeMinutes)
    }

    @Test
    fun `标准完全缺省按0 全量按超时结算`() {
        val out = summarize(records = listOf(work("2026-09-07", 480)))
        assertEquals(0, out.periodStandardMinutes)
        assertEquals(480, out.overtimeMinutes)
        // 480min × 20元/h × 1.5 = 240.00 元
        assertEquals(24_000L, out.otPayCents)
    }

    // ---- 法定节假日即时档（D3）----

    @Test
    fun `法定节假日即时计酬且不计入周期工时`() {
        val out = summarize(
            records = listOf(
                work("2026-10-01", 480),              // AUTO 判定为 STATUTORY
                work("2026-09-07", 480),              // 平时
            ),
            standardMinutes = 960,
        )
        // 节假日 8h × 20 × 3 = 480 元；普通 8h 未超标准（480 < 960）
        assertEquals(48_000L, out.otPayCents)
        assertEquals(0, out.overtimeMinutes)
        assertEquals(480, out.otMinutes)              // 周期工时不含节假日
        assertEquals(480, out.holidayWorkMinutes)
        assertEquals(348_000L + 48_000L, out.incomeCents)
    }

    @Test
    fun `节假日与普通工时同时超时`() {
        val out = summarize(
            records = listOf(
                work("2026-10-01", 480),
                work("2026-09-07", 960),
            ),
            standardMinutes = 480,
        )
        // 节假日 48000 + 超时 (960-480)=480min → 24000
        assertEquals(72_000L, out.otPayCents)
        assertEquals(480, out.overtimeMinutes)
        assertEquals(960, out.paidOtMinutes)
    }

    @Test
    fun `手动补改法定档同样即时计酬`() {
        val out = summarize(
            records = listOf(
                work("2026-09-07", 60, tier = RateTier.STATUTORY, source = TierSource.MANUAL),
            ),
        )
        assertEquals(60, out.holidayWorkMinutes)
        assertEquals(0, out.otMinutes)
        // 1h × 20 × 3 = 60 元
        assertEquals(6_000L, out.otPayCents)
    }

    @Test
    fun `WEEKEND档计入周期工时不即时计酬`() {
        // D5：综合工时无 200% 档，周末上班只累计分钟
        val out = summarize(
            records = listOf(work("2026-09-05", 480, tier = RateTier.WEEKEND, source = TierSource.MANUAL)),
            standardMinutes = 480,
        )
        assertEquals(480, out.otMinutes)
        assertEquals(0, out.overtimeMinutes)
        assertEquals(0L, out.otPayCents)
    }

    // ---- 金额与舍入 ----

    @Test
    fun `超时费HALF_UP到分`() {
        // 底薪 1000 元 → 时薪 574.7126…分；超时 60min × 1.5 = 862.068…→ 862 分
        val out = summarize(
            salary = cfg(baseCents = 100_000L),
            records = listOf(work("2026-09-07", 60)),
        )
        assertEquals(862L, out.otPayCents)
    }

    @Test
    fun `手动单价模式按平时档单价结算超时`() {
        val out = summarize(
            salary = cfg(mode = SalaryMode.MANUAL, baseCents = 0),
            records = listOf(
                work("2026-09-07", 600),   // 600 − 标准 480 = 120min 超时 → 30元/h × 2h = 60 元
                work("2026-10-01", 60),    // 法定 90元/h × 1h = 90 元
            ),
            standardMinutes = 480,
        )
        assertEquals(6_000L, out.otPayByTier[RateTier.WEEKDAY])
        assertEquals(9_000L, out.otPayByTier[RateTier.STATUTORY])
        assertEquals(15_000L, out.otPayCents)
        assertEquals(15_000L, out.incomeCents)    // 无底薪
    }

    // ---- 请假与底薪 ----

    @Test
    fun `请假扣款同标准工时口径`() {
        // 日薪 = 3480/21.75 = 160 元；事假 4h = 半天 → 扣 80.00 元
        val out = summarize(
            records = listOf(leave("2026-09-08", 240, LeaveType.PERSONAL)),
            standardMinutes = 9600,
        )
        assertEquals(240, out.leaveMinutes)
        assertEquals(8_000L, out.leaveDeductCents)
        assertEquals(348_000L - 8_000L, out.incomeCents)
    }

    @Test
    fun `不计底薪时收入仅加班费`() {
        val out = summarize(
            salary = cfg(includeBase = false),
            records = List(22) { work("2026-09-%02d".format(it + 1), 480) },
            standardMinutes = 9600,
        )
        assertEquals(0L, out.baseIncludedCents)
        assertEquals(48_000L, out.incomeCents)
    }

    // ---- 调休与记录语义 ----

    @Test
    fun `无调休 手动调整记录被忽略`() {
        val out = summarize(adjustments = listOf(PayrollCalculator.CompAdjustmentLite(60)))
        assertEquals(0, out.compBalanceMinutes)
    }

    @Test
    fun `转调休字段忽略 全时长计入周期`() {
        // D4：综合工时下 toCompMinutes 不生效
        val out = summarize(
            records = listOf(work("2026-09-07", 480, toComp = 240)),
            standardMinutes = 960,
        )
        assertEquals(480, out.otMinutes)
        assertEquals(0, out.overtimeMinutes)
    }

    @Test
    fun `breakdown按日期升序`() {
        val out = summarize(
            records = listOf(
                work("2026-09-09", 60),
                work("2026-09-02", 60),
                leave("2026-09-05", 60, LeaveType.SICK),
            ),
        )
        assertEquals(
            listOf("2026-09-02", "2026-09-05", "2026-09-09"),
            out.breakdowns.map { it.record.date.toString() },
        )
    }
}
