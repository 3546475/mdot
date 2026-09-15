package com.mdot.app.feature.stats

import com.mdot.app.domain.model.AdvancePurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * SiteDetailRow.stableKey 回归测试（13 文档 B6-04 修复的二次修正）：
 *
 * 背景：明细页 LazyColumn 的 key 最初用 `date_kind_itemName_purpose` 组合——同一天记两笔
 * 同用途借支时该组合完全相同 → `IllegalArgumentException: Key "..." was already used` 崩溃
 * （v0.6.12 后引入，用户极易触发）。改为 `kind + 源记录 id`：跨表自增 id 会撞，故必须带
 * kind 段前缀（同 11 文档 005 教训）。
 *
 * 本测试断言：同一天同用途的多笔借支（仅 id 不同）产生互不相同的 key，且不同 kind 的
 * 相同 id 也不撞。
 */
class SiteDetailStableKeyTest {

    private fun advance(id: Long, purpose: AdvancePurpose = AdvancePurpose.OTHER) = SiteDetailRow(
        date = LocalDate.of(2026, 9, 15),
        kind = SiteDetailKind.ADVANCE,
        id = id,
        amountCents = 100_00,
        purpose = purpose,
    )

    @Test
    fun `同一天同用途多笔借支的 key 互不相同`() {
        // 崩溃场景：三笔同日期同用途借支（修复前三者 key 完全相同）
        val rows = listOf(advance(1), advance(2), advance(3))
        val keys = rows.map { it.stableKey }
        assertEquals("key 数量应与行数一致", rows.size, keys.toSet().size)
    }

    @Test
    fun `不同 kind 的相同 id 不撞（跨表自增 id 需段前缀隔离）`() {
        val att = SiteDetailRow(LocalDate.of(2026, 9, 15), SiteDetailKind.WORK, id = 1)
        val piece = SiteDetailRow(LocalDate.of(2026, 9, 15), SiteDetailKind.PIECE, id = 1)
        val adv = SiteDetailRow(LocalDate.of(2026, 9, 15), SiteDetailKind.ADVANCE, id = 1)
        val partial = SiteDetailRow(LocalDate.of(2026, 9, 15), SiteDetailKind.PARTIAL, id = 1)
        val keys = listOf(att, piece, adv, partial).map { it.stableKey }
        assertEquals("四类同 id 记录应产生 4 个不同 key", 4, keys.toSet().size)
    }

    @Test
    fun `key 形如 kind_id`() {
        assertTrue(advance(7).stableKey.startsWith("ADVANCE_"))
        assertEquals("ADVANCE_7", advance(7).stableKey)
    }
}
