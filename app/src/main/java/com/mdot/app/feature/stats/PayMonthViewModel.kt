package com.mdot.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.InsuranceFill
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.livePreviewSheet
import com.mdot.app.domain.model.DEFAULT_COLLAPSED_GROUPS
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.nextUserRowId
import com.mdot.app.domain.model.reconciliations
import com.mdot.app.domain.toCalcLite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * 记月页：月度工资单编辑。数据按月存 DataStore JSON（键 paymonth_<yyyy-MM>），
 * 未编辑过的月份回落出厂单；出厂固定行不可删，新增行可改可删。
 * 展示走**实时预览**（[displaySheet]，与首页数据区同款的 domain/PayMonthSync.kt 推导）：
 * 引擎算得出的行（基本工资/加班工资/调休折现/事假/病假/社保/公积金）随当前考勤与薪资设定实时出数，
 * 手改/手填行保留用户值——不再需要「同步本月考勤」按钮（2026-09-28 用户要求，按钮已移除）。
 * 工地制度无引擎值，保持存盘单据原值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PayMonthViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    private val recordRepo: RecordRepository,
    private val holidayRepo: HolidayRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /**
     * 存盘单据（**只读展示用**）；未编辑过的月份回落出厂单。
     *
     * ⚠️⚠️ **写盘路径一律不许读它**——见 [freshSheet]。它是无订阅者的 `WhileSubscribed` StateFlow，
     * 页面只订阅 [displaySheet]/[attendance]/[collapsed]，所以它的 `.value` 恒为种子 `PayMonthSheet.default()`。
     * 当初所有 mutate 都在这里读底稿 ⇒ 每次增/改/删都把整张单子覆盖成「出厂默认 + 那一处改动」，
     * 已记金额与用户新增行当场蒸发（2026-10-07 用户报「增加项目后数据清 0 / 改任意数据时新增项消失」）。
     */
    val sheet: StateFlow<PayMonthSheet> = _month
        .flatMapLatest { m -> storedFlow(m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayMonthSheet.default())

    /**
     * 展示用单据 = **实时预览**（硬规则 12，domain/PayMonthSync.kt 的 livePreviewSheet，
     * 与首页数据区「实发工资」同一套推导、只算不写盘）：引擎行走当前考勤/薪资实时值、
     * 手改/手填行保留用户值。页面各卡与汇总的金额都读它，随记随更新（2026-09-28）。
     */
    val displaySheet: StateFlow<PayMonthSheet> = _month
        .flatMapLatest { m -> previewFlow(m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayMonthSheet.default())

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
            // 比例 >0 → 带上引擎值 + 「基数×比例」推导（保存后行标「来自考勤 · 自动计算」）；
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

    /** 某月存盘单据（读取侧出厂行已由 SettingsDataSource 补齐） */
    private fun storedFlow(m: YearMonth): Flow<PayMonthSheet> =
        settings.payMonthFlow(m.toString()).map { it ?: PayMonthSheet.default() }

    /** 某月的实时预览单据（只算不写盘；与首页同款）。工地无引擎值 → 原样返回存盘单据 */
    private fun previewFlow(m: YearMonth): Flow<PayMonthSheet> =
        combine(storedFlow(m), calcFlow(m), settings.salaryFlow) { stored, out, salary ->
            if (out == null) stored else livePreviewSheet(
                stored,
                out,
                fillBase = salary.includeBase,
                syncedAt = LocalDate.now().toString(),
                compCashCents = PayrollCalculator.compCashCents(salary, out),
                compMinutes = PayrollCalculator.compFromOtMinutes(out),
                insurance = InsuranceFill.of(salary),
            )
        }

    /** 某月工资计算结果（非工地；工地为 null）。保持冷流：预览/摘要按需组合订阅 */
    private fun calcFlow(m: YearMonth): Flow<PayrollCalculator.Output?> {
        val from = m.atDay(1)
        val to = m.atEndOfMonth()
        return combine(
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

    /** 本月考勤摘要（摘要条用）：从当月计算流推导 */
    val attendance: StateFlow<PayrollCalculator.AttendanceSummary?> = _month
        .flatMapLatest { calcFlow(it) }
        .map { it?.let(PayrollCalculator::attendanceSummary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 上月单据（环比基线；**同为实时预览**——上月没手动填过也有数）；全 0 时 UI 不显环比 */
    val prevSheet: StateFlow<PayMonthSheet> = _month
        .flatMapLatest { previewFlow(it.minusMonths(1)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayMonthSheet.default())

    /** 导入上月的覆盖前快照（撤销用），仅最近一次有效 */
    private var importBackup: PayMonthSheet? = null
    private var importBackupMonth: YearMonth? = null

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
     * 恢复某行的引擎值（对账弹窗用，docs/20 P2-2 修订）：金额改回**实时**引擎值并回到 SYNCED——
     * 引擎元数据随预览刷新（liveMerged），恢复的也是「引擎现在算出来的值」而非手改当时的快照。
     * **不做全局「覆盖/仅填空」开关**，由用户按行决定，既尊重手改、也不丢引擎值。
     */
    fun restoreEngineValue(group: PayGroup, itemId: Long) {
        val engine = displaySheet.value.itemAt(group, itemId)?.engineCents ?: return
        mutate(group) { list ->
            list.map { item ->
                if (item.id != itemId) item
                else item.copy(amountCents = engine, engineCents = engine, source = PayMonthSource.SYNCED)
            }
        }
    }

    /** 一键恢复全部手改行（口径同 [restoreEngineValue]：只动对账里那些行，恢复到**实时**引擎值） */
    fun restoreAllEngineValues() {
        viewModelScope.launch {
            val m = _month.value
            sheetEditMutex.withLock {
                // 底稿现取（不读 sheet.value，见 freshSheet）：否则整张单子被覆盖成出厂默认
                val cur = freshSheet(m)
                val targets = displaySheet.value.reconciliations()
                    .mapNotNull { r -> r.item.engineCents?.let { (r.group to r.item.id) to it } }
                    .toMap()
                fun restore(group: PayGroup, list: List<PayMonthItem>) = list.map { item ->
                    val engine = targets[group to item.id]
                    if (engine == null) item
                    else item.copy(amountCents = engine, engineCents = engine, source = PayMonthSource.SYNCED)
                }
                settings.setPayMonth(
                    m.toString(),
                    cur.copy(
                        basic = restore(PayGroup.BASIC, cur.basic),
                        subsidy = restore(PayGroup.SUBSIDY, cur.subsidy),
                        deduction = restore(PayGroup.DEDUCTION, cur.deduction),
                        other = restore(PayGroup.OTHER, cur.other),
                    ),
                )
            }
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

    /** 批量新增（预选项多选一次添加，docs/20 P2-3）：名称去重去空，id 依次递增。
     *  id 从 `USER_ROW_ID_BASE` 起（避开出厂固定行号段，否则扣款组会撞上社保/公积金的 id） */
    fun addItems(group: PayGroup, names: List<String>, cents: Long) = mutate(group) { list ->
        var next = list.nextUserRowId()
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

    /**
     * 导入上月：上月**实时预览**单据整体覆盖本月——手填/手改行随行带过来；
     * 引擎行带上的是上月实时值（不手改就跟本月实时值走，不锁死上月数）。
     */
    fun importPrevMonth() {
        val cur = _month.value
        viewModelScope.launch {
            sheetEditMutex.withLock {
                // 快照与覆盖在**同一把锁内**取：既杜绝「撤销早于覆盖落盘」的竞态，
                // 快照也必须现取（不读 sheet.value，见 freshSheet）——否则撤销会把整张单子恢复成出厂默认
                importBackup = freshSheet(cur)
                importBackupMonth = cur
                settings.setPayMonth(cur.toString(), previewFlow(cur.minusMonths(1)).first())
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

    /**
     * 写盘前的**权威底稿**：现取 DataStore 里的存盘单据（读取侧已补出厂固定行）。
     *
     * ⚠️ 写路径**必须**用它、不能用 [sheet] 的 `.value`。这是记月丢数据的根因
     * （2026-10-07 用户报「增加项目后已记录数据清 0；改任意项目数据时新增项消失」）：
     * [sheet] 是 `stateIn(WhileSubscribed)`，而页面只订阅 [displaySheet]/[attendance]/[collapsed]，
     * 从不订阅 [sheet] ⇒ 无订阅者 ⇒ 上游永不启动 ⇒ `sheet.value` 永远是种子值
     * `PayMonthSheet.default()`（全 0、无用户行）。拿它当底稿写盘，
     * 每一次增/改/删都会把整张单子覆盖成「出厂默认 + 那一处改动」，其余金额与用户行当场蒸发；
     * 又因为 UI 读的是 [displaySheet]（每次从存盘单据重算），看上去「有数据」，把写盘侧的持续丢数盖住了。
     *
     * 附带修掉的同族隐患：`nextUserRowId()` 也从这份底稿算 id——底稿恒为 default 时
     * **连加两行都拿到 id 1000**（互相撞车，`saveItem`/`removeItem` 按 id 匹配会改错删错行）。
     */
    private suspend fun freshSheet(month: YearMonth): PayMonthSheet =
        settings.payMonthFlow(month.toString()).first() ?: PayMonthSheet.default()

    private fun mutate(group: PayGroup, block: (List<PayMonthItem>) -> List<PayMonthItem>) {
        viewModelScope.launch {
            // 月份锁内取一次：读盘与写盘必须是同一个 key，否则翻月瞬间会跨月写
            val m = _month.value
            sheetEditMutex.withLock {
                val cur = freshSheet(m)
                val next = when (group) {
                    PayGroup.BASIC -> cur.copy(basic = block(cur.basic))
                    PayGroup.SUBSIDY -> cur.copy(subsidy = block(cur.subsidy))
                    PayGroup.DEDUCTION -> cur.copy(deduction = block(cur.deduction))
                    PayGroup.OTHER -> cur.copy(other = block(cur.other))
                }
                settings.setPayMonth(m.toString(), next)
            }
        }
    }
}

// 记月的纯函数推导（applyRecordSync / InsuranceFill / livePreviewSheet）在 domain/PayMonthSync.kt：
// 首页数据区「实发工资」与记月页展示共用同一套算法（硬规则 12），避免两处漂移。

/** 按组取行（对账恢复用）；行 id 只在组内唯一，故带 [PayGroup] 定位（docs/11 060 教训） */
private fun PayMonthSheet.itemAt(group: PayGroup, id: Long): PayMonthItem? = when (group) {
    PayGroup.BASIC -> basic
    PayGroup.SUBSIDY -> subsidy
    PayGroup.DEDUCTION -> deduction
    PayGroup.OTHER -> other
}.firstOrNull { it.id == id }
