package com.mdot.app.core.designsystem.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.engineIsMiuix
import com.mdot.app.core.designsystem.engineShape
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mdot.app.core.designsystem.ButtonSpec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * miuix-preference 组件桥（**仅 MIUIX 引擎**由 [com.mdot.app.core.designsystem.component.SettingRow] /
 * [com.mdot.app.core.designsystem.component.SwitchRow] / [SectionCard] 在引擎分支里调用）。
 *
 * 组合方式照抄 miuix 官方示例（docs/demo 的 ArrowPreferenceDemo / SwitchPreferenceDemo）：
 * `Card { ArrowPreference(...) / SwitchPreference(...) }`。
 *
 * 行内边距：**横向不补、纵向走本 App 令牌**——卡片（[MiuixSectionCard]）已给 16dp 横向内边距，
 * 行再补就会变成 32dp（MD3 行的旧观感）；只补纵向后**行距卡片边正好 16dp** = MIUI 规范间距。
 * 副作用（有意接受）：行必须住在卡片里才有横向留白。
 */
private val MiuixRowInsideMargin = PaddingValues(vertical = Spacing.l)

/** 设置行图标（行内 tonal 小底盒，与 MD3 行同款——两引擎共用，避免样式漂移） */
@Composable
fun SettingRowIcon(icon: Painter) {
    Box(
        modifier = Modifier
            .size(IconBoxSpec.tile.box)
            .background(MaterialTheme.colorScheme.secondaryContainer, engineShape(Radius.small)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconBoxSpec.tile.icon),
        )
    }
}

/** 行图标（MIUIX：**裸图标无底色盒**——MIUI 真实设置页的做法；尺寸沿用本 App 图标令牌） */
@Composable
private fun MiuixPlainIcon(icon: Painter) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(IconBoxSpec.tile.icon),
    )
}

/**
 * 配置行图标（首页卡片配置 / 底栏槽位配置共用）：
 * - MIUIX：**裸图标**——与「我的」页（[MiuixSettingRow] 的 [MiuixPlainIcon]）同款，用户 2026-09-30 定；
 * - MD3：既有 tonal 瓦片（底色 [tileColor]，可随开关动画）。
 */
@Composable
fun ConfigRowIcon(icon: Painter, tileColor: Color) {
    if (engineIsMiuix) {
        MiuixPlainIcon(icon)
    } else {
        Box(
            modifier = Modifier
                .size(IconBoxSpec.tile.box)
                .background(tileColor, engineShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconBoxSpec.tile.icon),
            )
        }
    }
}

/**
 * MIUIX 设置行 = 库 [ArrowPreference]（标题 + 右侧值/自定义尾随 + 库内箭头 + 整行按压反馈）。
 * 仅在**可点行**（`onClick != null`）使用——库组件恒画箭头，不可点行不该有箭头。
 * 行内边距走库默认（16dp 四周，随用户要求），故不传 `insideMargin`。
 */
@Composable
fun MiuixSettingRow(
    title: String,
    value: String?,
    icon: Painter?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val startSlot: (@Composable () -> Unit)? = icon?.let { p -> { MiuixPlainIcon(p) } }
    ArrowPreference(
        title = title,
        modifier = modifier.fillMaxWidth(),
        startAction = startSlot,
        endActions = {
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        },
        onClick = onClick,
        insideMargin = MiuixRowInsideMargin,
    )
}

/**
 * MIUIX 开关行 = 库 [SwitchPreference]（整行 `Role.Switch` + 库内 Switch，语义与 MD3 行一致：
 * 开关本体不另挂回调，由整行承担，避免无障碍读到两个重复开关节点）。
 */
@Composable
fun MiuixSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
) {
    SwitchPreference(
        title = title,
        summary = desc,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth(),
        insideMargin = MiuixRowInsideMargin,
    )
}

/**
 * MIUIX 卡片容器 = 库 [top.yukonga.miuix.kmp.basic.Card]（miuix 的圆角/底色/层级语言）。
 *
 * 内边距仍保持本 App 的 `Spacing.l`（16dp）：卡片在本 App 里也承载非行内容（hero/图表/说明文），
 * 直接改成库默认 0.dp 会让那些内容贴边。**「行距卡片边 16dp」这种更 MIUI 的紧凑版式**需把行栈
 * 卡片的卡片内边距置 0 再逐页核对（18 处调用点），留待下一步单独做。
 *
 * @param containerColorOverride 仅当调用方**显式覆盖**底色时传入（否则用库的中性 surfaceContainer，
 * 不把 MD3 色阶带进 MIUIX）
 */
@Composable
fun MiuixSectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColorOverride: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (containerColorOverride != null) {
        CardDefaults.defaultColors(color = containerColorOverride)
    } else {
        CardDefaults.defaultColors()
    }
    val padding = PaddingValues(Spacing.l)
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            insideMargin = padding,
            colors = colors,
            onClick = onClick,
        ) { content() }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            insideMargin = padding,
            colors = colors,
        ) { content() }
    }
}

/**
 * MIUIX 按钮 = 库 [Button]（PRIMARY：主色填充）/ [TextButton]（SECONDARY/GHOST：文字按钮，透明容器）。
 *
 * ⚠️ 库按钮没有本 App 的两种形态 → [com.mdot.app.core.designsystem.component.JiabanButton] 在
 * `loading`（收缩到 ✓）或带 `icon` 时**回落自绘实现**（两引擎同款，行为一致）。
 * 另：MD3 的「SECONDARY 描边 / GHOST 纯文字」在 MIUI 语言里是一回事（次要动作 = 文字按钮），
 * 故两角色在 MIUIX 下外观相同——这是有意的引擎差异。
 */
@Composable
fun MiuixJiabanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
    large: Boolean,
    enabled: Boolean,
    textColorOverride: Color? = null,
) {
    val minHeight = if (large) ButtonSpec.heightL else ButtonDefaults.MinHeight
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minHeight = minHeight,
            // 2026-09-30 修「实心主色按钮填充发黑、文字看不清」（用户实测 (0,0,0)）：
            // 不再依赖库对 primary/onPrimary 的解析（本 App 与库的角色映射可能不一致），
            // 直接显式给本 App 配色对，保证填充与文字的对比度。
            colors = top.yukonga.miuix.kmp.basic.ButtonColors(
                color = MaterialTheme.colorScheme.primary,
                disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            // 2026-09-30 二修：仅传 colors.contentColor **不生效**（实测文字仍为黑 ✗，而 TextButton 那条
            // 的文字色是生效的）⇒ 明确把文字色写在 Text 上，不再依赖库的颜色传递（禁用态沿用本 App 约定）。
            Text(
                text,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    } else {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minHeight = minHeight,
            colors = ButtonDefaults.textButtonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                textColor = textColorOverride ?: MiuixTheme.colorScheme.primary,
                disabledTextColor = MiuixTheme.colorScheme.disabledPrimary,
            ),
        )
    }
}
