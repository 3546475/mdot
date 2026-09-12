package com.mdot.app.feature.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.util.Money

/**
 * 记月页（统计页第 1 页）：月度工资单编辑。
 * 布局仿设计稿：四分组卡（基本/补贴/扣款/其他，头部合计 + 折叠），行点击编辑金额，
 * 补贴/扣款组可添加行（出厂行不可删），底部「导入上月」。
 */
@Composable
fun PayMonthContent(vm: PayMonthViewModel = hiltViewModel()) {
    val month by vm.month.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val isSite by vm.isSite.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<PayGroup, PayMonthItem>?>(null) }
    var adding by remember { mutableStateOf<PayGroup?>(null) }
    var importing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = AdaptiveSpecs.contentMaxWidth)
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.m))

        // ---- 月份导航 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.KeyboardArrowLeft, stringResource(R.string.paymonth_prev_cd))
            }
            Text(
                stringResource(R.string.paymonth_month, month.year, month.monthValue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 120.dp),
            )
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.Filled.KeyboardArrowRight, stringResource(R.string.paymonth_next_cd))
            }
        }
        Spacer(Modifier.height(Spacing.s))

        PayGroupCard(
            PayGroup.BASIC, sheet.basic,
            onEdit = { editing = PayGroup.BASIC to it },
            extraAction = if (!isSite) {
                {
                    TextButton(onClick = { syncing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.paymonth_sync))
                    }
                }
            } else null,
        )
        Spacer(Modifier.height(Spacing.m))
        PayGroupCard(
            PayGroup.SUBSIDY, sheet.subsidy,
            onEdit = { editing = PayGroup.SUBSIDY to it },
            onAdd = { adding = PayGroup.SUBSIDY },
        )
        Spacer(Modifier.height(Spacing.m))
        PayGroupCard(
            PayGroup.DEDUCTION, sheet.deduction,
            onEdit = { editing = PayGroup.DEDUCTION to it },
            onAdd = { adding = PayGroup.DEDUCTION },
        )
        Spacer(Modifier.height(Spacing.m))
        PayGroupCard(PayGroup.OTHER, sheet.other, onEdit = { editing = PayGroup.OTHER to it })
        Spacer(Modifier.height(Spacing.l))

        Button(
            onClick = { importing = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(Radius.pill),
        ) {
            Text(stringResource(R.string.paymonth_import_prev))
        }
        Spacer(Modifier.height(Spacing.l))
    }

    editing?.let { (group, item) ->
        EditItemDialog(
            item = item,
            onSave = { updated ->
                vm.saveItem(group, updated)
                editing = null
            },
            onDelete = {
                vm.removeItem(group, item.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    adding?.let { group ->
        AddItemDialog(
            onSave = { name, cents ->
                vm.addItem(group, name, cents)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text(stringResource(R.string.paymonth_import_title)) },
            text = { Text(stringResource(R.string.paymonth_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.importPrevMonth()
                    importing = false
                }) { Text(stringResource(R.string.paymonth_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { importing = false }) { Text(stringResource(R.string.paymonth_cancel)) }
            },
        )
    }
    if (syncing) {
        AlertDialog(
            onDismissRequest = { syncing = false },
            title = { Text(stringResource(R.string.paymonth_sync_title)) },
            text = { Text(stringResource(R.string.paymonth_sync_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.syncFromRecords()
                    syncing = false
                }) { Text(stringResource(R.string.paymonth_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { syncing = false }) { Text(stringResource(R.string.paymonth_cancel)) }
            },
        )
    }
}

/** 分组卡：头部（色块符号 + 组名 + 合计 + 折叠箭头）+ 条目行 + 可选添加/额外动作按钮 */
@Composable
private fun PayGroupCard(
    group: PayGroup,
    items: List<PayMonthItem>,
    onEdit: (PayMonthItem) -> Unit,
    onAdd: (() -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(true) }
    val total = items.sumOf { it.amountCents }
    val negative = group == PayGroup.DEDUCTION || group == PayGroup.OTHER
    val container = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.primary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.tertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.error
        PayGroup.OTHER -> MaterialTheme.colorScheme.secondary
    }
    val onContainer = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.onPrimary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.onTertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.onError
        PayGroup.OTHER -> MaterialTheme.colorScheme.onSecondary
    }
    val labelRes = when (group) {
        PayGroup.BASIC -> R.string.paymonth_group_basic
        PayGroup.SUBSIDY -> R.string.paymonth_group_subsidy
        PayGroup.DEDUCTION -> R.string.paymonth_group_deduction
        PayGroup.OTHER -> R.string.paymonth_group_other
    }
    val glyph = when (group) {
        PayGroup.BASIC -> "¥"
        PayGroup.SUBSIDY -> "+"
        else -> "−"
    }
    val addRes = when (group) {
        PayGroup.SUBSIDY -> R.string.paymonth_add_subsidy
        else -> R.string.paymonth_add_deduction
    }

    SectionCard {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, style = MaterialTheme.typography.labelLarge, color = onContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (negative) "-${Money.yuanTrimText(total)}" else Money.yuanTrimText(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                items.forEachIndexed { index, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (negative) "-${Money.yuanTrimText(item.amountCents)}" else Money.yuanTrimText(item.amountCents),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                onAdd?.let {
                    TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(addRes))
                    }
                }
                extraAction?.invoke()
            }
        }
    }
}

/** 编辑条目：出厂行只可改金额，新增行可改名称/金额/删除 */
@Composable
private fun EditItemDialog(
    item: PayMonthItem,
    onSave: (PayMonthItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(Money.yuanTrimText(item.amountCents)) }
    val cents = Money.parseYuanToCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_edit_title)) },
        text = {
            Column {
                if (!item.builtin) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.paymonth_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.textField),
                    )
                    Spacer(Modifier.height(Spacing.s))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.paymonth_amount_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                    isError = cents == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && (item.builtin || name.isNotBlank()),
                onClick = {
                    onSave(item.copy(name = if (item.builtin) item.name else name.trim(), amountCents = cents ?: 0))
                },
            ) { Text(stringResource(R.string.paymonth_save)) }
        },
        dismissButton = {
            Row {
                if (!item.builtin) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.paymonth_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_cancel)) }
            }
        },
    )
}

/** 添加条目：名称 + 金额 */
@Composable
private fun AddItemDialog(
    onSave: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val cents = Money.parseYuanToCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.paymonth_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                )
                Spacer(Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.paymonth_amount_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                    isError = amount.isNotBlank() && cents == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && cents != null,
                onClick = { onSave(name, cents ?: 0) },
            ) { Text(stringResource(R.string.paymonth_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_cancel)) }
        },
    )
}
