package com.mdot.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.RecordSheetController
import java.time.LocalDate

/** 首页（03 文档 §5.1 线框）；顶栏由 AppRoot 的固定一级顶栏统一提供 */
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding()
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
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith
                            fadeOut(tween(120))
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
                SectionCard(Modifier.weight(1f), onClick = onOpenCalendar) {
                    Column {
                        // M3 Expressive：图标置于圆角 tonal 方块上
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.button)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_ms_calendar_month), null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(Spacing.s))
                        Text(stringResource(R.string.home_entry_calendar), style = MaterialTheme.typography.titleMedium)
                    }
                }
                SectionCard(Modifier.weight(1f), onClick = onOpenStats) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.button)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_ms_bar_chart), null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(Spacing.s))
                        Text(stringResource(R.string.home_entry_stats), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // 记加班大按钮：醒目主入口，点击直达记录弹层
        RecordHeroButton(workSystem = state.salary.workSystem, onClick = onRecord)

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 首页骨架屏：标题 + 大数字 + 收入胶囊 + 两张卡片的占位，透明度呼吸闪烁 */
@Composable
private fun HomeSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
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
