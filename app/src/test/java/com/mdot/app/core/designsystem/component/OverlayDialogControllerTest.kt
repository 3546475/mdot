package com.mdot.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 覆盖层控制器的**栈语义**（v0.7.8.1 之后把状态从进程级 `object` 收敛为组合级 controller，
 * 见 `OverlayDialog.kt` 的注释；此前这段逻辑不可测）。
 */
class OverlayDialogControllerTest {

    private fun entry(tag: String): OverlayEntry =
        OverlayEntry(onDismiss = {}, content = {}).also { it.tag = tag }

    /** 测试期识别用的标签（`OverlayEntry` 本体不需要这个字段，故用扩展属性） */
    private var OverlayEntry.tag: String
        get() = tags.getValue(this)
        set(v) { tags[this] = v }

    private val tags = java.util.IdentityHashMap<OverlayEntry, String>()

    @Test
    fun `空栈时 current 为 null`() {
        assertNull(OverlayDialogController().current)
    }

    @Test
    fun `后进先出：current 恒为最后 push 的那条`() {
        val c = OverlayDialogController()
        val a = entry("a")
        val b = entry("b")
        c.push(a)
        assertSame(a, c.current)
        c.push(b)
        assertSame(b, c.current)
    }

    @Test
    fun `移除栈顶后下层自动恢复可见`() {
        val c = OverlayDialogController()
        val a = entry("a")
        val b = entry("b")
        c.push(a)
        c.push(b)
        c.remove(b)
        assertSame("栈顶关闭后应回到下层", a, c.current)
        c.remove(a)
        assertNull("两条都关掉后应为空", c.current)
    }

    @Test
    fun `重复移除同一条是幂等的且不误伤他人`() {
        val c = OverlayDialogController()
        val a = entry("a")
        val b = entry("b")
        c.push(a)
        c.push(b)
        c.remove(b)
        c.remove(b)               // 再删一次（如 onDispose 被触发两次）
        assertEquals(1, c.stack.size)
        assertSame(a, c.current)
    }
}
