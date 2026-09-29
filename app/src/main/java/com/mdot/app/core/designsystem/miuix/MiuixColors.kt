package com.mdot.app.core.designsystem.miuix

import androidx.compose.ui.graphics.Color

/**
 * ══ 我的 miuix 引擎——颜色令牌层（外观页「主题引擎」= MIUIX，v0.7.8）═══
 *
 * 数值取自开源项目 **miuix**（https://github.com/compose-miuix-ui/miuix，Apache-2.0）
 * `miuix-ui` 模块 `theme/Colors.kt` 的 `lightColorScheme()` / `darkColorScheme()`，
 * 但**角色按本 App 的实际用法裁剪**（去掉 miuix 组件专属的 disabled\* / slider\* 细分档），
 * 并把页面/卡片语义对齐到本 App 的角色表（见 Tokens.kt 令牌表）：
 *
 * | 本 App 角色（用法） | miuix 来源角色 | 浅色 | 深色 |
 * |---|---|---|---|
 * | `background`/`surface`（页面底） | `surface` | #F7F7F7 灰 | 纯黑 |
 * | `surfaceContainer`（分区卡底） | `surfaceContainer` | 白 | #242424 |
 * | `surfaceContainerHigh`（卡内凹陷） | `secondaryVariant` / `surfaceContainerHighest` | #F0F0F0 | #2D2D2D |
 * | `surfaceContainerHighest`（强凹陷） | `surfaceContainerHigh` / `disabledSecondary` | #E8E8E8 | #3F3F3F |
 * | `onSurfaceVariant`（次级文字） | `onSurfaceVariantSummary` | 60% 黑 | 50% 白 |
 * | `outline` / `outlineVariant` | `outline` / `dividerLine` | #D9D9D9 / #E0E0E0 | #404040 / #393939 |
 * | `error*` | `error*`（同名照搬） | #E94634 系 | #F12522 系 |
 * | `scrim`（弹层压暗） | `windowDimming` | 黑 30% | 黑 60% |
 *
 * ⚠️ 与 M3 的关键观感差：miuix 是**灰底白卡**（浅色下卡片比页面更亮），M3 是**白底灰卡**——
 * 两套引擎切换时观感差异主要来自这张中性色表，不是强调色。
 *
 * **强调色（primary/secondary/tertiary 各档）不在此表**：由 [miuixColorScheme] 从当次的
 * 强调色源（配色方案 / 动态取色）注入——对齐 miuix 自己的 `ThemeController(keyColor)` +
 * `MonetMapping`「中性色固定、强调色可变」的架构，故本 App 的配色方案与动态取色在
 * MIUIX 引擎下照常生效（语义色「调休=tertiary / 节假日=secondary」也不丢）。
 */
data class MiuixColors(
    /** 页面底（本 App `background`/`surface` 的用法，见 AppRoot/MainActivity 宿主 Surface） */
    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceVariant: Color,
    /** 分区卡底（SectionCard） */
    val surfaceContainer: Color,
    val surfaceContainerLowest: Color,
    /** 卡内凹陷（分段控件未选中档、空状态图标底、顶栏） */
    val surfaceContainerHigh: Color,
    /** 强凹陷（凹陷里的选中/描边档） */
    val surfaceContainerHighest: Color,
    val onBackground: Color,
    val onSurface: Color,
    /** 次级文字/次级图标（本 App 用量最大的文字角色） */
    val onSurfaceVariant: Color,
    val outline: Color,
    /** 分隔线（M3 `outlineVariant` 的用法） */
    val dividerLine: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** 弹层压暗（M3 `scrim` 的用法） */
    val windowDimming: Color,
)

/** miuix 浅色中性令牌（值见 [MiuixColors] 表；来源 miuix `lightColorScheme()`） */
fun miuixLightColors(): MiuixColors = MiuixColors(
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFF7F7F7),
    surfaceDim = Color(0xFFE8E8E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0x99000000),
    outline = Color(0xFFD9D9D9),
    dividerLine = Color(0xFFE0E0E0),
    error = Color(0xFFE94634),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDF6F4),
    onErrorContainer = Color(0xFF410002),
    windowDimming = Color(0x4D000000),
)

/** miuix 深色中性令牌（值见 [MiuixColors] 表；来源 miuix `darkColorScheme()`） */
fun miuixDarkColors(): MiuixColors = MiuixColors(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2D2D2D),
    surfaceVariant = Color(0xFF242424),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerLowest = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2D2D2D),
    surfaceContainerHighest = Color(0xFF3F3F3F),
    onBackground = Color(0xE6FFFFFF),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0x80FFFFFF),
    outline = Color(0xFF404040),
    dividerLine = Color(0xFF393939),
    error = Color(0xFFF12522),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2E0603),
    onErrorContainer = Color(0xFFFFDAD6),
    windowDimming = Color(0x99000000),
)
