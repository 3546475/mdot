package com.mdot.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.ThemeMode

// ---- 配色方案（03 文档 §2.2 原 4 套 + docs/18 新增 4 套，共 8 套预设种子色锚点） ----

private data class Palette(
    val id: String,
    val labelRes: Int,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
)

private fun classicBlue() = Palette(
    id = AppearanceConfig.PALETTE_CLASSIC_BLUE,
    labelRes = R.string.ds_palette_classic_blue,
    light = lightColorScheme(
        primary = Color(0xFF3B5FDB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE1FF),
        onPrimaryContainer = Color(0xFF001159),
        secondary = Color(0xFF595E72),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE2F9),
        onSecondaryContainer = Color(0xFF161B2C),
        tertiary = Color(0xFF00696B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF9CF1F2),
        onTertiaryContainer = Color(0xFF002021),
        surface = Color(0xFFF8F9FE),
        onSurface = Color(0xFF1A1B21),
        surfaceVariant = Color(0xFFE2E2EC),
        onSurfaceVariant = Color(0xFF45464F),
        surfaceContainer = Color(0xFFECECF4),
        surfaceContainerHigh = Color(0xFFE6E6EE),
        surfaceContainerHighest = Color(0xFFE0E0E8),
        surfaceContainerLow = Color(0xFFF2F2FA),
        outline = Color(0xFF757680),
        outlineVariant = Color(0xFFC6C6D0),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFB4C4FF),
        onPrimary = Color(0xFF1F2A6B),
        primaryContainer = Color(0xFF37449E),
        onPrimaryContainer = Color(0xFFDDE1FF),
        secondary = Color(0xFFC1C5DD),
        onSecondary = Color(0xFF2B3042),
        secondaryContainer = Color(0xFF414659),
        onSecondaryContainer = Color(0xFFDDE2F9),
        tertiary = Color(0xFF80D4D5),
        onTertiary = Color(0xFF003738),
        tertiaryContainer = Color(0xFF004F51),
        onTertiaryContainer = Color(0xFF9CF1F2),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE4E1EC),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C6D0),
        surfaceContainer = Color(0xFF1E1F25),
        surfaceContainerHigh = Color(0xFF282A30),
        surfaceContainerHighest = Color(0xFF32343A),
        surfaceContainerLow = Color(0xFF1A1B21),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF45464F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun teal() = Palette(
    id = AppearanceConfig.PALETTE_TEAL,
    labelRes = R.string.ds_palette_teal,
    light = lightColorScheme(
        primary = Color(0xFF006C52),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF89F8D0),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF4C635A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCEE9DD),
        onSecondaryContainer = Color(0xFF092018),
        tertiary = Color(0xFF3E6374),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC2E8FC),
        onTertiaryContainer = Color(0xFF001F2C),
        surface = Color(0xFFF5FBF7),
        onSurface = Color(0xFF171D1A),
        surfaceVariant = Color(0xFFDBE5DE),
        onSurfaceVariant = Color(0xFF3F4944),
        surfaceContainer = Color(0xFFE9EFEB),
        surfaceContainerHigh = Color(0xFFE3E9E5),
        surfaceContainerHighest = Color(0xFFDDE3DF),
        surfaceContainerLow = Color(0xFFEFF5F1),
        outline = Color(0xFF6F7973),
        outlineVariant = Color(0xFFBFC9C2),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF5EDDB4),
        onPrimary = Color(0xFF003825),
        primaryContainer = Color(0xFF005138),
        onPrimaryContainer = Color(0xFF79F8CE),
        secondary = Color(0xFFB3CCC2),
        onSecondary = Color(0xFF1F352C),
        secondaryContainer = Color(0xFF354B42),
        onSecondaryContainer = Color(0xFFCEE9DD),
        tertiary = Color(0xFFA6CCE0),
        onTertiary = Color(0xFF0A3547),
        tertiaryContainer = Color(0xFF264C5C),
        onTertiaryContainer = Color(0xFFC2E8FC),
        surface = Color(0xFF0F1512),
        onSurface = Color(0xFFDEE4DF),
        surfaceVariant = Color(0xFF3F4944),
        onSurfaceVariant = Color(0xFFBFC9C2),
        surfaceContainer = Color(0xFF1A201D),
        surfaceContainerHigh = Color(0xFF242A27),
        surfaceContainerHighest = Color(0xFF2E3431),
        surfaceContainerLow = Color(0xFF171D1A),
        outline = Color(0xFF89938C),
        outlineVariant = Color(0xFF3F4944),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun warmOrange() = Palette(
    id = AppearanceConfig.PALETTE_WARM_ORANGE,
    labelRes = R.string.ds_palette_warm_orange,
    light = lightColorScheme(
        // ⚠️ 2026-09-20 提彩度：原 #96491B（HSL 22°/S69%/L35%）与色卡预期差太远——用户按「暖橙」
        // 选下去拿到的是偏褐的深色。改 #B35217（22°/S77%/L40%）后仍是同一色相，
        // 对比度核对：白字压其上 5.08:1、它作正文压 surface(#FFF8F5) 4.83:1，双向都过 AA(4.5)。
        primary = Color(0xFFB35217),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCC),
        onPrimaryContainer = Color(0xFF331200),
        secondary = Color(0xFF77574A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBCC),
        onSecondaryContainer = Color(0xFF2C1608),
        tertiary = Color(0xFF655F31),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECE4AA),
        onTertiaryContainer = Color(0xFF1F1C00),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF221A15),
        surfaceVariant = Color(0xFFF4DED5),
        onSurfaceVariant = Color(0xFF52443C),
        surfaceContainer = Color(0xFFFDEEE6),
        surfaceContainerHigh = Color(0xFFF7E8E0),
        surfaceContainerHighest = Color(0xFFF1E2DA),
        surfaceContainerLow = Color(0xFFFFF3ED),
        outline = Color(0xFF85736A),
        outlineVariant = Color(0xFFD7C2B8),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB68B),
        onPrimary = Color(0xFF551F00),
        primaryContainer = Color(0xFF7A2F05),
        onPrimaryContainer = Color(0xFFFFDBCC),
        secondary = Color(0xFFE7BEAD),
        onSecondary = Color(0xFF442A1E),
        secondaryContainer = Color(0xFF5D4033),
        onSecondaryContainer = Color(0xFFFFDBCC),
        tertiary = Color(0xFFCFC88F),
        onTertiary = Color(0xFF343100),
        tertiaryContainer = Color(0xFF4C4817),
        onTertiaryContainer = Color(0xFFECE4AA),
        surface = Color(0xFF1A120D),
        onSurface = Color(0xFFF1DFD6),
        surfaceVariant = Color(0xFF52443C),
        onSurfaceVariant = Color(0xFFD7C2B8),
        surfaceContainer = Color(0xFF261D17),
        surfaceContainerHigh = Color(0xFF312720),
        surfaceContainerHighest = Color(0xFF3B312A),
        surfaceContainerLow = Color(0xFF221A15),
        outline = Color(0xFF9F8D83),
        outlineVariant = Color(0xFF52443C),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun violet() = Palette(
    id = AppearanceConfig.PALETTE_VIOLET,
    labelRes = R.string.ds_palette_violet,
    light = lightColorScheme(
        primary = Color(0xFF5B43C6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4DFFF),
        onPrimaryContainer = Color(0xFF170063),
        secondary = Color(0xFF605B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6DFF9),
        onSecondaryContainer = Color(0xFF1C192B),
        tertiary = Color(0xFF7B5262),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E4),
        onTertiaryContainer = Color(0xFF30111F),
        surface = Color(0xFFF9F8FE),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE5E0F0),
        onSurfaceVariant = Color(0xFF47464F),
        surfaceContainer = Color(0xFFEEEDF6),
        surfaceContainerHigh = Color(0xFFE8E7F1),
        surfaceContainerHighest = Color(0xFFE2E1EB),
        surfaceContainerLow = Color(0xFFF3F2FC),
        outline = Color(0xFF787680),
        outlineVariant = Color(0xFFC9C5D4),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC7BFFF),
        onPrimary = Color(0xFF2E0098),
        primaryContainer = Color(0xFF4526AF),
        onPrimaryContainer = Color(0xFFE4DFFF),
        secondary = Color(0xFFCAC3DF),
        onSecondary = Color(0xFF322E41),
        secondaryContainer = Color(0xFF484459),
        onSecondaryContainer = Color(0xFFE6DFF9),
        tertiary = Color(0xFFECB9CA),
        onTertiary = Color(0xFF482535),
        tertiaryContainer = Color(0xFF613B4B),
        onTertiaryContainer = Color(0xFFFFD9E4),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE5E1E9),
        surfaceVariant = Color(0xFF47464F),
        onSurfaceVariant = Color(0xFFC9C5D4),
        surfaceContainer = Color(0xFF1F1E24),
        surfaceContainerHigh = Color(0xFF2A2930),
        surfaceContainerHighest = Color(0xFF34333A),
        surfaceContainerLow = Color(0xFF1B1B21),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF47464F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

// ---- v0.7.x 配色方案扩展：4 -> 8 套（docs/18）。新增四套沿用与上面四套**完全相同的配方与字段** ----
// primary 直接取种子色本身（不经过 tone40 转换）——与现有四套一致，保证「色卡所见 = 选中所得」；
// 其余色阶由种子的 HCT(hue, chroma) 推导：primary 族用种子色度、secondary = 同一色相 C16、
// tertiary = 色相 +60° C24、中性族 = 同色相 C6、中性变体族 = 同色相 C8、error 固定 hue25/C84。
// 生成器：material-color-utilities（Google 官方 HCT 实现）；四套新配色的白字对比分别为
// 朱砂 5.98:1 / 玫红 5.78:1 / 湖蓝 5.92:1 / 石墨 8.11:1，均高于 AA 的 4.5:1。
private fun cinnabar() = Palette(
    id = AppearanceConfig.PALETTE_CINNABAR,
    labelRes = R.string.ds_palette_cinnabar,
    light = lightColorScheme(
        primary = Color(0xFFB8321C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD3),
        onPrimaryContainer = Color(0xFF3F0300),
        secondary = Color(0xFF775750),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDAD3),
        onSecondaryContainer = Color(0xFF2C1511),
        tertiary = Color(0xFF6E5C2E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFAE0A6),
        onTertiaryContainer = Color(0xFF241A00),
        surface = Color(0xFFFFF8F6),
        onSurface = Color(0xFF231918),
        surfaceVariant = Color(0xFFF5DDD9),
        onSurfaceVariant = Color(0xFF534340),
        surfaceContainer = Color(0xFFFCEAE6),
        surfaceContainerHigh = Color(0xFFF7E4E1),
        surfaceContainerHighest = Color(0xFFF1DFDB),
        surfaceContainerLow = Color(0xFFFFF0EE),
        outline = Color(0xFF85736F),
        outlineVariant = Color(0xFFD8C2BD),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB4A6),
        onPrimary = Color(0xFF660900),
        primaryContainer = Color(0xFF8E1201),
        onPrimaryContainer = Color(0xFFFFDAD3),
        secondary = Color(0xFFE7BDB5),
        onSecondary = Color(0xFF442A24),
        secondaryContainer = Color(0xFF5D3F3A),
        onSecondaryContainer = Color(0xFFFFDAD3),
        tertiary = Color(0xFFDCC48C),
        onTertiary = Color(0xFF3D2E04),
        tertiaryContainer = Color(0xFF554519),
        onTertiaryContainer = Color(0xFFFAE0A6),
        surface = Color(0xFF1A1110),
        onSurface = Color(0xFFF1DFDB),
        surfaceVariant = Color(0xFF534340),
        onSurfaceVariant = Color(0xFFD8C2BD),
        surfaceContainer = Color(0xFF271D1C),
        surfaceContainerHigh = Color(0xFF322826),
        surfaceContainerHighest = Color(0xFF3D3230),
        surfaceContainerLow = Color(0xFF231918),
        outline = Color(0xFFA08C89),
        outlineVariant = Color(0xFF534340),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun rose() = Palette(
    id = AppearanceConfig.PALETTE_ROSE,
    labelRes = R.string.ds_palette_rose,
    light = lightColorScheme(
        primary = Color(0xFFB5316F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E4),
        onPrimaryContainer = Color(0xFF3E0020),
        secondary = Color(0xFF745660),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E4),
        onSecondaryContainer = Color(0xFF2A151D),
        tertiary = Color(0xFF7D5636),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCC3),
        onTertiaryContainer = Color(0xFF2F1500),
        surface = Color(0xFFFFF8F8),
        onSurface = Color(0xFF22191C),
        surfaceVariant = Color(0xFFF2DDE2),
        onSurfaceVariant = Color(0xFF514347),
        surfaceContainer = Color(0xFFFAEAED),
        surfaceContainerHigh = Color(0xFFF4E4E7),
        surfaceContainerHighest = Color(0xFFEFDFE2),
        surfaceContainerLow = Color(0xFFFFF0F3),
        outline = Color(0xFF837377),
        outlineVariant = Color(0xFFD5C2C6),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB0CB),
        onPrimary = Color(0xFF640037),
        primaryContainer = Color(0xFF8B074F),
        onPrimaryContainer = Color(0xFFFFD9E4),
        secondary = Color(0xFFE2BDC8),
        onSecondary = Color(0xFF422932),
        secondaryContainer = Color(0xFF5A3F48),
        onSecondaryContainer = Color(0xFFFFD9E4),
        tertiary = Color(0xFFF0BC95),
        onTertiary = Color(0xFF48290D),
        tertiaryContainer = Color(0xFF623F21),
        onTertiaryContainer = Color(0xFFFFDCC3),
        surface = Color(0xFF191114),
        onSurface = Color(0xFFEFDFE2),
        surfaceVariant = Color(0xFF514347),
        onSurfaceVariant = Color(0xFFD5C2C6),
        surfaceContainer = Color(0xFF261D20),
        surfaceContainerHigh = Color(0xFF31282A),
        surfaceContainerHighest = Color(0xFF3C3235),
        surfaceContainerLow = Color(0xFF22191C),
        outline = Color(0xFF9E8C91),
        outlineVariant = Color(0xFF514347),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun lake() = Palette(
    id = AppearanceConfig.PALETTE_LAKE,
    labelRes = R.string.ds_palette_lake,
    light = lightColorScheme(
        primary = Color(0xFF116C8C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC0E8FF),
        onPrimaryContainer = Color(0xFF001F2B),
        secondary = Color(0xFF4D616C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD0E6F3),
        onSecondaryContainer = Color(0xFF091E27),
        tertiary = Color(0xFF5E5A7D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE4DFFF),
        onTertiaryContainer = Color(0xFF1B1736),
        surface = Color(0xFFF6FAFE),
        onSurface = Color(0xFF171C1F),
        surfaceVariant = Color(0xFFDCE3E9),
        onSurfaceVariant = Color(0xFF40484C),
        surfaceContainer = Color(0xFFEAEEF2),
        surfaceContainerHigh = Color(0xFFE4E9EC),
        surfaceContainerHighest = Color(0xFFDFE3E7),
        surfaceContainerLow = Color(0xFFF0F4F8),
        outline = Color(0xFF71787D),
        outlineVariant = Color(0xFFC0C7CD),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF87D0F4),
        onPrimary = Color(0xFF003547),
        primaryContainer = Color(0xFF004D66),
        onPrimaryContainer = Color(0xFFC0E8FF),
        secondary = Color(0xFFB4CAD6),
        onSecondary = Color(0xFF1F333D),
        secondaryContainer = Color(0xFF364954),
        onSecondaryContainer = Color(0xFFD0E6F3),
        tertiary = Color(0xFFC7C2EA),
        onTertiary = Color(0xFF302D4C),
        tertiaryContainer = Color(0xFF464364),
        onTertiaryContainer = Color(0xFFE4DFFF),
        surface = Color(0xFF0F1417),
        onSurface = Color(0xFFDFE3E7),
        surfaceVariant = Color(0xFF40484C),
        onSurfaceVariant = Color(0xFFC0C7CD),
        surfaceContainer = Color(0xFF1B2023),
        surfaceContainerHigh = Color(0xFF262B2E),
        surfaceContainerHighest = Color(0xFF303539),
        surfaceContainerLow = Color(0xFF171C1F),
        outline = Color(0xFF8A9297),
        outlineVariant = Color(0xFF40484C),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private fun graphite() = Palette(
    id = AppearanceConfig.PALETTE_GRAPHITE,
    labelRes = R.string.ds_palette_graphite,
    light = lightColorScheme(
        primary = Color(0xFF4F4F57),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3E1EB),
        onPrimaryContainer = Color(0xFF1A1B22),
        secondary = Color(0xFF5E5E64),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4E1E9),
        onSecondaryContainer = Color(0xFF1B1B21),
        tertiary = Color(0xFF695A63),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF1DDE8),
        onTertiaryContainer = Color(0xFF231820),
        surface = Color(0xFFFCF8FB),
        onSurface = Color(0xFF1B1B1E),
        surfaceVariant = Color(0xFFE4E1E7),
        onSurfaceVariant = Color(0xFF47464B),
        surfaceContainer = Color(0xFFF0EDF0),
        surfaceContainerHigh = Color(0xFFEAE7EA),
        surfaceContainerHighest = Color(0xFFE5E1E5),
        surfaceContainerLow = Color(0xFFF6F2F6),
        outline = Color(0xFF77767C),
        outlineVariant = Color(0xFFC8C5CB),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC7C5CF),
        onPrimary = Color(0xFF2F3037),
        primaryContainer = Color(0xFF46464E),
        onPrimaryContainer = Color(0xFFE3E1EB),
        secondary = Color(0xFFC7C5CD),
        onSecondary = Color(0xFF303036),
        secondaryContainer = Color(0xFF46464C),
        onSecondaryContainer = Color(0xFFE4E1E9),
        tertiary = Color(0xFFD4C1CC),
        onTertiary = Color(0xFF392D35),
        tertiaryContainer = Color(0xFF50434B),
        onTertiaryContainer = Color(0xFFF1DDE8),
        surface = Color(0xFF131315),
        onSurface = Color(0xFFE5E1E5),
        surfaceVariant = Color(0xFF47464B),
        onSurfaceVariant = Color(0xFFC8C5CB),
        surfaceContainer = Color(0xFF201F22),
        surfaceContainerHigh = Color(0xFF2A2A2C),
        surfaceContainerHighest = Color(0xFF353437),
        surfaceContainerLow = Color(0xFF1B1B1E),
        outline = Color(0xFF919095),
        outlineVariant = Color(0xFF47464B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

/** 配色总表（顺序即外观页色卡顺序，前 4 套为原有，后 4 套 v0.7.x 新增） */
private val palettes = listOf(
    classicBlue(), teal(), warmOrange(), violet(),
    cinnabar(), rose(), lake(), graphite(),
)

fun paletteOptions(): List<Pair<String, Int>> = palettes.map { it.id to it.labelRes }

/**
 * 配色方案色卡的取色来源：该配色**浅色模式**的 `primary`（即 03 文档 §2.2 的种子色锚点）。
 *
 * ⚠️ 刻意**不在 UI 层另存一套「更鲜艳的色卡色」**：早先 UI 层硬编码
 * （经典蓝 #3D6DF2 / 青绿 #00A87E / 暖橙 #E8702A / 紫罗兰 #7A5CF0）与主题表完全脱节，
 * 用户按色卡预期选「暖橙」实际拿到 #96491B，且新增配色时极易漏改（`else ->` 兜底会掩盖）。
 * 现在色卡 = 所见即所得，想要更鲜艳就改本文件的配色表。
 *
 * 深色模式也用浅色 primary：色卡承担的是「这是哪一套配色」的**识别**职能，
 * 而深色 primary（#B4C4FF 等）是同一色相的浅色调，四张放一起几乎分不出彼此。
 */
fun palettePrimary(id: String): Color = paletteOf(id).light.primary

private fun paletteOf(id: String): Palette = palettes.firstOrNull { it.id == id } ?: palettes.first()

/** 语义色补充：调休 = tertiary，节假日 = secondary（03 文档 §2.1 走 M3 角色，不新增色值） */

// ---- 排印：数字启用 tnum（03 文档 §3.1） ----

private fun TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

/** M3 Expressive 形状档：按钮/chip/分段控件走 small(20)，FAB/对话框走 large/extraLarge */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val JiabanTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.tnum(),
        displayMedium = base.displayMedium.tnum(),
        displaySmall = base.displaySmall.tnum(),
        headlineLarge = base.headlineLarge.tnum(),
        headlineMedium = base.headlineMedium.tnum(),
        headlineSmall = base.headlineSmall.tnum(),
        titleLarge = base.titleLarge.tnum().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.tnum().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.tnum().copy(fontWeight = FontWeight.SemiBold),
    )
}

// ---- 主题唯一入口（04 文档 §4.1） ----

/**
 * 主题切换跨淡（docs/15 T6 #20）：palette / 明暗 / 动态色变化时，
 * 用逐色动画包装目标 ColorScheme——单棵组合树内颜色平滑过渡（无 Crossfade 双树复制的状态风险）。
 * 首次组合 target 即初始值，无入场动画；target 变化时逐色过渡（motionScheme defaultEffects）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val motion = remember { MotionScheme.expressive() }
    val spec = remember { motion.defaultEffectsSpec<Color>() }
    @Composable fun anim(c: Color): Color = animateColorAsState(c, spec, label = "schemeColor").value
    return ColorScheme(
        primary = anim(target.primary),
        onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer),
        onPrimaryContainer = anim(target.onPrimaryContainer),
        inversePrimary = anim(target.inversePrimary),
        secondary = anim(target.secondary),
        onSecondary = anim(target.onSecondary),
        secondaryContainer = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        tertiary = anim(target.tertiary),
        onTertiary = anim(target.onTertiary),
        tertiaryContainer = anim(target.tertiaryContainer),
        onTertiaryContainer = anim(target.onTertiaryContainer),
        background = anim(target.background),
        onBackground = anim(target.onBackground),
        surface = anim(target.surface),
        onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant),
        onSurfaceVariant = anim(target.onSurfaceVariant),
        surfaceTint = anim(target.surfaceTint),
        inverseSurface = anim(target.inverseSurface),
        inverseOnSurface = anim(target.inverseOnSurface),
        error = anim(target.error),
        onError = anim(target.onError),
        errorContainer = anim(target.errorContainer),
        onErrorContainer = anim(target.onErrorContainer),
        outline = anim(target.outline),
        outlineVariant = anim(target.outlineVariant),
        scrim = anim(target.scrim),
        surfaceBright = anim(target.surfaceBright),
        surfaceDim = anim(target.surfaceDim),
        surfaceContainer = anim(target.surfaceContainer),
        surfaceContainerHigh = anim(target.surfaceContainerHigh),
        surfaceContainerHighest = anim(target.surfaceContainerHighest),
        surfaceContainerLow = anim(target.surfaceContainerLow),
        surfaceContainerLowest = anim(target.surfaceContainerLowest),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JiabanTheme(
    appearance: AppearanceConfig,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> paletteOf(appearance.paletteId).dark
        else -> paletteOf(appearance.paletteId).light
    }
    MaterialExpressiveTheme(
        colorScheme = rememberAnimatedColorScheme(colorScheme),
        motionScheme = MotionScheme.expressive(),
        typography = JiabanTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
