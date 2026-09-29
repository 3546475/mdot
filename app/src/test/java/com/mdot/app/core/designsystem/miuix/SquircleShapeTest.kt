package com.mdot.app.core.designsystem.miuix

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.domain.model.ThemeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * squircle 形状几何守门（miuix-squircle 路径的泛化版）：
 * Robolectric 跑（Path 是 Android 后端）。核心风险 = 大半径小尺寸翻折 / 出界，
 * 故断言路径包围盒恒在尺寸内、零圆角退化为矩形、四角不等不受影响。
 */
@RunWith(RobolectricTestRunner::class)
class SquircleShapeTest {

    private val density = Density(1f)

    private fun outlineOf(
        w: Float,
        h: Float,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
    ): Outline = SquircleShape(
        topStart = topStart.dp,
        topEnd = topEnd.dp,
        bottomEnd = bottomEnd.dp,
        bottomStart = bottomStart.dp,
    ).createOutline(Size(w, h), LayoutDirection.Ltr, density)

    @Test
    fun `单值构造四角同径（左上角独圆回归）`() {
        // 真机验收 bug 回归：早期单参 Dp 构造只给 topStart 赋值 → 只有左上角圆角
        val probe = Size(100f, 100f)
        val density = Density(1f)
        val s = SquircleShape(16.dp)
        assertEquals(16f, s.topStart.toPx(probe, density), 0.01f)
        assertEquals(16f, s.topEnd.toPx(probe, density), 0.01f)
        assertEquals(16f, s.bottomEnd.toPx(probe, density), 0.01f)
        assertEquals(16f, s.bottomStart.toPx(probe, density), 0.01f)
    }

    @Test
    fun `engineShape 单值与 MiuixShapes 四角同径`() {
        val probe = Size(100f, 100f)
        val density = Density(1f)
        Radius.applyEngine(ThemeEngine.MIUIX)
        val es = engineShape(20.dp) as SquircleShape
        assertEquals(20f, es.topStart.toPx(probe, density), 0.01f)
        assertEquals(20f, es.bottomEnd.toPx(probe, density), 0.01f)
        val slot = MiuixShapes.medium as SquircleShape
        assertEquals(16f, slot.topEnd.toPx(probe, density), 0.01f)
        assertEquals(16f, slot.bottomStart.toPx(probe, density), 0.01f)
    }

    @Test
    fun `零圆角退化为矩形轮廓`() {
        val outline = outlineOf(100f, 60f, 0f, 0f, 0f, 0f)
        assertTrue(outline is Outline.Generic)
        val bounds = (outline as Outline.Generic).path.getBounds()
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(100f, bounds.right, 0.01f)
        assertEquals(60f, bounds.bottom, 0.01f)
    }

    @Test
    fun `大半径小尺寸不翻折且包围盒不出界`() {
        // 半径远超短边一半：角块按比例回缩，包围盒必须仍在尺寸内
        val outline = outlineOf(40f, 20f, 500f, 500f, 500f, 500f)
        val bounds = (outline as Outline.Generic).path.getBounds()
        assertTrue("left ${bounds.left} 出界", bounds.left >= -0.01f)
        assertTrue("top ${bounds.top} 出界", bounds.top >= -0.01f)
        assertTrue("right ${bounds.right} 出界", bounds.right <= 40.01f)
        assertTrue("bottom ${bounds.bottom} 出界", bounds.bottom <= 20.01f)
    }

    @Test
    fun `四角不等的弹层形态包围盒不出界`() {
        val outline = outlineOf(300f, 400f, 28f, 28f, 0f, 0f)
        val bounds = (outline as Outline.Generic).path.getBounds()
        assertTrue(bounds.left >= -0.01f && bounds.top >= -0.01f)
        assertTrue(bounds.right <= 300.01f && bounds.bottom <= 400.01f)
    }

    @Test
    fun `等值与复制保持四角`() {
        val s = SquircleShape(16.dp, 16.dp, 0.dp, 0.dp)
        val copied = s.copy(
            topStart = CornerSize(16.dp),
            topEnd = CornerSize(16.dp),
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
        assertEquals(s, copied)
        assertTrue(s != SquircleShape(16.dp, 16.dp, 16.dp, 16.dp))
    }
}
