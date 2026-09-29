package com.mdot.app.core.designsystem.miuix

import androidx.compose.material3.ColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * ══ miuix 库组件的配色桥（接库后新增，v0.7.8）═══
 *
 * miuix-ui 的库组件（Switch/Slider/Card…）读的是**它自己的** `MiuixTheme` LocalColors
 * （`top.yukonga.miuix.kmp.theme.Colors`，53 角色），不是 MaterialTheme——
 * 要让库组件跟随本 App 的明暗与强调色，必须把 [Colors] 喂给它的 `MiuixTheme(colors=…)`。
 *
 * 装配规则与 [miuixColorScheme]（M3 侧）完全一致：
 * - **中性色/错误色** ← miuix 库默认词表（`lightColorScheme()`/`darkColorScheme()` 工厂，
 *   即 HyperOS 本色）；
 * - **强调色与语义色**（primary/secondary/tertiary 各档）← 本 App 强调色源
 *   （配色方案 / 动态取色）注入——与 M3 侧同源，两套组件同色不打架。
 *
 * 纯函数，有单测（MiuixEngineTest）。
 */
fun miuixLibraryColors(dark: Boolean, accent: ColorScheme): Colors {
    val base = if (dark) miuixDarkColorScheme() else miuixLightColorScheme()
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryVariant = accent.secondary,
        onSecondaryVariant = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
    )
}
