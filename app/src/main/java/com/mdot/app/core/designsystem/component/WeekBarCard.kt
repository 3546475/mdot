package com.mdot.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 本周柱状卡（统计页/首页共用，v0.6）：固定本周周一~周日 7 柱。
 * 卡内最上方周几单字表头（一、二……日）；柱上时长（0 不显示）、柱下日期常显；
 * 空日/未来日仅占位；点击柱高亮 + 下方「M/D · 时长」。
 * value 语义由调用方决定（加班分钟 | 工数），数值文本经 [valueText] 单位化。
 */
data class WeekBar(val date: LocalDate, val value: Float, val future: Boolean = false)

/** 本周一~周日 7 柱（周一起；未来日仅占位 value=0） */
fun buildWeekBars(values: Map<LocalDate, Float>): List<WeekBar> {
    val today = LocalDate.now()
    val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
    return (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        if (d.isAfter(today)) WeekBar(d, 0f, future = true)
        else WeekBar(d, values[d] ?: 0f)
    }
}

@Composable
fun WeekBarCard(
    bars: List<WeekBar>,
    /** 数值文本（按调用方模式单位化：prettyDuration | 「N 工」） */
    valueText: @Composable (Float) -> String,
    modifier: Modifier = Modifier,
    selectedLabel: String? = null,
    onSelect: (String?) -> Unit = {},
) {
    SectionCard(modifier) {
        Column {
            // 表头：周几（只显示一、二……）
            Row(Modifier.fillMaxWidth()) {
                bars.forEach { bar ->
                    Text(
                        TimeUtils.weekdayCnShort(bar.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            // Canvas 高度 = 热点图网格高度（WorkHeatmap 同公式：26 周方格 aspectRatio(1) 随卡宽推导），
            // 表头行与热图月份行同结构（labelMedium + 4dp），两卡总高严格一致且随宽度响应
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val today = LocalDate.now()
                val total = 26 * 7
                val start = today.minusDays((total - 1).toLong())
                val leading = (start.dayOfWeek.value + 6) % 7
                val cols = (leading + total + 6) / 7
                val gap = 2.dp
                val cell = (maxWidth - gap * (cols - 1)) / cols
                val gridHeight = cell * 7 + gap * 6
                WeekBarChart(
                    bars = bars,
                    selectedLabel = selectedLabel,
                    onSelect = onSelect,
                    valueTexts = bars.map { if (it.value > 0f) valueText(it.value) else null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                )
            }
            bars.firstOrNull { TimeUtils.md(it.date) == selectedLabel }?.let { bar ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.stats_bar_selected, TimeUtils.md(bar.date), valueText(bar.value)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 本周柱状图（Canvas）：柱上时长（0 不显示）、柱下日期；空日/未来日仅占位。
 * 数据变化时柱子生长动画（motionScheme）。
 */
@Composable
private fun WeekBarChart(
    bars: List<WeekBar>,
    selectedLabel: String?,
    onSelect: (String?) -> Unit,
    valueTexts: List<String?>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val slowSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, slowSpec)
    }
    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = onSurfaceVariant.toArgb(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = primary.toArgb(); textSize = 22f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    Canvas(
        modifier
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val idx = (offset.x / size.width * bars.size).toInt().coerceIn(0, bars.size - 1)
                    val bar = bars[idx]
                    if (!bar.future) {
                        val label = TimeUtils.md(bar.date)
                        onSelect(if (label == selectedLabel) null else label)
                    }
                }
            }
    ) {
        if (bars.isEmpty()) return@Canvas
        val barTopColor = primary
        val barBottomColor = primary.copy(alpha = 0.45f)
        val topPad = 34f                               // 柱顶数值留白
        val bottomPad = 34f                            // 日期区
        val chartTop = topPad
        val chartBottom = size.height - bottomPad
        val chartH = chartBottom - chartTop
        val max = (bars.maxOf { it.value }).coerceAtLeast(1f)
        val slot = size.width / bars.size
        val barWidth = (slot * 0.55f).coerceAtMost(28.dp.toPx())
        // 基线
        drawLine(outline, Offset(0f, chartBottom + 6f), Offset(size.width, chartBottom + 6f), 2f)
        bars.forEachIndexed { i, bar ->
            val cx = slot * (i + 0.5f)
            val v = bar.value
            if (v > 0f && !bar.future) {
                val h = chartH * v / max * progress.value
                // 选中柱满饱和、其余渐变淡出（与月柱状图同款渐变）
                val topColor = if (TimeUtils.md(bar.date) == selectedLabel) primary else primary.copy(alpha = 0.75f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topColor, barBottomColor),
                        startY = chartBottom - h,
                        endY = chartBottom,
                    ),
                    topLeft = Offset(cx - barWidth / 2, chartBottom - h),
                    size = Size(barWidth, h.coerceAtLeast(4f * progress.value)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                )
                // 柱上方：时长（0 不显示）
                valueTexts.getOrNull(i)?.let { text ->
                    drawContext.canvas.nativeCanvas.drawText(text, cx, chartBottom - h - 8f, valuePaint)
                }
            }
            // 柱下方：日期（全部显示，含空日/未来日占位）
            drawContext.canvas.nativeCanvas.drawText(TimeUtils.md(bar.date), cx, size.height - 8f, labelPaint)
        }
    }
}
