package com.mdot.app.feature.tax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.TaxCalculator
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.SalaryConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * 个税估算（累计预扣法，docs/20 P3-5）。
 *
 * **数据尽量自己算**（硬规则 12）：累计收入 / 累计专项扣除（社保+公积金）/ 累计已预缴个税
 * 全部从**本年的记月单据**汇总；用户只需勾一次「专项附加扣除」。
 * 结果只是**估算**——页面必须写明仅供参考，以单位实际代扣为准。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaxEstimateViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {

    /** 目标月份（默认为当月；用户也可在页面里切） */
    private val month = MutableStateFlow(YearMonth.now())

    /** 用户在手输的「本期税额」文本；null = 仍显示估算值 */
    private val manualText = MutableStateFlow<String?>(null)

    private val message = MutableStateFlow<String?>(null)
    val messageFlow: StateFlow<String?> = message.asStateFlow()
    fun clearMessage() { message.value = null }

    /** 目标月 + 当年 12 个月单据（一次 DataStore 读取） */
    private val yearSheets = month.flatMapLatest { m ->
        settings.payMonthsFlow(m.year).map { m to it }
    }

    val state: StateFlow<TaxUiState> = combine(
        yearSheets,
        settings.salaryFlow,
        manualText,
    ) { (m, months), salary, manual ->
        TaxUiState(
            month = m,
            estimateInput = buildInput(m, salary, months),
            additionalMonthlyCents = salary.taxAdditionalDeductionCents,
            deductionKeys = salary.taxDeductionItemKeys,
            manualText = manual,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaxUiState())

    /** 累计口径：收入/专项扣除/已预缴都从当年 1 月算到目标月 */
    private fun buildInput(
        m: YearMonth,
        salary: SalaryConfig,
        months: Map<Int, PayMonthSheet>,
    ): TaxCalculator.Input {
        val upToThis = months.filterKeys { it <= m.monthValue }.values
        val upToPrev = months.filterKeys { it < m.monthValue }.values
        return TaxCalculator.Input(
            cumulativeIncomeCents = upToThis.sumOf { it.incomeCents },
            cumulativeSpecialCents = upToThis.sumOf { it.deductionCents + it.otherCents - taxRowOf(it) },
            cumulativeAdditionalCents = salary.taxAdditionalDeductionCents * m.monthValue,
            cumulativePaidCents = upToPrev.sumOf { taxRowOf(it) },
            months = m.monthValue,
        )
    }

    /** 某月单据里的「个人所得税」行金额 */
    private fun taxRowOf(sheet: PayMonthSheet): Long =
        sheet.other.firstOrNull { it.id == PayMonthSheet.TAX_ROW_ID }?.amountCents ?: 0L

    fun onMonth(target: YearMonth) {
        month.value = target
        manualText.value = null
    }

    fun onManualTax(text: String) = manualText.update { text.filter { c -> c.isDigit() || c == '.' } }

    /** 勾选/取消专项附加扣除项（多选）；金额 = 各项之和 */
    fun toggleDeduction(key: String, cents: Long) {
        viewModelScope.launch {
            val cur = settings.salaryFlow.first()
            val keys = if (key in cur.taxDeductionItemKeys) cur.taxDeductionItemKeys - key
            else cur.taxDeductionItemKeys + key
            val total = TaxDeductionItems.ALL.filter { it.key in keys }.sumOf { it.monthlyCents }
            settings.setSalary(
                cur.copy(taxDeductionItemKeys = keys, taxAdditionalDeductionCents = total),
            )
            manualText.value = null
        }
    }

    /** 把页面上的税额填进目标月的记月单据（engineCents 存估算值，便于日后对账） */
    fun applyToMonth(currentTaxCents: Long, estimatedCents: Long) {
        viewModelScope.launch {
            val m = month.value
            val key = m.toString()
            val sheet = settings.payMonthFlow(key).first() ?: PayMonthSheet.default()
            val next = sheet.copy(
                other = sheet.other.map { item ->
                    if (item.id != PayMonthSheet.TAX_ROW_ID) item
                    else item.copy(
                        amountCents = currentTaxCents,
                        source = PayMonthSource.ESTIMATED,
                        engineCents = estimatedCents,
                        syncedAt = LocalDate.now().toString(),
                    )
                },
            )
            settings.setPayMonth(key, next)
            message.value = "已填入 ${m.year}年${m.monthValue}月记月"
        }
    }
}

/** 个税页状态（估算结果全部由 [estimateInput] 现算，不缓存） */
data class TaxUiState(
    val month: YearMonth = YearMonth.now(),
    val estimateInput: TaxCalculator.Input = TaxCalculator.Input(0, 0, 0, 0, 1),
    val additionalMonthlyCents: Long = 0,
    val deductionKeys: Set<String> = emptySet(),
    val manualText: String? = null,
) {
    val estimate: TaxCalculator.Output get() = TaxCalculator.estimate(estimateInput)
}

/** 专项附加扣除预置项（2023 年起标准；选项优先，不让人去查税率表） */
/**
 * 专项附加扣除预置项（2023 年起标准；选项优先，不让人去查税率表）。
 *
 * [common] = 「常用」：弹层里置顶成一组，先把最可能用到的摆到眼前（用户 2026-09-23 提：8 项一眼排开太多）。
 * 判定依据：这四项**全国统一、覆盖面最广**——子女教育 / 婴幼儿照护 / 赡养老人 / 住房贷款利息；
 * 住房租金分一线/二线/其他三个城市档位（属于「按城市挑一个」的子选择）与继续教育归「其他」。
 */
data class TaxDeductionItem(
    val key: String,
    val label: String,
    val monthlyCents: Long,
    val common: Boolean = false,
)

object TaxDeductionItems {
    val ALL = listOf(
        TaxDeductionItem("child_edu", "子女教育", 200_000L, common = true),
        TaxDeductionItem("infant", "婴幼儿照护", 200_000L, common = true),
        TaxDeductionItem("elderly", "赡养老人", 300_000L, common = true),
        TaxDeductionItem("continuing_edu", "继续教育", 40_000L),
        TaxDeductionItem("mortgage", "住房贷款利息", 100_000L, common = true),
        TaxDeductionItem("rent_1500", "住房租金（一线）", 150_000L),
        TaxDeductionItem("rent_1100", "住房租金（二线）", 110_000L),
        TaxDeductionItem("rent_800", "住房租金（其他）", 80_000L),
    )

    /** 「常用」组（弹层里置顶）；其余项走 [other] */
    val COMMON = ALL.filter { it.common }
    val OTHER = ALL.filterNot { it.common }
}
