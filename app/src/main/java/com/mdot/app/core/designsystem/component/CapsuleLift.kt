package com.mdot.app.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.GlassLiftSpec
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * 浮起胶囊的「外缘亮线 + 内晕 + 叠层环境阴影」——模糊态底栏胶囊（JiabanBottomBar）与
 * 右侧「记加班」圆钮（RecordCircleButton）共用。
 *
 * 配方来源：参考项目 memos-m 的配方卡 `docs/floating-capsule-highlight-card.md`（2026-10 移植，
 * 数值全部收进 [GlassLiftSpec]）。三件事各管一层表达：
 *   阴影 → 「浮起」；外缘亮线 → 「边界」；内晕 → 「厚度」。
 *
 * ⚠️ 三条硬约束（都踩过）：
 *  ① **阴影必须挂在 `.clip()` 之前**（画在裁切之外），且父布局不得 `clipToBounds`——挂到 clip
 *     之后阴影会被整个裁掉，看起来「什么都没加」；
 *  ② **亮线/内晕必须画在内容之后**（`drawContent()` 之后再描）——否则按压高亮/图标会把亮线盖住；
 *  ③ **不要改用 `Modifier.shadow`**：它是一种独立的投影实现，和缩放/淡入动画容易差一帧
 *     （弹出时阴影晚到），而这里的叠层与组件同一次绘制、天然同步。
 *
 * 形状假定是**胶囊/圆**：圆角半径取 `高度 / 2`（圆钮宽高相等 ⇒ 同一个公式画出来就是圆）。
 */

/**
 * 一层阴影：向外扩张 [growPx] 的黑色圆角矩形 + 不透明度 [alpha]。
 * [alpha] 已量化到 `1/255`（8-bit 混合的粒度）——小于半档的圈会被直接跳过、不画。
 */
internal data class ShadowBand(val growPx: Float, val alpha: Float)

/** 8-bit 混合下每层能表示的最小不透明度（1/255） */
internal const val SHADOW_ALPHA_STEP = 1f / 255f

/**
 * 纯函数：按「**越往外越淡**」生成由外到内的阴影层（`drawCapsuleShadow` 依次绘制，也可单测）。
 *
 * 目标曲线：紧贴形状处峰值 [peakAlpha]，距离 `d` 处累计 `peakAlpha × (1 − d/spread)^curve`
 * ——暗部集中在贴边一圈，越往外越薄；`curve` 越大越贴边。
 *
 * ⚠️ 每层 α 必须量化到 `1/255`（低于半档直接舍成 0）：由外向内逐圈解「这圈还要补多少」，
 * 补不足半档的圈**直接跳过**（画了也是 0）——所以实际画出的圈数常少于 `layers`，尾部自然收细。
 *
 * 这正是原先「每层 α 相同」做法的病根：各圈衰减量相等 ⇒ 肉眼看是**等宽的暗带**而不是渐变；
 * 而且 `0.05 / 8 层` 的每层 α = 1.63/255 被量化成 **2/255** ⇒ 实际总深 6.09%
 * （比名义 5% 更重，用户两次报「太明显」，见 docs/11 096 补记）。
 */
internal fun computeShadowBands(
    spreadPx: Float,
    layers: Int,
    peakAlpha: Float,
    curve: Float,
): List<ShadowBand> {
    if (spreadPx <= 0f || layers <= 0 || peakAlpha <= 0f) return emptyList()
    val step = spreadPx / layers
    val bands = ArrayList<ShadowBand>(layers)
    var covered = 0f // 已画好的（更外圈的）累计不透明度
    for (k in layers downTo 1) { // 由外向内
        val grow = step * k
        val target = peakAlpha * (1f - grow / spreadPx).pow(curve)
        // 解 1-(1-covered)(1-a)=target ⇒ 这圈需补的 α
        val need = 1f - (1f - target) / (1f - covered)
        val quantized = (need * 255f).roundToInt() * SHADOW_ALPHA_STEP
        if (quantized > 0f) {
            bands += ShadowBand(grow, quantized)
            covered = 1f - (1f - covered) * (1f - quantized)
        }
    }
    return bands
}

/**
 * 一档（日/夜）预建好的高光画刷与阴影层。
 *
 * 画刷在 [rememberCapsuleLiftStyle] 里按明暗档建一次（`verticalGradient` 的 `endY` 取
 * `POSITIVE_INFINITY`，绘制时自动铺满目标高度）——不要挪进 draw 里逐帧新建；
 * 阴影层（[computeShadowBands]）同理在这里一次算完（纯几何/α 计算，与尺寸无关）。
 */
@Immutable
class CapsuleLiftStyle internal constructor(
    /** 底色是否亮（判日/夜）；阴影档位与画刷都由它决定 */
    val isLight: Boolean,
    internal val edgeBrush: Brush,
    internal val glowBrush: Brush,
    /** 由外到内、已量化到 1/255 的阴影层；**空 = 不投影**（夜间即如此） */
    internal val shadowBands: List<ShadowBand>,
)

/**
 * 取当次绘制用的胶囊高光样式。
 *
 * 日/夜判定按配方卡口径取 **`colorScheme.surface` 的亮度 > 0.5**（而不是读「主题模式」）：
 * 判据是「胶囊浮在这块底上」，认底色比认开关更准（MIUIX 引擎浅色底 #F7F7F7、深色底纯黑，
 * 两套引擎都能分对）。
 */
@Composable
fun rememberCapsuleLiftStyle(): CapsuleLiftStyle {
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val density = LocalDensity.current
    val spreadPx = with(density) { GlassLiftSpec.shadowSpread.toPx() }
    return remember(isLight, spreadPx) {
        CapsuleLiftStyle(
            isLight = isLight,
            edgeBrush = whiteGradient(
                stops = if (isLight) GlassLiftSpec.edgeStopsLight else GlassLiftSpec.edgeStopsDark,
                alphas = if (isLight) GlassLiftSpec.edgeAlphasLight else GlassLiftSpec.edgeAlphasDark,
            ),
            glowBrush = whiteGradient(
                stops = if (isLight) GlassLiftSpec.glowStopsLight else GlassLiftSpec.glowStopsDark,
                alphas = if (isLight) GlassLiftSpec.glowAlphasLight else GlassLiftSpec.glowAlphasDark,
            ),
            shadowBands = computeShadowBands(
                spreadPx = spreadPx,
                layers = GlassLiftSpec.shadowLayers,
                peakAlpha = if (isLight) GlassLiftSpec.shadowAlphaLight else GlassLiftSpec.shadowAlphaDark,
                curve = GlassLiftSpec.shadowFalloffCurve,
            ),
        )
    }
}

/** 白 + 逐档不透明度的纵向渐变（`stops` 与 `alphas` 一一对应） */
private fun whiteGradient(stops: List<Float>, alphas: List<Float>): Brush = Brush.verticalGradient(
    *stops.zip(alphas) { stop, alpha -> stop to Color.White.copy(alpha = alpha) }.toTypedArray(),
)

/**
 * 叠层胶囊阴影（环境暗部）。
 *
 * **必须挂在 `.clip()` 之前**（画在裁切之外，见文件头 ⚠️①）；夜间 [CapsuleLiftStyle.shadowBands]
 * 为空 ⇒ 整段跳过，不产生任何绘制。
 *
 * 「越往外越淡」由 [computeShadowBands] 的缓出曲线保证（每圈 α 递减、尾部逐圈跳过）。
 *
 * @param extraWidth 阴影向本节点**右缘之外**额外铺开的宽度（只影响阴影本身，不影响布局）。
 *   用于「浮起的面只有一个」的情形：「按钮右置」布局下圆钮与胶囊并排、是同一块浮起的面，
 *   阴影由**胶囊一处**画到圆钮右缘（间距 + 圆钮直径），圆钮自己不再画——
 *   两边各画一遍会在 8dp 间距处叠成近双倍暗（`1-(1-α)²`），用户 2026-10 报「观感较重」，
 *   见 docs/11 098。
 */
fun Modifier.capsuleAmbientShadow(
    style: CapsuleLiftStyle,
    extraWidth: Dp = 0.dp,
): Modifier = drawWithContent {
    drawCapsuleShadow(
        bands = style.shadowBands,
        offsetY = GlassLiftSpec.shadowOffsetY.toPx(),
        extraWidth = extraWidth.toPx(),
    )
    drawContent()
}

/**
 * 外缘亮线 + 内晕。
 *
 * **必须挂在 `.clip()` 之后**（跟着胶囊形状裁），且由本修饰符保证「先内容、后描边」
 * （见文件头 ⚠️②）——调用方把它放在链尾即可。
 */
fun Modifier.capsuleEdgeHighlight(style: CapsuleLiftStyle): Modifier = drawWithContent {
    drawContent()
    drawCapsuleEdge(style.edgeBrush, style.glowBrush)
}

/**
 * 叠层胶囊阴影本体：按 [bands]（由外到内、已量化）逐圈画黑色圆角矩形。
 *
 * 这是**环境阴影**不是方向投影：只靠 `offsetY` 整体下移，柔度由 [ShadowBand.growPx] 决定。
 * 曲线（峰值 / 衰减 / 向外的尾巴）全在 [computeShadowBands] 里算好，这里只管画。
 */
private fun DrawScope.drawCapsuleShadow(
    bands: List<ShadowBand>,
    offsetY: Float,
    /** 向右额外铺开的宽度（见 [capsuleAmbientShadow]）：形状仍取「高度/2」为圆角 ⇒ 加宽后是更长的胶囊 */
    extraWidth: Float = 0f,
) {
    if (bands.isEmpty()) return
    val radius = size.height / 2f
    val width = size.width + extraWidth
    bands.forEach { band -> // 已按由外到内排好：先铺最外圈最淡的
        val grow = band.growPx
        drawRoundRect(
            color = Color.Black.copy(alpha = band.alpha),
            topLeft = Offset(-grow, -grow + offsetY),
            size = Size(width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(radius + grow),
        )
    }
}

/** 外缘亮线（1dp）+ 内晕（2dp）（配方卡 §4）：都是**内缩半线宽的描边**，半径随内缩量收小 */
private fun DrawScope.drawCapsuleEdge(edgeBrush: Brush, glowBrush: Brush) {
    val glow = GlassLiftSpec.glowWidth.toPx()
    val edge = GlassLiftSpec.edgeWidth.toPx()
    // 尺寸过小（布局首帧/极端宽度）时不描，否则内缩后出现负尺寸
    if (size.width <= glow * 2f || size.height <= glow * 2f) return
    // 内晕：紧贴外缘内侧的一圈白，往外渐隐
    drawRoundRect(
        brush = glowBrush,
        topLeft = Offset(glow / 2f, glow / 2f),
        size = Size(size.width - glow, size.height - glow),
        cornerRadius = CornerRadius((size.height - glow) / 2f),
        style = Stroke(glow),
    )
    // 外缘亮线：压在晕上，1dp 白（上缘最亮）
    drawRoundRect(
        brush = edgeBrush,
        topLeft = Offset(edge / 2f, edge / 2f),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius((size.height - edge) / 2f),
        style = Stroke(edge),
    )
}
