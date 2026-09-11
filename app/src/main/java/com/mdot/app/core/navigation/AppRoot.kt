package com.mdot.app.core.navigation

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.mdot.app.core.designsystem.component.pressScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.mdot.app.core.designsystem.AdaptiveContainer
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.rememberContentSideInset
import com.mdot.app.core.designsystem.rememberWindowSpec
import com.mdot.app.core.designsystem.component.JiabanBottomBar
import com.mdot.app.core.designsystem.component.TopLevelBar
import com.mdot.app.core.designsystem.component.SlotRegistry
import com.mdot.app.feature.calendar.CalendarScreen
import com.mdot.app.feature.export.ExportScreen
import com.mdot.app.feature.home.HomeScreen
import com.mdot.app.feature.onboarding.OnboardingScreen
import com.mdot.app.feature.payroll.PayrollScreen
import com.mdot.app.feature.profile.ProfileScreen
import com.mdot.app.feature.record.RecordSheet
import com.mdot.app.feature.settings.AboutScreen
import com.mdot.app.feature.settings.AppearanceScreen
import com.mdot.app.feature.settings.CycleScreen
import com.mdot.app.feature.settings.DataSourceScreen
import com.mdot.app.feature.settings.SettingsScreen
import com.mdot.app.feature.settings.ShiftsScreen
import com.mdot.app.feature.settings.SystemScreen
import com.mdot.app.feature.settings.SystemSwitchScreen
import com.mdot.app.feature.settings.WorkdaysScreen
import com.mdot.app.feature.comp.CompBalanceScreen
import com.mdot.app.feature.stats.StatsScreen
import com.mdot.app.feature.sync.SyncScreen
import com.mdot.app.feature.sync.SyncStorageScreen
import com.mdot.app.feature.settings.BottomBarScreen
import com.mdot.app.feature.settings.HomeCardsScreen
import com.mdot.app.feature.site.SiteProjectsScreen
import com.mdot.app.feature.site.SiteProjectEditScreen
import com.mdot.app.feature.site.SiteSettlementScreen
import com.mdot.app.feature.site.SiteRecordScreen

/** 内容底部避让底栏（03 文档 §4.1：滚动内容从底栏下方穿过）；
 *  无底栏形态（showBottomBar=false）只避让系统导航栏，避免底部大片空隙 */
fun Modifier.contentBottomPadding(showBottomBar: Boolean = true): Modifier = composed {
    if (showBottomBar) {
        padding(bottom = bottomBarContentPaddingValues().calculateBottomPadding())
    } else {
        navigationBarsPadding()
    }
}

/** 供 LazyColumn contentPadding 使用的底部避让值 */
@Composable
fun bottomBarContentPaddingValues(): androidx.compose.foundation.layout.PaddingValues {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return androidx.compose.foundation.layout.PaddingValues(
        bottom = BottomBarSpec.height + BottomBarSpec.bottomMargin + 8.dp + navBar,
    )
}

/** 按底栏形态返回内容底部避让值（无底栏时只避让系统导航栏） */
@Composable
fun contentPaddingValues(showBottomBar: Boolean): androidx.compose.foundation.layout.PaddingValues {
    return if (showBottomBar) {
        bottomBarContentPaddingValues()
    } else {
        androidx.compose.foundation.layout.PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
    }
}

/** 最近一次底栏 Tab 切换方向：+1=新页从右入（前进），-1=新页从左入（后退）。点击 Tab 默认 +1 */
private val lastTabSwipeDir = androidx.compose.runtime.mutableStateOf(1)

/**
 * 一级 Tab 页滑动切换：跟手平移 + 松手后按方向滑出并切换到相邻底栏槽位页。
 * 页面内部自身的横向手势（如统计维度的横向 chip 行）优先级更高、不受影响。
 *
 * selfRoute：本宿主承载的路由；当快速滑动打断转场、页面在滑出动画途中被重新导航回来时，
 * 旧实例的 offset 可能停在 ±屏宽（内容平移出屏 → 空白页），因此「重新成为当前页」时
 * 必须停掉残留动画并把 offset 归零。
 */
@Composable
private fun SwipeTabHost(
    orderedSlots: List<String>,
    currentBase: String?,
    selfRoute: String,
    onNavigate: (String) -> Boolean,
    content: @Composable () -> Unit,
) {
    val idx = orderedSlots.indexOf(currentBase)
    val isActive = currentBase == selfRoute.substringBefore('?')
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(0) }

    LaunchedEffect(isActive) {
        if (isActive) {
            offset.stop()
            offset.snapTo(0f)
        }
    }

    if (idx >= 0) {
        val prev = orderedSlots.getOrNull(idx - 1)
        val next = orderedSlots.getOrNull(idx + 1)
        // 返回 false = 导航被防抖忽略（转场进行中），此时页面只回弹不飞出
        val goTo = { route: String, dir: Int ->
            lastTabSwipeDir.value = dir
            onNavigate(route)
        }

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { pageWidth = it.width }
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo(offset.value + delta) }
                    },
                    onDragStopped = {
                        val w = pageWidth.coerceAtLeast(1)
                        val vel = it
                        // 低阈值：位移超过 12% 屏宽或甩动 > 700px/s 即切换
                        val pass = when {
                            offset.value > w * 0.12f -> vel >= -400f
                            offset.value < -w * 0.12f -> vel <= 400f
                            offset.value > 0f && vel > 700f -> true
                            offset.value < 0f && vel < -700f -> true
                            else -> false
                        }
                        val right = offset.value > 0f || (vel > 0f && offset.value > -w * 0.05f)
                        val target = when {
                            !pass -> 0f // 回弹
                            right && prev != null -> if (goTo(prev, -1)) w.toFloat() else 0f
                            !right && next != null -> if (goTo(next, 1)) -w.toFloat() else 0f
                            else -> 0f
                        }
                        scope.launch {
                            if (target == 0f) {
                                offset.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ))
                            } else {
                                // 跟手飞出交给 NavHost 转场接力；防抖保证转场不会被
                                // 新导航打断（打断会把页面定格在退出位 → 空白/偏移）
                                offset.animateTo(target, tween(220))
                            }
                        }
                    },
                ),
        ) { content() }
    } else {
        // 非当前页（正在滑出/转场中）：保留残留位移，避免内容在淡出途中跳回原位
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset.value }) { content() }
    }
}


@Composable
fun AppRoot(firstLaunchDone: Boolean, appVm: AppViewModel) {
    // 响应式布局（docs 03 §3.2）：窗口档位全局下发（页面读 LocalWindowSpec 决定单列/双栏）；
    // 二级页统一经 AdaptiveContainer 限宽居中（下方逐页包裹）；固定一级顶栏/底栏/
    // 记录弹层/FAB 等悬浮层各自跟随内容宽度对齐
    val windowSpec = rememberWindowSpec()
    val fabSideInset = rememberContentSideInset()
    androidx.compose.runtime.CompositionLocalProvider(LocalWindowSpec provides windowSpec) {
        AppRootContent(firstLaunchDone, appVm, fabSideInset)
    }
}

@Composable
private fun AppRootContent(
    firstLaunchDone: Boolean,
    appVm: AppViewModel,
    fabSideInset: androidx.compose.ui.unit.Dp,
) {
    val navController = rememberNavController()
    val request by appVm.recordRequest.collectAsStateWithLifecycle()
    val bottomBar by appVm.bottomBar.collectAsStateWithLifecycle()
    val bottomBarIconOnly by appVm.bottomBarIconOnly.collectAsStateWithLifecycle()
    val workSystem by appVm.workSystem.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 去掉 ?month 等查询参数后比对
    val currentBase = currentRoute?.substringBefore('?')
    // 底栏可见性 = 当前页面确实配置在用户的底栏槽位里；
    // 池里但未配置的页面（工资/导出/同步）走无底栏形态，顶栏带返回
    val slots = bottomBar.slots.toSet()
    val orderedSlots = bottomBar.slots
    val showBar = currentBase != null && currentBase in slots
    // 池页面是否以"底栏一级"形态出现
    val inBar: (String) -> Boolean = { it.substringBefore('?') in slots }

    Box(Modifier.fillMaxSize()) {
        // 转场动画（03 文档 §7）：底栏 Tab=淡入淡出 200ms；层级跳转=水平滑入 300ms（返回反向）
        val isTabEntry: (androidx.navigation.NavBackStackEntry) -> Boolean =
            { entry -> entry.destination.route?.substringBefore('?') in slots }
        NavHost(
            navController = navController,
            startDestination = if (firstLaunchDone) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (isTabEntry(targetState) && isTabEntry(initialState))
                    slideInHorizontally(
                        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                    ) { it * lastTabSwipeDir.value } +
                        fadeIn(tween(Duration.normal))
                else
                    slideInHorizontally(tween(Duration.slow)) { it } +
                        fadeIn(tween(Duration.slow)) +
                        scaleIn(tween(Duration.slow), initialScale = 0.96f)
            },
            exitTransition = {
                if (isTabEntry(targetState) && isTabEntry(initialState) || isTabEntry(targetState))
                    slideOutHorizontally(
                        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
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
                            spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
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
                        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
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
                        onOpenStats = { navTo(navController, Routes.STATS, slots) },
                        onOpenDetail = { navTo(navController, Routes.DETAIL, slots) },
                        onOpenRecord = {
                            if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                                navTo(navController, Routes.SITE_RECORD, slots)
                            } else {
                                appVm.recordSheetController.open(java.time.LocalDate.now())
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
            composable(Routes.STATS) {
                SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.STATS, onNavigate = { navTo(navController, it, slots) }) {
                    StatsScreen(
                        canBack = !inBar("stats"),
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { navTo(navController, Routes.DETAIL, slots) },
                    )
                }
            }
            composable(Routes.COMP) {
                AdaptiveContainer {
                    CompBalanceScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.PAYROLL) {
                SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.PAYROLL, onNavigate = { navTo(navController, it, slots) }) {
                    AdaptiveContainer {
                        PayrollScreen(
                            canBack = !inBar("payroll"),
                            onBack = { navController.popBackStack() },
                            onOpenSiteProjects = { navTo(navController, Routes.SITE_PROJECTS, slots) },
                            onOpenSiteSettlement = { navTo(navController, Routes.SITE_SETTLEMENT, slots) },
                        )
                    }
                }
            }
            composable(Routes.EXPORT) {
                SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.EXPORT, onNavigate = { navTo(navController, it, slots) }) {
                    AdaptiveContainer {
                        ExportScreen(canBack = !inBar("export"), onBack = { navController.popBackStack() })
                    }
                }
            }
            composable(Routes.SYNC) {
                SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.SYNC, onNavigate = { navTo(navController, it, slots) }) {
                    AdaptiveContainer {
                        SyncScreen(
                            canBack = !inBar("sync"),
                            onBack = { navController.popBackStack() },
                            onOpenStorage = { navTo(navController, Routes.SYNC_STORAGE, slots) },
                        )
                    }
                }
            }
            composable(Routes.SYNC_STORAGE) {
                AdaptiveContainer {
                    SyncStorageScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.SETTINGS) {
                // EXPANDED 双列分组页（设置中心自管双栏形态），不走统一限宽
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navTo(navController, route, slots) },
                )
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
            composable(Routes.SYSTEM) {
                AdaptiveContainer {
                    SystemScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { route -> navTo(navController, route, slots) },
                        onOpenPayroll = { navTo(navController, Routes.PAYROLL, slots) },
                    )
                }
            }
            composable(Routes.SYSTEM_SWITCH) {
                AdaptiveContainer {
                    SystemSwitchScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.CYCLE) {
                AdaptiveContainer { CycleScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.WORKDAYS) {
                AdaptiveContainer { WorkdaysScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.SHIFTS) {
                AdaptiveContainer { ShiftsScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.APPEARANCE) {
                AdaptiveContainer {
                    AppearanceScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBottomBar = { navTo(navController, Routes.BOTTOM_BAR, slots) },
                        onOpenHomeCards = { navTo(navController, Routes.HOME_CARDS, slots) },
                    )
                }
            }
            composable(Routes.BOTTOM_BAR) {
                AdaptiveContainer { BottomBarScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.HOME_CARDS) {
                AdaptiveContainer { HomeCardsScreen(onBack = { navController.popBackStack() }) }
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
            composable(Routes.SITE_SETTLEMENT) {
                AdaptiveContainer {
                    SiteSettlementScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.DETAIL) {
                AdaptiveContainer {
                    com.mdot.app.feature.detail.DetailScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.SITE_RECORD) {
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
                    )
                }
            }
            composable(Routes.DATASOURCE) {
                AdaptiveContainer { DataSourceScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.ABOUT) {
                AdaptiveContainer { AboutScreen(onBack = { navController.popBackStack() }) }
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
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                TopLevelBar(
                    workSystem = workSystem,
                    onOpenWorkSystem = { navTo(navController, Routes.SYSTEM, slots) },
                    onOpenSettings = { navTo(navController, Routes.SETTINGS, slots) },
                )
            }
        }

        val calSelDate by appVm.recordSheetController.calendarSelectedDate.collectAsStateWithLifecycle()
        // 中央按钮入场动画只播一次：底栏隐藏→再现会销毁/重建该按钮，
        // flag 提升到 AppRootContent（跨导航存活）避免每次重播弹簧弹入
        var recordPillEntered by remember { mutableStateOf(false) }
        JiabanBottomBar(
            slots = bottomBar.slots.mapNotNull { SlotRegistry.resolve(it) },
            selectedRoute = currentBase,
            visible = showBar,
            iconOnly = bottomBarIconOnly,
            centerAction = {
                RecordPillButton(
                    onRecord = {
                        when {
                            // 日历一级页：承担原悬浮 FAB 功能——工地模式直达记工页，其余打开选中日期的记录
                            currentBase == "calendar" && workSystem == com.mdot.app.domain.model.WorkSystem.SITE ->
                                navTo(navController, Routes.SITE_RECORD, slots)
                            currentBase == "calendar" -> appVm.recordSheetController.open(calSelDate)
                            workSystem == com.mdot.app.domain.model.WorkSystem.SITE ->
                                navTo(navController, Routes.SITE_RECORD, slots)
                            else -> appVm.recordSheetController.open(java.time.LocalDate.now())
                        }
                    },
                    playEntrance = !recordPillEntered,
                    onEntranceDone = { recordPillEntered = true },
                )
            },
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
            FloatingActionButton(
                onClick = {
                    // 工地模式：直接打开记工页（不走加班/请假弹窗）
                    if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                        navTo(navController, Routes.SITE_RECORD, slots)
                    } else {
                        appVm.recordSheetController.open(calSelDate)
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(painterResource(R.drawable.ic_ms_add), contentDescription = stringResource(R.string.nav_fab_cd))
            }
        }

        request?.let { req ->
            RecordSheet(
                request = req,
                onDismiss = { appVm.recordSheetController.dismiss() },
            )
        }
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
            // 只有目标真的配置在底栏里才走 Tab 式切换（保存/恢复状态），
            // 否则一律入栈为二级页面（顶栏可返回）
            route.substringBefore('?') in slots -> {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                restoreState = true
            }
        }
    }
    return true
}

/**
 * 底栏中央主操作按钮（记加班/记工）：主色圆形（M3 FAB 形态），M3E 动效——
 * 入场弹簧弹入（slowSpatialSpec）+ 按压 shape morph（圆形→超圆角方，fastSpatialSpec）+ 阴影贴合（fastEffectsSpec）。
 * spec 须先在 composable 上下文取出再传入动画 API（03 文档规则 7）。
 */
@Composable
private fun RecordPillButton(
    onRecord: () -> Unit,
    /** 仅首次出现播放弹簧弹入；底栏隐藏→再现（组合销毁重建）时不重播 */
    playEntrance: Boolean,
    onEntranceDone: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val entrance = remember { Animatable(if (playEntrance) 0f else 1f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (playEntrance) {
            entrance.animateTo(1f, entranceSpec)
            onEntranceDone()
        }
    }
    val elevSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val elevation by animateFloatAsState(
        targetValue = if (pressed) 1f else 3f,
        animationSpec = elevSpec,
        label = "recordPillElevation",
    )
    // 方圆形 20dp，与底栏配置页预览完全一致（按压反馈由 pressScale 缩放 + 阴影贴合承担，无形状 morph）
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = elevation.dp,
        modifier = Modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e
                val scale = 0.8f + 0.2f * e
                scaleX = scale
                scaleY = scale
                translationY = (1f - e) * 24.dp.toPx()
            }
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onRecord),
    ) {
        Box(
            Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_more_time), null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
