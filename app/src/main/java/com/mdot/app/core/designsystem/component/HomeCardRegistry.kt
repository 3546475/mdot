package com.mdot.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mdot.app.R
import com.mdot.app.domain.model.HomeCardContents
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

    /** 解析出有效显示序列（调用方传「已启用」列表，顺序即显示顺序）：剔除未知 id、去重；空则回退整池 */
    fun resolve(cards: List<String>): List<HomeCardSpec> {
        val cleaned = cards.filter { ALL.containsKey(it) }.distinct()
        return if (cleaned.isEmpty()) POOL_SPECS else cleaned.mapNotNull { ALL[it] }
    }

    val POOL_SPECS: List<HomeCardSpec> = HomeCardsConfig.POOL.mapNotNull { ALL[it] }

    fun resolveSpec(id: String): HomeCardSpec? = ALL[id]
}

/**
 * 卡片内容项的 UI 资源（卡片内容编辑）。**id 在 domain 的 [HomeCardContents]**（硬规则 1：
 * domain 零 Android 依赖），这里只负责 id → 文案/图标，显示点才解析。
 *
 * 当前只「数据区」开放内容自定义；以后其他卡要开放，往 [ALL] 加行 + 在 [HomeCardContents.OPTIONS]
 * 里给它列选项即可，模型与配置页不用改。
 */
data class HomeCardContentUi(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

object HomeCardContentRegistry {
    val ALL = listOf(
        // 数据区（单选）
        HomeCardContentUi(HomeCardContents.DATA_OT_HOURS, R.string.home_content_ot_hours, R.drawable.ic_ms_more_time),
        HomeCardContentUi(HomeCardContents.DATA_NET_PAY, R.string.home_content_net_pay, R.drawable.ic_ms_account_balance_wallet),
        // 快捷入口（多选；选中项渲染成入口卡，图标/文案与这里同源）
        HomeCardContentUi(HomeCardContents.ENTRY_CALENDAR, R.string.home_entry_calendar, R.drawable.ic_ms_calendar_month),
        HomeCardContentUi(HomeCardContents.ENTRY_STATS, R.string.home_entry_stats, R.drawable.ic_ms_bar_chart),
        HomeCardContentUi(HomeCardContents.ENTRY_PAYMONTH, R.string.home_entry_paymonth, R.drawable.ic_ms_paid),
        HomeCardContentUi(HomeCardContents.ENTRY_DETAIL, R.string.home_entry_detail, R.drawable.ic_ms_table_chart),
        HomeCardContentUi(HomeCardContents.ENTRY_PROFILE, R.string.home_entry_profile, R.drawable.ic_ms_person),
    )

    /** 某张卡可选的内容项（顺序与 domain 的 [HomeCardContents.optionsOf] 一致） */
    fun of(cardId: String): List<HomeCardContentUi> =
        HomeCardContents.optionsOf(cardId).mapNotNull { id -> ALL.find { it.id == id } }

    fun resolve(id: String): HomeCardContentUi? = ALL.find { it.id == id }
}
