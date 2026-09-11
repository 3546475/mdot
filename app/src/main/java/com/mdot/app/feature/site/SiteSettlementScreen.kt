package com.mdot.app.feature.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.core.repository.SiteSettlementPreview
import com.mdot.app.core.util.onFailure
import com.mdot.app.core.util.onSuccess
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteAdvance
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SiteSettlementUi(
    val loading: Boolean = true,
    val projectName: String = "",
    /** 本期待结摘要（未结算区间实时口径） */
    val summary: SitePayCalculator.Output? = null,
    /** 结算预览（确认前） */
    val preview: SiteSettlementPreview? = null,
    /** 历史结算单行 */
    val settlements: List<SettlementRow> = emptyList(),
    /** 未结算借支流水 */
    val advances: List<SiteAdvance> = emptyList(),
    /** 未结算包工/工量流水（Phase 2） */
    val pieces: List<com.mdot.app.domain.model.SitePieceWork> = emptyList(),
    val message: String? = null,
) {
    data class SettlementRow(
        val id: Long,
        val periodLabel: String,
        val workYuan: String,
        val advanceYuan: String,
        val netYuan: String,
        /** 部分结算单（本次结算金额） */
        val isPartial: Boolean = false,
        val netCents: Long = 0L,
    )
}

@HiltViewModel
class SiteSettlementViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(SiteSettlementUi())
    val state: StateFlow<SiteSettlementUi> = _state.asStateFlow()

    private suspend fun currentProjectId(): Long {
        val salary = settings.salaryFlow.first()
        return if (salary.workSystem == WorkSystem.SITE) siteRepo.currentProjectId() else salary.siteCurrentProjectId
    }

    init {
        viewModelScope.launch {
            // 项目/制度变化触发刷新；写操作（确认/撤销/删借支）后由调用方手动 refresh
            settings.salaryFlow.collect { salary ->
                if (salary.workSystem == WorkSystem.SITE) {
                    refresh()
                } else {
                    _state.update { it.copy(loading = false, summary = null, advances = emptyList(), settlements = emptyList()) }
                }
            }
        }
    }

    /** 一次性读取摘要/流水/历史并刷新状态（不做内层 collect，避免阻断外层） */
    suspend fun refresh() {
        val pid = siteRepo.currentProjectId()
        val name = siteRepo.getProject(pid)?.name.orEmpty()
        val summary = runCatching { siteRepo.summarizeUnsettled(pid) }.getOrNull()
        val (from, to) = siteRepo.unsettledRange(pid)
        val advances = siteRepo.unsettledAdvances(pid, from, to)
        val pieces = siteRepo.unsettledPieces(pid, from, to)
        val rows = siteRepo.settlementRows(pid)
        _state.update {
            it.copy(
                loading = false, projectName = name, summary = summary,
                advances = advances, pieces = pieces, settlements = rows,
            )
        }
    }

    /** 发起结算：构建预览（默认未结算全区间） */
    fun buildPreview() = viewModelScope.launch {
        val salary = settings.salaryFlow.first()
        if (salary.workSystem != WorkSystem.SITE) return@launch
        val pid = currentProjectId()
        val (from, to) = siteRepo.unsettledRange(pid)
        val preview = siteRepo.previewSettlement(pid, from, to)
        _state.update { it.copy(preview = preview) }
    }

    fun confirmSettlement() = viewModelScope.launch {
        val preview = _state.value.preview ?: return@launch
        siteRepo.confirmSettlement(preview, note = null).onSuccess {
            _state.update { it.copy(preview = null) }
            refresh()
        }
    }

    fun dismissPreview() = _state.update { it.copy(preview = null) }

    fun deleteAdvance(id: Long) = viewModelScope.launch {
        siteRepo.deleteAdvance(id).onFailure { e ->
            _state.update { it.copy(message = e.toSiteText()) }
        }.onSuccess { refresh() }
    }

    fun deletePiece(id: Long) = viewModelScope.launch {
        siteRepo.deletePieceWork(id).onFailure { e ->
            _state.update { it.copy(message = e.toSiteText()) }
        }.onSuccess { refresh() }
    }

    fun revertSettlement(id: Long) = viewModelScope.launch {
        siteRepo.revertSettlement(id).onFailure { e ->
            _state.update { it.copy(message = e.toSiteText()) }
        }.onSuccess { refresh() }
    }

    /** 部分结算（本次结算金额）：入账后刷新；金额错误经 message 提示 */
    fun settlePartial(amountYuan: String) = viewModelScope.launch {
        val cents = Money.parseYuanToCents(amountYuan) ?: 0
        val pid = currentProjectId()
        siteRepo.settlePartial(pid, cents).onFailure { e ->
            _state.update { it.copy(message = e.toSiteText()) }
        }.onSuccess { refresh() }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

private fun com.mdot.app.core.util.AppError.toSiteText(): String = when (this) {
    is com.mdot.app.core.util.AppError.InvalidMessage -> message
    else -> "操作失败，请重试"
}

private suspend fun SiteRepository.settlementRows(projectId: Long): List<SiteSettlementUi.SettlementRow> {
    val list = observeSettlements(projectId).first()
    return list.orEmpty().map {
        SiteSettlementUi.SettlementRow(
            id = it.id,
            periodLabel = "${it.periodStart.monthValue}/${it.periodStart.dayOfMonth}–${it.periodEnd.monthValue}/${it.periodEnd.dayOfMonth}",
            workYuan = Money.yuanWithSign(it.workPayCents + it.piecePayCents),
            advanceYuan = Money.yuanWithSign(it.advanceTotalCents),
            netYuan = Money.yuanWithSign(it.netCents),
            isPartial = it.isPartial,
            netCents = it.netCents,
        )
    }
}


/** 结算与借支页（12 文档 F-S6）：本期待结摘要 + 发起结算 + 借支流水 + 历史结算单 */
@Composable
fun SiteSettlementScreen(
    onBack: () -> Unit,
    vm: SiteSettlementViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmRevertId by remember { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.site_settlement_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.s))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(Spacing.l)) {
                        if (state.loading) {
                            Text(stringResource(R.string.site_settlement_loading), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val summary = state.summary
                            if (summary == null) {
                                Text(stringResource(R.string.site_settlement_empty), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(
                                    stringResource(R.string.site_settlement_pending_title, state.projectName),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(Spacing.s))
                                SummaryLine(
                                    stringResource(R.string.site_settlement_works),
                                    stringResource(
                                        R.string.site_settlement_works_value,
                                        summary.totalWorksMilli / 1000.0,
                                        TimeUtils.hoursDecimal(summary.otMinutes),
                                    ),
                                )
                                SummaryLine(
                                    stringResource(R.string.site_settlement_receivable),
                                    Money.yuanWithSign(summary.receivableCents),
                                    valueColor = MaterialTheme.colorScheme.primary,
                                )
                                SummaryLine(
                                    stringResource(R.string.site_settlement_advanced),
                                    Money.yuanWithSign(summary.advanceTotalCents),
                                    valueColor = SiteMoneyColors.ReceivedGreen,
                                )
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.site_settlement_net),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        Money.yuanWithSign(summary.pendingCents),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (summary.pendingCents < 0) MaterialTheme.colorScheme.error
                                        else SiteMoneyColors.PendingOrange,
                                    )
                                }
                                Spacer(Modifier.height(Spacing.s))
                                OutlinedButton(
                                    onClick = { vm.buildPreview() },
                                    enabled = summary.pendingCents != 0L,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.site_settlement_settle_all_action)) }
                            }
                        }
                    }
                }
            }

            if (state.pieces.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.site_settlement_pieces), style = MaterialTheme.typography.titleSmall)
                }
                items(state.pieces, key = { "piece_${it.id}" }) { piece ->
                    SectionCard {
                        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        Money.yuanWithSign(piece.amountCents),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    val qty = piece.quantityMilli / 1000.0
                                    val qtyText = if (piece.quantityMilli > 0)
                                        (if (qty % 1.0 == 0.0) "${qty.toInt()}" else String.format(java.util.Locale.US, "%.3f", qty).trimEnd('0').trimEnd('.')) + piece.unit + " × "
                                    else ""
                                    Text(
                                        piece.date + " · " + (piece.itemName.ifEmpty { stringResource(R.string.site_piece_unnamed) }) +
                                            " · " + qtyText + Money.yuanText(piece.unitPriceCents).replace(",", "") + "元",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.deletePiece(piece.id) }) {
                                    Text(stringResource(R.string.site_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (state.advances.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.site_settlement_advances), style = MaterialTheme.typography.titleSmall)
                }
                items(state.advances, key = { "advance_${it.id}" }) { adv ->
                    SectionCard {
                        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        Money.yuanWithSign(adv.amountCents),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SiteMoneyColors.ReceivedGreen,
                                    )
                                    Text(
                                        adv.date + " · " + purposeLabel(adv.purpose) + (adv.note?.let { " · " + it } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.deleteAdvance(adv.id) }) {
                                    Text(stringResource(R.string.site_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (state.settlements.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.site_settlement_history), style = MaterialTheme.typography.titleSmall)
                }
                items(state.settlements, key = { "settle_${it.id}" }) { row ->
                    SectionCard {
                        if (row.isPartial) {
                            // 部分结算（本次结算金额）：到手流水，绿色；撤销即删除
                            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.site_settlement_partial_row), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            row.periodLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        Money.yuanWithSign(row.netCents),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SiteMoneyColors.ReceivedGreen,
                                    )
                                }
                                TextButton(onClick = { confirmRevertId = row.id }) {
                                    Text(stringResource(R.string.site_settlement_revert), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.periodLabel, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            stringResource(R.string.site_settlement_row_detail, row.workYuan, row.advanceYuan, row.netYuan),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        Money.yuanWithSign(row.netCents),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TextButton(onClick = { confirmRevertId = row.id }) {
                                        Text(stringResource(R.string.site_settlement_revert), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }

    state.preview?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.dismissPreview() },
            title = { Text(stringResource(R.string.site_settlement_preview_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.site_settlement_preview_period,
                            p.from.monthValue, p.from.dayOfMonth, p.to.monthValue, p.to.dayOfMonth,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    SummaryLine(
                        stringResource(R.string.site_settlement_receivable),
                        Money.yuanWithSign(p.workPayCents + p.piecePayCents),
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    SummaryLine(
                        stringResource(R.string.site_settlement_advanced_count, p.advanceCount),
                        Money.yuanWithSign(p.advanceTotalCents),
                        valueColor = SiteMoneyColors.ReceivedGreen,
                    )
                    if (p.partialSettledCents > 0) {
                        SummaryLine(
                            stringResource(R.string.site_settlement_partial_deduct),
                            "−" + Money.yuanWithSign(p.partialSettledCents),
                            valueColor = SiteMoneyColors.ReceivedGreen,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SummaryLine(
                        stringResource(R.string.site_settlement_partial_paid),
                        Money.yuanWithSign(p.netCents),
                        valueColor = SiteMoneyColors.PendingOrange,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.site_settlement_lock_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmSettlement() }) { Text(stringResource(R.string.site_settlement_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPreview() }) { Text(stringResource(R.string.site_dialog_cancel)) }
            },
        )
    }

    confirmRevertId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRevertId = null },
            title = { Text(stringResource(R.string.site_settlement_revert_title)) },
            text = { Text(stringResource(R.string.site_settlement_revert_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevertId = null
                    vm.revertSettlement(id)
                }) { Text(stringResource(R.string.site_settlement_revert), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevertId = null }) { Text(stringResource(R.string.site_dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun purposeLabel(purpose: String): String = when (purpose) {
    "WAGE" -> stringResource(R.string.site_purpose_wage)
    "LIVING" -> stringResource(R.string.site_purpose_living)
    "LODGING" -> stringResource(R.string.site_purpose_lodging)
    "LODGING_ALLOW" -> stringResource(R.string.site_purpose_lodging_allow)
    "MEALS" -> stringResource(R.string.site_purpose_meals)
    "MEALS_ALLOW" -> stringResource(R.string.site_purpose_meals_allow)
    "REWARD" -> stringResource(R.string.site_purpose_reward)
    "MATERIALS" -> stringResource(R.string.site_purpose_materials)
    "TRANSPORT" -> stringResource(R.string.site_purpose_transport)
    "PROJECT_PAYMENT" -> stringResource(R.string.site_purpose_project)
    "POCKET" -> stringResource(R.string.site_purpose_pocket)
    else -> stringResource(R.string.site_purpose_other)
}
