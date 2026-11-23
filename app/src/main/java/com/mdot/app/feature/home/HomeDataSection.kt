package com.mdot.app.feature.home

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.model.HomeCardContents
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.core.designsystem.engineIsMiuix
import com.mdot.app.core.designsystem.emphasisCardInk

/** 数据区：工时制度标签 + 本期大数字 + 综合工时副行 + 今日加班胶囊 + 考勤周期行（SITE 形态另见 SiteDataContent） */
@Composable
internal fun DataSection(state: HomeUiState, onOpenStats: () -> Unit, onOpenRecord: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            // 考勤周期胶囊（点击进统计），取代原「本期加班时长」标签行
            val periodInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .clip(engineShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = periodInteraction,
                        indication = LocalIndication.current,
                        onClick = onOpenStats,
                    )
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ms_calendar_month), null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSpec.inline),
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
            // hero 数字：**显示什么由用户在「首页卡片 → 数据区」里选**（卡片内容自定义）。
            // 时长走 AnimatedNumberText（与工地记工工钱同款滚动反馈），金额走 AnimatedMoneyText。
            when (state.dataContent) {
                HomeCardContents.DATA_NET_PAY -> DataMoneyHero(state.netPayCents, label = "netPayBigNumber")
                else -> {
                    // 数值变化时滚动滑入（与工地记工工钱同款滑动动画，docs/15 统一数字反馈）
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumberText(
                            value = state.cycleOtMinutes,
                            text = { TimeUtils.hoursDecimal(it) },
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            label = "otBigNumber",
                        )
                        Text(
                            stringResource(R.string.detail_worked_hours_unit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.s, start = Spacing.xs),
                        )
                    }
                }
            }
            // 综合工时副行：超时部分 primary 强调（10 文档 F-Z5；月中为预演值）。
            // 它解释的是**上面那个时长数字**（超时 X · 标准 Y），hero 被换成金额时就不再适用
            if (state.dataContent == HomeCardContents.DATA_OT_HOURS &&
                state.salary.workSystem == WorkSystem.COMPREHENSIVE && state.cycleOtMinutes > 0
            ) {
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
            // 今日胶囊两态：已记 → 「今日 Xh」；没记 → 「今日还没记」可点直达记加班。
            // hero 被换成金额（实发工资）后时长不再是大数字 → 旁边补一颗**同款**「总 Xh」把时长留住
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.todayOtMinutes > 0) {
                    TodayPill(
                        text = stringResource(R.string.home_today_ot, TimeUtils.hoursDecimal(state.todayOtMinutes)),
                        filled = true,
                        onRecord = onOpenRecord,
                    )
                } else {
                    TodayPill(text = stringResource(R.string.home_today_empty), filled = false, onRecord = onOpenRecord)
                }
                if (state.dataContent != HomeCardContents.DATA_OT_HOURS) {
                    Spacer(Modifier.width(Spacing.s))
                    // 同款填充胶囊（filled 态本身不可点，故不接记录动作）
                    TodayPill(
                        text = stringResource(R.string.home_total_ot, TimeUtils.hoursDecimal(state.cycleOtMinutes)),
                        filled = true,
                        onRecord = {},
                    )
                }
            }
        }
    }
}

/** 数据区的金额型 hero（实发工资 / 本月收入）：金额未配置或实时预览全 0（单据与引擎皆空）时给 "-"（同收入卡口径，不显 ¥0.00） */
@Composable
private fun DataMoneyHero(cents: Long?, label: String) {
    val style = MaterialTheme.typography.displaySmall
    val color = MaterialTheme.colorScheme.onSurface
    if (cents == null) {
        Text("-", style = style, color = color, fontWeight = FontWeight.Bold)
    } else {
        AnimatedMoneyText(
            cents,
            style = style,
            color = color,
            fontWeight = FontWeight.Bold,
            label = label,
        )
    }
}

/** 今日胶囊两态：已记（primaryContainer 填充「今日 Xh/N 工」）/ 未记（描边「今日还没记」，点击直达记加班/记工） */
@Composable
internal fun TodayPill(text: String, filled: Boolean, onRecord: () -> Unit) {
    // 记一笔后「今日还没记」→「今日 Xh」切换：新状态从上方淡入下滑（T4-2b，motionScheme specs）
    val pillFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val pillSlide = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(pillFade) + slideInVertically(pillSlide) { -it / 2 })
                .togetherWith(fadeOut(pillFade))
        },
        label = "todayPill",
    ) { t ->
        if (filled) {
            Text(
                text = t,
                style = MaterialTheme.typography.labelLarge,
                color = if (engineIsMiuix) MaterialTheme.colorScheme.primary else emphasisCardInk(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = Spacing.s)
                    .clip(engineShape(Radius.pill))
                    .background(
                        if (engineIsMiuix) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                    )
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        } else {
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = t,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = Spacing.s)
                    .pressScale(interaction, pressedScale = 0.93f)
                    .clip(engineShape(Radius.pill))
                    .border(1.dp, MaterialTheme.colorScheme.primary, engineShape(Radius.pill))
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onRecord)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
internal fun IncomeCard(
    workSystem: WorkSystem,
    cycleOtPay: Long?,
    monthIncome: Long?,
    onOpenDetail: () -> Unit,
) {
    // hero 卡（primaryContainer，与统计/明细页同视觉体系）；整卡与右端「明细 ›」小字均进入明细页
    SectionCard(
        onClick = onOpenDetail,
        // 首页收色（2026-09-30 用户口径）：MIUIX 卡底走中性（强调整色只留给数字/链接）；MD3 保持原强调色底
        containerColor = if (engineIsMiuix) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (workSystem == WorkSystem.HOURLY) stringResource(R.string.home_income_hourly) else stringResource(R.string.home_income_ot),
                    style = MaterialTheme.typography.labelSmall,
                    color = emphasisCardInk().copy(alpha = 0.7f),
                )
                if (cycleOtPay != null) {
                    AnimatedMoneyText(
                        cycleOtPay,
                        style = MaterialTheme.typography.titleMedium,
                        color = emphasisCardInk(),
                        label = "incomeCyclePay",
                    )
                } else {
                    Text("-", style = MaterialTheme.typography.titleMedium, color = emphasisCardInk())
                }
            }
            Column(Modifier.weight(1.2f)) {
                Text(
                    stringResource(R.string.home_income_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = emphasisCardInk().copy(alpha = 0.7f),
                )
                if (monthIncome != null) {
                    AnimatedMoneyText(
                        monthIncome,
                        style = MaterialTheme.typography.titleMedium,
                        color = emphasisCardInk(),
                        label = "incomeMonth",
                    )
                } else {
                    Text("-", style = MaterialTheme.typography.titleMedium, color = emphasisCardInk())
                }
            }
            // 「工资 ›」小字：只有这一小块进入工资设定
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.88f)
                    .clip(engineShape(Radius.button))
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onOpenDetail,
                    )
                    .padding(horizontal = Spacing.s, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_income_detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = emphasisCardInk(),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = emphasisCardInk(),
                )
            }
        }
    }
}
