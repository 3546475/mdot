package com.mdot.app.feature.stats

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.KeyValue
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.WeekBarCard
import com.mdot.app.core.designsystem.component.chartHintText
import com.mdot.app.core.designsystem.component.buildWeekBars
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.modeValueText
import com.mdot.app.core.designsystem.component.DayPickDialog
import com.mdot.app.core.designsystem.component.MonthStepper
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.primaryTabEdgeRelay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.StatsRangeKind
import com.mdot.app.domain.StatsRanges
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.detail.DetailPane
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.jiabanFilterChipColors

enum class StatsDimension(val labelRes: Int) {
    CYCLE(R.string.stats_dim_cycle), MONTH(R.string.stats_dim_month), YEAR(R.string.stats_dim_year), CUSTOM(R.string.stats_dim_custom),
    /** 工地记工维度（21 文档 B2）：工地以项目为核心，无固定周期——本期待结 / 项目全周期 */
    SITE_PENDING(R.string.stats_dim_site_pending), SITE_SPAN(R.string.stats_dim_site_span),
}

/**
 * 界面维度 → domain 的区间粒度。工地两个维度没有对应粒度（它走 `SiteRanges` 项目口径），
 * 这里回落到 CYCLE 只是取出一个**不会被使用**的值——工地分支在 `flatMapLatest` 里先分叉，
 * 从不读这个 range。别把 SITE_* 当成真的走考勤周期。
 */
val StatsDimension.rangeKind: StatsRangeKind
    get() = when (this) {
        StatsDimension.CYCLE -> StatsRangeKind.CYCLE
        StatsDimension.MONTH -> StatsRangeKind.MONTH
        StatsDimension.YEAR -> StatsRangeKind.YEAR
        StatsDimension.CUSTOM -> StatsRangeKind.CUSTOM
        StatsDimension.SITE_PENDING, StatsDimension.SITE_SPAN -> StatsRangeKind.CYCLE
    }

/**
 * 区间胶囊**下方 Spacer** 的取值——统计/记月/明细三页共用同一个常量。
 *
 * 为什么不用 Spacing 令牌：上方的留白不是页面自己定的，而是宿主把页签条
 * `SegmentBar`（42dp）塞进 `CenterAlignedTopAppBar`（固定 64dp）后多出来的余量（实测 64px），
 * 三页完全一致、也不由这三页控制；令牌表里没有对应值，硬凑会漂。
 * 所以显式记常量并三页共用：改一次三页同步。
 *
 * 取值依据：用户要求把胶囊上下留白收到上一版（约 86px）的 1/3，故取 10dp(≈28px)。
 * 页面顶部已不再加 Spacer（页签条自带余量），上方因此固定在 64px。
 * ⚠️ 比"距"时注意下方**总距** = 本 Spacer + 卡片自身上内边距(约 12dp)，
 * 二者不同源，别把卡片内边距算进胶囊留白。
 */
val StatsRangeSpacing: androidx.compose.ui.unit.Dp
    get() = 10.dp

/** 非工地：自定义起止快照（两流合成一路——`combine` 的定长重载上限是 5 流） */
private data class StatsCustomRange(val from: LocalDate?, val to: LocalDate?)

/** 非工地：区间推导的输入快照（combine 五流解包，避免再嵌一层 Triple） */
private data class StatsRangeParams(
    val dim: StatsDimension,
    val range: CycleCalculator.Period?,
    val salaryWorkdays: Pair<com.mdot.app.domain.model.SalaryConfig, Set<java.time.DayOfWeek>>,
    val offset: Int,
)

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
    /** 源记录 id（出勤/包工/借支/结算单各表内自增）——与 kind 组合成列表稳定 key。
     *  同一天同用途可有多笔（如两笔借支），故不能用 date+用途做 key（重复 → LazyColumn 崩溃）。 */
    val id: Long = 0,
    /** 出勤工数×1000 */
    val worksMilli: Long = 0,
    val otMinutes: Int = 0,
    val amountCents: Long = 0,
    val itemName: String = "",
    val purpose: AdvancePurpose? = null,
) {
    /** LazyColumn 稳定 key：跨表自增 id 会撞，必须带 kind 段前缀（同 11 文档 005 教训） */
    val stableKey: String get() = "${kind}_$id"
}

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
    /** 非工地：区间选择弹窗所需的现状快照（当前粒度 + 当前区间）；工地为 null */
    val picker: StatsRangePicker? = null,
    /** 相对当前区间的**步进次数**（0=当前区间，-1=前一条）；自定义时按区间天数整段平移 */
    val rangeOffset: Int = 0,
    /** 「往前还可以走几步」：0 ⇒ 前进箭头置灰（已贴到今天） */
    val maxForwardSteps: Int = 0,
    val showMoney: Boolean = false,
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 汇总（非工地，PayrollCalculator） */
    val output: PayrollCalculator.Output? = null,
    /** 汇总（工地，SitePayCalculator） */
    val siteSummary: SitePayCalculator.Output? = null,
    /** 柱状图每日值（**所选区间内**，工地=工数/其他=加班分钟；与热点图窗口解耦，21 文档 B3） */
    val barValues: Map<LocalDate, Float> = emptyMap(),
    /** 统一热点图（所有模式）：非工地=加班小时，工地=工数 */
    val heatValues: Map<LocalDate, Float> = emptyMap(),
    /** 热点图铺格终止日（固定今天，与所选区间无关） */
    val heatEnd: LocalDate = LocalDate.now(),
    /** 热点图铺格起始日（本月月初向前推五个月的月初，共六个月） */
    val heatStart: LocalDate? = null,
    /** 区间起止日（月柱状图铺柱用，不截断到今天） */
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
    // private val：onPickMonth 要在点击时**现取**锚点（cycleAnchorDayFlow.first()），不能只靠流订阅
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    dataRevision: com.mdot.app.core.repository.DataRevision,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dimension = MutableStateFlow(StatsDimension.CYCLE)
    private val customFrom = MutableStateFlow<LocalDate?>(null)
    private val customTo = MutableStateFlow<LocalDate?>(null)

    /** 统计页区间步进（0=当前区间）。换维度时清零，否则残留的 offset 会让新区间莫名偏移 */
    private val rangeOffset = MutableStateFlow(0)

    /** 自定义起止合一路（`combine` 定长重载上限 5 流，故与下面的循环步进合起来共 5 个输入） */
    private val customRange = combine(customFrom, customTo) { f, t -> StatsCustomRange(f, t) }

    /**
     * 上一条区间（‹）；自定义按区间天数整段平移。
     * 前进方向的上限由 [maxForwardSteps] 在 [stepForward] 拦住，故这里单调递减即安全。
     */
    fun stepBack() {
        rangeOffset.value -= 1
    }

    /**
     * 下一条区间（›）。上限由 [StatsRanges.maxForwardSteps] 定——
     * 已贴到今天时不再前进（全 App「不越今天」口径），故这里只做「能不能走一步」的判定。
     */
    fun stepForward() {
        if (rangeOffset.value >= 0) return          // 已在当前区间，不再前进
        val st = uiState.value
        if (st.maxForwardSteps <= 0) return         // 自定义整段平移的余量不足
        rangeOffset.value += 1
    }

    /** 回到当前区间（「回本期」）。注意自定义的下限是用户选的起止，不受此影响 */
    fun backToCurrentRange() {
        rangeOffset.value = 0
    }

    /**
     * 统计页区间弹窗：月份网格点选 → 换算成步进偏移。
     * 换算逻辑住 domain（[CycleCalculator.cycleOffsetToMonth] / `ChronoUnit.MONTHS`），VM 只做分发。
     * 考勤周期按**锚点月之差**反解（同明细页），故 anchor 26 时点「8 月」得到 8/26–9/25 那一期。
     */
    fun onPickMonth(month: java.time.YearMonth) {
        viewModelScope.launch {
            val anchor = settings.cycleAnchorDayFlow.first()
            val base = CycleCalculator.cycleStartMonth(today, anchor)
            rangeOffset.value = when (dimension.value) {
                StatsDimension.YEAR ->
                    (month.year - today.year).coerceAtMost(0)
                else -> CycleCalculator.cycleOffsetToMonth(base, month)
            }
        }
    }

    /** 统计页区间弹窗：年份网格点选 → 步进偏移（年粒度，不越今天） */
    fun onPickYear(year: Int) {
        rangeOffset.value = (year - today.year).coerceAtMost(0)
    }

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
        customRange,
        // 云端恢复导入后 DataRevision bump → 强制本页全量重算
        combine(settings.cycleAnchorDayFlow, dataRevision.version) { anchor, _ -> anchor },
        combine(settings.salaryFlow, settings.workdaysFlow) { s, w -> s to w },
        rangeOffset,
    ) { dim, custom, anchor, (salary, workdays), offset ->
        val base = StatsRanges.current(
            kind = dim.rangeKind,
            today = today,
            anchorDay = anchor,
            customFrom = custom.from,
            customTo = custom.to,
            fallback = CycleCalculator.periodContaining(today, anchor),
        )
        // 「‹ ›」步进：CYCLE=整周期、MONTH=整月、YEAR=整年、CUSTOM=按区间天数整段平移，
        // 全部走 domain 纯函数（VM/UI 不碰日期算术）。走不到（跨越今天）时退回 base——
        // 前进方向另有 maxForwardSteps 置灰兜底，故正常操作不会走到这个退化分支。
        // ⚠️ 工地维度（effDim 归一后是 SITE_*）在下面 isSite 分支重新按项目口径解析，不使用本 range。
        val stepped = if (offset == 0 || base == null) base else StatsRanges.step(
            kind = dim.rangeKind, from = base, anchorDay = anchor, steps = offset, today = today,
        )
        // 弹窗的「当前区间」用步进后的 range —— 这样月份/年份网格的高亮跟着用户实际在看的那一期走；
        // 反解基准（base）的计算在 onPickMonth 内部按 anchor 现算，不依赖这里。
        StatsRangeParams(dim, stepped ?: base, salary to workdays, offset)
    }.flatMapLatest { (dim, range, salaryWorkdays, offset) ->
        val (salary, workdays) = salaryWorkdays
        val isSite = salary.workSystem == WorkSystem.SITE
        // 维度归一（跨制度残留，21 文档 B2）：工地的固定周期维度回落「本期待结」，非工地的工地维度回落「考勤周期」
        val effDim = when {
            isSite && dim in setOf(StatsDimension.CYCLE, StatsDimension.MONTH, StatsDimension.YEAR) ->
                StatsDimension.SITE_PENDING
            !isSite && dim in setOf(StatsDimension.SITE_PENDING, StatsDimension.SITE_SPAN) ->
                StatsDimension.CYCLE
            else -> dim
        }
        if (!isSite && range == null) return@flatMapLatest flowOf(StatsUiState(dimension = effDim))
        // 热点图窗口：固定六个月（本月月初向前推五个月的月初 → 今天），与所选区间无关（21 文档 B3 修订）
        val heatStart = today.withDayOfMonth(1).minusMonths(5)
        val heatEnd = today
        // 工地记工（21 文档 B1/B2）：汇总/明细走项目口径区间，热点图由下方 merge 提供项目活动期窗口
        val base = if (isSite) {
            val pid = siteRepo.currentProjectId()
            // 结算/撤销结算 → observeSettlements 触发区间滚动
            siteRepo.observeSettlements(pid).flatMapLatest {
                val (rFrom, rTo) = when (effDim) {
                    StatsDimension.SITE_SPAN -> siteRepo.projectSpan(pid)
                    StatsDimension.CUSTOM -> {
                        val from = customFrom.value ?: today.withDayOfMonth(1)
                        val to = customTo.value ?: today
                        from to maxOf(to, from)
                    }
                    else -> siteRepo.unsettledRange(pid) // SITE_PENDING
                }
                combine(
                siteRepo.observeAttendance(pid, rFrom, rTo),
                siteRepo.observePieceWorks(pid, rFrom, rTo),
                siteRepo.observeAdvances(pid, rFrom, rTo),
                siteRepo.observeAttendanceAllProjects(rFrom, rTo),
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
                // 流水行构造三处共用（21 文档 B5 重构）
                val details = buildSiteDetailRows(atts, pieces, advs)
                StatsUiState(
                    dimension = effDim,
                    rangeLabel = "${TimeUtils.mdCn(rFrom)} – ${TimeUtils.mdCn(rTo)}",
                    workSystem = WorkSystem.SITE,
                    siteSummary = out,
                    barValues = worksByDay,
                    heatEnd = heatEnd,
                    rangeFrom = rFrom,
                    rangeTo = rTo,
                    weekBars = buildWeekBars(worksByDay),
                    pieSiteProjects = pieSiteProjects,
                    siteDetails = details,
                    customFrom = customFrom.value,
                    customTo = customTo.value,
                    // 工地：项目口径区间随结算滚动，没有「上一条/下一条」概念（‹ › 在 UI 侧隐藏）
                    rangeOffset = 0,
                    maxForwardSteps = 0,
                )
            }
            }
        } else {
            @Suppress("NAME_SHADOWING")
            val range = range ?: return@flatMapLatest flowOf(StatsUiState(dimension = effDim))
            combine(
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
                dimension = effDim,
                rangeLabel = range.toString(),
                output = out,
                showMoney = showMoney,
                workSystem = salary.workSystem,
                pieShift = pieShift,
                pieLeave = pieLeave,
                barValues = heat,
                heatValues = heat,
                heatEnd = heatEnd,
                rangeFrom = range.from,
                rangeTo = range.to,
                weekBars = buildWeekBars(heat),
                customFrom = customFrom.value,
                customTo = customTo.value,
                picker = StatsRangePicker(kind = effDim.rangeKind, current = range),
                rangeOffset = offset,
                maxForwardSteps = StatsRanges.maxForwardSteps(effDim.rangeKind, range, today),
            )
            }
        }
        // 热点图数据独立于所选区间：固定六个月窗口（21 文档 B3 修订：恢复月窗，工地/非工地同款）
        val heatFlow: kotlinx.coroutines.flow.Flow<Map<LocalDate, Float>> =
            if (isSite) {
                val pid = siteRepo.currentProjectId()
                siteRepo.observeAttendance(pid, heatStart, heatEnd).map { atts ->
                    atts.filter { it.workMinutes > 0 }
                        .groupBy { LocalDate.parse(it.date) }
                        .mapValues { (_, list) ->
                            list.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) } / 1000f
                        }
                }
            } else {
                recordRepo.observeRange(heatStart, heatEnd).map { recs ->
                    recs.filter { it.type == RecordType.OT }
                        .groupBy { it.date }
                        .mapValues { (_, list) -> list.sumOf { it.durationMinutes }.toFloat() }
                }
            }
        base.combine(heatFlow) { st, heat ->
            st.copy(heatValues = heat, heatEnd = heatEnd, heatStart = heatStart)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onDimension(d: StatsDimension) {
        dimension.value = d
        rangeOffset.value = 0   // 换维度必须清零，否则上一条区间的 offset 会让新区间莫名偏移
        if (d != StatsDimension.CUSTOM) {
            customFrom.value = null
            customTo.value = null
        }
    }

    /** 自定义起止改动后，步进偏移同样清零（用户重新框了区间，就不该还挂着旧的平移量） */
    fun onCustomFrom(d: LocalDate) {
        customFrom.value = d
        rangeOffset.value = 0
        if ((customTo.value ?: d).isBefore(d)) customTo.value = d
    }

    fun onCustomTo(d: LocalDate) {
        if (d.isAfter(LocalDate.now())) return
        rangeOffset.value = 0
        customTo.value = d
        if ((customFrom.value ?: d).isAfter(d)) customFrom.value = d
    }

}

/** 统计页（03 文档 §5.4 线框；v0.6 全制度统一布局：维度行→汇总→热点图→本周柱状→饼图；
 * 顶栏页签 统计/记月/明细——明细内容并入为末位页签，独立明细页保留给首页收入卡入口） */
@Composable
fun StatsScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    /** 初始页签（首页收入卡直达明细：工地=1、非工地=2；默认 0=统计） */
    initialTab: Int = 0,
    vm: StatsViewModel = hiltViewModel(),
    /** 记月「个人所得税」行 → 个税估算页（导航由 AppRoot 注入） */
    onOpenTax: () -> Unit = {},
    /** 图表长按「记那一天」（21 文档：按制度分流——工地→记工页带日期、其他→记录弹层），AppRoot 注入 */
    onChartDay: (LocalDate) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pieMode by vm.pieMode.collectAsStateWithLifecycle()
    val selectedBar by vm.selectedBar.collectAsStateWithLifecycle()
    // 记月=月度工资单（基本项目卡：基本工资/加班工资/调休 + 「同步本月考勤」回填），仅底薪制有意义——
    // 标准工时/综合工时显示；小时工（纯时薪，引擎 baseIncludedCents=0 无底薪、compBalanceMinutes=0 无调休，
    // 基本项目卡 2/3 行恒空值）与工地记工（无 PayrollCalculator 引擎值）隐藏——两制度为「统计/明细」两页签
    val monthTab = state.workSystem == WorkSystem.STANDARD || state.workSystem == WorkSystem.COMPREHENSIVE
    // 首帧 workSystem 尚未加载（默认 STANDARD=3 页签），按初始页签夹取防越界
    val pagerState = rememberPagerState(initialPage = initialTab.coerceAtMost(2), pageCount = { if (monthTab) 3 else 2 })

    // 摘要条/空态点「明细」时切页签用（pager 由本页持有）
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            // 一级 Tab 形态下的「边缘接力」：内层 pager 滑到第一/最后一页还在拖时，
            // 把余量交给整页横滑（LocalPrimaryTabSwipe）切相邻一级 Tab
            .primaryTabEdgeRelay(pagerState),
    ) {
        if (canBack) {
            // 二级页形态（从首页收入卡/工资页等入栈）：本页自带顶栏 + 返回
            JiabanTopBar(
                title = null,
                titleContent = { StatsTabBar(pagerState, monthTab) },
                showBack = true,
                onBack = onBack,
            )
        } else {
            // 一级 Tab 形态：AppRoot 的固定顶栏（工时制度 + 设置齿轮）压在本页之上，
            // 故页签条下移一行（避让状态栏 + 固定顶栏高度），不再被固定顶栏遮住（用户所选方案）
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Spacer(Modifier.height(statusBar + TopBarHeight))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                StatsTabBar(pagerState, monthTab)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 不预组合相邻页（记月/明细体量都不小）：进入统计页只组合当前页 → 减少切页卡顿；
            // 代价是点页签切换时目标页在动画中才组合（体感好于「一进场就卡」）
            beyondViewportPageCount = 0,
        ) { page ->
            when (page) {
                0 -> StatsContent(
                    vm = vm,
                    state = state,
                    pieMode = pieMode,
                    selectedBar = selectedBar,
                    showBottomBar = !canBack,
                    onChartDay = onChartDay,
                )
                1 -> if (monthTab) {
                    // 摘要条/空态点「明细」：切到末位页签（pager 由本页持有）
                    PayMonthContent(
                        onOpenDetail = { scope.launch { pagerState.animateScrollToPage(2) } },
                        onOpenTax = onOpenTax,
                        showBottomBar = !canBack,
                    )
                } else {
                    DetailPane(showBottomBar = !canBack)
                }
                else -> DetailPane(showBottomBar = !canBack)
            }
        }
    }
}

/** 统计页第 0 页：原统计内容（维度行→汇总→热点图→柱状→饼图） */
@Composable
private fun StatsContent(
    vm: StatsViewModel,
    state: StatsUiState,
    pieMode: PieMode,
    selectedBar: String?,
    showBottomBar: Boolean,
    onChartDay: (LocalDate) -> Unit,
) {
    // 方案 C：区间胶囊 → 本弹窗（选维度 / 跳月跳年 / 框自定义）
    var showRangeDialog by remember { mutableStateOf(false) }
    var showSiteRangeDialog by remember { mutableStateOf(false) } // 工地：胶囊 → 口径/自定义起止
    LazyColumn(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = AdaptiveSpecs.contentMaxWidth)
            .padding(horizontal = Spacing.page),
        contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = showBottomBar),
    ) {
        // 维度行（21 文档 B2）：工地=与明细页共用的三药丸 SiteRangeChips（同功能同样式）；
        // 非工地=方案 C——不再常驻四 chip，改由「区间胶囊 → 弹窗选维度」承担
        val isSite = state.workSystem == WorkSystem.SITE
        item {
            // 顶部不加 Spacer：宿主页签条（SegmentBar 撑满 64dp 顶栏）下方已有天然留白，
            // 再叠一层只会把胶囊推得太低。下方留白用 StatsRangeSpacing 与上方对齐（见该常量注释）。

            if (isSite) {
                // 工地：**胶囊 = 唯一入口**，口径切换与自定义起止都收进弹窗
                // （与明细页工地分支同款，共用 RangePill + SiteRangeDialog）
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.mdot.app.feature.detail.RangePill(
                        label = state.rangeLabel,
                        onClick = { showSiteRangeDialog = true },
                    )
                }
            } else {
                // 方案 C：胶囊 = 唯一入口（点开弹窗选维度/跳月/框自定义）；
                // 工地不支持步进（区间随结算滚动），故只在非工地给 ‹ ›
                MonthStepper(
                    label = statsRangeLabel(state.dimension.rangeKind, state.picker?.current),
                    onPrev = vm::stepBack,
                    onNext = vm::stepForward,
                    // 胶囊**始终可点**：弹窗里既能换维度、也能改自定义起止。
                    // ⚠️ 别按维度把它置为不可点（曾让「自定义」维度下胶囊变成死路——
                    // 页面已无常驻 chip 行，用户就再也换不回周期/月/年了，只能切页签绕）
                    onOpenPicker = { showRangeDialog = true },
                    nextEnabled = state.rangeOffset < 0 && state.maxForwardSteps > 0,
                    onBackToCurrent = if (state.rangeOffset < 0) vm::backToCurrentRange else null,
                    backToCurrentLabel = statsBackToCurrentLabel(state.dimension.rangeKind),
                    prevContentDescription = statsStepDescription(forward = false, kind = state.dimension.rangeKind),
                    nextContentDescription = statsStepDescription(forward = true, kind = state.dimension.rangeKind),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 下方留白补到与上方（宿主页签条链路给的 126px）相等：三页统一用同一个值，
            // 改这里请同步改记月页/明细页。实测对齐目标 = 42dp。
            Spacer(Modifier.height(StatsRangeSpacing))
        }

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
                    onAction = { onChartDay(LocalDate.now()) },
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
                    onDayLongPress = { onChartDay(it) },
                    hintValueText = chartHintText(state.workSystem),
                )
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 柱状图（铺满所选区间：≤31 天每日柱，超长按周聚合，21 文档 B3） ----
            val rangeDays = state.rangeFrom?.let { f -> state.rangeTo?.let { t -> (t.toEpochDay() - f.toEpochDay()).toInt() + 1 } } ?: 0
            if (rangeDays >= 2) {
                item {
                    MonthBarCard(state, { onChartDay(it) }, chartHintText(state.workSystem))
                    Spacer(Modifier.height(Spacing.m))
                }
            }

            // ---- 热点图卡（固定六个月：本月向前推五个月，如九月 = 四~九月） ----
            item {
                SectionCard {
                    WorkHeatmap(
                        values = state.heatValues,
                        start = state.heatStart,
                        end = state.heatEnd,
                        onDayLongPress = { onChartDay(it) },
                        hintValueText = chartHintText(state.workSystem),
                    )
                }
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 饼图卡 ----
            item {
                PieCard(state, pieMode, vm::onPieMode)
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    // 方案 C 的区间选择弹窗（非工地）：维度 + 跳月/跳年 + 自定义起止都在它里面
    if (showRangeDialog) {
        StatsRangeDialog(
            dimension = state.dimension,
            picker = state.picker,
            customFrom = state.customFrom,
            customTo = state.customTo,
            onDimension = vm::onDimension,
            onPickMonth = vm::onPickMonth,
            onPickYear = vm::onPickYear,
            onCustomFrom = vm::onCustomFrom,
            onCustomTo = vm::onCustomTo,
            onDismiss = { showRangeDialog = false },
        )
    }

    // 工地：区间口径弹窗（口径 chips + 自定义起止；与明细页共用 SiteRangeDialog）
    if (showSiteRangeDialog) {
        com.mdot.app.feature.detail.SiteRangeDialog(
            mode = when (state.dimension) {
                StatsDimension.SITE_SPAN -> com.mdot.app.feature.detail.SiteDetailRangeMode.PROJECT_SPAN
                StatsDimension.CUSTOM -> com.mdot.app.feature.detail.SiteDetailRangeMode.CUSTOM
                else -> com.mdot.app.feature.detail.SiteDetailRangeMode.UNSETTLED
            },
            customFrom = state.customFrom,
            customTo = state.customTo,
            onMode = { mode ->
                vm.onDimension(
                    when (mode) {
                        com.mdot.app.feature.detail.SiteDetailRangeMode.UNSETTLED -> StatsDimension.SITE_PENDING
                        com.mdot.app.feature.detail.SiteDetailRangeMode.PROJECT_SPAN -> StatsDimension.SITE_SPAN
                        com.mdot.app.feature.detail.SiteDetailRangeMode.CUSTOM -> StatsDimension.CUSTOM
                    }
                )
            },
            onCustomFrom = vm::onCustomFrom,
            onCustomTo = vm::onCustomTo,
            onDismiss = { showSiteRangeDialog = false },
        )
    }
}

/**
 * 统计页区间胶囊的文案：与明细页不同，这里**要带维度前缀**（"周期 …"/"自然月 …"/"年 …"）。
 * 原先那行常驻 chip 承担的正是「现在是什么口径」的提示，收进弹窗后必须由文案补回来
 * —— 这是方案 C 的固有代价，别为了好看把前缀去掉。
 */
@Composable
private fun statsRangeLabel(kind: StatsRangeKind, period: CycleCalculator.Period?): String {
    if (period == null) return ""
    val prefix = stringResource(
        when (kind) {
            StatsRangeKind.CYCLE -> R.string.stats_dim_cycle
            StatsRangeKind.MONTH -> R.string.stats_dim_month
            StatsRangeKind.YEAR -> R.string.stats_dim_year
            StatsRangeKind.CUSTOM -> R.string.stats_dim_custom
        }
    )
    return if (kind == StatsRangeKind.YEAR) {
        "$prefix ${period.from.year}"
    } else {
        "$prefix ${TimeUtils.mdCn(period.from)} – ${TimeUtils.mdCn(period.to)}"
    }
}

/** 「回去」胶囊文案：按粒度说人话（周期叫「回本期」、年叫「回今年」、月才叫「回本月」） */
@Composable
private fun statsBackToCurrentLabel(kind: StatsRangeKind): String = when (kind) {
    StatsRangeKind.MONTH -> stringResource(R.string.month_stepper_back_to_current)
    StatsRangeKind.YEAR -> stringResource(R.string.ds_back_this_year)
    else -> stringResource(R.string.stats_range_back_to_current)
}

/** 步进箭头的无障碍描述：按维度说人话（读屏用户听到「上一期」才知道一步走多远） */
@Composable
private fun statsStepDescription(forward: Boolean, kind: StatsRangeKind): String = stringResource(
    when (kind) {
        StatsRangeKind.CYCLE ->
            if (forward) R.string.month_stepper_next else R.string.month_stepper_prev
        StatsRangeKind.MONTH ->
            if (forward) R.string.stats_step_next_month else R.string.stats_step_prev_month
        StatsRangeKind.YEAR ->
            if (forward) R.string.stats_step_next_year else R.string.stats_step_prev_year
        StatsRangeKind.CUSTOM ->
            if (forward) R.string.stats_step_next_span else R.string.stats_step_prev_span
    }
)

/** 顶栏分段控件（样式对齐工地记工记录页顶栏胶囊）：统计、明细常驻；记月仅非工地制度显示（工地模式后续配专属页） */
@Composable
private fun StatsTabBar(pagerState: PagerState, showMonth: Boolean) {
    val scope = rememberCoroutineScope()
    val labels = buildList {
        add(stringResource(R.string.stats_title))
        if (showMonth) add(stringResource(R.string.stats_tab_month))
        add(stringResource(R.string.stats_tab_detail))
    }
    SegmentBar(
        labels = labels,
        selected = pagerState.currentPage,
        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        // 三段总宽（3×84+8=260dp）须 ≤ 顶栏标题区（屏宽 − 两侧 48dp 占位）
        segWidth = 84.dp,
        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
    )
}

/** 模式取值文本：非工地=时长（小时），工地=工数（"N 工"） */


/** 月柱状图：统计页薄包装（共享组件在 core/designsystem/component/MonthBarCard.kt）。
 *  铺满所选区间（21 文档 B3）：≤31 天每日柱、超长按周聚合；取数用 barValues（区间内），与热点图窗口解耦 */
@Composable
private fun MonthBarCard(
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
private fun SummaryCard(state: StatsUiState) {
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
private fun PieCard(state: StatsUiState, pieMode: PieMode, onPieMode: (PieMode) -> Unit) {
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

