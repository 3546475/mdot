package com.mdot.app.core.navigation

/** 路由常量（04 文档 §2.1 路由表） */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR_PATTERN = "calendar?month={month}"
    const val STATS = "stats"
    const val COMP = "comp"
    const val PAYROLL = "payroll"
    const val EXPORT = "export"
    const val SYNC = "sync"
    const val SYNC_STORAGE = "sync/storage"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val BOTTOM_BAR = "settings/bottombar"
    const val SYSTEM = "settings/system"
    const val SYSTEM_SWITCH = "settings/system/switch"
    const val CYCLE = "settings/cycle"
    const val WORKDAYS = "settings/workdays"
    const val SHIFTS = "settings/shifts"
    const val APPEARANCE = "settings/appearance"
    const val DATASOURCE = "settings/datasource"
    const val ABOUT = "settings/about"

    fun calendar(month: String? = null): String =
        if (month == null) "calendar" else "calendar?month=$month"

    /**
     * 一级页面（底栏功能池路由，路由 pattern 去掉查询参数后比对）：
     * 一级页面显示底栏、Tab 式切换；其余路由均为二级/次级页面（无底栏 + 顶栏）。
     * 注意：设置是二级页面（从首页 ⚙ / 我的 进入）。
     */
    val TOP_LEVEL_BASE_ROUTES = setOf(
        HOME, "calendar", STATS, PAYROLL, EXPORT, SYNC, PROFILE,
    )

    /** 当前路由（destination.route，可含 ?query）是否一级页面 */
    fun isTopLevel(destinationRoute: String?): Boolean =
        destinationRoute?.substringBefore('?') in TOP_LEVEL_BASE_ROUTES

    /** 导航目标路由是否一级页面 */
    fun isTopLevelTarget(route: String): Boolean =
        route.substringBefore('?') in TOP_LEVEL_BASE_ROUTES
}
