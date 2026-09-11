package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * GitHub 贡献图风格热点图（参照用户提供的竞品截图）：
 * **周为列 × 星期为行**的密集小方格（默认 26 列 ≈ 半年），整卡横向铺满；
 * 顶部月份标注（列含某月 1 号时标该月，如「九月」）；空格浅灰填充+描边，有记录按强度 primary alpha 分级。
 * 无星期标、无图例。
 */
@Composable
fun WorkHeatmap(
    values: Map<LocalDate, Float>,
    end: LocalDate,
    modifier: Modifier = Modifier,
    weeks: Int = 26,
    cellAspect: Float = 1f,
    gap: Dp = 2.dp,
) {
    val total = weeks * 7
    val start = end.minusDays((total - 1).toLong())
    // 起始列对齐周一
    val leading = (start.dayOfWeek.value + 6) % 7
    val cols = (leading + total + 6) / 7
    val grid: List<LocalDate?> = List(leading) { null } +
        (0 until total).map { start.plusDays(it.toLong()) } +
        List((cols * 7 - leading - total) % 7) { null }
    val maxV = values.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.CHINA)

    // 月份标注：每列首个日期为某月 1 号（且月份与上一标注不同）时标该月
    var lastMonth = -1
    val monthLabels = (0 until cols).map { c ->
        val first = (0..6).mapNotNull { r -> grid.getOrNull(c * 7 + r) }.firstOrNull()
        when {
            first != null && first.dayOfMonth <= 7 && first.monthValue != lastMonth -> {
                lastMonth = first.monthValue
                monthFormatter.format(first)
            }
            else -> ""
        }
    }

    Column(modifier.fillMaxWidth()) {
        // 月份行：与格子列同宽；文字超出列宽时可见溢出（参考图月份比格子宽）
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
                        val shape = RoundedCornerShape(3.dp)
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
                                .background(heatColor(level)),
                        )
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
