package com.mdot.app.core.designsystem

import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

// ---- 配色方案（03 文档 §2.2：4 套预设种子色锚点） ----

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
        primary = Color(0xFF96491B),
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
        surfaceContainerLow = Color(0xFF1B1B21),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF47464F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
)

private val palettes = listOf(classicBlue(), teal(), warmOrange(), violet())

fun paletteOptions(): List<Pair<String, Int>> = palettes.map { it.id to it.labelRes }

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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JiabanTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
