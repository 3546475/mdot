package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.domain.model.ThemeEngine

/**
 * 图标按钮（引擎分发）：MIUIX = 库 `basic.IconButton`（miuix 的按压/圆角语言）；MD3 = M3 `IconButton`。
 *
 * ⚠️ 库 IconButton 的 `minWidth/minHeight` 是库自己的一套默认值，**不等于 M3 的 48dp 最小触达尺寸**——
 * 直接换上会让依赖该尺寸的布局塌陷（工作日页 Checkbox 已栽过一次）。
 * 故 MIUIX 分支统一补 `minimumInteractiveComponentSize()`：只设下限，库默认更大时不受影响。
 *
 * @param iconSize 图标本身尺寸；null = 用本 App 的图标令牌 [IconSpec.bar]
 */
@Composable
fun JiabanIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = onClick,
            modifier = modifier.minimumInteractiveComponentSize(),
            enabled = enabled,
        ) { content() }
    } else {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { content() }
    }
}
