package com.mdot.app.feature.site

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.feature.record.toText
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.domain.model.SiteOtMode
import com.mdot.app.domain.model.SiteProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.mdot.app.core.util.AppResult
import com.mdot.app.core.util.onSuccess
import com.mdot.app.core.util.onFailure
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 新建（未设置）项目的默认点工标准展示值：8 小时 = 1 个工 = 260 元 */
private const val DEFAULT_BASE_HOURS_TEXT = "8"
private const val DEFAULT_RATE_YUAN_TEXT = "260"
private const val DEFAULT_OT_BASE_HOURS_TEXT = "6"

data class SiteProjectEditUi(
    val id: Long = 0,
    val name: String = "",
    val baseHoursText: String = DEFAULT_BASE_HOURS_TEXT,
    val rateYuanText: String = DEFAULT_RATE_YUAN_TEXT,
    val otMode: SiteOtMode = SiteOtMode.BY_DAY,
    val otBaseHoursText: String = DEFAULT_OT_BASE_HOURS_TEXT,
    val otHourlyYuanText: String = "",
    val archived: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SiteProjectEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val siteRepo: SiteRepository,
) : ViewModel() {

    private val projectId: Long = when (val raw = checkNotNull(savedStateHandle["projectId"])) {
        is Long -> raw
        is Number -> raw.toLong()
        is String -> raw.toString().toLong()
        else -> error("projectId 参数类型异常")
    }

    private val _state = MutableStateFlow(SiteProjectEditUi(id = projectId))
    val state: StateFlow<SiteProjectEditUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            siteRepo.getProject(projectId)?.let { p -> _state.value = p.toUi() }
        }
    }

    /** 未设置（0 值）的项目回落到默认展示值 8 小时 / 260 元，避免出现 0 元默认 */
    private fun SiteProject.toUi() = SiteProjectEditUi(
        id = id, name = name,
        baseHoursText = if (baseMinutes > 0) trimHours(baseMinutes) else DEFAULT_BASE_HOURS_TEXT,
        rateYuanText = if (dailyRateCents > 0) yuanTextShort(dailyRateCents) else DEFAULT_RATE_YUAN_TEXT,
        otMode = SiteOtMode.valueOf(otMode),
        otBaseHoursText = if (otBaseMinutes > 0) trimHours(otBaseMinutes) else DEFAULT_OT_BASE_HOURS_TEXT,
        otHourlyYuanText = if (otHourlyCents > 0) yuanTextShort(otHourlyCents) else "",
        archived = archived,
    )

    private fun trimHours(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60}" else String.format(java.util.Locale.US, "%.1f", minutes / 60.0)

    private fun yuanTextShort(cents: Long): String {
        val text = com.mdot.app.domain.util.Money.yuanText(cents).replace(",", "")
        return if (text.endsWith(".00")) text.removeSuffix(".00") else text
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(30), saved = false, error = null) }

    /** 点工标准弹窗确认：按模式映射到 otMode/otBaseMinutes/otHourlyCents（BY_DAY 用日价，BY_HOUR 用固定时薪） */
    fun applyStandard(
        baseHours: String,
        rateYuan: String,
        mode: SiteOtMode,
        otBaseHours: String,
        otHourlyYuan: String,
    ) = _state.update {
        it.copy(
            baseHoursText = baseHours,
            rateYuanText = rateYuan,
            otMode = mode,
            otBaseHoursText = otBaseHours,
            otHourlyYuanText = otHourlyYuan,
            saved = false,
            error = null,
        )
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "项目名不能为空") }
            return
        }
        val baseMinutes = ((s.baseHoursText.toDoubleOrNull() ?: 8.0) * 60).toInt().coerceIn(60, 1440)
        val rate = com.mdot.app.domain.util.Money.parseYuanToCents(s.rateYuanText) ?: 0
        val otBaseMinutes = ((s.otBaseHoursText.toDoubleOrNull() ?: 6.0) * 60).toInt().coerceIn(60, 1440)
        val otHourly = com.mdot.app.domain.util.Money.parseYuanToCents(s.otHourlyYuanText) ?: 0
        viewModelScope.launch {
            val current = siteRepo.getProject(s.id) ?: return@launch
            siteRepo.updateProject(
                current.copy(
                    name = s.name, baseMinutes = baseMinutes, dailyRateCents = rate,
                    otMode = s.otMode.name, otBaseMinutes = otBaseMinutes, otHourlyCents = otHourly,
                )
            ).onSuccess {
                // 仅置 saved，由 LaunchedEffect 统一触发 onBack（避免双重 popBackStack）
                _state.update { it.copy(saved = true) }
            }.onFailure { err: com.mdot.app.core.util.AppError ->
                _state.update { it.copy(error = err.toText()) }
            }
        }
    }

    /** 有数据 → 归档；空项目 → 归档同样生效（数据保留策略统一：仅归档不物理删除，D1-rev） */
    fun archiveOrDelete(onDone: () -> Unit) = viewModelScope.launch {
        siteRepo.archiveOrDeleteProject(projectId)
        onDone()
    }
}

/**
 * 项目设置页（12 文档 F-S2；v0.6.2 改版为极简版式）：
 * 项目名输入框 + 「点工标准设置」按钮 item（点开弹窗两行：上班 / 加班，含按小时↔按工天切换）+ 保存 + 归档。
 */
@Composable
fun SiteProjectEditScreen(
    onBack: () -> Unit,
    vm: SiteProjectEditViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
    var showStandard by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.site_project_settings), onBack = onBack)
        Spacer(Modifier.height(Spacing.s))

        // ---- 项目名 ----
        SectionCard {
            OutlinedTextField(
                shape = RoundedCornerShapeField,
                value = state.name,
                onValueChange = vm::onName,
                label = { Text(stringResource(R.string.site_project_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.m))

        // ---- 点工标准设置（弹窗编辑） ----
        SectionCard(onClick = { showStandard = true }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_paid), null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.site_project_standard_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        standardSummary(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.l))

        Button(
            onClick = { vm.save(onDone = onBack) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.site_save)) }

        Spacer(Modifier.height(Spacing.m))
        TextButton(
            onClick = { confirmArchive = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.site_project_archive),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    if (showStandard) {
        ProjectStandardDialog(
            initial = state,
            onApply = { baseHours, rateYuan, mode, otBaseHours, otHourlyYuan ->
                vm.applyStandard(baseHours, rateYuan, mode, otBaseHours, otHourlyYuan)
                showStandard = false
            },
            onDismiss = { showStandard = false },
        )
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.site_project_archive_title)) },
            text = { Text(stringResource(R.string.site_project_archive_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    vm.archiveOrDelete(onDone = onBack)
                }) { Text(stringResource(R.string.site_project_archive), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.site_dialog_cancel)) }
            },
        )
    }
}

/** 入口 item 的摘要文案：「上班 8 小时 = 1 个工 = 260 元 · 加班…」 */
@Composable
private fun standardSummary(state: SiteProjectEditUi): String {
    val hours = state.baseHoursText.ifBlank { DEFAULT_BASE_HOURS_TEXT }
    val rate = state.rateYuanText.ifBlank { DEFAULT_RATE_YUAN_TEXT }
    return if (state.otMode == SiteOtMode.BY_HOUR) {
        stringResource(
            R.string.site_project_summary_hour_format,
            hours, rate, state.otHourlyYuanText.ifBlank { "—" },
        )
    } else {
        stringResource(
            R.string.site_project_summary_day_format,
            hours, rate, state.otBaseHoursText.ifBlank { DEFAULT_OT_BASE_HOURS_TEXT },
        )
    }
}

/**
 * 点工标准弹窗：等式行大字版——「1 个工 = [8] 小时 = [260] 元」，
 * 数字用大号加粗 primary（与记工页工钱/工程量同风格），说明文字小号，一句读下来直观不折行。
 * 加班段：胶囊切换按加班工/按小时算，等式随模式切换。
 * 确认时统一映射：按工天=BY_DAY+otBaseMinutes，按小时=BY_HOUR+otHourlyCents。
 */
@Composable
internal fun ProjectStandardDialog(
    initial: SiteProjectEditUi,
    onApply: (baseHours: String, rateYuan: String, mode: SiteOtMode, otBaseHours: String, otHourlyYuan: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var baseHours by remember { mutableStateOf(initial.baseHoursText) }
    var rateYuan by remember { mutableStateOf(initial.rateYuanText) }
    var mode by remember { mutableStateOf(initial.otMode) }
    var otBaseHours by remember { mutableStateOf(initial.otBaseHoursText) }
    var otHourlyYuan by remember { mutableStateOf(initial.otHourlyYuanText) }

    SiteBottomSheet(
        title = stringResource(R.string.site_project_standard_settings),
        onDismiss = onDismiss,
    ) {
        // ① 上班：1 个工 = [X] 小时 = [Y] 元
        Text(
            stringResource(R.string.site_work),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        EquationCard(iconRes = R.drawable.ic_ms_more_time) {
            EqText(stringResource(R.string.site_std_eq_work))
            EqField(baseHours, { baseHours = it }, 56.dp)
            EqText(stringResource(R.string.site_std_eq_hours))
            EqField(rateYuan, { rateYuan = it }, 96.dp)
            EqText(stringResource(R.string.site_std_eq_yuan))
        }
        Spacer(Modifier.height(Spacing.m))

        // ② 加班：计钱方式胶囊切换（居中，与点工/包工切换同款）
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
            ) {
                SegmentPill(stringResource(R.string.site_ot_by_day), mode == SiteOtMode.BY_DAY) {
                    mode = SiteOtMode.BY_DAY
                }
                SegmentPill(stringResource(R.string.site_ot_by_hour), mode == SiteOtMode.BY_HOUR) {
                    mode = SiteOtMode.BY_HOUR
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        if (mode == SiteOtMode.BY_DAY) {
            EquationCard(iconRes = R.drawable.ic_ms_more_time) {
                EqText(stringResource(R.string.site_std_eq_ot_day))
                EqField(otBaseHours, { otBaseHours = it }, 56.dp)
                EqText(stringResource(R.string.site_std_eq_ot_hours))
            }
        } else {
            EquationCard(iconRes = R.drawable.ic_ms_paid) {
                EqText(stringResource(R.string.site_std_eq_ot_hour))
                EqField(otHourlyYuan, { otHourlyYuan = it }, 96.dp)
                EqText(stringResource(R.string.site_std_eq_yuan))
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            stringResource(R.string.site_project_standard_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_cancel))
            }
            Button(
                onClick = { onApply(baseHours, rateYuan, mode, otBaseHours, otHourlyYuan) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.site_dialog_ok)) }
        }
    }
}

/** 等式卡：图标方块 + 一行等式（文字与可编辑大字数字交替） */
@Composable
private fun EquationCard(
    iconRes: Int,
    content: @Composable RowScope.() -> Unit,
) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

/** 等式说明文字 */
@Composable
private fun EqText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 等式中的可编辑大字数字（只允许数字与小数点） */
@Composable
private fun EqField(value: String, onValueChange: (String) -> Unit, width: Dp) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.width(width),
    )
}

private val RoundedCornerShapeField = RoundedCornerShape(Radius.textField)
