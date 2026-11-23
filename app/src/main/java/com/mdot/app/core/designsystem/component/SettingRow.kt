package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.miuix.MiuixSettingRow
import com.mdot.app.core.designsystem.miuix.MiuixSwitchRow
import com.mdot.app.core.designsystem.miuix.SettingRowIcon
import com.mdot.app.domain.model.ThemeEngine

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
