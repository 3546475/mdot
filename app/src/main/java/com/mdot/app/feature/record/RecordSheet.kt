package com.mdot.app.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ConfirmDialog
import com.mdot.app.core.designsystem.component.TierRow
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.HourlyPayrollStrategy
import com.mdot.app.domain.StandardPayrollStrategy
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/** 记录底部弹层（03 文档 §5.2 线框）：两段式——简洁面板（类型+时长）⇄ 完整面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    request: RecordRequest,
    onDismiss: () -> Unit,
    vm: RecordSheetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shifts by vm.visibleShifts.collectAsStateWithLifecycle()

    // 两段式：简洁面板（类型+时长）⇄ 完整面板；点箭头或上拉展开
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(request.token) {
        expanded = false
        vm.bind(request)
    }

    // 自实现底部弹层（M3 ModalBottomSheet 在内容高度变化时锚点会误判滑出，故弃用）
    var dismissRequested by remember { mutableStateOf(false) }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true

    fun requestDismiss() {
        dismissRequested = true
    }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visibleState.targetState = false
            while (!visibleState.isIdle) delay(16)
            onDismiss()
        }
    }

    BackHandler(onBack = { requestDismiss() })

    if (!dismissRequested || !visibleState.isIdle) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(tween(Duration.slow)) { it },
            exit = slideOutVertically(tween(Duration.normal)) { it } + fadeOut(tween(Duration.normal)),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 遮罩
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
                )
                // 面板
                val wide = LocalWindowSpec.current == WindowSpec.EXPANDED
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        // 响应式：宽屏下弹层限宽居中 + 四角全圆 + 底部留边（悬浮面板形态），窄屏全宽贴底
                        .widthIn(max = AdaptiveSpecs.sheetMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                        .padding(bottom = if (wide) Spacing.l else 0.dp)
                        .clip(
                            if (wide) RoundedCornerShape(Radius.sheet)
                            else RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = false) { }
                        .imePadding()
                ) {
                    // 把手：点击切换 + 上拉展开/下拉收起
                    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount < -24) expanded = true
                                    else if (dragAmount > 24 && expanded) expanded = false
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_keyboard_arrow_up),
                            contentDescription = if (expanded) stringResource(R.string.record_cd_collapse) else stringResource(R.string.record_cd_expand),
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer { rotationZ = rotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(
                        Modifier
                            .padding(horizontal = Spacing.l)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ---- 日期 + Tab ----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp),
                            ) {
                                TextButton(onClick = { showDatePicker = true }) {
                                    Text(
                                        "${TimeUtils.dateLabel(state.date, LocalDate.now())}  " +
                                            TimeUtils.mdCn(state.date) + " ›"
                                    )
                                }
                            }
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = state.tab == RecordType.OT,
                                    onClick = { vm.onTabChange(RecordType.OT) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text(stringResource(otLabel(state.salary.workSystem))) }
                                SegmentedButton(
                                    selected = state.tab == RecordType.LEAVE,
                                    onClick = { vm.onTabChange(RecordType.LEAVE) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text(stringResource(R.string.record_tab_leave)) }
                            }
                        }

                        Spacer(Modifier.height(Spacing.s))

                        // ---- 时长（标题 + 数值 + 小时/分钟切换） ----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (state.tab == RecordType.OT) stringResource(otLabel(state.salary.workSystem)) + stringResource(R.string.record_duration_suffix)
                                    else stringResource(R.string.record_duration_leave),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 数值变化时按方向滑动淡入淡出
                                AnimatedContent(
                                    targetState = state.durationMinutes(),
                                    transitionSpec = {
                                        if (targetState >= initialState) {
                                            (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))) togetherWith
                                                (slideOutVertically(tween(150)) { -it / 2 } + fadeOut(tween(120)))
                                        } else {
                                            (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(180))) togetherWith
                                                (slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(120)))
                                        }
                                    },
                                    label = "durationValue",
                                ) { minutes ->
                                    Text(
                                        minutes.takeIf { it > 0 }
                                            ?.let { TimeUtils.prettyDuration(it) } ?: "…",
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                }
                            }
                            // 小时 / 分钟 切换（样式与上方加班/请假分段按钮一致）
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = state.durationMode == DurationInputMode.HOURS,
                                    onClick = { vm.onDurationModeChange(DurationInputMode.HOURS) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text(stringResource(R.string.record_mode_hours)) }
                                SegmentedButton(
                                    selected = state.durationMode == DurationInputMode.MINUTES,
                                    onClick = { vm.onDurationModeChange(DurationInputMode.MINUTES) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text(stringResource(R.string.record_mode_minutes)) }
                            }
                        }

                        Spacer(Modifier.height(Spacing.s))
                        // 两种输入态容器等高（小时网格可视 3 行 ×44+间距 ≈144dp，预设已扩至 24h、
                        // 更多的行在网格内部滚动），切换不引起弹层高度跳动
                        Box(Modifier.fillMaxWidth().height(144.dp)) {
                            if (state.durationMode == DurationInputMode.MINUTES) {
                                MinutesWheels(
                                    totalMinutes = state.pendingMinutes,
                                    onChange = vm::onPendingMinutes,
                                )
                            } else {
                                DurationGrid(
                                    selectedHours = state.hoursValue(),
                                    onPreset = vm::onPresetHours,
                                    onCustomCommit = vm::onCustomHours,
                                )
                            }
                        }

                        // ---- 完整面板区域（展开后显示） ----
                        if (expanded) {
                            Column {
                                Spacer(Modifier.height(Spacing.m))
                                Text(stringResource(R.string.record_shift_pick_title), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.xs))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val options = if (state.tab == RecordType.OT) {
                                        shifts.map {
                                            ChipOption(it.id.toString(), it.name, it.id == state.selectedShiftId)
                                        }
                                    } else {
                                        com.mdot.app.domain.model.LeaveType.entries.map {
                                            ChipOption(it.name, it.displayName, it == state.leaveType)
                                        }
                                    }
                                    ChipFlow(options) { key ->
                                        if (state.tab == RecordType.OT) {
                                            shifts.firstOrNull { it.id.toString() == key }
                                                ?.let { vm.onShiftSelect(it) }
                                        } else {
                                            vm.onLeaveType(
                                                com.mdot.app.domain.model.LeaveType.valueOf(key)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(Spacing.m))

                                if (state.tab == RecordType.OT) {
                                    // ---- 档位行 + 补改（综合工时两种形态：10 文档 F-Z3）----
                                    val salary = state.salary
                                    val isComprehensive = salary.workSystem == WorkSystem.COMPREHENSIVE
                                    val tierText = when {
                                        isComprehensive && state.tier == RateTier.STATUTORY ->
                                            // 节假日＝唯一即时档：法定节假日 · 3倍 · xx元/小时
                                            stringResource(
                                                R.string.record_tier_statutory_holiday,
                                                Money.multiplierText(salary.multipliers[RateTier.STATUTORY] ?: 3.0),
                                                rateText(salary, RateTier.STATUTORY),
                                            )
                                        isComprehensive ->
                                            // 其余＝计入周期工时，不即时算加班
                                            stringResource(R.string.record_tier_period_counted)
                                        salary.workSystem == WorkSystem.HOURLY ->
                                            stringResource(
                                                R.string.record_tier_hourly,
                                                state.tier.displayName,
                                                rateText(salary, state.tier),
                                            )
                                        else ->
                                            stringResource(
                                                R.string.record_tier_standard,
                                                state.tier.displayName,
                                                Money.multiplierText(salary.multipliers[state.tier] ?: 1.5),
                                                rateText(salary, state.tier),
                                            )
                                    }
                                    TierRow(
                                        text = tierText + if (state.tierManual) stringResource(R.string.record_tier_manual_suffix) else "",
                                        onClick = null,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RateTier.entries.forEach { tier ->
                                            // 综合工时无周末档（10 文档 D5）
                                            if (isComprehensive && tier == RateTier.WEEKEND) return@forEach
                                            FilterChip(
                                                selected = state.tier == tier,
                                                onClick = { vm.onTierSelect(tier) },
                                                label = { Text(tier.displayName) },
                                            )
                                        }
                                        if (state.tierManual) {
                                            FilterChip(
                                                selected = false,
                                                onClick = { vm.onTierReset() },
                                                label = { Text(stringResource(R.string.record_tier_auto)) },
                                            )
                                        }
                                    }

                                    if (state.salary.workSystem == WorkSystem.STANDARD) {
                                        Spacer(Modifier.height(Spacing.m))

                                        // ---- 转调休（仅标准工时；小时工/综合工时无调休基础，10 文档 D4）----
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.record_tocomp_value, TimeUtils.prettyDuration(state.toCompMinutes)),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                                OutlinedButton(
                                                    onClick = { vm.onToCompDelta(-30) },
                                                    shape = RoundedCornerShape(
                                                        topStart = Radius.textField,
                                                        bottomStart = Radius.textField,
                                                        topEnd = 4.dp,
                                                        bottomEnd = 4.dp,
                                                    ),
                                                ) {
                                                    Icon(painterResource(R.drawable.ic_ms_remove), null, Modifier.size(18.dp))
                                                }
                                                OutlinedButton(
                                                    onClick = { vm.onToCompDelta(30) },
                                                    shape = RoundedCornerShape(
                                                        topStart = 4.dp,
                                                        bottomStart = 4.dp,
                                                        topEnd = Radius.textField,
                                                        bottomEnd = Radius.textField,
                                                    ),
                                                ) {
                                                    Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ---- 请假扣款预览 ----
                                    val deduct = if (state.salary.workSystem == WorkSystem.HOURLY) {
                                        HourlyPayrollStrategy.leaveDeductCents(
                                            state.salary, state.leaveType, state.durationMinutes()
                                        )
                                    } else {
                                        StandardPayrollStrategy.leaveDeductCents(
                                            state.salary, state.leaveType, state.durationMinutes()
                                        )
                                    }
                                    TierRow(text = stringResource(R.string.record_deduct_preview, Money.yuanWithSign(deduct)), onClick = null)
                                }

                                Spacer(Modifier.height(Spacing.m))

                                // ---- 备注 ----
                                OutlinedTextField(
                                shape = RoundedCornerShape(Radius.textField),
                                    value = state.note,
                                    onValueChange = vm::onNote,
                                    label = { Text(stringResource(R.string.record_note_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 1,
                                    maxLines = 3,
                                )
                            }
                        }

                        state.errorText?.let { err ->
                            Spacer(Modifier.height(Spacing.xs))
                            Text(err, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(Modifier.height(Spacing.m))

                        // ---- 操作区（两态共用；编辑态展开时含删除） ----
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = Spacing.l),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            if (state.editing && expanded) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.record_delete), color = MaterialTheme.colorScheme.error) }
                            }
                            OutlinedButton(
                                onClick = { requestDismiss() },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.record_cancel)) }
                            Button(
                                onClick = vm::save,
                                enabled = state.durationMinutes() > 0,
                                modifier = Modifier.weight(1.6f),
                            ) { Text(stringResource(R.string.record_save)) }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.onDateChange(
                            java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.record_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.record_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.record_delete_title),
            text = stringResource(
                R.string.record_delete_confirm_text,
                TimeUtils.mdCn(state.date),
                stringResource(
                    if (state.tab == RecordType.OT) otLabel(state.salary.workSystem)
                    else R.string.record_tab_leave
                ),
            ),
            onConfirm = {
                showDeleteConfirm = false
                vm.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** 记录弹层 OT Tab 的制度化文案（10 文档 F-Z3：综合工时显示「上班」） */
private fun otLabel(workSystem: com.mdot.app.domain.model.WorkSystem): Int = when (workSystem) {
    com.mdot.app.domain.model.WorkSystem.HOURLY -> R.string.record_tab_ot_hourly
    com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> R.string.record_tab_ot_comprehensive
    com.mdot.app.domain.model.WorkSystem.STANDARD -> R.string.record_tab_ot_standard
    com.mdot.app.domain.model.WorkSystem.SITE -> R.string.site_tab_ot
}

private fun rateText(salary: com.mdot.app.domain.model.SalaryConfig, tier: RateTier): String =
    when {
        salary.workSystem == com.mdot.app.domain.model.WorkSystem.HOURLY ->
            Money.yuanText(salary.hourlyRatesCents[tier] ?: 0L)
        salary.mode == com.mdot.app.domain.model.SalaryMode.BASE ->
            Money.hourlyRateText(salary.baseSalaryCents)
        else ->
            Money.yuanText(salary.manualRatesCents[tier] ?: 0L)
    }

/** 分钟模式：时/分两列循环无限滚轮；0 小时 0 分钟时自动给 30 分钟默认值 */
@Composable
private fun MinutesWheels(
    totalMinutes: Int,
    onChange: (Int) -> Unit,
) {
    // 首次进入（时长为 0）默认 30 分钟，避免打开滚轮无值
    LaunchedEffect(Unit) {
        if (totalMinutes <= 0) onChange(30)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoopWheel(
            value = (totalMinutes / 60).coerceIn(0, 23),
            itemCount = 24,
            onValueChange = { h -> onChange(h * 60 + totalMinutes % 60) },
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.record_wheel_hour),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.s))
        LoopWheel(
            value = totalMinutes % 60,
            itemCount = 60,
            onValueChange = { m -> onChange((totalMinutes / 60).coerceIn(0, 23) * 60 + m) },
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.record_wheel_minute),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ChipOption(val key: String, val label: String, val selected: Boolean)

/** 单行流式 Chip（选项多时可横滑） */
@Composable
private fun ChipFlow(options: List<ChipOption>, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            FilterChip(selected = opt.selected, onClick = { onSelect(opt.key) }, label = { Text(opt.label) })
        }
    }
}
