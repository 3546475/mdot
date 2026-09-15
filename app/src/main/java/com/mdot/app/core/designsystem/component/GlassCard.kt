package com.mdot.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.RainbowColors
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import kotlinx.coroutines.delay

/** 环境动画统一周期（星光带跑动 / 彩虹跑马灯流动）：统一值避免装饰之间速度分叉 */
internal const val GLASS_AMBIENT_PERIOD_MS = 14000

/** 等页面转场落定后再起播入场光扫（避免与转场叠加、肉眼捕捉不到） */
private const val GLASS_SWEEP_DELAY_MS = 420L

/**
 * 玻璃质感卡片容器（主题级视觉语言，供「致谢名单卡」「检查更新卡」等共用）。
 *
 * 视觉分层（全部令牌化，禁硬编码色值）：
 * ① 底：primaryContainer → tertiaryContainer → secondaryContainer 三段斜向渐变，色标持续缓慢流动
 * ② 光：卡内左上暖光 + 右下冷光两处径向柔光（玻璃质感来源）
 * ③ 面：半透明 surface 磨砂覆盖
 * ④ 入：卡片淡入上浮；落位后一道斜向高光自左掠过（一次性强调）
 * ⑤ 边：沿卡片**上边**流动的低饱和彩虹描边（含两端圆角弧，两端 12% 淡出）
 *
 * 用法：`GlassCard { /* 内容；用 MaterialTheme.colorScheme.onPrimaryContainer 作为前景色 */ }`
 *
 * ⚠️ 硬规则 7 说明：motionScheme 的 fast/default/slow 是为**交互反馈**设计的有限时长
 * spec，入场强调动画与持续循环装饰动画无对应档位，故此处用明确时长 tween 作为
 * 可感知性的最低要求（slowSpatialSpec 实测约 300ms，肉眼捕捉不到）。见 docs/11 020。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.motionScheme
    val cs = MaterialTheme.colorScheme

    // ---- 入场：淡入上浮 ----
    val enterSpec = scheme.defaultSpatialSpec<Float>()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = enterSpec,
        label = "glassEnter",
    )

    // ---- 入场光扫：独立进度 + 起播延迟（见类注释的硬规则 7 说明）----
    var sweepStarted by remember { mutableStateOf(false) }
    val sweep by animateFloatAsState(
        targetValue = if (sweepStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 1150, easing = LinearEasing),
        label = "glassSweep",
    )
    LaunchedEffect(Unit) {
        delay(GLASS_SWEEP_DELAY_MS)
        sweepStarted = true
    }

    // ---- 持续流动：底渐变色标推移 + 彩虹跑马灯相位 ----
    val flow = rememberInfiniteTransition(label = "glassFlow")
    val gradientShift by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glassGradientShift",
    )
    val rainbowPhase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = GLASS_AMBIENT_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glassRainbowPhase",
    )

    // 色标推移（比整体平移渐变端点明显得多：让各段颜色在卡面上真实游走）
    val drift = gradientShift * 0.55f
    val sheetGradient = Brush.linearGradient(
        colorStops = arrayOf(
            0f to cs.primaryContainer,
            (0.30f + drift * 0.55f).coerceAtMost(0.85f) to cs.tertiaryContainer,
            (0.62f + drift * 0.38f).coerceAtMost(0.95f) to cs.secondaryContainer,
            1f to cs.primaryContainer,
        ),
        start = Offset(0f, 0f),
        end = Offset(1100f, 820f),
    )
    val warmGlow = cs.tertiary.copy(alpha = 0.28f)
    val coolGlow = cs.primary.copy(alpha = 0.24f)
    val frosted = cs.surface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 16.dp.toPx()
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(sheetGradient)
                // 双光源柔光：左上暖 + 右下冷
                .drawBehind {
                    val r = size.minDimension * 0.9f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(warmGlow, Color.Transparent),
                            center = Offset(size.width * 0.12f, size.height * 0.08f),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                    )
                    val r2 = size.minDimension * 1.05f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(coolGlow, Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.95f),
                            radius = r2,
                        ),
                        radius = r2,
                        center = Offset(size.width * 0.92f, size.height * 0.95f),
                    )
                }
                .background(frosted)
                // 入场光扫：斜向白色高光带自左掠到右
                .drawWithContent {
                    drawContent()
                    if (sweep in 0.02f..0.99f) {
                        val w = size.width
                        val bandW = w * 0.38f
                        val cx = -bandW + (w + bandW * 2) * sweep
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.42f),
                                    Color.Transparent,
                                ),
                                start = Offset(cx - bandW, 0f),
                                end = Offset(cx + bandW, size.height),
                            ),
                        )
                    }
                }
                // 上边彩虹跑马灯
                .drawBehind {
                    drawRainbowMarqueeTop(
                        phase = rainbowPhase,
                        strokeWidth = 2.dp.toPx(),
                        cornerRadius = Radius.card.toPx(),
                    )
                }
                .padding(Spacing.l),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * 彩虹跑马灯（仅**上边**）：沿卡片上边铺低饱和彩虹描边，颜色自左向右流动。
 *
 * 路径含「左上圆弧 → 顶部直边 → 右上圆弧」，贴合圆角形状（而非横切两角）；
 * 颜色按「沿路径累计比例 − 相位」取样 → 色带向右移动；
 * 两端 12% 淡出，收口自然不生硬。周期见 [GLASS_AMBIENT_PERIOD_MS]。
 */
private fun DrawScope.drawRainbowMarqueeTop(
    phase: Float,
    strokeWidth: Float,
    cornerRadius: Float,
) {
    val inset = strokeWidth / 2f
    val l = inset
    val t = inset
    val r = size.width - inset
    val radius = cornerRadius.coerceAtMost((r - l) / 2f).coerceAtMost(size.height - inset)
    if (r <= l || size.height <= inset) return

    val arc = (Math.PI.toFloat() / 2f) * radius
    val straight = ((r - l) - 2 * radius).coerceAtLeast(0f)
    val pathLen = arc + straight + arc
    if (pathLen <= 0f) return

    /** 沿上边路径取点：dist ∈ [0, pathLen)，起点为左上角弧起始（左边靠上） */
    fun pointAt(dist: Float): Offset {
        val d = dist.coerceIn(0f, pathLen)
        if (d <= arc) {
            val a = d / arc * (Math.PI.toFloat() / 2f)
            return Offset(
                l + radius - radius * kotlin.math.cos(a),
                t + radius - radius * kotlin.math.sin(a),
            )
        }
        if (d <= arc + straight) {
            return Offset(l + radius + (d - arc), t)
        }
        val a = (d - arc - straight) / arc * (Math.PI.toFloat() / 2f)
        return Offset(
            r - radius + radius * kotlin.math.sin(a),
            t + radius - radius * kotlin.math.cos(a),
        )
    }

    val step = 4f
    val steps = (pathLen / step).toInt().coerceAtLeast(24)
    for (i in 0 until steps) {
        val d0 = pathLen * i / steps
        val d1 = pathLen * (i + 1) / steps
        val frac = i.toFloat() / steps
        val color = RainbowColors.sample(frac - phase)
        val fade = when {
            frac < 0.12f -> frac / 0.12f
            frac > 0.88f -> (1f - frac) / 0.12f
            else -> 1f
        }
        drawLine(
            color = color.copy(alpha = 0.95f * fade.coerceIn(0f, 1f)),
            start = pointAt(d0),
            end = pointAt(d1),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
