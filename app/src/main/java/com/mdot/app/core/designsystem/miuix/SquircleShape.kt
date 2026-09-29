package com.mdot.app.core.designsystem.miuix

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/**
 * ══ miuix 引擎——squircle（超椭圆感连续圆角）形状（v0.7.8）═══
 *
 * 路径数学：**四角同径直接调用 miuix-squircle 库**（`addSquircleRect`，top.yukonga.miuix.kmp:miuix-squircle）；
 * 四角不等（弹层只圆上角等场景）库无 API，此处按同算法泛化（控制柄比例、角块防翻折与库一致，有单测）。
 *
 * 归一化策略：逐边校验角块不重叠（两角块边长之和 ≤ 边长，超出按比例回缩）+ 角块上限
 * 短边一半——大半径小尺寸（如胶囊）不会画出翻折（有单测：SquircleShapeTest）。
 *
 * MD3 引擎不用本形状（标准 RoundedCornerShape），引擎分发见
 * `core/designsystem/EngineStyle.kt` 的 `engineShape`。
 */

/** 贝塞尔控制柄比例（与 miuix-squircle 的 SDF 预烘焙值保持一致，勿单改） */
private const val SQUIRCLE_CONTROL = 0.643f

/** 角块边长 = 半径 × 1.1（miuix `SquircleDefaults.Extension`：1.0=圆弧，1.1=连续角） */
private const val SQUIRCLE_EXTENSION = 1.1f

@Immutable
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    /** Dp 便捷构造：**单值 = 四角同径**（与 `RoundedCornerShape(Dp)` 同款语义） */
    constructor(corner: Dp) : this(CornerSize(corner), CornerSize(corner), CornerSize(corner), CornerSize(corner))

    /** 四角可不等（与 `RoundedCornerShape(Dp...)` 同款）；刻意**不给默认值**——漏传角直接编译错 */
    constructor(
        topStart: Dp,
        topEnd: Dp,
        bottomEnd: Dp,
        bottomStart: Dp,
    ) : this(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart))

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        // RTL 镜像与 RoundedCornerShape 同款：起止角互换
        val rtl = layoutDirection == LayoutDirection.Rtl
        val ts = if (rtl) topEnd else topStart
        val te = if (rtl) topStart else topEnd
        val be = bottomEnd
        val bs = if (rtl) bottomEnd else bottomStart

        val path = Path().apply {
            if (ts == te && te == be && be == bs) {
                // 四角同径：直接走 miuix-squircle **库本体**（top.yukonga.miuix.kmp:squircle）
                addSquircleRect(size.width, size.height, ts)
            } else {
                // 四角不等（弹层半开角等）：库只提供等角 API，此处为其算法的泛化延伸（单测覆盖防翻折）
                addSquircleRoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftRadius = ts,
                    topRightRadius = te,
                    bottomRightRadius = be,
                    bottomLeftRadius = bs,
                )
            }
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun toString(): String =
        "SquircleShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = $bottomEnd, bottomStart = $bottomStart)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        // CornerSize 的实现类未必有值相等：按参考尺寸渲染出的像素值比较（Dp/百分比角均正确）
        val probe = Size(100f, 100f)
        val density = Density(1f)
        return topStart.toPx(probe, density) == other.topStart.toPx(probe, density) &&
            topEnd.toPx(probe, density) == other.topEnd.toPx(probe, density) &&
            bottomEnd.toPx(probe, density) == other.bottomEnd.toPx(probe, density) &&
            bottomStart.toPx(probe, density) == other.bottomStart.toPx(probe, density)
    }

    override fun hashCode(): Int {
        val probe = Size(100f, 100f)
        val density = Density(1f)
        var result = topStart.toPx(probe, density).hashCode()
        result = 31 * result + topEnd.toPx(probe, density).hashCode()
        result = 31 * result + bottomEnd.toPx(probe, density).hashCode()
        result = 31 * result + bottomStart.toPx(probe, density).hashCode()
        return result
    }
}

/**
 * 追加 squircle 圆角矩形路径（四角可不等，半径为像素）。角块 = 半径 × [SQUIRCLE_EXTENSION]，
 * 每条边保证两角块不重叠（超出按比例回缩）。
 */
internal fun Path.addSquircleRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    topLeftRadius: Float,
    topRightRadius: Float,
    bottomRightRadius: Float,
    bottomLeftRadius: Float,
) {
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return

    // 角块边长：半径放大 Extension 倍，上限为短边一半（与 miuix addSquircleRect 同款）
    val halfMin = minOf(w, h) * 0.5f
    var tl = (topLeftRadius * SQUIRCLE_EXTENSION).coerceIn(0f, halfMin)
    var tr = (topRightRadius * SQUIRCLE_EXTENSION).coerceIn(0f, halfMin)
    var br = (bottomRightRadius * SQUIRCLE_EXTENSION).coerceIn(0f, halfMin)
    var bl = (bottomLeftRadius * SQUIRCLE_EXTENSION).coerceIn(0f, halfMin)

    // 逐边校验角块不重叠：两角块按比例回缩到边长以内（防大半径小尺寸翻折）
    val topPair = clampPair(tl, tr, w); tl = topPair.first; tr = topPair.second
    val rightPair = clampPair(tr, br, h); tr = rightPair.first; br = rightPair.second
    val bottomPair = clampPair(br, bl, w); br = bottomPair.first; bl = bottomPair.second
    val leftPair = clampPair(bl, tl, h); bl = leftPair.first; tl = leftPair.second

    fun handle(tile: Float) = tile * (1f - SQUIRCLE_CONTROL)

    moveTo(left + tl, top)
    lineTo(right - tr, top)
    // 右上角
    run {
        val hd = handle(tr)
        cubicTo(right - hd, top, right, top + hd, right, top + tr)
    }
    lineTo(right, bottom - br)
    // 右下角
    run {
        val hd = handle(br)
        cubicTo(right, bottom - hd, right - hd, bottom, right - br, bottom)
    }
    lineTo(left + bl, bottom)
    // 左下角
    run {
        val hd = handle(bl)
        cubicTo(left + hd, bottom, left, bottom - hd, left, bottom - bl)
    }
    lineTo(left, top + tl)
    // 左上角
    run {
        val hd = handle(tl)
        cubicTo(left, top + hd, left + hd, top, left + tl, top)
    }
    close()
}

/** 两角块边长按比例回缩到 [edge] 以内（都为 0 或不超额时原样返回） */
private fun clampPair(a: Float, b: Float, edge: Float): Pair<Float, Float> {
    val sum = a + b
    if (sum <= edge || sum <= 0f) return a to b
    val scale = edge / sum
    return (a * scale) to (b * scale)
}
