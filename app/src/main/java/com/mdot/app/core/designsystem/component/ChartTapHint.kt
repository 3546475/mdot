package com.mdot.app.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 图表点击提示（docs/15 T6 #19）：点热力图/柱状图时，在点击列上方显示
 * 柔和气泡（日期 + 时长/工数），自动消失；长按进入该日记工弹窗由调用方接线。
 */
data class ChartHint(val column: Int, val date: LocalDate, val text: String)

/** 图表点击事件（手势回调只写状态；文本在组合上下文用 [hintValueText] 计算） */
data class ChartEvent(val column: Int, val date: LocalDate, val value: Float)

/** 图表点击提示状态：写入即显示，[autoHideMs] 后自动清空；连续点击重置计时。 */
@Composable
fun rememberChartHint(autoHideMs: Long = 2200): MutableState<ChartHint?> {
    val hint = remember { mutableStateOf<ChartHint?>(null) }
    LaunchedEffect(hint.value) {
        val h = hint.value ?: return@LaunchedEffect
        delay(autoHideMs)
        hint.value = null
    }
    return hint
}

/**
 * 图表点击提示宿主：包住图表内容，气泡水平居中于 [hint.column]（0-based）所在列；
 * 气泡宽 = max(2 列宽, 文本宽+padding)（窄列不换行），并 clamp 在宿主宽度内（首/末列不出屏）；
 * 浅色柔和气泡（surfaceContainerHigh + 主色细描边），淡入（上浮+轻微放大）淡出（下沉）。
 * 列宽按 [columns] 均分，与各图表 weight 列布局一致。
 */
@Composable
fun ChartHintBox(
    hint: ChartHint?,
    columns: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        content()
        if (columns > 0 && maxWidth > 0.dp) {
            val motion = MaterialTheme.motionScheme
            val fx = motion.defaultEffectsSpec<Float>()
            val offsetSpec = motion.defaultEffectsSpec<IntOffset>()
            val density = LocalDensity.current
            val colWidthPx = with(density) { (maxWidth / columns).toPx() }
            val textStyle = MaterialTheme.typography.labelMedium
            val measurer = rememberTextMeasurer()
            val hPaddingPx = with(density) { 24.dp.toPx() }
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(fx) + scaleIn(fx, initialScale = 0.94f) +
                    slideInVertically(offsetSpec) { it / 3 },
                exit = fadeOut(fx) + scaleOut(fx, targetScale = 0.96f) +
                    slideOutVertically(offsetSpec) { it / 6 },
            ) {
                hint?.let { h ->
                    val textWidth = measurer.measure(AnnotatedString(h.text), textStyle).size.width
                    val bubbleWidth = max(colWidthPx * 2f, textWidth + hPaddingPx)
                    val hostWidth = colWidthPx * columns
                    val left = (colWidthPx * (h.column + 0.5f) - bubbleWidth / 2f)
                        .coerceIn(0f, (hostWidth - bubbleWidth).coerceAtLeast(0f))
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(left.roundToInt(), 0) }
                            .width(with(density) { bubbleWidth.toDp() })
                            .wrapContentSize(Alignment.Center)
                            .shadow(2.dp, RoundedCornerShape(8.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            h.text,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
