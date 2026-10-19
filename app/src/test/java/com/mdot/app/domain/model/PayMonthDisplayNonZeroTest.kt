package com.mdot.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工资单长图的「只显示有金额的项目」（v0.7.9.0，用户 2026-10-20）：
 * **图**过滤 0 值行，**CSV** 记录全部 → 这个变换只给图用，且不能动金额口径。
 */
class PayMonthDisplayNonZeroTest {

    private val sheet = PayMonthSheet(
        basic = listOf(
            PayMonthItem(id = 1, name = "基本工资", amountCents = 500_000),
            PayMonthItem(id = 2, name = "加班工资", amountCents = 0),
            PayMonthItem(id = 3, name = "调休折现", amountCents = 12_000),
        ),
        subsidy = listOf(
            PayMonthItem(id = 4, name = "其它补贴", amountCents = 0),
        ),
        deduction = listOf(
            PayMonthItem(id = 11, name = "事假扣款", amountCents = 2_000),
        ),
        other = listOf(
            PayMonthItem(id = 21, name = "个人所得税", amountCents = 0),
        ),
    )

    @Test
    fun `0 值行被剔除 非 0 行保留原顺序`() {
        val shown = sheet.displayNonZero()
        assertEquals(listOf("基本工资", "调休折现"), shown.basic.map { it.name })
        assertEquals(listOf("事假扣款"), shown.deduction.map { it.name })
    }

    @Test
    fun `整组都是 0 时该组变空（长图据此不画组标题）`() {
        val shown = sheet.displayNonZero()
        assertTrue("补贴组全 0 ⇒ 空", shown.subsidy.isEmpty())
        assertTrue("其他组全 0 ⇒ 空", shown.other.isEmpty())
    }

    @Test
    fun `金额口径不变 只裁行`() {
        val shown = sheet.displayNonZero()
        // 0 值行对求和没有贡献 ⇒ 应发/扣款/其他/实发与过滤前完全一致
        assertEquals(sheet.incomeCents, shown.incomeCents)
        assertEquals(sheet.deductionCents, shown.deductionCents)
        assertEquals(sheet.otherCents, shown.otherCents)
        assertEquals(sheet.netCents, shown.netCents)
    }

    @Test
    fun `原单据不被修改（纯函数）`() {
        sheet.displayNonZero()
        assertEquals(3, sheet.basic.size)
        assertEquals(1, sheet.subsidy.size)
        assertEquals(1, sheet.other.size)
    }
}
