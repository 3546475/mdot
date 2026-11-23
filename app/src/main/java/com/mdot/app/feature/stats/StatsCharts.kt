package com.mdot.app.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.chartHintText
import com.mdot.app.core.designsystem.component.modeValueText
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.jiabanFilterChipColors

/** 月柱状图：统计页薄包装（共享组件在 core/designsystem/component/MonthBarCard.kt）。
 *  铺满所选区间（21 文档 B3）：≤31 天每日柱、超长按周聚合；取数用 barValues（区间内），与热点图窗口解耦 */
@Composable
internal fun MonthBarCard(
    state: StatsUiState,
    onDayLongPress: (LocalDate) -> Unit,
    hintValueText: @Composable (LocalDate, Float) -> String?,
) {
    val from = state.rangeFrom ?: return
    val to = state.rangeTo ?: return
    if (from.isAfter(to)) return
    val bars = com.mdot.app.domain.SiteRanges.bucketize(from, to, state.barValues)
    if (bars.size < 2) return
    // 周聚合柱（21 文档 B3 用户反馈）：浮窗日期段改周区间「M/d – M/d」、禁长按（柱=整周，长按无单日语义）
    val weekly = bars.any { it.label != null }
    val hintFn = if (weekly) {
        chartHintText(
            state.workSystem,
            dateLabel = { d -> "${TimeUtils.mdCn(d)} – ${TimeUtils.mdCn(d.plusDays(6))}" },
        )
    } else hintValueText
    com.mdot.app.core.designsystem.component.MonthBarCard(
        values = bars.map { it.value },
        from = bars.first().date,
        workSystem = state.workSystem,
        onDayLongPress = if (weekly) null else onDayLongPress,
        hintValueText = hintFn,
        dates = bars.map { it.date },
        barLabels = bars.map { it.label },
    )
}

/** 汇总卡（模式化 hero，与明细页同视觉体系）：
 * 工地=工数为主 + 应得/已借支/待结三色钱账；非工地=收入为主 + 档位分布（标准）/周期口径（综合）。 */
@Composable
internal fun SummaryCard(state: StatsUiState) {
    SectionCard(containerColor = emphasisCardSurface()) {
        // SectionCard 内已含 Spacing.l padding（与明细页 hero 同一层），此处不再叠加
        Column(Modifier.fillMaxWidth()) {
            if (state.workSystem == WorkSystem.SITE) {
                val out = state.siteSummary ?: return@Column
                Text(
                    stringResource(R.string.site_stat_works),
                    style = MaterialTheme.typography.labelSmall,
                    color = emphasisCardInk().copy(alpha = 0.8f),
                )
                AnimatedNumberText(
                    value = out.totalWorksMilli,
                    text = { milli ->
                        val tw = milli / 1000f
                        val wn = if (tw % 1f == 0f) tw.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", tw)
                        stringResource(R.string.stats_works_value, wn)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = emphasisCardInk(),
                    fontWeight = FontWeight.Bold,
                    label = "summaryWorks",
                )
                Text(
                    listOf(
                        stringResource(R.string.stats_ot_duration_line, TimeUtils.prettyDuration(out.otMinutes)),
                        stringResource(R.string.stats_site_days_value, out.daysWork),
                        stringResource(R.string.site_stat_piece_pay) + " " + Money.yuanWithSign(out.piecePayCents),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = emphasisCardInk().copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_work_pay),
                        Money.yuanWithSign(out.receivableCents),
                        emphasisCardInk(),
                        modifier = Modifier.weight(1f),
                        animatedCents = out.receivableCents,
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_advance),
                        Money.yuanWithSign(out.advanceTotalCents),
                        SiteMoneyColors.ReceivedGreen,
                        modifier = Modifier.weight(1f),
                        animatedCents = out.advanceTotalCents,
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_pending),
                        Money.yuanWithSign(out.pendingCents),
                        if (out.pendingCents < 0) MaterialTheme.colorScheme.error else SiteMoneyColors.PendingOrange,
                        modifier = Modifier.weight(1f),
                        animatedCents = out.pendingCents,
                    )
                }
            } else {
                val out = state.output ?: return@Column
                Text(
                    when (state.workSystem) {
                        WorkSystem.HOURLY -> stringResource(R.string.stats_income_hourly)
                        else -> stringResource(R.string.stats_ot_pay)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = emphasisCardInk().copy(alpha = 0.8f),
                )
                if (state.showMoney) {
                    AnimatedMoneyText(
                        out.incomeCents,
                        style = MaterialTheme.typography.headlineMedium,
                        color = emphasisCardInk(),
                        fontWeight = FontWeight.Bold,
                        label = "summaryIncome",
                    )
                } else {
                    Text(
                        "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = emphasisCardInk(),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    listOf(
                        stringResource(R.string.stats_ot_duration_line, TimeUtils.prettyDuration(out.otMinutes)),
                        if (out.leaveDeductCents > 0)
                            stringResource(R.string.stats_leave_deduct_line, Money.yuanWithSign(out.leaveDeductCents))
                        else "",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = emphasisCardInk().copy(alpha = 0.8f),
                )
                // 标准工时：三档时薪分布胶囊（与明细页同款）
                if (state.workSystem == WorkSystem.STANDARD) {
                    Spacer(Modifier.height(Spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        com.mdot.app.feature.detail.TierChip(RateTier.WEEKDAY, out, Modifier.weight(1f))
                        com.mdot.app.feature.detail.TierChip(RateTier.WEEKEND, out, Modifier.weight(1f))
                        com.mdot.app.feature.detail.TierChip(RateTier.STATUTORY, out, Modifier.weight(1f))
                    }
                }
                // 综合工时：周期口径（10 文档 F-Z6）
                if (state.workSystem == WorkSystem.COMPREHENSIVE) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(
                            R.string.stats_period_line,
                            TimeUtils.prettyDuration(out.otMinutes + out.holidayWorkMinutes),
                            TimeUtils.prettyDuration(out.periodStandardMinutes),
                            TimeUtils.prettyDuration(out.overtimeMinutes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = emphasisCardInk().copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                // 请假：时长 + 扣款（红）
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.stats_leave),
                        TimeUtils.prettyDuration(out.leaveMinutes),
                        emphasisCardInk(),
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.stats_leave_deduct),
                        if (state.showMoney) Money.yuanWithSign(out.leaveDeductCents) else "-",
                        MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        animatedCents = if (state.showMoney) out.leaveDeductCents else null,
                    )
                }
            }
        }
    }
}

/** 饼图卡：非工地=班次分布/请假类型切换；工地=项目工数 */
@Composable
internal fun PieCard(state: StatsUiState, pieMode: PieMode, onPieMode: (PieMode) -> Unit) {
    val isSite = state.workSystem == WorkSystem.SITE
    SectionCard {
        Column {
            if (!isSite) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PieMode.entries.forEach { mode ->
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = pieMode == mode,
                            onClick = { onPieMode(mode) },
                            label = { Text(stringResource(mode.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s))
            }
            val slices = when {
                isSite -> state.pieSiteProjects
                pieMode == PieMode.LEAVE -> state.pieLeave
                else -> state.pieShift
            }
            if (slices.isEmpty()) {
                Text(
                    when {
                        isSite -> stringResource(R.string.stats_pie_site_empty)
                        pieMode == PieMode.LEAVE -> stringResource(R.string.stats_pie_empty_leave)
                        state.workSystem == WorkSystem.HOURLY -> stringResource(R.string.stats_pie_empty_hourly)
                        else -> stringResource(R.string.stats_pie_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val total = slices.fold(0f) { acc, s -> acc + s.value }.coerceAtLeast(1f)
                PieChart(
                    slices = slices,
                    centerText = modeValueText(state.workSystem, total),
                    modifier = Modifier
                        .size(160.dp)
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(Spacing.m))
                slices.forEachIndexed { index, slice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(slice.colorIndex?.let { pieColor(it) } ?: pieColor(index), CircleShape)
                        )
                        Spacer(Modifier.size(Spacing.s))
                        Text(slice.label, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Text(
                            stringResource(
                                R.string.stats_pie_legend,
                                modeValueText(state.workSystem, slice.value),
                                (slice.value * 100 / total).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun pieColor(index: Int): Color = pieColor(index, MaterialTheme.colorScheme)

internal fun pieColor(index: Int, scheme: androidx.compose.material3.ColorScheme): Color =
    listOf(
        scheme.primary, scheme.tertiary, scheme.secondary,
        scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer,
        scheme.outline,
    )[index % 7]

/** Canvas 饼图（环形，中心留白）；数据变化时扫描展开动画 */
@Composable
private fun PieChart(slices: List<PieSlice>, centerText: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = List(slices.size) { i -> slices[i].colorIndex?.let { pieColor(it, scheme) } ?: pieColor(i, scheme) }
    val slowSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, slowSpec)
    }
    // 中心文字与环同容器叠加（对准环心）
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = slices.fold(0f) { acc, s -> acc + s.value }.coerceAtLeast(1f)
            var start = -90f
            val stroke = size.minDimension * 0.28f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            slices.forEachIndexed { index, slice ->
                val sweep = slice.value / total * 360f * progress.value
                drawArc(
                    color = colors[index],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
                )
                start += sweep
            }
            // 中心合计
            drawCircle(scheme.surfaceContainer, radius = diameter / 2 - stroke - 6f, center = Offset(size.width / 2, size.height / 2))
        }
        Text(centerText, style = MaterialTheme.typography.titleMedium)
    }
}
