package com.mdot.app.core.designsystem.miuix

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdot.app.core.designsystem.tnum

/**
 * ══ 我的 miuix 引擎——主题装配层（外观页「主题引擎」= MIUIX，v0.7.8）═══
 *
 * 参考开源项目 **miuix**（https://github.com/compose-miuix-ui/miuix，Apache-2.0）的
 * `theme/` 层架构（`Colors` + `TextStyles` + `MiuixTheme`），但**按本 App 的组件体系装配**：
 * 全 App 组件读的是 Material3 角色（`MaterialTheme.colorScheme`/`typography`），
 * 故引擎的输出是「一套 M3 `ColorScheme` + `Typography` + `Shapes`」——
 * 引擎决定**令牌怎么长**，组件库不用动（对齐 miuix `MonetMapping` 的角色映射思路，
 * 方向相反：那份是 MD3 角色 → miuix 角色，本文件是 miuix 令牌 → M3 角色）。
 *
 * 装配规则（对应 [MiuixColors] 的角色表）：
 * 1. **中性色/文字/错误/分隔/压暗** ← miuix 固定令牌（[miuixLightColors]/[miuixDarkColors]）；
 * 2. **强调色与语义色**（primary/secondary/tertiary 各档、inverse*）← **强调色源**原样注入——
 *    强调色源 = 外观页的「配色方案」或「动态取色」（[miuixColorScheme] 的 `accent` 入参），
 *    与 miuix `ThemeController(keyColor)` 同思路：中性色固定、强调色可变。
 *    故 MD3/MIUIX 两引擎共用同一套配色与动态取色设置，「调休=tertiary / 节假日=secondary」
 *    的语义色也不丢。
 */

/**
 * miuix 引擎的 M3 角色装配：中性色走 miuix 令牌，强调/语义色从 [accent]（配色方案或
 * 动态取色给出的 M3 `ColorScheme`）注入。纯函数，有单测（MiuixEngineTest）。
 *
 * @param accent 强调色源（`JiabanTheme` 里 palette/dynamicColor 解析出的那套）
 * @param dark 深色档（决定取 [miuixDarkColors] 还是 [miuixLightColors]）
 */
fun miuixColorScheme(accent: ColorScheme, dark: Boolean): ColorScheme {
    val t = if (dark) miuixDarkColors() else miuixLightColors()
    return ColorScheme(
        // ---- 强调色 / 语义色：来自强调色源（配色方案 / 动态取色）----
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        inversePrimary = accent.inversePrimary,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
        inverseSurface = accent.inverseSurface,
        inverseOnSurface = accent.inverseOnSurface,
        surfaceTint = accent.primary,
        // ---- 中性色 / 文字 / 错误 / 分隔 / 压暗：来自 miuix 令牌 ----
        background = t.background,
        onBackground = t.onBackground,
        surface = t.surface,
        onSurface = t.onSurface,
        surfaceVariant = t.surfaceVariant,
        onSurfaceVariant = t.onSurfaceVariant,
        surfaceBright = t.surfaceBright,
        surfaceDim = t.surfaceDim,
        // miuix 没有 low 档：与分区卡同值（灰底白卡里不存在「比卡更低」的容器）
        surfaceContainerLow = t.surfaceContainer,
        surfaceContainerLowest = t.surfaceContainerLowest,
        surfaceContainer = t.surfaceContainer,
        surfaceContainerHigh = t.surfaceContainerHigh,
        surfaceContainerHighest = t.surfaceContainerHighest,
        error = t.error,
        onError = t.onError,
        errorContainer = t.errorContainer,
        onErrorContainer = t.onErrorContainer,
        outline = t.outline,
        outlineVariant = t.dividerLine,
        scrim = t.windowDimming,
    )
}

/**
 * miuix 引擎排印（档位表 = miuix `theme/TextStyles.kt` 的字号词表）：
 *
 * | M3 角色（本 App 用法） | miuix 文本档 | 字号 | 字重 |
 * |---|---|---|---|
 * | `titleLarge`（顶栏标题） | `title3` | 22→**20**sp | Bold |
 * | `titleMedium`（卡片标题） | `headline1` | 16→**17**sp | Bold |
 * | `titleSmall`（小节标题） | `subtitle` | 14sp | SemiBold→**Bold** |
 * | `bodyLarge`/`bodyMedium` | `body1`/`body2` | 16/14sp | 常规 |
 * | `bodySmall`/`labelMedium` | `footnote1` | 12→**13**sp | 常规 |
 * | `labelLarge`/`labelSmall` | `body2`/`footnote2` | 14/11sp | 常规 |
 * | `headlineLarge`/`headlineSmall` | `title1`/`title2` | 32/24sp 同值 | —— |
 *
 * ⚠️ `display*` 与 `headlineMedium` 是 **hero 数值锚点**（HeroSpec 金额档、首页大数字），
 * 不随引擎缩放——两套引擎共用同一数值骨架，换引擎不许把首页大数字变小。
 * 数字 `tnum` 特性与 [com.mdot.app.core.designsystem.JiabanTypography] 同款（03 文档 §3.1）。
 */
val MiuixTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.tnum(),
        displayMedium = base.displayMedium.tnum(),
        displaySmall = base.displaySmall.tnum(),
        headlineLarge = base.headlineLarge.tnum(),
        headlineMedium = base.headlineMedium.tnum(),
        headlineSmall = base.headlineSmall.tnum(),
        titleLarge = base.titleLarge.tnum().copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.tnum().copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.tnum().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
}

/**
 * miuix 引擎形状档（词表 = miuix 组件圆角：Card/Button/TextField `CornerRadius = 16.dp`、
 * `TabRowCornerRadius = 12.dp`、Tooltip 8.dp、**Dialog `CornerRadius = 32.dp`**），
 * 且用 [SquircleShape] 超椭圆连续圆角（miuix-squircle 同款路径）。与
 * [com.mdot.app.core.designsystem.ExpressiveShapes] 的差异：M3 档整体更圆
 * （small 20 / large 28），miuix 档收敛在 8–24、对话框 32 与 M3 同值但为 squircle。
 *
 * ⚠️ 只影响 M3 **组件内部**（对话框/Switch/Divider 等）的形状；本 App 自有组件走
 * `engineShape(Radius.*)`（见 core/designsystem/EngineStyle.kt），两处同为 squircle。
 */
val MiuixShapes = Shapes(
    extraSmall = SquircleShape(8.dp),
    small = SquircleShape(12.dp),
    medium = SquircleShape(16.dp),
    large = SquircleShape(20.dp),
    extraLarge = SquircleShape(32.dp),
)
