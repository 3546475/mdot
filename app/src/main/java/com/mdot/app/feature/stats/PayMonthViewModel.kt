package com.mdot.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.DEFAULT_COLLAPSED_GROUPS
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.SalaryConfig
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** 薪资设定：社保/公积金行的弹窗要读它的比例、基数与底薪 */
    val salary: StateFlow<SalaryConfig> = settings.salaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SalaryConfig())

    /**
     * 保存社保/公积金的「比例 + 基数」（设置就在**它自己那一行的弹窗**里，工资设定页不再放这张卡），
     * 并把该行金额按引擎口径回填。比例选「不算」(0) 时**只清来源戳、不动金额**（那是用户自己填的）。
     */
    fun saveInsurance(
        group: PayGroup,
        item: PayMonthItem,
        social: Boolean,
        rateBp: Int,
        baseCents: Long,
    ) {
        viewModelScope.launch {
            val cur = settings.salaryFlow.first()
            val next = if (social) {
                cur.copy(socialInsuranceRateBp = rateBp, socialInsuranceBaseCents = baseCents)
            } else {
                cur.copy(housingFundRateBp = rateBp, housingFundBaseCents = baseCents)
            }
            settings.setSalary(next)

            val derived = if (social) PayrollCalculator.socialInsuranceCents(next)
            else PayrollCalculator.housingFundCents(next)
            val base = if (social) PayrollCalculator.socialInsuranceBaseCents(next)
            else PayrollCalculator.housingFundBaseCents(next)
            // 比例 >0 → 带上引擎值 + 「基数×比例」推导（保存后行标「来自考勤 · 同步」）；
            // 编辑金额与引擎值不一致时 saveItem 会自动改标「已改」
            val patched = if (rateBp > 0) {
                item.copy(
                    engineCents = derived,
                    syncedAt = LocalDate.now().toString(),
                    derivationBaseCents = base,
                    derivationRateBp = rateBp,
                )
            } else {
                item.copy(
                    engineCents = null,
                    syncedAt = null,
                    derivationBaseCents = null,
                    derivationRateBp = null,
                )
            }
            saveItem(group, patched)
        }
    }

    // T1-3：同步/导入结果反馈（docs/15；文案按硬规则 10 例外走 VM 消息硬编码）
    private val message = MutableStateFlow<String?>(null)
    val messageFlow: StateFlow<String?> = message.asStateFlow()
    fun clearMessage() {
        message.value = null
    }

    /** 当月工资计算结果（非工地；供同步回填）。保持冷流：syncFromRecords 按需订阅计算 */
    private val monthCalc = _month
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
        }

    /**
     * 本月考勤摘要（摘要条用）：从 monthCalc 推导。UI 订阅即热；
     * ⚠️ 不影响 syncFromRecords 的**冷流订阅**修复（那里仍走 `monthCalc.filterNotNull().first()`）。
     */
    val attendance: StateFlow<PayrollCalculator.AttendanceSummary?> = monthCalc
        .map { it?.let(PayrollCalculator::attendanceSummary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 上月单据（环比用）；该月没填过则 null */
    val prevSheet: StateFlow<PayMonthSheet?> = month
        .flatMapLatest { settings.payMonthFlow(it.minusMonths(1).toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 覆盖前快照（撤销用）：导入上月 / 同步考勤各留一份，仅各自最近一次有效 */
    private var importBackup: PayMonthSheet? = null
    private var importBackupMonth: YearMonth? = null
    private var syncBackup: PayMonthSheet? = null
    private var syncBackupMonth: YearMonth? = null

    /** 覆盖/撤销串行化：防止「撤销早于覆盖落盘」导致撤销结果被覆盖 */
    private val sheetEditMutex = Mutex()

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    /** 跳到指定年月（月份选择器） */
    fun goToMonth(target: YearMonth) {
        _month.value = target
    }

    /** 回到本月（标题旁的「回本月」胶囊用） */
    fun goToCurrentMonth() {
        _month.value = YearMonth.now()
    }

    /**
     * 恢复某行的引擎值（对账弹窗用，docs/20 P2-2 修订）：
     * 金额改回 `engineCents` 并回到 SYNCED——**不做全局「覆盖/仅填空」开关**，
     * 由用户按行决定，既尊重手改、也不丢引擎值。
     */
    fun restoreEngineValue(group: PayGroup, itemId: Long) = mutate(group) { list ->
        list.map { item ->
            val engine = item.engineCents
            if (item.id != itemId || engine == null) item
            else item.copy(amountCents = engine, source = PayMonthSource.SYNCED)
        }
    }

    /** 一键恢复全部手改行 */
    fun restoreAllEngineValues() {
        viewModelScope.launch {
            val cur = sheet.value
            fun restore(list: List<PayMonthItem>) = list.map { item ->
                val engine = item.engineCents
                if (engine != null && item.amountCents != engine) {
                    item.copy(amountCents = engine, source = PayMonthSource.SYNCED)
                } else {
                    item
                }
            }
            settings.setPayMonth(
                _month.value.toString(),
                cur.copy(
                    basic = restore(cur.basic),
                    subsidy = restore(cur.subsidy),
                    deduction = restore(cur.deduction),
                    other = restore(cur.other),
                ),
            )
        }
    }

    /** 保存条目（改名称/金额；组内按 id 替换）
     *
     * 来源判定（docs/20 P0-3）：有引擎值的行——金额与引擎值相同则回到 [PayMonthSource.SYNCED]，
     * 不同则 [PayMonthSource.EDITED]（engineCents 保留，供对账）；无引擎值的行保持 null（纯手填）。
     */
    fun saveItem(group: PayGroup, item: PayMonthItem) = mutate(group) { list ->
        list.map { cur ->
            if (cur.id != item.id) cur
            else item.copy(
                source = when {
                    item.engineCents == null -> null
                    item.amountCents == item.engineCents -> PayMonthSource.SYNCED
                    else -> PayMonthSource.EDITED
                },
            )
        }
    }

    /** 新增条目（名称必填，金额可为 0） */
    fun addItem(group: PayGroup, name: String, cents: Long) = addItems(group, listOf(name), cents)

    /** 批量新增（预选项多选一次添加，docs/20 P2-3）：名称去重去空，id 依次递增 */
    fun addItems(group: PayGroup, names: List<String>, cents: Long) = mutate(group) { list ->
        var next = (list.maxOfOrNull { it.id } ?: 0) + 1
        list + names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .map { name -> PayMonthItem(next++, name, cents) }
    }

    /**
     * 分组折叠状态（持久化；开关只影响显隐，不动数据）。
     * **从未设置过 → 四个分组全折叠**（v0.7.4 用户要求：进页面先看汇总，别一上来铺四张展开卡）。
     */
    val collapsed: StateFlow<Set<String>> = settings.payMonthCollapsedFlow
        .map { it ?: DEFAULT_COLLAPSED_GROUPS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_COLLAPSED_GROUPS)

    fun toggleCollapsed(group: PayGroup) {
        viewModelScope.launch {
            val cur = settings.payMonthCollapsedFlow.first() ?: DEFAULT_COLLAPSED_GROUPS
            settings.setPayMonthCollapsed(
                if (group.name in cur) cur - group.name else cur + group.name,
            )
        }
    }

    /** 删除条目（出厂行的删除入口由 UI 层禁掉） */
    fun removeItem(group: PayGroup, itemId: Long) = mutate(group) { list ->
        list.filter { it.id != itemId }
    }

    /** 导入上月：上月单据整体覆盖本月（上月未编辑过则用出厂单） */
    fun importPrevMonth() {
        val cur = _month.value
        // 同步取快照（sheet 已是 StateFlow 值），避免「撤销早于导入落盘」被覆盖的竞态
        importBackup = sheet.value
        importBackupMonth = cur
        viewModelScope.launch {
            sheetEditMutex.withLock {
                val prev = settings.payMonthFlow(cur.minusMonths(1).toString()).first() ?: PayMonthSheet.default()
                settings.setPayMonth(cur.toString(), prev)
            }
        }
    }

    /** 撤销最近一次导入上月：恢复导入前的本月快照（仍在本月时；翻月后作废） */
    fun undoImport() {
        val backup = importBackup ?: return
        val month = importBackupMonth ?: return
        importBackup = null
        importBackupMonth = null
        viewModelScope.launch {
            sheetEditMutex.withLock {
                if (month == _month.value) settings.setPayMonth(month.toString(), backup)
            }
        }
    }

    /** 同步本月考勤：加班工资/基本工资/事假/病假 ← PayrollCalculator 当月结果（可再手改）；覆盖前快照供撤销 */
    fun syncFromRecords() {
        viewModelScope.launch {
            if (isSite.value) return@launch // 工地无引擎值
            // 订阅冷流触发计算并等到首个非空结果（修：stateIn .value 在无订阅者时恒为初始 null，点一次无效）
            val out = monthCalc.filterNotNull().first()
            val salary = settings.salaryFlow.first()
            sheetEditMutex.withLock {
                val cur = _month.value
                syncBackup = sheet.value
                syncBackupMonth = cur
                settings.setPayMonth(
                    cur.toString(),
                    applyRecordSync(
                        sheet.value,
                        out,
                        fillBase = salary.includeBase,
                        syncedAt = LocalDate.now().toString(),
                        compCashCents = PayrollCalculator.compCashCents(salary, out),
                        compMinutes = PayrollCalculator.compFromOtMinutes(out),
                        insurance = InsuranceFill.of(salary),
                    ),
                )
            }
        }
    }

    /** 撤销最近一次同步考勤：恢复同步前的本月快照（仍在本月时；翻月后作废） */
    fun undoSync() {
        val backup = syncBackup ?: return
        val month = syncBackupMonth ?: return
        syncBackup = null
        syncBackupMonth = null
        viewModelScope.launch {
            sheetEditMutex.withLock {
                if (month == _month.value) settings.setPayMonth(month.toString(), backup)
            }
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

/** 记月同步（纯函数，供单测）：仅回填存在的行——加班工资/事假/病假；基本工资仅在薪资含底薪时回填；其余行与分组不动。
 *
 * 回填时打**来源戳**（[PayMonthSource.SYNCED] + engineCents 引擎原值 + syncedAt 日期），
 * 让「哪个数是引擎算的、哪个是手写的」可追溯（docs/20 P0-3）；[syncedAt] 由调用方传（VM 用当天日期，便于单测固定值）。
 */
internal fun applyRecordSync(
    sheet: PayMonthSheet,
    out: PayrollCalculator.Output,
    fillBase: Boolean,
    syncedAt: String,
    /** 「调休折现」金额（分），由 [PayrollCalculator.compCashCents] 算好传入 */
    compCashCents: Long = 0,
    /** 推导依据：本月转调休分钟，UI 渲染成「本月转调休 1.5 小时」 */
    compMinutes: Int = 0,
    /** 社保/公积金自动回填载荷（比例 0 = 不算，行保持原值） */
    insurance: InsuranceFill = InsuranceFill(),
): PayMonthSheet {
    fun set(
        list: List<PayMonthItem>,
        id: Long,
        cents: Long,
        derivationMinutes: Int? = null,
        derivationBaseCents: Long? = null,
        derivationRateBp: Int? = null,
    ) = list.map {
        if (it.id == id) {
            it.copy(
                amountCents = cents,
                source = PayMonthSource.SYNCED,
                engineCents = cents,
                syncedAt = syncedAt,
                derivationMinutes = derivationMinutes,
                derivationBaseCents = derivationBaseCents,
                derivationRateBp = derivationRateBp,
            )
        } else {
            it
        }
    }

    val basic = if (fillBase) set(sheet.basic, PayMonthSheet.BASE_ROW_ID, out.baseIncludedCents) else sheet.basic
    return sheet.copy(
        // 加班工资 ← 引擎；「调休折现」← 转调休分钟按同一套口径折算（自动，不用手填）
        basic = set(
            set(basic, 2L, out.otPayCents),
            PayMonthSheet.COMP_ROW_ID,
            compCashCents,
            derivationMinutes = compMinutes.takeIf { it > 0 },
        ),
        deduction = set(
            set(sheet.deduction, 6L, out.leaveDeductByType[LeaveType.PERSONAL] ?: 0L),
            7L, out.leaveDeductByType[LeaveType.SICK] ?: 0L,
        ),
        // 社保/公积金 ← 薪资设定里的「基数 × 比例」（设一次、之后每月自动）。
        // ⚠️ 比例 0 = 用户没启用该项，**不动该行**——否则会把用户手填的金额清零。
        other = sheet.other
            .let {
                if (insurance.socialRateBp <= 0) it
                else set(
                    it,
                    PayMonthSheet.SOCIAL_ROW_ID,
                    insurance.socialCents,
                    derivationBaseCents = insurance.socialBaseCents,
                    derivationRateBp = insurance.socialRateBp,
                )
            }
            .let {
                if (insurance.fundRateBp <= 0) it
                else set(
                    it,
                    PayMonthSheet.FUND_ROW_ID,
                    insurance.fundCents,
                    derivationBaseCents = insurance.fundBaseCents,
                    derivationRateBp = insurance.fundRateBp,
                )
            },
    )
}

/**
 * 社保/公积金自动回填载荷（由薪资设定算出，docs/20「自动 > 选项 > 手动」）。
 * 比例 0 = 用户没启用该项，对应行保持手填不动。
 */
internal data class InsuranceFill(
    val socialCents: Long = 0,
    val socialBaseCents: Long = 0,
    val socialRateBp: Int = 0,
    val fundCents: Long = 0,
    val fundBaseCents: Long = 0,
    val fundRateBp: Int = 0,
) {
    companion object {
        /** 从薪资设定算出两个载荷（比例 0 时金额也是 0） */
        fun of(salary: SalaryConfig) = InsuranceFill(
            socialCents = PayrollCalculator.socialInsuranceCents(salary),
            socialBaseCents = PayrollCalculator.socialInsuranceBaseCents(salary),
            socialRateBp = salary.socialInsuranceRateBp,
            fundCents = PayrollCalculator.housingFundCents(salary),
            fundBaseCents = PayrollCalculator.housingFundBaseCents(salary),
            fundRateBp = salary.housingFundRateBp,
        )
    }
}
