package com.mdot.app.core.navigation

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mdot.app.AppViewModel
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.AdaptiveContainer
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.rememberContentSideInset
import com.mdot.app.core.designsystem.rememberWindowSpec
import com.mdot.app.core.designsystem.component.JiabanBottomBar
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import com.mdot.app.core.designsystem.component.BackdropBlurState
import com.mdot.app.core.designsystem.component.RecordCircleButton
import com.mdot.app.core.designsystem.component.RecordPillButton
import com.mdot.app.core.designsystem.component.backdropBlur
import com.mdot.app.core.designsystem.component.backdropBlurSource
import com.mdot.app.core.designsystem.component.TopLevelBar
import com.mdot.app.core.designsystem.component.SheetBackdropLayer
import com.mdot.app.core.designsystem.component.SlotRegistry
import com.mdot.app.feature.calendar.CalendarScreen
import com.mdot.app.feature.export.ExportScreen
import com.mdot.app.feature.home.HomeScreen
import com.mdot.app.feature.onboarding.OnboardingScreen
import com.mdot.app.feature.profile.ProfileScreen
import com.mdot.app.feature.record.RecordSheet
import com.mdot.app.feature.record.RecordSheetViewModel
import com.mdot.app.feature.payroll.OtPricingScreen
import com.mdot.app.feature.settings.AboutScreen
import com.mdot.app.feature.settings.DataSourceScreen
import com.mdot.app.feature.settings.SystemSettingsScreen
import com.mdot.app.feature.settings.SystemSwitchScreen
import com.mdot.app.feature.stats.StatsScreen
import com.mdot.app.feature.sync.SyncScreen
import com.mdot.app.feature.settings.HomeBottomConfigScreen
import com.mdot.app.feature.site.SiteProjectsScreen
import com.mdot.app.feature.site.SiteProjectEditScreen
import com.mdot.app.feature.site.SiteSettlementScreen
import com.mdot.app.feature.site.SiteRecordScreen
import com.mdot.app.core.designsystem.component.JiabanFab
import com.mdot.app.core.designsystem.component.OverlayDialogLayer
import com.mdot.app.core.designsystem.sheetSpatialSpec
import com.mdot.app.core.designsystem.MotionSpec
import com.mdot.app.core.designsystem.SheetBackdrop
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import com.mdot.app.core.designsystem.component.LocalOverlayDialogs

@Composable
fun AppRoot(firstLaunchDone: Boolean, appVm: AppViewModel, backdropBlurState: BackdropBlurState, backdropFullState: BackdropBlurState) {
    // 响应式布局（docs 03 §3.2）：窗口档位全局下发（页面读 LocalWindowSpec 决定单列/双栏）；
    // 二级页统一经 AdaptiveContainer 限宽居中（下方逐页包裹）；固定一级顶栏/底栏/
    // 记录弹层/FAB 等悬浮层各自跟随内容宽度对齐
    val windowSpec = rememberWindowSpec()
    val fabSideInset = rememberContentSideInset()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWindowSpec provides windowSpec,
    ) {
        AppRootContent(firstLaunchDone, appVm, fabSideInset, backdropBlurState, backdropFullState)
    }
}

@Composable
private fun AppRootContent(
    firstLaunchDone: Boolean,
    appVm: AppViewModel,
    fabSideInset: androidx.compose.ui.unit.Dp,
    /** 玻璃共享源（MainActivity 创建后经参数传入；底栏/圆钮/提示窗共用） */
    backdropBlurState: BackdropBlurState,
    backdropFullState: BackdropBlurState,
) {
    val navController = rememberNavController()
    // 记录弹层 VM 为 Activity 级（弹层在 NavHost 外组合）——此处取同一实例，
    // 弹层关闭后仍能收它的删除撤销消息（硬规则 11：撤销住底部提示窗）
    val recordVm: RecordSheetViewModel = hiltViewModel()
    val request by appVm.recordRequest.collectAsStateWithLifecycle()
    // 记录弹层可见性状态提升到此处：背景「模糊 + 缩小」层（SheetBackdropLayer）与弹层自身
    // 共享同一过渡状态，进出场严格同步（弹层内部负责置 targetState）
    val recordSheetVisible = remember { MutableTransitionState(false) }
    // 底栏毛玻璃：导航宿主为「源」（录制内容层），底栏为「效果方」（模糊身后内容）；
    // 源状态由 MainActivity 创建（参数传入，提示窗等共享采样）
    val bottomBar by appVm.bottomBar.collectAsStateWithLifecycle()
    val bottomBarIconOnly by appVm.bottomBarIconOnly.collectAsStateWithLifecycle()
    val bottomBarSideAction by appVm.bottomBarSideAction.collectAsStateWithLifecycle()
    val bottomBarFixedWidth by appVm.bottomBarFixedWidth.collectAsStateWithLifecycle()
    val bottomBarFrosted by appVm.bottomBarFrosted.collectAsStateWithLifecycle()
    // 液态玻璃是**交互**、毛玻璃是**材质**，两者独立（2026-10-30 边界重规划）：
    // 只有毛玻璃需要挂页面玻璃源；液滴读的是底栏自身的录制层，与磨砂无关
    val bottomBarLiquidGlass by appVm.bottomBarLiquidGlass.collectAsStateWithLifecycle()
    val workSystem by appVm.workSystem.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 去掉 ?month 等查询参数后比对
    val currentBase = currentRoute?.substringBefore('?')
    // 底栏可见性 = 当前页面确实配置在用户的底栏槽位里；
    // 池里但未配置的页面（工资/导出/同步）走无底栏形态，顶栏带返回
    // 覆盖层控制器：由 MainActivity 的 setContent 下发（组合级持有，非进程单例）
    val overlayDialogs = LocalOverlayDialogs.current
    val slots = bottomBar.slots.toSet()
    val orderedSlots = bottomBar.slots
    val showBar = currentBase != null && currentBase in slots
    // 池页面是否以"底栏一级"形态出现
    val inBar: (String) -> Boolean = { it.substringBefore('?') in slots }

    Box(Modifier.fillMaxSize()) {
        // 背景层：弹层出现时整屏内容模糊 + 缩小成圆角卡片（弹层自身在最上层、不受影响）
        // 覆盖层（页内模态弹窗）也用**同一条背景层**：弹窗本体画在层外 ⇒ 只糊「背后的页面」，
        // 与底部弹层完全同款（页内模态不能再自建一套模糊管线，见 OverlayDialog.kt）
        SheetBackdropLayer(visible = recordSheetVisible.targetState || overlayDialogs?.current != null) {
            // 全画面源（对话框背景效果采样用）：包住内容+顶栏+底栏+FAB——⚠️ 玻璃消费方
            // （底栏/圆钮）画的是**另一个** NavHost 源的层，两源不同 RenderNode 无自引用环
            // （docs/11 062 禁的是「效果方画自己所在的源」）。按需录制（BackdropSourceDemand）。
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropBlurSource(backdropFullState)
            ) {
            // 转场动画（03 文档 §7）：底栏 Tab=淡入淡出 200ms；层级跳转=水平滑入 300ms（返回反向）
            val isTabEntry: (androidx.navigation.NavBackStackEntry) -> Boolean =
                { entry -> entry.destination.route?.substringBefore('?') in slots }
            NavHost(
                navController = navController,
                startDestination = if (firstLaunchDone) Routes.HOME else Routes.ONBOARDING,
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlurSource(backdropBlurState),
                enterTransition = {
                    if (isTabEntry(targetState) && isTabEntry(initialState))
                        slideInHorizontally(
                            MotionSpec.tabSwitch()
                        ) { it * lastTabSwipeDir.value } +
                            fadeIn(tween(Duration.normal))
                    else
                        slideInHorizontally(tween(Duration.slow)) { it } +
                            fadeIn(tween(Duration.slow)) +
                            scaleIn(tween(Duration.slow), initialScale = 0.96f)
                },
                exitTransition = {
                    if (isTabEntry(targetState))
                        slideOutHorizontally(
                            MotionSpec.tabSwitch()
                        ) { -it * lastTabSwipeDir.value / 4 } +
                            fadeOut(tween(Duration.normal))
                    else
                        slideOutHorizontally(tween(Duration.slow)) { -it / 4 } +
                            fadeOut(tween(Duration.slow)) +
                            scaleOut(tween(Duration.slow), targetScale = 0.96f)
                },
                popEnterTransition = {
                    when {
                        isTabEntry(targetState) && isTabEntry(initialState) ->
                            slideInHorizontally(
                                MotionSpec.tabSwitch()
                            ) { it * lastTabSwipeDir.value } +
                                fadeIn(tween(Duration.normal))
                        isTabEntry(targetState) ->
                            fadeIn(tween(Duration.normal))
                        else ->
                            slideInHorizontally(tween(Duration.slow)) { -it / 4 } +
                                fadeIn(tween(Duration.slow)) +
                                scaleIn(tween(Duration.slow), initialScale = 1.04f)
                    }
                },
                popExitTransition = {
                    if (isTabEntry(targetState) && isTabEntry(initialState))
                        slideOutHorizontally(
                            MotionSpec.tabSwitch()
                        ) { -it * lastTabSwipeDir.value / 4 } +
                            fadeOut(tween(Duration.normal))
                    else
                        slideOutHorizontally(tween(Duration.slow)) { it } +
                            fadeOut(tween(Duration.slow)) +
                            scaleOut(tween(Duration.slow), targetScale = 1.04f)
                },
            ) {
                composable(Routes.ONBOARDING) {
                    AdaptiveContainer {
                        OnboardingScreen(onDone = { navTo(navController, Routes.HOME, slots, clearStack = true) })
                    }
                }
                composable(Routes.HOME) {
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.HOME, onNavigate = { navTo(navController, it, slots) }) {
                        HomeScreen(
                            onOpenCalendar = { navTo(navController, Routes.CALENDAR_PATTERN, slots) },
                            onOpenStats = { navTo(navController, Routes.statsEntry(0, slots), slots) },
                            onOpenDetail = { navTo(navController, Routes.statsEntry(if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) 1 else 2, slots), slots) },
                            // 快捷入口的「记月」→ 记月页签（统计/记月/明细 = 0/1/2）；
                            // 工地制度下该入口不渲染（无记月概念），故这里不必按制度分支
                            onOpenPayMonth = { navTo(navController, Routes.statsEntry(1, slots), slots) },
                            onOpenProfile = { navTo(navController, Routes.PROFILE, slots) },
                            onOpenRecord = {
                                if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                                    navTo(navController, Routes.SITE_RECORD, slots)
                                } else {
                                    appVm.recordSheetController.open(java.time.LocalDate.now())
                                }
                            },
                            // 图表长按「记那一天」按制度分流（21 文档：工地勿弹其它模式的记录弹层）
                            onChartDay = { date ->
                                if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                                    navTo(navController, Routes.siteRecord(date), slots)
                                } else {
                                    appVm.recordSheetController.open(date)
                                }
                            },
                        )
                    }
                }
                composable(
                    Routes.CALENDAR_PATTERN,
                    arguments = listOf(navArgument("month") {
                        type = NavType.StringType
                        defaultValue = ""
                    }),
                ) { entry ->
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.CALENDAR_PATTERN, onNavigate = { navTo(navController, it, slots) }) {
                        CalendarScreen(
                            initialMonth = entry.arguments?.getString("month"),
                            canBack = !inBar("calendar"),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    Routes.STATS_DETAIL_PATTERN,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    // 统计页二级页实例：不包 SwipeTabHost（不参与整页横滑），永远带返回
                    AdaptiveContainer {
                        StatsScreen(
                            canBack = true,
                            onBack = { navController.popBackStack() },
                            initialTab = entry.arguments?.getInt("tab") ?: 0,
                            onOpenTax = { navTo(navController, Routes.TAX_ESTIMATE, slots) },
                            onChartDay = { date ->
                                if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                                    navTo(navController, Routes.siteRecord(date), slots)
                                } else {
                                    appVm.recordSheetController.open(date)
                                }
                            },
                        )
                    }
                }
                composable(
                    Routes.STATS_PATTERN,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.STATS, onNavigate = { navTo(navController, it, slots) }) {
                        StatsScreen(
                            canBack = !inBar("stats"),
                            onBack = { navController.popBackStack() },
                            initialTab = entry.arguments?.getInt("tab") ?: 0,
                            onOpenTax = { navTo(navController, Routes.TAX_ESTIMATE, slots) },
                            onChartDay = { date ->
                                if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                                    navTo(navController, Routes.siteRecord(date), slots)
                                } else {
                                    appVm.recordSheetController.open(date)
                                }
                            },
                        )
                    }
                }
                composable(Routes.EXPORT) {
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.EXPORT, onNavigate = { navTo(navController, it, slots) }) {
                        AdaptiveContainer {
                            ExportScreen(
                                canBack = !inBar("export"),
                                onBack = { navController.popBackStack() },
                                onViewRecords = { navTo(navController, Routes.statsEntry(if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) 1 else 2, slots), slots) },
                            )
                        }
                    }
                }
                composable(Routes.SYNC) {
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.SYNC, onNavigate = { navTo(navController, it, slots) }) {
                        AdaptiveContainer {
                            SyncScreen(
                                canBack = !inBar("sync"),
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                composable(Routes.SYNC_STORAGE) {
                    AdaptiveContainer {
                        SyncScreen(canBack = true, initialTab = 1, onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.PROFILE) {
                    SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.PROFILE, onNavigate = { navTo(navController, it, slots) }) {
                        AdaptiveContainer {
                            ProfileScreen(
                                canBack = !inBar("profile"),
                                onBack = { navController.popBackStack() },
                                onOpen = { route -> navTo(navController, route, slots) },
                            )
                        }
                    }
                }
                composable(
                    Routes.SYSTEM_TAB_PATTERN,
                    arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    AdaptiveContainer {
                        SystemSettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProject = { id -> navTo(navController, Routes.siteProjectEdit(id), slots) },
                            initialTabKey = entry.arguments?.getString("tab"),
                            onOpenOtPricing = { navTo(navController, Routes.OT_PRICING, slots) },
                        )
                    }
                }
                composable(Routes.SYSTEM) {
                    AdaptiveContainer {
                        // 顶栏齿轮＝**制度设定**：当前工时制度的多页签页（原「工时设置」入口列表页移除）
                        SystemSettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProject = { id -> navTo(navController, Routes.siteProjectEdit(id), slots) },
                            onOpenOtPricing = { navTo(navController, Routes.OT_PRICING, slots) },
                        )
                    }
                }
                composable(Routes.OT_PRICING) {
                    AdaptiveContainer {
                        OtPricingScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.SYSTEM_SWITCH) {
                    AdaptiveContainer {
                        SystemSwitchScreen(
                            onBack = { navController.popBackStack() },
                            // 切换确认生效（撤销倒计时结束）后自动回首页（Tab 式导航，恢复首页状态）
                            onAutoHome = {
                                // 回首页用**纯弹栈**：实测 navigate(HOME) + popUpTo(start){saveState}+restoreState
                                // 在这里是 no-op（currentDestination 不变、界面不动，debug/release 一样，见 docs/11 043）——
                                // 弹栈不走那套 saved-state 机制，也不受 Tab 防抖影响。
                                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                                    // 万一首页不在栈里（异常状态）：直接重建到首页
                                    navTo(navController, Routes.HOME, slots, clearStack = true)
                                }
                            },
                        )
                    }
                }
                composable(Routes.APPEARANCE) {
                    AdaptiveContainer {
                        // 「外观」入口进入「外观 / 首页 / 底栏」合并页，默认落「外观」页签
                        HomeBottomConfigScreen(initialTab = 0, onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.BOTTOM_BAR) {
                    AdaptiveContainer { HomeBottomConfigScreen(initialTab = 2, onBack = { navController.popBackStack() }) }
                }
                composable(Routes.HOME_CARDS) {
                    AdaptiveContainer { HomeBottomConfigScreen(initialTab = 1, onBack = { navController.popBackStack() }) }
                }
                // ---- 工地记工（12 文档 F-S2/F-S6） ----
                composable(
                    Routes.SITE_PROJECTS_PATTERN,
                    arguments = listOf(navArgument("pick") {
                        type = NavType.StringType
                        defaultValue = "0"
                    }),
                ) { entry ->
                    AdaptiveContainer {
                        SiteProjectsScreen(
                            pickMode = entry.arguments?.getString("pick") == "1",
                            onBack = { navController.popBackStack() },
                            onOpenProject = { id -> navTo(navController, Routes.siteProjectEdit(id), slots) },
                            onPicked = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.SITE_PROJECT_EDIT_PATTERN) { entry ->
                    AdaptiveContainer {
                        SiteProjectEditScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.TAX_ESTIMATE) {
                    AdaptiveContainer {
                        com.mdot.app.feature.tax.TaxEstimateScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.SITE_SETTLEMENT) {
                    AdaptiveContainer {
                        SiteSettlementScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.SITE_RECORD_PATTERN,
                    arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    AdaptiveContainer {
                        SiteRecordScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProjectSettings = { id ->
                                navTo(navController, Routes.siteProjectEdit(id), slots)
                            },
                            onOpenProjectPick = {
                                navTo(navController, Routes.siteProjects(pick = true), slots)
                            },
                            onOpenSettlement = { navTo(navController, Routes.SITE_SETTLEMENT, slots) },
                            onOpenDetail = { navTo(navController, Routes.statsEntry(1, slots), slots) },
                            // 图表长按「记那一天」带初始日期（21 文档）
                            initialDate = entry.arguments?.getString("date")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                        )
                    }
                }
                composable(Routes.ABOUT) {
                    AdaptiveContainer {
                        AboutScreen(
                            onBack = { navController.popBackStack() },
                            onOpen = { route -> navTo(navController, route, slots) },
                        )
                    }
                }
                composable(Routes.DATASOURCE) {
                    AdaptiveContainer {
                        DataSourceScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            // 一级页面统一固定顶栏（无标题：左=标准工时切换，右=齿轮设置）。
            // 挂在 NavHost 之外、底栏与弹层之下，页面切换时不参与转场、保持不动。
            // MD3E：顶栏显隐走 motionScheme effects spec（与底栏动效语言一致，禁硬编码时长）
            val topBarFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(topBarFadeSpec),
                exit = fadeOut(topBarFadeSpec),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // 2026-09-30 用户口径：顶栏改**透明底**（MIUIX 与 MD3 一致，内容可从下方穿过可见）。
                        // 原「不透底」（2026-09-20 用户定）作废。
                        // ⚠️ 代价：滚动时内容会从顶栏文字/图标下方穿过；若观感不佳，可加顶栏下缘渐隐遮罩。
                        // 毛玻璃在本结构下不可用（顶栏在共享源子树内，见 docs/11 062 自引用禁令）。
                        .background(androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    TopLevelBar(
                        workSystem = workSystem,
                        onOpenWorkSystem = { navTo(navController, Routes.SYSTEM_SWITCH, slots) },
                        onOpenSettings = { navTo(navController, Routes.SYSTEM, slots) },
                        // 设置入口兜底：不依赖底栏里是否存在「我的」（见 TopLevelBar 类注释）
                        onOpenAppearance = { navTo(navController, Routes.APPEARANCE, slots) },
                    )
                }
            }

            val calSelDate by appVm.recordSheetController.calendarSelectedDate.collectAsStateWithLifecycle()
            // 中央按钮入场动画只播一次：底栏隐藏→再现会销毁/重建该按钮，
            // flag 提升到 AppRootContent（跨导航存活）避免每次重播弹簧弹入
            var recordPillEntered by remember { mutableStateOf(false) }
            // 记加班按钮的触发逻辑（居中胶囊 / 右侧圆钮两种布局共用）
            val recordAction: () -> Unit = {
                when {
                    // 日历一级页：承担原悬浮 FAB 功能——工地模式直达记工页，其余打开选中日期的记录
                    currentBase == "calendar" && workSystem == com.mdot.app.domain.model.WorkSystem.SITE ->
                        navTo(navController, Routes.SITE_RECORD, slots)
                    currentBase == "calendar" -> appVm.recordSheetController.open(calSelDate)
                    workSystem == com.mdot.app.domain.model.WorkSystem.SITE ->
                        navTo(navController, Routes.SITE_RECORD, slots)
                    else -> appVm.recordSheetController.open(java.time.LocalDate.now())
                }
            }
            // 两种布局形态二选一（外观页「记加班按钮置右」）：居中胶囊 / 右侧独立圆钮（仅图标）
            val recordPill: @Composable () -> Unit = {
                RecordPillButton(
                    onRecord = recordAction,
                    playEntrance = !recordPillEntered,
                    onEntranceDone = { recordPillEntered = true },
                )
            }
            val recordCircle: @Composable () -> Unit = {
                RecordCircleButton(
                    onRecord = recordAction,
                    playEntrance = !recordPillEntered,
                    onEntranceDone = { recordPillEntered = true },
                    // 「按钮右置」布局下与底栏共用同一套玻璃源（磨砂材质只看毛玻璃开关；关闭/API<31 → 实心主色）
                    backdropBlur = if (bottomBarFrosted) backdropBlurState else null,
                )
            }
            JiabanBottomBar(
                slots = bottomBar.slots.mapNotNull { SlotRegistry.resolve(it) },
                selectedRoute = currentBase,
                visible = showBar,
                iconOnly = bottomBarIconOnly,
                centerAction = if (bottomBarSideAction) null else recordPill,
                sideAction = if (bottomBarSideAction) recordCircle else null,
                backdropBlur = if (bottomBarFrosted) backdropBlurState else null,
                liquidGlass = bottomBarLiquidGlass,
                // 液态玻璃开时：拖动到中央「记加班」按钮上松手由补记入口接管（拖动期间 clickable 已被消费）
                onCenterAction = if (bottomBarSideAction) null else recordAction,
                fixedCells = if (bottomBarFixedWidth) BottomBarSpec.fixedCellCount else 0,
                onSlotClick = { spec ->
                    // 点击底栏：按目标相对当前位置设置左右平移方向
                    val fromIdx = orderedSlots.indexOf(currentBase)
                    val toIdx = orderedSlots.indexOf(spec.route)
                    lastTabSwipeDir.value = if (fromIdx >= 0 && toIdx >= 0 && toIdx < fromIdx) -1 else 1
                    navTo(navController, spec.route, slots)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // 日历页面右下角 FAB：补记/编辑
            val isCalendarPage = currentBase == "calendar"
            // 日历为一级页（在底栏）时由底栏中央记加班按钮承担记录入口，悬浮 FAB 仅二级页形态保留
            val showCalendarFab = isCalendarPage && !inBar("calendar")
            val fabBottomPadding = Spacing.page + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val calFabFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val calFabScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            androidx.compose.animation.AnimatedVisibility(
                visible = showCalendarFab,
                enter = androidx.compose.animation.fadeIn(calFabFadeSpec) +
                    androidx.compose.animation.scaleIn(calFabScaleSpec, initialScale = 0.8f),
                exit = androidx.compose.animation.fadeOut(calFabFadeSpec) +
                    androidx.compose.animation.scaleOut(calFabScaleSpec, targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.page + fabSideInset, bottom = fabBottomPadding),
            ) {
                JiabanFab(
                    onClick = {
                        // 工地模式：直接打开记工页（不走加班/请假弹窗）
                        if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                            navTo(navController, Routes.SITE_RECORD, slots)
                        } else {
                            appVm.recordSheetController.open(calSelDate)
                        }
                    },
                    shape = engineShape(Radius.textField),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.ic_ms_add), contentDescription = stringResource(R.string.nav_fab_cd))
                }
            }
        }
        }

        // ---- 全局底部提示（一个宿主两条消息；优先级：记录删除撤销 > 冷启动新版本）----
        // 记录删除撤销：弹层已关、触发元素消失，按硬规则 11 撤销住提示窗（同调休删调整）；
        // 新版本「去更新」：只在首页渲染（冷启动落点），避免与各页面自带 MessageSnackbar 叠罗汉。
        // 底部抬高 = 底栏占位（与 bottomBarContentPaddingValues 同式，宿主内部已另加导航栏避让）
        val recordMessage by recordVm.messageFlow.collectAsStateWithLifecycle()
        val updateAvailable by appVm.updateAvailable.collectAsStateWithLifecycle()
        val rMsg = recordMessage
        val showUpdatePrompt =
            firstLaunchDone && updateAvailable != null && currentBase == Routes.HOME
        val globalMessage: String? = when {
            rMsg != null -> rMsg.text
            showUpdatePrompt -> stringResource(R.string.update_available_msg, "v$updateAvailable")
            else -> null
        }
        val (globalSnackbarState, globalIsError) = rememberMessageSnackbar(
            message = globalMessage,
            isError = rMsg?.isError == true,
            onClear = {
                if (recordMessage != null) recordVm.clearMessage()
                else appVm.dismissUpdateAvailable()
            },
            actionLabel = when {
                rMsg?.canUndo == true -> stringResource(R.string.record_undo)
                showUpdatePrompt -> stringResource(R.string.update_available_action)
                else -> null
            },
            onAction = {
                if (recordMessage?.canUndo == true) {
                    recordVm.undoDelete()
                } else {
                    appVm.dismissUpdateAvailable()
                    navController.navigate(Routes.ABOUT) { launchSingleTop = true }
                }
            },
        )
        if (globalMessage != null) {
            MessageSnackbarHost(
                globalSnackbarState,
                isError = globalIsError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 底栏占位（= bottomBarContentPaddingValues 减导航栏——宿主内部已另加导航栏避让）；
                    // 8dp 用 Spacing.s：SpacingContractTest 禁 padding 行出现 dp 字面量
                    .padding(bottom = BottomBarSpec.height + BottomBarSpec.bottomMargin + Spacing.s),
            )
        }

        request?.let { req ->
            // 弹层在「有弹窗盖在它上面」时也要糊掉——但**不能用 SheetBackdropLayer**：
            // 那个组件会给内容垫一层**全屏不透明实底**（为"页面内容"设计，见它内部那句"不透明实底"），
            // 而弹层画在页面背景层**之外** ⇒ 那层实底会把背后已经模糊好的页面整片盖成一片纯色
            // （用户 2026-09-30 三次反馈的"背景一片空白"根因；它在 progress=0 时也照画，与弹窗无关）。
            // 故这里只做「模糊 + 压暗」，不垫底：模糊在链首（先糊再压暗），压暗用 drawWithContent 叠加。
            val sheetUnderDialog by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (overlayDialogs?.current != null) 1f else 0f,
                animationSpec = com.mdot.app.core.designsystem.sheetSpatialSpec<Float>(),
                label = "sheetUnderDialog",
            )
            // ⚠️ 颜色要在 Composable 上下文里取好：drawWithContent 的 lambda 是 DrawScope（非 Composable）
            val sheetScrim = MaterialTheme.colorScheme.scrim
            Box(
                Modifier
                    .blur(SheetBackdrop.blurRadius * sheetUnderDialog)
                    .drawWithContent {
                        drawContent()
                        drawRect(sheetScrim.copy(alpha = SheetBackdrop.contentScrim * sheetUnderDialog))
                    },
            ) {
                RecordSheet(
                    request = req,
                    visibleState = recordSheetVisible,
                    onDismiss = { appVm.recordSheetController.dismiss() },
                )
            }
        }
    // 页内模态覆盖层（所有弹窗都在这里渲染）——**必须是根 Box 的最后一个子节点**：
    // ① 要在 SheetBackdropLayer **之外**（否则会被连带着模糊/压暗/缩放）；
    // ② 要在 RecordSheet / 全局提示窗**之后**（否则"从弹层里打开的日期选择"会被弹层盖住）。
    // 两条都踩过坑，见 OverlayDialog.kt 与 docs/11 071。
    if (overlayDialogs != null) OverlayDialogLayer(overlayDialogs)
    }
}

/**
 * 统一导航：
 * - 一级页面（底栏功能池路由）：Tab 式切换——弹回起始页之上、保存/恢复状态，永远落在固定页面；
 * - 二级/次级页面：普通入栈（系统返回可回退）。
 */
/** 上一次 Tab 式切换的时刻：转场进行中（窗口内）再切会打断退出转场，
 *  导致被打断的页面定格在退出位（内容偏移/空白），故对 Tab 导航做防抖 */
private var lastTabNavAtMs = 0L

private const val TAB_NAV_DEBOUNCE_MS = 500L

/** @return 是否真正执行了导航（false = Tab 防抖窗口内被忽略） */
private fun navTo(
    navController: NavHostController,
    route: String,
    slots: Set<String>,
    clearStack: Boolean = false,
): Boolean {
    val isTabSwitch = !clearStack && route.substringBefore('?') in slots
    if (isTabSwitch) {
        val now = System.currentTimeMillis()
        if (now - lastTabNavAtMs < TAB_NAV_DEBOUNCE_MS) return false
        lastTabNavAtMs = now
    }
    navController.navigate(route) {
        launchSingleTop = true
        when {
            clearStack -> popUpTo(0)
            // 只有目标真的配置在底栏里、且调用方未要求二级页形态时才走 Tab 式切换（保存/恢复状态），
            // 否则一律入栈为二级页面（顶栏可返回）
            isTabSwitch -> {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                restoreState = true
            }
        }
    }
    return true
}
