package com.mdot.app.feature.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.designsystem.paletteOptions
import com.mdot.app.domain.model.ThemeMode

/** 外观（F7-6 + F7-7：深浅三档、4 套配色、动态取色开关、底栏槽位配置、首页卡片配置） */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onOpenBottomBar: () -> Unit = {},
    onOpenHomeCards: () -> Unit = {},
    vm: AppearanceViewModel = hiltViewModel(),
) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val bottomBarCount by vm.bottomBarCount.collectAsStateWithLifecycle()
    val homeCardsCount by vm.homeCardsCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.appearance_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        SectionCard {
            Column {
                Text(stringResource(R.string.appearance_theme_heading), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.LIGHT to stringResource(R.string.appearance_theme_light),
                        ThemeMode.DARK to stringResource(R.string.appearance_theme_dark),
                        ThemeMode.SYSTEM to stringResource(R.string.appearance_theme_system),
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = appearance.themeMode == mode,
                            onClick = { vm.setMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.l))
                Text(stringResource(R.string.appearance_palette_heading), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    paletteOptions().forEach { (id, label) ->
                        val isSelected = appearance.paletteId == id && !appearance.dynamicColor
                        val interaction = remember { MutableInteractionSource() }
                        // 选中描边宽度/颜色平滑过渡 + 按压缩放
                        val borderWidth by animateDpAsState(
                            targetValue = if (isSelected) 3.dp else 1.dp,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "paletteBorderW",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "paletteBorderC",
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .pressScale(interaction, pressedScale = 0.88f)
                                    .background(paletteSeed(id), CircleShape)
                                    .border(borderWidth, borderColor, CircleShape)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = LocalIndication.current,
                                    ) { vm.setPalette(id) },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(Spacing.l))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.appearance_dynamic_color), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.appearance_dynamic_color_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = appearance.dynamicColor, onCheckedChange = { vm.setDynamic(it) })
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))

        // ---- 首页卡片 + 底栏配置入口（首页卡片在上） ----
        SectionCard {
            Column {
                SettingRow(
                    stringResource(R.string.appearance_home_cards_config), stringResource(R.string.appearance_home_cards_summary, homeCardsCount),
                    painterResource(R.drawable.ic_ms_palette), onClick = onOpenHomeCards,
                )
                SettingRow(
                    stringResource(R.string.appearance_bottom_bar_config), stringResource(R.string.appearance_bottom_bar_summary, bottomBarCount),
                    painterResource(R.drawable.ic_ms_dashboard), onClick = onOpenBottomBar,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun paletteSeed(id: String): Color = when (id) {
    com.mdot.app.domain.model.AppearanceConfig.PALETTE_TEAL -> Color(0xFF00A87E)
    com.mdot.app.domain.model.AppearanceConfig.PALETTE_WARM_ORANGE -> Color(0xFFE8702A)
    com.mdot.app.domain.model.AppearanceConfig.PALETTE_VIOLET -> Color(0xFF7A5CF0)
    else -> Color(0xFF3D6DF2)
}
