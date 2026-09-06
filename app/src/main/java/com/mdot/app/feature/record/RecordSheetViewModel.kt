package com.mdot.app.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.repository.ShiftRepository
import com.mdot.app.core.util.AppError
import com.mdot.app.core.util.onFailure
import com.mdot.app.core.util.onSuccess
import com.mdot.app.domain.model.DailyRecord
import com.mdot.app.domain.model.LeaveDraft
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.OtDraft
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.Shift
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.TierSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** 时长输入模式：HOURS=纯小时网格（步进 0.5）；MINUTES=时分滚轮（精确到分钟） */
enum class DurationInputMode { HOURS, MINUTES }

data class SheetUiState(
    val date: LocalDate = LocalDate.now(),
    val tab: RecordType = RecordType.OT,
    /** 时长输入模式（默认纯小时网格，可切换到时分滚轮） */
    val durationMode: DurationInputMode = DurationInputMode.HOURS,
    /** 分钟模式下滚轮暂存的时长（分钟）；切到分钟模式时从 durationMinutes 初始化 */
    val pendingMinutes: Int = 0,
    val shifts: List<Shift> = emptyList(),
    val selectedShiftId: Long? = null,
    val selectedShiftName: String? = null,
    /** 选中的时长（十进制小时文本，如 "2.5"）；null = 未选择 */
    val hoursText: String? = null,
    /** 生效档位（含手动补改） */
    val tier: RateTier = RateTier.WEEKDAY,
    val tierManual: Boolean = false,
    /** 该日期的自动判定档位 */
    val autoTier: RateTier = RateTier.WEEKDAY,
    val toCompMinutes: Int = 0,
    val leaveType: LeaveType = LeaveType.PERSONAL,
    val note: String = "",
    val editing: Boolean = false,
    val salary: SalaryConfig = SalaryConfig(),
    val errorText: String? = null,
) {
    fun durationMinutes(): Int {
        if (durationMode == DurationInputMode.MINUTES) {
            return pendingMinutes.coerceIn(0, 1440)
        }
        return hoursText?.toDoubleOrNull()
            ?.let { (it * 60).roundToInt().coerceIn(0, 1440) }
            ?: 0
    }

    /** 选中时长的小时数（未选择时为 null），供网格高亮 */
    fun hoursValue(): Double? = hoursText?.toDoubleOrNull()?.takeIf { it > 0.0 }
}

@HiltViewModel
class RecordSheetViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
    shiftRepo: ShiftRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    private val controller: RecordSheetController,
) : ViewModel() {

    private val _state = MutableStateFlow(SheetUiState())
    val state: StateFlow<SheetUiState> = _state.asStateFlow()

    val visibleShifts: StateFlow<List<Shift>> = shiftRepo.observeAll()
        .map { list -> list.filter { !it.hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            settings.salaryFlow.collect { s -> _state.update { it.copy(salary = s) } }
        }
    }

    private var boundToken: Long = -1
    private var existingRecord: DailyRecord? = null

    fun bind(request: RecordRequest) {
        if (boundToken == request.token) return
        boundToken = request.token
        viewModelScope.launch {
            val workdays = settings.workdaysFlow.first()
            val autoTier = holidayRepo.tierFor(request.date, workdays)
            val records = recordRepo.observeByDate(request.date).first()
            existingRecord = records.firstOrNull { it.type == request.tab }
            val existing = existingRecord

            _state.value = if (existing != null) {
                SheetUiState(
                    date = request.date,
                    tab = request.tab,
                    durationMode = DurationInputMode.HOURS,
                    pendingMinutes = existing.durationMinutes,
                    shifts = _state.value.shifts,
                    selectedShiftId = existing.shiftId,
                    selectedShiftName = existing.shiftName,
                    hoursText = minutesToHoursText(existing.durationMinutes),
                    tier = existing.tier ?: autoTier,
                    tierManual = existing.tierSource == TierSource.MANUAL,
                    autoTier = autoTier,
                    toCompMinutes = existing.toCompMinutes,
                    leaveType = existing.leaveType ?: LeaveType.PERSONAL,
                    note = existing.note ?: "",
                    editing = true,
                    salary = _state.value.salary,
                )
            } else {
                SheetUiState(
                    date = request.date,
                    tab = request.tab,
                    durationMode = DurationInputMode.HOURS,
                    pendingMinutes = 0,
                    shifts = _state.value.shifts,
                    selectedShiftId = _state.value.shifts.firstOrNull()?.id,
                    selectedShiftName = _state.value.shifts.firstOrNull()?.name,
                    hoursText = null,
                    tier = autoTier,
                    tierManual = false,
                    autoTier = autoTier,
                    salary = _state.value.salary,
                )
            }
        }
    }

    /** 分钟数 → 十进制小时文本（150 → "2.5"，60 → "1"，45 → "0.75"） */
    private fun minutesToHoursText(minutes: Int): String =
        String.format(java.util.Locale.US, "%.2f", minutes / 60.0)
            .trimEnd('0').trimEnd('.')

    // ---- 输入事件 ----

    fun onDateChange(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            val workdays = settings.workdaysFlow.first()
            val auto = holidayRepo.tierFor(date, workdays)
            _state.update {
                it.copy(
                    date = date,
                    autoTier = auto,
                    tier = if (it.tierManual) it.tier else auto,
                )
            }
        }
    }

    fun onTabChange(tab: RecordType) = _state.update { it.copy(tab = tab) }

    /** 切换时长输入模式：进入分钟模式时把当前时长同步给滚轮暂存值 */
    fun onDurationModeChange(mode: DurationInputMode) = _state.update {
        if (mode == it.durationMode) it
        else if (mode == DurationInputMode.MINUTES) it.copy(
            durationMode = mode,
            pendingMinutes = it.durationMinutes(),
        )
        else it.copy(
            durationMode = mode,
            // 滚轮选的时长回灌为十进制小时文本，供网格高亮/保存
            hoursText = if (it.pendingMinutes > 0) minutesToHoursText(it.pendingMinutes) else null,
            errorText = null,
        )
    }

    /** 分钟模式：滚轮暂存时长变化（小时、分钟两列合成） */
    fun onPendingMinutes(minutes: Int) = _state.update {
        if (it.durationMode == DurationInputMode.MINUTES) it.copy(pendingMinutes = minutes.coerceIn(0, 1440))
        else it
    }

    fun onShiftSelect(shift: Shift) = _state.update {
        it.copy(selectedShiftId = shift.id, selectedShiftName = shift.name)
    }

    fun onPresetHours(hours: Double) = _state.update {
        it.copy(
            hoursText = hours.toString(),
            pendingMinutes = (hours * 60).roundToInt(),
            errorText = null,
        )
    }

    /** 自定义输入格提交：合法值归一化存储，非法/零值视为未选择 */
    fun onCustomHours(raw: String) = _state.update {
        val minutes = raw.toDoubleOrNull()
            ?.takeIf { v -> v > 0.0 && v <= 24.0 }
            ?.let { v -> (v * 60).roundToInt().coerceIn(1, 1440) }
        when (minutes) {
            null -> it.copy(hoursText = null, pendingMinutes = 0)
            else -> it.copy(
                hoursText = minutesToHoursText(minutes),
                pendingMinutes = minutes,
                errorText = null,
            )
        }
    }

    fun onTierSelect(tier: RateTier) = _state.update { it.copy(tier = tier, tierManual = true) }

    fun onTierReset() = _state.update { it.copy(tier = it.autoTier, tierManual = false) }

    fun onToCompDelta(deltaMinutes: Int) = _state.update {
        val max = it.durationMinutes() / 30 * 30
        it.copy(toCompMinutes = (it.toCompMinutes + deltaMinutes).coerceIn(0, max))
    }

    fun onLeaveType(type: LeaveType) = _state.update { it.copy(leaveType = type) }

    fun onNote(text: String) = _state.update { it.copy(note = text.take(200)) }

    // ---- 保存 / 删除 ----

    fun save() {
        val s = _state.value
        val minutes = s.durationMinutes()
        viewModelScope.launch {
            val result = if (s.tab == RecordType.OT) {
                recordRepo.saveOt(
                    OtDraft(
                        date = s.date,
                        shiftId = s.selectedShiftId,
                        shiftName = s.selectedShiftName,
                        durationMinutes = minutes,
                        tier = s.tier,
                        tierSource = if (s.tierManual) TierSource.MANUAL else TierSource.AUTO,
                        toCompMinutes = s.toCompMinutes.coerceAtMost(minutes),
                        note = s.note.takeIf { it.isNotBlank() },
                    )
                )
            } else {
                recordRepo.saveLeave(
                    LeaveDraft(
                        date = s.date,
                        shiftId = s.selectedShiftId,
                        shiftName = s.selectedShiftName,
                        durationMinutes = minutes,
                        leaveType = s.leaveType,
                        note = s.note.takeIf { it.isNotBlank() },
                    )
                )
            }
            result.onSuccess { controller.dismiss() }
                .onFailure { err -> _state.update { it.copy(errorText = err.toText()) } }
        }
    }

    fun delete() {
        val s = _state.value
        viewModelScope.launch {
            recordRepo.delete(s.date, s.tab)
            controller.dismiss()
        }
    }

    fun clearError() = _state.update { it.copy(errorText = null) }
}

fun AppError.toText(): String = when (this) {
    AppError.InvalidDuration -> "时长需在 0–24 小时之间"
    AppError.InvalidComp -> "转调休不能超过本条加班时长"
    AppError.FutureDate -> "不能选择未来的日期"
    AppError.DuplicateName -> "名称已存在"
    AppError.InvalidName -> "名称为空或过长（≤10 字）"
    is AppError.Storage -> reason
    AppError.Unexpected -> "操作失败，请重试"
}
