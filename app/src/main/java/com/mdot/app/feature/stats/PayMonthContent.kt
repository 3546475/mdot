package com.mdot.app.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import com.mdot.app.core.designsystem.component.InlineConfirmStyle
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.HeroAmountTier
import com.mdot.app.core.designsystem.HeroTile
import com.mdot.app.core.designsystem.heroAmountStyle
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.MonthPickDialog
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.IncomeSliceKind
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.reconciliations
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.Reconciliation
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.YearMonth

/**
 * 记月页（统计页第 1 页）：月度工资单编辑。
 * 布局仿设计稿：四分组卡（基本/补贴/扣款/其他，头部合计 + 折叠），行点击编辑金额，
 * 补贴/扣款组可添加行（出厂行不可删），底部「导入上月」。
 */
@Composable
fun PayMonthContent(
    /** 摘要条/空态点击 → 跳到「明细」页签（由统计页控制 pager，故回调注入） */
    onOpenDetail: () -> Unit = {},
    /** 「个人所得税」行 → 个税估算页 */
    onOpenTax: () -> Unit = {},
    vm: PayMonthViewModel = hiltViewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val isSite by vm.isSite.collectAsStateWithLifecycle()
    val attendance by vm.attendance.collectAsStateWithLifecycle()
    val collapsedGroups by vm.collapsed.collectAsStateWithLifecycle()
    val prevSheet by vm.prevSheet.collectAsStateWithLifecycle()
    val salary by vm.salary.collectAsStateWithLifecycle()
    var showRecon by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    // 汇总卡 A/B（2026-09-23 用户要求对比）：长按卡片头部在「完整 ⇄ 精简」间切；会话内保留
    var compactCard by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<PayGroup, PayMonthItem>?>(null) }
    var adding by remember { mutableStateOf<PayGroup?>(null) }
    // T1-3：同步/导入结果 Snackbar（docs/15）：浅色悬浮胶囊（与关于页检查更新同范式）
    val opMessage by vm.messageFlow.collectAsStateWithLifecycle()
    val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
        message = opMessage,
        onClear = vm::clearMessage,
    )
    // 切换版式（用户要求：不再弹悬浮提示，直接看形变动画）
    val onToggleCompact: () -> Unit = { compactCard = !compactCard }
    val prevNet = prevSheet
        ?.takeIf { it.netCents != 0L || it.incomeCents != 0L }
        ?.netCents

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                .padding(horizontal = Spacing.page),
        ) {
            Spacer(Modifier.height(Spacing.m))

            // ---- 月份导航（标题可点 → 月份选择器；非本月时旁边给「回本月」）----
            val isCurrentMonth = month == YearMonth.now()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::prevMonth) {
                    Icon(painterResource(R.drawable.ic_ms_keyboard_arrow_left), stringResource(R.string.paymonth_prev_cd))
                }
                Row(
                    Modifier
                        .widthIn(min = 120.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable { showMonthPicker = true }
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.paymonth_month, month.year, month.monthValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(
                        painterResource(R.drawable.ic_ms_expand_more),
                        contentDescription = stringResource(R.string.paymonth_picker_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSpec.inline),
                    )
                }
                if (!isCurrentMonth) {
                    Spacer(Modifier.width(Spacing.xs))
                    AssistChip(
                        onClick = vm::goToCurrentMonth,
                        label = {
                            Text(
                                stringResource(R.string.paymonth_this_month),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
                IconButton(onClick = vm::nextMonth) {
                    Icon(painterResource(R.drawable.ic_ms_keyboard_arrow_right), stringResource(R.string.paymonth_next_cd))
                }
            }
            Spacer(Modifier.height(Spacing.s))

            // 汇总卡（docs/20 P0-2）：实发大字 + 构成三行 + 「同步本月考勤」主操作。
            // 「同步」原先长在「基本项目」卡内部——页面级操作不该挂在分组列表里（docs/20 §4）。
            // 汇总卡（docs/20 P0-2）：实发大字 + 构成 + 考勤摘要。
            // 两个版式并列（长按卡片头部切换）：完整版=信息全，精简版=高度约一半（用户 A/B 对比用）
            PayMonthSummaryCard(
                sheet = sheet,
                attendance = attendance,
                prevNetCents = prevNet,
                period = month,
                compact = compactCard,
                onToggleCompact = onToggleCompact,
                onOpenDetail = onOpenDetail,
                onOpenReconcile = { showRecon = true },
            )
            // 页面级两个动作**并排**（2026-09-23 用户定：导入上月回到同步旁边）：
            // 两者同为 Tonal（对称、都不与卡里的金额抢主色，卡片保持纯信息展示）；
            // 工地模式无引擎值 → 同步隐藏，只留导入
            Spacer(Modifier.height(Spacing.m))
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isSite) {
                    InlineConfirmButton(
                        idleText = stringResource(R.string.paymonth_sync),
                        confirmText = stringResource(R.string.paymonth_sync_confirm_action),
                        cancelText = stringResource(R.string.paymonth_cancel),
                        undoText = stringResource(R.string.paymonth_undo),
                        onConfirm = vm::syncFromRecords,
                        onUndo = vm::undoSync,
                        resetKey = month,
                        style = InlineConfirmStyle.Tonal,
                    )
                }
                InlineConfirmButton(
                    idleText = stringResource(R.string.paymonth_import_prev),
                    confirmText = stringResource(R.string.paymonth_import_confirm_action),
                    cancelText = stringResource(R.string.paymonth_cancel),
                    undoText = stringResource(R.string.paymonth_undo),
                    onConfirm = vm::importPrevMonth,
                    onUndo = vm::undoImport,
                    resetKey = month,
                    style = InlineConfirmStyle.Tonal,
                )
            }
            Spacer(Modifier.height(Spacing.m))
            PayGroupCard(
                PayGroup.BASIC, sheet.basic,
                collapsed = PayGroup.BASIC.name in collapsedGroups,
                onToggleCollapse = { vm.toggleCollapsed(PayGroup.BASIC) },
                onEdit = { editing = PayGroup.BASIC to it },
                onAdd = { adding = PayGroup.BASIC },
            )
            Spacer(Modifier.height(Spacing.m))
            PayGroupCard(
                PayGroup.SUBSIDY, sheet.subsidy,
                collapsed = PayGroup.SUBSIDY.name in collapsedGroups,
                onToggleCollapse = { vm.toggleCollapsed(PayGroup.SUBSIDY) },
                onEdit = { editing = PayGroup.SUBSIDY to it },
                onAdd = { adding = PayGroup.SUBSIDY },
            )
            Spacer(Modifier.height(Spacing.m))
            PayGroupCard(
                PayGroup.DEDUCTION, sheet.deduction,
                collapsed = PayGroup.DEDUCTION.name in collapsedGroups,
                onToggleCollapse = { vm.toggleCollapsed(PayGroup.DEDUCTION) },
                onEdit = { editing = PayGroup.DEDUCTION to it },
                onAdd = { adding = PayGroup.DEDUCTION },
            )
            Spacer(Modifier.height(Spacing.m))
            PayGroupCard(
                PayGroup.OTHER, sheet.other,
                collapsed = PayGroup.OTHER.name in collapsedGroups,
                onToggleCollapse = { vm.toggleCollapsed(PayGroup.OTHER) },
                // 「个人所得税」行 → 独立个税估算页（docs/20 P3-5）
                onEdit = { item ->
                    if (item.id == PayMonthSheet.TAX_ROW_ID) onOpenTax() else editing = PayGroup.OTHER to item
                },
                onAdd = { adding = PayGroup.OTHER },
            )
            Spacer(Modifier.height(Spacing.l))
        }

        MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
    editing?.let { (group, item) ->
        // 社保 / 公积金行：比例与基数就在**它自己的弹窗**里设（工资设定页不再放这张卡，见 docs/20）
        val insurance = when (item.id) {
            PayMonthSheet.SOCIAL_ROW_ID -> InsuranceEditUi(
                social = true,
                rateBp = salary.socialInsuranceRateBp,
                baseCents = salary.socialInsuranceBaseCents,
                baseSalaryCents = salary.baseSalaryCents,
            )
            PayMonthSheet.FUND_ROW_ID -> InsuranceEditUi(
                social = false,
                rateBp = salary.housingFundRateBp,
                baseCents = salary.housingFundBaseCents,
                baseSalaryCents = salary.baseSalaryCents,
            )
            else -> null
        }
        EditItemDialog(
            item = item,
            insurance = insurance,
            insurancePreview = { bp, base ->
                // 走引擎口径（硬规则 12）；只改动本行对应的那一项，其余沿用当前设定
                val cfg = if (insurance?.social == true) {
                    salary.copy(socialInsuranceRateBp = bp, socialInsuranceBaseCents = base)
                } else {
                    salary.copy(housingFundRateBp = bp, housingFundBaseCents = base)
                }
                if (insurance?.social == true) PayrollCalculator.socialInsuranceCents(cfg)
                else PayrollCalculator.housingFundCents(cfg)
            },
            onSave = { updated, rateBp, baseCents ->
                if (insurance != null) vm.saveInsurance(group, updated, insurance.social, rateBp, baseCents)
                else vm.saveItem(group, updated)
                editing = null
            },
            onDelete = {
                vm.removeItem(group, item.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (showMonthPicker) {
        MonthPickDialog(
            title = stringResource(R.string.paymonth_picker_title),
            selected = month,
            onPick = { vm.goToMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false },
        )
    }
    if (showRecon) {
        ReconciliationDialog(
            sheet = sheet,
            onRestoreOne = vm::restoreEngineValue,
            onRestoreAll = vm::restoreAllEngineValues,
            onDismiss = { showRecon = false },
        )
    }
    adding?.let { group ->
        AddItemDialog(
            presets = when (group) {
                PayGroup.BASIC -> stringArrayResource(R.array.paymonth_basic_presets).toList()
                PayGroup.SUBSIDY -> stringArrayResource(R.array.paymonth_subsidy_presets).toList()
                PayGroup.DEDUCTION -> stringArrayResource(R.array.paymonth_deduction_presets).toList()
                PayGroup.OTHER -> stringArrayResource(R.array.paymonth_other_presets).toList()
            },
            onSave = { names, cents ->
                vm.addItems(group, names, cents)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
}

/**
 * 汇总卡（docs/20 P0-2）：实发大字 + 应发/扣款/其他构成 + 可选主操作。
 * 之前全页只有四个分组小计、没有总数，而扣款/其他两组还是负数展示，用户得心算四项——
 * 工资单最核心的数字不应该让用户自己算。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayMonthSummaryCard(
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
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
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
                            painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.size(IconSpec.inline),
                        )
                    }
                }
            }

            // 空态引导（docs/20 P2-7）：考勤有记录、但单据还是空的 → 先同步，别让人手工从头填（仅完整形态）
            AnimatedVisibility(
                visible = !compact && attendance?.isEmpty == false &&
                    sheet.incomeCents == 0L && sheet.deductionCents == 0L && sheet.otherCents == 0L,
                enter = fadeIn(morph) + expandVertically(morphSize),
                exit = fadeOut(morph) + shrinkVertically(morphSize),
            ) {
                Text(
                    stringResource(R.string.paymonth_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            // 对账入口（docs/20 P1-2）：同步过的行被手改 → 与引擎值有差，点看明细（两形态都有）
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(Spacing.xs)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(elapsedDays.toFloat() / totalDays)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)),
                            )
                        }
                    }
                }
            }
        }
    }
}


/** 图例圆点直径（6dp）：纯图形装饰、非间距语义，就地常量 */
private val LegendDot = 6.dp

/**
 * 应发构成占比条（0 段不画；全 0 则整块不出）[showLegend] = false 时不带图例（精简卡用）。
 * 图例给「名称 + 百分比」而不只给颜色——不让颜色单独承担含义（配色与分组卡同源）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SliceBar(
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
            .clip(RoundedCornerShape(Radius.pill)),
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
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
private fun sliceColor(kind: IncomeSliceKind): Color = when (kind) {
    IncomeSliceKind.BASE -> MaterialTheme.colorScheme.onPrimaryContainer
    IncomeSliceKind.OVERTIME -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
    IncomeSliceKind.OTHER_INCOME -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f)
}

private fun sliceLabelRes(kind: IncomeSliceKind): Int = when (kind) {
    IncomeSliceKind.BASE -> R.string.paymonth_slice_base
    IncomeSliceKind.OVERTIME -> R.string.paymonth_slice_overtime
    IncomeSliceKind.OTHER_INCOME -> R.string.paymonth_slice_other
}

/**
 * 汇总卡里的一个数据格（三段数据条）：标签小字在上、金额在下。
 * 扣款/其他带「−」前缀表示它们是从应发里减掉的，0 值淡一档（不喧宾夺主）。
 */
@Composable
private fun SumCell(label: String, cents: Long, negative: Boolean, modifier: Modifier = Modifier) {
    val zero = cents == 0L
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            (if (negative && !zero) "−" else "") + Money.yuanTrimText(cents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (zero) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = if (zero) null else FontWeight.SemiBold,
        )
    }
}

/** 摘要徽章：卡片底上的浅层小胶囊（读一眼「这个月干了多少 / 记了几天」） */
@Composable
private fun AttendanceBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/**
 * 考勤摘要徽章：加班总时长 + （仅当多于一个档位时）分档明细 + 请假 + 记录天数。
 * 只有一个档位时不再列分档（旧文案会写成「加班 4小时（平日 4小时）」，重复又绕）。
 */
@Composable
private fun attendanceBadges(a: PayrollCalculator.AttendanceSummary): List<String> = buildList {
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
private fun SummaryCardHeader(
    net: Long,
    prevNetCents: Long?,
    compact: Boolean,
    onToggleCompact: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.small))
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
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        // 环比上月（上月没填过则不显示）：小药丸 + ↑↓ 方向（↓ 偏 error 色提醒）
        prevNetCents?.let { prev ->
            val delta = net - prev
            val down = delta < 0
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (down) "↓" else "↑",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (down) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_mom, signedYuanText(delta)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
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
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
    }
}


/** 对账入口行（两个版式共用；出现时轻微放大淡入） */
@Composable
private fun ReconRow(recon: List<Reconciliation>, onOpenReconcile: () -> Unit) {
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(IconSpec.inline),
            )
        }
    }
}

/** 带符号金额文本：+320 / -86.5（环比与对账差额用） */
private fun signedYuanText(cents: Long): String =
    if (cents >= 0) "+${Money.yuanTrimText(cents)}" else "-${Money.yuanTrimText(-cents)}"

/** 对账明细（docs/20 P1-2 + P2-2 修订）：逐行「引擎值 → 现值（差）」，每行可**一键恢复引擎值**。
 *
 * 刻意不做全局「覆盖 / 仅填空」开关——按行决定既尊重手改，又不会丢掉引擎值。
 */
@Composable
private fun ReconciliationDialog(
    sheet: PayMonthSheet,
    onRestoreOne: (PayGroup, Long) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = sheet.reconciliations()
    AlertDialog(
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
                TextButton(onClick = { onRestoreAll(); onDismiss() }) {
                    Text(stringResource(R.string.paymonth_recon_restore_all))
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_close)) }
        },
    )
}

@Composable
private fun PayGroupCard(
    group: PayGroup,
    items: List<PayMonthItem>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onEdit: (PayMonthItem) -> Unit,
    onAdd: (() -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val expanded = !collapsed
    val total = items.sumOf { it.amountCents }
    val negative = group == PayGroup.DEDUCTION || group == PayGroup.OTHER
    val container = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.primary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.tertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.error
        PayGroup.OTHER -> MaterialTheme.colorScheme.secondary
    }
    val onContainer = when (group) {
        PayGroup.BASIC -> MaterialTheme.colorScheme.onPrimary
        PayGroup.SUBSIDY -> MaterialTheme.colorScheme.onTertiary
        PayGroup.DEDUCTION -> MaterialTheme.colorScheme.onError
        PayGroup.OTHER -> MaterialTheme.colorScheme.onSecondary
    }
    val labelRes = when (group) {
        PayGroup.BASIC -> R.string.paymonth_group_basic
        PayGroup.SUBSIDY -> R.string.paymonth_group_subsidy
        PayGroup.DEDUCTION -> R.string.paymonth_group_deduction
        PayGroup.OTHER -> R.string.paymonth_group_other
    }
    val glyph = when (group) {
        PayGroup.BASIC -> "¥"
        PayGroup.SUBSIDY -> "+"
        else -> "−"
    }
    val addRes = when (group) {
        PayGroup.BASIC -> R.string.paymonth_add_basic
        PayGroup.SUBSIDY -> R.string.paymonth_add_subsidy
        PayGroup.DEDUCTION -> R.string.paymonth_add_deduction
        PayGroup.OTHER -> R.string.paymonth_add_other
    }

    SectionCard {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, style = MaterialTheme.typography.labelLarge, color = onContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AnimatedNumberText(
                    value = total,
                    text = { cents -> if (negative) "-${Money.yuanTrimText(cents)}" else Money.yuanTrimText(cents) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    label = "payGroupTotal",
                )
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    painterResource(if (expanded) R.drawable.ic_ms_expand_less else R.drawable.ic_ms_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                items.forEachIndexed { index, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(item) }
                            .padding(vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (negative) "-${Money.yuanTrimText(item.amountCents)}" else Money.yuanTrimText(item.amountCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                            )
                            // 金额来源（docs/20 P0-3）：引擎算的与手填的分得出来；
                            // 有推导依据的（如「调休折现」）额外附上依据，自动值可解释
                            item.source?.let { src ->
                                Spacer(Modifier.height(Spacing.xs))
                                val derivation = when {
                                    item.derivationMinutes != null -> stringResource(
                                        R.string.paymonth_derivation_comp,
                                        TimeUtils.prettyDuration(item.derivationMinutes),
                                    )
                                    item.derivationBaseCents != null && item.derivationRateBp != null -> stringResource(
                                        R.string.paymonth_derivation_rate,
                                        Money.yuanTrimText(item.derivationBaseCents),
                                        Money.ratePercentText(item.derivationRateBp),
                                    )
                                    else -> null
                                }
                                Text(
                                    when (src) {
                                        PayMonthSource.SYNCED -> if (derivation == null) {
                                            stringResource(
                                                R.string.paymonth_source_synced,
                                                syncedDayLabel(item.syncedAt),
                                            )
                                        } else {
                                            stringResource(
                                                R.string.paymonth_source_synced_derived,
                                                syncedDayLabel(item.syncedAt),
                                                derivation,
                                            )
                                        }
                                        PayMonthSource.EDITED -> stringResource(
                                            R.string.paymonth_source_edited,
                                            Money.yuanTrimText(item.engineCents ?: 0L),
                                        )
                                        PayMonthSource.ESTIMATED -> stringResource(
                                            R.string.paymonth_source_estimated,
                                            syncedDayLabel(item.syncedAt),
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSpec.inline),
                        )
                    }
                }
                onAdd?.let {
                    TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(addRes))
                    }
                }
                extraAction?.invoke()
            }
        }
    }
}

/** "2026-09-22" → "9/22"；解析失败或缺失则返回空串（旧数据/异常值不崩） */
private fun syncedDayLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = java.time.LocalDate.parse(iso)
        "${d.monthValue}/${d.dayOfMonth}"
    }.getOrDefault(iso)
}

/** 编辑条目：出厂行只可改金额，新增行可改名称/金额/删除。
 *
 * 注意：**不提供「天数 × 日薪」这类手算入口**——能自动算的（如「调休折现」）由「同步本月考勤」
 * 按引擎口径自动回填（[PayrollCalculator.compCashCents]），用户只在结果不对时直接改金额（docs/20 设计原则）。
 */
/** 社保个人比例预设（基点）：不算 / 仅养老 8% / 北京广州等 10.2% / 多数城市 10.5% */
private val SOCIAL_RATE_PRESETS = listOf(0, 800, 1020, 1050)

/** 公积金个人比例预设（基点）：法定区间 5%–12%，取常见档 */
private val FUND_RATE_PRESETS = listOf(0, 500, 700, 800, 1000, 1200)

/**
 * 社保/公积金行的弹窗附加设置（docs/20：**设置放进它自己那一行的弹窗**——
 * 工资设定页签内容太多，那边不再放这张卡）。比例用预设选项、基数默认跟随底薪。
 */
private data class InsuranceEditUi(
    val social: Boolean,
    /** 当前比例（基点，0 = 不算） */
    val rateBp: Int,
    /** 当前缴费基数（分）；0 = 跟随底薪 */
    val baseCents: Long,
    /** 底薪（跟随时的基数；未设则为 0） */
    val baseSalaryCents: Long,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditItemDialog(
    item: PayMonthItem,
    insurance: InsuranceEditUi? = null,
    /** 预览金额：由调用方用引擎函数算（硬规则 12：UI 不另写公式） */
    insurancePreview: (Int, Long) -> Long = { _, _ -> 0L },
    onSave: (PayMonthItem, Int, Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(Money.yuanTrimText(item.amountCents)) }
    val cents = Money.parseYuanToCents(amount)
    // ---- 社保/公积金设置态（选项优先：不让用户去查比例表）----
    val presets = if (insurance?.social == true) SOCIAL_RATE_PRESETS else FUND_RATE_PRESETS
    var rateBp by remember { mutableStateOf(insurance?.rateBp ?: 0) }
    var rateCustom by remember {
        mutableStateOf(insurance != null && insurance.rateBp > 0 && insurance.rateBp !in presets)
    }
    var rateText by remember { mutableStateOf("") }
    var baseFollows by remember { mutableStateOf(insurance == null || insurance.baseCents <= 0) }
    var baseText by remember {
        mutableStateOf(
            if (insurance != null && insurance.baseCents > 0) Money.yuanTrimText(insurance.baseCents) else ""
        )
    }
    fun effRate(): Int =
        if (rateCustom) ((rateText.toDoubleOrNull() ?: 0.0) * 100).toInt().coerceIn(0, 10_000) else rateBp

    fun effBase(): Long =
        if (baseFollows) (insurance?.baseSalaryCents ?: 0L) else (Money.parseYuanToCents(baseText) ?: 0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_edit_title)) },
        text = {
            Column {
                if (!item.builtin) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.paymonth_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.textField),
                    )
                    Spacer(Modifier.height(Spacing.s))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.paymonth_amount_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                    isError = cents == null,
                )

                // ---- 社保 / 公积金的「比例 + 基数」就设在它自己的行弹窗里 ----
                insurance?.let { ins ->
                    Spacer(Modifier.height(Spacing.m))
                    Text(
                        stringResource(if (ins.social) R.string.payroll_social_title else R.string.payroll_fund_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        presets.forEach { bp ->
                            FilterChip(
                                selected = !rateCustom && rateBp == bp,
                                onClick = {
                                    rateBp = bp
                                    rateCustom = false
                                    // 改了比例就把金额按新口径算出来（自动优先，仍可手改）
                                    amount = Money.yuanTrimText(insurancePreview(bp, effBase()))
                                },
                                label = {
                                    Text(
                                        if (bp == 0) stringResource(R.string.payroll_rate_off)
                                        else stringResource(
                                            R.string.payroll_rate_percent,
                                            Money.ratePercentText(bp),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                        FilterChip(
                            selected = rateCustom,
                            onClick = { rateCustom = true },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_rate_custom),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                    if (rateCustom) {
                        Spacer(Modifier.height(Spacing.s))
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = {
                                rateText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = { Text(stringResource(R.string.payroll_rate_custom_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(Radius.textField),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        stringResource(if (ins.social) R.string.payroll_social_hint else R.string.payroll_fund_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = baseFollows,
                            onClick = {
                                baseFollows = true
                                amount = Money.yuanTrimText(insurancePreview(effRate(), ins.baseSalaryCents))
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Text(
                                stringResource(R.string.payroll_base_follow),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        SegmentedButton(
                            selected = !baseFollows,
                            onClick = { baseFollows = false },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text(
                                stringResource(R.string.payroll_base_custom),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (!baseFollows) {
                        Spacer(Modifier.height(Spacing.s))
                        OutlinedTextField(
                            value = baseText,
                            onValueChange = {
                                baseText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = { Text(stringResource(R.string.payroll_insurance_base_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(Radius.textField),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (effRate() > 0) {
                            stringResource(
                                R.string.paymonth_insurance_formula,
                                Money.yuanTrimText(effBase()),
                                Money.ratePercentText(effRate()),
                                Money.yuanTrimText(insurancePreview(effRate(), effBase())),
                            )
                        } else {
                            stringResource(R.string.payroll_insurance_preview_off)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && (item.builtin || name.isNotBlank()),
                onClick = {
                    onSave(
                        item.copy(name = if (item.builtin) item.name else name.trim(), amountCents = cents ?: 0),
                        effRate(),
                        if (baseFollows) 0L else (Money.parseYuanToCents(baseText) ?: 0L),
                    )
                },
            ) { Text(stringResource(R.string.paymonth_save)) }
        },
        dismissButton = {
            Row {
                if (!item.builtin) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.paymonth_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_cancel)) }
            }
        },
    )
}

/**
 * 添加条目：**预选项多选一次添加**（docs/20 P2-3），或手填一条自定义。
 *
 * 选项优先（硬规则 12）：常用工资项都在预设里点一下；只在预设没有时才敲名称。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddItemDialog(
    presets: List<String>,
    onSave: (List<String>, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var amount by remember { mutableStateOf("") }
    val cents = if (amount.isBlank()) 0L else Money.parseYuanToCents(amount)
    val names = if (picked.isNotEmpty()) picked.toList() else listOf(custom.trim()).filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it; if (it.isNotBlank()) picked = emptySet() },
                    label = { Text(stringResource(R.string.paymonth_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                )
                Spacer(Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.paymonth_amount_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                    isError = amount.isNotBlank() && cents == null,
                )
                Spacer(Modifier.height(Spacing.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    presets.forEach { preset ->
                        val on = preset in picked
                        FilterChip(
                            selected = on,
                            onClick = {
                                picked = if (on) picked - preset else picked + preset
                                if (!on) custom = ""
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_add_multi_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = names.isNotEmpty() && cents != null,
                onClick = { onSave(names, cents ?: 0L) },
            ) {
                Text(
                    if (names.size > 1) stringResource(R.string.paymonth_add_count, names.size)
                    else stringResource(R.string.paymonth_save),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_cancel)) }
        },
    )
}
