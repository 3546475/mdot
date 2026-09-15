package com.mdot.app.feature.site

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.model.SiteDayStatus
import com.mdot.app.domain.model.SiteOtMode
import com.mdot.app.domain.util.Money

/**
 * 记工页四个表单：记账·点工 / 记账·包工 / 记借支·借支 / 记借支·结算。
 * 均渲染在 SiteRecordScreen 的表单分页内（页 0..3），由悬浮保存按钮分发保存。
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttendanceForm(
    state: SiteRecordUiState,
    vm: SiteRecordViewModel,
) {
    var showWorkDayPicker by remember { mutableStateOf(false) }
    var showWorkHourPicker by remember { mutableStateOf(false) }
    var showOtDayPicker by remember { mutableStateOf(false) }
    var showOtHourPicker by remember { mutableStateOf(false) }
    var showStandardDialog by remember { mutableStateOf(false) }

    val base = state.project?.baseMinutes ?: 480
    val otBase = state.project?.otBaseMinutes ?: 360

    // ---- 上班 + 加班：一张卡两段（段间分隔线），减少散块 ----
    SectionCard {
        Column {
            Text(
                stringResource(R.string.site_work),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SelectTile(
            label = stringResource(R.string.site_one_work),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 0,
            modifier = Modifier.weight(1f),
        ) { vm.onWorkMinutes(base); vm.onHalfOfDay(null) }
        SelectTile(
            label = if (state.dayStatus == SiteDayStatus.WORK && state.workTile == 1)
                fmtAmount(state.workMinutes.toDouble() / base, R.string.site_picked_days_fmt)
            else stringResource(R.string.site_pick_days),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 1,
            modifier = Modifier.weight(1f),
        ) { showWorkDayPicker = true }
        SelectTile(
            label = if (state.dayStatus == SiteDayStatus.WORK && state.workTile == 2)
                fmtAmount(state.workMinutes / 60.0, R.string.site_picked_hours_fmt)
            else stringResource(R.string.site_pick_hours),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 2,
            modifier = Modifier.weight(1f),
        ) { showWorkHourPicker = true }
        SelectTile(
            label = stringResource(R.string.site_rest),
            selected = state.dayStatus == SiteDayStatus.REST,
            modifier = Modifier.weight(1f),
        ) { vm.onDayStatus(SiteDayStatus.REST) }
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(R.string.site_ot),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SelectTile(
                    label = stringResource(R.string.site_no_ot),
                    selected = state.otTile == 0,
                    modifier = Modifier.weight(1f),
                ) { vm.onOtMinutes(0) }
                SelectTile(
                    label = if (state.otTile == 1) fmtAmount(state.otMinutes.toDouble() / otBase, R.string.site_picked_days_fmt)
                    else stringResource(R.string.site_pick_days),
                    selected = state.otTile == 1,
                    modifier = Modifier.weight(1f),
                ) { showOtDayPicker = true }
                SelectTile(
                    label = if (state.otTile == 2) fmtAmount(state.otMinutes / 60.0, R.string.site_picked_hours_fmt)
                    else stringResource(R.string.site_pick_hours),
                    selected = state.otTile == 2,
                    modifier = Modifier.weight(1f),
                ) { showOtHourPicker = true }
            }
        }
    }

    Spacer(Modifier.height(Spacing.s))

    // ---- 工钱 hero 卡（primaryContainer 底色突出主角；¥ 大字滚动动画；点击 → 点工标准设置） ----
    PayHeroCard(
        previewCents = state.previewCents(),
        summary = state.project?.let {
            stringResource(R.string.site_pay_summary_work, Money.yuanText(it.dailyRateCents).replace(",", "")) +
                "\n" + stringResource(R.string.site_pay_summary_ot, it.otBaseMinutes / 60)
        } ?: "",
        onClick = { showStandardDialog = true },
    )

    Spacer(Modifier.height(Spacing.s))

    if (showWorkDayPicker) {
        DayCountDialog(
            title = stringResource(R.string.site_pick_days),
            current = state.workMinutes,
            unitMinutes = base,
            onPick = { minutes -> vm.pickWorkDays(minutes); showWorkDayPicker = false },
            onDismiss = { showWorkDayPicker = false },
        )
    }
    if (showWorkHourPicker) {
        HourInputDialog(
            title = stringResource(R.string.site_pick_hours),
            initialHours = state.workMinutes.takeIf { m -> m > 0 }?.let { m -> m / 60.0 },
            onPick = { hours -> vm.pickWorkHours((hours * 60).toInt()); showWorkHourPicker = false },
            onDismiss = { showWorkHourPicker = false },
        )
    }
    if (showOtDayPicker) {
        DayCountDialog(
            title = stringResource(R.string.site_pick_days),
            current = state.otMinutes,
            unitMinutes = otBase,
            onPick = { minutes -> vm.pickOtDays(minutes); showOtDayPicker = false },
            onDismiss = { showOtDayPicker = false },
        )
    }
    if (showOtHourPicker) {
        HourInputDialog(
            title = stringResource(R.string.site_pick_hours),
            initialHours = state.otMinutes.takeIf { m -> m > 0 }?.let { m -> m / 60.0 },
            onPick = { hours -> vm.pickOtHours((hours * 60).toInt()); showOtHourPicker = false },
            onDismiss = { showOtHourPicker = false },
        )
    }
    if (showStandardDialog) {
        state.project?.let { p ->
            fun hoursText(minutes: Int): String =
                if (minutes % 60 == 0) "${minutes / 60}"
                else String.format(java.util.Locale.US, "%.1f", minutes / 60.0)
            ProjectStandardDialog(
                initial = SiteProjectEditUi(
                    id = p.id,
                    name = p.name,
                    baseHoursText = hoursText(p.baseMinutes),
                    rateYuanText = Money.yuanText(p.dailyRateCents).replace(",", "").removeSuffix(".00"),
                    otMode = runCatching { SiteOtMode.valueOf(p.otMode) }.getOrDefault(SiteOtMode.BY_DAY),
                    otBaseHoursText = hoursText(p.otBaseMinutes),
                    otHourlyYuanText = if (p.otHourlyCents > 0) {
                        Money.yuanText(p.otHourlyCents).replace(",", "").removeSuffix(".00")
                    } else "",
                ),
                onApply = { baseHours, rateYuan, mode, otBaseHours, otHourlyYuan ->
                    vm.applyStandard(baseHours, rateYuan, mode, otBaseHours, otHourlyYuan)
                    showStandardDialog = false
                },
                onDismiss = { showStandardDialog = false },
            )
        }
    }
}

// ---- 借支内容 ----

@Composable
internal fun AdvanceForm(state: SiteRecordUiState, vm: SiteRecordViewModel) {
    // ---- 借支：本次金额（大字直填，样式同包工工钱） ----
    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_advance_amount_label),
        value = state.advanceYuanText,
        fallback = "0.00",
        onValueChange = vm::onAdvanceAmount,
    ) {
        Text(
            stringResource(R.string.site_advance_yuan_unit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettleForm(state: SiteRecordUiState, vm: SiteRecordViewModel, onOpenSettlement: () -> Unit) {
    // ---- 结算：本次结算金额（部分结算，像借支一样拿走一笔；结清入口见页面底部） ----
    SectionLabel(stringResource(R.string.site_settlement_partial_title))
    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_settlement_partial_amount_label),
        value = state.settleAmountText,
        fallback = "0.00",
        onValueChange = vm::onSettleAmount,
    ) {
        Text(
            stringResource(R.string.site_yuan_symbol),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Spacing.m))

    // ---- 结清（原结算页主流程）：锁定全部未结算记录并归档快照，一次清零待结余额 ----
    TonalRowCard(
        iconRes = R.drawable.ic_ms_flip,
        label = stringResource(R.string.site_settlement_settle_all),
        value = stringResource(R.string.site_settlement_settle_all_hint),
        onClick = onOpenSettlement,
    )
}

// ---- 包工/工量表单（Phase 2，D4-rev：量价齐自动算钱，否则直填） ----

@Composable
internal fun PieceForm(state: SiteRecordUiState, vm: SiteRecordViewModel, onOpenUnitSheet: () -> Unit) {
    val directCents = Money.parseYuanToCents(state.pieceAmountText) ?: 0
    // 量价齐自动：仅在未手填工钱时生效（手填工钱直接在下方工钱数字上编辑）
    val autoAmount = directCents <= 0 && state.pieceQuantityMilli > 0 &&
        (Money.parseYuanToCents(state.piecePriceText) ?: 0) > 0

    // ---- 工程量：大字直填 + 右侧单位按钮（点击弹底部弹层选单位） ----
    BigAmountRow(
        iconRes = R.drawable.ic_ms_dashboard,
        label = stringResource(R.string.site_piece_quantity),
        value = state.pieceQuantityText,
        fallback = "0.00",
        onValueChange = vm::onPieceQuantity,
    ) { UnitPickButton(current = state.pieceUnit, onClick = onOpenUnitSheet) }
    Spacer(Modifier.height(Spacing.s))

    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_piece_price),
        value = state.piecePriceText,
        fallback = "0.00",
        onValueChange = vm::onPiecePrice,
    ) {
        Text(
            stringResource(R.string.site_piece_price_unit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Spacing.m))

    // 工钱：预览数字本身可编辑（样式不变）：点大字直接键入手填工钱；
    // 未填时展示量×价自动值，清空后回到自动
    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_pay),
        value = state.pieceAmountText,
        fallback = Money.yuanText(state.piecePreviewCents()).replace(",", ""),
        onValueChange = vm::onPieceAmount,
    ) {
        if (autoAmount) {
            Text(
                stringResource(R.string.site_piece_auto_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 单位按钮：显示当前单位 + 下拉箭头，点击打开自实现底部弹层 */
@Composable
private fun UnitPickButton(current: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(44.dp)
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(RoundedCornerShape(Radius.textField))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            current,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            painterResource(R.drawable.ic_ms_expand_more), null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}
