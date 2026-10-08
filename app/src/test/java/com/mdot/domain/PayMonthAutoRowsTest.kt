package com.mdot.domain

import com.mdot.app.domain.PayMonthDayBasis
import com.mdot.app.domain.PayMonthDayCounts
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.applyRecordSync
import com.mdot.app.domain.isUserOwned
import com.mdot.app.domain.livePreviewSheet
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记月「引擎管钱的行」自动推导（v0.7.8.3）：按日行每月自动算 + 全勤奖自动发。
 *
 * 本文件守两条最容易出错的链路：
 * ① **全勤奖的配置金额不能被"请假后变 0"吃掉**（否则恢复满勤后永远发不出来）；
 * ② 自动行**不能被 isUserOwned 保护住**（否则自动推导形同虚设）。
 */
class PayMonthAutoRowsTest {

    private val out = PayrollCalculator.Output(
        breakdowns = emptyList(), otMinutes = 0, paidOtMinutes = 0, otPayCents = 0,
        leaveMinutes = 0, leaveDeductCents = 0, compBalanceMinutes = 0,
        baseIncludedCents = 0, incomeCents = 0, otPayByTier = emptyMap(), leaveDeductByType = emptyMap(),
    )

    /** 22 个应出勤日、记了 5 天加班、周期 30 天 */
    private val counts = PayMonthDayCounts(
        standardMinutes = 22 * 480,
        recordDayCount = 5,
        calendarDayCount = 30,
        leaveMinutes = 0,
    )

    private fun sheetWith(
        name: String,
        rate: Long,
        basis: PayMonthDayBasis = PayMonthDayBasis.STANDARD,
        deductLeave: Boolean = false,
        amount: Long = 0,
    ): PayMonthSheet {
        val base = PayMonthSheet.default()
        val row = PayMonthItem(
            id = base.subsidy.maxOf { it.id } + 1,
            name = name,
            amountCents = amount,
            dayRateCents = rate,
            dayBasis = basis,
            dayDeductLeave = deductLeave,
        )
        return base.copy(subsidy = base.subsidy + row)
    }

    private fun fill(sheet: PayMonthSheet, c: PayMonthDayCounts = counts) =
        applyRecordSync(sheet, out, fillBase = false, syncedAt = "2026-10-08", dayCounts = c)

    private fun rowOf(sheet: PayMonthSheet, name: String) = sheet.subsidy.first { it.name == name }

    // ---- 按日行 ----

    @Test
    fun `按日行 - 每月自动算金额`() {
        val out0 = fill(sheetWith("日补贴", 5_000))
        val row = rowOf(out0, "日补贴")
        assertEquals(110_000L, row.amountCents)          // 5000 × 22
        assertEquals(PayMonthSource.SYNCED, row.source)
        assertEquals(110_000L, row.engineCents)
        assertEquals(22.0, row.derivationDays!!, 1e-9)
        assertEquals(5_000L, row.derivationUnitCents)
    }

    @Test
    fun `按日行 - 记录日口径按记录天数算`() {
        val s = sheetWith("日补贴", 5_000, basis = PayMonthDayBasis.RECORD)
        assertEquals(25_000L, rowOf(fill(s), "日补贴").amountCents)   // 5000 × 5
    }

    @Test
    fun `按日行 - 自然日口径按周期天数算`() {
        val s = sheetWith("日补贴", 5_000, basis = PayMonthDayBasis.CALENDAR)
        assertEquals(150_000L, rowOf(fill(s), "日补贴").amountCents)  // 5000 × 30
    }

    @Test
    fun `按日行 - 扣请假开关生效`() {
        val withLeave = counts.copy(leaveMinutes = 480)
        val s = sheetWith("日补贴", 5_000, deductLeave = true)
        assertEquals(105_000L, rowOf(fill(s, withLeave), "日补贴").amountCents)  // 5000 × 21
        // 开关关着则不受影响
        val noDeduct = sheetWith("日补贴", 5_000, deductLeave = false)
        assertEquals(110_000L, rowOf(fill(noDeduct, withLeave), "日补贴").amountCents)
    }

    @Test
    fun `按日行 - 四个组都会自动算`() {
        val base = PayMonthSheet.default()
        val mk = { id: Long, g: String -> PayMonthItem(id, "按日-$g", dayRateCents = 1_000) }
        val s = base.copy(
            basic = base.basic + mk(101L, "basic"),
            subsidy = base.subsidy + mk(102L, "subsidy"),
            deduction = base.deduction + mk(103L, "deduction"),
            other = base.other + mk(104L, "other"),
        )
        val f = fill(s)
        assertEquals(22_000L, f.basic.first { it.id == 101L }.amountCents)
        assertEquals(22_000L, f.subsidy.first { it.id == 102L }.amountCents)
        assertEquals(22_000L, f.deduction.first { it.id == 103L }.amountCents)
        assertEquals(22_000L, f.other.first { it.id == 104L }.amountCents)
    }

    @Test
    fun `按日行 - 没配单价的行不受影响`() {
        val base = PayMonthSheet.default()
        val s = base.copy(subsidy = base.subsidy.map { it.copy(amountCents = 12_345) })
        assertEquals(12_345L, rowOf(fill(s), "其它补贴").amountCents)
    }

    // ---- 全勤奖：2026-10-08 已由自动降为**手动填**，引擎不再碰它 ----

    @Test
    fun `全勤奖 - 引擎不再自动改它的金额`() {
        val base = PayMonthSheet.default().let { s ->
            s.copy(subsidy = s.subsidy.map {
                if (it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID) it.copy(amountCents = 30_000) else it
            })
        }
        // 即使请假（旧逻辑会归零），手填的金额也必须原样保留
        val out0 = fill(base, counts.copy(leaveMinutes = 480))
        assertEquals("全勤奖是手填项，引擎不得归零", 30_000L, rowOf(out0, "全勤奖").amountCents)
        assertNull("引擎不该给全勤奖打来源戳（打了就会进对账/恢复引擎值）", rowOf(fill(base), "全勤奖").source)
    }

    @Test
    fun `全勤奖 - 实时预览保留用户填的金额`() {
        val base = PayMonthSheet.default().let { s ->
            s.copy(subsidy = s.subsidy.map {
                if (it.id == PayMonthSheet.FULL_ATTENDANCE_ROW_ID) it.copy(amountCents = 30_000) else it
            })
        }
        val preview = livePreviewSheet(base, out, false, "2026-10-08", dayCounts = counts.copy(leaveMinutes = 480))
        assertEquals(30_000L, rowOf(preview, "全勤奖").amountCents)
    }

    // ---- 自动行不能被"用户所有"保护住 ----

    @Test
    fun `自动行 - 存盘态也判为非用户所有`() {
        // 首次同步前的存盘行：amountCents 非 0、无引擎戳 —— 老逻辑会判成"用户手填"从而保护它
        val perDay = PayMonthItem(1000L, "日补贴", amountCents = 99_999, dayRateCents = 5_000)
        assertFalse("按日行不该被判成用户所有", perDay.isUserOwned())
    }

    @Test
    fun `全勤奖 - 降为手填后必须受"用户所有"保护`() {
        // 与上一例相反：全勤奖不再是自动行，手填的金额必须被保护住（否则会被引擎/预览冲掉）
        val full = PayMonthItem(PayMonthSheet.FULL_ATTENDANCE_ROW_ID, "全勤奖", amountCents = 30_000)
        assertTrue("全勤奖是手填项，必须判成用户所有", full.isUserOwned())
    }

    @Test
    fun `自动行 - 普通手填行仍受保护`() {
        val manual = PayMonthItem(1000L, "餐补", amountCents = 5_000)
        assertTrue("纯手填行必须继续受保护，否则用户填的数会被冲掉", manual.isUserOwned())
    }

    @Test
    fun `实时预览 - 自动行取引擎值而非存盘旧值`() {
        val s = sheetWith("日补贴", 5_000, amount = 999_999)   // 存盘里是脏的旧值
        val preview = livePreviewSheet(s, out, false, "2026-10-08", dayCounts = counts)
        assertEquals("实时预览必须给出引擎值", 110_000L, rowOf(preview, "日补贴").amountCents)
    }

    @Test
    fun `实时预览 - 手填行仍保留用户值`() {
        val base = PayMonthSheet.default()
        val s = base.copy(subsidy = base.subsidy + PayMonthItem(1000L, "餐补", amountCents = 6_666))
        val preview = livePreviewSheet(s, out, false, "2026-10-08", dayCounts = counts)
        assertEquals(6_666L, rowOf(preview, "餐补").amountCents)
    }
}