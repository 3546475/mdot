package com.mdot.app.feature.detail

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.DayPickDialog
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.navigation.contentPaddingValues
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.RecordRules
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.StatsRangeKind
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import com.mdot.app.feature.stats.SiteDetailKind
import com.mdot.app.feature.stats.SiteDetailRow
import com.mdot.app.feature.stats.StatsRangeSpacing
import com.mdot.app.feature.stats.buildSiteDetailRows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.jiabanFilterChipColors
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.heroChipSurface

/** 工地明细区间口径（21 文档 B1）：UNSETTLED/PROJECT_SPAN 走 domain 推导（SiteRanges.Kind），CUSTOM=用户选起止 */
enum class SiteDetailRangeMode { UNSETTLED, PROJECT_SPAN, CUSTOM }

/** 明细页 UiState：非工地=宿主所选区间的收入构成明细；工地=项目区间流水（21 文档 B1） */
data class DetailUiState(
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 非工地：加班/请假逐条明细 + 引擎汇总（档位分布/合计） */
    val breakdowns: List<PayrollCalculator.RecordBreakdown> = emptyList(),
    val output: PayrollCalculator.Output? = null,
    /** 工地：当前项目区间流水（出工/休息/包工/借支/部分结算，日期倒序）+ 三色汇总 */
    val siteDetails: List<SiteDetailRow> = emptyList(),
    val siteSummary: SitePayCalculator.Output? = null,
    val sitePartialCents: Long = 0,
)

/**
 * 明细页签的**宿主下发**（统计页 → 明细页签）。
 *
 * v0.7.9.0 起明细页签**跟随统计页的区间**（用户 2026-10-17 拍板：统计有「考勤周期/自然月」筛选，
 * 明细也该有 ⇒ 做成**共用同一份状态**，而不是在明细里再开一套选择器——两套状态终究会对不上）。
 * 故本页不再持有任何区间状态：头部（胶囊 + 步进 + 区间弹窗）由宿主给，取数区间也由宿主算好。
 */
class DetailPaneHost(
    /** 区间头（统计页的 `StatsRangeHeader`）——两页同一组件、同一份状态 */
    val header: @Composable () -> Unit,
    /** 本次取数区间（统计页已算好：考勤周期/自然月/年/自定义；工地 = 项目口径） */
    val range: CycleCalculator.Period?,
    /** 区间维度（只用于本页文案口径词：本期/本月/本年/区间） */
    val rangeKind: StatsRangeKind,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    private val siteRepo: SiteRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val salaryFlow = combine(settings.salaryFlow, settings.workdaysFlow) { s, w -> s to w }

    /**
     * 宿主（统计页）下发的取数区间（见 [DetailPaneHost]）。本页**没有**自己的区间状态，
     * 也就没有「另一套步进」可与统计页分叉。
     * null = 首帧宿主还没算出区间（统计 state 还是种子值）——此时出空态，**不要**回落成"本周期"：
     * 那会让两页在首帧显示不同区间，正是要消灭的现象。
     */
    private val hostRange = MutableStateFlow<CycleCalculator.Period?>(null)

    fun setHostRange(period: CycleCalculator.Period?) {
        hostRange.value = period
    }

    val uiState = combine(salaryFlow, hostRange) { (salary, workdays), range ->
        Triple(salary, workdays, range)
    }.flatMapLatest { (salary, workdays, range) ->
        if (range == null) return@flatMapLatest flowOf(DetailUiState(workSystem = salary.workSystem))
        if (salary.workSystem == WorkSystem.SITE) {
            // 工地（21 文档 B1）：项目区间流水（出工/包工/借支/部分结算合并倒序）。
            // 区间由宿主下发（「本期待结」随结算滚动那一层订阅在统计页），故这里不再自己
            // observeSettlements 推区间——一处推导，两页同一份。
            val pid = siteRepo.currentProjectId()
            combine(
                siteRepo.observeAttendance(pid, range.from, range.to),
                siteRepo.observePieceWorks(pid, range.from, range.to),
                siteRepo.observeAdvances(pid, range.from, range.to),
                siteRepo.observePartialSettlements(pid, range.from, range.to),
            ) { atts, pieces, advs, partials ->
                // 流水行构造三处共用（21 文档 B5 重构）
                val details = buildSiteDetailRows(atts, pieces, advs, partials)
                val summary = SitePayCalculator.summarize(
                    SitePayCalculator.Input(
                        attendance = atts, pieceWorks = pieces, advances = advs,
                    )
                )
                val partial = partials.sumOf { it.netCents }
                DetailUiState(
                    workSystem = WorkSystem.SITE,
                    siteDetails = details,
                    siteSummary = summaryOut(summary, partial),
                    sitePartialCents = partial,
                )
            }
        } else {
            // 非工地：宿主所选区间的加班/请假逐条 + 金额明细——同统计页/工资单口径。
            // 日期算术一律在 domain 与统计页（`StatsRanges`），本页不另算
            combine(
                recordRepo.observeRange(range.from, range.to),
                recordRepo.observeAdjustments(),
            ) { records, adjustments ->
                val tierOf: (LocalDate) -> RateTier = { date -> holidayRepo.tierFor(date, workdays) }
                val standardMinutes = if (salary.workSystem == WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(range.from, range.to)
                    CycleCalculator.standardMinutesFor(range.from, range.to, workdays, holidays, makeups)
                } else null
                val out = PayrollCalculator.summarize(
                    PayrollCalculator.Input(
                        salary,
                        records.map { it.toCalcLite() },
                        adjustments.map { it.toCalcLite() },
                        tierOf = tierOf,
                        standardMinutes = standardMinutes,
                    )
                )
                DetailUiState(
                    workSystem = salary.workSystem,
                    breakdowns = out.breakdowns,
                    output = out,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    private fun summaryOut(
        summary: SitePayCalculator.Output,
        partial: Long,
    ): SitePayCalculator.Output =
        if (partial > 0) summary.copy(pendingCents = summary.pendingCents - partial) else summary
}

/**
 * 工地区间口径三选项（21 文档 B1）：**明细页与统计页工地维度行共用本组件**——
 * 同功能必须同样式同实现，禁止两处各画一套。
 * 样式 = FilterChip 轻量筛选 chip（用户拍板 dec-8b817f2b6a12e91d：口径切换是次要信息，
 * ChoicePillRow 药丸太占视觉重心，与统计页维度 chips 统一成 chip 语言）。
 *
 * ⚠️ **2026-10-08（v0.7.8.3）起本组件不再直接铺在页面上**：工地两页的口径切换都收进了
 * [com.mdot.app.feature.detail.SiteRangeDialog]，页面只剩一颗 [RangePill]（它才是唯一入口）。
 * 本组件现在由该弹窗内部使用——**不要在页面层直接调用它**，否则又变回"胶囊 + chips"两套入口。
 */
@Composable
fun SiteRangeChips(
    selected: SiteDetailRangeMode,
    onSelect: (SiteDetailRangeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SiteDetailRangeMode.entries.forEach { mode ->
            FilterChip(
                colors = jiabanFilterChipColors(),
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                SiteDetailRangeMode.UNSETTLED -> R.string.stats_dim_site_pending
                                SiteDetailRangeMode.PROJECT_SPAN -> R.string.stats_dim_site_span
                                SiteDetailRangeMode.CUSTOM -> R.string.stats_dim_custom
                            }
                        )
                    )
                },
            )
        }
    }
}

/**
 * 明细内容主体（统计页「明细」页签）：区间头 + 模式化汇总卡 + 逐条明细列表。
 *
 * **区间由宿主要（见 [DetailPaneHost]）**：v0.7.9.0 起本页跟随统计页的区间（考勤周期/自然月/年/自定义；
 * 工地 = 项目口径），自己不再持有步进与选择器——头部直接用宿主给的 `StatsRangeHeader`
 * （同一 lambda、同一份状态），取数区间用宿主算好的 `range`。这样两页永远显示同一段区间。
 */
@Composable
fun DetailPane(
    showBottomBar: Boolean,
    host: DetailPaneHost,
    vm: DetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 宿主区间变化 → 下发给 VM（keyed on range：同区间重组不会重复 set）
    androidx.compose.runtime.LaunchedEffect(host.range) { vm.setHostRange(host.range) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.page),
        contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = showBottomBar),
    ) {
        item {
            // 区间头（统计页与明细页签**同一组件**）：顶部不加 Spacer，宿主页签条下方已有天然留白。
            host.header()
            // 三页共用的下方留白常量（见 StatsRangeSpacing 的注释：为何不是 Spacing 令牌）。
            // 本页 LazyColumn 的 verticalArrangement = spacedBy(Spacing.xs) 会再补 4dp，
            // 故这里减掉那 4dp，最终实际留白仍与统计页/记月页相等。
            Spacer(Modifier.height(StatsRangeSpacing - Spacing.xs))
        }
        item {
            DetailSummary(
                state = state,
                // 口径词随宿主维度变：小时工看「工时收入」，其余按 本期/本月/本年/区间
                incomeLabel = if (state.workSystem == WorkSystem.HOURLY) {
                    stringResource(R.string.detail_income_work)
                } else {
                    stringResource(
                        when (host.rangeKind) {
                            StatsRangeKind.CYCLE -> R.string.detail_income_cycle
                            StatsRangeKind.MONTH -> R.string.home_income_month
                            StatsRangeKind.YEAR -> R.string.detail_income_year
                            StatsRangeKind.CUSTOM -> R.string.detail_income_custom
                        }
                    )
                },
            )
            Spacer(Modifier.height(Spacing.m))
        }
        when {
            state.workSystem == WorkSystem.SITE -> {
                // 平铺单行明细（日期星期并入行内）
                if (state.siteDetails.isEmpty()) item { EmptyHint() }
                items(state.siteDetails, key = { it.stableKey }) { row -> SiteDetailRowItem(row) }
            }
            else -> {
                if (state.breakdowns.isEmpty() && state.output != null) item { EmptyHint() }
                items(state.breakdowns, key = { it.record.date.toString() + it.record.type.name }) { bd ->
                    NormalDetailRow(
                        state.workSystem,
                        bd,
                        onOpen = { vm.recordSheet.open(bd.record.date, bd.record.type) },
                    )
                }
            }
        }
    }
}

/**
 * 区间胶囊（**工地口径的唯一入口**）——与非工地
 * [com.mdot.app.core.designsystem.component.MonthStepper] 的胶囊**同款外观**：
 * 日历图标 + 文案 + ▾，点开弹窗换口径。工地在统计页与明细页共用本组件。
 *
 * ⚠️ 本组件**没有箭头**，高度只有胶囊本身（≈24dp）；而 MonthStepper 的行高由两侧
 * 48dp 最小触达的图标按钮撑起、胶囊在其中居中。
 * **2026-10-08 更新（commit 6085792）**：已改为把那侧的行高**锁到 `RangeCapsuleHeight`**，
 * 于是两支胶囊实际落在同一条 y 上（真机实测两制度下胶囊中心同为 y=296）——
 * 换句话说，「对齐」的责任放在**有行的那一侧**（MonthStepper），本组件**仍然不要**补 `minHeight`：
 * 它本身就是那颗胶囊（`clip`+`background`+`padding` 挂在同一个 Row 上），
 * 补 `minHeight` 会把胶囊自己撑成 48dp 的胖胶囊、并牵动整页节奏。
 * 用户看过两态后选择保留本形态，要动就动 `RangeCapsuleHeight` 一处。
 */
@Composable
fun RangePill(
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(engineShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_ms_calendar_month), null,
            tint = ink,
            modifier = Modifier.size(IconSpec.inline),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            // 与非工地胶囊同款：单行截断，两种口径的胶囊高度恒为 RangeCapsuleHeight
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                painterResource(R.drawable.ic_ms_expand_more),
                stringResource(R.string.month_stepper_pick_cd),
                tint = ink,
                modifier = Modifier.size(IconSpec.inline),
            )
        }
    }
}

// ---- 模式化汇总卡 ----

@Composable
private fun DetailSummary(state: DetailUiState, incomeLabel: String) {
    SectionCard(onClick = {}, containerColor = emphasisCardSurface()) {
        Column(Modifier.fillMaxWidth()) {
            when {
                state.workSystem == WorkSystem.SITE -> {
                    val out = state.siteSummary ?: return@Column
                    Text(
                        stringResource(R.string.site_settlement_receivable),
                        style = MaterialTheme.typography.labelSmall,
                        color = emphasisCardInk().copy(alpha = 0.8f),
                    )
                    AnimatedMoneyText(
                        out.receivableCents,
                        style = MaterialTheme.typography.headlineMedium,
                        color = emphasisCardInk(),
                        fontWeight = FontWeight.Bold,
                        label = "detailReceivable",
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        MiniStat(
                            stringResource(R.string.site_stat_advance),
                            Money.yuanWithSign(out.advanceTotalCents),
                            SiteMoneyColors.ReceivedGreen,
                            modifier = Modifier.weight(1f),
                            animatedCents = out.advanceTotalCents,
                        )
                        MiniStat(
                            stringResource(R.string.site_settlement_partial_row),
                            Money.yuanWithSign(state.sitePartialCents),
                            SiteMoneyColors.ReceivedGreen,
                            modifier = Modifier.weight(1f),
                            animatedCents = state.sitePartialCents,
                        )
                        MiniStat(
                            stringResource(R.string.site_stat_pending),
                            Money.yuanWithSign(out.pendingCents),
                            if (out.pendingCents < 0) MaterialTheme.colorScheme.error else SiteMoneyColors.PendingOrange,
                            modifier = Modifier.weight(1f),
                            animatedCents = out.pendingCents,
                        )
                    }
                }
                else -> {
                    val out = state.output ?: return@Column
                    Text(
                        incomeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = emphasisCardInk().copy(alpha = 0.8f),
                    )
                    AnimatedMoneyText(
                        out.incomeCents,
                        style = MaterialTheme.typography.headlineMedium,
                        color = emphasisCardInk(),
                        fontWeight = FontWeight.Bold,
                        label = "detailIncome",
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    if (state.workSystem == WorkSystem.HOURLY) {
                        // 小时工：纯时薪，只看工时
                        Text(
                            stringResource(R.string.detail_worked_hours, TimeUtils.hoursDecimal(out.otMinutes)),
                            style = MaterialTheme.typography.labelMedium,
                            color = emphasisCardInk().copy(alpha = 0.8f),
                        )
                    } else {
                        Text(
                            stringResource(R.string.detail_ot_leave_line, TimeUtils.hoursDecimal(out.otMinutes)) +
                                if (out.leaveDeductCents > 0) " · " + stringResource(R.string.detail_leave_deduct, Money.yuanWithSign(out.leaveDeductCents)) else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = emphasisCardInk().copy(alpha = 0.8f),
                        )
                    }
                    // 标准工时：三档时薪分布 chips（平时/周末/法定）
                    if (state.workSystem == WorkSystem.STANDARD) {
                        Spacer(Modifier.height(Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            TierChip(RateTier.WEEKDAY, out, Modifier.weight(1f))
                            TierChip(RateTier.WEEKEND, out, Modifier.weight(1f))
                            TierChip(RateTier.STATUTORY, out, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MiniStat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    animatedCents: Long? = null,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = emphasisCardInk().copy(alpha = 0.7f),
        )
        if (animatedCents != null) {
            AnimatedMoneyText(
                animatedCents,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                label = "miniStat",
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}

/** 标准工时三档收入小胶囊（0 档灰显） */
@Composable
internal fun TierChip(tier: RateTier, out: PayrollCalculator.Output, modifier: Modifier = Modifier) {
    val cents = out.otPayByTier[tier] ?: 0L
    val tint = tierTint(tier)
    Box(
        modifier
            .clip(engineShape(Radius.pill))
            .background(heroChipSurface())
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(tint, engineShape(Radius.pill)),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                tier.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = emphasisCardInk(),
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            AnimatedMoneyText(
                cents,
                style = MaterialTheme.typography.labelSmall,
                color = emphasisCardInk(),
                maxLines = 1,
                label = "tierChip",
            )
        }
    }
}

internal fun tierTint(tier: RateTier): androidx.compose.ui.graphics.Color = when (tier) {
    RateTier.WEEKDAY -> androidx.compose.ui.graphics.Color(0xFF3D6DF2)
    RateTier.WEEKEND -> androidx.compose.ui.graphics.Color(0xFFE8930C)
    RateTier.STATUTORY -> androidx.compose.ui.graphics.Color(0xFFD64545)
}

// ---- 日期小节头 ----


@Composable
private fun EmptyHint() {
    Text(
        stringResource(R.string.detail_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.xl),
    )
}

// ---- 明细行（原统计页共享组件移入，模式化美化） ----

/** 明细行（普通制度）：单行——「9/1 周二 · 班次 + 档位徽章」+ 右侧时长/金额上下布局 */
@Composable
private fun NormalDetailRow(
    workSystem: WorkSystem,
    bd: PayrollCalculator.RecordBreakdown,
    onOpen: () -> Unit,
) {
    val r = bd.record
    val isOt = r.type == com.mdot.app.domain.model.RecordType.OT
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            )
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    "${TimeUtils.md(r.date)} ${TimeUtils.weekdayCn(r.date)}" +
                        (r.shiftName?.let { " · $it" } ?: if (isOt) " · " + stringResource(R.string.stats_ot) else " · " + stringResource(R.string.stats_leave)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isOt) {
                    bd.tier?.let { TierBadge(it) }
                } else {
                    r.leaveType?.let { type ->
                        Box(
                            Modifier
                                .clip(engineShape(Radius.pill))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.s, vertical = 1.dp),
                        ) {
                            Text(
                                type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            // 附加信息仅在有内容时出现（转调休/备注），多数记录保持单行
            if (isOt && r.toCompMinutes > 0) {
                Text(
                    stringResource(R.string.stats_detail_ot_comp, TimeUtils.prettyDuration(r.toCompMinutes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            r.note?.let { note ->
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            // 时长在上，金额在下。「只记班次」（0 时长，v0.7.9.0）显示「未加班」而不是「0小时」
            Text(
                if (RecordRules.isShiftOnly(r.durationMinutes)) stringResource(R.string.detail_no_ot)
                else TimeUtils.hoursDecimal(r.durationMinutes) + stringResource(R.string.detail_worked_hours_unit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                // 0 时长没有金额可看（+¥0 只会误导），显示占位符
                when {
                    RecordRules.isShiftOnly(r.durationMinutes) -> "-"
                    isOt -> "+" + Money.yuanText(bd.amountCents)
                    else -> "−" + Money.yuanText(bd.amountCents)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 明细行（工地）：单行——「9/11 周五 · 类型/构成」+ 金额（明细页列表与记工页保存预览共用） */
@Composable
fun SiteDetailRowItem(row: SiteDetailRow) {
    val kindLine = when (row.kind) {
        SiteDetailKind.WORK -> {
            val num = if (row.worksMilli % 1000L == 0L) (row.worksMilli / 1000L).toString()
            else String.format(java.util.Locale.US, "%.1f", row.worksMilli / 1000f)
            stringResource(R.string.stats_site_detail_work, num) +
                if (row.otMinutes > 0) " · " + stringResource(R.string.stats_site_detail_ot_part, TimeUtils.prettyDuration(row.otMinutes)) else ""
        }
        SiteDetailKind.REST -> stringResource(R.string.stats_site_detail_rest)
        SiteDetailKind.PIECE -> {
            val name = row.itemName.ifBlank { stringResource(R.string.site_piece_unnamed) }
            stringResource(R.string.stats_site_detail_piece, name)
        }
        SiteDetailKind.ADVANCE -> stringResource(
            R.string.stats_site_detail_advance,
            stringResource(advancePurposeLabelRes(row.purpose)),
        )
        SiteDetailKind.PARTIAL -> stringResource(R.string.site_settlement_partial_row)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${TimeUtils.md(row.date)} ${TimeUtils.weekdayCn(row.date)} · $kindLine",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // 留证照片：行上显示张数，点开看大图（v0.7.9.0——照片以前只在编辑弹层里看得到）
        var viewingPhotos by remember { mutableStateOf(false) }
        if (row.photos.isNotEmpty()) {
            com.mdot.app.feature.site.PhotoCountBadge(
                count = row.photos.size,
                onClick = { viewingPhotos = true },
            )
            Spacer(Modifier.width(Spacing.s))
            if (viewingPhotos) {
                com.mdot.app.feature.site.PhotoGalleryDialog(
                    paths = row.photos,
                    onDismiss = { viewingPhotos = false },
                )
            }
        }
        val amountText = when {
            row.kind == SiteDetailKind.ADVANCE -> "−" + Money.yuanText(row.amountCents)
            row.kind == SiteDetailKind.PARTIAL -> "+" + Money.yuanText(row.amountCents)
            row.kind == SiteDetailKind.REST || row.amountCents == 0L -> ""
            else -> "+" + Money.yuanText(row.amountCents)
        }
        if (amountText.isNotEmpty()) {
            Text(
                amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (row.kind == SiteDetailKind.ADVANCE || row.kind == SiteDetailKind.PARTIAL) SiteMoneyColors.ReceivedGreen
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun kindIcon(kind: SiteDetailKind): Int = when (kind) {
    SiteDetailKind.WORK -> R.drawable.ic_ms_more_time
    SiteDetailKind.REST -> R.drawable.ic_ms_calendar_month
    SiteDetailKind.PIECE -> R.drawable.ic_ms_dashboard
    SiteDetailKind.ADVANCE -> R.drawable.ic_ms_paid
    SiteDetailKind.PARTIAL -> R.drawable.ic_ms_flip
}


/** 档位徽章：平时/周末/法定着色小胶囊 */
@Composable
private fun TierBadge(tier: RateTier) {
    Box(
        Modifier
            .clip(engineShape(Radius.pill))
            .background(tierTint(tier).copy(alpha = 0.12f))
            .padding(horizontal = Spacing.s, vertical = 1.dp),
    ) {
        Text(
            tier.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = tierTint(tier),
        )
    }
}

/** 借支用途 → 标签资源（展示点解析） */
private fun advancePurposeLabelRes(purpose: AdvancePurpose?): Int = when (purpose) {
    AdvancePurpose.WAGE -> R.string.site_purpose_wage
    AdvancePurpose.LIVING -> R.string.site_purpose_living
    AdvancePurpose.LODGING -> R.string.site_purpose_lodging
    AdvancePurpose.LODGING_ALLOW -> R.string.site_purpose_lodging_allow
    AdvancePurpose.MEALS -> R.string.site_purpose_meals
    AdvancePurpose.MEALS_ALLOW -> R.string.site_purpose_meals_allow
    AdvancePurpose.REWARD -> R.string.site_purpose_reward
    AdvancePurpose.MATERIALS -> R.string.site_purpose_materials
    AdvancePurpose.TRANSPORT -> R.string.site_purpose_transport
    AdvancePurpose.PROJECT_PAYMENT -> R.string.site_purpose_project
    AdvancePurpose.POCKET -> R.string.site_purpose_pocket
    AdvancePurpose.OTHER, null -> R.string.site_purpose_other
}
