package com.mdot.app.core.designsystem.miuix

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdot.app.core.designsystem.EngineIcons
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.domain.model.ThemeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * miuix 引擎装配层守门（外观页「主题引擎」= MIUIX，v0.7.8）：
 * 1. **中性色映射**：灰底白卡（浅）/ 黑底深灰卡（深）——两套引擎观感差异的主来源；
 * 2. **强调色注入**：primary/secondary/tertiary 各档来自配色源，语义色不丢；
 * 3. **排印映射**：miuix 字号词表 + 标题加粗，hero 数值锚点字号不许动；
 * 4. **圆角令牌**：Radius.card/textField 随引擎切 16dp 并可复位。
 */
class MiuixEngineTest {

    /** 强调色源：刻意用「一眼能认出」的假配色，防止误拿默认色通过 */
    private val accent = lightColorScheme(
        primary = Color(0xFF123456),
        onPrimaryContainer = Color(0xFF654321),
        secondary = Color(0xFF222222),
        tertiary = Color(0xFF00AA88),
    )

    @Test
    fun `浅色中性映射：灰底白卡（miuix 语义，与 M3 白底灰卡相反）`() {
        val s = miuixColorScheme(accent, dark = false)
        assertEquals(Color(0xFFF7F7F7), s.background)
        assertEquals(Color(0xFFF7F7F7), s.surface)
        assertEquals(Color(0xFFFFFFFF), s.surfaceContainer)
        assertEquals(Color(0xFFF0F0F0), s.surfaceContainerHigh)
        assertEquals(Color(0xFFE8E8E8), s.surfaceContainerHighest)
        assertEquals(Color(0x99000000), s.onSurfaceVariant)
        assertEquals(Color(0xFFD9D9D9), s.outline)
        assertEquals(Color(0xFFE0E0E0), s.outlineVariant)
    }

    @Test
    fun `深色中性映射：黑底深灰卡`() {
        val s = miuixColorScheme(accent, dark = true)
        assertEquals(Color(0xFF000000), s.background)
        assertEquals(Color(0xFF242424), s.surfaceContainer)
        assertEquals(Color(0xFF2D2D2D), s.surfaceContainerHigh)
        assertEquals(Color(0xFF3F3F3F), s.surfaceContainerHighest)
        assertEquals(Color(0x80FFFFFF), s.onSurfaceVariant)
    }

    @Test
    fun `强调色与语义色来自配色源（配色与动态取色在 MIUIX 下照常生效）`() {
        val s = miuixColorScheme(accent, dark = false)
        assertEquals(accent.primary, s.primary)
        assertEquals(accent.onPrimaryContainer, s.onPrimaryContainer)
        assertEquals(accent.secondary, s.secondary)
        // 调休 = tertiary 语义色不许丢
        assertEquals(accent.tertiary, s.tertiary)
        assertEquals(accent.primary, s.surfaceTint)
    }

    @Test
    fun `错误色与压暗来自 miuix 令牌`() {
        val s = miuixColorScheme(accent, dark = false)
        assertEquals(Color(0xFFE94634), s.error)
        assertEquals(Color(0xFFFDF6F4), s.errorContainer)
        assertEquals(Color(0x4D000000), s.scrim)
    }

    @Test
    fun `排印映射 miuix 字号词表且标题加粗`() {
        assertEquals(20.sp, MiuixTypography.titleLarge.fontSize)
        assertEquals(FontWeight.Bold, MiuixTypography.titleLarge.fontWeight)
        assertEquals(17.sp, MiuixTypography.titleMedium.fontSize)
        assertEquals(FontWeight.Bold, MiuixTypography.titleMedium.fontWeight)
        assertEquals(FontWeight.Bold, MiuixTypography.titleSmall.fontWeight)
        assertEquals(13.sp, MiuixTypography.bodySmall.fontSize)
        assertEquals(13.sp, MiuixTypography.labelMedium.fontSize)
    }

    @Test
    fun `hero 数值锚点字号不随引擎缩放`() {
        val base = Typography()
        assertEquals(base.displayLarge.fontSize, MiuixTypography.displayLarge.fontSize)
        assertEquals(base.displaySmall.fontSize, MiuixTypography.displaySmall.fontSize)
        assertEquals(base.headlineMedium.fontSize, MiuixTypography.headlineMedium.fontSize)
    }

    @Test
    fun `数字 tnum 特性覆盖标题档（03 文档排印条款）`() {
        assertEquals("tnum", MiuixTypography.titleMedium.fontFeatureSettings)
        assertEquals("tnum", MiuixTypography.headlineMedium.fontFeatureSettings)
    }

    @Test
    fun `圆角令牌随引擎切换并可复位`() {
        Radius.applyEngine(ThemeEngine.MIUIX)
        assertEquals(16.dp, Radius.card)
        assertEquals(16.dp, Radius.textField)
        Radius.applyEngine(ThemeEngine.MD3)
        assertEquals(24.dp, Radius.card)
        assertEquals(20.dp, Radius.textField)
    }

    // ---- v0.7.8 特性层：squircle / 弹簧 / 图标分发 ----

    @Test
    fun `圆角形状随引擎分发（MIUIX=squircle，MD3=圆角矩形）`() {
        Radius.applyEngine(ThemeEngine.MIUIX)
        assertTrue(engineShape(16.dp) is SquircleShape)
        Radius.applyEngine(ThemeEngine.MD3)
        assertTrue(engineShape(16.dp) is RoundedCornerShape)
    }

    @Test
    fun `MD3 图标资源映射保持（图标契约引用层锚点）`() {
        // MIUIX 分支已接 miuix-icons 库本体（ImageVector，编译期可验）；MD3 分支的 res id
        // 是图标契约「引用层」的锚点，不可改名/丢失
        assertEquals(com.mdot.app.R.drawable.ic_ms_keyboard_arrow_right, EngineIcons.chevronRes())
        assertEquals(com.mdot.app.R.drawable.ic_ms_check, EngineIcons.checkRes())
    }

    @Test
    fun `folme 弹簧刚度由响应时间反推`() {
        val spec = folmeSpring<Float>(damping = 1f, response = 0.2f)
        val expectedStiffness = ((2.0 * Math.PI / 0.2) * (2.0 * Math.PI / 0.2)).toFloat()
        assertEquals(expectedStiffness, spec.stiffness, 0.01f)
        assertEquals(1f, spec.dampingRatio, 0f)
    }

    @Test
    fun `运动方案是弹簧且空间档带轻微回弹`() {
        val spatial = MiuixMotionScheme.defaultSpatialSpec<Float>()
        assertTrue(spatial is SpringSpec)
        assertTrue((spatial as SpringSpec).dampingRatio < 1f) // 欠阻尼：MIUI 回弹感
        val effects = MiuixMotionScheme.defaultEffectsSpec<Float>()
        assertEquals(1f, (effects as SpringSpec).dampingRatio, 0f) // 效果档不回弹
    }

    @Test
    fun `miuix 形状档对齐 miuix 组件词表（对话框 32dp）`() {
        val probe = androidx.compose.ui.geometry.Size(100f, 100f)
        val density = androidx.compose.ui.unit.Density(1f)
        // miuix DialogContentLayout.DialogDefaults.CornerRadius = 32.dp；Card/Button = 16dp 档
        assertEquals(32f, (MiuixShapes.extraLarge as SquircleShape).topStart.toPx(probe, density), 0.01f)
        assertEquals(16f, (MiuixShapes.medium as SquircleShape).topStart.toPx(probe, density), 0.01f)
        assertEquals(12f, (MiuixShapes.small as SquircleShape).topStart.toPx(probe, density), 0.01f)
    }

    @Test
    fun `库组件配色桥：强调色注入、中性色留 miuix 本色`() {
        val libLight = miuixLibraryColors(dark = false, accent = lightColorScheme(primary = Color(0xFF123456)))
        assertEquals(Color(0xFF123456), libLight.primary) // 强调色来自配色源
        // 中性色 = miuix 库默认词表（lightColorScheme 工厂）不被覆盖
        val ref = top.yukonga.miuix.kmp.theme.lightColorScheme()
        assertEquals(ref.background, libLight.background)
        assertEquals(ref.surface, libLight.surface)
    }

    @Test
    fun `回弹阻尼曲线与求逆往返一致`() {
        val range = 2400f
        // 端点：全量程阻尼后落在 range/3
        assertEquals(range / 3f, dampingDistance(1f, range), 0.01f)
        // 单调：拖得越多位移越大
        assertTrue(dampingDistance(0.5f, range) > dampingDistance(0.2f, range))
        // 往返：拖拽位移 ↔ 显示位移互逆
        for (raw in listOf(50f, 200f, 600f, 1000f)) {
            val shown = dampingDistance(raw / range, range)
            assertEquals(raw, touchDistance(shown, range), 1f)
        }
    }
}
