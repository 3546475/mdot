package com.mdot.app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.engineSwitchColors
import com.mdot.app.domain.model.ThemeEngine
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * 开关（引擎分发的唯一入口，全 App 8 处调用点统一走它——**禁止直呼 `Switch`**：
 * 两引擎观感不同（MIUIX = 49×28 轨道 + 20dp 白圆钮 + 按压缩放弹簧；MD3 = M3 观感），
 * 直呼会把引擎差异做丢）。
 *
 * - MIUIX = **miuix-ui 库本体** `top.yukonga.miuix.kmp.basic.Switch`，
 *   配色走其 LocalColors（由 JiabanTheme 的 miuixLibraryColors 桥接，与 M3 侧同源强调色）；
 * - MD3 = M3 `Switch` + [engineSwitchColors]（现行为不变）。
 */
@Composable
fun JiabanSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (Radius.engine) {
        ThemeEngine.MIUIX -> MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // ⚠️ 库 Switch 固定 49×28dp、**没有 M3 的 48dp 最小触达尺寸** → 开关行会比 MD3 矮一档
            //（与工作日 Checkbox 同族坑）。补回同一约束：行高与 MD3 一致，只有滑块外形走库。
            modifier = modifier.minimumInteractiveComponentSize(),
            enabled = enabled,
        )
        ThemeEngine.MD3 -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = engineSwitchColors(),
        )
    }
}
