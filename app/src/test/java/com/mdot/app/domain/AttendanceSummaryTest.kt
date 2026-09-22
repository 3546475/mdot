package com.mdot.app.domain

import com.mdot.app.domain.PayrollCalculator.DailyRecordLite
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.model.reconciliations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * B3：考勤摘要（摘要条）与对账（引擎值 vs 手改值）。
 * 摘要全部从引擎 breakdowns 推导，**与工资计算同源**，不在 UI 另算（docs/20 P1-1/P1-2）。
 */
class AttendanceSummaryTest {

    private val salary = SalaryConfig(baseSalaryCents = 230_000)

    private fun ot(date: String, minutes: Int, tier: RateTier = RateTier.WEEKDAY) = DailyRecordLite(
        date = LocalDate.parse(date),
        type = RecordType.OT,
        durationMinutes = minutes,
        tier = tier,
        tierSource = TierSource.MANUAL,
    )

    private fun leave(date: String, minutes: Int, type: LeaveType) = DailyRecordLite(
        date = LocalDate.parse(date),
        type = RecordType.LEAVE,
        durationMinutes = minutes,
        leaveType = type,
    )

    private fun summary(records: List<DailyRecordLite>) =
        PayrollCalculator.attendanceSummary(
            PayrollCalculator.summarize(
                PayrollCalculator.Input(salary = salary, records = records, tierOf = { RateTier.WEEKDAY })
            )
        )

    @Test
    fun `加班分钟按档位拆分`() {
        val s = summary(
            listOf(
                ot("2026-09-01", 120, RateTier.WEEKDAY),
                ot("2026-09-02", 60, RateTier.WEEKDAY),
                ot("2026-09-05", 240, RateTier.WEEKEND),
                ot("2026-09-10", 120, RateTier.STATUTORY),
            )
        )
        assertEquals(180, s.otMinutesByTier[RateTier.WEEKDAY])
        assertEquals(240, s.otMinutesByTier[RateTier.WEEKEND])
        assertEquals(120, s.otMinutesByTier[RateTier.STATUTORY])
        assertEquals(540, s.otMinutes)
    }

    @Test
    fun `记录天数按日期去重`() {
        val s = summary(
            listOf(
                ot("2026-09-01", 60),
                ot("2026-09-01", 120),   // 同一天两条只算一天
                leave("2026-09-02", 480, LeaveType.PERSONAL),
            )
        )
        assertEquals(2, s.recordDays)
    }

    @Test
    fun `请假按类型拆分且不计入加班`() {
        val s = summary(
            listOf(
                leave("2026-09-02", 480, LeaveType.PERSONAL),
                leave("2026-09-03", 240, LeaveType.SICK),
                ot("2026-09-04", 60),
            )
        )
        assertEquals(480, s.leaveMinutesByType[LeaveType.PERSONAL])
        assertEquals(240, s.leaveMinutesByType[LeaveType.SICK])
        assertEquals(720, s.leaveMinutes)
        assertEquals(60, s.otMinutes)
    }

    @Test
    fun `没有记录时为空`() {
        val s = summary(emptyList())
        assertEquals(0, s.otMinutes)
        assertEquals(0, s.leaveMinutes)
        assertEquals(0, s.recordDays)
        assertEquals(true, s.isEmpty)
    }
}

/** 对账：只有「有引擎值且现值不同」的行才算差异 */
class ReconciliationTest {

    private fun sheet(
        engine: Long?,
        current: Long,
        source: PayMonthSource? = PayMonthSource.EDITED,
    ) = PayMonthSheet(
        basic = listOf(
            PayMonthItem(1, "基本工资", 230_000, builtin = true, source = PayMonthSource.SYNCED, engineCents = 230_000),
            PayMonthItem(2, "加班工资", current, builtin = true, source = source, engineCents = engine),
            PayMonthItem(3, "调休折现", 0, builtin = true),
        ),
    )

    @Test
    fun `手改过的行列出差额`() {
        val r = sheet(engine = 1_286, current = 1_200).reconciliations()
        assertEquals(1, r.size)
        assertEquals(PayGroup.BASIC, r.first().group)
        assertEquals("加班工资", r.first().item.name)
        assertEquals(-86, r.first().diffCents)
    }

    @Test
    fun `现值与引擎值相同的行不算差异`() {
        assertEquals(0, sheet(engine = 1_286, current = 1_286).reconciliations().size)
    }

    @Test
    fun `没有引擎值的行不算差异`() {
        assertEquals(0, sheet(engine = null, current = 999, source = null).reconciliations().size)
    }
}
