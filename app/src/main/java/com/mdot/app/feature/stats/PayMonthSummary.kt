package com.mdot.app.feature.stats

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.EngineIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.HeroAmountTier
import com.mdot.app.core.designsystem.HeroTile
import com.mdot.app.core.designsystem.heroAmountStyle
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.IncomeSliceKind
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.reconciliations
import com.mdot.app.domain.model.Reconciliation
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.YearMonth
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.heroChipSurface

/**
 * 汇总卡（docs/20 P0-2）：实发大字 + 应发/扣款/其他构成。
 * 之前全页只有四个分组小计、没有总数，而扣款/其他两组还是负数展示，用户得心算四项——
 * 工资单最核心的数字不应该让用户自己算。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PayMonthSummaryCard(
    sheet: PayMonthSheet,
    attendance: PayrollCalculator.AttendanceSummary?,
    prevNetCents: Long?,
    /** 本周期（月份）：用于「周期进度」，也是翻月淡入的 key */
    period: YearMonth,
    /** 精简形态（长按卡片头部切换）：不是「另一张卡」，而是同一张卡的**形变目标** */
    compact: Boolean,
    onToggleCompact: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenReconcile: () -> Unit,
) {
    val net = sheet.netCents
    val recon = sheet.reconciliations()
    // 翻月轻淡入（hero 数字另有 AnimatedNumberText 滚动）：换月时卡片不再「啄」地一下跳完全换
    val appear = remember { Animatable(1f) }
    val appearSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(period) {
        appear.snapTo(0.55f)
        appear.animateTo(1f, appearSpec)
    }
    // ---- 完整 ⇄ 精简：**一张卡自己形变**，全卡共用**同一条**缓动曲线（slow 档）----
    // 早先的做法是两张卡 `AnimatedContent` 交叉淡入——观感是「换了一个数字」而不是「数字缩小了」，且偏生硬。
    // 现在：字号连续插值（大数字收缩成小数字）+ 精简版没有的元素 fadeOut·shrinkVertically 收掉，
    // 共用的考勤胶囊/对账行**随上方布局收起自然上移**——位移与字号收缩同一条曲线，故同步。
    // ⚠️ 曲线档位的实测手感（同一台真机、30fps 录屏量）：slow ≈ 300ms、default ≈ 250ms（差得不多，
    // 因为 spatial 是弹簧、时长随距离变），fast ≈ 120ms。用户先是要慢、后是嫌慢 → 取 **fast 档**。
    // 若以后还嫌不合适：三档之外只能走显式 tween（同 InlineConfirmButton 倒计时条的例外）。
    val morph = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val morphSize = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val morphDp = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    // 形变分数：hero 金额在两档字号之间插值（大档 ⇄ 标准档）——
    // 「数字整体缩小」的观感靠它；单档就没有收缩过程了
    val shrink by animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = morph,
        label = "summaryShrink",
    )
    val barHeight by animateDpAsState(
        targetValue = if (compact) Spacing.xs else Spacing.s,
        animationSpec = morphDp,
        label = "sliceBarHeight",
    )
    val sliceTopGap by animateDpAsState(
        targetValue = if (compact) Spacing.s else Spacing.m,
        animationSpec = morphDp,
        label = "sliceTopGap",
    )
    // 周期进度（第 N / 总 天）：提醒「这个月的数字还会长」；历史月份则显示已跑完
    val today = LocalDate.now()
    val elapsedDays = when {
        period == YearMonth.from(today) -> today.dayOfMonth
        period.isBefore(YearMonth.from(today)) -> period.lengthOfMonth()
        else -> 0
    }
    val totalDays = period.lengthOfMonth()
    // 底色与「首页/明细页的 hero 卡」**完全一致**：primaryContainer + onPrimaryContainer
    //（早先用的是自创的 surfaceContainerHigh + ¥ 字形圆底，与另两页不一致，2026-09-23 统一）
    SectionCard(containerColor = emphasisCardSurface()) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = appear.value },
        ) {
            SummaryCardHeader(
                net = net,
                prevNetCents = prevNetCents,
                compact = compact,
                onToggleCompact = onToggleCompact,
            )
            Spacer(Modifier.height(Spacing.xs))
            // hero 金额：两档字号之间随形变插值（大档 ⇄ 标准档，档位定义见 HeroSpec）
            AnimatedMoneyText(
                cents = net,
                style = heroAmountStyle(
                    from = HeroAmountTier.Large,
                    to = HeroAmountTier.Standard,
                    fraction = shrink,
                ),
                color = emphasisCardInk(),
                fontWeight = FontWeight.Bold,
                label = "payMonthNet",
            )
            // 应发构成占比条：完整形态配图例、条更厚；精简形态只留细条（高度/间距也渐变）
            SliceBar(
                sheet = sheet,
                legendVisible = !compact,
                barHeight = barHeight,
                topGap = sliceTopGap,
            )
            // 应发 / 扣款 / 其他 三段数据条（**仅完整形态**）：随形变 fadeOut + shrinkVertically 收起
            AnimatedVisibility(
                visible = !compact,
                enter = fadeIn(morph) + expandVertically(morphSize),
                exit = fadeOut(morph) + shrinkVertically(morphSize),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.m))
                    Row(Modifier.fillMaxWidth()) {
                        SumCell(
                            label = stringResource(R.string.paymonth_sum_income),
                            cents = sheet.incomeCents,
                            negative = false,
                            modifier = Modifier.weight(1f),
                        )
                        SumCell(
                            label = stringResource(R.string.paymonth_sum_deduction),
                            cents = sheet.deductionCents,
                            negative = true,
                            modifier = Modifier.weight(1f),
                        )
                        SumCell(
                            label = stringResource(R.string.paymonth_sum_other),
                            cents = sheet.otherCents,
                            negative = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // 考勤胶囊（**两形态都有**）：上方行收起后它自然上移到 hero 下面——位移与字号收缩同一条曲线
            attendance?.let { a ->
                Spacer(Modifier.height(Spacing.s))
                Row(
                    Modifier
                        .fillMaxWidth()
                        // 完整形态：整行可点（≥ 48dp 触控目标）+ 右侧箭头；精简形态不占触控高度
                        // （箭头是「没有的元素」，随形变淡出缩放消失）
                        .then(if (compact) Modifier else Modifier.minimumInteractiveComponentSize())
                        .then(if (compact) Modifier else Modifier.clickable(onClick = onOpenDetail)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (a.isEmpty) {
                        Text(
                            stringResource(R.string.paymonth_attendance_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = emphasisCardInk().copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // 徽章组：不再把总时长和分档时长括号重复（「加班 4小时（平日 4小时）」）
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.weight(1f),
                        ) {
                            attendanceBadges(a).forEach { AttendanceBadge(it) }
                        }
                    }
                    AnimatedVisibility(
                        visible = !compact,
                        enter = fadeIn(morph) + scaleIn(animationSpec = morph),
                        exit = fadeOut(morph) + scaleOut(animationSpec = morph),
                    ) {
                        Icon(
                            EngineIcons.chevron(),
                            contentDescription = null,
                            tint = emphasisCardInk().copy(alpha = 0.75f),
                            modifier = Modifier.size(IconSpec.inline),
                        )
                    }
                }
            }

            // 空态引导（docs/20 P2-7）：考勤有记录、但实时预览还是全 0 → 引导补薪资设定，别让人手工从头填（仅完整形态）
            AnimatedVisibility(
                visible = !compact && attendance?.isEmpty == false &&
                    sheet.incomeCents == 0L && sheet.deductionCents == 0L && sheet.otherCents == 0L,
                enter = fadeIn(morph) + expandVertically(morphSize),
                exit = fadeOut(morph) + shrinkVertically(morphSize),
            ) {
                Text(
                    stringResource(R.string.paymonth_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = emphasisCardInk(),
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            // 对账入口（docs/20 P1-2）：手改过的引擎行 → 与实时引擎值有差，点看明细（两形态都有）
            ReconRow(recon, onOpenReconcile)

            // 周期进度（**仅完整形态**）
            AnimatedVisibility(
                visible = !compact && elapsedDays > 0,
                enter = fadeIn(morph) + expandVertically(morphSize),
                exit = fadeOut(morph) + shrinkVertically(morphSize),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.m))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.paymonth_period_progress, elapsedDays, totalDays),
                            style = MaterialTheme.typography.labelSmall,
                            color = emphasisCardInk().copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(Spacing.xs)
                                .clip(engineShape(Radius.pill))
                                .background(emphasisCardInk().copy(alpha = 0.12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(elapsedDays.toFloat() / totalDays)
                                    .fillMaxHeight()
                                    .background(emphasisCardInk().copy(alpha = 0.55f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 图例圆点直径（6dp）：纯图形装饰、非间距语义，就地常量 */
internal val LegendDot = 6.dp

/**
 * 应发构成占比条（0 段不画；全 0 则整块不出）[showLegend] = false 时不带图例（精简卡用）。
 * 图例给「名称 + 百分比」而不只给颜色——不让颜色单独承担含义（配色与分组卡同源）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SliceBar(
    sheet: PayMonthSheet,
    /** 图例（名称 + 百分比）：精简形态不显示——随形变淡出并收起高度 */
    legendVisible: Boolean = true,
    barHeight: Dp = Spacing.s,
    topGap: Dp = Spacing.m,
) {
    val slices = sheet.incomeSlices()
    val total = sheet.incomeCents
    if (slices.isEmpty() || total <= 0) return
    Spacer(Modifier.height(topGap))
    Row(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(engineShape(Radius.pill)),
    ) {
        slices.forEach { slice ->
            Box(
                Modifier
                    .weight(slice.cents.toFloat() / total)
                    .fillMaxHeight()
                    .background(sliceColor(slice.kind)),
            )
        }
    }
    // 图例与卡上其他元素共用同一条缓动（慢档）
    val legendMorph = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val legendSize = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = legendVisible,
        enter = fadeIn(legendMorph) + expandVertically(legendSize),
        exit = fadeOut(legendMorph) + shrinkVertically(legendSize),
    ) {
        Column {
            Spacer(Modifier.height(Spacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(LegendDot)
                                .clip(CircleShape)
                                .background(sliceColor(slice.kind)),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            stringResource(
                                R.string.paymonth_slice_percent,
                                stringResource(sliceLabelRes(slice.kind)),
                                (slice.cents * 100.0 / total).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = emphasisCardInk().copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 占比段的配色：**同在 `primaryContainer` 卡上，用同色系的不同透明度分层**（不是三个色相）。
 * ⚠️ 早先「基本＝primary / 加班＝tertiary / 其他＝secondary」在卡底换成 `primaryContainer` 后被批量替换
 * 成同一个 `onPrimaryContainer`，两段颜色完全一样（用户 2026-09-23 反馈「基本工资/加班工资对比条颜色相近不好区分」）。
 * 现取值 1.0 / 0.5 / 0.22——保证对比足够、层次清楚；图例同时给出名称与百分比，不只靠颜色。
 */
@Composable
internal fun sliceColor(kind: IncomeSliceKind): Color = when (kind) {
    IncomeSliceKind.BASE -> emphasisCardInk()
    IncomeSliceKind.OVERTIME -> emphasisCardInk().copy(alpha = 0.5f)
    IncomeSliceKind.OTHER_INCOME -> emphasisCardInk().copy(alpha = 0.22f)
}

internal fun sliceLabelRes(kind: IncomeSliceKind): Int = when (kind) {
    IncomeSliceKind.BASE -> R.string.paymonth_slice_base
    IncomeSliceKind.OVERTIME -> R.string.paymonth_slice_overtime
    IncomeSliceKind.OTHER_INCOME -> R.string.paymonth_slice_other
}

/**
 * 汇总卡里的一个数据格（三段数据条）：标签小字在上、金额在下。
 * 扣款/其他带「−」前缀表示它们是从应发里减掉的，0 值淡一档（不喧宾夺主）。
 */
@Composable
internal fun SumCell(label: String, cents: Long, negative: Boolean, modifier: Modifier = Modifier) {
    val zero = cents == 0L
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = emphasisCardInk().copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            (if (negative && !zero) "−" else "") + Money.yuanTrimText(cents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (zero) emphasisCardInk().copy(alpha = 0.45f) else emphasisCardInk(),
            fontWeight = if (zero) null else FontWeight.SemiBold,
        )
    }
}

/** 摘要徽章：卡片底上的浅层小胶囊（读一眼「这个月干了多少 / 记了几天」）。⚠️ 底色/字色走 [heroChipSurface]/[emphasisCardInk]，与统计/明细 hero 的分档胶囊同款 */
@Composable
internal fun AttendanceBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = emphasisCardInk(),
        modifier = Modifier
            .clip(engineShape(Radius.pill))
            .background(heroChipSurface())
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/**
 * 考勤摘要徽章：加班总时长 + （仅当多于一个档位时）分档明细 + 请假 + 记录天数。
 * 只有一个档位时不再列分档（旧文案会写成「加班 4小时（平日 4小时）」，重复又绕）。
 */
@Composable
internal fun attendanceBadges(a: PayrollCalculator.AttendanceSummary): List<String> = buildList {
    if (a.otMinutes > 0) {
        add(stringResource(R.string.paymonth_attendance_ot_total, TimeUtils.prettyDuration(a.otMinutes)))
        val tiers = RateTier.entries.mapNotNull { tier ->
            a.otMinutesByTier[tier]?.takeIf { it > 0 }?.let { tier to it }
        }
        if (tiers.size > 1) {
            tiers.forEach { (tier, minutes) ->
                add(
                    stringResource(
                        when (tier) {
                            RateTier.WEEKDAY -> R.string.paymonth_attendance_weekday
                            RateTier.WEEKEND -> R.string.paymonth_attendance_weekend
                            RateTier.STATUTORY -> R.string.paymonth_attendance_statutory
                        },
                        TimeUtils.prettyDuration(minutes),
                    ),
                )
            }
        }
    }
    if (a.leaveMinutes > 0) {
        add(stringResource(R.string.paymonth_attendance_leave, TimeUtils.prettyDuration(a.leaveMinutes)))
    }
    if (a.recordDays > 0) add(stringResource(R.string.paymonth_attendance_days, a.recordDays))
}

/**
 * 汇总卡头部（两形态共用）：与四张分组卡同一套「字形圆底 + 标题」语言（总览卡用主色强调）+ 环比药丸。
 *
 * **形态切换入口：整行可点**（右侧配 expand_less/more 箭头作指示）——与分组卡折叠完全同款：
 * ① 可见（有箭头就知道能点）＋热区大（整行）；
 * ② **不再用长按**：用户反馈「不知道能长按」——隐藏手势教不会，而且它与整行点击会互相打架
 *   （长按切一次 + 松手又算一次点击 = 原地不动）。
 * 特意不用 `IconButton`：它会把行搞成 48dp 高（图标 24dp 撑开），两形态都变高。
 */
@Composable
internal fun SummaryCardHeader(
    net: Long,
    prevNetCents: Long?,
    compact: Boolean,
    onToggleCompact: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(engineShape(Radius.small))
            .clickable(onClick = onToggleCompact)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 记月汇总卡保留瓦片（用户定：工地 hero 与记月的瓦片留着，其余 hero 不要）
        HeroTile(glyph = "¥")
        Spacer(Modifier.width(Spacing.s))
        // 说明行（labelSmall + α0.8）
        Text(
            stringResource(R.string.paymonth_net_title),
            style = MaterialTheme.typography.labelSmall,
            color = emphasisCardInk().copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        // 环比上月（上月没填过则不显示）：小药丸 + ↑↓ 方向（↓ 偏 error 色提醒）
        prevNetCents?.let { prev ->
            val delta = net - prev
            val down = delta < 0
            Row(
                Modifier
                    .clip(engineShape(Radius.pill))
                    .background(heroChipSurface())
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (down) "↓" else "↑",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (down) MaterialTheme.colorScheme.error
                    else emphasisCardInk(),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_mom, signedYuanText(delta)),
                    style = MaterialTheme.typography.labelMedium,
                    color = emphasisCardInk(),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        // 形态指示：完整时指「可收起」、精简时指「可展开」（与分组卡折叠箭头同图标同位置）
        Icon(
            painterResource(
                if (compact) R.drawable.ic_ms_expand_more else R.drawable.ic_ms_expand_less,
            ),
            contentDescription = stringResource(R.string.paymonth_card_toggle_cd),
            tint = emphasisCardInk().copy(alpha = 0.75f),
        )
    }
}

/** 对账入口行（两个版式共用；出现时轻微放大淡入） */
@Composable
internal fun ReconRow(recon: List<Reconciliation>, onOpenReconcile: () -> Unit) {
    AnimatedVisibility(
        visible = recon.isNotEmpty(),
        enter = scaleIn(initialScale = 0.92f) + fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable(onClick = onOpenReconcile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.paymonth_recon_line,
                    recon.size,
                    signedYuanText(recon.sumOf { it.diffCents }),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = emphasisCardInk(),
                modifier = Modifier.weight(1f),
            )
            Icon(
                EngineIcons.chevron(),
                contentDescription = null,
                tint = emphasisCardInk(),
                modifier = Modifier.size(IconSpec.inline),
            )
        }
    }
}

/** 带符号金额文本：+320 / -86.5（环比与对账差额用） */
internal fun signedYuanText(cents: Long): String =
    if (cents >= 0) "+${Money.yuanTrimText(cents)}" else "-${Money.yuanTrimText(-cents)}"

/** 对账明细（docs/20 P1-2 + P2-2 修订）：逐行「引擎值 → 现值（差）」，每行可**一键恢复引擎值**。
 *
 * 刻意不做全局「覆盖 / 仅填空」开关——按行决定既尊重手改，又不会丢掉引擎值。
 */
@Composable
internal fun ReconciliationDialog(
    sheet: PayMonthSheet,
    onRestoreOne: (PayGroup, Long) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = sheet.reconciliations()
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_recon_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.paymonth_recon_empty), style = MaterialTheme.typography.bodyMedium)
                }
                rows.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(
                                    R.string.paymonth_recon_row,
                                    Money.yuanTrimText(r.item.engineCents ?: 0L),
                                    Money.yuanTrimText(r.item.amountCents),
                                    signedYuanText(r.diffCents),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRestoreOne(r.group, r.item.id) }) {
                            Text(
                                stringResource(R.string.paymonth_recon_restore),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (rows.size > 1) {
                JiabanButton(
                    text = stringResource(R.string.paymonth_recon_restore_all),
                    onClick = { onRestoreAll(); onDismiss() },
                    role = JiabanButtonRole.GHOST,
                )
            }
            JiabanButton(
                text = stringResource(R.string.paymonth_close),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
