package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.domain.model.WorkSystem
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 月柱状图（参考竞品版式；统计页与首页「月柱状」卡片共用）：
 * 区间每日柱（底部对齐渐变，0=空柱）+ 右侧最大/半程/0 虚线刻度 + 最多日文字。
 * values[i] = from + i 天的强度值；强度随制度（工地=工数，其他=加班分钟）。
 */
@Composable
fun MonthBarCard(
    values: List<Float>,
    from: LocalDate,
    workSystem: WorkSystem,
    modifier: Modifier = Modifier,
) {
    val days = values.size
    if (days < 2) return
    val maxV = values.maxOrNull() ?: 0f
    val bestIdx = values.indices.maxByOrNull { values[it] } ?: 0

    // 自绘容器：底距比统一卡片更紧（最多日文字贴近下缘）
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(113.dp)
                    ) {
                        // 虚线网格（最大/半程/0）
                        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        val barTopColor = MaterialTheme.colorScheme.primary
                        val barBottomColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        Canvas(Modifier.fillMaxSize()) {
                            listOf(1f, 0.5f, 0f).forEach { f ->
                                val y = size.height * (1f - f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                                )
                            }
                        }
                        // 每日柱（底部对齐，渐变）
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxSize(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            values.forEachIndexed { _, v ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    if (v > 0f) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight(
                                                    (v / maxV.coerceAtLeast(1f)).coerceIn(0.03f, 1f)
                                                )
                                                .width(5.dp)
                                                .background(
                                                    Brush.verticalGradient(listOf(barTopColor, barBottomColor)),
                                                    RoundedCornerShape(3.dp),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // X 轴固定标签（1 5 10 15 20 25 30，按当月日期定位，Canvas 直绘防换行）
                    val labelStyle = MaterialTheme.typography.labelSmall
                    val measurer = rememberTextMeasurer()
                    val labelDates = (0 until days)
                        .map { from.plusDays(it.toLong()) }
                        .filter { it.dayOfMonth == 1 || it.dayOfMonth % 5 == 0 }
                    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                        labelDates.forEach { date ->
                            val idx = (date.toEpochDay() - from.toEpochDay()).toInt()
                            val measured = measurer.measure(
                                AnnotatedString(date.dayOfMonth.toString()),
                                labelStyle,
                            )
                            drawText(
                                measured,
                                topLeft = Offset(
                                    size.width * ((idx + 0.5f) / days) - measured.size.width / 2f,
                                    0f,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.s))
                // 右侧刻度（与网格线对齐）
                Box(Modifier.height(113.dp)) {
                    Text(
                        modeValueText(workSystem, maxV),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    Text(
                        modeValueText(workSystem, maxV / 2f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                    Text(
                        "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
            // 最多日（纯文字）
            if (maxV > 0f) {
                Spacer(Modifier.height(Spacing.m))
                Text(
                    stringResource(
                        R.string.stats_month_best,
                        from.plusDays(bestIdx.toLong()).dayOfMonth,
                        modeValueText(workSystem, values[bestIdx]),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 值 → 展示文字：工地=工数（1 位小数可省），其他=加班时长（分钟归一） */
@Composable
internal fun modeValueText(workSystem: WorkSystem, value: Float): String =
    if (workSystem == WorkSystem.SITE) {
        val num = if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
        stringResource(R.string.stats_works_value, num)
    } else {
        TimeUtils.prettyDuration(value.roundToInt())
    }
