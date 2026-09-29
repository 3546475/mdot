package com.mdot.app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.domain.model.ThemeEngine
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * 复选框（引擎分发）：MIUIX = 库 `basic.Checkbox`（miuix 的勾选动效与配色）；MD3 = M3 `Checkbox`。
 *
 * ⚠️ 库 Checkbox 收的是 `ToggleableState`（不是 Boolean）、且回调是 `onClick: (() -> Unit)?`——
 * 故此处做一层语义换算：`checked` ⇄ ToggleableState.On/Off，点击回调翻转后回传。
 */
@Composable
fun JiabanCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = onCheckedChange?.let { cb -> { cb(!checked) } },
            // ⚠️ 库 Checkbox 没有 M3 的 48dp 最小触达尺寸：直接换上会让行高塌到文字高度、
            // 行与行挤在一起（工作日页多选实测）。补回同一约束——行高/间距与 MD3 完全一致，
            // 只有字形走库。
            modifier = modifier.minimumInteractiveComponentSize(),
            enabled = enabled,
        )
    } else {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

/** 单选钮（引擎分发）：MIUIX = 库 `basic.RadioButton`；MD3 = M3 `RadioButton`（两者签名同形） */
@Composable
fun JiabanRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier.minimumInteractiveComponentSize(),   // 同 Checkbox：补 M3 的 48dp 触达尺寸，保住行高
            enabled = enabled,
        )
    } else {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    }
}
