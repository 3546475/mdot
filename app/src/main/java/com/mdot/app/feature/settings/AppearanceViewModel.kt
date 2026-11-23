package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.model.AppearanceConfig
import com.mdot.app.domain.model.BottomBarConfig
import com.mdot.app.domain.model.HomeCardsConfig
import com.mdot.app.domain.model.ThemeEngine
import com.mdot.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val appearance: StateFlow<AppearanceConfig> = settings.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceConfig())

    val bottomBarCount: StateFlow<Int> = settings.bottomBarFlow
        .map { it.slots.count { id -> id != BottomBarConfig.HOME } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    /** 首页卡片数（清洗未知 id 后的实际张数；未配置时显示出厂默认张数，与首页一致） */
    val homeCardsCount: StateFlow<Int> = settings.homeCardsFlow
        .map {
            it.enabledCards.size
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeCardsConfig().enabledCards.size)

    fun setMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(themeMode = mode))
    }

    /** 主题引擎：MD3（Material3 Expressive，现行）/ MIUIX（miuix 风格引擎，v0.7.8） */
    fun setEngine(engine: ThemeEngine) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(themeEngine = engine))
    }

    fun setPalette(id: String) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(paletteId = id, dynamicColor = false))
    }

    fun setDynamic(value: Boolean) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(dynamicColor = value))
    }

    /** 弹层背景效果：压暗 / 模糊 / 模糊缩小 */
    fun setSheetBackdrop(mode: com.mdot.app.domain.model.SheetBackdropMode) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(sheetBackdropMode = mode))
    }

    /** 日历隐藏农历日期（默认关） */
    fun setHideLunar(value: Boolean) = viewModelScope.launch {
        settings.setAppearance(appearance.value.copy(hideLunarDate = value))
    }
}

/** 外观摘要（我的页外观行右值），如"浅色 · 抹茶绿"/"跟随系统 · 动态取色"；MIUIX 引擎加前缀 */
fun appearanceSummary(context: android.content.Context, config: AppearanceConfig): String {
    val theme = when (config.themeMode) {
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
        ThemeMode.SYSTEM -> "跟随系统"
    }
    val core = if (config.dynamicColor &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    ) {
        "$theme · 动态取色"
    } else {
        val paletteRes = com.mdot.app.core.designsystem.paletteOptions()
            .firstOrNull { it.first == config.paletteId }?.second
        val palette = paletteRes?.let { context.getString(it) } ?: "自定义"
        "$theme · $palette"
    }
    // MD3 是出厂默认，不加前缀（保持既有回显）；MIUIX 引擎标出来，否则摘要看不出换过引擎
    return if (config.themeEngine == ThemeEngine.MIUIX) "MIUIX · $core" else core
}
