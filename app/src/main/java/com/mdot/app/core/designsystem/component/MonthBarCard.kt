package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.mdot.app.core.designsystem.ChartSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.domain.model.WorkSystem
import java.time.LocalDate
import kotlin.math.ceil
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
    /** 长按柱 → 记工弹窗等（docs/15 T6 #19；null 不启用长按） */
    onDayLongPress: ((LocalDate) -> Unit)? = null,
    /** 浮窗文本（日期+时长/工数）；返回 null 则不显示浮窗 */
    hintValueText: @Composable (LocalDate, Float) -> String? = { _, _ -> null },
    /** 各柱日期（21 文档 B3 周聚合用）；null = from + i 天（每日柱）。size 与 values 对齐 */
    dates: List<LocalDate>? = null,
    /** 各柱 X 轴标签；null = 自动日标签（1/5/10/15/20/25/30）。周聚合传「M/d」稀疏标签 */
    barLabels: List<String?>? = null,
) {
    // 是否周柱（21 文档 B3）：判据是**标签里有没有非空值**，而不是「列表本身是否为 null」——
    // 调用方传 `bars.map { it.label }`（日柱时元素全为 null 的**非空**列表）时，旧判据 `barLabels != null`
    // 会把日柱误判成周柱 ⇒ 按周提示、宽柱、周式「最多」全跑出来（2026-09-30 用户报「月柱状图非工地也显示按周汇总」）。
    // 按周聚合只应发生在工地记工且区间 > 31 天：非工地区间 ≤ 29 天（周期收窄 1–29）恒为日柱。
    val weeklyBars = barLabels?.any { it != null } == true
    val days = values.size
    if (days < 2) return
    val barDates = (0 until days).map { dates?.getOrNull(it) ?: from.plusDays(it.toLong()) }
    val maxV = values.maxOrNull() ?: 0f
    val bestIdx = values.indices.maxByOrNull { values[it] } ?: 0
    val hint = rememberChartHint()
    var tapEvent by remember { mutableStateOf<ChartEvent?>(null) }
    // 选中柱（点击切换；仅内部状态，周柱由外部 selectedLabel 驱动）
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    // 手势只写事件；文本在组合上下文计算（手势回调非 @Composable）
    tapEvent?.let { ev ->
        hintValueText(ev.date, ev.value)?.let { hint.value = ChartHint(ev.column, ev.date, it) }
        tapEvent = null
    }

    // 自绘容器：底距比统一卡片更紧（最多日文字贴近下缘）
    Surface(
        modifier = modifier,
        shape = engineShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ChartHintBox(hint = hint.value, columns = days) {
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
                            values.forEachIndexed { idx, v ->
                                val date = barDates[idx]
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .pointerInput(date) {
                                            detectTapGestures(
                                                onTap = {
                                                    tapEvent = ChartEvent(idx, date, v)
                                                    selectedIdx = if (selectedIdx == idx) null else idx
                                                },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDayLongPress?.invoke(date)
                                                },
                                            )
                                        },
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    val selected = selectedIdx == idx
                                    if (v > 0f) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight(
                                                    (v / maxV.coerceAtLeast(1f)).coerceIn(0.03f, 1f)
                                                )
                                                .then(
                                                    // 周聚合柱：占槽宽比例（加宽收紧、与细日柱明显区分）；日柱固定细宽
                                                    if (weeklyBars) {
                                                        Modifier.fillMaxWidth(if (selected) 0.92f else 0.72f)
                                                    } else {
                                                        Modifier.width(if (selected) 7.dp else 5.dp)
                                                    }
                                                )
                                                .background(
                                                    // 选中柱满饱和（区分于未选中渐变）
                                                    if (selected) Brush.verticalGradient(
                                                        listOf(barTopColor, barTopColor.copy(alpha = 0.75f)),
                                                    ) else Brush.verticalGradient(
                                                        listOf(barTopColor, barBottomColor),
                                                    ),
                                                    engineShape(ChartSpec.cellRadius),
                                                )
                                                .then(
                                                    if (selected) Modifier.border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.onPrimary,
                                                        engineShape(ChartSpec.cellRadius),
                                                    ) else Modifier
                                                ),
                                        )
                                    } else if (selected) {
                                        // 空柱选中：底部占位框
                                        Box(
                                            Modifier
                                                .width(8.dp)
                                                .height(10.dp)
                                                .border(
                                                    1.5.dp,
                                                    MaterialTheme.colorScheme.onPrimary,
                                                    engineShape(ChartSpec.cellRadius),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // X 轴标签：周聚合桶自带「M/d」标签；日柱自动 1 5 10 15 20 25 30。
                    // 周桶稀疏**按实测宽度定步长**（相邻标签留间隙、保证不重叠）：按年筛选时周桶可达 53 个，
                    // 原「≤13 全标、否则隔一标一」会画出 26 个标签糊成一整条（2026-09-30 用户报）。
                    val labelStyle = MaterialTheme.typography.labelSmall
                    val measurer = rememberTextMeasurer()
                    val labelTexts: List<Pair<Int, String>> = if (weeklyBars) {
                        barLabels.orEmpty().mapIndexedNotNull { i, l -> l?.let { i to it } }
                    } else {
                        barDates.mapIndexedNotNull { i, d ->
                            if (d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0) i to d.dayOfMonth.toString() else null
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                        val shown = if (weeklyBars && labelTexts.isNotEmpty()) {
                            val slotW = size.width / days
                            val labelW = labelTexts.maxOf {
                                measurer.measure(AnnotatedString(it.second), labelStyle).size.width
                            }
                            val step = maxOf(1, ceil((labelW + Spacing.xs.toPx()) / slotW).toInt())
                            labelTexts.filterIndexed { i, _ -> i % step == 0 }
                        } else {
                            labelTexts
                        }
                        shown.forEach { (idx, text) ->
                            val measured = measurer.measure(
                                AnnotatedString(text),
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
            // 周聚合说明（21 文档 B3 用户反馈：周柱与日柱同形，需明示聚合口径）
            // 仅在真·周柱时出现（工地记工且区间 > 31 天）；日柱不显示（2026-09-30 用户要求）
            if (weeklyBars) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.stats_bar_weekly_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            // 最多日/最多周（纯文字；周聚合时以周起 M/d 标注）
            if (maxV > 0f) {
                Spacer(Modifier.height(Spacing.m))
                Text(
                    if (weeklyBars) {
                        stringResource(
                            R.string.stats_bar_best_label,
                            "${barDates[bestIdx].monthValue}/${barDates[bestIdx].dayOfMonth}",
                            modeValueText(workSystem, values[bestIdx]),
                        )
                    } else {
                        stringResource(
                            R.string.stats_month_best,
                            barDates[bestIdx].dayOfMonth,
                            modeValueText(workSystem, values[bestIdx]),
                        )
                    },
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
