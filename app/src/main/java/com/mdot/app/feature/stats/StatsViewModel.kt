package com.mdot.app.feature.stats

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.component.buildWeekBars
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.StatsRanges
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.RecordType
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    recordRepo: RecordRepository,
    // private val：onPickMonth 要在点击时**现取**锚点（cycleAnchorDayFlow.first()），不能只靠流订阅
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    dataRevision: com.mdot.app.core.repository.DataRevision,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
    val recordSheet: RecordSheetController,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dimension = MutableStateFlow(StatsDimension.CYCLE)
    private val customFrom = MutableStateFlow<LocalDate?>(null)
    private val customTo = MutableStateFlow<LocalDate?>(null)

    /** 统计页区间步进（0=当前区间）。换维度时清零，否则残留的 offset 会让新区间莫名偏移 */
    private val rangeOffset = MutableStateFlow(0)

    /** 自定义起止合一路（`combine` 定长重载上限 5 流，故与下面的循环步进合起来共 5 个输入） */
    private val customRange = combine(customFrom, customTo) { f, t -> StatsCustomRange(f, t) }

    /**
     * 上一条区间（‹）；自定义按区间天数整段平移。
     * 前进方向的上限由 [maxForwardSteps] 在 [stepForward] 拦住，故这里单调递减即安全。
     */
    fun stepBack() {
        rangeOffset.value -= 1
    }

    /**
     * 下一条区间（›）。上限由 [StatsRanges.maxForwardSteps] 定——
     * 已贴到今天时不再前进（全 App「不越今天」口径），故这里只做「能不能走一步」的判定。
     */
    fun stepForward() {
        if (rangeOffset.value >= 0) return          // 已在当前区间，不再前进
        val st = uiState.value
        if (st.maxForwardSteps <= 0) return         // 自定义整段平移的余量不足
        rangeOffset.value += 1
    }

    /** 回到当前区间（「回本期」）。注意自定义的下限是用户选的起止，不受此影响 */
    fun backToCurrentRange() {
        rangeOffset.value = 0
    }

    /**
     * 统计页区间弹窗：月份网格点选 → 换算成步进偏移。
     * 换算逻辑住 domain，VM 只做分发。**两个维度的基准月不同，别共用一套**：
     * - 考勤周期：[CycleCalculator.cycleOffsetToMonth]，基准 = **今天所在周期的锚点月**
     *   （anchor 26 时点「8 月」得到 8/26–9/25 那一期）；
     * - 自然月：[CycleCalculator.monthOffsetTo]，基准 = **今天所在自然月**（点 8 月就是 8 月）。
     *   ⚠️ 此前两者共用了周期那套基准 ⇒ 起始日 ≠ 1 时**整体后移一个月**（点 1 月跳 2 月，
     *   2026-10-20 用户报，见 docs/11 092）；起始日 = 1 时两者恰好相同，所以一直没被发现。
     */
    fun onPickMonth(month: java.time.YearMonth) {
        viewModelScope.launch {
            rangeOffset.value = when (dimension.value) {
                StatsDimension.YEAR ->
                    (month.year - today.year).coerceAtMost(0)
                StatsDimension.MONTH ->
                    CycleCalculator.monthOffsetTo(java.time.YearMonth.from(today), month)
                else -> {
                    val anchor = settings.cycleAnchorDayFlow.first()
                    CycleCalculator.cycleOffsetToMonth(
                        CycleCalculator.cycleStartMonth(today, anchor), month,
                    )
                }
            }
        }
    }

    /** 统计页区间弹窗：年份网格点选 → 步进偏移（年粒度，不越今天） */
    fun onPickYear(year: Int) {
        rangeOffset.value = (year - today.year).coerceAtMost(0)
    }

    /** 饼图模式（非工地）：与数据流解耦，独立保存 */
    val pieMode = MutableStateFlow(PieMode.SHIFT)

    /** 柱状图选中项（M/D 标签）：与数据流解耦 */
    val selectedBar = MutableStateFlow<String?>(null)

    fun onPieMode(mode: PieMode) {
        pieMode.value = mode
    }

    fun selectBar(label: String?) {
        selectedBar.value = if (selectedBar.value == label) null else label
    }

    val uiState: StateFlow<StatsUiState> = combine(
        dimension,
        customRange,
        // 云端恢复导入后 DataRevision bump → 强制本页全量重算
        combine(settings.cycleAnchorDayFlow, dataRevision.version) { anchor, _ -> anchor },
        combine(settings.salaryFlow, settings.workdaysFlow) { s, w -> s to w },
        rangeOffset,
    ) { dim, custom, anchor, (salary, workdays), offset ->
        val base = StatsRanges.current(
            kind = dim.rangeKind,
            today = today,
            anchorDay = anchor,
            customFrom = custom.from,
            customTo = custom.to,
            fallback = CycleCalculator.periodContaining(today, anchor),
        )
        // 「‹ ›」步进：CYCLE=整周期、MONTH=整月、YEAR=整年、CUSTOM=按区间天数整段平移，
        // 全部走 domain 纯函数（VM/UI 不碰日期算术）。走不到（跨越今天）时退回 base——
        // 前进方向另有 maxForwardSteps 置灰兜底，故正常操作不会走到这个退化分支。
        // ⚠️ 工地维度（effDim 归一后是 SITE_*）在下面 isSite 分支重新按项目口径解析，不使用本 range。
        val stepped = if (offset == 0 || base == null) base else StatsRanges.step(
            kind = dim.rangeKind, from = base, anchorDay = anchor, steps = offset, today = today,
        )
        // 弹窗的「当前区间」用步进后的 range —— 这样月份/年份网格的高亮跟着用户实际在看的那一期走；
        // 反解基准（base）的计算在 onPickMonth 内部按 anchor 现算，不依赖这里。
        StatsRangeParams(dim, stepped ?: base, salary to workdays, offset)
    }.flatMapLatest { (dim, range, salaryWorkdays, offset) ->
        val (salary, workdays) = salaryWorkdays
        val isSite = salary.workSystem == WorkSystem.SITE
        // 维度归一（跨制度残留，21 文档 B2）：工地的固定周期维度回落「本期待结」，非工地的工地维度回落「考勤周期」
        val effDim = when {
            isSite && dim in setOf(StatsDimension.CYCLE, StatsDimension.MONTH, StatsDimension.YEAR) ->
                StatsDimension.SITE_PENDING
            !isSite && dim in setOf(StatsDimension.SITE_PENDING, StatsDimension.SITE_SPAN) ->
                StatsDimension.CYCLE
            else -> dim
        }
        if (!isSite && range == null) return@flatMapLatest flowOf(StatsUiState(dimension = effDim))
        // 热点图窗口：固定六个月（本月月初向前推五个月的月初 → 今天），与所选区间无关（21 文档 B3 修订）
        val heatStart = today.withDayOfMonth(1).minusMonths(5)
        val heatEnd = today
        // 工地记工（21 文档 B1/B2）：汇总/明细走项目口径区间，热点图由下方 merge 提供项目活动期窗口
        val base = if (isSite) {
            val pid = siteRepo.currentProjectId()
            // 结算/撤销结算 → observeSettlements 触发区间滚动
            siteRepo.observeSettlements(pid).flatMapLatest {
                val (rFrom, rTo) = when (effDim) {
                    StatsDimension.SITE_SPAN -> siteRepo.projectSpan(pid)
                    StatsDimension.CUSTOM -> {
                        val from = customFrom.value ?: today.withDayOfMonth(1)
                        val to = customTo.value ?: today
                        from to maxOf(to, from)
                    }
                    else -> siteRepo.unsettledRange(pid) // SITE_PENDING
                }
                combine(
                siteRepo.observeAttendance(pid, rFrom, rTo),
                siteRepo.observePieceWorks(pid, rFrom, rTo),
                siteRepo.observeAdvances(pid, rFrom, rTo),
                siteRepo.observeAttendanceAllProjects(rFrom, rTo),
                siteRepo.observeAllProjects(),
            ) { atts, pieces, advs, allAtts, projects ->
                val out0 = SitePayCalculator.summarize(
                    SitePayCalculator.Input(atts, pieces, advs)
                )
                // 待结余额扣减未结清的部分结算（本次结算金额）
                val partial = siteRepo.partialSettledTotal(pid)
                val out = if (partial > 0) out0.copy(pendingCents = out0.pendingCents - partial) else out0
                // 工数 = 上班分钟 ÷ 基准分钟快照（当日标准）
                fun worksMilliOf(minutes: Int, base: Int): Long = minutes * 1000L / base.coerceAtLeast(1)
                val worksByDay: Map<LocalDate, Float> = atts
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, list) ->
                        list.sumOf { worksMilliOf(it.workMinutes, it.baseMinutes) } / 1000f
                    }
                val nameById = projects.associate { it.id to it.name }
                val pieSiteProjects = allAtts
                    .filter { it.workMinutes > 0 }
                    .groupBy { it.projectId }
                    .map { (projectId, list) ->
                        PieSlice(
                            label = nameById[projectId] ?: "#$projectId",
                            value = list.sumOf { worksMilliOf(it.workMinutes, it.baseMinutes) } / 1000f,
                            colorIndex = (projectId % 7).toInt(),
                        )
                    }
                    .filter { it.value > 0f }
                    .sortedByDescending { it.value }
                // 流水行构造三处共用（21 文档 B5 重构）
                val details = buildSiteDetailRows(atts, pieces, advs)
                StatsUiState(
                    dimension = effDim,
                    rangeLabel = "${TimeUtils.mdCn(rFrom)} – ${TimeUtils.mdCn(rTo)}",
                    workSystem = WorkSystem.SITE,
                    siteSummary = out,
                    barValues = worksByDay,
                    heatEnd = heatEnd,
                    rangeFrom = rFrom,
                    rangeTo = rTo,
                    weekBars = buildWeekBars(worksByDay),
                    pieSiteProjects = pieSiteProjects,
                    siteDetails = details,
                    customFrom = customFrom.value,
                    customTo = customTo.value,
                    // 工地：项目口径区间随结算滚动，没有「上一条/下一条」概念（‹ › 在 UI 侧隐藏）
                    rangeOffset = 0,
                    maxForwardSteps = 0,
                )
            }
            }
        } else {
            @Suppress("NAME_SHADOWING")
            val range = range ?: return@flatMapLatest flowOf(StatsUiState(dimension = effDim))
            combine(
                recordRepo.observeRange(range.from, range.to),
                recordRepo.observeAdjustments(),
            ) { records, adjustments ->
            val tierOf: (LocalDate) -> RateTier = { date -> holidayRepo.tierFor(date, workdays) }
            // 综合工时：区间标准 = 应出勤天数 × 8h（手动覆盖在引擎内优先，10 文档 §4）
            val standardMinutes =
                if (salary.workSystem == WorkSystem.COMPREHENSIVE) {
                    val (holidays, makeups) = holidayRepo.holidaySetsInRange(range.from, range.to)
                    CycleCalculator.standardMinutesFor(range.from, range.to, workdays, holidays, makeups)
                } else null
            val out = PayrollCalculator.summarize(
                PayrollCalculator.Input(
                    salary,
                    records.map { it.toCalcLite() },
                    adjustments.map { it.toCalcLite() },
                    tierOf,
                    standardMinutes = standardMinutes,
                )
            )
            val showMoney = when (salary.mode) {
                com.mdot.app.domain.model.SalaryMode.BASE -> salary.hasBaseSalary
                com.mdot.app.domain.model.SalaryMode.MANUAL ->
                    salary.hasBaseSalary || salary.manualRatesCents.values.any { it > 0 }
            }
            val pieShift = records.filter { it.type == RecordType.OT }
                .groupBy { it.shiftName ?: "未选班次" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }.toFloat()) }
                // 「只记班次」的 0 时长记录（v0.7.9.0）不贡献时长 ⇒ 过滤掉，别画出 0 值扇区
                .filter { it.value > 0f }
                .sortedByDescending { it.value }
            val pieLeave = records.filter { it.type == RecordType.LEAVE }
                .groupBy { it.leaveType?.displayName ?: "其他" }
                .map { (name, list) -> PieSlice(name, list.sumOf { it.durationMinutes }.toFloat()) }
                .sortedByDescending { it.value }
            // 统一热点图强度：加班分钟（WorkHeatmap 按 v/max 归一，单位不影响渲染；柱状卡 prettyDuration 需要分钟）
            val heat = records.filter { it.type == RecordType.OT }
                .groupBy { it.date }
                .mapValues { (_, list) -> list.sumOf { it.durationMinutes }.toFloat() }
            StatsUiState(
                dimension = effDim,
                rangeLabel = range.toString(),
                output = out,
                showMoney = showMoney,
                workSystem = salary.workSystem,
                pieShift = pieShift,
                pieLeave = pieLeave,
                barValues = heat,
                heatValues = heat,
                heatEnd = heatEnd,
                rangeFrom = range.from,
                rangeTo = range.to,
                weekBars = buildWeekBars(heat),
                customFrom = customFrom.value,
                customTo = customTo.value,
                picker = StatsRangePicker(kind = effDim.rangeKind, current = range),
                rangeOffset = offset,
                maxForwardSteps = StatsRanges.maxForwardSteps(effDim.rangeKind, range, today),
            )
            }
        }
        // 热点图数据独立于所选区间：固定六个月窗口（21 文档 B3 修订：恢复月窗，工地/非工地同款）
        val heatFlow: kotlinx.coroutines.flow.Flow<Map<LocalDate, Float>> =
            if (isSite) {
                val pid = siteRepo.currentProjectId()
                siteRepo.observeAttendance(pid, heatStart, heatEnd).map { atts ->
                    atts.filter { it.workMinutes > 0 }
                        .groupBy { LocalDate.parse(it.date) }
                        .mapValues { (_, list) ->
                            list.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) } / 1000f
                        }
                }
            } else {
                recordRepo.observeRange(heatStart, heatEnd).map { recs ->
                    recs.filter { it.type == RecordType.OT }
                        .groupBy { it.date }
                        .mapValues { (_, list) -> list.sumOf { it.durationMinutes }.toFloat() }
                }
            }
        base.combine(heatFlow) { st, heat ->
            st.copy(heatValues = heat, heatEnd = heatEnd, heatStart = heatStart)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onDimension(d: StatsDimension) {
        dimension.value = d
        rangeOffset.value = 0   // 换维度必须清零，否则上一条区间的 offset 会让新区间莫名偏移
        if (d != StatsDimension.CUSTOM) {
            customFrom.value = null
            customTo.value = null
        }
    }

    /** 自定义起止改动后，步进偏移同样清零（用户重新框了区间，就不该还挂着旧的平移量） */
    fun onCustomFrom(d: LocalDate) {
        customFrom.value = d
        rangeOffset.value = 0
        if ((customTo.value ?: d).isBefore(d)) customTo.value = d
    }

    fun onCustomTo(d: LocalDate) {
        if (d.isAfter(LocalDate.now())) return
        rangeOffset.value = 0
        customTo.value = d
        if ((customFrom.value ?: d).isAfter(d)) customFrom.value = d
    }

}
