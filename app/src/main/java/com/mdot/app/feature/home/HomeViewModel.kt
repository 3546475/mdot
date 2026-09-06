package com.mdot.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.sync.SyncEngine
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.SalaryConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val backupConfigured: Boolean = false,
    val backupConflict: Boolean = false,
    val lastBackupAt: Long = 0,
    val period: CycleCalculator.Period? = null,
    val cycleOtMinutes: Int = 0,
    val todayOtMinutes: Int = 0,
    val cycleOtPayCents: Long? = null,
    val monthOtPayCents: Long? = null,
    val monthIncomeCents: Long? = null,
    val compBalanceMinutes: Int = 0,
    val showEntryCards: Boolean = true,
    val salary: SalaryConfig = SalaryConfig(),
    // ---- 综合工时副行（10 文档 F-Z5）：超时 X h · 标准 Y h ----
    val overtimeMinutes: Int = 0,
    val periodStandardMinutes: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    engine: com.mdot.app.core.sync.SyncEngine,
    dataRevision: com.mdot.app.core.repository.DataRevision,
    val recordSheet: com.mdot.app.feature.record.RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<HomeUiState> = combine(
        settings.cycleAnchorDayFlow.flatMapLatest { anchor ->
            val period = CycleCalculator.periodContaining(today, anchor)
            recordRepo.observeRange(period.from, period.to).map { period to it }
        },
        recordRepo.observeRange(
            CycleCalculator.naturalMonth(today.toYearMonth()).from,
            CycleCalculator.naturalMonth(today.toYearMonth()).to,
        ),
        settings.salaryFlow,
        settings.workdaysFlow,
        combine(
            engine.status,
            combine(settings.bottomBarFlow, recordRepo.observeCompBalance()) { bar, bal -> bar to bal },
            // 云端恢复导入等大规模替换后 bump → 强制本页全量重算
            dataRevision.version,
        ) { sync: com.mdot.app.core.sync.SyncStatus,
            extra2: Pair<com.mdot.app.domain.model.BottomBarConfig, Int>,
            _: Int ->
            sync to extra2
        },
    ) { cycle, monthRecords, salary, workdays, extra ->
        val (sync, extra0) = extra
        val (bar, compBalance) = extra0
        val (period, cycleRecords) = cycle
        val tierOf: (LocalDate) -> com.mdot.app.domain.model.RateTier =
            { date -> holidayRepo.tierFor(date, workdays) }
        val isComprehensive = salary.workSystem == com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE
        // 综合工时：按「应出勤天数 × 8h」注入周期标准（手动覆盖在引擎内优先，10 文档 §4）
        fun autoStandard(from: LocalDate, to: LocalDate): Int? {
            if (!isComprehensive) return null
            val (holidays, makeups) = holidayRepo.holidaySetsInRange(from, to)
            return CycleCalculator.standardMinutesFor(from, to, workdays, holidays, makeups)
        }
        val cycleOut = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary, cycleRecords.map { it.toCalcLite() }, tierOf = tierOf,
                standardMinutes = autoStandard(period.from, period.to),
            )
        )
        val todayOtMinutes = cycleRecords
            .filter { it.date == today && it.type == com.mdot.app.domain.model.RecordType.OT }
            .sumOf { it.durationMinutes }
        val monthOut = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary, monthRecords.map { it.toCalcLite() }, tierOf = tierOf,
                standardMinutes = autoStandard(
                    CycleCalculator.naturalMonth(today.toYearMonth()).from,
                    CycleCalculator.naturalMonth(today.toYearMonth()).to,
                ),
            )
        )
        val showMoney = when (salary.mode) {
            com.mdot.app.domain.model.SalaryMode.BASE -> salary.hasBaseSalary
            com.mdot.app.domain.model.SalaryMode.MANUAL ->
                salary.hasBaseSalary || salary.manualRatesCents.values.any { it > 0 }
        }
        HomeUiState(
            loading = false,
            period = period,
            // 综合工时大数字 = 本期总工时（普通 + 法定节假日）；其余制度 = 加班时长
            cycleOtMinutes = if (isComprehensive) cycleOut.otMinutes + cycleOut.holidayWorkMinutes
            else cycleOut.otMinutes,
            todayOtMinutes = todayOtMinutes,
            cycleOtPayCents = if (showMoney) cycleOut.otPayCents else null,
            monthOtPayCents = if (showMoney) monthOut.otPayCents else null,
            monthIncomeCents = if (showMoney) monthOut.incomeCents else null,
            compBalanceMinutes = compBalance,
            showEntryCards = !bar.slots.contains("calendar") && !bar.slots.contains("stats"),
            backupConfigured = sync.configured,
            backupConflict = sync.conflict,
            lastBackupAt = sync.lastBackupAt,
            salary = salary,
            overtimeMinutes = cycleOut.overtimeMinutes,
            periodStandardMinutes = cycleOut.periodStandardMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

private fun LocalDate.toYearMonth(): java.time.YearMonth = java.time.YearMonth.from(this)
