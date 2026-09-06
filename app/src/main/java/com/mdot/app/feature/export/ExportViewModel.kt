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
import com.mdot.app.core.util.PayslipRenderer
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
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

data class ExportUiState(
    val dimension: ExportDimension = ExportDimension.CYCLE,
    val range: CycleCalculator.Period? = null,
    val busy: Boolean = false,
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
) : ViewModel() {

    private val today = LocalDate.now()
    private val extra = MutableStateFlow(ExportUiState())

    val uiState: StateFlow<ExportUiState> = combine(
        extra,
        settings.cycleAnchorDayFlow,
    ) { st, anchor ->
        val range = when (st.dimension) {
            ExportDimension.CYCLE -> CycleCalculator.periodContaining(today, anchor)
            ExportDimension.MONTH -> CycleCalculator.naturalMonth(java.time.YearMonth.from(today))
            ExportDimension.CUSTOM -> {
                val from = st.customFrom ?: today.withDayOfMonth(1)
                val to = st.customTo ?: today
                if (from.isAfter(to)) null else CycleCalculator.Period(from, to)
            }
        }
        st.copy(range = range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    fun onDimension(d: ExportDimension) = extra.update { it.copy(dimension = d, doneText = null, errorText = null) }
    fun onCustomFrom(d: LocalDate) = extra.update { it.copy(customFrom = d) }
    fun onCustomTo(d: LocalDate) = extra.update { it.copy(customTo = d) }

    fun dismissPreview() = extra.update { it.copy(preview = null) }

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
        extra.update { it.copy(busy = true, errorText = null) }
        viewModelScope.launch {
            try {
                val salary = settings.salaryFlow.first()
                val workdays = settings.workdaysFlow.first()
                val records = recordRepo.getRange(range.from, range.to)
                if (records.isEmpty()) {
                    extra.update { it.copy(busy = false, errorText = "该区间没有记录") }
                    return@launch
                }
                val csv = CsvWriter.buildRangeCsv(
                    range, records, salary,
                    tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                    standardMinutes = autoStandardMinutes(salary, workdays, range),
                )
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(
                    dir,
                    "${when (salary.workSystem) {
                        com.mdot.app.domain.model.WorkSystem.HOURLY -> "工作明细"
                        com.mdot.app.domain.model.WorkSystem.COMPREHENSIVE -> "上班明细"
                        else -> "加班明细"
                    }}_${range.from}_${range.to}.csv",
                )
                file.writeText(csv, Charsets.UTF_8)
                extra.update { it.copy(busy = false, preview = PreviewArtifact.Csv(file)) }
            } catch (e: Exception) {
                extra.update { it.copy(busy = false, errorText = e.message ?: "生成失败") }
            }
        }
    }

    /** 生成工资单长图并打开预览弹窗（不直接分享） */
    fun preparePayslipPreview(palette: PayslipRenderer.Palette) {
        val st = uiState.value
        val range = st.range ?: run { extra.update { it.copy(errorText = "请先选择有效区间") }; return }
        extra.update { it.copy(busy = true, errorText = null) }
        viewModelScope.launch {
            try {
                val salary = settings.salaryFlow.first()
                val workdays = settings.workdaysFlow.first()
                val records = recordRepo.getRange(range.from, range.to)
                if (records.isEmpty()) {
                    extra.update { it.copy(busy = false, errorText = "该区间没有记录") }
                    return@launch
                }
                val out = PayrollCalculator.summarize(
                    PayrollCalculator.Input(
                        salary,
                        records.map { it.toCalcLite() },
                        tierOf = { date -> holidayRepo.tierFor(date, workdays) },
                        standardMinutes = autoStandardMinutes(salary, workdays, range),
                    )
                )
                val showMoney = when (salary.mode) {
                    com.mdot.app.domain.model.SalaryMode.BASE -> salary.hasBaseSalary
                    com.mdot.app.domain.model.SalaryMode.MANUAL ->
                        salary.hasBaseSalary || salary.manualRatesCents.values.any { it > 0 }
                }
                val bitmap = PayslipRenderer.render(
                    context,
                    title = "工资单",
                    range = range,
                    output = out,
                    showMoney = showMoney,
                    workSystem = salary.workSystem,
                    palette = palette,
                )
                val file = PayslipRenderer.savePng(bitmap, context, "工资单_${range.from}_${range.to}.png")
                extra.update { it.copy(busy = false, preview = PreviewArtifact.Payslip(file)) }
            } catch (e: Exception) {
                extra.update { it.copy(busy = false, errorText = e.message ?: "生成失败") }
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
