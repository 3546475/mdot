package com.mdot.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.navigation.bottomBarContentPaddingValues
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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** 明细页 UiState：本周期（考勤周期）内的收入构成明细，与首页收入卡同口径 */
data class DetailUiState(
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 周期区间标签（如「9月1日 – 9月30日」） */
    val rangeLabel: String = "",
    /** 非工地：加班/请假逐条明细 + 引擎汇总（档位分布/合计） */
    val breakdowns: List<PayrollCalculator.RecordBreakdown> = emptyList(),
    val output: PayrollCalculator.Output? = null,
    /** 工地：当前项目流水（出工/休息/包工/借支/部分结算，日期倒序）+ 三色汇总 */
    val siteDetails: List<SiteDetailRow> = emptyList(),
    val siteSummary: SitePayCalculator.Output? = null,
    val sitePartialCents: Long = 0,
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

    val uiState = combine(anchorFlow, salaryFlow) { anchor, (salary, workdays) ->
        Triple(anchor, salary, workdays)
    }.flatMapLatest { (anchor, salary, workdays) ->
        val range = CycleCalculator.periodContaining(today, anchor)
        val rangeLabel = "${TimeUtils.mdCn(range.from)} – ${TimeUtils.mdCn(range.to)}"
        if (salary.workSystem == WorkSystem.SITE) {
            // 工地：当前项目本周期流水（出工/包工/借支/部分结算合并倒序，同统计页明细口径）
            val pid = siteRepo.currentProjectId()
            combine(
                siteRepo.observeAttendance(pid, range.from, range.to),
                siteRepo.observePieceWorks(pid, range.from, range.to),
                siteRepo.observeAdvances(pid, range.from, range.to),
                siteRepo.observePartialSettlements(pid, range.from, range.to),
            ) { atts, pieces, advs, partials ->
                fun worksMilliOf(minutes: Int, base: Int): Long = minutes * 1000L / base.coerceAtLeast(1)
                val details = buildList {
                    atts.forEach { a ->
                        add(
                            SiteDetailRow(
                                date = LocalDate.parse(a.date),
                                kind = SiteDetailKind.WORK,
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
                    partials.forEach { p ->
                        add(
                            SiteDetailRow(
                                date = p.periodStart,
                                kind = SiteDetailKind.PARTIAL,
                                amountCents = p.netCents,
                            )
                        )
                    }
                }.sortedByDescending { it.date }
                val summary = SitePayCalculator.summarize(
                    SitePayCalculator.Input(
                        attendance = atts, pieceWorks = pieces, advances = advs,
                    )
                )
                val partial = partials.sumOf { it.netCents }
                val summaryOut = if (partial > 0) summary.copy(pendingCents = summary.pendingCents - partial) else summary
                DetailUiState(
                    workSystem = WorkSystem.SITE,
                    rangeLabel = rangeLabel,
                    siteDetails = details,
                    siteSummary = summaryOut(summary, partial),
                    sitePartialCents = partial,
                )
            }
        } else {
            // 非工地：本周期加班/请假逐条金额明细（同统计页/工资单口径）
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
 * 明细页（二级页）：所有模式首页收入卡点击进入——本考勤周期内的收入构成逐条明细。
 * 模式化美化：顶部周期标签 + 模式化 hero 汇总卡（标准=三档时薪分布/小时工=纯工时/综合=周期口径/
 * 工地=应得·借支·部分结算·待结三色）+ 按日期分组的行列表（行内档位/类型徽章与图标瓦片）。
 */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.detail_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.s))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(bottom = Spacing.s)) {
                    Text(
                        stringResource(R.string.detail_cycle_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.rangeLabel.isNotEmpty()) {
                        Text(
                            state.rangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                DetailSummary(state)
                Spacer(Modifier.height(Spacing.m))
            }
            when {
                state.workSystem == WorkSystem.SITE -> {
                    // 按日期分组：日期小节头 + 行
                    val byDate = state.siteDetails.groupBy { it.date }
                    if (byDate.isEmpty()) item { EmptyHint() }
                    byDate.forEach { (date, rows) ->
                        item(key = "h_${date}") { DateHeader(date) }
                        items(rows) { row -> SiteDetailRowItem(row) }
                    }
                }
                else -> {
                    val byDate = state.breakdowns.groupBy { it.record.date }
                    if (byDate.isEmpty() && state.output != null) item { EmptyHint() }
                    byDate.forEach { (date, rows) ->
                        item(key = "h_${date}") { DateHeader(date) }
                        items(rows) { bd ->
                            NormalDetailRow(
                                state.workSystem,
                                bd,
                                onOpen = { vm.recordSheet.open(bd.record.date, bd.record.type) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(bottomBarContentPaddingValues().calculateBottomPadding())) }
        }
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
                    Text(
                        Money.yuanWithSign(out.receivableCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        MiniStat(
                            stringResource(R.string.site_stat_advance),
                            Money.yuanWithSign(out.advanceTotalCents),
                            SiteMoneyColors.ReceivedGreen,
                            modifier = Modifier.weight(1f),
                        )
                        MiniStat(
                            stringResource(R.string.site_settlement_partial_row),
                            Money.yuanWithSign(state.sitePartialCents),
                            SiteMoneyColors.ReceivedGreen,
                            modifier = Modifier.weight(1f),
                        )
                        MiniStat(
                            stringResource(R.string.site_stat_pending),
                            Money.yuanWithSign(out.pendingCents),
                            if (out.pendingCents < 0) MaterialTheme.colorScheme.error else SiteMoneyColors.PendingOrange,
                            modifier = Modifier.weight(1f),
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
                    Text(
                        Money.yuanWithSign(out.incomeCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
internal fun MiniStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(tint, RoundedCornerShape(Radius.pill)),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                tier.displayName + " " + Money.yuanText(cents),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
private fun DateHeader(date: LocalDate) {
    Text(
        "${TimeUtils.mdCn(date)} · ${TimeUtils.weekdayCn(date)}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.xs),
    )
}

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

/** 明细行（普通制度）：加班/请假记录，点击打开记录弹层；档位/请假类型徽章着色 */
@Composable
private fun NormalDetailRow(
    workSystem: WorkSystem,
    bd: PayrollCalculator.RecordBreakdown,
    onOpen: () -> Unit,
) {
    val r = bd.record
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${TimeUtils.md(r.date)} ${TimeUtils.weekdayCn(r.date)}" +
                    (r.shiftName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.type == com.mdot.app.domain.model.RecordType.OT) {
                    Text(
                        stringResource(
                            R.string.stats_detail_ot_line,
                            stringResource(R.string.stats_ot),
                            TimeUtils.prettyDuration(r.durationMinutes),
                            bd.tier?.displayName ?: "",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    bd.tier?.let { TierBadge(it) }
                } else {
                    Text(
                        stringResource(R.string.stats_detail_leave, TimeUtils.prettyDuration(r.durationMinutes), r.leaveType?.displayName ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (r.type == com.mdot.app.domain.model.RecordType.OT && r.toCompMinutes > 0) {
                Text(
                    stringResource(R.string.stats_detail_ot_comp, TimeUtils.prettyDuration(r.toCompMinutes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (r.type == com.mdot.app.domain.model.RecordType.OT) "+" + Money.yuanText(bd.amountCents)
                else "−" + Money.yuanText(bd.amountCents),
                style = MaterialTheme.typography.titleSmall,
                color = if (r.type == com.mdot.app.domain.model.RecordType.OT) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
            r.note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 档位徽章：平时/周末/法定着色小胶囊 */
@Composable
private fun TierBadge(tier: RateTier) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(tierTint(tier).copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            tier.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = tierTint(tier),
        )
    }
}

/** 明细行（工地）：图标瓦片 + 出工/休息/包工/借支/部分结算 */
@Composable
private fun SiteDetailRowItem(row: SiteDetailRow) {
    val line = when (row.kind) {
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(kindIcon(row.kind)), null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                "${TimeUtils.md(row.date)} ${TimeUtils.weekdayCn(row.date)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                style = MaterialTheme.typography.titleSmall,
                color = if (row.kind == SiteDetailKind.ADVANCE) SiteMoneyColors.ReceivedGreen
                else if (row.kind == SiteDetailKind.PARTIAL) SiteMoneyColors.ReceivedGreen
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
