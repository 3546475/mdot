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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
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
    // 绘图区高度：**以左侧绘图区的实际测量值为唯一真值**（[plotHeightPx]），
    // 右侧刻度按它定位 —— 两边不再各写一个 dp（此前 113/113 一写死就长期不一致：
    // 顶对齐修掉了 18dp 整体下沉，但绘图区真实高度与写死值仍有差额，越靠上偏得越多）。
    val chartHeight = 113.dp
    var plotHeightPx by remember { mutableFloatStateOf(0f) }
    // 组合期算好像素值（offset lambda 不是组合上下文，不能调 toPx()）
    val fallbackPlotHeightPx = with(LocalDensity.current) { chartHeight.toPx() }
    val tickMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
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
            // ⚠️ 必须 **Top** 对齐（2026-10-09）：此前用 `Bottom`，而左列 = 绘图区 + x 轴标签（高 18dp）、
            // 右列（右侧刻度）只有绘图区高 ⇒ 底部对齐会把右列整体**下沉 18dp**，刻度整体比网格线低一线。
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            // 记下绘图区**实际**高度供右侧刻度定位（单一真值，避免两边各猜一个 dp）
                            .onSizeChanged { plotHeightPx = it.height.toFloat() }
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
                // 右侧刻度：**与绘图区同一个 [chartHeight]**、按网格线同样的公式定位——
                // 不再 Top/Center/Bottom 均分（均分会让「0」的文字**底边**贴 0% 线、中心浮在线上方，
                // 看着像掉出绘图区并与 x 轴日期挤在一行，2026-10-09 真机取像素定位）。
                Box(Modifier.height(chartHeight)) {
                    // 刻度拆成**数字列 + 单位列**两段（2026-10-09 用户要求）：
                    // ① 先「0 要和数字对齐」——此前三档都是 End 右对齐，「4.5小时」/「2.3小时」的**末字**贴右边
                    //    ⇒ 单独的「0」落在「时」下方而不是数字下方；
                    // ② 再「0 和上方数字**左边**个位对齐」——数字列改**左对齐**，三档数字左缘齐平
                    //    （`0` 只有个位，居在数字列左端；右缘不齐是有意为之）。
                    // 两列各自固定宽度 ⇒ 数字列左缘对齐的同时，单位列仍整列齐平。
                    val ticks = listOf(1f, 0.5f, 0f).map { f ->
                        f to (if (f == 0f) "0" else modeValueText(workSystem, maxV * f))
                    }
                    fun splitTick(t: String): Pair<String, String> {
                        val i = t.indexOfFirst { !it.isDigit() && it != '.' && it != '-' }
                        return if (i > 0) t.substring(0, i) to t.substring(i) else t to ""
                    }
                    // 两列各自的列宽 = 该列在三档里的最大实测宽（数字列最宽档通常是「4.5」这类）
                    val numW = ticks.maxOf { (_, t) ->
                        tickMeasurer.measure(AnnotatedString(splitTick(t).first), labelStyle).size.width
                    }.toFloat()
                    val unitW = ticks.maxOf { (_, t) ->
                        val u = splitTick(t).second
                        if (u.isEmpty()) 0f
                        else tickMeasurer.measure(AnnotatedString(u), labelStyle).size.width.toFloat()
                    }
                    val density = LocalDensity.current
                    ticks.forEach { (f, tickText) ->
                        // ⚠️ 文案与高度必须在**组合期**算好：modeValueText 是 @Composable，
                        // 而 offset 的 lambda 跑在布局期，不能调它（2026-10-09 编译错）。
                        val (numPart, unitPart) = splitTick(tickText)
                        val tickH = tickMeasurer.measure(AnnotatedString(tickText), labelStyle).size.height
                        Row(
                            Modifier
                                // TopStart：整列靠左起排，三档左缘齐平（Right 会把单字符的「0」甩到右边）
                                .align(Alignment.TopStart)
                                .offset {
                                    // 与 Canvas 里 `size.height * (1f - f)` 完全同一公式。
                                    // ⚠️ 垂直对齐按**文字块边缘**而非中心：顶部那条线就在绘图区顶边，
                                    // 中心对齐会把两行的「4.5小时」压到线**下方** 45px（2026-10-09 真机量）。
                                    // 规则：f=1 顶对齐、f=0.5 居中、f=0 底对齐 —— 三档各贴各的线。
                                    val h = if (plotHeightPx > 0f) plotHeightPx else fallbackPlotHeightPx
                                    val line = h * (1f - f)
                                    val dy = when (f) {
                                        1f -> 0f
                                        0f -> -tickH.toFloat()
                                        else -> -(tickH / 2f)
                                    }
                                    IntOffset(0, (line + dy).roundToInt())
                                },
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                numPart,
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // 固定列宽 + **左对齐**：三档数字左缘齐平（用户要求「0 和上方数字左边个位对齐」）
                                modifier = Modifier.width(with(density) { numW.toDp() }),
                            )
                            if (unitPart.isNotEmpty()) {
                                Text(
                                    unitPart,
                                    style = labelStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                // 「0」无单位 ⇒ 补同宽空位，单位列才与另两档齐平
                                Spacer(Modifier.width(with(density) { unitW.toDp() }))
                            }
                        }
                    }
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
