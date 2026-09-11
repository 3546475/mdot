package com.mdot.app.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.layout.wrapContentWidth
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.mdot.app.domain.model.OtDraft
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.toCalcLite
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.HolidayInfo
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.DurationGrid
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

data class CalendarCell(
    val date: LocalDate?,
    val otMinutes: Int = 0,
    val leaveMinutes: Int = 0,
    val shiftName: String? = null,
    val holiday: HolidayInfo? = null,
    /** 工地记工：当日工数×1000（非 SITE=0） */
    val worksMilli: Long = 0,
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
    /** 工地记工：选中日工数×1000 / 本月工数×1000（非 SITE=0） */
    val selectedWorksMilli: Long = 0,
    val monthWorksMilli: Long = 0,
)

/** 小结卡工时行标签随制度变化（综合工时=上班、小时工=工作、标准=加班） */
private fun otNoun(workSystem: com.mdot.app.domain.model.WorkSystem): Int = when (workSystem) {
    com.mdot.app.domain.model.WorkSystem.HOURLY -> R.string.calendar_ot_hourly
    com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> R.string.calendar_ot_comp
    com.mdot.app.domain.model.WorkSystem.STANDARD -> R.string.calendar_ot
    com.mdot.app.domain.model.WorkSystem.SITE -> R.string.site_cycle_label
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
    private val shiftRepo: com.mdot.app.core.repository.ShiftRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    // ---- 长按多选批量记加班（非 SITE 制度；未来日期不可选） ----
    private val batchSelecting = MutableStateFlow(false)
    private val batchSelected = MutableStateFlow<Set<LocalDate>>(emptySet())
    val isBatchSelecting: StateFlow<Boolean> = batchSelecting
    val batchSelection: StateFlow<Set<LocalDate>> = batchSelected

    /** 长按进入多选：以该日期为首项（工地制度/未来日期不进入） */
    fun startBatchSelect(date: LocalDate) {
        if (uiState.value.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) return
        if (date.isAfter(LocalDate.now())) return
        batchSelecting.value = true
        batchSelected.value = setOf(date)
    }

    /** 多选中点按：切换选中（未来日期不可选） */
    fun toggleBatchSelect(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        batchSelected.update { if (date in it) it - date else it + date }
    }

    fun exitBatchSelect() {
        batchSelecting.value = false
        batchSelected.value = emptySet()
    }

    /**
     * 批量写入加班：统一时长；班次取首个可见班次（同单记默认），档位按各日期自动判定；
     * 已有加班记录的日期按 saveOt 语义覆盖（同单记编辑）。未来日期跳过。
     */
    fun saveBatch(durationMinutes: Int) {
        val dates = batchSelected.value.filter { !it.isAfter(LocalDate.now()) }.sorted()
        if (durationMinutes !in 1..RecordRepository.MAX_MINUTES || dates.isEmpty()) return
        viewModelScope.launch {
            val workdays = settings.workdaysFlow.first()
            val shift = shiftRepo.getAll().firstOrNull { !it.hidden }
            dates.forEach { date ->
                recordRepo.saveOt(
                    OtDraft(
                        date = date,
                        shiftId = shift?.id,
                        shiftName = shift?.name,
                        durationMinutes = durationMinutes,
                        tier = holidayRepo.tierFor(date, workdays),
                        tierSource = TierSource.AUTO,
                    )
                )
            }
            exitBatchSelect()
        }
    }

    val uiState: StateFlow<CalendarUiState> = month.flatMapLatest { m ->
        selectedDate.flatMapLatest { selDate ->
            settings.salaryFlow.flatMapLatest { salary ->
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                    siteStateFlow(m, selDate)
                } else {
                    normalStateFlow(m, selDate, salary)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** 普通制度（标准/小时/综合）：每日加班/请假记录 + PayrollCalculator 汇总 */
    private fun normalStateFlow(
        m: YearMonth,
        selDate: LocalDate,
        salary: com.mdot.app.domain.model.SalaryConfig,
    ) = combine(
        recordRepo.observeRange(m.atDay(1), m.atEndOfMonth()),
        settings.workdaysFlow,
    ) { records, workdays ->
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

    /** 工地记工：每日出勤工数（workMinutes/baseMinutes 快照）+ 加班分钟；无 PayrollCalculator 汇总 */
    private fun siteStateFlow(
        m: YearMonth,
        selDate: LocalDate,
    ) = kotlinx.coroutines.flow.flow<CalendarUiState> {
        val pid = siteRepo.currentProjectId()
        siteRepo.observeAttendance(pid, m.atDay(1), m.atEndOfMonth()).collect { atts ->
            val byDate = atts.groupBy { java.time.LocalDate.parse(it.date) }
            val leading = (m.atDay(1).dayOfWeek.value - 1)
            val cells = buildList {
                repeat(leading) { add(CalendarCell(null)) }
                (1..m.lengthOfMonth()).forEach { day ->
                    val date = m.atDay(day)
                    val dayAtts = byDate[date].orEmpty()
                    val workAtt = dayAtts.firstOrNull { it.dayStatus == "WORK" }
                    val worksMilli = dayAtts.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) }
                    val otMinutes = dayAtts.sumOf { it.otMinutes }
                    add(
                        CalendarCell(
                            date = date,
                            otMinutes = otMinutes,
                            worksMilli = worksMilli,
                            shiftName = workAtt?.let { if (worksMilli % 1000L == 0L) (worksMilli / 1000L).toString() else String.format(java.util.Locale.US, "%.1f", worksMilli / 1000f) },
                            holiday = holidayRepo.infoFor(date),
                        )
                    )
                }
            }
            val selCell = cells.firstOrNull { it.date == selDate }
            emit(
                CalendarUiState(
                    month = m,
                    cells = cells,
                    recordedDays = byDate.size,
                    selectedDate = selDate,
                    selectedOtMinutes = selCell?.otMinutes ?: 0,
                    selectedShiftName = selCell?.shiftName,
                    selectedWorksMilli = selCell?.worksMilli ?: 0,
                    monthOtMinutes = atts.sumOf { it.otMinutes },
                    monthWorksMilli = atts.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) },
                    workSystem = com.mdot.app.domain.model.WorkSystem.SITE,
                )
            )
        }
    }


    init {
        // 恢复缓存后回到当月
    }

    fun prevMonth() {
        exitBatchSelect()
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        exitBatchSelect()
        month.value = month.value.plusMonths(1)
    }

    fun goToday() {
        exitBatchSelect()
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
        // ---- 手机：单列（原布局） ----
        Column(
            Modifier
                .fillMaxSize()
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

    if (showBatchDialog) {
        BatchOtDialog(
            selectedCount = batchSelected.size,
            overwriteCount = overwriteCount,
            onConfirm = { minutes ->
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::prevMonth) {
            Icon(painterResource(R.drawable.ic_ms_keyboard_arrow_left), contentDescription = stringResource(R.string.calendar_prev_month))
        }
        AnimatedContent(
            targetState = state.month,
            transitionSpec = {
                val forward = targetState > initialState
                val spec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))) togetherWith
                    (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)))
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
            Icon(painterResource(R.drawable.ic_ms_keyboard_arrow_right), contentDescription = stringResource(R.string.calendar_next_month))
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

/** 月网格：左右滑动切月（阈值 50dp）+ 方向感滑动动画（contentKey 只认月份，同月改记录不重播）；
 *  长按日期进入多选（多选中点按切换选中，切月退出） */
@Composable
private fun MonthGrid(state: CalendarUiState, vm: CalendarViewModel) {
    val batchSelecting by vm.isBatchSelecting.collectAsStateWithLifecycle()
    val batchSelected by vm.batchSelection.collectAsStateWithLifecycle()
    val dragThreshold = with(LocalDensity.current) { 50.dp.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
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
        AnimatedContent(
            targetState = MonthGridData(state.month, state.cells),
            contentKey = { it.month },
            transitionSpec = {
                val forward = targetState.month > initialState.month
                val spec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))) togetherWith
                    (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)))
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
                                // 多选模式下抑制单选高亮（多选格有自己的填充样式）
                                isSelected = !batchSelecting && cell.date == state.selectedDate,
                                isSite = state.workSystem == com.mdot.app.domain.model.WorkSystem.SITE,
                                isBatchSelected = batchSelecting && cell.date != null && cell.date in batchSelected,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.95f),
                                onClick = {
                                    cell.date?.let { d ->
                                        if (batchSelecting) vm.toggleBatchSelect(d) else vm.selectDate(d)
                                    }
                                },
                                onLongClick = {
                                    if (!batchSelecting) cell.date?.let { vm.startBatchSelect(it) }
                                },
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
            Text(stringResource(R.string.calendar_month_summary), style = MaterialTheme.typography.titleSmall)
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
                    )
                }
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
            Button(
                onClick = onSelectConfirm,
                enabled = selectedCount > 0,
            ) { Text(stringResource(R.string.calendar_batch_title)) }
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
    androidx.compose.material3.AlertDialog(
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
            TextButton(
                onClick = { onConfirm(minutes) },
                enabled = minutes in 1..1440,
            ) { Text(stringResource(R.string.calendar_batch_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_batch_cancel)) }
        },
    )
}

@Composable
private fun CalendarCellView(
    cell: CalendarCell,
    isToday: Boolean,
    isSelected: Boolean = false,
    isSite: Boolean = false,
    isBatchSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    // 选中态与记加班弹窗时长格同款：深主题色描边 + 浅主题色填充，颜色缓切过渡；
    // 多选格 = 半透明填充 + 描边（与单选/今天区分层级）
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bgColor by animateColorAsState(
        targetValue = when {
            isBatchSelected -> colorScheme.primaryContainer.copy(alpha = 0.55f)
            isSelected || isToday -> colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "calBg",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (isBatchSelected || isSelected) colorScheme.primary else Color.Transparent,
        animationSpec = colorSpec,
        label = "calStroke",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isBatchSelected || isSelected || isToday -> colorScheme.onPrimaryContainer
            cell.holiday?.kind == com.mdot.app.domain.model.HolidayKind.HOLIDAY -> colorScheme.secondary
            else -> colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "calText",
    )
    val otColor by animateColorAsState(
        targetValue = if (isBatchSelected || isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
        animationSpec = colorSpec,
        label = "calOt",
    )
    // 指示圈"绽放"：弹簧缩放 + 淡入淡出（时长格是两色块交叉渐变，日历从透明出现，
    // 纯色变会显得生硬，叠加弹簧缩放后与时长格手感一致）
    val bloomOn = isBatchSelected || isSelected || isToday
    val indicatorScale by animateFloatAsState(
        targetValue = if (bloomOn) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "calBloom",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (bloomOn) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
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
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
                    )
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
                if (isSite && cell.worksMilli > 0) {
                    // 工地：当日工数（1 工 / 1.5 工）
                    val works = cell.worksMilli / 1000f
                    val wText = if (works % 1f == 0f) works.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", works)
                    Text(
                        stringResource(R.string.calendar_site_works_badge, wText),
                        style = MaterialTheme.typography.labelSmall,
                        color = otColor,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (cell.otMinutes > 0) {
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
