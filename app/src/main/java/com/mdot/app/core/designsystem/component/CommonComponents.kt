package com.mdot.app.core.designsystem.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** 统一卡片容器：16dp 圆角、surfaceContainer 色阶（03 文档 §3.3）；可点卡片带按压缩放；containerColor 可覆盖底色（如工钱 hero 卡） */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier),
        shape = RoundedCornerShape(Radius.card),
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
            TextButton(onClick = onAction) { Text(actionText) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_cancel)) } },
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
            // M3 Expressive：行图标置于圆角 tonal 小底上，增强节奏感（盒+图标成对，见 IconBoxSpec）
            Box(
                modifier = Modifier
                    .size(IconBoxSpec.tile.box)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconBoxSpec.tile.icon),
                )
            }
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
        Switch(checked = checked, onCheckedChange = null)
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
 * 浮动 label 输入框：label 在空且未聚焦时缩在框内作占位，聚焦或有值后浮到顶边框（M3 标准动效）；
 * 输入框高度固定，不随 label 状态变化。
 * 基于 OutlinedTextField 实现，圆角统一 20dp（Radius.textField）；[suffix] 渲染为右侧尾随内容。
 */
@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    suffix: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // 标签锁单行：三栏窄容器下避免「平时」被后缀挤成两行撑高卡片
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip) },
        modifier = modifier,
        shape = RoundedCornerShape(Radius.textField),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        trailingIcon = suffix,
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
