package com.mdot.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.domain.model.HomeCardsConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页卡片配置（v0.6.0 首页卡片可编辑）：开关显隐 + 拖拽排序；至少保留一张 */
@HiltViewModel
class HomeCardsViewModel @Inject constructor(
    private val settings: SettingsDataSource,
) : ViewModel() {
    val config: StateFlow<HomeCardsConfig> = settings.homeCardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeCardsConfig())

    /** 显示/隐藏某张卡片：**只改 disabled，不动 order**（开关不影响顺序，对齐班次卡片） */
    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        if (id == HomeCardsConfig.DATA) return@launch // 数据区固定不可隐藏
        val cfg = config.value
        val disabled = cfg.disabled.toMutableList()
        if (enabled) disabled.remove(id) else if (id !in disabled) disabled.add(id)
        settings.setHomeCards(cfg.copy(disabled = disabled.distinct()))
    }

    /** 换某张卡显示什么（卡片内容自定义）：只动 content，不动 order/disabled。
     *  [ids] = **选中的内容项**（单选卡传单元素列表；多选卡传多个，顺序即显示顺序） */
    fun setContents(cardId: String, ids: List<String>) = viewModelScope.launch {
        val valid = ids.distinct().filter { com.mdot.app.domain.model.HomeCardContents.isValid(cardId, it) }
        if (valid.isEmpty()) return@launch
        val cfg = config.value
        val next = cfg.content.toMutableMap()
        // 回到出厂默认就不留记录（存储最小化，以后升/降级归一化也简单）
        if (valid == com.mdot.app.domain.model.HomeCardContents.defaultsOf(cardId)) next.remove(cardId)
        else next[cardId] = valid
        settings.setHomeCards(cfg.copy(content = next))
    }

    /** 拖拽排序后的顺序提交（disabled 原样保留；池内其余项补在后面，不丢项） */
    fun setOrder(order: List<String>) = viewModelScope.launch {
        val cfg = config.value
        val cleaned = order.filter { it in HomeCardsConfig.POOL }.distinct()
        settings.setHomeCards(cfg.copy(order = cleaned + HomeCardsConfig.DEFAULT_ORDER.filter { it !in cleaned }))
    }
}
