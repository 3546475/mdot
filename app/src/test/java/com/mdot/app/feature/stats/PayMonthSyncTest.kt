package com.mdot.app.feature.stats

import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.RateTier
import org.junit.Assert.assertEquals
import org.junit.Test

/** 记月「同步本月考勤」回填（applyRecordSync）用例 */
class PayMonthSyncTest {

    private fun out(
        base: Long = 230_000,
        ot: Long = 19_830,
        personal: Long = 13_220,
        sick: Long = 6_610,
    ) = PayrollCalculator.Output(
        breakdowns = emptyList(),
        otMinutes = 60,
        paidOtMinutes = 60,
        otPayCents = ot,
        leaveMinutes = 90,
        leaveDeductCents = personal + sick,
        compBalanceMinutes = 0,
        baseIncludedCents = base,
        incomeCents = base + ot - personal - sick,
        otPayByTier = mapOf(RateTier.WEEKDAY to ot),
        leaveDeductByType = mapOf(LeaveType.PERSONAL to personal, LeaveType.SICK to sick),
    )

    /** 固定同步日期（避免单测依赖当天日期） */
    private val SYNC_DAY = "2026-09-22"

    private fun sheet() = PayMonthSheet.default()

    private fun itemOf(s: PayMonthSheet, group: PayGroup, id: Long): PayMonthItem = when (group) {
        PayGroup.BASIC -> s.basic
        PayGroup.SUBSIDY -> s.subsidy
        PayGroup.DEDUCTION -> s.deduction
        PayGroup.OTHER -> s.other
    }.first { it.id == id }

    private fun amountOf(s: PayMonthSheet, group: PayGroup, id: Long): Long = when (group) {
        PayGroup.BASIC -> s.basic
        PayGroup.SUBSIDY -> s.subsidy
        PayGroup.DEDUCTION -> s.deduction
        PayGroup.OTHER -> s.other
    }.first { it.id == id }.amountCents

    @Test
    fun `回填基本工资加班工资事假病假`() {
        val s = applyRecordSync(sheet(), out(), fillBase = true, syncedAt = SYNC_DAY)
        assertEquals(230_000, amountOf(s, PayGroup.BASIC, 1L))
        assertEquals(19_830, amountOf(s, PayGroup.BASIC, 2L))
        assertEquals(13_220, amountOf(s, PayGroup.DEDUCTION, 6L))
        assertEquals(6_610, amountOf(s, PayGroup.DEDUCTION, 7L))
    }

    @Test
    fun `不含底薪时基本工资保持手动值`() {
        val manual = sheet().copy(
            basic = sheet().basic.map { if (it.id == 1L) it.copy(amountCents = 9_999) else it },
        )
        val s = applyRecordSync(manual, out(), fillBase = false, syncedAt = SYNC_DAY)
        assertEquals(9_999, amountOf(s, PayGroup.BASIC, 1L))
        assertEquals(19_830, amountOf(s, PayGroup.BASIC, 2L))
    }

    @Test
    fun `缺行不崩溃且其余分组不动`() {
        val stripped = sheet().copy(
            deduction = sheet().deduction.filter { it.id != 6L },
            subsidy = listOf(PayMonthItem(4, "其它补贴", amountCents = 8_800, builtin = true)),
        )
        val s = applyRecordSync(stripped, out(), fillBase = true, syncedAt = SYNC_DAY)
        assertEquals(8_800, s.subsidy.first().amountCents)
        assertEquals(0, s.deduction.count { it.id == 6L })
        assertEquals(6_610, amountOf(s, PayGroup.DEDUCTION, 7L))
    }

    // ── v0.7.4「调休折现」自动回填（docs/20 设计原则：能自动的不要用户手动）──
    @Test
    fun `同步同时自动回填调休折现并带推导分钟`() {
        val s = applyRecordSync(
            sheet(), out(), fillBase = true, syncedAt = SYNC_DAY,
            compCashCents = 2_974, compMinutes = 90,
        )
        val comp = itemOf(s, PayGroup.BASIC, PayMonthSheet.COMP_ROW_ID)
        assertEquals(2_974, comp.amountCents)
        assertEquals(PayMonthSource.SYNCED, comp.source)
        assertEquals(90, comp.derivationMinutes)
        assertEquals(SYNC_DAY, comp.syncedAt)
    }

    @Test
    fun `没有转调休时折现为 0 且不写推导分钟`() {
        val s = applyRecordSync(sheet(), out(), fillBase = true, syncedAt = SYNC_DAY, compCashCents = 0, compMinutes = 0)
        val comp = itemOf(s, PayGroup.BASIC, PayMonthSheet.COMP_ROW_ID)
        assertEquals(0, comp.amountCents)
        assertEquals(null, comp.derivationMinutes)
    }

    // ── v0.7.4 社保/公积金自动回填（docs/20「自动 > 选项 > 手动」）──
    @Test
    fun `同步按薪资设定回填社保公积金并带基数与比例`() {
        val fill = InsuranceFill(
            socialCents = 52_500, socialBaseCents = 500_000, socialRateBp = 1_050,
            fundCents = 60_000, fundBaseCents = 500_000, fundRateBp = 1_200,
        )
        val s = applyRecordSync(sheet(), out(), fillBase = true, syncedAt = SYNC_DAY, insurance = fill)
        val social = itemOf(s, PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID)
        assertEquals(52_500, social.amountCents)
        assertEquals(PayMonthSource.SYNCED, social.source)
        assertEquals(500_000L, social.derivationBaseCents)
        assertEquals(1_050, social.derivationRateBp)
        val fund = itemOf(s, PayGroup.OTHER, PayMonthSheet.FUND_ROW_ID)
        assertEquals(60_000, fund.amountCents)
        assertEquals(1_200, fund.derivationRateBp)
    }

    @Test
    fun `比例 0 时不动这两行_不覆盖用户手填值`() {
        val manual = sheet().copy(
            other = sheet().other.map { if (it.id == PayMonthSheet.SOCIAL_ROW_ID) it.copy(amountCents = 9_999) else it },
        )
        // 比例全 0 = 用户没启用自动算
        val s = applyRecordSync(manual, out(), fillBase = true, syncedAt = SYNC_DAY, insurance = InsuranceFill())
        val social = itemOf(s, PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID)
        assertEquals(9_999, social.amountCents)
        assertEquals(null, social.source)
        assertEquals(null, social.derivationRateBp)
    }

    // ── v0.7.4 来源戳（docs/20 P0-3）──────────────────────────────
    @Test
    fun `同步回填的行带来源戳与引擎原值`() {
        val s = applyRecordSync(sheet(), out(), fillBase = true, syncedAt = SYNC_DAY)
        val ot = itemOf(s, PayGroup.BASIC, 2L)
        assertEquals(PayMonthSource.SYNCED, ot.source)
        assertEquals(19_830L, ot.engineCents)
        assertEquals(SYNC_DAY, ot.syncedAt)
        // 未回填的行不打戳（补贴/社保等仍由用户自己填）
        val subsidy = itemOf(s, PayGroup.SUBSIDY, 4L)
        assertEquals(null, subsidy.source)
        assertEquals(null, subsidy.engineCents)
    }

    @Test
    fun `不含底薪时基本工资不被回填也不打戳`() {
        val s = applyRecordSync(sheet(), out(), fillBase = false, syncedAt = SYNC_DAY)
        val base = itemOf(s, PayGroup.BASIC, 1L)
        assertEquals(0, base.amountCents)
        assertEquals(null, base.source)
        assertEquals(null, base.engineCents)
    }

    @Test
    fun `重复同步覆盖引擎值与日期`() {
        val first = applyRecordSync(sheet(), out(ot = 1_000), fillBase = true, syncedAt = "2026-09-20")
        val second = applyRecordSync(first, out(ot = 2_000), fillBase = true, syncedAt = "2026-09-25")
        val ot = itemOf(second, PayGroup.BASIC, 2L)
        assertEquals(2_000, ot.amountCents)
        assertEquals(2_000L, ot.engineCents)
        assertEquals("2026-09-25", ot.syncedAt)
        assertEquals(PayMonthSource.SYNCED, ot.source)
    }
}
