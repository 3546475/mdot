package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.model.BottomBarConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BottomBarViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val config: StateFlow<BottomBarConfig> = settings.bottomBarFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BottomBarConfig())

    val iconOnly: StateFlow<Boolean> = settings.bottomBarIconOnlyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏布局：记加班按钮置右（右侧独立圆钮） */
    val sideAction: StateFlow<Boolean> = settings.bottomBarSideActionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏固定长度（4 个槽位宽；默认关闭 = 随槽位数自适应） */
    val fixedWidth: StateFlow<Boolean> = settings.bottomBarFixedWidthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏毛玻璃（**材质**：半透明 + 模糊身后内容；v0.6.21 起默认关闭） */
    val frosted: StateFlow<Boolean> = settings.bottomBarFrostedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 底栏液态玻璃（**交互**：按压时的液滴透镜；不动静止材质，与毛玻璃互相独立，默认关闭） */
    val liquidGlass: StateFlow<Boolean> = settings.bottomBarLiquidGlassFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setIconOnly(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarIconOnly(value)
    }

    fun setSideAction(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarSideAction(value)
    }

    fun setFixedWidth(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarFixedWidth(value)
    }

    fun setFrosted(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarFrosted(value)
    }

    /** 液态玻璃（交互开关，与毛玻璃材质互相独立） */
    fun setLiquidGlass(value: Boolean) = viewModelScope.launch {
        settings.setBottomBarLiquidGlass(value)
    }

    /** 打开/关闭某个槽位：**只改 disabled，不动 order**（开关不影响顺序，对齐班次卡片） */
    fun setEnabled(slotId: String, enabled: Boolean) = viewModelScope.launch {
        if (slotId == BottomBarConfig.HOME) return@launch // 首页固定不可关
        val cfg = config.value
        val disabled = cfg.disabled.toMutableList()
        if (enabled) disabled.remove(slotId) else if (slotId !in disabled) disabled.add(slotId)
        settings.setBottomBar(cfg.copy(disabled = disabled.distinct()))
    }

    /** 拖拽排序后的顺序提交（disabled 原样保留；池内其余项补在后面，不丢项） */
    fun setOrder(order: List<String>) = viewModelScope.launch {
        val cfg = config.value
        val cleaned = order.filter { it in BottomBarConfig.CONFIGURABLE }.distinct()
        settings.setBottomBar(cfg.copy(order = cleaned + BottomBarConfig.DEFAULT_ORDER.filter { it !in cleaned }))
    }
}
