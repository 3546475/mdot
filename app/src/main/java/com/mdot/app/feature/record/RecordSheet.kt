package com.mdot.app.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ConfirmDialog
import com.mdot.app.core.designsystem.component.SunkenWell
import com.mdot.app.core.designsystem.component.TierRow
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.HourlyPayrollStrategy
import com.mdot.app.domain.StandardPayrollStrategy
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanButtonSize
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/** 记录底部弹层（03 文档 §5.2 线框）：两段式——简洁面板（类型+时长）⇄ 完整面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    request: RecordRequest,
    /** 弹层可见性状态：由宿主（AppRoot）持有——背景「模糊 + 缩小」层与本弹层共享同一过渡状态，
     *  进出场因此严格同步。本弹层只负责置 targetState（打开 true / 关闭 false）。 */
    visibleState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    vm: RecordSheetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shifts by vm.visibleShifts.collectAsStateWithLifecycle()

    // 两段式：简洁面板（类型+时长）⇄ 完整面板；点箭头或上拉展开
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    // T1-1：保存类重动作统一 LongPress 触觉（docs/15，共享扩展）
    val saveHaptic = rememberSaveWithHaptic()

    LaunchedEffect(request.token) {
        expanded = false
        vm.bind(request)
        // 每次打开重置过渡状态→true（放在 request.token 键下：
        // 原写法在组合体内无条件置 true，退场途中任何一次重组都会把动画拉回，故改由此处一次性触发）
        visibleState.targetState = true
    }

    // 自实现底部弹层（M3 ModalBottomSheet 在内容高度变化时锚点会误判滑出，故弃用）
    var dismissRequested by rememberSaveable { mutableStateOf(false) }
    // B6-02：弹层进出场接入 motionScheme（原 tween(Duration.*) 绕过动效体系）
    val sheetEnterSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val sheetFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

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
    // 保存/删除成功：走退出动画后再真正关闭（不直接摘掉组合）
    LaunchedEffect(Unit) { vm.closeRequests.collect { requestDismiss() } }

    // 遮罩与面板拆成两个 AnimatedVisibility（共享同一 visibleState）：遮罩原地淡入淡出、
    // 面板自下而上滑入/下滑淡出——避免遮罩跟随上推，且保证退出动画完整播放
    // （原外层 if 会在 dismiss 同帧移除组合，吞掉 exit 动画，导致弹层突兀消失）
    // 遮罩本体已透明：压暗/模糊/缩小由宿主的 SheetBackdropLayer 负责，此处只保留「挡板」
    // 职责（消费点击、拦截穿透），背景色留着会与背景层叠加成双重压暗
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(sheetFadeSpec),
        exit = fadeOut(sheetFadeSpec),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
        )
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(sheetEnterSpec) { it },
        exit = slideOutVertically(sheetEnterSpec) { it } + fadeOut(sheetFadeSpec),
    ) {
        // 面板（Box 提供 BottomCenter 对齐作用域）
        Box(Modifier.fillMaxSize()) {
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
                                // B6-05：原按单事件增量判定——快甩有效、慢拖永不触发；改累计位移
                                var acc = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { acc = 0f },
                                    onDragEnd = { acc = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        acc += dragAmount
                                        if (acc < -24f) { expanded = true; acc = 0f }
                                        else if (acc > 24f && expanded) { expanded = false; acc = 0f }
                                    },
                                )
                            }
                            .padding(vertical = Spacing.m),
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
                                    .padding(vertical = Spacing.xs),
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
                        // 两种输入态容器等高（凹陷托盘 156dp：内层 144dp 可视 3 行 ×44+间距，
                        // 外圈 6dp 凹面留边；预设已扩至 24h、更多的行在网格内部滚动），
                        // 切换不引起弹层高度跳动
                        // 高度 = DurationGrid 可视 3 行（44×3 + Spacing.s×2 = 148）+ SunkenWell 内衬 Spacing.s×2
                        SunkenWell(Modifier.fillMaxWidth().height(164.dp)) {
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

                        // ---- 完整面板区域（展开后显示；半开⇄全开带高度展开/收起动画） ----
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                expandFrom = Alignment.Top,
                            ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkVertically(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            Column {
                                Spacer(Modifier.height(Spacing.m))
                                Text(stringResource(R.string.record_shift_pick_title), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.xs))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
                                                    Icon(painterResource(R.drawable.ic_ms_remove), null, Modifier.size(IconSpec.dense))
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
                                                    Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(IconSpec.dense))
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
                        // 间距不走 spacedBy：删除钮与取消之间的间距放在 AnimatedVisibility
                        // 内容内部（随收合一起归零）——否则收合完成后子项移除、spacedBy 间隙
                        // 消失，取消/保存会再跳一截（用户反馈的迟滞位移）
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = Spacing.l),
                        ) {
                            // 删除钮随面板展开/收起同拍进出（水平展开/收合 + 淡入淡出，
                            // 与上方面板同一 motionScheme 弹簧）：出现/消失都不再瞬时跳变
                            if (state.editing) {
                                AnimatedVisibility(
                                    visible = expanded,
                                    enter = expandHorizontally(
                                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                        expandFrom = Alignment.Start,
                                    ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                    exit = shrinkHorizontally(
                                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                        shrinkTowards = Alignment.Start,
                                    ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                                ) {
                                    OutlinedButton(
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier.padding(end = Spacing.m),
                                    ) { Text(stringResource(R.string.record_delete), color = MaterialTheme.colorScheme.error) }
                                }
                            }
                            OutlinedButton(
                                onClick = { requestDismiss() },
                                modifier = Modifier.weight(1f).padding(end = Spacing.m),
                            ) { Text(stringResource(R.string.record_cancel)) }
                            Button(
                                onClick = {
                                    saveHaptic()
                                    vm.save()
                                },
                                enabled = state.durationMinutes() > 0,
                                modifier = Modifier.weight(1.6f),
                            ) { Text(stringResource(R.string.record_save)) }
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
        val density = LocalDensity.current
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                // 与记加班弹窗操作行同款（docs 03 §13 形态）：确定 PRIMARY、取消 GHOST
                JiabanButton(
                    text = stringResource(R.string.record_ok),
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            vm.onDateChange(
                                java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    },
                    role = JiabanButtonRole.PRIMARY,
                    size = JiabanButtonSize.M,
                )
            },
            dismissButton = {
                JiabanButton(
                    text = stringResource(R.string.record_cancel),
                    onClick = { showDatePicker = false },
                    role = JiabanButtonRole.GHOST,
                    size = JiabanButtonSize.M,
                )
            },
        ) {
            // 手动输入模式切换与标题均已移除（用户定稿）：无模式切换即无尺寸动画，
            // 也无 M3 alpha18 缺失中文翻译的「Select date」标题
            // 标题槽传空：压掉 M3 默认英文「Select date」
            // layout 裁掉标题槽残留的空内容+内边距（约 40dp），消除大标题上方留白
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                title = {},
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    val trim = with(density) { 40.dp.roundToPx() }
                    layout(p.width, (p.height - trim).coerceAtLeast(0)) {
                        p.place(0, -trim)
                    }
                },
            )
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { opt ->
            FilterChip(selected = opt.selected, onClick = { onSelect(opt.key) }, label = { Text(opt.label) })
        }
    }
}
