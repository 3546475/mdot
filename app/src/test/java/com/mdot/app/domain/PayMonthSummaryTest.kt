package com.mdot.app.domain

import com.mdot.app.domain.model.DEFAULT_COLLAPSED_GROUPS
import com.mdot.app.domain.model.IncomeSlice
import com.mdot.app.domain.model.IncomeSliceKind
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记月合计口径（docs/20 P0-2）：实发 = 应发(基本+补贴) − 扣款 − 其他。
 * 金额全程「分」（Long），逐条求和无舍入（硬规则 1）。
 */
class PayMonthSummaryTest {

    private fun sheet(
        basic: List<Long> = listOf(230_000, 19_830, 0),
        subsidy: List<Long> = listOf(0),
        deduction: List<Long> = listOf(0, 13_220, 6_610),
        other: List<Long> = listOf(48_000, 32_000, 15_000),
    ) = PayMonthSheet(
        basic = basic.mapIndexed { i, c -> PayMonthItem(i + 1L, "b$i", c) },
        subsidy = subsidy.mapIndexed { i, c -> PayMonthItem(i + 1L, "s$i", c) },
        deduction = deduction.mapIndexed { i, c -> PayMonthItem(i + 1L, "d$i", c) },
        other = other.mapIndexed { i, c -> PayMonthItem(i + 1L, "o$i", c) },
    )

    @Test
    fun `实发等于应发减扣款减其他`() {
        val s = sheet()
        assertEquals(249_830, s.incomeCents)
        assertEquals(19_830, s.deductionCents)
        assertEquals(95_000, s.otherCents)
        assertEquals(135_000, s.netCents)
    }

    @Test
    fun `空单据全为 0`() {
        val empty = PayMonthSheet()
        assertEquals(0, empty.incomeCents)
        assertEquals(0, empty.deductionCents)
        assertEquals(0, empty.otherCents)
        assertEquals(0, empty.netCents)
    }

    @Test
    fun `扣除超过应发时实发为负`() {
        val s = sheet(basic = listOf(1_000), subsidy = emptyList(), deduction = listOf(5_000), other = emptyList())
        assertEquals(-4_000, s.netCents)
    }

    @Test
    fun `出厂单调休行已改名调休折现且 id 固定`() {
        val d = PayMonthSheet.default()
        val comp = d.basic.first { it.id == PayMonthSheet.COMP_ROW_ID }
        assertEquals("调休折现", comp.name)
    }

    @Test
    fun `应发构成按基本加班其他切开`() {
        // basic[0]=基本工资(id1) basic[1]=加班工资(id2) basic[2]=调休折现(id3)
        val s = sheet(basic = listOf(230_000, 19_830, 12_000), subsidy = listOf(5_000))
        assertEquals(
            listOf(
                IncomeSliceKind.BASE to 230_000L,
                IncomeSliceKind.OVERTIME to 19_830L,
                // 其他应发 = 调休折现 12_000 + 补贴 5_000（由合计反推，新增行也不会漏）
                IncomeSliceKind.OTHER_INCOME to 17_000L,
            ),
            s.incomeSlices().map { it.kind to it.cents },
        )
    }

    @Test
    fun `应发构成去掉 0 段且合计与应发一致`() {
        val s = sheet(basic = listOf(230_000, 0, 0), subsidy = listOf(0))
        val slices = s.incomeSlices()
        assertEquals(1, slices.size)
        assertEquals(IncomeSliceKind.BASE, slices.first().kind)
        assertEquals(s.incomeCents, slices.sumOf { it.cents })
    }

    @Test
    fun `分组折叠默认值为四个分组全折叠`() {
        // v0.7.4：进页面先看汇总卡，四个分组默认收起（用户动过折叠后按持久化值走）
        assertEquals(PayGroup.entries.size, DEFAULT_COLLAPSED_GROUPS.size)
        PayGroup.entries.forEach { assertTrue(it.name in DEFAULT_COLLAPSED_GROUPS) }
    }

    @Test
    fun `无收入时构成为空`() {
        val s = sheet(basic = listOf(0, 0, 0), subsidy = listOf(0))
        assertEquals(emptyList<IncomeSlice>(), s.incomeSlices())
    }
}
