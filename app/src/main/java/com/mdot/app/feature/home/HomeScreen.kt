package com.mdot.app.feature.home

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.wrapContentWidth
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.component.HomeCardContentRegistry
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.HomeCardContents
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import com.mdot.app.core.designsystem.engineIsMiuix

/** 首页（03 文档 §5.1 线框）；顶栏由 AppRoot 的固定一级顶栏统一提供。
 *  响应式（docs 03 §3.2）：EXPANDED（≥840dp）双栏——左数据（大数字/收入）| 右操作（入口卡/记加班主按钮），
 *  其余档位单列滚动（原布局）。 */
@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenPayMonth: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRecord: () -> Unit,
    /** 图表长按某日 → 记录那天（21 文档：AppRoot 按制度分流——工地→记工页带日期、其他→记录弹层） */
    onChartDay: (LocalDate) -> Unit = { homeVm.recordSheet.open(it) },
    homeVm: HomeViewModel = hiltViewModel(),
) {
    val state by homeVm.uiState.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val twoPane = LocalWindowSpec.current == WindowSpec.EXPANDED
    // 卡片渲染序列（v0.6.0 首页卡片可编辑；id -> 内容由 HomeCardContent 按 id 分发）
    val cardIds = state.cards
    val site by homeVm.siteState.collectAsStateWithLifecycle()

    if (twoPane) {
        // ---- 宽屏双栏：总宽 720dp 居中，左右各半 ----
        Row(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .contentBottomPadding()
                .padding(horizontal = Spacing.page)
                .widthIn(max = AdaptiveSpecs.twoPaneMaxWidth),
        ) {
            // 左栏（数据区，内容短不滚动）
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(statusBar + TopBarHeight + Spacing.s))
                if (state.loading) {
                    HomeSkeleton()
                } else {
                    Spacer(Modifier.height(Spacing.xl))
                    var firstData = true
                    cardIds.forEach { id ->
                        val content = HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenPayMonth, onOpenProfile, onOpenRecord, onChartDay = onChartDay, stackedEntries = true)
                        if (content != null) {
                            if (!firstData) Spacer(Modifier.height(Spacing.l))
                            content()
                            firstData = false
                        }
                    }
                }
            }
            Spacer(Modifier.width(Spacing.xl))
            // 右栏（操作区，独立滚动）：入口卡纵向堆叠 + 记加班主按钮
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(statusBar + TopBarHeight + Spacing.s))
                if (!state.loading) {
                    cardIds.forEach { id ->
                        if (id == "entries" || id == "record") {
                            HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenPayMonth, onOpenProfile, onOpenRecord, onChartDay = onChartDay, stackedEntries = true)?.let {
                                it()
                                Spacer(Modifier.height(Spacing.m))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    } else {
        // ---- 手机：单列滚动（原布局） ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .contentBottomPadding()
                .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                .padding(horizontal = Spacing.page),
        ) {
            // 顶部避让固定一级顶栏（状态栏 + 56dp）
            Spacer(Modifier.height(statusBar + TopBarHeight + Spacing.s))

            // 数据库打开/首次汇总期间先渲染骨架屏（KDF 降级后仅极短），避免空数字闪烁
            if (state.loading) {
                HomeSkeleton()
                Spacer(Modifier.height(Spacing.xl))
                return@Column
            }

            // 按配置序列渲染卡片；卡间距沿用原节奏：数据区之后 l，其余 m
            var prev: String? = null
            cardIds.forEach { id ->
                val content = HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenPayMonth, onOpenProfile, onOpenRecord, onChartDay = onChartDay, stackedEntries = false)
                if (content != null) {
                    if (prev != null) {
                        Spacer(Modifier.height(if (prev == "data") Spacing.l else Spacing.m))
                    }
                    content()
                    prev = id
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

    }
}

/** 按卡片 id 渲染对应内容；返回 null = 该 id 未知（忽略）。entryOnRight 仅宽屏双栏布局差异用 */
@Composable
private fun HomeCardContent(
    id: String,
    state: HomeUiState,
    site: SiteHomeUi,
    onOpenCalendar: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenPayMonth: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRecord: () -> Unit,
    /** 图表长按某日 → 记录（与统计页同一接线） */
    onChartDay: (LocalDate) -> Unit,
    stackedEntries: Boolean,
): (@Composable () -> Unit)? = when (id) {
    "data" -> ({
        if (state.salary.workSystem == WorkSystem.SITE) {
            SiteDataContent(state, site, onOpenRecord)
        } else {
            DataSection(state, onOpenStats = onOpenStats, onOpenRecord = onOpenRecord)
        }
    })
    "income" -> ({
        if (state.salary.workSystem == WorkSystem.SITE) {
            SitePendingCard(site, onOpenDetail)
        } else {
            IncomeCard(
                workSystem = state.salary.workSystem,
                cycleOtPay = state.cycleOtPayCents,
                monthIncome = state.monthIncomeCents,
                onOpenDetail = onOpenDetail,
            )
        }
    })
    // 快捷入口：按配置的入口项逐个渲染（多选，顺序即显示顺序），图标/文案与配置弹窗同源。
    // 工地制度没有「记月」概念（同记月页签的处理），该入口在工地下不渲染
    "entries" -> ({
        val visible = state.entries.filterNot {
            it == HomeCardContents.ENTRY_PAYMONTH && state.salary.workSystem == WorkSystem.SITE
        }
        val onClickOf: (String) -> Unit = { entryId ->
            when (entryId) {
                HomeCardContents.ENTRY_CALENDAR -> onOpenCalendar()
                HomeCardContents.ENTRY_STATS -> onOpenStats()
                HomeCardContents.ENTRY_PAYMONTH -> onOpenPayMonth()
                HomeCardContents.ENTRY_DETAIL -> onOpenDetail()
                HomeCardContents.ENTRY_PROFILE -> onOpenProfile()
                else -> {}
            }
        }
        if (stackedEntries) {
            visible.forEachIndexed { i, entryId ->
                val ui = HomeCardContentRegistry.resolve(entryId) ?: return@forEachIndexed
                if (i > 0) Spacer(Modifier.height(Spacing.m))
                EntryCard(onClick = { onClickOf(entryId) }, iconRes = ui.iconRes, labelRes = ui.labelRes)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                visible.forEach { entryId ->
                    val ui = HomeCardContentRegistry.resolve(entryId) ?: return@forEach
                    EntryCard(
                        onClick = { onClickOf(entryId) }, iconRes = ui.iconRes, labelRes = ui.labelRes,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    })
    "heatmap" -> ({
        // 显隐与数据解耦：所有制度同序列（配置驱动）；窗口=工地项目活动期（21 文档 B3）/其他固定六个月，与统计页同款
        HomeHeatmapCard(site.heatValues, state.salary.workSystem, onChartDay)
    })
    "weekbar" -> ({
        // 本周柱状卡（与统计页同款共享组件）：从本月 daily 过滤本周；强度随制度（工地=工数，其他=加班分钟）
        val isSite = state.salary.workSystem == WorkSystem.SITE
        val today = java.time.LocalDate.now()
        val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val weekValues = site.daily.mapNotNull { d ->
            val date = d.date
            if (date < monday || date.isAfter(today)) null else date to (if (isSite) d.worksMilli / 1000f else d.otMinutes.toFloat())
        }.toMap()
        HomeWeekBarCard(
            bars = com.mdot.app.core.designsystem.component.buildWeekBars(weekValues),
            workSystem = state.salary.workSystem,
            onChartDay = onChartDay,
        )
    })
    "monthbar" -> ({
        // 柱状卡（与统计页同款共享组件）：非工地=本月自然月每日柱；工地=本期待结区间柱状（>31 天按周聚合，21 文档 B3）
        val isSite = state.salary.workSystem == WorkSystem.SITE
        val today = java.time.LocalDate.now()
        if (isSite && site.rangeFrom != null && site.rangeTo != null) {
            val dayValues = site.daily.associate { it.date to (it.worksMilli / 1000f) }
            val bars = com.mdot.app.domain.SiteRanges.bucketize(site.rangeFrom, site.rangeTo, dayValues)
            // 周聚合柱（21 文档 B3 用户反馈）：浮窗日期段改周区间、禁长按（柱=整周，长按无单日语义）
            val weekly = bars.any { it.label != null }
            com.mdot.app.core.designsystem.component.MonthBarCard(
                values = bars.map { it.value },
                from = site.rangeFrom,
                workSystem = state.salary.workSystem,
                onDayLongPress = if (weekly) null else onChartDay,
                hintValueText = if (weekly) {
                    com.mdot.app.core.designsystem.component.chartHintText(
                        state.salary.workSystem,
                        dateLabel = { d -> "${TimeUtils.mdCn(d)} – ${TimeUtils.mdCn(d.plusDays(6))}" },
                    )
                } else com.mdot.app.core.designsystem.component.chartHintText(state.salary.workSystem),
                dates = bars.map { it.date },
                barLabels = bars.map { it.label },
            )
        } else {
            val from = today.withDayOfMonth(1)
            val days = today.lengthOfMonth()
            val byDate = site.daily.associateBy { it.date }
            val values = (0 until days).map { off ->
                val p = byDate[from.plusDays(off.toLong())]
                if (p == null) 0f else if (isSite) p.worksMilli / 1000f else p.otMinutes.toFloat()
            }
            // 与统计页完全一致（共享组件自带卡片底，故不再套 SectionCard）：点击出浮窗、长按进该日记加班弹层
            com.mdot.app.core.designsystem.component.MonthBarCard(
                values = values,
                from = from,
                workSystem = state.salary.workSystem,
                onDayLongPress = onChartDay,
                hintValueText = com.mdot.app.core.designsystem.component.chartHintText(state.salary.workSystem),
            )
        }
    })
    else -> null
}

/** 首页骨架屏：标题 + 大数字 + 收入胶囊 + 两张卡片的占位，透明度呼吸闪烁 */
@Composable
private fun HomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    @Composable
    fun SkeletonBlock(mod: Modifier) {
        Surface(
            shape = engineShape(Radius.button),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = mod.alpha(alpha),
        ) { Box(Modifier) }
    }
    Column {
        SkeletonBlock(Modifier.fillMaxWidth(0.32f).height(16.dp))
        Spacer(Modifier.height(Spacing.s))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(56.dp))
        Spacer(Modifier.height(Spacing.l))
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp))
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SkeletonBlock(Modifier.weight(1f).height(120.dp))
            SkeletonBlock(Modifier.weight(1f).height(120.dp))
        }
    }
}

/** 入口卡：图标 tonal 方块 + 标题（首页单列时与另一卡等宽并排；宽屏右栏纵向堆叠全宽） */
@Composable
private fun EntryCard(
    onClick: () -> Unit,
    iconRes: Int,
    labelRes: Int,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier, onClick = onClick) {
        Column {
            // MIUIX：**去掉图标瓦片**（用户 2026-09-30）——裸图标 + 主色，与顶栏裸图标同一口径；
            // MD3 保持原圆角 tonal 方块（“图标置于 tonal 底上”是 M3E 的节奏手法）。
            if (engineIsMiuix) {
                Icon(
                    painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconBoxSpec.entry.icon),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(IconBoxSpec.entry.box)
                        .background(MaterialTheme.colorScheme.secondaryContainer, engineShape(Radius.button)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(iconRes), null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(IconBoxSpec.entry.icon),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
        }
    }
}
