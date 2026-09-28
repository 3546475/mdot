package com.mdot.app.feature.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mdot.app.core.repository.DataRevision
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import com.mdot.app.feature.stats.SiteDetailKind
import com.mdot.app.feature.stats.SiteDetailRow
import com.mdot.app.feature.stats.buildSiteDetailRows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** 工地明细区间口径（21 文档 B1）：UNSETTLED/PROJECT_SPAN 走 domain 推导（SiteRanges.Kind），CUSTOM=用户选起止 */
enum class SiteDetailRangeMode { UNSETTLED, PROJECT_SPAN, CUSTOM }

/** VM 数据流合并参数（combine 五流解包） */
private data class DetailParams(
    val anchor: Int,
    val salary: com.mdot.app.domain.model.SalaryConfig,
    val workdays: Set<java.time.DayOfWeek>,
    val siteMode: SiteDetailRangeMode,
    val customFrom: LocalDate?,
    val customTo: LocalDate?,
)

/** 明细页 UiState：非工地=本周期（考勤周期）收入构成明细；工地=项目区间流水（21 文档 B1） */
data class DetailUiState(
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 周期区间标签（如「9月1日 – 9月30日」） */
    val rangeLabel: String = "",
    /** 非工地：加班/请假逐条明细 + 引擎汇总（档位分布/合计） */
    val breakdowns: List<PayrollCalculator.RecordBreakdown> = emptyList(),
    val output: PayrollCalculator.Output? = null,
    /** 工地：当前项目区间流水（出工/休息/包工/借支/部分结算，日期倒序）+ 三色汇总 */
    val siteDetails: List<SiteDetailRow> = emptyList(),
    val siteSummary: SitePayCalculator.Output? = null,
    val sitePartialCents: Long = 0,
    /** 工地区间口径（默认本期待结） */
    val siteRangeMode: SiteDetailRangeMode = SiteDetailRangeMode.UNSETTLED,
    val siteCustomFrom: LocalDate? = null,
    val siteCustomTo: LocalDate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    recordRepo: RecordRepository,
    settings: SettingsDataSource,
    dataRevision: DataRevision,
    private val holidayRepo: HolidayRepository,
    private val siteRepo: SiteRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 周期锚点变化（含云端恢复 bump）时重算 */
    private val anchorFlow = combine(settings.cycleAnchorDayFlow, dataRevision.version) { a, _ -> a }
    private val salaryFlow = combine(settings.salaryFlow, settings.workdaysFlow) { s, w -> s to w }

    /** 工地区间口径 + 自定义起止（21 文档 B1） */
    private val siteMode = MutableStateFlow(SiteDetailRangeMode.UNSETTLED)
    private val siteCustomFrom = MutableStateFlow<LocalDate?>(null)
    private val siteCustomTo = MutableStateFlow<LocalDate?>(null)

    fun onSiteRangeMode(mode: SiteDetailRangeMode) {
        siteMode.value = mode
        if (mode != SiteDetailRangeMode.CUSTOM) {
            siteCustomFrom.value = null
            siteCustomTo.value = null
        }
    }

    fun onSiteCustomFrom(d: LocalDate) {
        siteCustomFrom.value = d
        if ((siteCustomTo.value ?: d).isBefore(d)) siteCustomTo.value = d
    }

    fun onSiteCustomTo(d: LocalDate) {
        if (d.isAfter(today)) return
        siteCustomTo.value = d
        if ((siteCustomFrom.value ?: d).isAfter(d)) siteCustomFrom.value = d
    }

    val uiState = combine(anchorFlow, salaryFlow, siteMode, siteCustomFrom, siteCustomTo) { anchor, sw, mode, cf, ct ->
        DetailParams(anchor, sw.first, sw.second, mode, cf, ct)
    }.flatMapLatest { (anchor, salary, workdays, mode, cf, ct) ->
        if (salary.workSystem == WorkSystem.SITE) {
            // 工地（21 文档 B1）：项目区间流水（出工/包工/借支/部分结算合并倒序），
            // 口径 = 本期待结/项目全周期/自定义；结算变化 → observeSettlements 触发区间滚动
            val pid = siteRepo.currentProjectId()
            siteRepo.observeSettlements(pid).flatMapLatest {
                val (from, to) = when (mode) {
                    SiteDetailRangeMode.UNSETTLED -> siteRepo.unsettledRange(pid)
                    SiteDetailRangeMode.PROJECT_SPAN -> siteRepo.projectSpan(pid)
                    SiteDetailRangeMode.CUSTOM -> {
                        val span = siteRepo.projectSpan(pid)
                        val f = cf ?: span.first
                        val t = maxOf(ct ?: today, f)
                        f to t
                    }
                }
                val rangeLabel = "${TimeUtils.mdCn(from)} – ${TimeUtils.mdCn(to)}"
                combine(
                    siteRepo.observeAttendance(pid, from, to),
                    siteRepo.observePieceWorks(pid, from, to),
                    siteRepo.observeAdvances(pid, from, to),
                    siteRepo.observePartialSettlements(pid, from, to),
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
                        rangeLabel = rangeLabel,
                        siteDetails = details,
                        siteSummary = summaryOut(summary, partial),
                        sitePartialCents = partial,
                        siteRangeMode = mode,
                        siteCustomFrom = cf,
                        siteCustomTo = ct,
                    )
                }
            }
        } else {
            // 非工地：本周期（考勤周期）加班/请假逐条金额明细（同统计页/工资单口径）
            val range = CycleCalculator.periodContaining(today, anchor)
            val rangeLabel = "${TimeUtils.mdCn(range.from)} – ${TimeUtils.mdCn(range.to)}"
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
                    rangeLabel = rangeLabel,
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

/** 明细内容主体（统计页「明细」页签与本页共用）：区间胶囊 + 模式化汇总卡 + 逐条明细列表 */
@Composable
fun DetailPane(
    showBottomBar: Boolean,
    vm: DetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<String?>(null) } // "from" | "to"（工地自定义区间）

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.page),
        contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = showBottomBar),
    ) {
        item {
            // 工地：区间口径行（21 文档 B1/Q3）——本期待结（默认）/ 项目全周期 / 自定义（与统计页工地维度行共用 SiteRangeChips）
            if (state.workSystem == WorkSystem.SITE) {
                SiteRangeChips(
                    selected = state.siteRangeMode,
                    onSelect = vm::onSiteRangeMode,
                )
                if (state.siteRangeMode == SiteDetailRangeMode.CUSTOM) {
                    Spacer(Modifier.height(Spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { picking = "from" }) {
                            Text(
                                stringResource(
                                    R.string.stats_custom_from,
                                    state.siteCustomFrom?.let(TimeUtils::mdCn)
                                        ?: stringResource(R.string.stats_custom_from_default),
                                )
                            )
                        }
                        OutlinedButton(onClick = { picking = "to" }) {
                            Text(
                                stringResource(
                                    R.string.stats_custom_to,
                                    state.siteCustomTo?.let(TimeUtils::mdCn)
                                        ?: stringResource(R.string.stats_custom_to_default),
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.s))
            }
            if (state.rangeLabel.isNotEmpty()) {
                // 区间胶囊（与首页数据区日期同款样式）
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_calendar_month), null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSpec.inline),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        state.rangeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.s))
            }
        }
        item {
            DetailSummary(state)
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

    // 工地自定义区间起止选择（21 文档 B1）
    picking?.let { which ->
        DayPickDialog(
            title = stringResource(
                if (which == "from") R.string.ds_pick_start else R.string.ds_pick_end,
            ),
            initial = if (which == "from") state.siteCustomFrom ?: LocalDate.now().withDayOfMonth(1)
            else state.siteCustomTo ?: LocalDate.now(),
            onPick = { d ->
                if (which == "from") vm.onSiteCustomFrom(d) else vm.onSiteCustomTo(d)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

// ---- 模式化汇总卡 ----

@Composable
private fun DetailSummary(state: DetailUiState) {
    SectionCard(onClick = {}, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth()) {
            when {
                state.workSystem == WorkSystem.SITE -> {
                    val out = state.siteSummary ?: return@Column
                    Text(
                        stringResource(R.string.site_settlement_receivable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    AnimatedMoneyText(
                        out.receivableCents,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    val incomeLabel = when (state.workSystem) {
                        WorkSystem.HOURLY -> stringResource(R.string.detail_income_work)
                        else -> stringResource(R.string.detail_income_cycle)
                    }
                    Text(
                        incomeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    AnimatedMoneyText(
                        out.incomeCents,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        label = "detailIncome",
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    if (state.workSystem == WorkSystem.HOURLY) {
                        // 小时工：纯时薪，只看工时
                        Text(
                            stringResource(R.string.detail_worked_hours, TimeUtils.hoursDecimal(out.otMinutes)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    } else {
                        Text(
                            stringResource(R.string.detail_ot_leave_line, TimeUtils.hoursDecimal(out.otMinutes)) +
                                if (out.leaveDeductCents > 0) " · " + stringResource(R.string.detail_leave_deduct, Money.yuanWithSign(out.leaveDeductCents)) else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
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
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
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
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(tint, RoundedCornerShape(Radius.pill)),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                tier.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            AnimatedMoneyText(
                cents,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                .clip(RoundedCornerShape(Radius.pill))
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
            // 时长在上，金额在下
            Text(
                TimeUtils.hoursDecimal(r.durationMinutes) + stringResource(R.string.detail_worked_hours_unit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (isOt) "+" + Money.yuanText(bd.amountCents) else "−" + Money.yuanText(bd.amountCents),
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
            .clip(RoundedCornerShape(Radius.pill))
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
