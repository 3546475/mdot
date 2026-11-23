package com.mdot.app.feature.payroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ChoicePillOption
import com.mdot.app.core.designsystem.component.ChoicePillRow
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanIconButton
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.ShrinkFeedbackButton
import com.mdot.app.core.designsystem.component.TimeField
import com.mdot.app.core.designsystem.component.formatMinuteOfDay
import com.mdot.app.core.designsystem.jiabanFilterChipColors
import com.mdot.app.domain.OtPricingCalculator
import com.mdot.app.domain.model.OtClockBand
import com.mdot.app.domain.model.OtDurationBand
import com.mdot.app.domain.model.OtPricingConfig
import com.mdot.app.domain.model.OtPricingIssue
import com.mdot.app.domain.model.OtPricingMode
import com.mdot.app.domain.model.OtRateUnit
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.model.bandCount
import com.mdot.app.domain.model.validate
import com.mdot.app.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class OtPricingViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {

    data class DurationDraft(
        val endText: String,
        val multiplierText: String,
        val hourlyText: String,
    )

    data class ClockDraft(
        val startMinute: Int,
        val endMinute: Int,
        val multiplierText: String,
        val hourlyText: String,
    )

    data class UiState(
        val salary: SalaryConfig = SalaryConfig(),
        val mode: OtPricingMode = OtPricingMode.FLAT,
        val rateUnit: OtRateUnit = OtRateUnit.MULTIPLIER,
        val tiers: Set<RateTier> = setOf(RateTier.WEEKDAY),
        val durationDrafts: List<DurationDraft> = emptyList(),
        val clockDrafts: List<ClockDraft> = emptyList(),
        val defaultStartMinute: Int = 17 * 60,
        val previewMinutesText: String = "2",
        val previewTier: RateTier = RateTier.WEEKDAY,
        val previewStartMinute: Int = 17 * 60,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val salary = settings.salaryFlow.first()
            val ot = salary.otPricing
            val drafts = ot.durationBands.ifEmpty { OtPricingConfig.DEFAULT_DURATION_BANDS }
            val clocks = ot.clockBands.ifEmpty { OtPricingConfig.DEFAULT_CLOCK_BANDS }
            _state.value = UiState(
                salary = salary,
                mode = ot.mode,
                rateUnit = ot.rateUnit,
                tiers = ot.tiers.ifEmpty { setOf(RateTier.WEEKDAY) },
                durationDrafts = drafts.map { band ->
                    DurationDraft(
                        endText = band.endMinute?.let { hourText(it) } ?: "",
                        multiplierText = Money.multiplierText(band.multiplier),
                        hourlyText = if (band.hourlyCents > 0) {
                            Money.yuanText(band.hourlyCents).replace(",", "")
                        } else {
                            ""
                        },
                    )
                },
                clockDrafts = clocks.map { band ->
                    ClockDraft(
                        startMinute = band.startMinute,
                        endMinute = band.endMinute,
                        multiplierText = Money.multiplierText(band.multiplier),
                        hourlyText = if (band.hourlyCents > 0) {
                            Money.yuanText(band.hourlyCents).replace(",", "")
                        } else {
                            ""
                        },
                    )
                },
                defaultStartMinute = ot.defaultStartMinute,
                previewMinutesText = "2",
                previewTier = ot.tiers.firstOrNull() ?: RateTier.WEEKDAY,
                previewStartMinute = ot.defaultStartMinute,
                loaded = true,
            )
        }
    }

    fun onMode(mode: OtPricingMode) = _state.update {
        it.copy(
            mode = mode,
            tiers = if (it.tiers.isEmpty()) setOf(RateTier.WEEKDAY) else it.tiers,
            durationDrafts = if (it.durationDrafts.size < 2) defaultDurationDrafts() else it.durationDrafts,
            clockDrafts = if (it.clockDrafts.isEmpty()) defaultClockDrafts() else it.clockDrafts,
        )
    }

    fun onRateUnit(unit: OtRateUnit) = _state.update { it.copy(rateUnit = unit) }

    fun onTierToggle(tier: RateTier) = _state.update {
        val next = if (tier in it.tiers) {
            if (it.tiers.size <= 1) it.tiers else it.tiers - tier
        } else {
            it.tiers + tier
        }
        it.copy(tiers = next, previewTier = if (it.previewTier in next) it.previewTier else next.first())
    }

    fun onDurationEnd(index: Int, text: String) = _state.update {
        it.copy(
            durationDrafts = it.durationDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(endText = decimalText(text)) else draft
            },
        )
    }

    fun onDurationMultiplier(index: Int, text: String) = _state.update {
        it.copy(
            durationDrafts = it.durationDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(multiplierText = decimalText(text)) else draft
            },
        )
    }

    fun onDurationHourly(index: Int, text: String) = _state.update {
        it.copy(
            durationDrafts = it.durationDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(hourlyText = decimalText(text)) else draft
            },
        )
    }

    fun addDurationBand() = _state.update {
        if (it.durationDrafts.size >= 5) return@update it
        val finite = it.durationDrafts.dropLast(1)
        val lastEnd = finite.lastOrNull()?.endText?.toDoubleOrNull() ?: 2.0
        val nextEnd = (lastEnd + 1.0).coerceAtMost(23.0)
        if (nextEnd <= lastEnd) return@update it
        val source = finite.lastOrNull() ?: it.durationDrafts.first()
        val list = it.durationDrafts.toMutableList()
        list.add(
            list.lastIndex,
            DurationDraft(
                endText = hourText((nextEnd * 60).roundToInt()),
                multiplierText = source.multiplierText,
                hourlyText = source.hourlyText,
            ),
        )
        it.copy(durationDrafts = list)
    }

    fun removeDurationBand(index: Int) = _state.update {
        if (it.durationDrafts.size <= 2 || index < 0 || index >= it.durationDrafts.lastIndex) {
            return@update it
        }
        it.copy(durationDrafts = it.durationDrafts.filterIndexed { i, _ -> i != index })
    }

    fun onClockStart(index: Int, minute: Int) = _state.update {
        it.copy(
            clockDrafts = it.clockDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(startMinute = minute) else draft
            },
        )
    }

    fun onClockEnd(index: Int, minute: Int) = _state.update {
        it.copy(
            clockDrafts = it.clockDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(endMinute = minute) else draft
            },
        )
    }

    fun onClockMultiplier(index: Int, text: String) = _state.update {
        it.copy(
            clockDrafts = it.clockDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(multiplierText = decimalText(text)) else draft
            },
        )
    }

    fun onClockHourly(index: Int, text: String) = _state.update {
        it.copy(
            clockDrafts = it.clockDrafts.mapIndexed { i, draft ->
                if (i == index) draft.copy(hourlyText = decimalText(text)) else draft
            },
        )
    }

    fun addClockBand() = _state.update {
        if (it.clockDrafts.size >= 6) return@update it
        val last = it.clockDrafts.lastOrNull()
        val start = (last?.endMinute ?: (17 * 60)) % (24 * 60)
        val end = (start + 2 * 60) % (24 * 60)
        val source = last ?: ClockDraft(17 * 60, 19 * 60, "1.5", "")
        it.copy(
            clockDrafts = it.clockDrafts + ClockDraft(
                startMinute = start,
                endMinute = if (end == start) (start + 60) % (24 * 60) else end,
                multiplierText = source.multiplierText,
                hourlyText = source.hourlyText,
            ),
        )
    }

    fun removeClockBand(index: Int) = _state.update {
        if (it.clockDrafts.size <= 1) return@update it
        it.copy(clockDrafts = it.clockDrafts.filterIndexed { i, _ -> i != index })
    }

    fun onDefaultStart(minute: Int) = _state.update { it.copy(defaultStartMinute = minute) }

    fun onPreviewMinutes(text: String) = _state.update {
        it.copy(previewMinutesText = decimalText(text))
    }

    fun onPreviewTier(tier: RateTier) = _state.update { it.copy(previewTier = tier) }

    fun onPreviewStart(minute: Int) = _state.update { it.copy(previewStartMinute = minute) }

    fun save(config: OtPricingConfig, onDone: () -> Unit) {
        viewModelScope.launch {
            val prev = settings.salaryFlow.first()
            settings.setSalary(prev.copy(otPricing = config))
            onDone()
        }
    }

    private fun decimalText(text: String): String =
        text.filter { c -> c.isDigit() || c == '.' }

    private fun hourText(minutes: Int): String =
        if (minutes % 60 == 0) {
            (minutes / 60).toString()
        } else {
            String.format(Locale.US, "%.2f", minutes / 60.0).trimEnd('0').trimEnd('.')
        }

    private fun defaultDurationDrafts(): List<DurationDraft> =
        OtPricingConfig.DEFAULT_DURATION_BANDS.map { band ->
            DurationDraft(
                endText = band.endMinute?.let(::hourText) ?: "",
                multiplierText = Money.multiplierText(band.multiplier),
                hourlyText = "",
            )
        }

    private fun defaultClockDrafts(): List<ClockDraft> =
        OtPricingConfig.DEFAULT_CLOCK_BANDS.map { band ->
            ClockDraft(
                startMinute = band.startMinute,
                endMinute = band.endMinute,
                multiplierText = Money.multiplierText(band.multiplier),
                hourlyText = "",
            )
        }
}

/** 把编辑态转成配置；数字填写不完整时返回 null（由页面提示）。 */
private fun OtPricingViewModel.UiState.toConfigOrNull(): OtPricingConfig? {
    val duration = if (mode == OtPricingMode.DURATION_BANDS) {
        durationDrafts.mapIndexed { index, draft ->
            val last = index == durationDrafts.lastIndex
            val end = if (last) {
                null
            } else {
                draft.endText.toDoubleOrNull()?.let { (it * 60).roundToInt() } ?: return null
            }
            val multiplier = draft.multiplierText.toDoubleOrNull()
            val hourly = Money.parseYuanToCents(draft.hourlyText)
            if (rateUnit == OtRateUnit.MULTIPLIER && multiplier == null) return null
            if (rateUnit == OtRateUnit.FIXED_HOURLY && hourly == null) return null
            OtDurationBand(
                endMinute = end,
                multiplier = multiplier ?: 0.0,
                hourlyCents = hourly ?: 0L,
            )
        }
    } else {
        salary.otPricing.durationBands
    }
    val clocks = if (mode == OtPricingMode.CLOCK_BANDS) {
        clockDrafts.map { draft ->
            val multiplier = draft.multiplierText.toDoubleOrNull()
            val hourly = Money.parseYuanToCents(draft.hourlyText)
            if (rateUnit == OtRateUnit.MULTIPLIER && multiplier == null) return null
            if (rateUnit == OtRateUnit.FIXED_HOURLY && hourly == null) return null
            OtClockBand(
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                multiplier = multiplier ?: 0.0,
                hourlyCents = hourly ?: 0L,
            )
        }
    } else {
        salary.otPricing.clockBands
    }
    return OtPricingConfig(
        mode = mode,
        rateUnit = rateUnit,
        tiers = tiers,
        durationBands = duration,
        clockBands = clocks,
        defaultStartMinute = defaultStartMinute,
    )
}

@Composable
fun OtPricingScreen(
    onBack: () -> Unit,
    vm: OtPricingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    val config = state.toConfigOrNull()
    val issue = config?.validate()
    val canSave = state.loaded && config != null && issue == null

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
            JiabanTopBar(title = stringResource(R.string.payroll_ot_title), onBack = onBack)
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.page)
                        .padding(bottom = Spacing.xl * 4),
                ) {
                Spacer(Modifier.height(Spacing.s))
                ModeCard(state, vm)
                Spacer(Modifier.height(Spacing.s))
                if (state.mode != OtPricingMode.FLAT) {
                    UnitCard(state, vm)
                    Spacer(Modifier.height(Spacing.s))
                    TierCard(state, vm)
                    Spacer(Modifier.height(Spacing.s))
                    when (state.mode) {
                        OtPricingMode.DURATION_BANDS -> DurationCard(state, vm)
                        OtPricingMode.CLOCK_BANDS -> ClockCard(state, vm)
                        OtPricingMode.FLAT -> Unit
                    }
                    Spacer(Modifier.height(Spacing.s))
                    PreviewCard(state, vm, config)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        stringResource(R.string.payroll_ot_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (config == null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.payroll_ot_issue_input),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (issue != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            issueText(issue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.page, vertical = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                ShrinkFeedbackButton(
                    text = stringResource(R.string.payroll_save),
                    busy = saving,
                    enabled = canSave,
                    onClick = {
                        if (config != null) {
                            saving = true
                            vm.save(config) {
                                saving = false
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }
        }
}
@Composable
private fun ModeCard(state: OtPricingViewModel.UiState, vm: OtPricingViewModel) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_mode_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.s))
            ChoicePillRow(
                options = listOf(
                    ChoicePillOption(R.drawable.ic_ms_paid, R.string.payroll_ot_mode_flat),
                    ChoicePillOption(R.drawable.ic_ms_straighten, R.string.payroll_ot_mode_duration),
                    ChoicePillOption(R.drawable.ic_ms_more_time, R.string.payroll_ot_mode_clock),
                ),
                selected = state.mode.ordinal,
                onSelect = { vm.onMode(OtPricingMode.entries[it]) },
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(
                    when (state.mode) {
                        OtPricingMode.FLAT -> R.string.payroll_ot_mode_flat_hint
                        OtPricingMode.DURATION_BANDS -> R.string.payroll_ot_mode_duration_hint
                        OtPricingMode.CLOCK_BANDS -> R.string.payroll_ot_mode_clock_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnitCard(state: OtPricingViewModel.UiState, vm: OtPricingViewModel) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_unit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    colors = jiabanFilterChipColors(),
                    selected = state.rateUnit == OtRateUnit.MULTIPLIER,
                    onClick = { vm.onRateUnit(OtRateUnit.MULTIPLIER) },
                    label = {
                        Text(
                            stringResource(R.string.payroll_ot_unit_multiplier),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                FilterChip(
                    colors = jiabanFilterChipColors(),
                    selected = state.rateUnit == OtRateUnit.FIXED_HOURLY,
                    onClick = { vm.onRateUnit(OtRateUnit.FIXED_HOURLY) },
                    label = {
                        Text(
                            stringResource(R.string.payroll_ot_unit_fixed),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
            if (state.salary.mode == SalaryMode.MANUAL) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.payroll_ot_unit_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TierCard(state: OtPricingViewModel.UiState, vm: OtPricingViewModel) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_tiers_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RateTier.entries.forEach { tier ->
                    FilterChip(
                        colors = jiabanFilterChipColors(),
                        selected = tier in state.tiers,
                        onClick = { vm.onTierToggle(tier) },
                        label = {
                            Text(tier.displayName, style = MaterialTheme.typography.labelMedium)
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.payroll_ot_tiers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DurationCard(state: OtPricingViewModel.UiState, vm: OtPricingViewModel) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_duration_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.payroll_ot_duration_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            state.durationDrafts.forEachIndexed { index, draft ->
                val last = index == state.durationDrafts.lastIndex
                val prevEnd = if (index == 0) {
                    "0"
                } else {
                    state.durationDrafts[index - 1].endText
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                last -> stringResource(R.string.payroll_ot_duration_above, prevEnd)
                                index == 0 -> stringResource(R.string.payroll_ot_duration_from_zero, draft.endText)
                                else -> stringResource(R.string.payroll_ot_duration_range, prevEnd, draft.endText)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (!last && state.durationDrafts.size > 2) {
                            JiabanIconButton(onClick = { vm.removeDurationBand(index) }) {
                                Icon(
                                    painterResource(R.drawable.ic_ms_delete),
                                    contentDescription = stringResource(R.string.payroll_ot_band_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        if (!last) {
                            FloatingLabelTextField(
                                value = draft.endText,
                                onValueChange = { vm.onDurationEnd(index, it) },
                                label = stringResource(R.string.payroll_ot_duration_end_label),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    stringResource(R.string.payroll_ot_duration_last),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        if (state.rateUnit == OtRateUnit.MULTIPLIER) {
                            FloatingLabelTextField(
                                value = draft.multiplierText,
                                onValueChange = { vm.onDurationMultiplier(index, it) },
                                label = stringResource(R.string.payroll_ot_band_multiplier),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            FloatingLabelTextField(
                                value = draft.hourlyText,
                                onValueChange = { vm.onDurationHourly(index, it) },
                                label = stringResource(R.string.payroll_ot_band_hourly),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.s))
                }
            }
            if (state.durationDrafts.size < 5) {
                JiabanButton(
                    text = stringResource(R.string.payroll_ot_add_band),
                    onClick = vm::addDurationBand,
                    role = JiabanButtonRole.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun ClockCard(state: OtPricingViewModel.UiState, vm: OtPricingViewModel) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_clock_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.payroll_ot_clock_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            TimeField(
                label = stringResource(R.string.payroll_ot_default_start),
                minuteOfDay = state.defaultStartMinute,
                onChange = vm::onDefaultStart,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.payroll_ot_default_start_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            OtTimeline(state.clockDrafts)
            Spacer(Modifier.height(Spacing.m))
            state.clockDrafts.forEachIndexed { index, draft ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                R.string.payroll_ot_clock_range,
                                formatMinuteOfDay(draft.startMinute),
                                formatMinuteOfDay(draft.endMinute),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.clockDrafts.size > 1) {
                            JiabanIconButton(onClick = { vm.removeClockBand(index) }) {
                                Icon(
                                    painterResource(R.drawable.ic_ms_delete),
                                    contentDescription = stringResource(R.string.payroll_ot_band_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TimeField(
                            label = stringResource(R.string.payroll_ot_clock_start),
                            minuteOfDay = draft.startMinute,
                            onChange = { vm.onClockStart(index, it) },
                            modifier = Modifier.weight(1f),
                        )
                        TimeField(
                            label = if (draft.endMinute <= draft.startMinute && draft.endMinute != 24 * 60) {
                                stringResource(R.string.payroll_ot_clock_end_next_day)
                            } else {
                                stringResource(R.string.payroll_ot_clock_end)
                            },
                            minuteOfDay = draft.endMinute,
                            onChange = { vm.onClockEnd(index, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    if (state.rateUnit == OtRateUnit.MULTIPLIER) {
                        FloatingLabelTextField(
                            value = draft.multiplierText,
                            onValueChange = { vm.onClockMultiplier(index, it) },
                            label = stringResource(R.string.payroll_ot_band_multiplier),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FloatingLabelTextField(
                            value = draft.hourlyText,
                            onValueChange = { vm.onClockHourly(index, it) },
                            label = stringResource(R.string.payroll_ot_band_hourly),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(Spacing.s))
                }
            }
            if (state.clockDrafts.size < 6) {
                JiabanButton(
                    text = stringResource(R.string.payroll_ot_add_clock),
                    onClick = vm::addClockBand,
                    role = JiabanButtonRole.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun OtTimeline(drafts: List<OtPricingViewModel.ClockDraft>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    Column {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            drawRoundRect(
                color = track,
                cornerRadius = CornerRadius(size.height / 2),
            )
            drafts.forEachIndexed { index, band ->
                val ranges = if (band.endMinute > band.startMinute) {
                    listOf(band.startMinute to band.endMinute)
                } else {
                    buildList {
                        add(band.startMinute to 24 * 60)
                        if (band.endMinute > 0) add(0 to band.endMinute)
                    }
                }
                ranges.forEach { (start, end) ->
                    val x = start / (24f * 60f) * size.width
                    val width = (end - start) / (24f * 60f) * size.width
                    drawRoundRect(
                        color = colors[index % colors.size].copy(alpha = 0.7f),
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    state: OtPricingViewModel.UiState,
    vm: OtPricingViewModel,
    config: OtPricingConfig?,
) {
    SectionCard {
        Column {
            Text(
                stringResource(R.string.payroll_ot_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FloatingLabelTextField(
                    value = state.previewMinutesText,
                    onValueChange = vm::onPreviewMinutes,
                    label = stringResource(R.string.payroll_ot_preview_minutes),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                if (state.mode == OtPricingMode.CLOCK_BANDS) {
                    TimeField(
                        label = stringResource(R.string.payroll_ot_preview_start),
                        minuteOfDay = state.previewStartMinute,
                        onChange = vm::onPreviewStart,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RateTier.entries.forEach { tier ->
                    FilterChip(
                        colors = jiabanFilterChipColors(),
                        selected = state.previewTier == tier,
                        onClick = { vm.onPreviewTier(tier) },
                        label = {
                            Text(tier.displayName, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))

            val validConfig = config?.takeIf { it.validate() == null }
            val minutes = state.previewMinutesText.toDoubleOrNull()
                ?.let { (it * 60).roundToInt().coerceIn(0, 24 * 60) }
                ?: 0
            if (validConfig == null) {
                Text(
                    stringResource(R.string.payroll_ot_preview_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val previewSalary = state.salary.copy(otPricing = validConfig)
                val startMinute = if (state.mode == OtPricingMode.CLOCK_BANDS) {
                    state.previewStartMinute
                } else {
                    null
                }
                val result = OtPricingCalculator.compute(
                    salary = previewSalary,
                    tier = state.previewTier,
                    totalMinutes = minutes,
                    startMinute = startMinute,
                )
                val flat = OtPricingCalculator.flatCents(previewSalary, state.previewTier, minutes)
                Text(
                    text = stringResource(R.string.payroll_ot_preview_total, Money.yuanText(result.cents)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (result.applied) {
                    Text(
                        text = stringResource(R.string.payroll_ot_preview_flat, Money.yuanText(flat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (minutes <= 0) {
                    Text(
                        stringResource(R.string.payroll_ot_preview_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(Spacing.xs))
                    result.segments.forEach { segment ->
                        val startLabel = if (state.mode == OtPricingMode.CLOCK_BANDS) {
                            formatMinuteOfDay((state.previewStartMinute + segment.startOffsetMinute) % (24 * 60))
                        } else {
                            offsetText(segment.startOffsetMinute)
                        }
                        val endLabel = if (state.mode == OtPricingMode.CLOCK_BANDS) {
                            formatMinuteOfDay((state.previewStartMinute + segment.endOffsetMinute) % (24 * 60))
                        } else {
                            offsetText(segment.endOffsetMinute)
                        }
                        Text(
                            text = stringResource(
                                R.string.payroll_ot_preview_segment,
                                startLabel,
                                endLabel,
                                durationText(segment.minutes),
                                Money.yuanText(segment.cents),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun issueText(issue: OtPricingIssue): String = stringResource(
    when (issue) {
        OtPricingIssue.NO_TIER -> R.string.payroll_ot_issue_no_tier
        OtPricingIssue.TOO_FEW_DURATION_BANDS -> R.string.payroll_ot_issue_duration_few
        OtPricingIssue.TOO_MANY_DURATION_BANDS -> R.string.payroll_ot_issue_duration_many
        OtPricingIssue.DURATION_BAND_INVALID -> R.string.payroll_ot_issue_duration_invalid
        OtPricingIssue.CLOCK_BAND_INVALID -> R.string.payroll_ot_issue_clock_invalid
        OtPricingIssue.CLOCK_BAND_OVERLAP -> R.string.payroll_ot_issue_clock_overlap
        OtPricingIssue.DEFAULT_START_INVALID -> R.string.payroll_ot_issue_start_invalid
    },
)

private fun offsetText(minutes: Int): String =
    String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)

private fun durationText(minutes: Int): String =
    if (minutes % 60 == 0) {
        (minutes / 60).toString()
    } else {
        String.format(Locale.US, "%.1f", minutes / 60.0)
    }
