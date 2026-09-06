package com.mdot.app.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.toCalcLite
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.HolidayInfo
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarCell(
    val date: LocalDate?,
    val otMinutes: Int = 0,
    val leaveMinutes: Int = 0,
    val shiftName: String? = null,
    val holiday: HolidayInfo? = null,
)

/** 月网格动画的稳定目标态：仅月+格子参与相等性，选日期不触发切换动画 */
private data class MonthGridData(
    val month: YearMonth,
    val cells: List<CalendarCell>,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val cells: List<CalendarCell> = emptyList(),
    val monthOtMinutes: Int = 0,
    val monthLeaveMinutes: Int = 0,
    val monthOtPayCents: Long? = null,
    val recordedDays: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedOtMinutes: Int = 0,
    val selectedLeaveMinutes: Int = 0,
    val selectedOtPayCents: Long? = null,
    val selectedShiftName: String? = null,
    val workSystem: com.mdot.app.domain.model.WorkSystem = com.mdot.app.domain.model.WorkSystem.STANDARD,
)

/** 小结卡工时行标签随制度变化（综合工时=上班、小时工=工作、标准=加班） */
private fun otNoun(workSystem: com.mdot.app.domain.model.WorkSystem): Int = when (workSystem) {
    com.mdot.app.domain.model.WorkSystem.HOURLY -> R.string.calendar_ot_hourly
    com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> R.string.calendar_ot_comp
    com.mdot.app.domain.model.WorkSystem.STANDARD -> R.string.calendar_ot
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    recordRepo: RecordRepository,
    settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<CalendarUiState> = month.flatMapLatest { m ->
        combine(
            recordRepo.observeRange(m.atDay(1), m.atEndOfMonth()),
            settings.salaryFlow,
            settings.workdaysFlow,
            selectedDate,
        ) { records, salary, workdays, selDate ->
            val byDate = records.groupBy { it.date }
            val leading = (m.atDay(1).dayOfWeek.value - 1) // 周一起始
            val cells = buildList {
                repeat(leading) { add(CalendarCell(null)) }
                (1..m.lengthOfMonth()).forEach { day ->
                    val date = m.atDay(day)
                    val dayRecords = byDate[date].orEmpty()
                    val ot = dayRecords.firstOrNull { it.type == com.mdot.app.domain.model.RecordType.OT }
                    val leave = dayRecords.firstOrNull { it.type == com.mdot.app.domain.model.RecordType.LEAVE }
                    add(
                        CalendarCell(
                            date = date,
                            otMinutes = ot?.durationMinutes ?: 0,
                            leaveMinutes = leave?.durationMinutes ?: 0,
                            shiftName = ot?.shiftName ?: leave?.shiftName,
                            holiday = holidayRepo.infoFor(date),
                        )
                    )
                }
            }
            // 综合工时：本月标准 = 应出勤天数 × 8h（10 文档 §4）；单日小结用同一标准（普通工时不即时计酬）
            val monthStart = m.atDay(1)
            val monthEnd = m.atEndOfMonth()
            val standardMinutes =
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(monthStart, monthEnd)
                    com.mdot.app.domain.CycleCalculator.standardMinutesFor(
                        monthStart, monthEnd, workdays, holidays, makeups,
                    )
                } else null
            val out = PayrollCalculator.summarize(
                PayrollCalculator.Input(
                    salary,
                    records.map { it.toCalcLite() },
                    tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                    standardMinutes = standardMinutes,
                )
            )
            val selCell = cells.firstOrNull { it.date == selDate }
            val selRecords = byDate[selDate].orEmpty()
            val selOut = if (selRecords.isNotEmpty()) PayrollCalculator.summarize(
                PayrollCalculator.Input(
                    salary,
                    selRecords.map { it.toCalcLite() },
                    tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                    standardMinutes = standardMinutes,
                )
            ) else null
            val isComp = salary.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE
            CalendarUiState(
                month = m,
                cells = cells,
                monthOtMinutes = if (isComp) out.otMinutes + out.holidayWorkMinutes else out.otMinutes,
                monthLeaveMinutes = out.leaveMinutes,
                monthOtPayCents = if (salary.hasBaseSalary || salary.mode == com.mdot.app.domain.model.SalaryMode.MANUAL) out.otPayCents else null,
                recordedDays = byDate.size,
                selectedDate = selDate,
                selectedOtMinutes = selCell?.otMinutes ?: 0,
                selectedLeaveMinutes = selCell?.leaveMinutes ?: 0,
                selectedOtPayCents = selOut?.let { if (salary.hasBaseSalary || salary.mode == com.mdot.app.domain.model.SalaryMode.MANUAL) it.otPayCents else null },
                selectedShiftName = selCell?.shiftName,
                workSystem = salary.workSystem,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init {
        // 恢复缓存后回到当月
    }

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun goToday() {
        month.value = YearMonth.now()
        selectedDate.value = LocalDate.now()
        recordSheet.setCalendarSelectedDate(LocalDate.now())
    }

    fun initMonth(initial: String?) {
        val parsed = initial?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (parsed != null) month.value = parsed
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        recordSheet.setCalendarSelectedDate(date)
    }
}

/** 日历页（03 文档 §5.3 线框） */
@Composable
fun CalendarScreen(
    initialMonth: String? = null,
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    vm: CalendarViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(initialMonth) { vm.initMonth(initialMonth) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .contentBottomPadding(showBottomBar = !canBack)
            .padding(horizontal = Spacing.page),
    ) {
        if (canBack) {
            JiabanTopBar(title = stringResource(R.string.calendar_title), onBack = onBack)
        } else {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + Spacing.xs))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month))
            }
            AnimatedContent(
                targetState = state.month,
                transitionSpec = {
                    val forward = targetState > initialState
                    val spec = tween<IntOffset>(260)
                    (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(tween(160)))
                },
                modifier = Modifier.weight(1f),
                label = "monthTitle",
            ) { m ->
                Text(
                    stringResource(R.string.calendar_month_title, m.year, m.monthValue),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            TextButton(onClick = vm::goToday) { Text(stringResource(R.string.calendar_back_today)) }
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
            }
        }

        Spacer(Modifier.height(Spacing.s))
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
        Spacer(Modifier.height(Spacing.xs))

        val dragThreshold = with(LocalDensity.current) { 90.dp.toPx() }
        Column(
            Modifier
                .fillMaxWidth()
                // 左右滑动切换月份（与 ‹ › 按钮等效）；只消费横向拖动，不影响点选与页面纵向滚动
                .pointerInput(dragThreshold) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (totalDrag < -dragThreshold) vm.nextMonth()
                            else if (totalDrag > dragThreshold) vm.prevMonth()
                        },
                    )
                }
        ) {
            // 月切换方向感动画：旧月滑出、新月滑入（滑动与按钮共用）。
            // 目标态只含月+格子：选日期不改变相等性，不会误触发切换动画
            AnimatedContent(
                targetState = MonthGridData(state.month, state.cells),
                transitionSpec = {
                    val forward = targetState.month > initialState.month
                    val spec = tween<IntOffset>(260)
                    (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(tween(160)))
                },
                label = "monthGrid",
            ) { page ->
                val today = LocalDate.now()
                Column {
                    page.cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { cell ->
                                CalendarCellView(
                                    cell = cell,
                                    isToday = cell.date == today,
                                    isSelected = cell.date == state.selectedDate,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.95f),
                                    onClick = { cell.date?.let { vm.selectDate(it) } },
                                )
                            }
                            if (week.size < 7) repeat(7 - week.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))
        SectionCard {
            Column {
                Text(
                    stringResource(R.string.calendar_day_summary, TimeUtils.mdCn(state.selectedDate)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.s))
                Row {
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
                    )
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
                Text(stringResource(R.string.calendar_month_summary), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Row {
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
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.calendar_month_recorded, state.month.monthValue, state.recordedDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalendarCellView(
    cell: CalendarCell,
    isToday: Boolean,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    // 选中态与记加班弹窗时长格同款：深主题色描边 + 浅主题色填充，颜色缓切过渡
    val colorSpec = tween<Color>(Duration.normal)
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected || isToday -> colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "calBg",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else Color.Transparent,
        animationSpec = colorSpec,
        label = "calStroke",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected || isToday -> colorScheme.onPrimaryContainer
            cell.holiday?.kind == com.mdot.app.domain.model.HolidayKind.HOLIDAY -> colorScheme.secondary
            else -> colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "calText",
    )
    val otColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
        animationSpec = colorSpec,
        label = "calOt",
    )
    // 指示圈"绽放"：弹簧缩放 + 淡入淡出（时长格是两色块交叉渐变，日历从透明出现，
    // 纯色变会显得生硬，叠加弹簧缩放后与时长格手感一致）
    val indicatorScale by animateFloatAsState(
        targetValue = if (isSelected || isToday) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "calBloom",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isSelected || isToday) 1f else 0f,
        animationSpec = tween(Duration.normal),
        label = "calBloomAlpha",
    )
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radius.button)
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.93f)
            .padding(1.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.date != null) {
            // 选中/今天指示圈（填充 + 描边）在文字下层绽放，文字只做颜色缓切
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                        alpha = indicatorAlpha
                    }
                    .background(bgColor, shape)
                    .border(1.5.dp, strokeColor, shape)
            )
            // 涟漪层夹在指示圈与文字之间： indication 画在本层 children 之下，
            // 若挂在外层容器会被不透明指示圈（尤其绽放动画中）整个盖住
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    cell.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                )
                Spacer(Modifier.height(1.dp))
                if (cell.otMinutes > 0) {
                    val hours = cell.otMinutes / 60.0
                    val text = "+" + String.format(java.util.Locale.US, "%.1f", hours).trimEnd('0').trimEnd('.')
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = otColor,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (cell.leaveMinutes > 0) {
                    Dot(colorScheme.error)
                } else if (cell.holiday != null) {
                    Dot(colorScheme.tertiary, small = true)
                } else {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color, small: Boolean = false) {
    Box(
        Modifier
            .size(if (small) 4.dp else 6.dp)
            .background(color, CircleShape)
    )
}
