package com.mdot.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.toCalcLite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * 记月页：月度工资单编辑。数据按月存 DataStore JSON（键 paymonth_<yyyy-MM>），
 * 未编辑过的月份回落出厂单；出厂固定行不可删，新增行可改可删。
 * 「同步本月考勤」用 PayrollCalculator 按当月加班/请假记录回填 基本工资/加班工资/事假/病假（工地模式无引擎值，隐藏）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PayMonthViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    recordRepo: RecordRepository,
    private val holidayRepo: HolidayRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val sheet: StateFlow<PayMonthSheet> = _month
        .flatMapLatest { m -> settings.payMonthFlow(m.toString()) }
        .map { it ?: PayMonthSheet.default() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayMonthSheet.default())

    /** 工地记工无 PayrollCalculator 引擎值，同步按钮隐藏 */
    val isSite: StateFlow<Boolean> = settings.salaryFlow
        .map { it.workSystem == WorkSystem.SITE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 当月工资计算结果（非工地；供同步回填） */
    private val monthCalc: StateFlow<PayrollCalculator.Output?> = _month
        .flatMapLatest { m ->
            val from = m.atDay(1)
            val to = m.atEndOfMonth()
            combine(
                settings.salaryFlow,
                settings.workdaysFlow,
                recordRepo.observeRange(from, to),
                recordRepo.observeAdjustments(),
            ) { salary, workdays, records, adjustments ->
                val tierOf = { date: LocalDate -> holidayRepo.tierFor(date, workdays) }
                val standardMinutes = if (salary.workSystem == WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(from, to)
                    CycleCalculator.standardMinutesFor(from, to, workdays, holidays, makeups)
                } else null
                if (salary.workSystem == WorkSystem.SITE) null else PayrollCalculator.summarize(
                    PayrollCalculator.Input(
                        salary,
                        records.map { it.toCalcLite() },
                        adjustments.map { it.toCalcLite() },
                        tierOf,
                        standardMinutes = standardMinutes,
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    /** 保存条目（改名称/金额；组内按 id 替换） */
    fun saveItem(group: PayGroup, item: PayMonthItem) = mutate(group) { list ->
        list.map { if (it.id == item.id) item else it }
    }

    /** 新增条目（名称必填，金额可为 0） */
    fun addItem(group: PayGroup, name: String, cents: Long) = mutate(group) { list ->
        list + PayMonthItem((list.maxOfOrNull { it.id } ?: 0) + 1, name.trim(), cents)
    }

    /** 删除条目（出厂行的删除入口由 UI 层禁掉） */
    fun removeItem(group: PayGroup, itemId: Long) = mutate(group) { list ->
        list.filter { it.id != itemId }
    }

    /** 导入上月：上月单据整体覆盖本月（上月未编辑过则用出厂单） */
    fun importPrevMonth() {
        val cur = _month.value
        viewModelScope.launch {
            val prev = settings.payMonthFlow(cur.minusMonths(1).toString()).first() ?: PayMonthSheet.default()
            settings.setPayMonth(cur.toString(), prev)
        }
    }

    /** 同步本月考勤：加班工资/基本工资/事假/病假 ← PayrollCalculator 当月结果（可再手改） */
    fun syncFromRecords() {
        viewModelScope.launch {
            // first() 按需触发计算（monthCalc 是 WhileSubscribed，无 UI 收集者时 .value 恒为初始 null）
            val out = monthCalc.first() ?: return@launch
            val fillBase = settings.salaryFlow.first().includeBase
            settings.setPayMonth(_month.value.toString(), applyRecordSync(sheet.value, out, fillBase))
        }
    }

    private fun mutate(group: PayGroup, block: (List<PayMonthItem>) -> List<PayMonthItem>) {
        viewModelScope.launch {
            val cur = sheet.value
            val next = when (group) {
                PayGroup.BASIC -> cur.copy(basic = block(cur.basic))
                PayGroup.SUBSIDY -> cur.copy(subsidy = block(cur.subsidy))
                PayGroup.DEDUCTION -> cur.copy(deduction = block(cur.deduction))
                PayGroup.OTHER -> cur.copy(other = block(cur.other))
            }
            settings.setPayMonth(_month.value.toString(), next)
        }
    }
}

/** 记月同步（纯函数，供单测）：仅回填存在的行——加班工资/事假/病假；基本工资仅在薪资含底薪时回填；其余行与分组不动 */
internal fun applyRecordSync(
    sheet: PayMonthSheet,
    out: PayrollCalculator.Output,
    fillBase: Boolean,
): PayMonthSheet {
    fun set(list: List<PayMonthItem>, id: Long, cents: Long) =
        list.map { if (it.id == id) it.copy(amountCents = cents) else it }

    val basic = if (fillBase) set(sheet.basic, 1L, out.baseIncludedCents) else sheet.basic
    return sheet.copy(
        basic = set(basic, 2L, out.otPayCents),
        deduction = set(
            set(sheet.deduction, 6L, out.leaveDeductByType[LeaveType.PERSONAL] ?: 0L),
            7L, out.leaveDeductByType[LeaveType.SICK] ?: 0L,
        ),
    )
}
