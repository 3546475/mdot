package com.mdot.app.feature.payroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.HourlyPayrollStrategy
import com.mdot.app.domain.StandardPayrollStrategy
import com.mdot.app.domain.model.LeaveType
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.SalaryConfig
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PayrollUiState(
    val mode: SalaryMode = SalaryMode.BASE,
    val baseText: String = "",
    val multTexts: Map<RateTier, String> = RateTier.entries.associateWith { "" },
    val rateTexts: Map<RateTier, String> = RateTier.entries.associateWith { "" },
    val hourlyRateText: String = "",
    val coefPercents: Map<LeaveType, Int> = LeaveType.entries.associateWith { 0 },
    val includeBase: Boolean = true,
    val workSystem: WorkSystem = WorkSystem.STANDARD,
    // ---- 综合工时（10 文档 F-Z2）----
    /** 周期标准工时（小时文本）；空 = 自动（应出勤天数 × 8h） */
    val stdHoursText: String = "",
    /** 当前周期的自动标准（分钟）与展示标签，仅提示用 */
    val autoStandardMinutes: Int = 0,
    val periodLabel: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    private val holidayRepo: com.mdot.app.core.holiday.HolidayRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PayrollUiState())
    val state: StateFlow<PayrollUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val salary = settings.salaryFlow.first()
            _state.value = PayrollUiState(
                mode = salary.mode,
                baseText = if (salary.baseSalaryCents > 0) Money.yuanText(salary.baseSalaryCents).replace(",", "") else "",
                multTexts = RateTier.entries.associateWith {
                    Money.multiplierText(salary.multipliers[it] ?: 1.5)
                },
                rateTexts = RateTier.entries.associateWith {
                    val cents = salary.manualRatesCents[it] ?: 0
                    if (cents > 0) Money.yuanText(cents).replace(",", "") else ""
                },
                hourlyRateText = run {
                    val cents = salary.hourlyRatesCents[RateTier.WEEKDAY] ?: 0
                    if (cents > 0) Money.yuanText(cents).replace(",", "") else ""
                },
                coefPercents = LeaveType.entries.associateWith {
                    ((salary.leaveCoefficients[it] ?: it.defaultCoefficient) * 100).toInt()
                },
                includeBase = salary.includeBase,
                workSystem = salary.workSystem,
                stdHoursText = salary.comprehensiveStandardMinutes.takeIf { it > 0 }
                    ?.let { stdHoursText(it) } ?: "",
            )
            // 当前周期自动标准工时（提示用，10 文档 F-Z2；权威值在首页装配层注入引擎）
            val anchorDay = settings.cycleAnchorDayFlow.first()
            val workdays = settings.workdaysFlow.first()
            val period = CycleCalculator.periodOfMonth(java.time.YearMonth.now(), anchorDay)
            val (holidays, makeups) = holidayRepo.holidaySetsInRange(period.from, period.to)
            _state.update {
                it.copy(
                    autoStandardMinutes =
                    CycleCalculator.standardMinutesFor(period.from, period.to, workdays, holidays, makeups),
                    periodLabel = "${period.from.monthValue}/${period.from.dayOfMonth}" +
                        "–${period.to.monthValue}/${period.to.dayOfMonth}",
                )
            }
        }
    }

    fun onMode(mode: SalaryMode) = _state.update { it.copy(mode = mode, saved = false) }
    fun onBase(text: String) = _state.update { it.copy(baseText = text.filter { c -> c.isDigit() || c == '.' }, saved = false) }
    fun onMult(tier: RateTier, text: String) = _state.update {
        it.copy(multTexts = it.multTexts + (tier to text.filter { c -> c.isDigit() || c == '.' }), saved = false)
    }
    fun onRate(tier: RateTier, text: String) = _state.update {
        it.copy(rateTexts = it.rateTexts + (tier to text.filter { c -> c.isDigit() || c == '.' }), saved = false)
    }
    fun onCoef(type: LeaveType, percent: Int) = _state.update {
        it.copy(coefPercents = it.coefPercents + (type to percent.coerceIn(0, 100)), saved = false)
    }
    fun onIncludeBase(value: Boolean) = _state.update { it.copy(includeBase = value, saved = false) }
    fun onHourlyRate(text: String) = _state.update {
        it.copy(hourlyRateText = text.filter { c -> c.isDigit() || c == '.' }, saved = false)
    }
    fun onStdHours(text: String) = _state.update {
        it.copy(stdHoursText = text.filter { c -> c.isDigit() || c == '.' }, saved = false)
    }

    /** 分钟 → 小时文本（整点不带小数，半点一位小数） */
    private fun stdHoursText(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60}" else String.format(java.util.Locale.US, "%.1f", minutes / 60.0)

    fun save(onDone: () -> Unit = {}) {
        val s = _state.value
        val hourlyCents = Money.parseYuanToCents(s.hourlyRateText) ?: 0
        val salary = SalaryConfig(
            mode = s.mode,
            baseSalaryCents = Money.parseYuanToCents(s.baseText) ?: 0,
            includeBase = s.includeBase,
            multipliers = RateTier.entries.associateWith { tier ->
                s.multTexts[tier]?.toDoubleOrNull()?.coerceIn(0.1, 10.0) ?: salaryDefaultMult(tier)
            },
            manualRatesCents = RateTier.entries.associateWith { tier ->
                Money.parseYuanToCents(s.rateTexts[tier] ?: "") ?: 0
            },
            hourlyRatesCents = RateTier.entries.associateWith { hourlyCents },
            leaveCoefficients = LeaveType.entries.associateWith { type ->
                (s.coefPercents[type] ?: 0) / 100.0
            },
            workSystem = s.workSystem,
            // 空/0 = 自动（应出勤天数 × 8h）；手填覆盖（10 文档 F-Z2）
            comprehensiveStandardMinutes = s.stdHoursText.trim().toDoubleOrNull()
                ?.let { (it * 60).toInt().coerceIn(0, 31 * 24 * 60) } ?: 0,
        )
        viewModelScope.launch {
            settings.setSalary(salary)
            _state.update { it.copy(saved = true) }
            onDone()
        }
    }

    private fun salaryDefaultMult(tier: RateTier): Double = when (tier) {
        RateTier.WEEKDAY -> 1.5
        RateTier.WEEKEND -> 2.0
        RateTier.STATUTORY -> 3.0
    }
}

/** 工资设定页 —— 参考布局：悬浮保存按钮、预览上移、倍率三栏、请假系数折叠 */
@Composable
fun PayrollScreen(canBack: Boolean = false, onBack: () -> Unit = {}, vm: PayrollViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page)
                .padding(bottom = 96.dp), // 留出悬浮按钮空间
        ) {
            JiabanTopBar(
                title = if (canBack) stringResource(R.string.payroll_title) else null,
                showBack = canBack,
                onBack = onBack,
            )
            Spacer(Modifier.height(Spacing.s))

            when (state.workSystem) {
                WorkSystem.STANDARD -> StandardPayrollContent(state, vm)
                WorkSystem.HOURLY -> HourlyPayrollContent(state, vm)
                WorkSystem.COMPREHENSIVE -> ComprehensivePayrollContent(state, vm)
            }
        }

        // 悬浮底部保存按钮
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.page, vertical = 12.dp),
        ) {
            Button(
                onClick = { vm.save(onDone = onBack) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(com.mdot.app.core.designsystem.Radius.pill),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(stringResource(R.string.payroll_save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun StandardPayrollContent(state: PayrollUiState, vm: PayrollViewModel) {
    SalaryModeSection(state, vm)

    Spacer(Modifier.height(Spacing.s))

    // ========== 模块2：加班倍率 横向三栏 ==========
    SectionCard {
        Column {
            Text(stringResource(R.string.payroll_overtime_multiplier), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            val previewSalary = buildPreviewSalary(state, WorkSystem.STANDARD)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RateTier.entries.forEach { tier ->
                    Column(modifier = Modifier.weight(1f)) {
                        TierField(
                            label = tier.displayName,
                            suffix = if (state.mode == SalaryMode.BASE) stringResource(R.string.payroll_suffix_x) else stringResource(R.string.payroll_suffix_yuan),
                            value = if (state.mode == SalaryMode.BASE) state.multTexts[tier].orEmpty()
                            else state.rateTexts[tier].orEmpty(),
                            onValueChange = { text ->
                                if (state.mode == SalaryMode.BASE) vm.onMult(tier, text)
                                else vm.onRate(tier, text)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val perHour = StandardPayrollStrategy.overtimeCents(previewSalary, tier, 60)
                        Text(
                            Money.yuanText(perHour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
            if (state.mode == SalaryMode.BASE) {
                Text(
                    stringResource(R.string.payroll_weekend_law_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.s))

    // ========== 模块3：请假扣薪系数（标准工时/综合工时共用） ==========
    LeaveCoefficientSection(state, vm)
}

/** 计薪模式 + 底薪 + 折算时薪（标准工时/综合工时共用，10 文档 §5.1） */
@Composable
private fun SalaryModeSection(state: PayrollUiState, vm: PayrollViewModel) {
    SectionCard {
        Column {
            Text(stringResource(R.string.payroll_mode_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == SalaryMode.BASE,
                    onClick = { vm.onMode(SalaryMode.BASE) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.payroll_mode_base), style = MaterialTheme.typography.bodyMedium) }
                SegmentedButton(
                    selected = state.mode == SalaryMode.MANUAL,
                    onClick = { vm.onMode(SalaryMode.MANUAL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.payroll_mode_manual), style = MaterialTheme.typography.bodyMedium) }
            }

            Spacer(Modifier.height(Spacing.s))

            if (state.mode == SalaryMode.MANUAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.payroll_include_base), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.includeBase, onCheckedChange = vm::onIncludeBase)
                }
                Spacer(Modifier.height(Spacing.s))
            }

            if (state.mode == SalaryMode.BASE || state.includeBase) {
                OutlinedTextField(
                shape = RoundedCornerShape(Radius.textField),
                    value = state.baseText,
                    onValueChange = vm::onBase,
                    label = { Text(stringResource(R.string.payroll_base_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleMedium,
                )
                val baseCents = Money.parseYuanToCents(state.baseText) ?: 0
                Text(
                    if (baseCents > 0) stringResource(R.string.payroll_hourly_converted, Money.yuanText(baseCents / 174))
                    else stringResource(R.string.payroll_hourly_formula),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (baseCents > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(Spacing.s))
            }

        }
    }
}

/** 请假扣薪系数 可折叠卡片（标准工时/综合工时共用） */
@Composable
private fun LeaveCoefficientSection(state: PayrollUiState, vm: PayrollViewModel) {
    val deductHasModify = state.coefPercents.any { (type, percent) ->
        percent != (type.defaultCoefficient * 100).toInt()
    }
    ExpandableCard(
        title = stringResource(R.string.payroll_leave_coef_title),
        hasModifyMark = deductHasModify,
    ) {
        Column {
            Text(
                stringResource(R.string.payroll_leave_coef_formula),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            // 双栏网格布局
            val leaveTypes = LeaveType.entries
            for (i in leaveTypes.indices step 2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                    shape = RoundedCornerShape(Radius.textField),
                        value = (state.coefPercents[leaveTypes[i]] ?: 0).toString(),
                        onValueChange = { text -> text.toIntOrNull()?.let { vm.onCoef(leaveTypes[i], it) } },
                        label = { Text(leaveTypes[i].displayName) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = { Text("%") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    if (i + 1 < leaveTypes.size) {
                        OutlinedTextField(
                        shape = RoundedCornerShape(Radius.textField),
                            value = (state.coefPercents[leaveTypes[i + 1]] ?: 0).toString(),
                            onValueChange = { text -> text.toIntOrNull()?.let { vm.onCoef(leaveTypes[i + 1], it) } },
                            label = { Text(leaveTypes[i + 1].displayName) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = { Text("%") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (i + 2 < leaveTypes.size) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 三栏倍率/单价输入框：label 可动（空且未聚焦时在框内，聚焦/有值浮到顶部），复用公共 FloatingLabelTextField。
 */
@Composable
private fun TierField(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingLabelTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = {
            Text(
                suffix,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun HourlyPayrollContent(state: PayrollUiState, vm: PayrollViewModel) {
    SectionCard {
        Column {
            Text(stringResource(R.string.payroll_hourly_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
            shape = RoundedCornerShape(Radius.textField),
                value = state.hourlyRateText,
                onValueChange = vm::onHourlyRate,
                label = { Text(stringResource(R.string.payroll_hourly_rate_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** 综合工时设定（10 文档 §5.1）：计薪模式/底薪共用 + 周期标准工时 + 两档倍率（无周末档） */
@Composable
private fun ComprehensivePayrollContent(state: PayrollUiState, vm: PayrollViewModel) {
    SalaryModeSection(state, vm)

    Spacer(Modifier.height(Spacing.s))

    // 周期标准工时：空 = 自动（应出勤天数 × 8h），手填覆盖（审批文件口径）
    SectionCard {
        Column {
            Text(stringResource(R.string.payroll_std_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
            shape = RoundedCornerShape(Radius.textField),
                value = state.stdHoursText,
                onValueChange = vm::onStdHours,
                label = { Text(stringResource(R.string.payroll_std_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (state.stdHoursText.isBlank()) {
                    stringResource(
                        R.string.payroll_std_auto_hint,
                        state.periodLabel,
                        state.autoStandardMinutes / 60,
                    )
                } else {
                    stringResource(R.string.payroll_std_manual_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(Spacing.s))

    // 加班倍率两栏：超时（周期结算）+ 法定（即时）；综合工时无周末档（D5）
    SectionCard {
        Column {
            Text(stringResource(R.string.payroll_overtime_multiplier), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            val previewSalary = buildPreviewSalary(state, WorkSystem.COMPREHENSIVE)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(RateTier.WEEKDAY to stringResource(R.string.payroll_tier_overtime), RateTier.STATUTORY to stringResource(R.string.payroll_tier_statutory)).forEach { (tier, label) ->
                    Column(modifier = Modifier.weight(1f)) {
                        TierField(
                            label = label,
                            suffix = if (state.mode == SalaryMode.BASE) stringResource(R.string.payroll_suffix_x) else stringResource(R.string.payroll_suffix_yuan),
                            value = if (state.mode == SalaryMode.BASE) state.multTexts[tier].orEmpty()
                            else state.rateTexts[tier].orEmpty(),
                            onValueChange = { text ->
                                if (state.mode == SalaryMode.BASE) vm.onMult(tier, text)
                                else vm.onRate(tier, text)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val perHour = StandardPayrollStrategy.overtimeCents(previewSalary, tier, 60)
                        Text(
                            Money.yuanText(perHour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.payroll_comp_multiplier_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(Spacing.s))

    LeaveCoefficientSection(state, vm)
}

/** 可折叠卡片 */
@Composable
private fun ExpandableCard(
    title: String,
    hasModifyMark: Boolean = false,
    defaultExpand: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpand) }
    SectionCard {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (hasModifyMark) {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                    }
                }
                Icon(
                    if (expanded) painterResource(R.drawable.ic_ms_expand_less) else painterResource(R.drawable.ic_ms_expand_more),
                    contentDescription = if (expanded) stringResource(R.string.payroll_cd_collapse) else stringResource(R.string.payroll_cd_expand),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.s))
                    content()
                }
            }
        }
    }
}

private fun buildPreviewSalary(state: PayrollUiState, workSystem: WorkSystem): SalaryConfig = SalaryConfig(
    mode = state.mode,
    baseSalaryCents = Money.parseYuanToCents(state.baseText) ?: 0,
    includeBase = state.includeBase,
    multipliers = RateTier.entries.associateWith { t -> state.multTexts[t]?.toDoubleOrNull() ?: 1.5 },
    manualRatesCents = RateTier.entries.associateWith { t -> Money.parseYuanToCents(state.rateTexts[t] ?: "") ?: 0 },
    hourlyRatesCents = RateTier.entries.associateWith { Money.parseYuanToCents(state.hourlyRateText) ?: 0 },
    workSystem = workSystem,
)

