package com.mdot.app.core.designsystem

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字体放大适配的算式守门（docs 03 §8：字体缩放 1.0–1.3 布局不破碎）。
 *
 * 只锁算式本身（行高 + 上下余量、不低于下限）。行高换算走 [Density]，用的是真机同一套 sp→dp
 * 逻辑（Android 14 起字放大是非线性的，故不假设「行高 = 字号 × fontScale」）。
 */
class TextScaleTest {

    private fun textDp(sp: Float, fontScale: Float): Float =
        with(Density(density = 2.75f, fontScale = fontScale)) { sp.sp.toDp().value }

    @Test
    fun `缩放1倍时胶囊行高等于设计基准24dp`() {
        // labelMedium 行高 16sp @fontScale 1.0 ⇒ 16dp，+ 上下各 Spacing.xs(4dp)
        val height = fittedHeight(textDp(16f, 1f).dp, 4.dp, 24.dp)
        assertEquals(24f, height.value, 0.01f)
    }

    @Test
    fun `缩放1倍时分段控件高度等于设计基准34dp`() {
        val height = fittedHeight(textDp(20f, 1f).dp, 7.dp, 34.dp)
        assertEquals(34f, height.value, 0.01f)
    }

    @Test
    fun `胶囊行高随字体放大单调变高且始终装得下文字`() {
        val scales = listOf(1f, 1.3f, 1.5f, 2f)
        val heights = scales.map { f ->
            val lineHeight = textDp(16f, f)
            val height = fittedHeight(lineHeight.dp, 4.dp, 24.dp)
            assertTrue("fontScale=$f 行高 $height 装不下文字行高 $lineHeight", height.value >= lineHeight)
            height.value
        }
        assertEquals(24f, heights.first(), 0.01f)
        heights.zipWithNext { a, b -> assertTrue("行高未随字体缩放增长：$a → $b", b > a) }
    }

    @Test
    fun `分段控件放大后仍能塞进56dp顶栏`() {
        listOf(1f, 1.3f, 1.5f).forEach { f ->
            val lineHeight = textDp(20f, f)
            val height = fittedHeight(lineHeight.dp, 7.dp, 34.dp)
            assertTrue("fontScale=$f 时分段控件 $height 超过顶栏 56dp", height.value <= 56f)
            assertTrue("fontScale=$f 时分段控件 $height 装不下文字行高 $lineHeight", height.value >= lineHeight)
        }
    }

    @Test
    fun `缩小字号时不低于设计下限`() {
        // fontScale < 1 时行高变小，仍按 24dp 立着（保持既有版式）
        val height = fittedHeight(textDp(16f, 0.85f).dp, 4.dp, 24.dp)
        assertEquals(24f, height.value, 0.01f)
    }
}
