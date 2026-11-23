package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported

/*
 * 液态玻璃底栏的「液滴透镜」——照抄参考实现 memos-m
 * `docs/liquid-glass-bottom-bar.md` §6（LiquidLens.kt）。
 *
 * 液滴是一颗压在底栏玻璃上的透明药丸：
 *  - 内部按 1.14× 放大它覆盖的那部分底栏（图标、磨砂底都在里面）；
 *  - 只有外圈约 24% 高度的环带把取样向内拉（模拟液滴曲面），中段是纯放大；
 *  - 同一环带里红/蓝两路取样沿弯曲方向错开一点（色散），只在边缘出现细彩边。
 * 高光两档统一用底栏同款 `capsuleEdgeHighlight`（深色档就这一层）；浅色档再叠 1dp 深色描边
 * （见 JiabanBottomBar 的液滴节点）。
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

