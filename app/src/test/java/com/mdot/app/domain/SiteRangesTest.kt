package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 21 文档 B1a：工地区间推导纯函数（本期待结 / 项目全周期 / 热点图窗口） */
class SiteRangesTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 28)

    // ---- resolve · UNSETTLED（本期待结） ----

    @Test
    fun `未结算区间 - 无结算单从首条记录日起`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.UNSETTLED,
            lastSettlementEnd = null,
            firstRecord = LocalDate.of(2026, 8, 3),
            lastRecord = LocalDate.of(2026, 9, 27),
            today = today,
        )
        assertEquals(LocalDate.of(2026, 8, 3), from)
        assertEquals(today, to)
    }

    @Test
    fun `未结算区间 - 有结算单从次日起`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.UNSETTLED,
            lastSettlementEnd = LocalDate.of(2026, 9, 15),
            firstRecord = LocalDate.of(2026, 8, 3),
            lastRecord = LocalDate.of(2026, 9, 27),
            today = today,
        )
        assertEquals(LocalDate.of(2026, 9, 16), from)
        assertEquals(today, to)
    }

    @Test
    fun `未结算区间 - 空项目退化为今天单日`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.UNSETTLED,
            lastSettlementEnd = null,
            firstRecord = null,
            lastRecord = null,
            today = today,
        )
        assertEquals(today, from)
        assertEquals(today, to)
    }

    @Test
    fun `未结算区间 - 结算截止今天则起点为明天且终点同起点`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.UNSETTLED,
            lastSettlementEnd = today,
            firstRecord = LocalDate.of(2026, 9, 1),
            lastRecord = today,
            today = today,
        )
        assertEquals(today.plusDays(1), from)
        assertEquals(today.plusDays(1), to)
    }

    // ---- resolve · PROJECT_SPAN（项目全周期） ----

    @Test
    fun `项目全周期 - 首条记录日到末条记录日`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.PROJECT_SPAN,
            lastSettlementEnd = LocalDate.of(2026, 9, 15), // 已结算不影响全周期
            firstRecord = LocalDate.of(2026, 5, 1),
            lastRecord = LocalDate.of(2026, 9, 27),
            today = today,
        )
        assertEquals(LocalDate.of(2026, 5, 1), from)
        assertEquals(LocalDate.of(2026, 9, 27), to)
    }

    @Test
    fun `项目全周期 - 空项目退化为今天单日`() {
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.PROJECT_SPAN,
            lastSettlementEnd = null,
            firstRecord = null,
            lastRecord = null,
            today = today,
        )
        assertEquals(today, from)
        assertEquals(today, to)
    }

    @Test
    fun `项目全周期 - 单条记录为单日`() {
        val only = LocalDate.of(2026, 7, 7)
        val (from, to) = SiteRanges.resolve(
            SiteRanges.Kind.PROJECT_SPAN,
            lastSettlementEnd = null,
            firstRecord = only,
            lastRecord = only,
            today = today,
        )
        assertEquals(only, from)
        assertEquals(only, to)
    }

    // ---- bucketize（柱状图分桶，B3） ----

    @Test
    fun `分桶 - 31 天内按日出柱无标签`() {
        val from = LocalDate.of(2026, 9, 1)
        val to = LocalDate.of(2026, 9, 30)
        val bars = SiteRanges.bucketize(from, to, mapOf(LocalDate.of(2026, 9, 15) to 2.5f))
        assertEquals(30, bars.size)
        assertEquals(from, bars.first().date)
        assertEquals(0f, bars.first().value)
        assertEquals(2.5f, bars[14].value)
        assertTrue(bars.all { it.label == null })
    }

    @Test
    fun `分桶 - 超 31 天按周聚合且每桶带周起标签`() {
        val from = LocalDate.of(2026, 5, 1) // 周五
        val to = LocalDate.of(2026, 9, 28)
        val bars = SiteRanges.bucketize(from, to, mapOf(LocalDate.of(2026, 5, 2) to 1f, LocalDate.of(2026, 5, 4) to 2f))
        // 第一桶 = 4/27 起的周一（含 5/2 周六）；第二桶 = 5/4 起的周一（含 5/4 本身）
        assertEquals(LocalDate.of(2026, 4, 27), bars.first().date)
        assertEquals(1f, bars.first().value)
        assertEquals(LocalDate.of(2026, 5, 4), bars[1].date)
        assertEquals(2f, bars[1].value)
        // 每桶都带「M/d」周起标签（标注疏密由组件按桶数决定）
        assertEquals("4/27", bars.first().label)
        assertTrue(bars.all { it.label != null })
    }

    @Test
    fun `分桶 - 反向区间为空`() {
        val bars = SiteRanges.bucketize(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), emptyMap())
        assertTrue(bars.isEmpty())
    }
}
