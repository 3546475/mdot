package com.mdot.app.feature.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.holiday.HolidayRepository
import com.mdot.app.core.repository.RecordRepository
import com.mdot.app.domain.toCalcLite
import com.mdot.app.core.util.CsvWriter
import com.mdot.app.core.util.rethrowIfCancellation
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.core.util.PayslipRenderer
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.withTemplateRows
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

enum class ExportDimension(val labelRes: Int) {
    CYCLE(R.string.export_dim_cycle),
    MONTH(R.string.export_dim_month),
    CUSTOM(R.string.export_dim_custom),
}

/** 哪个导出按钮在 busy（docs/15：只有被点的按钮变圆环居中，另一个保持文字 disabled） */
enum class ExportBusyAction { CSV, PAYSLIP }

data class ExportUiState(
    val dimension: ExportDimension = ExportDimension.CYCLE,
    val range: CycleCalculator.Period? = null,
    /** 当前制度（工地没有「记月」工资单 ⇒ 隐藏工资单相关动作） */
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    /** 工资单对应的月份（= 区间结束日所在月；v0.7.9.0 起只有「记月单据」一个来源） */
    val sheetMonth: java.time.YearMonth = java.time.YearMonth.now(),
    val busy: Boolean = false,
    /** 正在 busy 的导出按钮（CSV 详情 / 生成工资单）；null=无导出按钮在 busy */
    val busyAction: ExportBusyAction? = null,
    val doneText: String? = null,
    val errorText: String? = null,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    /** 预览弹窗产物：工资单长图 或 CSV */
    val preview: PreviewArtifact? = null,
)

/** 导出预览产物 */
sealed interface PreviewArtifact {
    val file: File
    data class Payslip(override val file: File) : PreviewArtifact
    data class Csv(override val file: File) : PreviewArtifact
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordRepo: RecordRepository,
    private val settings: SettingsDataSource,
    private val holidayRepo: HolidayRepository,
    private val siteRepo: com.mdot.app.core.repository.SiteRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val extra = MutableStateFlow(ExportUiState())

    val uiState: StateFlow<ExportUiState> = combine(
        extra,
        settings.cycleAnchorDayFlow,
        settings.salaryFlow,
    ) { st, anchor, salary ->
        val range = when (st.dimension) {
            ExportDimension.CYCLE -> CycleCalculator.periodContaining(today, anchor)
            ExportDimension.MONTH -> CycleCalculator.naturalMonth(java.time.YearMonth.from(today))
            ExportDimension.CUSTOM -> {
                val from = st.customFrom ?: today.withDayOfMonth(1)
                val to = st.customTo ?: today
                if (from.isAfter(to)) null else CycleCalculator.Period(from, to)
            }
        }
        st.copy(range = range, workSystem = salary.workSystem, sheetMonth = sheetMonthOf(range))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    /** 工资单月份 = 区间**结束日**所在月（自然月=本月；周期 9/26–10/25=10 月；自定义=止日所在月） */
    private fun sheetMonthOf(range: CycleCalculator.Period?): java.time.YearMonth =
        java.time.YearMonth.from(range?.to ?: today)

    fun onDimension(d: ExportDimension) = extra.update { it.copy(dimension = d, doneText = null, errorText = null) }
    fun onCustomFrom(d: LocalDate) = extra.update { it.copy(customFrom = d) }
    fun onCustomTo(d: LocalDate) = extra.update { it.copy(customTo = d) }

    fun dismissPreview() = extra.update { it.copy(preview = null) }
    fun clearResult() = extra.update { it.copy(doneText = null, errorText = null) }

    /** 从 CSV 文件导入记录（本 App 导出的明细）：解析后逐条入库 */
    fun importCsv(uri: android.net.Uri) {
        extra.update { it.copy(busy = true, errorText = null, doneText = null) }
        viewModelScope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("无法读取所选文件")
                val parsed = com.mdot.app.core.util.CsvReader.parse(text)
                var ok = 0
                parsed.ot.forEach { d -> if (recordRepo.saveOt(d) is com.mdot.app.core.util.AppResult.Success) ok++ }
                parsed.leave.forEach { d -> if (recordRepo.saveLeave(d) is com.mdot.app.core.util.AppResult.Success) ok++ }
                ok to parsed
            }.onSuccess { (ok, parsed) ->
                extra.update {
                    it.copy(
                        busy = false,
                        doneText = "已导入 $ok 条记录" +
                            if (parsed.skipped > 0) "（跳过 ${parsed.skipped} 行非数据内容）" else "",
                    )
                }
            }.onFailure { e ->
                extra.update {
                    it.copy(busy = false, errorText = "导入失败：${e.message ?: "文件格式不正确"}")
                }
            }
        }
    }

    /** 生成 CSV 并打开预览弹窗（不直接分享） */
    fun prepareCsvPreview() {
        val st = uiState.value
        val range = st.range ?: run { extra.update { it.copy(errorText = "请先选择有效区间") }; return }
        extra.update { it.copy(busy = true, busyAction = ExportBusyAction.CSV, errorText = null) }
        viewModelScope.launch {
            try {
                val salary = settings.salaryFlow.first()
                val workdays = settings.workdaysFlow.first()
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val csv: String
                val fileName: String
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                    // 工地记工：按项目 + 区间导出出勤/包工/借支明细
                    val pid = siteRepo.currentProjectId()
                    val project = siteRepo.getProject(pid)
                    val atts = siteRepo.observeAttendance(pid, range.from, range.to).first()
                    val pieces = siteRepo.observePieceWorks(pid, range.from, range.to).first()
                    val advs = siteRepo.observeAdvances(pid, range.from, range.to).first()
                    if (atts.isEmpty() && pieces.isEmpty() && advs.isEmpty()) {
                        extra.update { it.copy(busy = false, busyAction = null, errorText = "该区间没有记录") }
                        return@launch
                    }
                    val siteOut = com.mdot.app.domain.SitePayCalculator.summarize(
                        com.mdot.app.domain.SitePayCalculator.Input(atts, pieces, advs)
                    )
                    csv = CsvWriter.buildSiteCsv(range, project?.name.orEmpty(), atts, pieces, advs, siteOut)
                    fileName = "工地明细_${range.from}_${range.to}.csv"
                } else {
                    val records = recordRepo.getRange(range.from, range.to)
                    if (records.isEmpty()) {
                        extra.update { it.copy(busy = false, busyAction = null, errorText = "该区间没有记录") }
                        return@launch
                    }
                    // 一份 CSV 装全部数据（v0.7.9.0 用户拍板）：前半段逐日考勤（可再导入），
                    // 后半段记月工资单（全部行含 0 值）。工单月份 = 区间结束日所在月。
                    val month = sheetMonthOf(range)
                    val sheet = liveSheetFor(month, salary, workdays)
                    csv = CsvWriter.buildFullCsv(
                        range = range,
                        records = records,
                        salary = salary,
                        tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                        standardMinutes = autoStandardMinutes(salary, workdays, range),
                        monthLabel = context.getString(R.string.paymonth_month, month.year, month.monthValue),
                        sheet = sheet,
                    )
                    fileName = "${when (salary.workSystem) {
                        com.mdot.app.domain.model.WorkSystem.HOURLY -> "工作明细"
                        com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> "上班明细"
                        else -> "加班明细"
                    }}_${range.from}_${range.to}.csv"
                }
                val file = File(dir, fileName)
                file.writeText(csv, Charsets.UTF_8)
                extra.update { it.copy(busy = false, busyAction = null, preview = PreviewArtifact.Csv(file)) }
            } catch (e: Exception) {
            e.rethrowIfCancellation()
                extra.update { it.copy(busy = false, busyAction = null, errorText = e.message ?: "生成失败") }
            }
        }
    }

    /**
     * 某月的**实时预览记月单据**（与记月页/首页同一处组装，见 `domain/previewSheet`）。
     *
     * v0.7.9.0 起工资单只剩这一个数据源（用户 2026-10-20：「抹掉记月单据 / 考勤计算的分叉」）——
     * 考勤本来就是记月的子集（单据里基本工资/加班工资/事假那几行正是引擎按考勤算出来的）。
     * 工地没有记月，调用方别用它。
     */
    private suspend fun liveSheetFor(
        month: java.time.YearMonth,
        salary: com.mdot.app.domain.model.SalaryConfig,
        workdays: Set<java.time.DayOfWeek>,
    ): PayMonthSheet {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val records = recordRepo.getRange(from, to)
        val out = PayrollCalculator.summarize(
            PayrollCalculator.Input(
                salary,
                records.map { it.toCalcLite() },
                tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                standardMinutes = autoStandardMinutes(salary, workdays, CycleCalculator.Period(from, to)),
            )
        )
        val templates = settings.payMonthTemplatesFlow.first()
        val stored = settings.payMonthFlow(month.toString()).first()
            ?: PayMonthSheet.default().withTemplateRows(templates)
        val (holidays, makeups) = holidayRepo.holidaySetsInRange(from, to)
        val counts = com.mdot.app.domain.PayMonthDaily.counts(
            from = from,
            to = to,
            workdays = workdays,
            holidays = holidays,
            makeupDays = makeups,
            // 含「只记班次」的 0 时长记录（用户 2026-10-17 拍板算一天）
            recordDates = records.filter { it.type == com.mdot.app.domain.model.RecordType.OT }.map { it.date },
            leaveMinutes = out.leaveMinutes,
        )
        return com.mdot.app.domain.previewSheet(stored, out, salary, counts, today.toString())
    }

    /** 生成工资单长图并打开预览弹窗（不直接分享） */
    fun preparePayslipPreview(palette: PayslipRenderer.Palette) {
        val st = uiState.value
        val range = st.range ?: run { extra.update { it.copy(errorText = "请先选择有效区间") }; return }
        extra.update { it.copy(busy = true, busyAction = ExportBusyAction.PAYSLIP, errorText = null) }
        viewModelScope.launch {
            try {
                val salary = settings.salaryFlow.first()
                val workdays = settings.workdaysFlow.first()
                val bitmap: android.graphics.Bitmap
                val fileName: String
                if (salary.workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                    // 工地结算单长图（项目名 + 工数/点工/包工/借支/应结 + 签字栏）
                    val pid = siteRepo.currentProjectId()
                    val project = siteRepo.getProject(pid)
                    val atts = siteRepo.observeAttendance(pid, range.from, range.to).first()
                    val pieces = siteRepo.observePieceWorks(pid, range.from, range.to).first()
                    val advs = siteRepo.observeAdvances(pid, range.from, range.to).first()
                    if (atts.isEmpty() && pieces.isEmpty() && advs.isEmpty()) {
                        extra.update { it.copy(busy = false, busyAction = null, errorText = "该区间没有记录") }
                        return@launch
                    }
                    val siteOut = com.mdot.app.domain.SitePayCalculator.summarize(
                        com.mdot.app.domain.SitePayCalculator.Input(atts, pieces, advs)
                    )
                    val rows = listOf(
                        "工数合计" to String.format(java.util.Locale.US, "%.1f", siteOut.totalWorksMilli / 1000.0) + " 工",
                        "加班" to TimeUtils.prettyDuration(siteOut.otMinutes),
                        "点工工资" to Money.yuanText(siteOut.workPayCents) + " 元",
                        "包工工资" to Money.yuanText(siteOut.piecePayCents) + " 元",
                        "已借支" to Money.yuanText(siteOut.advanceTotalCents) + " 元",
                        "应结金额" to Money.yuanText(siteOut.pendingCents) + " 元",
                    )
                    bitmap = PayslipRenderer.renderSettlement(
                        context,
                        projectName = project?.name.orEmpty(),
                        range = range,
                        rows = rows,
                        palette = palette,
                    )
                    fileName = "结算单_${range.from}_${range.to}.png"
                } else {
                    // 工资单 = 记月单据（**唯一来源**）。图**只画有金额的项目**（用户 2026-10-20 定）；
                    // 全部行（含 0 值）在 CSV 里。整月一个数都没有 ⇒ 提示，而不是导出一张空表。
                    val month = sheetMonthOf(range)
                    val live = liveSheetFor(month, salary, workdays)
                    if (live.incomeCents == 0L && live.deductionCents == 0L && live.otherCents == 0L) {
                        extra.update {
                            it.copy(
                                busy = false,
                                busyAction = null,
                                errorText = context.getString(R.string.export_sheet_empty),
                            )
                        }
                        return@launch
                    }
                    bitmap = PayslipRenderer.renderSheet(
                        context,
                        title = "工资单",
                        monthLabel = context.getString(
                            com.mdot.app.R.string.paymonth_month,
                            month.year,
                            month.monthValue,
                        ),
                        sheet = live.displayNonZero(),
                        palette = palette,
                    )
                    fileName = "工资单_${month}.png"
                }
                val file = PayslipRenderer.savePng(bitmap, context, fileName)
                extra.update { it.copy(busy = false, busyAction = null, preview = PreviewArtifact.Payslip(file)) }
            } catch (e: Exception) {
            e.rethrowIfCancellation()
                extra.update { it.copy(busy = false, busyAction = null, errorText = e.message ?: "生成失败") }
            }
        }
    }

    /** 综合工时：区间自动标准工时（应出勤 × 8h）；其他制度返回 null（10 文档 §4） */
    private fun autoStandardMinutes(
        salary: com.mdot.app.domain.model.SalaryConfig,
        workdays: Set<java.time.DayOfWeek>,
        range: CycleCalculator.Period,
    ): Int? {
        if (salary.workSystem != com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE) return null
        val (holidays, makeups) = holidayRepo.holidaySetsInRange(range.from, range.to)
        return CycleCalculator.standardMinutesFor(range.from, range.to, workdays, holidays, makeups)
    }
}
