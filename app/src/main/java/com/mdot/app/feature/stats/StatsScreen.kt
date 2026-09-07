package com.mdot.app.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.KeyValue
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.DatePick
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

enum class StatsDimension(val labelRes: Int) {
    CYCLE(R.string.stats_dim_cycle), MONTH(R.string.stats_dim_month), YEAR(R.string.stats_dim_year), CUSTOM(R.string.stats_dim_custom),
}

enum class PieMode(val labelRes: Int) { SHIFT(R.string.stats_pie_shift), LEAVE(R.string.stats_pie_leave) }

data class DayBar(val label: String, val minutes: Int, val date: LocalDate?)
data class PieSlice(val label: String, val minutes: Int)

data class StatsUiState(
    val dimension: StatsDimension = StatsDimension.CYCLE,
    val rangeLabel: String = "",
    val output: PayrollCalculator.Output? = null,
    val showMoney: Boolean = false,
    val workSystem: com.mdot.app.domain.model.WorkSystem = com.mdot.app.domain.model.WorkSystem.STANDARD,
    val bars: List<DayBar> = emptyList(),
    val pieShift: List<PieSlice> = emptyList(),
    val pieLeave: List<PieSlice> = emptyList(),
    val pieMode: PieMode = PieMode.SHIFT,
    val selectedBarLabel: String? = null,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    recordRepo: RecordRepository,
    settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    dataRevision: com.mdot.app.core.repository.DataRevision,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dimension = MutableStateFlow(StatsDimension.CYCLE)
    private val customFrom = MutableStateFlow<LocalDate?>(null)
    private val customTo = MutableStateFlow<LocalDate?>(null)

    /** 饼图模式 / 柱状图选中项：与数据流解耦，独立保存 */
    val pieMode = MutableStateFlow(PieMode.SHIFT)
    val selectedBar = MutableStateFlow<String?>(null)

    fun onPieMode(mode: PieMode) {
        pieMode.value = mode
    }

    fun selectBar(label: String?) {
        selectedBar.value = if (selectedBar.value == label) null else label
    }

    val uiState: StateFlow<StatsUiState> = combine(
        dimension,
        customFrom,
        customTo,
        // 云端恢复导入后 DataRevision bump → 强制本页全量重算
        combine(settings.cycleAnchorDayFlow, dataRevision.version) { anchor, _ -> anchor },
        combine(settings.salaryFlow, settings.workdaysFlow) { s, w -> s to w },
    ) { dim, cf, ct, anchor, (salary, workdays) ->
        val range = when (dim) {
            StatsDimension.CYCLE -> CycleCalculator.periodContaining(today, anchor)
            StatsDimension.MONTH -> CycleCalculator.naturalMonth(java.time.YearMonth.from(today))
            StatsDimension.YEAR -> CycleCalculator.yearPeriod(today.year)
            StatsDimension.CUSTOM -> {
                val from = cf ?: today.withDayOfMonth(1)
                val to = ct ?: today
                if (from.isAfter(to)) null else CycleCalculator.Period(from, to)
            }
        }
        Triple(dim, range, salary to workdays)
    }.flatMapLatest { (dim, range, salaryWorkdays) ->
        val (salary, workdays) = salaryWorkdays
        if (range == null) return@flatMapLatest flowOf(StatsUiState(dimension = dim))
        combine(
            recordRepo.observeRange(range.from, range.to),
            recordRepo.observeAdjustments(),
        ) { records, adjustments ->
            val tierOf: (LocalDate) -> RateTier = { date -> holidayRepo.tierFor(date, workdays) }
            // 综合工时：区间标准 = 应出勤天数 × 8h（手动覆盖在引擎内优先，10 文档 §4）
            val standardMinutes =
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(range.from, range.to)
                    CycleCalculator.standardMinutesFor(range.from, range.to, workdays, holidays, makeups)
                } else null
            val out = PayrollCalculator.summarize(
                PayrollCalculator.Input(
                    salary,
                    records.map { it.toCalcLite() },
                    adjustments.map { it.toCalcLite() },
                    tierOf,
                    standardMinutes = standardMinutes,
                )
            )
            val showMoney = when (salary.mode) {
                com.mdot.app.domain.model.SalaryMode.BASE -> salary.hasBaseSalary
                com.mdot.app.domain.model.SalaryMode.MANUAL ->
                    salary.hasBaseSalary || salary.manualRatesCents.values.any { it > 0 }
            }
            val bars = if (dim == StatsDimension.YEAR) {
                (1..12).map { m ->
                    val minutes = records.filter { it.date.monthValue == m && it.type == RecordType.OT }
                        .sumOf { it.durationMinutes }
                    DayBar("${m}月", minutes, null)
                }
            } else {
                records.filter { it.type == RecordType.OT }
                    .groupBy { it.date }
                    .map { (date, list) ->
                        DayBar("${date.monthValue}/${date.dayOfMonth}", list.sumOf { it.durationMinutes }, date)
                    }
                    .sortedBy { it.date }
            }
            val pieShift = records.filter { it.type == RecordType.OT }
                .groupBy { it.shiftName ?: "未选班次" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }) }
                .sortedByDescending { it.minutes }
            val pieLeave = records.filter { it.type == RecordType.LEAVE }
                .groupBy { it.leaveType?.displayName ?: "其他" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }) }
                .sortedByDescending { it.minutes }

            StatsUiState(
                dimension = dim,
                rangeLabel = range.toString(),
                output = out,
                showMoney = showMoney,
                workSystem = salary.workSystem,
                bars = bars,
                pieShift = pieShift,
                pieLeave = pieLeave,
                pieMode = PieMode.SHIFT,
                customFrom = customFrom.value,
                customTo = customTo.value,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onDimension(d: StatsDimension) {
        dimension.value = d
        if (d != StatsDimension.CUSTOM) {
            customFrom.value = null
            customTo.value = null
        }
    }

    fun onCustomFrom(d: LocalDate) {
        customFrom.value = d
        if ((customTo.value ?: d).isBefore(d)) customTo.value = d
    }

    fun onCustomTo(d: LocalDate) {
        if (d.isAfter(LocalDate.now())) return
        customTo.value = d
        if ((customFrom.value ?: d).isAfter(d)) customFrom.value = d
    }
}

/** 统计页（03 文档 §5.4 线框） */
@Composable
fun StatsScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    vm: StatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pieMode by vm.pieMode.collectAsStateWithLifecycle()
    val selectedBar by vm.selectedBar.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<String?>(null) } // "from" | "to"

    val topGap = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + Spacing.xs
    Column(Modifier.fillMaxSize()) {
        if (canBack) {
            JiabanTopBar(title = stringResource(R.string.stats_title), onBack = onBack)
        } else {
            Spacer(Modifier.height(topGap))
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.page),
            contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = !canBack),
        ) {
        item {
        Spacer(Modifier.height(Spacing.m))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsDimension.entries.forEach { dim ->
                FilterChip(
                    selected = state.dimension == dim,
                    onClick = { vm.onDimension(dim) },
                    label = { Text(stringResource(dim.labelRes)) },
                )
            }
        }
        if (state.dimension == StatsDimension.CUSTOM) {
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "from" }) {
                    Text(
                        stringResource(
                            R.string.stats_custom_from,
                            state.customFrom?.let(TimeUtils::mdCn) ?: stringResource(R.string.stats_custom_from_default),
                        )
                    )
                }
                OutlinedButton(onClick = { picking = "to" }) {
                    Text(
                        stringResource(
                            R.string.stats_custom_to,
                            state.customTo?.let(TimeUtils::mdCn) ?: stringResource(R.string.stats_custom_to_default),
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            state.rangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.m))
        }

        val out = state.output
        if (out == null || out.breakdowns.isEmpty()) {
            item {
                EmptyState(
                    icon = painterResource(R.drawable.ic_ms_bar_chart),
                    title = stringResource(R.string.stats_empty_title),
                    hint = when (state.workSystem) {
                        com.mdot.app.domain.model.WorkSystem.HOURLY -> stringResource(R.string.stats_empty_hint_hourly)
                        com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> stringResource(R.string.stats_empty_hint_comp)
                        else -> stringResource(R.string.stats_empty_hint)
                    },
                    actionText = stringResource(R.string.stats_empty_action),
                    onAction = { vm.recordSheet.open(LocalDate.now()) },
                )
            }
        } else {
        item {

        // ---- 汇总卡 ----
        SectionCard {
            Column {
                Row {
                    KeyValue(
                        when (state.workSystem) {
                            com.mdot.app.domain.model.WorkSystem.HOURLY -> stringResource(R.string.stats_ot_hourly)
                            com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> stringResource(R.string.stats_ot_comp)
                            else -> stringResource(R.string.stats_ot)
                        },
                        TimeUtils.prettyDuration(out.otMinutes),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    KeyValue(
                        if (state.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY) stringResource(R.string.stats_income_hourly) else stringResource(R.string.stats_ot_pay), if (state.showMoney) Money.yuanWithSign(out.otPayCents) else "-",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    KeyValue(
                        stringResource(R.string.stats_summary_net_income), if (state.showMoney) Money.yuanWithSign(out.incomeCents) else "-",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.m))
                Row {
                    KeyValue(
                        stringResource(R.string.stats_leave), TimeUtils.prettyDuration(out.leaveMinutes),
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    KeyValue(
                        stringResource(R.string.stats_leave_deduct), if (state.showMoney) Money.yuanWithSign(out.leaveDeductCents) else "-",
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 综合工时：周期口径行（10 文档 F-Z6）
                if (state.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE) {
                    Spacer(Modifier.height(Spacing.m))
                    Row {
                        KeyValue(
                            stringResource(R.string.stats_period_total),
                            TimeUtils.prettyDuration(out.otMinutes + out.holidayWorkMinutes),
                            modifier = Modifier.weight(1f),
                        )
                        KeyValue(
                            stringResource(R.string.stats_period_standard),
                            TimeUtils.prettyDuration(out.periodStandardMinutes),
                            modifier = Modifier.weight(1f),
                        )
                        KeyValue(
                            stringResource(R.string.stats_overtime),
                            TimeUtils.prettyDuration(out.overtimeMinutes),
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 柱状图 ----
        SectionCard {
            Column {
                Text(if (state.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY) stringResource(R.string.stats_bar_title_hourly) else stringResource(R.string.stats_bar_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                if (state.bars.isEmpty()) {
                    Text(if (state.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY) stringResource(R.string.stats_bar_empty_hourly) else stringResource(R.string.stats_bar_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DailyBarChart(
                        bars = state.bars,
                        selectedLabel = selectedBar,
                        onSelect = vm::selectBar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                    state.bars.firstOrNull { it.label == selectedBar }?.let { bar ->
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.stats_bar_selected, bar.label, TimeUtils.prettyDuration(bar.minutes)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 饼图 ----
        SectionCard {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PieMode.entries.forEach { mode ->
                        FilterChip(
                            selected = pieMode == mode,
                            onClick = { vm.onPieMode(mode) },
                            label = { Text(stringResource(mode.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s))
                val slices = if (pieMode == PieMode.SHIFT) state.pieShift else state.pieLeave
                if (slices.isEmpty()) {
                    Text(
                        if (pieMode == PieMode.SHIFT) { if (state.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY) stringResource(R.string.stats_pie_empty_hourly) else stringResource(R.string.stats_pie_empty) } else stringResource(R.string.stats_pie_empty_leave),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PieChart(
                        slices = slices,
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(Spacing.m))
                    val total = slices.sumOf { it.minutes }.coerceAtLeast(1)
                    slices.forEachIndexed { index, slice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(pieColor(index), CircleShape)
                            )
                            Spacer(Modifier.size(Spacing.s))
                            Text(slice.label, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text(
                                stringResource(
                                    R.string.stats_pie_legend,
                                    TimeUtils.prettyDuration(slice.minutes),
                                    slice.minutes * 100 / total,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))
        }

        // ---- 明细列表 ----
        item {
        Text(stringResource(R.string.stats_detail_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        }
        val sorted = out.breakdowns.sortedByDescending { it.record.date }
        items(sorted) { bd ->
            val r = bd.record
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.recordSheet.open(r.date, r.type) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${TimeUtils.md(r.date)} ${TimeUtils.weekdayCn(r.date)}" +
                            (r.shiftName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (r.type == RecordType.OT) {
                            stringResource(
                                R.string.stats_detail_ot_line,
                                if (state.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY) stringResource(R.string.stats_ot_hourly) else stringResource(R.string.stats_ot),
                                TimeUtils.prettyDuration(r.durationMinutes),
                                bd.tier?.displayName ?: "",
                            ) +
                                if (r.toCompMinutes > 0) stringResource(R.string.stats_detail_ot_comp, TimeUtils.prettyDuration(r.toCompMinutes)) else ""
                        } else {
                            stringResource(
                                R.string.stats_detail_leave,
                                TimeUtils.prettyDuration(r.durationMinutes),
                                r.leaveType?.displayName ?: "",
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (r.type == RecordType.OT) "+" + Money.yuanText(bd.amountCents)
                        else "−" + Money.yuanText(bd.amountCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (r.type == RecordType.OT) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    r.note?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
        }
        }
    }

    picking?.let { which ->
        DatePick(
            initial = if (which == "from") state.customFrom ?: LocalDate.now().withDayOfMonth(1)
            else state.customTo ?: LocalDate.now(),
            onPick = { d ->
                if (which == "from") vm.onCustomFrom(d) else vm.onCustomTo(d)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun pieColor(index: Int): Color = pieColor(index, MaterialTheme.colorScheme)

private fun pieColor(index: Int, scheme: androidx.compose.material3.ColorScheme): Color =
    listOf(
        scheme.primary, scheme.tertiary, scheme.secondary,
        scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer,
        scheme.outline,
    )[index % 7]

/** Canvas 柱状图（04 文档 §6.3），点击柱子高亮；数据变化时柱子生长动画 */
@Composable
private fun DailyBarChart(
    bars: List<DayBar>,
    selectedLabel: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Duration.slow, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val idx = (offset.x / size.width * bars.size).toInt().coerceIn(0, bars.size - 1)
                    val label = bars[idx].label
                    onSelect(if (label == selectedLabel) null else label)
                }
            }
    ) {
        if (bars.isEmpty()) return@Canvas
        val max = (bars.maxOf { it.minutes }).coerceAtLeast(30)
        val slot = size.width / bars.size
        val barWidth = (slot * 0.55f).coerceAtMost(28.dp.toPx())
        // 基线
        drawLine(outline, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
        bars.forEachIndexed { i, bar ->
            val cx = slot * (i + 0.5f)
            val h = if (bar.minutes <= 0) 0f
            else size.height * 0.86f * bar.minutes / max * progress.value
            val topLeft = Offset(cx - barWidth / 2, size.height - 2f - h)
            drawRoundRect(
                color = if (bar.label == selectedLabel) primary else primary.copy(alpha = 0.55f),
                topLeft = topLeft,
                size = Size(barWidth, h.coerceAtLeast(if (bar.minutes > 0) 4f * progress.value else 0f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            )
        }
    }
}

/** Canvas 饼图（环形，中心留白）；数据变化时扫描展开动画 */
@Composable
private fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = List(slices.size) { pieColor(it, scheme) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Duration.slow, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val total = slices.sumOf { it.minutes }.toFloat().coerceAtLeast(1f)
        var start = -90f
        val stroke = size.minDimension * 0.28f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        slices.forEachIndexed { index, slice ->
            val sweep = slice.minutes / total * 360f * progress.value
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
    // 中心文字用叠加层
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            TimeUtils.prettyDuration(slices.sumOf { it.minutes }),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
