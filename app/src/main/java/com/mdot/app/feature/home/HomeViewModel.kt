package com.mdot.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.core.sync.SyncEngine
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.domain.SitePayCalculator
import com.mdot.app.domain.toCalcLite
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.HomeCardsConfig
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
    /** 首页卡片显示序列（v0.6.0 首页卡片可编辑；未配置时经旧逻辑推导） */
    val cards: List<String> = com.mdot.app.domain.model.HomeCardsConfig.DEFAULT_CARDS,
    // ---- 工地记工（12 文档 F-S6）----
    val siteLoading: Boolean = false,
    val siteProjectName: String = "",
    /** 本月工数合计 ×1000 */
    val siteTotalWorksMilli: Long = 0,
    val siteOtMinutes: Int = 0,
    val siteWorkPayCents: Long = 0,
    val siteAdvanceCents: Long = 0,
    val sitePendingCents: Long = 0,
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
    private val siteRepo: SiteRepository,
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
            combine(settings.bottomBarFlow, recordRepo.observeCompBalance(), settings.homeCardsFlow) { bar, bal, homeCards ->
                Triple(bar, bal, homeCards)
            },
            // 云端恢复导入等大规模替换后 bump → 强制本页全量重算
            dataRevision.version,
        ) { sync: com.mdot.app.core.sync.SyncStatus,
            extra2: Triple<com.mdot.app.domain.model.BottomBarConfig, Int, com.mdot.app.domain.model.HomeCardsConfig>,
            _: Int ->
            sync to extra2
        },
    ) { cycle, monthRecords, salary, workdays, extra ->
        val (sync, extra0) = extra
        val (bar, compBalance, homeCards) = extra0
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
        // 卡片序列：用户配置过（cards!=null）按配置；未配置走旧行为——
        // 底栏已放日历/统计时入口卡自动隐藏（等价于 entries 关），其余全显
        // 旧配置可能含已移除的 id："record"（记加班改固定悬浮胶囊）、"daily"（每日时长卡已删）
        val legacyFiltered = homeCards.cards?.filter { it != "record" && it != "daily" }
        val cards = legacyFiltered ?: HomeCardsConfig.DEFAULT_CARDS.filter { id ->
            !(id == "entries" && ("calendar" in bar.slots || "stats" in bar.slots))
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
            cards = cards,
            backupConfigured = sync.configured,
            backupConflict = sync.conflict,
            lastBackupAt = sync.lastBackupAt,
            salary = salary,
            overtimeMinutes = cycleOut.overtimeMinutes,
            periodStandardMinutes = cycleOut.periodStandardMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 每日统计首页数据（本月自然月；工地制度=工地台账，其他制度=当前制度每日加班时长，随制度切换） */
    val siteState: StateFlow<SiteHomeUi> = settings.salaryFlow
        .flatMapLatest { salary ->
            if (salary.workSystem != com.mdot.app.domain.model.WorkSystem.SITE) {
                // 非工地制度：每日时长卡显示当前制度每日加班时长（daily_record）
                val month = CycleCalculator.naturalMonth(today.toYearMonth())
                // 热点图固定六个月窗口（本月月初向前推五个月的月初 → 今天），与统计页同窗口
                val heatFrom = today.withDayOfMonth(1).minusMonths(5)
                combine(
                    recordRepo.observeRange(month.from, month.to),
                    recordRepo.observeRange(heatFrom, today),
                ) { records, heatRecs ->
                    val daily = records.filter { it.type == com.mdot.app.domain.model.RecordType.OT }
                        .groupBy { it.date }
                        .map { (d, list) ->
                            HomeDayPoint(
                                date = d,
                                worksMilli = 0L,
                                otMinutes = list.sumOf { it.durationMinutes },
                                payCents = 0L,
                            )
                        }.sortedBy { it.date }
                    val heat = heatRecs.filter { it.type == com.mdot.app.domain.model.RecordType.OT }
                        .groupBy { it.date }
                        .mapValues { (_, list) -> list.sumOf { it.durationMinutes }.toFloat() }
                    SiteHomeUi(
                        otMinutes = daily.sumOf { it.otMinutes },
                        daily = daily,
                        heatValues = heat,
                    )
                }
            } else {
                kotlinx.coroutines.flow.flow {
                    val pid = siteRepo.currentProjectId()
                    // 热点图固定六个月窗口（本月月初向前推五个月的月初 → 今天），与统计页同窗口
                    val heatFrom = today.withDayOfMonth(1).minusMonths(5)
                    val name = siteRepo.getProject(pid)?.name.orEmpty()
                    // 待结余额扣减未结清的部分结算（本次结算金额）
                    val partial = siteRepo.partialSettledTotal(pid)
                    val month = CycleCalculator.naturalMonth(today.toYearMonth())
                    combine(
                        siteRepo.observeAttendance(pid, month.from, month.to),
                        siteRepo.observePieceWorks(pid, month.from, month.to),
                        siteRepo.observeAdvances(pid, month.from, month.to),
                        siteRepo.observeAttendance(pid, heatFrom, today),
                    ) { atts, pieces, advs, heatAtts ->
                        val out = SitePayCalculator.summarize(
                            SitePayCalculator.Input(
                                attendance = atts,
                                pieceWorks = pieces,
                                advances = advs,
                            )
                        )
                        val todayRows = atts.filter { LocalDate.parse(it.date) == LocalDate.now() }
                        SiteHomeUi(
                            projectId = pid,
                            projectName = name,
                            totalWorksMilli = out.totalWorksMilli,
                            otMinutes = out.otMinutes,
                            workPayCents = out.receivableCents,
                            advanceCents = out.advanceTotalCents,
                            pendingCents = out.pendingCents - partial,
                            siteBaseMinutes = siteRepo.getProject(pid)?.baseMinutes ?: 480,
                            todayWorksMilli = todayRows.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) },
                            todayOtMinutes = todayRows.sumOf { it.otMinutes },
                            daily = atts.map { a ->
                                HomeDayPoint(
                                    date = LocalDate.parse(a.date),
                                    worksMilli = a.workMinutes * 1000L / a.baseMinutes.coerceAtLeast(1),
                                    otMinutes = a.otMinutes,
                                    payCents = a.workPayCents + a.otPayCents,
                                )
                            }.sortedBy { it.date },
                            heatValues = heatAtts.filter { it.workMinutes > 0 }
                                .groupBy { LocalDate.parse(it.date) }
                                .mapValues { (_, list) ->
                                    list.sumOf { it.workMinutes * 1000L / it.baseMinutes.coerceAtLeast(1) } / 1000f
                                },
                        )
                    }.collect { emit(it) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteHomeUi())
}

/** 工地记工首页数据（12 文档 F-S6；独立通道，SiteHomeUi 空实例=非 SITE 制度） */
data class SiteHomeUi(
    val projectId: Long = 0,
    val projectName: String = "",
    val totalWorksMilli: Long = 0,
    val otMinutes: Int = 0,
    val workPayCents: Long = 0,
    val advanceCents: Long = 0,
    val pendingCents: Long = 0,
    /** 当前项目上班基准分钟（HOUR 显示折算用） */
    val siteBaseMinutes: Int = 480,
    /** 每日工数（每日时长卡/周柱状/月柱状卡用，本月） */
    val daily: List<HomeDayPoint> = emptyList(),
    /** 热点图强度（本月月初向前六个月 → 今天；工地=工数，其他=加班分钟），与统计页同窗口 */
    val heatValues: Map<java.time.LocalDate, Float> = emptyMap(),
    /** 今日（数据区今日胶囊用） */
    val todayWorksMilli: Long = 0,
    val todayOtMinutes: Int = 0,
)

/** 首页每日工数点（date → 工数milli/加班分/工钱分） */
data class HomeDayPoint(
    val date: LocalDate,
    val worksMilli: Long,
    val otMinutes: Int,
    val payCents: Long,
)

private fun LocalDate.toYearMonth(): java.time.YearMonth = java.time.YearMonth.from(this)
