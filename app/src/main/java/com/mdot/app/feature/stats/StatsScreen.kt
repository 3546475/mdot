package com.mdot.app.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.WeekBarCard
import com.mdot.app.core.designsystem.component.chartHintText
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.modeValueText
import com.mdot.app.core.designsystem.component.MonthStepper
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.primaryTabEdgeRelay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.feature.detail.DetailPane
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 统计页（03 文档 §5.4 线框；v0.6 全制度统一布局：维度行→汇总→热点图→本周柱状→饼图；
 * 顶栏页签 统计/记月/明细——明细内容并入为末位页签，独立明细页保留给首页收入卡入口） */
@Composable
fun StatsScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    /** 初始页签（首页收入卡直达明细：工地=1、非工地=2；默认 0=统计） */
    initialTab: Int = 0,
    vm: StatsViewModel = hiltViewModel(),
    /** 记月「个人所得税」行 → 个税估算页（导航由 AppRoot 注入） */
    onOpenTax: () -> Unit = {},
    /** 图表长按「记那一天」（21 文档：按制度分流——工地→记工页带日期、其他→记录弹层），AppRoot 注入 */
    onChartDay: (LocalDate) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pieMode by vm.pieMode.collectAsStateWithLifecycle()
    val selectedBar by vm.selectedBar.collectAsStateWithLifecycle()
    // 记月=月度工资单（基本项目卡：基本工资/加班工资/调休 + 「同步本月考勤」回填），仅底薪制有意义——
    // 标准工时/综合工时显示；小时工（纯时薪，引擎 baseIncludedCents=0 无底薪、compBalanceMinutes=0 无调休，
    // 基本项目卡 2/3 行恒空值）与工地记工（无 PayrollCalculator 引擎值）隐藏——两制度为「统计/明细」两页签
    val monthTab = state.workSystem == WorkSystem.STANDARD || state.workSystem == WorkSystem.COMPREHENSIVE
    // 首帧 workSystem 尚未加载（默认 STANDARD=3 页签），按初始页签夹取防越界
    val pagerState = rememberPagerState(initialPage = initialTab.coerceAtMost(2), pageCount = { if (monthTab) 3 else 2 })

    // 摘要条/空态点「明细」时切页签用（pager 由本页持有）
    val scope = rememberCoroutineScope()

    // 区间弹窗状态提升到这一层（**全页一份**）：两页（统计/明细）点胶囊开的都是它，改的也是同一份状态。
    // ⚠️ 别下放回页内：头部住在 LazyColumn 的 item 里，弹窗状态留在 item 内会被列表回收带走。
    var showRangeDialog by remember { mutableStateOf(false) }
    var showSiteRangeDialog by remember { mutableStateOf(false) }

    // 区间头（统计/明细两页**同一个 lambda**：同一份状态 + 同一套交互）——两页因此不可能显示不同区间
    val rangeHeader: @Composable () -> Unit = {
        StatsRangeHeader(
            vm = vm,
            state = state,
            onOpenPicker = { showRangeDialog = true },
            onOpenSiteDialog = { showSiteRangeDialog = true },
        )
    }

    // 明细页签的宿主：区间与统计页同源（v0.7.9.0 用户拍板「明细也要能切考勤周期/自然月」）。
    // 取数区间直接用统计页算好的 [StatsUiState.rangeFrom]/[StatsUiState.rangeTo]。
    val detailHost = com.mdot.app.feature.detail.DetailPaneHost(
        header = rangeHeader,
        range = state.rangeFrom?.let { f ->
            state.rangeTo?.let { t -> CycleCalculator.Period(f, t) }
        },
        rangeKind = state.dimension.rangeKind,
    )

    Column(
        Modifier
            .fillMaxSize()
            // 一级 Tab 形态下的「边缘接力」：内层 pager 滑到第一/最后一页还在拖时，
            // 把余量交给整页横滑（LocalPrimaryTabSwipe）切相邻一级 Tab
            .primaryTabEdgeRelay(pagerState),
    ) {
        if (canBack) {
            // 二级页形态（从首页收入卡/工资页等入栈）：本页自带顶栏 + 返回
            JiabanTopBar(
                title = null,
                titleContent = { StatsTabBar(pagerState, monthTab) },
                showBack = true,
                onBack = onBack,
            )
        } else {
            // 一级 Tab 形态：AppRoot 的固定顶栏（工时制度 + 设置齿轮）压在本页之上，
            // 故页签条下移一行（避让状态栏 + 固定顶栏高度），不再被固定顶栏遮住（用户所选方案）
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Spacer(Modifier.height(statusBar + TopBarHeight))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                StatsTabBar(pagerState, monthTab)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 不预组合相邻页（记月/明细体量都不小）：进入统计页只组合当前页 → 减少切页卡顿；
            // 代价是点页签切换时目标页在动画中才组合（体感好于「一进场就卡」）
            beyondViewportPageCount = 0,
        ) { page ->
            when (page) {
                0 -> StatsContent(
                    vm = vm,
                    state = state,
                    pieMode = pieMode,
                    selectedBar = selectedBar,
                    showBottomBar = !canBack,
                    onChartDay = onChartDay,
                    header = rangeHeader,
                )
                1 -> if (monthTab) {
                    // 摘要条/空态点「明细」：切到末位页签（pager 由本页持有）
                    PayMonthContent(
                        onOpenDetail = { scope.launch { pagerState.animateScrollToPage(2) } },
                        onOpenTax = onOpenTax,
                        showBottomBar = !canBack,
                    )
                } else {
                    DetailPane(showBottomBar = !canBack, host = detailHost)
                }
                else -> DetailPane(showBottomBar = !canBack, host = detailHost)
            }
        }
    }

    // 区间弹窗（全页一份，pager 之外）：统计/明细两页点胶囊都开它，改的都是同一份区间状态
    StatsRangeDialogs(
        vm = vm,
        state = state,
        showRangeDialog = showRangeDialog,
        showSiteRangeDialog = showSiteRangeDialog,
        onDismissRange = { showRangeDialog = false },
        onDismissSite = { showSiteRangeDialog = false },
    )
}

/** 统计页第 0 页：原统计内容（维度行→汇总→热点图→柱状→饼图） */
@Composable
private fun StatsContent(
    vm: StatsViewModel,
    state: StatsUiState,
    pieMode: PieMode,
    selectedBar: String?,
    showBottomBar: Boolean,
    onChartDay: (LocalDate) -> Unit,
    /** 区间头（统计/明细两页共用的那一个 lambda，由 StatsScreen 传入） */
    header: @Composable () -> Unit,
) {
    // 方案 C：区间胶囊 → 弹窗（选维度 / 跳月跳年 / 框自定义）；工地则是指挥口径弹窗。
    // 两个弹窗的开关状态与渲染都在共用组件 [StatsRangeHeader] 里——明细页签也渲染它。
    LazyColumn(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = AdaptiveSpecs.contentMaxWidth)
            .padding(horizontal = Spacing.page),
        contentPadding = com.mdot.app.core.navigation.contentPaddingValues(showBottomBar = showBottomBar),
    ) {
        // 维度行（21 文档 B2）：工地=与明细页共用的三药丸 SiteRangeChips（同功能同样式）；
        // 非工地=方案 C——不再常驻四 chip，改由「区间胶囊 → 弹窗选维度」承担
        val isSite = state.workSystem == WorkSystem.SITE
        item {
            // 区间头（v0.7.9.0 起**统计页与明细页签共用同一个 lambda**）——见 StatsScreen 里的 rangeHeader。
            // 顶部不加 Spacer：宿主页签条（SegmentBar 撑满 64dp 顶栏）下方已有天然留白。
            header()
            // 下方留白补到与上方（宿主页签条链路给的 126px）相等：三页统一用同一个值，
            // 改这里请同步改记月页/明细页。实测对齐目标 = 42dp。
            Spacer(Modifier.height(StatsRangeSpacing))
        }

        val hasData = if (isSite) {
            val s = state.siteSummary
            s != null && (s.daysWork > 0 || s.piecePayCents > 0L || s.advanceTotalCents > 0L)
        } else {
            val out = state.output
            out != null && out.breakdowns.isNotEmpty()
        }

        if (!hasData) {
            item {
                EmptyState(
                    icon = painterResource(R.drawable.ic_ms_bar_chart),
                    title = stringResource(R.string.stats_empty_title),
                    hint = if (isSite) stringResource(R.string.stats_site_empty_hint) else when (state.workSystem) {
                        WorkSystem.HOURLY -> stringResource(R.string.stats_empty_hint_hourly)
                        WorkSystem.COMPREHENSIVE -> stringResource(R.string.stats_empty_hint_comp)
                        else -> stringResource(R.string.stats_empty_hint)
                    },
                    actionText = stringResource(R.string.stats_empty_action),
                    onAction = { onChartDay(LocalDate.now()) },
                )
            }
        } else {
            // ---- 汇总卡（按制度取数） ----
            item {
                SummaryCard(state)
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 本周柱状卡（共享组件，与首页同款） ----
            item {
                WeekBarCard(
                    bars = state.weekBars,
                    valueText = { modeValueText(state.workSystem, it) },
                    selectedLabel = selectedBar,
                    onSelect = vm::selectBar,
                    onDayLongPress = { onChartDay(it) },
                    hintValueText = chartHintText(state.workSystem),
                )
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 柱状图（铺满所选区间：≤31 天每日柱，超长按周聚合，21 文档 B3） ----
            val rangeDays = state.rangeFrom?.let { f -> state.rangeTo?.let { t -> (t.toEpochDay() - f.toEpochDay()).toInt() + 1 } } ?: 0
            if (rangeDays >= 2) {
                item {
                    MonthBarCard(state, { onChartDay(it) }, chartHintText(state.workSystem))
                    Spacer(Modifier.height(Spacing.m))
                }
            }

            // ---- 热点图卡（固定六个月：本月向前推五个月，如九月 = 四~九月） ----
            item {
                SectionCard {
                    WorkHeatmap(
                        values = state.heatValues,
                        start = state.heatStart,
                        end = state.heatEnd,
                        onDayLongPress = { onChartDay(it) },
                        hintValueText = chartHintText(state.workSystem),
                    )
                }
                Spacer(Modifier.height(Spacing.m))
            }

            // ---- 饼图卡 ----
            item {
                PieCard(state, pieMode, vm::onPieMode)
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * 区间头（**统计页与明细页签共用**，v0.7.9.0）：非工地 = `MonthStepper`；工地 = `RangePill`。
 * 点胶囊开的弹窗**不在这里**——两个弹窗的状态提升到 [StatsScreen] 那一层（见 [StatsRangeDialogs]）：
 * ① 两页各组合一份头部实例，但弹窗只能有一份，否则会叠出两个；② 头部住在 LazyColumn 的 item 里，
 * 弹窗状态若留在 item 内会被列表回收带走（页面滚动即可复现）。
 */
@Composable
private fun StatsRangeHeader(
    vm: StatsViewModel,
    state: StatsUiState,
    onOpenPicker: () -> Unit,
    onOpenSiteDialog: () -> Unit,
) {
    if (state.workSystem == WorkSystem.SITE) {
        // 工地：**胶囊 = 唯一入口**，口径切换与自定义起止都收进弹窗
        // （与明细页工地分支同款，共用 RangePill + SiteRangeDialog）
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.mdot.app.feature.detail.RangePill(
                label = state.rangeLabel,
                onClick = onOpenSiteDialog,
            )
        }
    } else {
        // 方案 C：胶囊 = 唯一入口（点开弹窗选维度/跳月/框自定义）；
        // 工地不支持步进（区间随结算滚动），故只在非工地给 ‹ ›
        MonthStepper(
            label = statsRangeLabel(state.dimension.rangeKind, state.picker?.current),
            onPrev = vm::stepBack,
            onNext = vm::stepForward,
            // 胶囊**始终可点**：弹窗里既能换维度、也能改自定义起止。
            // ⚠️ 别按维度把它置为不可点（曾让「自定义」维度下胶囊变成死路——
            // 页面已无常驻 chip 行，用户就再也换不回周期/月/年了，只能切页签绕）
            onOpenPicker = onOpenPicker,
            nextEnabled = state.rangeOffset < 0 && state.maxForwardSteps > 0,
            onBackToCurrent = if (state.rangeOffset < 0) vm::backToCurrentRange else null,
            backToCurrentLabel = statsBackToCurrentLabel(state.dimension.rangeKind),
            prevContentDescription = statsStepDescription(forward = false, kind = state.dimension.rangeKind),
            nextContentDescription = statsStepDescription(forward = true, kind = state.dimension.rangeKind),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 区间弹窗（**全页一份**，v0.7.9.0）：非工地 = [StatsRangeDialog]（维度 + 跳月跳年 + 自定义起止），
 * 工地 = [com.mdot.app.feature.detail.SiteRangeDialog]（口径三选一 + 自定义起止）。
 * 由 [StatsScreen] 在 pager **之外**渲染一次：两页（统计/明细）点胶囊开的都是它，改的也是同一份状态。
 */
@Composable
private fun StatsRangeDialogs(
    vm: StatsViewModel,
    state: StatsUiState,
    showRangeDialog: Boolean,
    showSiteRangeDialog: Boolean,
    onDismissRange: () -> Unit,
    onDismissSite: () -> Unit,
) {
    // 方案 C 的区间选择弹窗（非工地）：维度 + 跳月/跳年 + 自定义起止都在它里面
    if (showRangeDialog) {
        StatsRangeDialog(
            dimension = state.dimension,
            picker = state.picker,
            customFrom = state.customFrom,
            customTo = state.customTo,
            onDimension = vm::onDimension,
            onPickMonth = vm::onPickMonth,
            onPickYear = vm::onPickYear,
            onCustomFrom = vm::onCustomFrom,
            onCustomTo = vm::onCustomTo,
            onDismiss = onDismissRange,
        )
    }

    // 工地：区间口径弹窗（口径 chips + 自定义起止；与明细页共用 SiteRangeDialog）
    if (showSiteRangeDialog) {
        com.mdot.app.feature.detail.SiteRangeDialog(
            mode = when (state.dimension) {
                StatsDimension.SITE_SPAN -> com.mdot.app.feature.detail.SiteDetailRangeMode.PROJECT_SPAN
                StatsDimension.CUSTOM -> com.mdot.app.feature.detail.SiteDetailRangeMode.CUSTOM
                else -> com.mdot.app.feature.detail.SiteDetailRangeMode.UNSETTLED
            },
            customFrom = state.customFrom,
            customTo = state.customTo,
            onMode = { mode ->
                vm.onDimension(
                    when (mode) {
                        com.mdot.app.feature.detail.SiteDetailRangeMode.UNSETTLED -> StatsDimension.SITE_PENDING
                        com.mdot.app.feature.detail.SiteDetailRangeMode.PROJECT_SPAN -> StatsDimension.SITE_SPAN
                        com.mdot.app.feature.detail.SiteDetailRangeMode.CUSTOM -> StatsDimension.CUSTOM
                    }
                )
            },
            onCustomFrom = vm::onCustomFrom,
            onCustomTo = vm::onCustomTo,
            onDismiss = onDismissSite,
        )
    }
}

/** 顶栏分段控件（样式对齐工地记工记录页顶栏胶囊）：统计、明细常驻；记月仅非工地制度显示（工地模式后续配专属页） */
@Composable
private fun StatsTabBar(pagerState: PagerState, showMonth: Boolean) {
    val scope = rememberCoroutineScope()
    val labels = buildList {
        add(stringResource(R.string.stats_title))
        if (showMonth) add(stringResource(R.string.stats_tab_month))
        add(stringResource(R.string.stats_tab_detail))
    }
    SegmentBar(
        labels = labels,
        selected = pagerState.currentPage,
        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        // 三段总宽（3×84+8=260dp）须 ≤ 顶栏标题区（屏宽 − 两侧 48dp 占位）
        segWidth = 84.dp,
        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
    )
}

/** 模式取值文本：非工地=时长（小时），工地=工数（"N 工"） */
