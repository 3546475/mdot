package com.mdot.app.feature.stats

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import com.mdot.app.core.designsystem.component.InlineConfirmStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import kotlinx.coroutines.launch
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.domain.PayMonthDayBasis
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.util.Money
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanSwitch
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.jiabanFilterChipColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditItemDialog(
    item: PayMonthItem,
    insurance: InsuranceEditUi? = null,
    /** 预览金额：由调用方用引擎函数算（硬规则 12：UI 不另写公式） */
    insurancePreview: (Int, Long) -> Long = { _, _ -> 0L },
    onSave: (PayMonthItem, Int, Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(Money.yuanTrimText(item.amountCents)) }
    val cents = Money.parseYuanToCents(amount)
    // ---- 社保/公积金设置态（选项优先：不让用户去查比例表）----
    val presets = if (insurance?.social == true) SOCIAL_RATE_PRESETS else FUND_RATE_PRESETS
    var rateBp by remember { mutableStateOf(insurance?.rateBp ?: 0) }
    var rateCustom by remember {
        mutableStateOf(insurance != null && insurance.rateBp > 0 && insurance.rateBp !in presets)
    }
    var rateText by remember { mutableStateOf("") }
    var baseFollows by remember { mutableStateOf(insurance == null || insurance.baseCents <= 0) }
    var baseText by remember {
        mutableStateOf(
            if (insurance != null && insurance.baseCents > 0) Money.yuanTrimText(insurance.baseCents) else ""
        )
    }
    // ---- 「按日计算」态（v0.7.8.4：行级属性；只有「其它补贴」与用户自加行能开，见 supportsDailyRate）----
    var dailyOn by remember { mutableStateOf(item.isDailyComputed) }
    var dailyUnit by remember {
        mutableStateOf(if (item.dayRateCents > 0) Money.yuanTrimText(item.dayRateCents) else "")
    }
    var dailyBasis by remember { mutableStateOf(item.dayBasis) }
    var dailyDeductLeave by remember { mutableStateOf(item.dayDeductLeave) }
    var nightLinked by remember { mutableStateOf(item.nightShiftLinked == true) }
    val dailyUnitCents = Money.parseYuanToCents(dailyUnit)
    fun effRate(): Int =
        if (rateCustom) ((rateText.toDoubleOrNull() ?: 0.0) * 100).toInt().coerceIn(0, 10_000) else rateBp

    fun effBase(): Long =
        if (baseFollows) (insurance?.baseSalaryCents ?: 0L) else (Money.parseYuanToCents(baseText) ?: 0L)
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_edit_title)) },
        text = {
            Column {
                if (!item.builtin) {
                    FloatingLabelTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.paymonth_name_label),
                    )
                    Spacer(Modifier.height(Spacing.s))
                }
                FloatingLabelTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.paymonth_amount_label),
                    isError = cents == null,
                    // 按日计算时金额是算出来的，改它没有意义 —— 置灰并提示改日单价
                    enabled = !dailyOn,
                )
                if (dailyOn) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.paymonth_daily_amount_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- 按日计算（行级属性）：开关 + 日单价 + 天数口径 + 请假开关 ----
                // 只在**允许**按日的行上显示——出厂固定行里只有「其它补贴」允许
                // （其余出厂项要么有确定算法、要么本身是配置项，见 supportsDailyRate 的说明）
                if (item.supportsDailyRate) {
                    Spacer(Modifier.height(Spacing.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.paymonth_daily_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        JiabanSwitch(checked = dailyOn, onCheckedChange = { dailyOn = it })
                    }
                    if (dailyOn) {
                        Spacer(Modifier.height(Spacing.s))
                        FloatingLabelTextField(
                            value = dailyUnit,
                            onValueChange = { dailyUnit = it },
                            label = stringResource(R.string.paymonth_daily_unit_label),
                            isError = dailyUnitCents == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.m))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.paymonth_daily_night_link),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            JiabanSwitch(
                                checked = nightLinked,
                                onCheckedChange = { nightLinked = it },
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        if (nightLinked) {
                            Text(
                                stringResource(R.string.paymonth_daily_night_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.paymonth_daily_basis_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                DAY_BASIS_ORDER.forEach { basis ->
                                    FilterChip(
                                        colors = jiabanFilterChipColors(),
                                        selected = dailyBasis == basis,
                                        onClick = { dailyBasis = basis },
                                        label = { Text(stringResource(basis.labelRes)) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                stringResource(dailyBasis.hintRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.s))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.paymonth_daily_deduct_leave),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                JiabanSwitch(
                                    checked = dailyDeductLeave,
                                    onCheckedChange = { dailyDeductLeave = it },
                                )
                            }
                        }
                    }
                }

                // ---- 社保 / 公积金的「比例 + 基数」就设在它自己的行弹窗里 ----
                insurance?.let { ins ->
                    Spacer(Modifier.height(Spacing.m))
                    Text(
                        stringResource(if (ins.social) R.string.payroll_social_title else R.string.payroll_fund_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        presets.forEach { bp ->
                            FilterChip(
                                                                colors = jiabanFilterChipColors(),
                                selected = !rateCustom && rateBp == bp,
                                onClick = {
                                    rateBp = bp
                                    rateCustom = false
                                    // 改了比例就把金额按新口径算出来（自动优先，仍可手改）
                                    amount = Money.yuanTrimText(insurancePreview(bp, effBase()))
                                },
                                label = {
                                    Text(
                                        if (bp == 0) stringResource(R.string.payroll_rate_off)
                                        else stringResource(
                                            R.string.payroll_rate_percent,
                                            Money.ratePercentText(bp),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                        FilterChip(
                                                        colors = jiabanFilterChipColors(),
                            selected = rateCustom,
                            onClick = { rateCustom = true },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_rate_custom),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                    if (rateCustom) {
                        Spacer(Modifier.height(Spacing.s))
                        FloatingLabelTextField(
                            value = rateText,
                            onValueChange = {
                                rateText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = stringResource(R.string.payroll_rate_custom_label),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        stringResource(if (ins.social) R.string.payroll_social_hint else R.string.payroll_fund_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    // 次要选项口径（2026-09-28 定 / 2026-09-30 确认）：基数「跟随/自定义」→ 轻量 chip
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = baseFollows,
                            onClick = {
                                baseFollows = true
                                amount = Money.yuanTrimText(insurancePreview(effRate(), ins.baseSalaryCents))
                            },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_base_follow),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = !baseFollows,
                            onClick = { baseFollows = false },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_base_custom),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                    if (!baseFollows) {
                        Spacer(Modifier.height(Spacing.s))
                        FloatingLabelTextField(
                            value = baseText,
                            onValueChange = {
                                baseText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = stringResource(R.string.payroll_insurance_base_label),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (effRate() > 0) {
                            stringResource(
                                R.string.paymonth_insurance_formula,
                                Money.yuanTrimText(effBase()),
                                Money.ratePercentText(effRate()),
                                Money.yuanTrimText(insurancePreview(effRate(), effBase())),
                            )
                        } else {
                            stringResource(R.string.payroll_insurance_preview_off)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.paymonth_save),
                onClick = {
                    onSave(
                        item.copy(
                            name = if (item.builtin) item.name else name.trim(),
                            amountCents = cents ?: 0,
                            // 按日配置随行一起存；不允许按日的行强制清零（避免残留配置被引擎继续算）
                            dayRateCents = if (dailyOn && item.supportsDailyRate) (dailyUnitCents ?: 0L) else 0L,
                            dayBasis = if (dailyOn && nightLinked) PayMonthDayBasis.RECORD else dailyBasis,
                            dayDeductLeave = if (dailyOn && nightLinked) false else dailyDeductLeave,
                            nightShiftLinked = if (dailyOn && item.supportsDailyRate) nightLinked else item.nightShiftLinked,
                        ),
                        effRate(),
                        if (baseFollows) 0L else (Money.parseYuanToCents(baseText) ?: 0L),
                    )
                },
                role = JiabanButtonRole.GHOST,
                enabled = cents != null && (item.builtin || name.isNotBlank()),
            )
        },
        dismissButton = {
            Row {
                if (!item.builtin) {
                    JiabanButton(
                            text = stringResource(R.string.paymonth_delete),
                            onClick = onDelete,
                            role = JiabanButtonRole.GHOST,
                            contentColorOverride = MaterialTheme.colorScheme.error,
                        )
                }
                JiabanButton(
                    text = stringResource(R.string.paymonth_cancel),
                    onClick = onDismiss,
                    role = JiabanButtonRole.GHOST,
                )
            }
        },
    )
}

/**
 * 添加条目：**只选"加哪些项"**（预设多选 + 自定义名称），**不在这里填金额**。
 *
 * 选项优先（硬规则 12）：常用工资项都在预设里点一下；预设没有时才敲名称。
 *
 * 2026-10-09 改（用户反馈"逻辑不顺畅"）：
 * - **去掉金额输入框**：原先勾多项共用同一个金额（勾 3 项填 500 → 三行各 500），
 *   提示文案只能写"金额留空则为 0，之后逐条改"——把逐条定价做成批量操作再让用户返工。
 *   现在金额一律 0，用户到列表里点行填（那里本来就有行弹窗）；按日计算的行更不该在添加时填金额。
 * - **预设与自定义不再互斥**：以前敲名称会**静默清空**已选预设（反之亦然），
 *   用户先挑几项、再想补一个自定义名，前面的选择无声消失。现在两者可以同时用。
 * - **已存在的项置灰**：预设里可能与出厂固定行同名（如补贴组的「全勤奖」），
 *   点了会加出重复行；现在已有的直接禁用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddItemDialog(
    presets: List<String>,
    /** 本组**已有**的行名（预设命中即禁用，避免加出重复行） */
    existingNames: Set<String>,
    /** 本组**自建**的预设名（预设管理面板里可删的就是这些） */
    customPresetNames: List<String>,
    /** 新建一条自定义预设（持久化）；返回 true 表示真的写入了（可自动勾选） */
    onCreatePreset: suspend (String) -> Boolean,
    /** 删掉一条自定义预设（只从候选清单移除，不动已录入的行） */
    onDeletePreset: suspend (String) -> Boolean,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    // 只在点「+」时才出现的建预设弹窗（叠在覆盖层栈上）
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val names = picked.toList()
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_add_title)) },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    presets.forEach { preset ->
                        val alreadyThere = preset in existingNames
                        val on = preset in picked
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            // 已有同名行 → 不可选（置灰），否则会加出重复项
                            enabled = !alreadyThere,
                            selected = on,
                            onClick = { picked = if (on) picked - preset else picked + preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    // 「+」：自建一条预设并持久化——建一次，以后每个月点一下就出来。
                    // （原来是就地敲一个名称，但那只是个一次性行名，用户下次还得重敲）
                    FilterChip(
                        colors = jiabanFilterChipColors(),
                        selected = false,
                        onClick = { naming = true },
                        label = {
                            Text(
                                stringResource(R.string.paymonth_add_new_preset),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_add_multi_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            JiabanButton(
                text = if (names.size > 1) stringResource(R.string.paymonth_add_count, names.size)
                    else stringResource(R.string.paymonth_save),
                onClick = { onSave(names) },
                role = JiabanButtonRole.GHOST,
                enabled = names.isNotEmpty(),
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.paymonth_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
    if (naming) {
        ManagePresetsDialog(
            /** 本组自建的预设（可删的就是这些；内置项写死在 string-array 里，不在此列） */
            custom = customPresetNames,
            onCreate = { name ->
                scope.launch {
                    // 写入成功才勾选（同名/空名会被拒）
                    if (onCreatePreset(name)) picked = picked + name.trim()
                }
            },
            onDelete = { name -> scope.launch { onDeletePreset(name) } },
            onDismiss = { naming = false },
        )
    }
}

/**
 * 预设管理：**增与删放在同一处**（用户 2026-10-09 定：新增的预设原先没有删除入口）。
 *
 * 为什么不做成"chip 上挂个 ✕"或"长按删"：
 * - chip 上的 ✕ 紧挨点选区域 → 易误删，且自建项一多 chip 行会很吵；
 * - 长按在本项目已被否过一次（记月汇总卡曾用长按切换，用户反馈"不知道能长按"）。
 *
 * 删除走 [InlineConfirmButton] 的 Compact 式样（硬规则 11：原地确认、不弹二次确认框），
 * 与「班次管理」页删自定义班次同款。因为删完这一行就消失了，**只能"只确认"**（onUndo = null）。
 */
@Composable
internal fun ManagePresetsDialog(
    custom: List<String>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    JiabanAlertDialog(
        containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_manage_preset_title)) },
        text = {
            Column {
                // ---- 新建 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⚠️ 不要给这个字段强压高度：M3 的 OutlinedTextField 内部按 56dp 排版，
                    // 压到 40dp 会把占位文字**垂直裁掉**（实测踩过两次）。这里旁边是按钮不是 chip，
                    // 没有"要和 chip 同量纲"的问题，用自然高度即可。
                    FloatingLabelTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "",
                        placeholder = stringResource(R.string.paymonth_add_custom_label),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    JiabanButton(
                        text = stringResource(R.string.paymonth_add_preset_action),
                        onClick = {
                            onCreate(name)
                            name = ""
                        },
                        role = JiabanButtonRole.GHOST,
                        enabled = name.isNotBlank(),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_manage_preset_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ---- 已建的（本组）----
                if (custom.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    custom.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        ) {
                            Text(
                                preset,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            InlineConfirmButton(
                                idleText = stringResource(R.string.paymonth_delete),
                                confirmText = stringResource(R.string.paymonth_manage_preset_confirm),
                                cancelText = stringResource(R.string.paymonth_cancel),
                                undoText = stringResource(R.string.paymonth_undo),
                                onConfirm = { onDelete(preset) },
                                style = InlineConfirmStyle.Compact,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.paymonth_done),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
