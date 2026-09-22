package com.mdot.app.core.navigation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import com.mdot.app.AppViewModel
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.AdaptiveContainer
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.PredictiveBackSpec
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.rememberContentSideInset
import com.mdot.app.core.designsystem.rememberWindowSpec
import com.mdot.app.core.designsystem.component.JiabanBottomBar
import com.mdot.app.core.designsystem.component.BackdropBlurState
import com.mdot.app.core.designsystem.component.RecordCircleButton
import com.mdot.app.core.designsystem.component.RecordPillButton
import com.mdot.app.core.designsystem.component.backdropBlur
import com.mdot.app.core.designsystem.component.backdropBlurSource
import com.mdot.app.core.designsystem.component.rememberBackdropBlurState
import com.mdot.app.core.designsystem.component.TopLevelBar
import com.mdot.app.core.designsystem.component.SheetBackdropLayer
import com.mdot.app.core.designsystem.component.SlotRegistry
import com.mdot.app.feature.calendar.CalendarScreen
import com.mdot.app.feature.export.ExportScreen
import com.mdot.app.feature.home.HomeScreen
import com.mdot.app.feature.onboarding.OnboardingScreen
import com.mdot.app.feature.profile.ProfileScreen
import com.mdot.app.feature.record.RecordSheet
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
 * 一级 Tab「边缘接力」：内层横向滚动（如统计页的多页签 pager）滑到尽头后，页面调用它把
 * 「继续滑」的意图交给整页横滑（切相邻一级 Tab）——两套横滑不再抢手势。
 * [dir]：-1 = 上一个槽位（向右滑），+1 = 下一个槽位（向左滑）；非一级页形态下为空实现。
 */
val LocalPrimaryTabSwipe = compositionLocalOf<(Int) -> Unit> { {} }

/**
 * 页面级「返回优先」登记（预测性返回的让位开关）。
 *
 * 页面在自己需要优先处理返回时置位（目前仅日历页长按多选的「退出多选」），
 * AppRoot 的 `PredictiveBackHandler` 据此禁用——**OnBackPressedDispatcher 后注册者优先，
 * 应用级回调注册在 NavHost 之后会盖住页面内的 BackHandler**（否则多选时按返回会直接把页面 pop 掉）。
 * 页面用 `DisposableEffect(状态) { 置位; onDispose { 复位 } }` 维护。
 */
val LocalPageBackInterception = compositionLocalOf { androidx.compose.runtime.mutableStateOf(false) }

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
    // B6-02：跟手飞出时长接入 motionScheme（原硬编码 tween(220)）
    val flyOutSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

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

        // 边缘接力：本页内的横向滚动滚到尽头后，把续滑意图交回整页横滑
        val primarySwipe: (Int) -> Unit = { dir ->
            if (dir < 0) {
                if (prev != null) goTo(prev, -1)
            } else {
                if (next != null) goTo(next, 1)
            }
        }
        CompositionLocalProvider(LocalPrimaryTabSwipe provides primarySwipe) {
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
                                offset.animateTo(target, flyOutSpec)
                            }
                        }
                    },
                ),
        ) { content() }
        }
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
    // 页面级「返回优先」登记：既经 CompositionLocal 下发给各页（页面在自己需优先处理返回时置位，
    // 如日历长按多选），又供应用级预测性返回判断是否让位——见 [LocalPageBackInterception]
    val pageBackInterception = remember { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWindowSpec provides windowSpec,
        LocalPageBackInterception provides pageBackInterception,
    ) {
        AppRootContent(firstLaunchDone, appVm, fabSideInset, pageBackInterception)
    }
}

@Composable
private fun AppRootContent(
    firstLaunchDone: Boolean,
    appVm: AppViewModel,
    fabSideInset: androidx.compose.ui.unit.Dp,
    pageBackInterception: androidx.compose.runtime.MutableState<Boolean>,
) {
    val navController = rememberNavController()
    val request by appVm.recordRequest.collectAsStateWithLifecycle()
    // 记录弹层可见性状态提升到此处：背景「模糊 + 缩小」层（SheetBackdropLayer）与弹层自身
    // 共享同一过渡状态，进出场严格同步（弹层内部负责置 targetState）
    val recordSheetVisible = remember { MutableTransitionState(false) }
    // 底栏毛玻璃：导航宿主为「源」（录制内容层），底栏为「效果方」（模糊身后内容）
    val backdropBlurState = rememberBackdropBlurState()
    // 预测性返回（PredictiveBackHandler）：手势进度 0→1 映射为「当前页小幅右移」（不缩放），
    // 主体留在屏上、侧边只露极窄一条；确认松手继续走右滑出屏的 pop 转场，中途取消弹簧回弹。
    // 进度只在 graphicsLayer（绘制期）读取——组合期读会逐帧重组整棵树（同底栏 progress 的坑）
    val backProgress = remember { Animatable(0f) }
    val backCancelSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // 松手衔接 spec：与 NavHost 的 popExitTransition（`tween(Duration.slow)` 右滑出屏）**同长**——
    // 位移随转场一起衰减到 0，两段动画衔接成一段（页面级 NavHost 转场不受硬规则 7 约束）
    val backHandoffSpec = tween<Float>(Duration.slow)
    // 回弹/衔接动画跑在宿主作用域（而非 handler 协程）：弹栈会重建回调、取消时 handler 协程也可能被
    // 取消，动画放进去会被中途掐断（位移停在半路，页面歪着不复位）
    val backAnimScope = rememberCoroutineScope()
    // 预测性进度事件仅 API33+ 提供；低版本只会一次性给 progress=1，会造成「跳到满位移再 pop」的闪动
    val predictiveBackSupported =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val bottomBar by appVm.bottomBar.collectAsStateWithLifecycle()
    val bottomBarIconOnly by appVm.bottomBarIconOnly.collectAsStateWithLifecycle()
    val bottomBarSideAction by appVm.bottomBarSideAction.collectAsStateWithLifecycle()
    val bottomBarFixedWidth by appVm.bottomBarFixedWidth.collectAsStateWithLifecycle()
    val bottomBarFrosted by appVm.bottomBarFrosted.collectAsStateWithLifecycle()
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
        // 背景层：弹层出现时整屏内容模糊 + 缩小成圆角卡片（弹层自身在最上层、不受影响）
        SheetBackdropLayer(visible = recordSheetVisible.targetState) {
            // 转场动画（03 文档 §7）：底栏 Tab=淡入淡出 200ms；层级跳转=水平滑入 300ms（返回反向）
            val isTabEntry: (androidx.navigation.NavBackStackEntry) -> Boolean =
                { entry -> entry.destination.route?.substringBefore('?') in slots }
            // 预测性返回：页面内容包一层（transform 必须在**独立节点**上）—— 与录制 draw 同节点时，
            // 图层属性变更会连带重跑该节点的显示列表 → 每帧全屏重录（实测 60 次/秒，帧 p90 24ms 卡顿）；
            // 放独立父节点后，位移变更只重新合成图层、不重建显示列表，录制次数降为 0 ✓
            // （顶栏/底栏/FAB 是更外层的兄弟节点，不跟着动；效果关闭时不挂 graphicsLayer，避免多余图层）
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (PREDICTIVE_BACK_ENABLED) {
                            Modifier.graphicsLayer {
                                translationX = PredictiveBackSpec.translationFor(backProgress.value).toPx()
                            }
                        } else Modifier,
                    ),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (firstLaunchDone) Routes.HOME else Routes.ONBOARDING,
                    modifier = Modifier
                        .fillMaxSize()
                        .backdropBlurSource(backdropBlurState),
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
                        if (isTabEntry(targetState))
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
                                onOpenStats = { navTo(navController, Routes.statsDetail(0), slots) },
                                onOpenDetail = { navTo(navController, Routes.statsDetail(if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) 1 else 2), slots) },
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
                            )
                        }
                    }
                    composable(Routes.EXPORT) {
                        SwipeTabHost(orderedSlots, currentBase, selfRoute = Routes.EXPORT, onNavigate = { navTo(navController, it, slots) }) {
                            AdaptiveContainer {
                                ExportScreen(
                                    canBack = !inBar("export"),
                                    onBack = { navController.popBackStack() },
                                    onViewRecords = { navTo(navController, Routes.statsDetail(if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) 1 else 2), slots) },
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
                    composable(Routes.SYSTEM) {
                        AdaptiveContainer {
                            // 顶栏齿轮：当前工时制度的设定多页签页（原工时设置入口列表页移除）
                            SystemSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenProject = { id -> navTo(navController, Routes.siteProjectEdit(id), slots) },
                            )
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
                    composable(Routes.SITE_SETTLEMENT) {
                        AdaptiveContainer {
                            SiteSettlementScreen(onBack = { navController.popBackStack() })
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
                        AdaptiveContainer {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
                                onOpen = { route -> navTo(navController, route, slots) },
                            )
                        }
                    }
                }
            }

            // ---- 预测性返回（v0.6.21/UIfix）----
            // ⚠️ 必须注册在 NavHost **之后**：OnBackPressedDispatcher 后注册者优先，而 NavHost 自带的返回
            // 回调在 NavHost 调用期间就已注册——本回调若先注册则永远收不到手势进度。
            // 同理会盖住页面内的 BackHandler（如日历长按多选），故与页面级「返回优先」登记联动让位。
            // 仅 API33+ 注册：低版本无预测性进度事件，返回交回 NavController 默认回调即可。
            // ⚠️ 只在一级页之外的**二级页**启用位移：一级（底栏）页的返回转场是「按 Tab 方向滑出」，
            // 与位移方向相反——页面会先右移 36dp 再反向滑走，看起来就是「卡一下」；
            // 且一级页手势期间露出的只是背景（无信息量）。一级页交给 NavController 默认回调。
            PredictiveBackHandler(
                enabled = PREDICTIVE_BACK_ENABLED &&
                    navController.previousBackStackEntry != null &&
                    predictiveBackSupported &&
                    !showBar &&
                    !pageBackInterception.value,
            ) { progressFlow ->
                try {
                    // 是否收到过「渐进」进度：非预测性返回（3 键导航 / 无障碍返回 / 硬件返回键）
                    // 只会给一次 progress=1f——此时不应位移，否则页面会「凭空跳 36dp 再返回」。
                    var sawPartialProgress = false
                    // 手势起始时刻：位移前 ~60ms 不出，再在 120ms 内斜坡到满——
                    // 快速甩手「只想直接返回」时进度会在几十毫秒内冲到 1，若照搬就会先跳 36dp 再返回（卡一下）。
                    var gestureStartAt = 0L
                    // 手势进行中：逐事件直接跟随（snapTo 不做动画，跟手）
                    progressFlow.collect { event ->
                        val p = event.progress
                        if (p < 1f) sawPartialProgress = true
                        if (sawPartialProgress) {
                            if (gestureStartAt == 0L) gestureStartAt = android.os.SystemClock.uptimeMillis()
                            val elapsed = android.os.SystemClock.uptimeMillis() - gestureStartAt
                            val ramp = ((elapsed - 60f) / 120f).coerceIn(0f, 1f)
                            backProgress.snapTo(p * ramp)
                        }
                    }
                    // 滑到底松手（手势确认）：**不得瞬间复位**——瞬间复位会先「移回全屏」再播放返回动画，
                    // 视觉上闪一下。此处先 pop（右滑出屏转场接上手势终态），位移再随转场同长衰减到 0。
                    navController.popBackStack()
                    backAnimScope.launch { backProgress.animateTo(0f, backHandoffSpec) }
                } catch (e: CancellationException) {
                    // 中途取消：平滑回弹（motionScheme 空间 spec，禁硬编码，硬规则 7）
                    backAnimScope.launch { backProgress.animateTo(0f, backCancelSpec) }
                    throw e
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
                    // 「按钮右置」布局下与底栏共用同一套毛玻璃源（开关关/API<31 时为 null → 实心主色）
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
                FloatingActionButton(
                    onClick = {
                        // 工地模式：直接打开记工页（不走加班/请假弹窗）
                        if (workSystem == com.mdot.app.domain.model.WorkSystem.SITE) {
                            navTo(navController, Routes.SITE_RECORD, slots)
                        } else {
                            appVm.recordSheetController.open(calSelDate)
                        }
                    },
                    shape = RoundedCornerShape(Radius.textField),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.ic_ms_add), contentDescription = stringResource(R.string.nav_fab_cd))
                }
            }
        }

        request?.let { req ->
            RecordSheet(
                request = req,
                visibleState = recordSheetVisible,
                onDismiss = { appVm.recordSheetController.dismiss() },
            )
        }
    }
}

/**
 * 预测性返回手势效果总开关。
 *
 * ⚠️ 2026-09-20 暂时关闭（用户决定「先关掉、留待之后修改」）：手势过程的
 * 「位移 + 松手衔接」体感未达预期（快速甩手时仍有顿感、与 pop 转场的方向/时长难以完全对齐），
 * 实现与坑路已完整保留在下方与 docs/11 046，重新启用只需把本常量置 true。
 * 关闭后返回交回 NavController 默认回调（与加此特性前一致）。
 */
private const val PREDICTIVE_BACK_ENABLED = false

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
