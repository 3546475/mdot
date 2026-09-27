package com.mdot.app.domain

import com.mdot.app.domain.model.DEFAULT_COLLAPSED_GROUPS
import com.mdot.app.domain.model.IncomeSlice
import com.mdot.app.domain.model.IncomeSliceKind
import com.mdot.app.domain.model.InsuranceKind
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.insuranceKindOf
import com.mdot.app.domain.model.nextUserRowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---- 出厂固定行补齐（2026-09-27 新增「全勤奖」）----

    @Test
    fun `出厂单据补贴组含全勤奖且排在其它补贴之前`() {
        val d = PayMonthSheet.default()
        assertEquals(listOf("全勤奖", "其它补贴"), d.subsidy.map { it.name })
        assertTrue(d.subsidy.all { it.builtin })
    }

    @Test
    fun `老单子补上缺的固定行，且不动已有行与用户行的顺序`() {
        // 模拟升级前存的单子：补贴组只有「其它补贴」（出厂行，builtin），用户又自己加了一行「餐补」
        val old = PayMonthSheet(
            subsidy = listOf(
                PayMonthItem(4, "其它补贴", 100, builtin = true),
                PayMonthItem(5, "餐补", 200),
            ),
        )
        val filled = old.withBuiltinRows()
        // 全勤奖落在**出厂位置**（其它补贴之前）而不是堆末尾；用户行仍在最后，金额都不丢
        assertEquals(listOf("全勤奖", "其它补贴", "餐补"), filled.subsidy.map { it.name })
        assertEquals(listOf(0L, 100L, 200L), filled.subsidy.map { it.amountCents })
        assertEquals(listOf(true, true, false), filled.subsidy.map { it.builtin })
    }

    @Test
    fun `出厂单据补齐后不变`() {
        val d = PayMonthSheet.default()
        assertEquals(d, d.withBuiltinRows())
    }

    // ---- 跨组 id 撞车（2026-09-27 用户报：扣款组加两行，弹窗里出现社保/公积金设置）----

    @Test
    fun `扣款组用户行拿到 8 与 9 也不被误判为社保公积金`() {
        // 复现用户现场：扣款组出厂 [5,6,7]，按老算法加两行正好拿到 id 8/9
        listOf(PayMonthItem(8, "罚款"), PayMonthItem(9, "迟到")).forEach {
            assertNull(it.insuranceKindOf(PayGroup.DEDUCTION))
        }
    }

    @Test
    fun `只有其他组的出厂社保公积金行才判为设置行`() {
        val d = PayMonthSheet.default()
        val social = d.other.first { it.id == PayMonthSheet.SOCIAL_ROW_ID }
        val fund = d.other.first { it.id == PayMonthSheet.FUND_ROW_ID }
        assertEquals(InsuranceKind.SOCIAL, social.insuranceKindOf(PayGroup.OTHER))
        assertEquals(InsuranceKind.FUND, fund.insuranceKindOf(PayGroup.OTHER))
        // 同样的 id 换个组就不是
        assertNull(social.insuranceKindOf(PayGroup.DEDUCTION))
    }

    @Test
    fun `其他组里用户自加的行不是设置行`() {
        assertNull(PayMonthItem(12, "水电费").insuranceKindOf(PayGroup.OTHER))
    }

    @Test
    fun `用户行 id 从独立号段起，永不与出厂固定行相撞`() {
        val d = PayMonthSheet.default()
        val builtinIds = (d.basic + d.subsidy + d.deduction + d.other).map { it.id }
        // 出厂固定行 id 全局唯一，且都落在用户号段之下
        assertEquals(builtinIds.size, builtinIds.distinct().size)
        assertTrue(builtinIds.all { it < PayMonthSheet.USER_ROW_ID_BASE })
        // 每组的下一个用户行 id 都在号段内、不与任何出厂行相撞
        listOf(d.basic, d.subsidy, d.deduction, d.other).forEach { rows ->
            val next = rows.nextUserRowId()
            assertTrue(next >= PayMonthSheet.USER_ROW_ID_BASE)
            assertTrue(next !in builtinIds)
        }
    }

    @Test
    fun `用户行 id 接在已有用户行之后，不回退到出厂号段`() {
        // 扣款组老数据：用户行就是 8/9（老算法给的），下一个也不能再拿 10
        val rows = listOf(
            PayMonthItem(5, "其它扣款"), PayMonthItem(6, "事假"), PayMonthItem(7, "病假"),
            PayMonthItem(8, "罚款"), PayMonthItem(9, "迟到"),
        )
        assertEquals(PayMonthSheet.USER_ROW_ID_BASE, rows.nextUserRowId())
    }
}
