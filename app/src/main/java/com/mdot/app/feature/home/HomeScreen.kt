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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
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
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate

/** 首页（03 文档 §5.1 线框）；顶栏由 AppRoot 的固定一级顶栏统一提供。
 *  响应式（docs 03 §3.2）：EXPANDED（≥840dp）双栏——左数据（大数字/收入）| 右操作（入口卡/记加班主按钮），
 *  其余档位单列滚动（原布局）。 */
@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPayroll: () -> Unit,
    onRecord: () -> Unit,
    homeVm: HomeViewModel = hiltViewModel(),
) {
    val state by homeVm.uiState.collectAsStateWithLifecycle()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val twoPane = LocalWindowSpec.current == WindowSpec.EXPANDED

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
                    DataSection(state, onOpenStats = onOpenStats)
                    Spacer(Modifier.height(Spacing.l))
                    IncomeCard(
                        workSystem = state.salary.workSystem,
                        cycleOtPay = state.cycleOtPayCents,
                        monthIncome = state.monthIncomeCents,
                        onOpenPayroll = onOpenPayroll,
                    )
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
                    if (state.showEntryCards) {
                        EntryCard(onOpenCalendar, R.drawable.ic_ms_calendar_month, R.string.home_entry_calendar)
                        Spacer(Modifier.height(Spacing.m))
                        EntryCard(onOpenStats, R.drawable.ic_ms_bar_chart, R.string.home_entry_stats)
                        Spacer(Modifier.height(Spacing.m))
                    }
                    RecordHeroButton(workSystem = state.salary.workSystem, onClick = onRecord)
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

            DataSection(state, onOpenStats = onOpenStats)

            Spacer(Modifier.height(Spacing.l))

            // 收入卡（右端「工资 ›」小字可点进工资设定）
            IncomeCard(
                workSystem = state.salary.workSystem,
                cycleOtPay = state.cycleOtPayCents,
                monthIncome = state.monthIncomeCents,
                onOpenPayroll = onOpenPayroll,
            )

            if (state.showEntryCards) {
                Spacer(Modifier.height(Spacing.m))
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

            Spacer(Modifier.height(Spacing.m))

            // 记加班大按钮：醒目主入口，点击直达记录弹层
            RecordHeroButton(workSystem = state.salary.workSystem, onClick = onRecord)

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** 数据区：工时制度标签 + 本期大数字 + 综合工时副行 + 今日加班胶囊 + 考勤周期行 */
@Composable
private fun DataSection(state: HomeUiState, onOpenStats: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                when (state.salary.workSystem) {
                    WorkSystem.HOURLY -> stringResource(R.string.home_cycle_label_hourly)
                    WorkSystem.COMPREHENSIVE -> stringResource(R.string.home_cycle_label_comprehensive)
                    WorkSystem.STANDARD -> stringResource(R.string.home_cycle_label_standard)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 数值变化时轻微缩放淡入
            AnimatedContent(
                targetState = state.cycleOtMinutes,
                transitionSpec = {
                    (fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + scaleIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), initialScale = 0.92f)) togetherWith
                        fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
                },
                label = "otBigNumber",
            ) { minutes ->
                Text(
                    TimeUtils.hoursDecimal(minutes),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
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
            if (state.todayOtMinutes > 0) {
                Text(
                    text = stringResource(R.string.home_today_ot, TimeUtils.hoursDecimal(state.todayOtMinutes)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Text(
                text = state.period?.let { stringResource(R.string.home_cycle_period, it) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onOpenStats() },
            )
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
    onOpenPayroll: () -> Unit,
) {
    // 与首页其它入口卡同款容器；仅右端「工资 ›」小字可点进工资设定
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (workSystem == WorkSystem.HOURLY) stringResource(R.string.home_income_hourly) else stringResource(R.string.home_income_ot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    cycleOtPay?.let { Money.yuanWithSign(it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1.2f)) {
                Text(
                    stringResource(R.string.home_income_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    monthIncome?.let { Money.yuanWithSign(it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        onClick = onOpenPayroll,
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (cycleOtPay == null) stringResource(R.string.home_income_go_settings) else stringResource(R.string.home_income_payroll_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cycleOtPay == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cycleOtPay == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
