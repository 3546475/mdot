package com.mdot.app.core.designsystem.component

import androidx.annotation.DrawableRes
import com.mdot.app.R
import com.mdot.app.domain.model.HomeCardsConfig

/**
 * 首页卡片注册表（v0.6.0 首页卡片可编辑，模式对齐底栏 SlotRegistry）。
 * 每个卡片槽位 = id + labelRes + iconRes；显示顺序由 HomeCardsConfig.cards 决定。
 * POOL 顺序 = 出厂默认显示顺序；「入口卡」实际渲染为日历+统计两张并排小卡，
 * 若底栏已配置日历/统计则该组入口自动隐藏（原 showEntryCards 逻辑，HomeViewModel 处理）。
 */
data class HomeCardSpec(
    val id: String,
    val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

object HomeCardRegistry {
    val ALL = mapOf(
        "data" to HomeCardSpec("data", R.string.home_card_data, R.drawable.ic_ms_bar_chart),
        "income" to HomeCardSpec("income", R.string.home_card_income, R.drawable.ic_ms_paid),
        "entries" to HomeCardSpec("entries", R.string.home_card_entries, R.drawable.ic_ms_dashboard),
        "heatmap" to HomeCardSpec("heatmap", R.string.home_card_heatmap, R.drawable.ic_ms_table_chart),
        "weekbar" to HomeCardSpec("weekbar", R.string.home_card_weekbar, R.drawable.ic_ms_bar_chart),
        "monthbar" to HomeCardSpec("monthbar", R.string.home_card_monthbar, R.drawable.ic_ms_bar_chart),
    )

    /** 解析出有效显示序列：null/未配置 = POOL 默认；剔除未知 id；去重；不足 MIN_CARDS 回退默认 */
    fun resolve(cards: List<String>?): List<HomeCardSpec> {
        if (cards == null) return POOL_SPECS
        val cleaned = cards.filter { ALL.containsKey(it) }.distinct()
        return if (cleaned.size < HomeCardsConfig.MIN_CARDS) POOL_SPECS
        else cleaned.mapNotNull { ALL[it] }
    }

    val POOL_SPECS: List<HomeCardSpec> = HomeCardsConfig.POOL.mapNotNull { ALL[it] }

    fun resolveSpec(id: String): HomeCardSpec? = ALL[id]
}
