package com.mdot.app.feature.calendar

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.EngineIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.wrapContentWidth
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.feature.record.rememberSaveWithHaptic
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.contentBottomPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.DurationGrid
import java.time.LocalDate
import kotlin.math.roundToInt
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanIconButton

/** 日历页（03 文档 §5.3 线框）
 *  响应式（docs 03 §3.2）：EXPANDED（≥840dp）双栏——左月历 | 右小结卡纵向堆叠，总宽 720dp 居中；
 *  其余档位单列（原布局）。 */
@Composable
fun CalendarScreen(
    initialMonth: String? = null,
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    vm: CalendarViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(initialMonth) { vm.initMonth(initialMonth) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val batchSelecting by vm.isBatchSelecting.collectAsStateWithLifecycle()
    val batchSelected by vm.batchSelection.collectAsStateWithLifecycle()
    var showBatchDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // 已有加班记录的选中天数（弹层/操作条覆盖提示）
    val overwriteCount = batchSelected.count { d -> state.cells.any { it.date == d && it.otMinutes > 0 } }
    // T1-2：批量保存结果 Snackbar（docs/15）：浅色悬浮胶囊（与关于页检查更新同范式）
    val batchMessage by vm.messageFlow.collectAsStateWithLifecycle()
    val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
        message = batchMessage,
        onClear = vm::clearMessage,
    )
    // T1-1：批量保存触觉（LongPress）
    val saveHaptic = rememberSaveWithHaptic()
    // 多选模式下系统返回先退出多选，不退出日历页
    androidx.activity.compose.BackHandler(enabled = batchSelecting) { vm.exitBatchSelect() }
    val colorScheme = MaterialTheme.colorScheme
    val twoPane = LocalWindowSpec.current == WindowSpec.EXPANDED

    val topBar: @Composable () -> Unit = {
        if (canBack) {
            JiabanTopBar(title = stringResource(R.string.calendar_title), onBack = onBack)
        } else {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + Spacing.xs))
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .contentBottomPadding(showBottomBar = !canBack)
                    .padding(horizontal = Spacing.page)
                    .widthIn(max = AdaptiveSpecs.twoPaneMaxWidth),
            ) {
                // 左栏：月历（月份行 + 星期行 + 网格）
                Column(Modifier.weight(1f)) {
                    topBar()
                    MonthHeaderRow(vm)
                    Spacer(Modifier.height(Spacing.s))
                    WeekdayHeaderRow()
                    Spacer(Modifier.height(Spacing.xs))
                    MonthGrid(state, vm)
                }
                Spacer(Modifier.width(Spacing.l))
                // 右栏：小结卡（多选时替换为批量操作条）
                Column(Modifier.weight(1f)) {
                    topBar()
                    Spacer(Modifier.height(Spacing.xs))
                    if (batchSelecting) {
                        BatchActionBar(
                            selectedCount = batchSelected.size,
                            overwriteCount = overwriteCount,
                            onSelectConfirm = { showBatchDialog = true },
                            onCancel = { vm.exitBatchSelect() },
                        )
                    } else {
                        SummaryCards(state, colorScheme)
                    }
                }
            }
        } else {
            // ---- 手机：单列（原布局）——整列可滚动（2026-09-28 用户报：日历作底栏一级页时
            // 避让底栏后「本月小结」显示空间不足，应像其它页面一样可滚动） ----
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .contentBottomPadding(showBottomBar = !canBack)
                    .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                    .padding(horizontal = Spacing.page),
            ) {
                topBar()
                MonthHeaderRow(vm)
                Spacer(Modifier.height(Spacing.s))
                WeekdayHeaderRow()
                Spacer(Modifier.height(Spacing.xs))
                MonthGrid(state, vm)
                Spacer(Modifier.height(Spacing.m))
                if (batchSelecting) {
                    BatchActionBar(
                        selectedCount = batchSelected.size,
                        overwriteCount = overwriteCount,
                        onSelectConfirm = { showBatchDialog = true },
                        onCancel = { vm.exitBatchSelect() },
                    )
                } else {
                    SummaryCards(state, colorScheme)
                }
            }
        }

        MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
    if (showBatchDialog) {
        BatchOtDialog(
            selectedCount = batchSelected.size,
            overwriteCount = overwriteCount,
            onConfirm = { minutes ->
                saveHaptic()
                showBatchDialog = false
                vm.saveBatch(minutes)
            },
            onDismiss = { showBatchDialog = false },
        )
    }
}

/** 月份标题行：‹ 2026年9月 › + 回到今天（AnimatedContent 方向感滑动） */
@Composable
private fun MonthHeaderRow(vm: CalendarViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 动效走 motionScheme（硬规则 7）；「回到今天」整块 AnimatedVisibility 永远在组合里，
    // 不用 if 包裹——否则退场时节点瞬间摘除，收缩动画播不出（同 MonthStepper「回本期」范式）
    val backFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val backSlideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    Row(verticalAlignment = Alignment.CenterVertically) {
        JiabanIconButton(onClick = vm::prevMonth) {
            Icon(painterResource(R.drawable.ic_ms_keyboard_arrow_left), contentDescription = stringResource(R.string.calendar_prev_month))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = state.month,
                transitionSpec = {
                    val forward = targetState > initialState
                    val spec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                    (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))) togetherWith
                        (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)))
                },
                label = "monthTitle",
            ) { m ->
                // 周期视图下标题仍是「锚点月」（与明细/统计页「点 n 月 = 以 n 月为锚点的那一期」同一语义），
                // 第二行补出**本次实际展示的区间**，否则用户看不出网格为什么从 26 号起、又为什么跨到下月
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.calendar_month_title, m.year, m.monthValue),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (state.isCycleView) {
                        val p = state.period
                        Text(
                            stringResource(
                                R.string.calendar_period_range,
                                p.from.monthValue, p.from.dayOfMonth, p.to.monthValue, p.to.dayOfMonth,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // 已选中今天时不显示「回到今天」：没有可回之处（同月选别的日期仍要能一键回今天）
        AnimatedVisibility(
            visible = state.selectedDate != LocalDate.now(),
            enter = fadeIn(backFadeSpec) + expandHorizontally(animationSpec = backSlideSpec),
            exit = fadeOut(backFadeSpec) + shrinkHorizontally(animationSpec = backSlideSpec),
        ) {
            TextButton(onClick = vm::goToday) { Text(stringResource(R.string.calendar_back_today)) }
        }
        // 已到今天所在区间（自然月 = 今天所在月；周期 = 含今天的周期的锚点月）即尽头：› 置灰
        JiabanIconButton(onClick = vm::nextMonth, enabled = state.canGoNext) {
            Icon(EngineIcons.chevron(), contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

/** 星期表头：一 ~ 日 */
@Composable
private fun WeekdayHeaderRow() {
    Row(Modifier.fillMaxWidth()) {
        listOf(stringResource(R.string.calendar_weekday_mon), stringResource(R.string.calendar_weekday_tue), stringResource(R.string.calendar_weekday_wed), stringResource(R.string.calendar_weekday_thu), stringResource(R.string.calendar_weekday_fri), stringResource(R.string.calendar_weekday_sat), stringResource(R.string.calendar_weekday_sun)).forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 小结卡组：今日（选中日）小结 + 本月小结 */
@Composable
private fun SummaryCards(
    state: CalendarUiState,
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.calendar_day_summary, TimeUtils.mdCn(state.selectedDate)),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Spacing.s))
            Row {
                if (state.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                    val works = state.selectedWorksMilli / 1000f
                    val wText = if (works % 1f == 0f) works.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", works)
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_site_works), stringResource(R.string.stats_works_value, wText),
                        valueColor = colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.site_stat_ot), TimeUtils.prettyDuration(state.selectedOtMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_ot_pay), "-",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(otNoun(state.workSystem)), TimeUtils.prettyDuration(state.selectedOtMinutes),
                        valueColor = colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_leave), TimeUtils.prettyDuration(state.selectedLeaveMinutes),
                        valueColor = colorScheme.error, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_ot_pay), state.selectedOtPayCents?.let { Money.yuanWithSign(it) } ?: "-",
                        modifier = Modifier.weight(1f),
                        animatedCents = state.selectedOtPayCents,
                    )
                }
            }
            state.selectedShiftName?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.calendar_shift, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(R.string.calendar_summary_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(Spacing.m))
    SectionCard {
        Column {
            // 周期视图下第二张卡的口径是「本期」（锚点月对应的完整考勤周期），不是自然月
            Text(
                stringResource(
                    if (state.isCycleView) R.string.calendar_cycle_summary
                    else R.string.calendar_month_summary
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Spacing.s))
            Row {
                if (state.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                    val works = state.monthWorksMilli / 1000f
                    val wText = if (works % 1f == 0f) works.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", works)
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_site_works), stringResource(R.string.stats_works_value, wText),
                        valueColor = colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.site_stat_ot), TimeUtils.prettyDuration(state.monthOtMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_ot_pay), "-",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(otNoun(state.workSystem)), TimeUtils.prettyDuration(state.monthOtMinutes),
                        valueColor = colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_leave), TimeUtils.prettyDuration(state.monthLeaveMinutes),
                        valueColor = colorScheme.error, modifier = Modifier.weight(1f),
                    )
                    com.mdot.app.core.designsystem.component.KeyValue(
                        stringResource(R.string.calendar_ot_pay), state.monthOtPayCents?.let { Money.yuanWithSign(it) } ?: "-",
                        modifier = Modifier.weight(1f),
                        animatedCents = state.monthOtPayCents,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                if (state.isCycleView) {
                    stringResource(R.string.calendar_cycle_recorded, state.recordedDays)
                } else {
                    stringResource(R.string.calendar_month_recorded, state.month.monthValue, state.recordedDays)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 多选批量操作条：替换小结卡显示（选中计数 + 覆盖提示 + 写入/取消） */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    overwriteCount: Int,
    onSelectConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.calendar_batch_selected, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (overwriteCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.calendar_batch_overwrite_hint, overwriteCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.calendar_batch_cancel)) }
            Spacer(Modifier.width(Spacing.xs))
            JiabanButton(
                text = stringResource(R.string.calendar_batch_title),
                onClick = onSelectConfirm,
                role = JiabanButtonRole.PRIMARY,
                enabled = selectedCount > 0,
            )
        }
    }
}

/** 批量记加班弹层：统一时长（纯小时网格，与记录弹层同款），确认后逐日写入 */
@Composable
private fun BatchOtDialog(
    selectedCount: Int,
    overwriteCount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(0) }
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_batch_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.calendar_batch_duration_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.s))
                DurationGrid(
                    selectedHours = if (minutes > 0) minutes / 60.0 else null,
                    onPreset = { h -> minutes = (h * 60).roundToInt() },
                    onCustomCommit = { raw ->
                        minutes = raw.toDoubleOrNull()
                            ?.takeIf { it > 0.0 && it <= 24.0 }
                            ?.let { (it * 60).roundToInt() }
                            ?: 0
                    },
                )
                if (overwriteCount > 0) {
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        stringResource(R.string.calendar_batch_overwrite_hint, overwriteCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.calendar_batch_confirm),
                onClick = { onConfirm(minutes) },
                role = JiabanButtonRole.GHOST,
                enabled = minutes in 1..1440,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.calendar_batch_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
