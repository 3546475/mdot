package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.Radius

/**
 * 图表点击提示（docs/15 T6 #19）：点热力图/柱状图时，在点击列上方显示
 * 柔和气泡（日期 + 时长/工数），自动消失；长按进入该日记工弹窗由调用方接线。
 */
data class ChartHint(val column: Int, val date: LocalDate, val text: String)

/** 图表点击事件（手势回调只写状态；文本在组合上下文用 [chartHintText] 计算） */
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
 * 图表点击浮窗文本：日期 · 时长/工数。
 * **统计页与首页共用同一实现**（两页图表效果必须一致，见 03 文档；改一处两页同时生效）。
 * [dateLabel] 覆盖日期段文本（周聚合柱传「M/d – M/d」周区间，默认单日 mdCn）。
 */
@Composable
fun chartHintText(
    workSystem: WorkSystem,
    dateLabel: (LocalDate) -> String = { TimeUtils.mdCn(it) },
): @Composable (LocalDate, Float) -> String? = { date, v ->
    "${dateLabel(date)} · ${modeValueText(workSystem, v)}"
}

/**
 * 图表点击提示宿主：包住图表内容，气泡水平居中于 [hint.column]（0-based）所在列；
 * 气泡宽由文本自然撑开（不换行、至少两列宽），并 clamp 在宿主宽度内（首/末列不出屏）；
 * 浅色柔和气泡（surfaceContainerHigh + 主色细描边），淡入淡出。
 *
 * 实现要点（v0.6.19 修「首次出现只显左边、右边随后才出现」）：
 * - **宽度不再用 `rememberTextMeasurer` 预测量后写死**：预测量在首帧可能偏小（字体解析/缓存未就绪），
 *   写死的宽度会把文字右侧裁掉，等真实宽度回来才补上——观感就是「先左半、后右半」。
 *   改由 [ChartHintBubbleRow] 在**布局阶段**用无界约束量出气泡真实宽度再按列居中放置，
 *   文本永不换行、永不裁切，宽度也不会二次跳变。
 * - **只保留淡入淡出**（去掉 scaleIn/slideIn）：缩放/位移的变换原点是容器而非气泡，
 *   气泡被定位到列位置后会被变换出「从别处滑入/半截浮现」的错觉，淡入不存在这种相位问题。
 * 列宽按 [columns] 均分，与各图表 weight 列布局一致。
 *
 * ⚠️ **不加投影**（2026-10-09 用户定：直接移除）：曾用 `Modifier.shadow(2.dp)`，真机表现为
 * 「阴影比气泡晚一拍出现」；先试过「节点常驻 + 只播 alpha」预热（以为是首次建层），**仍然迟滞**；
 * 再试过在同一个 draw pass 里自绘多层轮廓，**依旧迟滞** ⇒ 判定为该 ROM 的合成/绘制行为，
 * 非本组件可隔离，用户定**去掉投影**。层次改由**主色细描边 + 抬一阶的底**承担。
 * ⇒ 别再加回 shadow（会重现迟滞）；要加投影请先真机确认不再迟滞。
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
            val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(fx),
                exit = fadeOut(fx),
            ) {
                hint?.let { h ->
                    ChartHintBubbleRow(column = h.column, columns = columns) {
                        ChartHintBubble(text = h.text)
                    }
                }
            }
        }
    }
}

/**
 * 气泡定位：宿主宽 = 全宽（气泡不溢出、也不参与任何变换原点）；气泡按列居中并 clamp 在宿主内。
 * 用无界约束量气泡 → 宽度就是文本真实宽度（换行/裁切都不会发生）。
 */
@Composable
private fun ChartHintBubbleRow(
    column: Int,
    columns: Int,
    bubble: @Composable () -> Unit,
) {
    Layout(content = bubble) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
        )
        val hostW = constraints.maxWidth
        val colW = hostW.toFloat() / columns
        // 窄列下气泡不能显小：至少两列宽（与旧实现一致）
        val bubbleW = placeable.width.coerceAtLeast((colW * 2f).roundToInt())
        val left = (colW * (column + 0.5f) - bubbleW / 2f)
            .roundToInt()
            .coerceIn(0, (hostW - bubbleW).coerceAtLeast(0))
        layout(hostW, placeable.height) {
            placeable.place(left + (bubbleW - placeable.width) / 2, 0)
        }
    }
}

/** 气泡本体：文本单行自然宽度 + 柔和底 + 主色细描边（**无投影**，见 [ChartHintBox] KDoc 的沿革） */
@Composable
private fun ChartHintBubble(text: String) {
    val shape = engineShape(Radius.xs)
    Box(
        Modifier
            .wrapContentSize(Alignment.Center)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                shape,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                shape,
            )
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}
