package com.mdot.app.core.navigation

/** 路由常量（04 文档 §2.1 路由表） */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR_PATTERN = "calendar?month={month}"
    const val STATS = "stats"
    /** 统计页带初始页签：tab=1 工地制度「明细」、tab=2 非工地「明细」；0=统计 */
    const val STATS_PATTERN = "stats?tab={tab}"

    /**
     * 统计页的「二级页实例」（首页统计卡 / 收入卡明细 / 工资页查看记录等入口专用）。
     * 与底栏那个一级 Tab 实例彻底分开：本路由不在底栏→不显示固定顶栏/底栏、永远带返回；
     * 也不再被当成一级页参与整页横滑（用户报告：曾误入底栏那个实例、没有返回）
     */
    const val STATS_DETAIL_PATTERN = "statsDetail?tab={tab}"

    fun statsDetail(tab: Int): String = "statsDetail?tab=$tab"

    fun stats(tab: Int): String = if (tab <= 0) STATS else "stats?tab=$tab"
    const val EXPORT = "export"
    const val SYNC = "sync"
    const val SYNC_STORAGE = "sync/storage"
    const val PROFILE = "profile"
    const val BOTTOM_BAR = "settings/bottombar"
    const val HOME_CARDS = "settings/homecards"
    const val SITE_PROJECTS = "site/projects"
    const val SITE_PROJECTS_PATTERN = "site/projects?pick={pick}"
    const val SITE_SETTLEMENT = "site/settlement"
    const val SITE_RECORD = "site/record"
    const val SITE_PROJECT_EDIT_PATTERN = "site/project/{projectId}"

    fun siteProjectEdit(projectId: Long) = "site/project/$projectId"

    /** 项目管理页；pick=true 为「选择模式」（从记工页进入：点行即切换当前项目并返回） */
    fun siteProjects(pick: Boolean = false): String =
        if (pick) "$SITE_PROJECTS?pick=1" else SITE_PROJECTS

    const val SYSTEM = "settings/system"
    const val SYSTEM_SWITCH = "settings/system/switch"
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
        HOME, "calendar", STATS, EXPORT, SYNC, PROFILE,
    )

    /** 当前路由（destination.route，可含 ?query）是否一级页面 */
    fun isTopLevel(destinationRoute: String?): Boolean =
        destinationRoute?.substringBefore('?') in TOP_LEVEL_BASE_ROUTES

    /** 导航目标路由是否一级页面 */
    fun isTopLevelTarget(route: String): Boolean =
        route.substringBefore('?') in TOP_LEVEL_BASE_ROUTES
}
