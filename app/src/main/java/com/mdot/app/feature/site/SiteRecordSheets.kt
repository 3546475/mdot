package com.mdot.app.feature.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.feature.record.DurationGrid
import java.time.LocalDate

/**
 * 记工页弹层集合：多选日期、选工天/选小时（复用记加班时长网格）、备注、工量单位选择。
 * 容器统一走 [SiteBottomSheet]（自实现，勿改回 M3 ModalBottomSheet）。
 */

/**
 * 工量单位候选（v0.6.2 D-单位扩展）：非 Composable 数据用 labelRes 携带，
 * 显示点 stringResource 解析（硬规则 10）。
 */
private val pieceUnitOptions = listOf(
    R.string.site_unit_sq_meter,
    R.string.site_unit_cubic,
    R.string.site_unit_ton,
    R.string.site_unit_meter,
    R.string.site_unit_inch,
    R.string.site_unit_piece,
    R.string.site_unit_time,
    R.string.site_unit_day,
    R.string.site_unit_block,
    R.string.site_unit_group,
    R.string.site_unit_machine,
    R.string.site_unit_bundle,
    R.string.site_unit_case,
    R.string.site_unit_item,
    R.string.site_unit_plant,
    R.string.site_unit_household,
    R.string.site_unit_car,
    R.string.site_unit_sheet,
    R.string.site_unit_other,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MultiDateDialog(
    primary: LocalDate,
    selected: List<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val days = (1..today.dayOfMonth).map { today.withDayOfMonth(it) }.filter { it != primary }
    SiteBottomSheet(title = stringResource(R.string.site_multi_select), onDismiss = onDismiss) { dismiss ->
        Text(
            stringResource(R.string.site_multi_select_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            days.forEach { d ->
                FilterChip(
                    selected = d in selected,
                    onClick = { onToggle(d) },
                    label = { Text("${d.dayOfMonth}") },
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Button(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.site_dialog_ok))
        }
    }
}

/** 选工天弹窗：同记加班的时长网格（预设 0.5–3 工 + 末位“…”自定义格），点格即生效并关窗 */
@Composable
internal fun DayCountDialog(
    title: String,
    current: Int,
    unitMinutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SiteBottomSheet(title = title, onDismiss = onDismiss) { _ ->
        DurationGrid(
            selectedHours = current.takeIf { it > 0 }?.let { it / unitMinutes.toDouble() },
            presetSteps = 6,
            onPreset = { onPick((it * unitMinutes).toInt()) },
            onCustomCommit = { text ->
                text.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { onPick((it * unitMinutes).toInt()) }
            },
        )
    }
}

@Composable
internal fun HourInputDialog(
    title: String,
    initialHours: Double?,
    onPick: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // 复用记加班弹窗的时长网格（预设 0.5–24 + 末位“…”自定义输入格），点格即生效并关窗
    SiteBottomSheet(title = title, onDismiss = onDismiss) { _ ->
        DurationGrid(
            selectedHours = initialHours?.takeIf { it > 0.0 },
            onPreset = onPick,
            onCustomCommit = { text -> text.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onPick) },
        )
    }
}

@Composable
internal fun NoteDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    SiteBottomSheet(title = stringResource(R.string.site_note), onDismiss = onDismiss) { dismiss ->
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(100) },
            placeholder = { Text(stringResource(R.string.site_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.textField),
        )
        Spacer(Modifier.height(Spacing.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = dismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_cancel))
            }
            Button(onClick = { onConfirm(text) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_ok))
            }
        }
    }
}

/**
 * 工量单位选择底部弹层：与选工天/选小时同款 SiteBottomSheet 容器（同款滑入动画/面板结构/取消确定），
 * 内容为现有单位候选（点选暂存，确定生效）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitPickerSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf(current) }
    SiteBottomSheet(title = stringResource(R.string.site_unit_picker_title), onDismiss = onDismiss) { dismiss ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            pieceUnitOptions.forEach { labelRes ->
                val unit = stringResource(labelRes)
                SegmentPill(unit, selected = unit == pending) { pending = unit }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = dismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_cancel))
            }
            Button(onClick = { onSelect(pending) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_ok))
            }
        }
    }
}
