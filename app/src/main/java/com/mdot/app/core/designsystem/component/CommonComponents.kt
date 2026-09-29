package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.miuix.MiuixSectionCard
import com.mdot.app.core.designsystem.miuix.MiuixSettingRow
import com.mdot.app.core.designsystem.miuix.MiuixSwitchRow
import com.mdot.app.core.designsystem.miuix.SettingRowIcon
import com.mdot.app.domain.model.ThemeEngine

/** 统一卡片容器：16dp 圆角、surfaceContainer 色阶（03 文档 §3.3）；可点卡片带按压缩放；containerColor 可覆盖底色（如工钱 hero 卡） */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    // MIUIX 引擎：容器改 miuix Card（库的圆角/底色/层级语言）；显式覆盖底色时才把颜色带过去
    if (Radius.engine == ThemeEngine.MIUIX) {
        val override = if (containerColor == MaterialTheme.colorScheme.surfaceContainer) null else containerColor
        MiuixSectionCard(modifier, onClick, override, content)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier),
        shape = engineShape(Radius.card),
        color = containerColor,
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interaction,
    ) {
        Box(Modifier.padding(Spacing.l)) { content() }
    }
}

/** 空状态（03 文档 §6；M3 Expressive：图标置于圆形 tonal 底上） */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    hint: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(IconBoxSpec.hero.box)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconBoxSpec.hero.icon),
            )
        }
        Spacer(Modifier.height(Spacing.m))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.l))
            JiabanButton(text = actionText, onClick = onAction, role = JiabanButtonRole.GHOST)
        }
    }
}

/** 二次确认对话框（危险操作主按钮 error 色） */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = stringResource(R.string.ds_confirm_delete),
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            JiabanButton(
                text = confirmText,
                onClick = onConfirm,
                role = JiabanButtonRole.GHOST,
                contentColorOverride = if (danger) MaterialTheme.colorScheme.error else null,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.ds_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}

/** 设置页列表行：标题 + 右侧值/箭头；可点行带按压反馈 */
@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    // MIUIX 引擎且为可点行：交 miuix-preference 的 ArrowPreference（箭头/按压/字体走库）；
    // 不可点行回落下方 MD3 行（库组件恒画箭头，不可点行不该有箭头）
    if (Radius.engine == ThemeEngine.MIUIX && onClick != null) {
        MiuixSettingRow(title, value, icon, onClick, modifier, trailing)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interaction, pressedScale = 0.98f)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                            onClick()
                        }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Spacing.l, vertical = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingRowIcon(icon)
            Spacer(Modifier.size(Spacing.l))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}

/**
 * 开关设置行：**整行可点**（`toggleable(role = Role.Switch)`，触达区 = 整行宽 × 48dp 高）。
 *
 * ⚠️ `Switch` 必须传 `onCheckedChange = null`：语义由外层 `toggleable` 承担。
 * 两边都挂回调会让无障碍读出**两个重复的开关节点**，且开关本体与整行各响一次。
 *
 * 外观页（动态取色 / 隐藏农历日期）与底栏配置页（毛玻璃 / 仅图标 / 按钮右置 / 固定长度）
 * 共用本组件——两页的开关行样式与触达行为从此不可能再走偏。
 *
 * @param desc 可选副行（labelSmall / onSurfaceVariant），说明放在标题下方而非右侧
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
) {
    // MIUIX 引擎：交 miuix-preference 的 SwitchPreference（整行 Role.Switch + 库内 Switch）
    if (Radius.engine == ThemeEngine.MIUIX) {
        MiuixSwitchRow(title, checked, onCheckedChange, modifier, desc)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.s))
        JiabanSwitch(checked = checked, onCheckedChange = null)
    }
}

/** 档位/单价行（记录弹层内）："平时加班 1.5倍 · 13.79元/小时 ›" */
@Composable
fun TierRow(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (onClick != null) {
            Text("›", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 浮动 label 输入框（全 App 输入框统一入口）：label 在空且未聚焦时缩在框内作占位，聚焦或有值后浮到顶边框
 * （M3 标准动效）；输入框高度固定，不随 label 状态变化。
 * 基于 OutlinedTextField 实现（MD3）/ 库 basic.TextField（MIUIX），圆角统一走 [Radius.textField]；
 * [suffix] 渲染为右侧尾随内容。
 *
 * 2026-09-30 拓宽 API：加入 placeholder / enabled / readOnly / isError / maxLines / leadingIcon / textStyle，
 * 目的是让其余 20 处“裸 OutlinedTextField”能逐步迁进来、统一两引擎观感。
 * ⚠️ 库端两处语义差异（已在下面映射）：
 *  1. **库没有独立 placeholder**——无标签时把 placeholder 当 label + `useLabelAsPlaceholder` 表达；
 *  2. **库没有 isError**——用 `textFieldColors(labelColor/borderColor = error)` 表达。
 */
@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    suffix: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    /** 辅助/报错文案（M3 supportingText）；MIUIX 无此参数属性 → 包一层 Column 画在输入框下方 */
    supportingText: (@Composable () -> Unit)? = null,
    /** 自定义外形；null = 本 App 统一圆角 [Radius.textField]。⚠️ MIUIX 只能取圆角值，非圆角形状会退化为该令牌 */
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val style = textStyle ?: MaterialTheme.typography.titleMedium
    // 引擎分发：MIUIX 用库 TextField（库自带 label 浮动/占位行为，与 M3 浮动标签等价）；
    // MD3 保持 M3 OutlinedTextField（原样，不动被点名的另一侧）
    if (com.mdot.app.core.designsystem.Radius.engine == com.mdot.app.domain.model.ThemeEngine.MIUIX) {
        val cs = MaterialTheme.colorScheme
        val field: @Composable () -> Unit = {
            top.yukonga.miuix.kmp.basic.TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = if (label.isNotBlank()) label else (placeholder ?: ""),
                // 空且未聚焦时把 label 当占位（≈ M3 浮动标签的空态），聚焦/有值后上浮
                useLabelAsPlaceholder = true,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = style,
                keyboardOptions = keyboardOptions,
                singleLine = maxLines == 1,
                maxLines = maxLines,
                leadingIcon = leadingIcon,
                trailingIcon = suffix,
                colors = if (isError) {
                    top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
                        backgroundColor = cs.surfaceContainerHigh,
                        labelColor = cs.error,
                        borderColor = cs.error,
                    )
                } else {
                    // 2026-09-30 用户口径「输入框颜色太多」：
                    // ① **底色改中性**——库默认 secondaryContainer 属**强调色角色**（由本 App 配色方案注入）
                    //   ⇒ 每个输入框都带一层强调色调，这才是「花」的主因（用户第二轮反馈）；
                    //   改用本 App 的中性凹陷灰 surfaceContainerHigh（浅#F0F0F0 / 深#2D2D2D）。
                    // ② 标签用中性次级文字色（onSurfaceVariant）。
                    // 聚焦边框/光标仍保留主题色（用户认可的那一抹聚焦反馈）。
                    top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
                        backgroundColor = cs.surfaceContainerHigh,
                        labelColor = cs.onSurfaceVariant,
                    )
                },
            )
        }
        // 库无 supportingText：自行包一层 Column 画在下方（保持与 MD3 同信息层级）
        if (supportingText != null) {
            Column(modifier = Modifier) { field(); supportingText() }
        } else {
            field()
        }
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // 标签锁单行：三栏窄容器下避免「平时」被后缀挤成两行撑高卡片
        label = if (label.isNotBlank()) {
            { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip) }
        } else {
            null
        },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier,
        shape = shape ?: engineShape(Radius.textField),
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
        singleLine = maxLines == 1,
        maxLines = maxLines,
        textStyle = style,
        leadingIcon = leadingIcon,
        trailingIcon = suffix,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
    )
}

/** 键值展示行（汇总卡等） */
@Composable
fun KeyValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    animatedCents: Long? = null,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (animatedCents != null) {
            AnimatedMoneyText(
                animatedCents,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                label = "keyValue",
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}
