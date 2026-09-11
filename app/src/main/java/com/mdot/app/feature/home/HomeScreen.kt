package com.mdot.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.wrapContentWidth
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SiteMoneyColors
import com.mdot.app.feature.stats.pieColor
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.WorkHeatmap
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/** 首页（03 文档 §5.1 线框）；顶栏由 AppRoot 的固定一级顶栏统一提供。
 *  响应式（docs 03 §3.2）：EXPANDED（≥840dp）双栏——左数据（大数字/收入）| 右操作（入口卡/记加班主按钮），
 *  其余档位单列滚动（原布局）。 */
@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenRecord: () -> Unit,
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
                        val content = HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenRecord, stackedEntries = true)
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
                            HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenRecord, stackedEntries = true)?.let {
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
                val content = HomeCardContent(id, state, site, onOpenCalendar, onOpenStats, onOpenDetail, onOpenRecord, stackedEntries = false)
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
    onOpenRecord: () -> Unit,
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
    "entries" -> ({
        if (stackedEntries) {
            EntryCard(onOpenCalendar, R.drawable.ic_ms_calendar_month, R.string.home_entry_calendar)
            Spacer(Modifier.height(Spacing.m))
            EntryCard(onOpenStats, R.drawable.ic_ms_bar_chart, R.string.home_entry_stats)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                EntryCard(
                    onOpenCalendar, R.drawable.ic_ms_calendar_month, R.string.home_entry_calendar,
                    modifier = Modifier.weight(1f),
                )
                EntryCard(
                    onOpenStats, R.drawable.ic_ms_bar_chart, R.string.home_entry_stats,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    })
    "heatmap" -> ({
        // 显隐与数据解耦：所有制度同序列（配置驱动）；固定六个月窗口（本月向前推五个月），与统计页同款
        HomeHeatmapCard(site.heatValues, state.salary.workSystem == WorkSystem.SITE)
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
            isSite = isSite,
        )
    })
    "monthbar" -> ({
        // 月柱状卡（与统计页同款共享组件）：本月自然月每日柱，强度随制度（工地=工数，其他=加班分钟）
        val isSite = state.salary.workSystem == WorkSystem.SITE
        val today = java.time.LocalDate.now()
        val from = today.withDayOfMonth(1)
        val days = today.lengthOfMonth()
        val byDate = site.daily.associateBy { it.date }
        val values = (0 until days).map { off ->
            val p = byDate[from.plusDays(off.toLong())]
            if (p == null) 0f else if (isSite) p.worksMilli / 1000f else p.otMinutes.toFloat()
        }
        SectionCard {
            com.mdot.app.core.designsystem.component.MonthBarCard(
                values = values,
                from = from,
                workSystem = state.salary.workSystem,
            )
        }
    })
    else -> null
}

/** 数据区：工时制度标签 + 本期大数字 + 综合工时副行 + 今日加班胶囊 + 考勤周期行（SITE 形态另见 SiteDataContent） */
@Composable
private fun DataSection(state: HomeUiState, onOpenStats: () -> Unit, onOpenRecord: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            // 考勤周期胶囊（点击进统计），取代原「本期加班时长」标签行
            val periodInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = periodInteraction,
                        indication = LocalIndication.current,
                        onClick = onOpenStats,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ms_calendar_month), null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    state.period?.let { period ->
                        stringResource(R.string.home_cycle_period, "${TimeUtils.mdCn(period.from)} – ${TimeUtils.mdCn(period.to)}")
                    } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            // 数值变化时轻微缩放淡入
            AnimatedContent(
                targetState = state.cycleOtMinutes,
                transitionSpec = {
                    (fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + scaleIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), initialScale = 0.92f)) togetherWith
                        fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
                },
                label = "otBigNumber",
            ) { minutes ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeUtils.hoursDecimal(minutes),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.detail_worked_hours_unit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                    )
                }
            }
            // 综合工时副行：超时部分 primary 强调（10 文档 F-Z5；月中为预演值）
            if (state.salary.workSystem == WorkSystem.COMPREHENSIVE && state.cycleOtMinutes > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_overtime_prefix),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.home_overtime_hours, TimeUtils.hoursDecimal(state.overtimeMinutes)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.home_overtime_standard_suffix, TimeUtils.hoursDecimal(state.periodStandardMinutes)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 今日胶囊两态：已记 → 「今日 Xh」；没记 → 「今日还没记」可点直达记加班
            if (state.todayOtMinutes > 0) {
                TodayPill(
                    text = stringResource(R.string.home_today_ot, TimeUtils.hoursDecimal(state.todayOtMinutes)),
                    filled = true,
                    onRecord = onOpenRecord,
                )
            } else {
                TodayPill(text = stringResource(R.string.home_today_empty), filled = false, onRecord = onOpenRecord)
            }
        }
    }
}

/** 今日胶囊两态：已记（primaryContainer 填充「今日 Xh/N 工」）/ 未记（描边「今日还没记」，点击直达记加班/记工） */
@Composable
private fun TodayPill(text: String, filled: Boolean, onRecord: () -> Unit) {
    if (filled) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    } else {
        val interaction = remember { MutableInteractionSource() }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.pill))
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onRecord)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
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
            // M3 Expressive：图标置于圆角 tonal 方块上
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.button)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
        }
    }
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
            shape = RoundedCornerShape(Radius.button),
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

/** 记加班大按钮：全宽 primary 胶囊，首页醒目主操作入口 */
@Composable
private fun RecordHeroButton(workSystem: WorkSystem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pressScale(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_more_time), contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when (workSystem) {
                    WorkSystem.HOURLY -> stringResource(R.string.home_record_hourly)
                    WorkSystem.COMPREHENSIVE -> stringResource(R.string.home_record_comprehensive)
                    WorkSystem.STANDARD -> stringResource(R.string.home_record_standard)
                    WorkSystem.SITE -> stringResource(R.string.site_record_hero)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun IncomeCard(
    workSystem: WorkSystem,
    cycleOtPay: Long?,
    monthIncome: Long?,
    onOpenDetail: () -> Unit,
) {
    // hero 卡（primaryContainer，与统计/明细页同视觉体系）；整卡与右端「明细 ›」小字均进入明细页
    SectionCard(onClick = onOpenDetail, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (workSystem == WorkSystem.HOURLY) stringResource(R.string.home_income_hourly) else stringResource(R.string.home_income_ot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    cycleOtPay?.let { Money.yuanWithSign(it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1.2f)) {
                Text(
                    stringResource(R.string.home_income_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    monthIncome?.let { Money.yuanWithSign(it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // 「工资 ›」小字：只有这一小块进入工资设定
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.88f)
                    .clip(RoundedCornerShape(Radius.button))
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onOpenDetail,
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_income_detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}


/** 首页 SITE 数据区（12 文档 F-S6）：本月工数大数字 + 加班副行 + 项目名 */
@Composable
private fun SiteDataContent(state: HomeUiState, site: SiteHomeUi, onOpenRecord: () -> Unit) {
    Column {
        Text(
            if (state.salary.siteDisplayUnit == "HOUR") stringResource(R.string.site_cycle_label_hour)
            else stringResource(R.string.site_cycle_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val hourMode = state.salary.siteDisplayUnit == "HOUR"
        // 数值变化时轻微缩放淡入（与 DataSection 同款）
        AnimatedContent(
            targetState = site.totalWorksMilli,
            transitionSpec = {
                (fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + scaleIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), initialScale = 0.92f)) togetherWith
                    fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
            },
            label = "siteBigNumber",
        ) { milli ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (hourMode) String.format(java.util.Locale.US, "%.1f", milli / 1000.0 * (site.siteBaseMinutes / 60.0))
                    else String.format(java.util.Locale.US, "%.1f", milli / 1000.0),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    if (hourMode) stringResource(R.string.detail_worked_hours_unit)
                    else stringResource(R.string.stats_works_unit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
            }
        }
        if (site.otMinutes > 0 || site.projectName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    listOfNotNull(
                        if (site.otMinutes > 0) stringResource(R.string.home_overtime_hours, TimeUtils.hoursDecimal(site.otMinutes)) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (site.projectName.isNotEmpty()) {
                    // 项目色点：与统计饼图同一稳定色（projectId 索引）
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(pieColor((site.projectId % 7).toInt()), androidx.compose.foundation.shape.CircleShape),
                    )
                    Text(
                        site.projectName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 今日胶囊两态（口径随显示单位偏好）
        if (site.todayWorksMilli > 0 || site.todayOtMinutes > 0) {
            TodayPill(
                text = if (hourMode)
                    stringResource(R.string.site_home_today_hours, TimeUtils.hoursDecimal(((site.todayWorksMilli * site.siteBaseMinutes / 1000L) + site.todayOtMinutes * 60L).toInt()))
                else
                    stringResource(R.string.site_home_today_works, site.todayWorksMilli / 1000L),
                filled = true,
                onRecord = onOpenRecord,
            )
        } else {
            TodayPill(text = stringResource(R.string.home_today_empty), filled = false, onRecord = onOpenRecord)
        }
    }
}

/** 首页 SITE 待结卡：应得 / 已借支 / 待结 + 「明细 ›」入口（primaryContainer hero；整卡与小字均进明细页） */
@Composable
private fun SitePendingCard(site: SiteHomeUi, onOpenDetail: () -> Unit) {
    SectionCard(onClick = onOpenDetail, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.site_settlement_receivable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    Money.yuanWithSign(site.workPayCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(
                        stringResource(R.string.site_stat_advance) + " " + Money.yuanWithSign(site.advanceCents),
                        style = MaterialTheme.typography.labelSmall,
                        color = SiteMoneyColors.ReceivedGreen,
                    )
                    Text(
                        stringResource(R.string.site_stat_pending) + " " + Money.yuanWithSign(site.pendingCents),
                        style = MaterialTheme.typography.labelSmall,
                        color = SiteMoneyColors.PendingOrange,
                    )
                }
            }
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.88f)
                    .clip(RoundedCornerShape(Radius.button))
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onOpenDetail)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_income_detail), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}


/** 首页本周柱状卡：与统计页同款共享组件；柱顶数值单位随制度（时长 | N 工） */
@Composable
private fun HomeWeekBarCard(
    bars: List<com.mdot.app.core.designsystem.component.WeekBar>,
    isSite: Boolean,
) {
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    com.mdot.app.core.designsystem.component.WeekBarCard(
        bars = bars,
        valueText = { v ->
            if (isSite) {
                val num = if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
                stringResource(R.string.stats_works_value, num)
            } else {
                TimeUtils.prettyDuration(v.roundToInt())
            }
        },
        selectedLabel = selected,
        onSelect = { selected = if (selected == it) null else it },
    )
}

/** 首页热点图卡：GitHub 贡献图风格，与统计页一致（无标题）；固定六个月窗口（本月向前推五个月），强度随制度（工地=工数，其他=加班分钟） */
@Composable
private fun HomeHeatmapCard(heatValues: Map<LocalDate, Float>, isSite: Boolean) {
    SectionCard {
        val today = LocalDate.now()
        WorkHeatmap(
            values = heatValues,
            start = today.withDayOfMonth(1).minusMonths(5),
            end = today,
        )
    }
}

