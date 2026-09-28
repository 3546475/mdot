package com.mdot.app.domain

import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.reconciliations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页数据区「实发工资」实时预览（[livePreviewSheet]）用例：
 * 引擎行走当前考勤/薪资实时值、用户自有的行保留——首页随记随更新，不等记月页手动同步。
 */
class PayMonthLivePreviewTest {

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

    private val DAY = "2026-09-28"

    private fun sheet() = PayMonthSheet.default()

    private fun amountOf(s: PayMonthSheet, group: PayGroup, id: Long): Long = when (group) {
        PayGroup.BASIC -> s.basic
        PayGroup.SUBSIDY -> s.subsidy
        PayGroup.DEDUCTION -> s.deduction
        PayGroup.OTHER -> s.other
    }.first { it.id == id }.amountCents

    private fun itemOf(s: PayMonthSheet, group: PayGroup, id: Long): PayMonthItem = when (group) {
        PayGroup.BASIC -> s.basic
        PayGroup.SUBSIDY -> s.subsidy
        PayGroup.DEDUCTION -> s.deduction
        PayGroup.OTHER -> s.other
    }.first { it.id == id }

    // ── 核心诉求：不用去记月页点「同步本月考勤」，首页也能拿到引擎实时值 ──
    @Test
    fun `从未同步的单据也按引擎实时出数`() {
        val s = livePreviewSheet(sheet(), out(), fillBase = true, syncedAt = DAY)
        assertEquals(230_000, amountOf(s, PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID))
        assertEquals(19_830, amountOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID))
        assertEquals(13_220, amountOf(s, PayGroup.DEDUCTION, 6L))
        assertEquals(6_610, amountOf(s, PayGroup.DEDUCTION, 7L))
        // 实发 = 应发 − 扣款 − 其他 = 249 830 − 19 830 − 0
        assertEquals(249_830, s.incomeCents)
        assertEquals(230_000, s.netCents)
    }

    @Test
    fun `记了新加班后预览立即跟随新引擎值_存盘单据不必动`() {
        // 存盘单据还是旧同步（加班工资 1 000）
        val stale = applyRecordSync(sheet(), out(ot = 1_000, personal = 0, sick = 0), fillBase = true, syncedAt = "2026-09-20")
        // 新记录进来 → 预览用新引擎值
        val s = livePreviewSheet(stale, out(ot = 2_000, personal = 0, sick = 0), fillBase = true, syncedAt = DAY)
        assertEquals(2_000, amountOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID))
        assertEquals(232_000, s.netCents)
    }

    // ── 手改优先：用户自有的行不被引擎覆盖（docs/20 P0-3/P2-2）──
    @Test
    fun `同步后手改的行保留用户值`() {
        val stale = applyRecordSync(sheet(), out(ot = 1_000), fillBase = true, syncedAt = "2026-09-20")
        val edited = stale.copy(
            basic = stale.basic.map {
                if (it.id == PayMonthSheet.OT_ROW_ID) it.copy(amountCents = 500, source = PayMonthSource.EDITED) else it
            },
        )
        val s = livePreviewSheet(edited, out(ot = 2_000), fillBase = true, syncedAt = DAY)
        // 手改的加班工资保留 500，其余引擎行仍实时
        assertEquals(500, amountOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID))
        assertEquals(230_000, amountOf(s, PayGroup.BASIC, PayMonthSheet.BASE_ROW_ID))
    }

    @Test
    fun `从未同步的纯手填非零行保留用户值`() {
        val manual = sheet().copy(
            basic = sheet().basic.map {
                if (it.id == PayMonthSheet.OT_ROW_ID) it.copy(amountCents = 333) else it
            },
        )
        val s = livePreviewSheet(manual, out(ot = 2_000), fillBase = true, syncedAt = DAY)
        assertEquals(333, amountOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID))
    }

    @Test
    fun `手填零行视为待引擎填_由引擎补上`() {
        val s = livePreviewSheet(sheet(), out(), fillBase = true, syncedAt = DAY)
        // 出厂行全 0 且从未同步 → 全部走引擎实时值（等价「从未同步的单据」主用例的行级断言）
        assertEquals(PayMonthSource.SYNCED, itemOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID).source)
        assertEquals(19_830L, itemOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID).engineCents)
    }

    @Test
    fun `手填补贴照计入实发`() {
        val manual = sheet().copy(
            subsidy = sheet().subsidy.map {
                if (it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID) it.copy(amountCents = 300) else it
            },
        )
        val s = livePreviewSheet(manual, out(), fillBase = true, syncedAt = DAY)
        assertEquals(230_300, s.netCents)
    }

    // ── 社保/公积金：比例 0 = 不算，不覆盖手填值（与「同步本月考勤」同口径）──
    @Test
    fun `社保公积金按薪资设定实时算进实发`() {
        val fill = InsuranceFill(
            socialCents = 52_500, socialBaseCents = 500_000, socialRateBp = 1_050,
            fundCents = 60_000, fundBaseCents = 500_000, fundRateBp = 1_200,
        )
        val s = livePreviewSheet(sheet(), out(), fillBase = true, syncedAt = DAY, insurance = fill)
        assertEquals(52_500, amountOf(s, PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID))
        assertEquals(60_000, amountOf(s, PayGroup.OTHER, PayMonthSheet.FUND_ROW_ID))
        assertEquals(117_500, s.netCents)
    }

    @Test
    fun `比例 0 时社保手填值保留且计入实发`() {
        val manual = sheet().copy(
            other = sheet().other.map {
                if (it.id == PayMonthSheet.SOCIAL_ROW_ID) it.copy(amountCents = 9_999) else it
            },
        )
        val s = livePreviewSheet(manual, out(), fillBase = true, syncedAt = DAY, insurance = InsuranceFill())
        assertEquals(9_999, amountOf(s, PayGroup.OTHER, PayMonthSheet.SOCIAL_ROW_ID))
        assertEquals(220_001, s.netCents)
    }

    @Test
    fun `预览不改动传入的存盘单据`() {
        val stored = sheet()
        livePreviewSheet(stored, out(), fillBase = true, syncedAt = DAY)
        assertEquals(0, amountOf(stored, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID))
    }

    // ── 记月页实时化（2026-09-28）：手改行的引擎元数据跟实时引擎走，对账/恢复口径不冻结在手改当时 ──
    @Test
    fun `手改行的引擎元数据随实时引擎刷新_对账不冻结在手改当时`() {
        val stale = applyRecordSync(sheet(), out(ot = 1_000, personal = 0, sick = 0), fillBase = true, syncedAt = "2026-09-20")
        val edited = stale.copy(
            basic = stale.basic.map {
                if (it.id == PayMonthSheet.OT_ROW_ID) it.copy(amountCents = 500, source = PayMonthSource.EDITED) else it
            },
        )
        val s = livePreviewSheet(edited, out(ot = 2_000, personal = 0, sick = 0), fillBase = true, syncedAt = DAY)
        val ot = itemOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID)
        assertEquals(500, ot.amountCents) // 用户金额保留
        assertEquals(PayMonthSource.EDITED, ot.source)
        assertEquals(2_000L, ot.engineCents) // 引擎值跟实时走，不冻结在手改当时的 1 000
        assertEquals(DAY, ot.syncedAt)
        // 对账差额也按实时引擎值算：500 − 2 000 = −1 500
        val recon = s.reconciliations().first { it.item.id == PayMonthSheet.OT_ROW_ID }
        assertEquals(-1_500, recon.diffCents)
    }

    @Test
    fun `纯手填行不补引擎戳_不进对账口径`() {
        val manual = sheet().copy(
            basic = sheet().basic.map {
                if (it.id == PayMonthSheet.OT_ROW_ID) it.copy(amountCents = 333) else it
            },
        )
        val s = livePreviewSheet(manual, out(ot = 2_000), fillBase = true, syncedAt = DAY)
        val ot = itemOf(s, PayGroup.BASIC, PayMonthSheet.OT_ROW_ID)
        assertEquals(333, ot.amountCents)
        assertNull(ot.engineCents) // 不补引擎戳 → 不进对账，纯手填口径不变
        assertNull(ot.source)
    }
}
