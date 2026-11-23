package com.mdot.app.feature.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.holiday.CalendarDayUseCase
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.model.OtDraft
import com.mdot.app.domain.model.TierSource
import com.mdot.app.domain.toCalcLite
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.ShiftDefaults
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    private val dayLabels: CalendarDayUseCase,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
    private val shiftRepo: com.mdot.app.core.repository.ShiftRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    /**
     * 外观设置「隐藏农历日期」（默认关）→ 日历格右槽是否回落显示农历。
     * 只有这一项变化才触发格子重算：外观里切主题/配色/弹层背景不会白白重算整个月与工资汇总。
     */
    private val showLunar: Flow<Boolean> =
        settings.appearanceFlow.map { !it.hideLunarDate }.distinctUntilChanged()

    // ---- 长按多选批量记加班（非 SITE 制度；未来日期不可选） ----
    private val batchSelecting = MutableStateFlow(false)
    private val batchSelected = MutableStateFlow<Set<LocalDate>>(emptySet())
    val isBatchSelecting: StateFlow<Boolean> = batchSelecting
    val batchSelection: StateFlow<Set<LocalDate>> = batchSelected

    // T1-2：批量保存结果反馈（docs/15；文案按硬规则 10 例外走 VM 消息硬编码）
    private val message = MutableStateFlow<String?>(null)
    val messageFlow: StateFlow<String?> = message
    fun clearMessage() {
        message.value = null
    }

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
     * 批量写入加班：统一时长；班次取**默认班次**（`ShiftDefaults`：排第一个的可见班次，同单记预选），
     * 档位按各日期自动判定；
     * 已有加班记录的日期按 saveOt 语义覆盖（同单记编辑）。未来日期跳过。
     */
    fun saveBatch(durationMinutes: Int) {
        val dates = batchSelected.value.filter { !it.isAfter(LocalDate.now()) }.sorted()
        if (durationMinutes !in 1..RecordRepository.MAX_MINUTES || dates.isEmpty()) return
        viewModelScope.launch {
            val workdays = settings.workdaysFlow.first()
            val shift = ShiftDefaults.of(shiftRepo.getAll())
            var success = 0
            dates.forEach { date ->
                val r = recordRepo.saveOt(
                    OtDraft(
                        date = date,
                        shiftId = shift?.id,
                        shiftName = shift?.name,
                        durationMinutes = durationMinutes,
                        tier = holidayRepo.tierFor(date, workdays),
                        tierSource = TierSource.AUTO,
                    )
                )
                if (r is AppResult.Success) success++
            }
            message.value =
                if (success == dates.size) "已为 $success 天记录加班"
                else "已为 $success/${dates.size} 天记录加班"
            exitBatchSelect()
        }
    }

    /**
     * 展示口径三件套：展示月（收敛到「不越今天」的上限内）+ 选中日 + 考勤周期设置。
     *
     * ⚠️ 展示月**必须在这里收敛**（[com.mdot.app.domain.CycleCalculator.clampDisplayMonth]）：
     * 切到周期视图时 [month] 仍是今天所在自然月，而含今天的周期可能始于上月
     * （起始日 26、今天 10/16 ⇒ 本期 9/26–10/25），不收敛就会一进来整屏未来日期。
     */
    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        settings.cycleAnchorDayFlow,
        settings.calendarCycleViewFlow,
    ) { m, selDate, anchor, byCycle ->
        CalendarQuery(
            month = com.mdot.app.domain.CycleCalculator.clampDisplayMonth(
                m, LocalDate.now(), anchor, byCycle,
            ),
            selectedDate = selDate,
            anchorDay = anchor,
            byCycle = byCycle,
        )
    }.flatMapLatest { q ->
        settings.salaryFlow.flatMapLatest { salary ->
            if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                siteStateFlow(q)
            } else {
                normalStateFlow(q, salary)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** 普通制度（标准/小时/综合）：每日加班/请假记录 + PayrollCalculator 汇总（区间 = 本次展示区间） */
    private fun normalStateFlow(
        q: CalendarQuery,
        salary: com.mdot.app.domain.model.SalaryConfig,
    ) = run {
        val range = com.mdot.app.domain.CycleCalculator.calendarRange(q.month, q.anchorDay, q.byCycle)
        combine(
            recordRepo.observeRange(range.from, range.to),
            settings.workdaysFlow,
            showLunar,
        ) { records, workdays, showLunarDate ->
            val byDate = records.groupBy { it.date }
            val leading = com.mdot.app.domain.CycleCalculator.leadingBlanks(range) // 周一起始
            val cells = buildList {
                repeat(leading) { add(CalendarCell(null)) }
                // 逐日铺满整段区间（周期视图下会跨自然月，故不能再用 month.lengthOfMonth）
                for (offset in 0 until range.days) {
                    val date = range.from.plusDays(offset.toLong())
                    val dayRecords = byDate[date].orEmpty()
                    val ot = dayRecords.firstOrNull { it.type == com.mdot.app.domain.model.RecordType.OT }
                    val leave = dayRecords.firstOrNull { it.type == com.mdot.app.domain.model.RecordType.LEAVE }
                    add(
                        CalendarCell(
                            date = date,
                            otMinutes = ot?.durationMinutes ?: 0,
                            leaveMinutes = leave?.durationMinutes ?: 0,
                            shiftName = ot?.shiftName ?: leave?.shiftName,
                            label = dayLabels.labelFor(date, showLunarDate),
                        )
                    )
                }
            }
            // 综合工时：本期标准 = 应出勤天数 × 8h（10 文档 §4）；单日小结用同一标准（普通工时不即时计酬）
            val standardMinutes =
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(range.from, range.to)
                    com.mdot.app.domain.CycleCalculator.standardMinutesFor(
                        range.from, range.to, workdays, holidays, makeups,
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
            val selCell = cells.firstOrNull { it.date == q.selectedDate }
            val selRecords = byDate[q.selectedDate].orEmpty()
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
                month = q.month,
                cells = cells,
                monthOtMinutes = if (isComp) out.otMinutes + out.holidayWorkMinutes else out.otMinutes,
                monthLeaveMinutes = out.leaveMinutes,
                monthOtPayCents = if (salary.hasBaseSalary || salary.mode == com.mdot.app.domain.model.SalaryMode.MANUAL) out.otPayCents else null,
                recordedDays = byDate.size,
                selectedDate = q.selectedDate,
                selectedOtMinutes = selCell?.otMinutes ?: 0,
                selectedLeaveMinutes = selCell?.leaveMinutes ?: 0,
                selectedOtPayCents = selOut?.let { if (salary.hasBaseSalary || salary.mode == com.mdot.app.domain.model.SalaryMode.MANUAL) it.otPayCents else null },
                selectedShiftName = selCell?.shiftName,
                workSystem = salary.workSystem,
                period = range,
                isCycleView = q.byCycle,
                canGoNext = q.month.isBefore(
                    com.mdot.app.domain.CycleCalculator.displayAnchorCap(LocalDate.now(), q.anchorDay, q.byCycle)
                ),
            )
        }
    }

    /** 工地记工：每日出勤工数（workMinutes/baseMinutes 快照）+ 加班分钟；无 PayrollCalculator 汇总 */
    private fun siteStateFlow(q: CalendarQuery) = kotlinx.coroutines.flow.flow<CalendarUiState> {
        val pid = siteRepo.currentProjectId()
        val range = com.mdot.app.domain.CycleCalculator.calendarRange(q.month, q.anchorDay, q.byCycle)
        combine(
            siteRepo.observeAttendance(pid, range.from, range.to),
            siteRepo.observePieceWorks(pid, range.from, range.to),
            siteRepo.observeAdvances(pid, range.from, range.to),
            showLunar,
        ) { atts, pieces, advances, showLunarDate ->
            val byDate = atts.groupBy { java.time.LocalDate.parse(it.date) }
            val piecesByDate = pieces.groupBy { java.time.LocalDate.parse(it.date) }
            val advancesByDate = advances.groupBy { java.time.LocalDate.parse(it.date) }
            val leading = com.mdot.app.domain.CycleCalculator.leadingBlanks(range)
            val cells = buildList {
                repeat(leading) { add(CalendarCell(null)) }
                for (offset in 0 until range.days) {
                    val date = range.from.plusDays(offset.toLong())
                    val dayAtts = byDate[date].orEmpty()
                    val workAtt = dayAtts.firstOrNull { it.dayStatus == "WORK" }
                    val worksMilli = dayAtts.sumOf { com.mdot.app.domain.SitePayCalculator.worksMilliOf(it.workMinutes, it.baseMinutes) }
                    val otMinutes = dayAtts.sumOf { it.otMinutes }
                    add(
                        CalendarCell(
                            date = date,
                            otMinutes = otMinutes,
                            worksMilli = worksMilli,
                            shiftName = workAtt?.let { if (worksMilli % 1000L == 0L) (worksMilli / 1000L).toString() else String.format(java.util.Locale.US, "%.1f", worksMilli / 1000f) },
                            label = dayLabels.labelFor(date, showLunarDate),
                            badges = com.mdot.app.domain.SiteDayBadges.of(
                                attendances = dayAtts,
                                pieces = piecesByDate[date].orEmpty(),
                                advances = advancesByDate[date].orEmpty(),
                            ),
                        )
                    )
                }
            }
            val selCell = cells.firstOrNull { it.date == q.selectedDate }
            CalendarUiState(
                month = q.month,
                cells = cells,
                recordedDays = byDate.size,
                selectedDate = q.selectedDate,
                selectedOtMinutes = selCell?.otMinutes ?: 0,
                selectedShiftName = selCell?.shiftName,
                selectedWorksMilli = selCell?.worksMilli ?: 0,
                monthOtMinutes = atts.sumOf { it.otMinutes },
                monthWorksMilli = atts.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) },
                workSystem = com.mdot.app.domain.model.WorkSystem.SITE,
                period = range,
                isCycleView = q.byCycle,
                canGoNext = q.month.isBefore(
                    com.mdot.app.domain.CycleCalculator.displayAnchorCap(LocalDate.now(), q.anchorDay, q.byCycle)
                ),
            )
        }.collect { emit(it) }
    }


    init {
        // 恢复缓存后回到当月
    }

    fun prevMonth() {
        exitBatchSelect()
        viewModelScope.launch {
            // 步进基准是**实际展示的那个月**（可能被上限收敛过），不是 month 的原始值——
            // 否则收敛期间点 ‹ 会拿回被夹掉的值、看起来"点一下没反应"（同 docs/11 074 的锚点月坑）
            month.value = shownAnchorMonth().minusMonths(1)
        }
    }

    fun nextMonth() {
        // 未来不可记（未来日期不可选）→ 不允许切到未来的月/周期；网格横滑到尽头与 › 按钮同此规则
        viewModelScope.launch {
            val shown = shownAnchorMonth()
            if (!shown.isBefore(anchorMonthCap())) return@launch
            exitBatchSelect()
            month.value = shown.plusMonths(1)
        }
    }

    fun goToday() {
        exitBatchSelect()
        viewModelScope.launch {
            val today = LocalDate.now()
            // 「回到今天」= 回到今天所在区间：自然月视图回今天所在月，周期视图回含今天的周期的锚点月
            month.value = anchorMonthCap()
            selectedDate.value = today
            recordSheet.setCalendarSelectedDate(today)
        }
    }

    /**
     * 实际展示的锚点月（与 [uiState] 用同一个收敛函数，见 [CalendarQuery] 注释）。
     * 读盘取真值而非读 stateIn 的 `.value`（硬规则 15：无订阅者时 `.value` 是种子值）。
     */
    private suspend fun shownAnchorMonth(): YearMonth =
        com.mdot.app.domain.CycleCalculator.clampDisplayMonth(
            month.value,
            LocalDate.now(),
            settings.cycleAnchorDayFlow.first(),
            settings.calendarCycleViewFlow.first(),
        )

    /** 展示区间的「今天上限」锚点月（前进方向的边界，也是「回到今天」的目标月） */
    private suspend fun anchorMonthCap(): YearMonth =
        com.mdot.app.domain.CycleCalculator.displayAnchorCap(
            LocalDate.now(),
            settings.cycleAnchorDayFlow.first(),
            settings.calendarCycleViewFlow.first(),
        )

    fun initMonth(initial: String?) {
        val parsed = initial?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (parsed != null) month.value = parsed
    }

    fun selectDate(date: LocalDate) {
        // 未来日期不可选（与网格置灰一致）；多选入口 startBatchSelect/toggleBatchSelect 已有同款守卫
        if (date.isAfter(LocalDate.now())) return
        selectedDate.value = date
        recordSheet.setCalendarSelectedDate(date)
    }
}
