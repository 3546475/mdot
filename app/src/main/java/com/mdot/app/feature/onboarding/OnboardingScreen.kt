package com.mdot.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {

    /** 完成引导：写标记；按工时制度保存底薪+倍率 或 三档时薪 */
    fun done(
        workSystem: WorkSystem,
        baseYuan: String?,
        weekdayMult: String, weekendMult: String, statutoryMult: String,
        hourlyWeekday: String, hourlyWeekend: String, hourlyStatutory: String,
    ) = viewModelScope.launch {
        val current = settings.salaryFlow.first()
        val baseCents = baseYuan
            ?.let { Money.parseYuanToCents(it) }
            ?.takeIf { it > 0 }
            ?: current.baseSalaryCents
        fun mult(text: String, default: Double): Double =
            text.toDoubleOrNull()?.coerceIn(0.1, 10.0) ?: default
        fun rate(text: String): Long = Money.parseYuanToCents(text) ?: 0
        val multipliers = mapOf(
            RateTier.WEEKDAY to mult(weekdayMult, 1.5),
            RateTier.WEEKEND to mult(weekendMult, 2.0),
            RateTier.STATUTORY to mult(statutoryMult, 3.0),
        )
        val hourlyRates = mapOf(
            RateTier.WEEKDAY to rate(hourlyWeekday),
            RateTier.WEEKEND to rate(hourlyWeekend),
            RateTier.STATUTORY to rate(hourlyStatutory),
        )
        settings.setSalary(
            current.copy(
                workSystem = workSystem,
                baseSalaryCents = if (workSystem == WorkSystem.HOURLY) 0 else baseCents,
                multipliers = multipliers,
                hourlyRatesCents = hourlyRates,
            )
        )
        settings.setFirstLaunchDone()
    }
}

/** 首启引导（F8-4：3 步，可跳过）；第 3 步 = 底薪 + 三档倍率 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by remember { mutableStateOf(0) }
    var selectedSystem by remember { mutableStateOf(WorkSystem.STANDARD) }
    var baseText by remember { mutableStateOf("") }
    var weekdayText by remember { mutableStateOf("1.5") }
    var weekendText by remember { mutableStateOf("2") }
    var statutoryText by remember { mutableStateOf("3") }
    var hourlyWeekday by remember { mutableStateOf("") }
    var hourlyWeekend by remember { mutableStateOf("") }
    var hourlyStatutory by remember { mutableStateOf("") }

    fun finish() {
        vm.done(
            selectedSystem,
            baseText.ifBlank { null }, weekdayText, weekendText, statutoryText,
            hourlyWeekday, hourlyWeekend, hourlyStatutory,
        )
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        LinearProgressIndicator(
            progress = { (step + 1) / 3f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(48.dp))

        when (step) {
            0 -> {
                Text(stringResource(R.string.onboarding_app_title), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.onboarding_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            1 -> {
                Text(stringResource(R.string.onboarding_step_work_system), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.onboarding_step_work_system_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                // 标准工时（原「记加班」）
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = if (selectedSystem == WorkSystem.STANDARD)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSystem = WorkSystem.STANDARD }
                        .then(
                            if (selectedSystem == WorkSystem.STANDARD) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.card),
                            ) else Modifier
                        ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.onboarding_ws_standard), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.onboarding_ws_standard_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 小时工
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = if (selectedSystem == WorkSystem.HOURLY)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSystem = WorkSystem.HOURLY }
                        .then(
                            if (selectedSystem == WorkSystem.HOURLY) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.card),
                            ) else Modifier
                        ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.onboarding_ws_hourly), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.onboarding_ws_hourly_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 综合工时（10 文档 F-Z1）
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = if (selectedSystem == WorkSystem.COMPREHENSIVE)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSystem = WorkSystem.COMPREHENSIVE }
                        .then(
                            if (selectedSystem == WorkSystem.COMPREHENSIVE) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.card),
                            ) else Modifier
                        ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.onboarding_ws_comprehensive), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.onboarding_ws_comprehensive_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            2 -> {
                when (selectedSystem) {
                    WorkSystem.HOURLY -> {
                        Text(stringResource(R.string.onboarding_hourly_rates_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.onboarding_hourly_rates_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultField(stringResource(R.string.onboarding_tier_weekday), hourlyWeekday) { hourlyWeekday = it.filter { c -> c.isDigit() || c == '.' } }
                            MultField(stringResource(R.string.onboarding_tier_weekend), hourlyWeekend) { hourlyWeekend = it.filter { c -> c.isDigit() || c == '.' } }
                            MultField(stringResource(R.string.onboarding_tier_statutory), hourlyStatutory) { hourlyStatutory = it.filter { c -> c.isDigit() || c == '.' } }
                        }
                    }

                    WorkSystem.COMPREHENSIVE -> {
                        // 综合工时：底薪 + 超时/法定两档倍率（无周末档，10 文档 D5/D6）
                        Text(stringResource(R.string.onboarding_pay_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.onboarding_comprehensive_pay_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                        shape = RoundedCornerShape(Radius.textField),
                            value = baseText,
                            onValueChange = { baseText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.onboarding_base_salary_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.onboarding_multiplier_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultField(stringResource(R.string.onboarding_tier_overtime), weekdayText) { weekdayText = it.filter { c -> c.isDigit() || c == '.' } }
                            MultField(stringResource(R.string.onboarding_tier_statutory), statutoryText) { statutoryText = it.filter { c -> c.isDigit() || c == '.' } }
                        }
                    }

                    WorkSystem.STANDARD -> {
                        Text(stringResource(R.string.onboarding_pay_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.onboarding_standard_pay_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                        shape = RoundedCornerShape(Radius.textField),
                            value = baseText,
                            onValueChange = { baseText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.onboarding_base_salary_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.onboarding_multiplier_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultField(stringResource(R.string.onboarding_tier_weekday), weekdayText) { weekdayText = it.filter { c -> c.isDigit() || c == '.' } }
                            MultField(stringResource(R.string.onboarding_tier_weekend), weekendText) { weekendText = it.filter { c -> c.isDigit() || c == '.' } }
                            MultField(stringResource(R.string.onboarding_tier_holiday), statutoryText) { statutoryText = it.filter { c -> c.isDigit() || c == '.' } }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { finish() },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.onboarding_skip)) }
            Button(
                onClick = { if (step < 2) step += 1 else finish() },
                modifier = Modifier.weight(1f),
            ) { Text(if (step < 2) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_start)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MultField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
    shape = RoundedCornerShape(Radius.textField),
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.weight(1f),
    )
}
