package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.ChartSpec

/**
 * GitHub 贡献图风格热点图（参照用户提供的竞品截图）：
 * **周为列 × 星期为行**的密集小方格（默认 26 列 ≈ 半年），整卡横向铺满；
 * 顶部月份标注（列含某月 1 号时标该月，如「九月」）；空格浅灰填充+描边，有记录按强度 primary alpha 分级。
 * 无星期标、无图例。
 *
 * 图表可点化（docs/15 T6 #19）：点击有记录的格子 → 列顶部显示半透明气泡（日期 + 时长/工数，自动消失）；
 * 长按格子 → 触觉反馈后回调 [onDayLongPress]（统计页接记工弹窗）。
 */
@Composable
fun WorkHeatmap(
    values: Map<LocalDate, Float>,
    end: LocalDate,
    modifier: Modifier = Modifier,
    weeks: Int = 26,
    /** 显式起点（如「本月向前六个月」的月初）：给了则铺 start..end，忽略 weeks */
    start: LocalDate? = null,
    cellAspect: Float = 1f,
    gap: Dp = 2.dp,
    /** 长按有记录格 → 记工弹窗等（可选；null 不启用长按） */
    onDayLongPress: ((LocalDate) -> Unit)? = null,
    /** 浮窗文本（日期+时长/工数）；返回 null 则不显示浮窗 */
    hintValueText: @Composable (LocalDate, Float) -> String? = { _, _ -> null },
) {
    val total = if (start != null) (end.toEpochDay() - start.toEpochDay()).toInt() + 1 else weeks * 7
    val realStart = start ?: end.minusDays((total - 1).toLong())
    // 起始列对齐周一
    val leading = (realStart.dayOfWeek.value + 6) % 7
    val cols = (leading + total + 6) / 7
    val grid: List<LocalDate?> = List(leading) { null } +
        (0 until total).map { realStart.plusDays(it.toLong()) } +
        List((cols * 7 - leading - total) % 7) { null }
    val maxV = values.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.CHINA)
    val hint = rememberChartHint()
    var tapEvent by remember { mutableStateOf<ChartEvent?>(null) }
    // 选中格（点击切换；仅内部状态，空格也可选中）
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val haptic = LocalHapticFeedback.current
    // 手势只写事件；文本在组合上下文计算（手势回调非 @Composable）
    tapEvent?.let { ev ->
        hintValueText(ev.date, ev.value)?.let { hint.value = ChartHint(ev.column, ev.date, it) }
        tapEvent = null
    }

    // 月份标注：某月 1 号落在哪列就标哪月（跨年/月中起始都能标全，如四月~九月六个月）
    var lastMonth = -1
    val monthLabels = (0 until cols).map { c ->
        val firstOfMonth = (0..6).mapNotNull { r -> grid.getOrNull(c * 7 + r) }.firstOrNull { it.dayOfMonth == 1 }
        when {
            firstOfMonth != null && firstOfMonth.monthValue != lastMonth -> {
                lastMonth = firstOfMonth.monthValue
                monthFormatter.format(firstOfMonth)
            }
            else -> ""
        }
    }

    ChartHintBox(hint = hint.value, columns = cols, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // 月份行：与格子列同宽；文字超出列宽时可见溢出（参考图月份比格子宽）
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
                monthLabels.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
            // 方格网格：**周为列**（每列竖排 7 格 = 星期一~日），列横向并排 → GitHub 贡献图布局
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                (0 until cols).forEach { c ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        (0 until 7).forEach { r ->
                            val date = grid.getOrNull(c * 7 + r)
                            val level = date?.let { heatLevel(values[it] ?: 0f, maxV) } ?: -1
                            val shape = RoundedCornerShape(ChartSpec.cellRadius)
                            val isSelected = date != null && date == selectedDate
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(cellAspect)
                                    .clip(shape)
                                    .then(
                                        if (level == -1) Modifier.border(
                                            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape,
                                        ) else Modifier
                                    )
                                    .background(
                                        when {
                                            isSelected && level >= 0 -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else -> heatColor(level)
                                        }
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(
                                            // onPrimary 描边：与数据深浅色块都区分（高数据满饱和也清晰）
                                            2.dp, MaterialTheme.colorScheme.onPrimary, shape,
                                        ) else Modifier
                                    )
                                    .then(
                                        if (date != null) Modifier.pointerInput(date) {
                                            detectTapGestures(
                                                onTap = {
                                                    tapEvent = ChartEvent(c, date, values[date] ?: 0f)
                                                    selectedDate = if (selectedDate == date) null else date
                                                },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDayLongPress?.invoke(date)
                                                },
                                            )
                                        } else Modifier
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 0=有数据最低档，4=最高；-1=空（浅灰描边） */
private fun heatLevel(v: Float, max: Float): Int = when {
    v <= 0f -> -1
    v >= max -> 4
    else -> (v / max * 4).toInt().coerceIn(1, 3)
}

@Composable
private fun heatColor(level: Int): Color = when (level) {
    -1 -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.primary
}
