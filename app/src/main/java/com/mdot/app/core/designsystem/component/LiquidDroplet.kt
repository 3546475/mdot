package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.mdot.app.core.designsystem.GlassLiquidSpec

/*
 * 液态玻璃底栏的「液滴透镜」——照抄参考实现 memos-m
 * `docs/liquid-glass-bottom-bar.md` §6（LiquidLens.kt）。
 *
 * 液滴是一颗压在底栏玻璃上的透明药丸：
 *  - 内部按 1.14× 放大它覆盖的那部分底栏（图标、磨砂底都在里面）；
 *  - 只有外圈约 24% 高度的环带把取样向内拉（模拟液滴曲面），中段是纯放大；
 *  - 同一环带里红/蓝两路取样沿弯曲方向错开一点（色散），只在边缘出现细彩边。
 * 画出来的边只有两条：1dp 深色细线 + 底栏同款白色高光（见 JiabanBottomBar 的液滴节点）。
 *
 * 着色器里的距离场/梯度函数取自 Kyant0/AndroidLiquidGlass（Apache-2.0）。
 * ⚠️ 只在 API 33+（RuntimeShader）才有效果；低版本 [isRuntimeShaderSupported] 直接返回。
 */

private const val RoundedBoxSdf = """
float sdRoundedBox(float2 coord, float2 halfSize, float radius) {
    float2 q = abs(coord) - halfSize + float2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float2 gradRoundedBox(float2 coord, float2 halfSize, float radius) {
    float2 q = abs(coord) - halfSize + float2(radius);
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(coord) * normalize(max(q, float2(1e-5)));
    }
    float gradX = step(q.y, q.x);
    return sign(coord) * float2(gradX, 1.0 - gradX);
}"""

private val LiquidDropletShader = """
uniform shader content;

uniform float2 center;          // 珠子中心（表面像素坐标）
uniform float2 halfSize;        // 珠子半宽/半高
uniform float cornerRadius;     // = halfSize.y（药丸）
uniform float magnification;    // 1.14
uniform float rimHeight;        // 环带宽度（px）
uniform float bendDepth;        // 最边缘向内拉多少（px）
uniform float dispersion;       // 红/蓝相对偏移（0 关闭）

$RoundedBoxSdf

half4 main(float2 coord) {
    float2 halfBead = max(halfSize, float2(1.0));
    float2 centered = coord - center;
    float radius = clamp(cornerRadius, 0.0, min(halfBead.x, halfBead.y));

    float sd = sdRoundedBox(centered, halfBead, radius);
    // 0 在边缘，1 已进入环带内侧
    float band = clamp(-sd / max(rimHeight, 1.0), 0.0, 1.0);
    float bend = (1.0 - band) * (1.0 - band);

    float2 lensed = centered / max(magnification, 1.0);
    float2 dir = gradRoundedBox(centered, halfBead, radius);
    float2 base = lensed - dir * (bendDepth * bend) + center;

    half4 green = content.eval(base);
    if (dispersion <= 0.0) {
        return green;
    }
    float spread = bendDepth * bend * dispersion;
    half4 red = content.eval(base - dir * spread);
    half4 blue = content.eval(base + dir * spread);
    return half4(red.r, green.g, blue.b, green.a);
}"""

/** 液滴形状：宽药丸（圆角 = 高度/2）。与底栏胶囊同一语言，但宽度更窄。 */
val LiquidDropletShape: Shape = RoundedCornerShape(percent = 50)

/**
 * 画一颗「压在底栏表面」的液滴透镜。
 *
 * 所有长度都是**底栏表面的像素**（效果画在液滴节点上，节点矩形即液滴矩形）。
 *
 * @param magnification 中心放大倍数；1.0 关闭透镜
 * @param rimHeight 边缘环带宽度（px）
 * @param bendDepth 边缘把取样向内拉的距离（px）
 * @param dispersion 红/蓝取样的相对错开量；0 关闭彩边
 */
internal fun BackdropEffectScope.liquidDroplet(
    magnification: Float,
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    rimHeight: Float,
    bendDepth: Float,
    dispersion: Float,
) {
    if (!isRuntimeShaderSupported()) return
    if (magnification <= 1f) return
    runtimeShaderEffect(
        key = "LiquidDroplet",
        shaderString = LiquidDropletShader,
        uniformShaderName = "content",
    ) {
        setFloatUniform("center", centerX, centerY)
        setFloatUniform("halfSize", halfWidth, halfHeight)
        setFloatUniform("cornerRadius", halfHeight)
        setFloatUniform("magnification", magnification)
        setFloatUniform("rimHeight", rimHeight.coerceAtLeast(1f))
        setFloatUniform("bendDepth", bendDepth.coerceAtLeast(0f))
        setFloatUniform("dispersion", dispersion.coerceAtLeast(0f))
    }
}

private val DropletEdgeWidth = 1.dp
private val DropletGlowWidth = 2.dp

/**
 * 液滴的玻璃高光——**仅浅色档**（2026-10-30 两轮真机反馈后定）。
 *
 * 浅色底栏上白高光本来就看不清，所以这一档做两件事：把反光做足、再配深色描边（描边在调用方）；
 * 深色档则完全不用本修饰符（白高光在深底上已够明显，再叠会发白），换回底栏同款 `capsuleEdgeHighlight`。
 *
 * 四层，全部按浅色档取值：
 *  ① **顶部反光面**：从上缘往下渐隐的白雾——玻璃被顶光照亮的那一层「面」；
 *  ② **镜面高光点**：偏左上的柔和光斑——玻璃珠在灯下的反光，是「看得出是颗珠子」的关键；
 *  ③ **内晕**（2dp，上缘起渐隐）：沿内缘的一圈白，给厚度；
 *  ④ **外缘亮线**（1dp，上亮下暗）：比底栏那圈更亮（珠子小，不亮就立不住）。
 *
 * ⚠️ 调用方必须已在链上 `clip(药丸)`（本修饰符按整节点矩形铺渐变，靠裁剪收形）；
 * 也**不要**再叠 [capsuleEdgeHighlight]——两圈亮线会重影。
 */
fun Modifier.liquidDropletGloss(): Modifier = drawWithContent {
    drawContent()
    if (size.width <= 0f || size.height <= 0f) return@drawWithContent
    val radius = size.height / 2f

    // ① 顶部反光面（整块铺，裁剪由调用方的 clip 负责）
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = GlassLiquidSpec.sheenAlphaLight),
            0.5f to Color.White.copy(alpha = 0.02f),
            1f to Color.Transparent,
        ),
    )

    // ② 镜面高光点（偏左上）
    val specularAlpha = GlassLiquidSpec.specularAlphaLight
    val spot = Offset(
        size.width * GlassLiquidSpec.specularCenterX,
        size.height * GlassLiquidSpec.specularCenterY,
    )
    val spotRadius = size.height * GlassLiquidSpec.specularRadiusFactor
    if (specularAlpha > 0f && spotRadius > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = specularAlpha), Color.Transparent),
                center = spot,
                radius = spotRadius,
            ),
            radius = spotRadius,
            center = spot,
        )
    }

    // ③ 内晕：紧贴外缘内侧的一圈白，上缘最亮
    val glow = DropletGlowWidth.toPx()
    if (size.width > glow * 2f && size.height > glow * 2f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = GlassLiquidSpec.glowAlphaLight),
                1f to Color.Transparent,
            ),
            topLeft = Offset(glow / 2f, glow / 2f),
            size = Size(size.width - glow, size.height - glow),
            cornerRadius = CornerRadius((radius - glow / 2f).coerceAtLeast(0f)),
            style = Stroke(glow),
        )
    }

    // ④ 外缘亮线：压在晕上，上缘最亮、下缘收暗
    val edge = DropletEdgeWidth.toPx()
    if (size.width > edge * 2f && size.height > edge * 2f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = GlassLiquidSpec.rimTopLight),
                0.45f to Color.White.copy(alpha = GlassLiquidSpec.rimMidLight),
                1f to Color.White.copy(alpha = GlassLiquidSpec.rimBottomLight),
            ),
            topLeft = Offset(edge / 2f, edge / 2f),
            size = Size(size.width - edge, size.height - edge),
            cornerRadius = CornerRadius((radius - edge / 2f).coerceAtLeast(0f)),
            style = Stroke(edge),
        )
    }
}
