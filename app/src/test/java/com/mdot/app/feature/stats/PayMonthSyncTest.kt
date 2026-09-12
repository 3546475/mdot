package com.mdot.app.feature.stats

import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
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

    private fun sheet() = PayMonthSheet.default()

    private fun amountOf(s: PayMonthSheet, group: PayGroup, id: Long): Long = when (group) {
        PayGroup.BASIC -> s.basic
        PayGroup.SUBSIDY -> s.subsidy
        PayGroup.DEDUCTION -> s.deduction
        PayGroup.OTHER -> s.other
    }.first { it.id == id }.amountCents

    @Test
    fun `回填基本工资加班工资事假病假`() {
        val s = applyRecordSync(sheet(), out(), fillBase = true)
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
        val s = applyRecordSync(manual, out(), fillBase = false)
        assertEquals(9_999, amountOf(s, PayGroup.BASIC, 1L))
        assertEquals(19_830, amountOf(s, PayGroup.BASIC, 2L))
    }

    @Test
    fun `缺行不崩溃且其余分组不动`() {
        val stripped = sheet().copy(
            deduction = sheet().deduction.filter { it.id != 6L },
            subsidy = listOf(PayMonthItem(4, "其它补贴", amountCents = 8_800, builtin = true)),
        )
        val s = applyRecordSync(stripped, out(), fillBase = true)
        assertEquals(8_800, s.subsidy.first().amountCents)
        assertEquals(0, s.deduction.count { it.id == 6L })
        assertEquals(6_610, amountOf(s, PayGroup.DEDUCTION, 7L))
    }
}
