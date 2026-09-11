package com.mdot.app.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.core.designsystem.component.KeyValue
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.WeekBarCard
import com.mdot.app.core.designsystem.component.buildWeekBars
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.core.designsystem.component.DatePick
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.WorkSystem
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
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class StatsDimension(val labelRes: Int) {
    CYCLE(R.string.stats_dim_cycle), MONTH(R.string.stats_dim_month), YEAR(R.string.stats_dim_year), CUSTOM(R.string.stats_dim_custom),
}

enum class PieMode(val labelRes: Int) { SHIFT(R.string.stats_pie_shift), LEAVE(R.string.stats_pie_leave) }

/** 本周柱状单日复用共享组件（value=强度：非工地=加班分钟，工地=工数） */
typealias WeekBar = com.mdot.app.core.designsystem.component.WeekBar

/** 饼图分片：value 随模式取值（非工地=分钟，工地=工数），展示层负责格式化 */
data class PieSlice(
    val label: String,
    val value: Float,
    /** 稳定色索引（工地项目饼图用 projectId，保证同一项目跨页面同色；null=按序取色） */
    val colorIndex: Int? = null,
)

/** 工地明细行（与汇总同口径：当前项目） */
data class SiteDetailRow(
    val date: LocalDate,
    val kind: SiteDetailKind,
    /** 出勤工数×1000 */
    val worksMilli: Long = 0,
    val otMinutes: Int = 0,
    val amountCents: Long = 0,
    val itemName: String = "",
    val purpose: AdvancePurpose? = null,
)

enum class SiteDetailKind {
    /** 出工（含加班） */
    WORK,
    /** 显式休息 */
    REST,
    /** 包工/工量 */
    PIECE,
    /** 借支 */
    ADVANCE,
    /** 部分结算（本次结算金额，从待结余额拿走一笔） */
    PARTIAL,
}

data class StatsUiState(
    val dimension: StatsDimension = StatsDimension.CYCLE,
    val rangeLabel: String = "",
    val showMoney: Boolean = false,
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 汇总（非工地，PayrollCalculator） */
    val output: PayrollCalculator.Output? = null,
    /** 汇总（工地，SitePayCalculator） */
    val siteSummary: SitePayCalculator.Output? = null,
    /** 统一热点图（所有模式）：非工地=加班小时，工地=工数 */
    val heatValues: Map<LocalDate, Float> = emptyMap(),
    /** 热点图铺格终止日（区间尾与今天取小，防未来空格子） */
    val heatEnd: LocalDate = LocalDate.now(),
    /** 区间起止日（月柱状图铺满整月用，不截断到今天） */
    val rangeFrom: LocalDate? = null,
    val rangeTo: LocalDate? = null,
    /** 统一柱状：本周周一~周日 7 柱，空日/未来日占位 */
    val weekBars: List<WeekBar> = emptyList(),
    /** 饼图（非工地）：班次分布 / 请假类型 */
    val pieShift: List<PieSlice> = emptyList(),
    val pieLeave: List<PieSlice> = emptyList(),
    /** 饼图（工地）：项目工数（跨项目聚合） */
    val pieSiteProjects: List<PieSlice> = emptyList(),
    /** 工地明细（当前项目，日期倒序） */
    val siteDetails: List<SiteDetailRow> = emptyList(),
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
)

/** 工地每日统计（首页每日时长卡用） */
data class SiteDayStat(
    val date: LocalDate,
    val worksMilli: Long,
    val otMinutes: Int,
    val payCents: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    recordRepo: RecordRepository,
    settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    dataRevision: com.mdot.app.core.repository.DataRevision,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dimension = MutableStateFlow(StatsDimension.CYCLE)
    private val customFrom = MutableStateFlow<LocalDate?>(null)
    private val customTo = MutableStateFlow<LocalDate?>(null)

    /** 饼图模式（非工地）：与数据流解耦，独立保存 */
    val pieMode = MutableStateFlow(PieMode.SHIFT)

    /** 柱状图选中项（M/D 标签）：与数据流解耦 */
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
        val heatEnd = minOf(range.to, today)
        if (salary.workSystem == WorkSystem.SITE) {
            // 工地记工：汇总/明细/热点图走当前项目（SitePayCalculator 口径），饼图为跨项目「项目工数」
            val pid = siteRepo.currentProjectId()
            combine(
                siteRepo.observeAttendance(pid, range.from, range.to),
                siteRepo.observePieceWorks(pid, range.from, range.to),
                siteRepo.observeAdvances(pid, range.from, range.to),
                siteRepo.observeAttendanceAllProjects(range.from, range.to),
                siteRepo.observeAllProjects(),
            ) { atts, pieces, advs, allAtts, projects ->
                val out0 = SitePayCalculator.summarize(
                    SitePayCalculator.Input(atts, pieces, advs)
                )
                // 待结余额扣减未结清的部分结算（本次结算金额）
                val partial = siteRepo.partialSettledTotal(pid)
                val out = if (partial > 0) out0.copy(pendingCents = out0.pendingCents - partial) else out0
                // 工数 = 上班分钟 ÷ 基准分钟快照（当日标准）
                fun worksMilliOf(minutes: Int, base: Int): Long = minutes * 1000L / base.coerceAtLeast(1)
                val worksByDay: Map<LocalDate, Float> = atts
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, list) ->
                        list.sumOf { worksMilliOf(it.workMinutes, it.baseMinutes) } / 1000f
                    }
                val nameById = projects.associate { it.id to it.name }
                val pieSiteProjects = allAtts
                    .filter { it.workMinutes > 0 }
                    .groupBy { it.projectId }
                    .map { (projectId, list) ->
                        PieSlice(
                            label = nameById[projectId] ?: "#$projectId",
                            value = list.sumOf { worksMilliOf(it.workMinutes, it.baseMinutes) } / 1000f,
                            colorIndex = (projectId % 7).toInt(),
                        )
                    }
                    .filter { it.value > 0f }
                    .sortedByDescending { it.value }
                val details = buildList {
                    atts.forEach { a ->
                        add(
                            SiteDetailRow(
                                date = LocalDate.parse(a.date),
                                kind = if (a.dayStatus == "REST") SiteDetailKind.REST else SiteDetailKind.WORK,
                                worksMilli = worksMilliOf(a.workMinutes, a.baseMinutes),
                                otMinutes = a.otMinutes,
                                amountCents = a.workPayCents + a.otPayCents,
                            )
                        )
                    }
                    pieces.forEach { p ->
                        add(
                            SiteDetailRow(
                                date = LocalDate.parse(p.date),
                                kind = SiteDetailKind.PIECE,
                                amountCents = p.amountCents,
                                itemName = p.itemName,
                            )
                        )
                    }
                    advs.forEach { a ->
                        add(
                            SiteDetailRow(
                                date = LocalDate.parse(a.date),
                                kind = SiteDetailKind.ADVANCE,
                                amountCents = a.amountCents,
                                purpose = runCatching { AdvancePurpose.valueOf(a.purpose) }.getOrNull(),
                            )
                        )
                    }
                }.sortedByDescending { it.date }
                StatsUiState(
                    dimension = dim,
                    rangeLabel = range.toString(),
                    workSystem = WorkSystem.SITE,
                    siteSummary = out,
                    heatValues = worksByDay,
                    heatEnd = heatEnd,
                    rangeFrom = range.from,
                    rangeTo = range.to,
                    weekBars = buildWeekBars(worksByDay),
                    pieSiteProjects = pieSiteProjects,
                    siteDetails = details,
                    customFrom = customFrom.value,
                    customTo = customTo.value,
                )
            }
        } else combine(
            recordRepo.observeRange(range.from, range.to),
            recordRepo.observeAdjustments(),
        ) { records, adjustments ->
            val tierOf: (LocalDate) -> RateTier = { date -> holidayRepo.tierFor(date, workdays) }
            // 综合工时：区间标准 = 应出勤天数 × 8h（手动覆盖在引擎内优先，10 文档 §4）
            val standardMinutes =
                if (salary.workSystem == WorkSystem.COMPREHENSIVE) {
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
            val pieShift = records.filter { it.type == RecordType.OT }
                .groupBy { it.shiftName ?: "未选班次" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }.toFloat()) }
                .sortedByDescending { it.value }
            val pieLeave = records.filter { it.type == RecordType.LEAVE }
                .groupBy { it.leaveType?.displayName ?: "其他" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }.toFloat()) }
                .sortedByDescending { it.value }
            // 统一热点图强度：加班分钟（WorkHeatmap 按 v/max 归一，单位不影响渲染；柱状卡 prettyDuration 需要分钟）
            val heat = records.filter { it.type == RecordType.OT }
                .groupBy { it.date }
                .mapValues { (_, list) -> list.sumOf { it.durationMinutes }.toFloat() }
            StatsUiState(
                dimension = dim,
                rangeLabel = range.toString(),
                output = out,
                showMoney = showMoney,
                workSystem = salary.workSystem,
                pieShift = pieShift,
                pieLeave = pieLeave,
                heatValues = heat,
                heatEnd = heatEnd,
                rangeFrom = range.from,
                rangeTo = range.to,
                weekBars = buildWeekBars(heat),
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

/** 统计页（03 文档 §5.4 线框；v0.6 全制度统一布局：维度行→汇总→热点图→本周柱状→饼图→明细入口） */
@Composable
fun StatsScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    onOpenDetail: () -> Unit = {},
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
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                .padding(horizontal = Spacing.page),
            contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = !canBack),
        ) {
        item {
            Spacer(Modifier.height(Spacing.m))

            // ---- 维度选择行 ----
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

        val isSite = state.workSystem == WorkSystem.SITE
        val hasData = if (isSite) {
            val s = state.siteSummary
            s != null && (s.daysWork > 0 || s.piecePayCents > 0L || s.advanceTotalCents > 0L)
        } else {
            val out = state.output
            out != null && out.breakdowns.isNotEmpty()
        }

        if (!hasData) {
            item {
                EmptyState(
                    icon = painterResource(R.drawable.ic_ms_bar_chart),
                    title = stringResource(R.string.stats_empty_title),
                    hint = if (isSite) stringResource(R.string.stats_site_empty_hint) else when (state.workSystem) {
                        WorkSystem.HOURLY -> stringResource(R.string.stats_empty_hint_hourly)
                        WorkSystem.COMPREHENSIVE -> stringResource(R.string.stats_empty_hint_comp)
                        else -> stringResource(R.string.stats_empty_hint)
                    },
                    actionText = stringResource(R.string.stats_empty_action),
                    onAction = { vm.recordSheet.open(LocalDate.now()) },
                )
            }
        } else {
            // ---- 汇总卡（按制度取数） ----
            item {
                SummaryCard(state)
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 本周柱状卡（共享组件，与首页同款） ----
            item {
                WeekBarCard(
                    bars = state.weekBars,
                    valueText = { modeValueText(state.workSystem, it) },
                    selectedLabel = selectedBar,
                    onSelect = vm::selectBar,
                )
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 月柱状图（区间 ≤31 天时展示：整月每日柱 + 刻度 + 最多日文字） ----
            val rangeDays = state.rangeFrom?.let { f -> (state.heatEnd.toEpochDay() - f.toEpochDay()).toInt() + 1 } ?: 0
            if (rangeDays in 2..31) {
                item {
                    MonthBarCard(state)
                    Spacer(Modifier.height(Spacing.m))
                }
            }

            // ---- 热点图卡（所有模式同款强度：加班小时 / 工数） ----
            item {
                SectionCard {
                    WorkHeatmap(values = state.heatValues, end = state.heatEnd)
                }
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 饼图卡 ----
            item {
                PieCard(state, pieMode, vm::onPieMode)
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 明细入口（明细列表独立成页，从首页收入卡/此处均可进入） ----
            item {
                SectionCard(onClick = onOpenDetail) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.stats_detail_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.home_income_detail),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
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

/** 模式取值文本：非工地=时长（小时），工地=工数（"N 工"） */
@Composable
private fun modeValueText(workSystem: WorkSystem, value: Float): String =
    if (workSystem == WorkSystem.SITE) {
        val num = if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
        stringResource(R.string.stats_works_value, num)
    } else {
        TimeUtils.prettyDuration(value.roundToInt())
    }

/** 月柱状图（参考竞品版式）：区间每日柱 + 右侧最大/半程/0 虚线刻度 + 最多日高亮胶囊 */
@Composable
private fun MonthBarCard(state: StatsUiState) {
    // 铺满完整区间（自然月即 1 号到月末，未来日期空柱），不截断到今天
    val from = state.rangeFrom ?: return
    val to = state.rangeTo ?: return
    if (from.isAfter(to)) return
    val days = ((to.toEpochDay() - from.toEpochDay()).toInt() + 1).coerceIn(2, 31)
    val values = (0 until days).map { state.heatValues[from.plusDays(it.toLong())] ?: 0f }
    val maxV = (values.maxOrNull() ?: 0f)
    val bestIdx = values.indices.maxByOrNull { values[it] } ?: 0

    // 自绘容器：底距比统一卡片更紧（最多日文字贴近下缘）
    Surface(
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
                            values.forEachIndexed { idx, v ->
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
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                        modeValueText(state.workSystem, maxV),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    Text(
                        modeValueText(state.workSystem, maxV / 2f),
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
                        modeValueText(state.workSystem, values[bestIdx]),
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

/** 汇总卡（模式化 hero，与明细页同视觉体系）：
 * 工地=工数为主 + 应得/已借支/待结三色钱账；非工地=收入为主 + 档位分布（标准）/周期口径（综合）。 */
@Composable
private fun SummaryCard(state: StatsUiState) {
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(Spacing.l)) {
            if (state.workSystem == WorkSystem.SITE) {
                val out = state.siteSummary ?: return@Column
                Text(
                    stringResource(R.string.site_stat_works),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                val totalWorks = out.totalWorksMilli / 1000f
                val worksNum = if (totalWorks % 1f == 0f) totalWorks.toInt().toString()
                else String.format(java.util.Locale.US, "%.1f", totalWorks)
                Text(
                    stringResource(R.string.stats_works_value, worksNum),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    listOf(
                        stringResource(R.string.stats_ot_duration_line, TimeUtils.prettyDuration(out.otMinutes)),
                        stringResource(R.string.stats_site_days_value, out.daysWork),
                        stringResource(R.string.site_stat_piece_pay) + " " + Money.yuanWithSign(out.piecePayCents),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_work_pay),
                        Money.yuanWithSign(out.receivableCents),
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_advance),
                        Money.yuanWithSign(out.advanceTotalCents),
                        SiteMoneyColors.ReceivedGreen,
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.site_stat_pending),
                        Money.yuanWithSign(out.pendingCents),
                        if (out.pendingCents < 0) MaterialTheme.colorScheme.error else SiteMoneyColors.PendingOrange,
                        modifier = Modifier.weight(1f),
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    if (state.showMoney) Money.yuanWithSign(out.incomeCents) else "-",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    listOf(
                        stringResource(R.string.stats_ot_duration_line, TimeUtils.prettyDuration(out.otMinutes)),
                        if (out.leaveDeductCents > 0)
                            stringResource(R.string.stats_leave_deduct_line, Money.yuanWithSign(out.leaveDeductCents))
                        else "",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                // 请假：时长 + 扣款（红）
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.stats_leave),
                        TimeUtils.prettyDuration(out.leaveMinutes),
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.feature.detail.MiniStat(
                        stringResource(R.string.stats_leave_deduct),
                        if (state.showMoney) Money.yuanWithSign(out.leaveDeductCents) else "-",
                        MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 饼图卡：非工地=班次分布/请假类型切换；工地=项目工数 */
@Composable
private fun PieCard(state: StatsUiState, pieMode: PieMode, onPieMode: (PieMode) -> Unit) {
    val isSite = state.workSystem == WorkSystem.SITE
    SectionCard {
        Column {
            if (!isSite) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PieMode.entries.forEach { mode ->
                        FilterChip(
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
                            .padding(vertical = 2.dp),
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

