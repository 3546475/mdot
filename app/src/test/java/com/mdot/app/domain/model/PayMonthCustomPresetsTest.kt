package com.mdot.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义添加预设（2026-10-09 用户要求：「+ chip 新建预设，持久化，不会在下一个月丢失」）。
 *
 * 与 [PayMonthTemplates] 的分工是本类最容易被搞混的点：
 * - 模板管**单据里的行**（本月加的行下月自动还在）
 * - 本类管**弹窗里的候选清单**（"我常加哪几项"，与月份无关）
 */
class PayMonthCustomPresetsTest {

    @Test
    fun `空实例 - 任何分组都没有预设`() {
        val p = PayMonthCustomPresets()
        PayGroup.entries.forEach { assertEquals(emptyList<String>(), p.of(it)) }
    }

    @Test
    fun `新建 - 追加到对应分组`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        assertEquals(listOf("季度奖"), p.of(PayGroup.SUBSIDY))
    }

    @Test
    fun `新建 - 不影响其它分组`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        assertEquals(emptyList<String>(), p.of(PayGroup.BASIC))
        assertEquals(emptyList<String>(), p.of(PayGroup.DEDUCTION))
        assertEquals(emptyList<String>(), p.of(PayGroup.OTHER))
    }

    @Test
    fun `新建 - 保持添加顺序`() {
        val p = PayMonthCustomPresets()
            .plus(PayGroup.SUBSIDY, "季度奖")!!
            .plus(PayGroup.SUBSIDY, "项目奖")!!
            .plus(PayGroup.SUBSIDY, "高温补贴")!!
        assertEquals(listOf("季度奖", "项目奖", "高温补贴"), p.of(PayGroup.SUBSIDY))
    }

    @Test
    fun `新建 - 去掉首尾空白`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "  季度奖  ")!!
        assertEquals(listOf("季度奖"), p.of(PayGroup.SUBSIDY))
    }

    @Test
    fun `新建 - 空名或纯空白返回 null（调用方据此不写盘）`() {
        assertNull(PayMonthCustomPresets().plus(PayGroup.SUBSIDY, ""))
        assertNull(PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "   "))
    }

    @Test
    fun `新建 - 同名返回 null（不会加出两条一样的预设）`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        assertNull("重复建同名预设应被拒", p.plus(PayGroup.SUBSIDY, "季度奖"))
        assertNull("去空白后同名也算重复", p.plus(PayGroup.SUBSIDY, "  季度奖 "))
    }

    @Test
    fun `新建 - 不同分组可以同名（两个组互不干扰）`() {
        val p = PayMonthCustomPresets()
            .plus(PayGroup.SUBSIDY, "季度奖")!!
            .plus(PayGroup.OTHER, "季度奖")!!
        assertEquals(listOf("季度奖"), p.of(PayGroup.SUBSIDY))
        assertEquals(listOf("季度奖"), p.of(PayGroup.OTHER))
    }

    @Test
    fun `新建 - 返回新实例不改原实例（快照语义）`() {
        val before = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        val after = before.plus(PayGroup.SUBSIDY, "项目奖")!!
        assertEquals("原实例不该被就地改动", listOf("季度奖"), before.of(PayGroup.SUBSIDY))
        assertEquals(listOf("季度奖", "项目奖"), after.of(PayGroup.SUBSIDY))
    }

    @Test
    fun `未知分组名 - 读取不崩（老包或脏数据）`() {
        val p = PayMonthCustomPresets(mapOf("NOT_A_GROUP" to listOf("x")))
        PayGroup.entries.forEach { assertTrue(p.of(it).isEmpty()) }
    }

    // ---- 删除（2026-10-09：新增的预设原先没有删除入口）----

    @Test
    fun `删除 - 移除指定项其余保留且保持顺序`() {
        val p = PayMonthCustomPresets()
            .plus(PayGroup.SUBSIDY, "季度奖")!!
            .plus(PayGroup.SUBSIDY, "项目奖")!!
            .plus(PayGroup.SUBSIDY, "高温津贴")!!
        val after = p.minus(PayGroup.SUBSIDY, "项目奖")!!
        assertEquals(listOf("季度奖", "高温津贴"), after.of(PayGroup.SUBSIDY))
    }

    @Test
    fun `删除 - 不存在则返回 null（调用方据此跳过写盘）`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        assertNull(p.minus(PayGroup.SUBSIDY, "没有这一项"))
        assertNull(PayMonthCustomPresets().minus(PayGroup.SUBSIDY, "季度奖"))
    }

    @Test
    fun `删除 - 只删本分组不误伤同名项`() {
        val p = PayMonthCustomPresets()
            .plus(PayGroup.SUBSIDY, "季度奖")!!
            .plus(PayGroup.OTHER, "季度奖")!!
        val after = p.minus(PayGroup.SUBSIDY, "季度奖")!!
        assertTrue("补助组应已删空", after.of(PayGroup.SUBSIDY).isEmpty())
        assertEquals("其它项目组那条同名项不该被牵连", listOf("季度奖"), after.of(PayGroup.OTHER))
    }

    @Test
    fun `删除 - 删空后该分组的键被移除（不留空列表）`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        val after = p.minus(PayGroup.SUBSIDY, "季度奖")!!
        assertFalse("空列表不该留在 map 里（否则恢复侧 isNotEmpty 判断会误判）", after.byGroup.containsKey(PayGroup.SUBSIDY.name))
        assertTrue(after.byGroup.isEmpty())
    }

    @Test
    fun `删除 - 不改原实例（快照语义）`() {
        val before = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        val after = before.minus(PayGroup.SUBSIDY, "季度奖")!!
        assertEquals("原实例不该被就地改动", listOf("季度奖"), before.of(PayGroup.SUBSIDY))
        assertTrue(after.of(PayGroup.SUBSIDY).isEmpty())
    }

    @Test
    fun `删除 - 加回来仍可（删不是拉黑）`() {
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        val removed = p.minus(PayGroup.SUBSIDY, "季度奖")!!
        val again = removed.plus(PayGroup.SUBSIDY, "季度奖")!!
        assertEquals(listOf("季度奖"), again.of(PayGroup.SUBSIDY))
    }

    // ---- 这是「不会在下个月丢失」的核心保障：跨月读取拿到的仍是同一份清单 ----

    @Test
    fun `跨月 - 预设清单与月份无关（建一次每月都在）`() {
        // 预设本身不带月份字段，是"用户习惯清单"而不是"某月的单据"
        val p = PayMonthCustomPresets().plus(PayGroup.SUBSIDY, "季度奖")!!
        // 序列化往返（DataStore 里就是这么存的）
        val text = kotlinx.serialization.json.Json.encodeToString(PayMonthCustomPresets.serializer(), p)
        val back = kotlinx.serialization.json.Json.decodeFromString(PayMonthCustomPresets.serializer(), text)
        assertEquals(listOf("季度奖"), back.of(PayGroup.SUBSIDY))
    }
}
