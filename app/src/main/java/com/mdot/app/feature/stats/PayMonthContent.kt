package com.mdot.app.feature.stats

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.EngineIcons
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import com.mdot.app.core.navigation.contentBottomPadding
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.MonthPickDialog
import com.mdot.app.core.designsystem.component.MonthStepper
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.IncomeSliceKind
import com.mdot.app.domain.model.InsuranceKind
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.PayMonthDayBasis
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.rowsOf
import com.mdot.app.domain.model.insuranceKindOf
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.reconciliations
import com.mdot.app.core.designsystem.ButtonSpec
import com.mdot.app.domain.model.PayMonthSource
import com.mdot.app.domain.model.Reconciliation
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.YearMonth
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanSwitch
import com.mdot.app.core.designsystem.component.JiabanIconButton
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.heroChipSurface
import com.mdot.app.core.designsystem.jiabanFilterChipColors

/**
 * 记月页（统计页第 1 页）：月度工资单编辑。金额走**实时预览**（与首页数据区同款）：
 * 引擎算得出的行随当前考勤/薪资实时出数、手改/手填行保留——没有「同步」动作（2026-09-28 起）。
 * 布局仿设计稿：四分组卡（基本/补贴/扣款/其他，头部合计 + 折叠），行点击编辑金额，
 * 补贴/扣款组可添加行（出厂行不可删），底部「导入上月」（原尺寸、居中）。
 */
@Composable
fun PayMonthContent(
    /** 摘要条/空态点击 → 跳到「明细」页签（由统计页控制 pager，故回调注入） */
    onOpenDetail: () -> Unit = {},
    /** 「个人所得税」行 → 个税估算页 */
    onOpenTax: () -> Unit = {},
    /** 底栏形态（统计页一级页签时 true）：内容底部避让底栏——否则最底部内容被底栏挡住无法点击 */
    showBottomBar: Boolean = false,
    vm: PayMonthViewModel = hiltViewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    // 展示一律读**实时预览**单据（vm.displaySheet）：引擎行走当前考勤/薪资实时值、手改/手填行保留，
    // 随记随更新——与首页数据区「实发工资」同款推导（2026-09-28 用户要求，「同步本月考勤」按钮已移除）
    val sheet by vm.displaySheet.collectAsStateWithLifecycle()
    val customPresets by vm.customPresets.collectAsStateWithLifecycle()
    val attendance by vm.attendance.collectAsStateWithLifecycle()
    val collapsedGroups by vm.collapsed.collectAsStateWithLifecycle()
    val prevSheet by vm.prevSheet.collectAsStateWithLifecycle()
    val salary by vm.salary.collectAsStateWithLifecycle()
    var showRecon by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    // 汇总卡 A/B（2026-09-23 用户要求对比）：点卡片头部在「完整 ⇄ 精简」间切；会话内保留
    // 默认**精简（收起）**（2026-10-08 用户要求）：先进页面先看结论，需要构成/考勤摘要再点开
    var compactCard by rememberSaveable { mutableStateOf(true) }
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
        .takeIf { it.netCents != 0L || it.incomeCents != 0L }
        ?.netCents

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .contentBottomPadding(showBottomBar = showBottomBar)
                .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                .padding(horizontal = Spacing.page),
        ) {
            // 上下留白统一取 Spacing.s（较小值）——与统计页/明细页的胶囊行完全同款，
            // 三页签来回切时胶囊与下方卡片都不跳动。改这里请同步改那两页。
            // 顶部不加 Spacer：宿主页签条（SegmentBar 撑满 64dp 顶栏）下方已有天然留白。
            // 下方留白用 StatsRangeSpacing 与上方对齐（见该常量注释）。

            // ---- 月份导航（与明细页共用 MonthStepper：同功能必须同实现，禁止两页各画一套）----
            val isCurrentMonth = month == YearMonth.now()
            MonthStepper(
                label = stringResource(R.string.paymonth_month, month.year, month.monthValue),
                nextEnabled = true,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onOpenPicker = { showMonthPicker = true },
                onBackToCurrent = if (isCurrentMonth) null else vm::goToCurrentMonth,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(StatsRangeSpacing))

            // 汇总卡（docs/20 P0-2）：实发大字 + 构成 + 考勤摘要（金额随实时预览走，随记随更新）。
            // 两个版式并列（点头部切换）：完整版=信息全，精简版=高度约一半（用户 A/B 对比用）
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
            // 页面级动作（2026-09-28：「同步本月考勤」移除——单据按考勤实时出数，无需手动同步）：
            // 「导入上月」保持原尺寸、居中（Tonal，不与卡里的金额抢主色，卡片保持纯信息展示）
            Spacer(Modifier.height(Spacing.m))
            InlineConfirmButton(
                idleText = stringResource(R.string.paymonth_import_prev),
                confirmText = stringResource(R.string.paymonth_import_confirm_action),
                cancelText = stringResource(R.string.paymonth_cancel),
                undoText = stringResource(R.string.paymonth_undo),
                onConfirm = vm::importPrevMonth,
                onUndo = vm::undoImport,
                resetKey = month,
                style = InlineConfirmStyle.Tonal,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
                    // ⚠️ 行 id 只在**组内**唯一，故叠上 builtin 判定（用户行永不是 builtin）——
                    // 只看 id 会被用户行撞上（同下方 insurance 判定，2026-09-27 用户报的 bug）
                    if (item.builtin && item.id == PayMonthSheet.TAX_ROW_ID) onOpenTax()
                    else editing = PayGroup.OTHER to item
                },
                onAdd = { adding = PayGroup.OTHER },
            )
            Spacer(Modifier.height(Spacing.l))
        }

        MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
    editing?.let { (group, item) ->
        // 社保 / 公积金行：比例与基数就在**它自己的弹窗**里设（工资设定页不再放这张卡，见 docs/20）
        // 「社保 / 公积金」设置放进它自己那一行的弹窗（docs/20）。
        // 辨识走 domain 的 insuranceKindOf——行 id 只在**组内**唯一、跨组会撞车：
        // 扣款组用户加的两行就拿到 id 8/9（= SOCIAL/FUND_ROW_ID），只看 id 时会误弹社保/公积金设置
        val insurance = when (item.insuranceKindOf(group)) {
            InsuranceKind.SOCIAL -> InsuranceEditUi(
                social = true,
                rateBp = salary.socialInsuranceRateBp,
                baseCents = salary.socialInsuranceBaseCents,
                baseSalaryCents = salary.baseSalaryCents,
            )
            InsuranceKind.FUND -> InsuranceEditUi(
                social = false,
                rateBp = salary.housingFundRateBp,
                baseCents = salary.housingFundBaseCents,
                baseSalaryCents = salary.baseSalaryCents,
            )
            null -> null
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
        val builtin = when (group) {
            PayGroup.BASIC -> stringArrayResource(R.array.paymonth_basic_presets).toList()
            PayGroup.SUBSIDY -> stringArrayResource(R.array.paymonth_subsidy_presets).toList()
            PayGroup.DEDUCTION -> stringArrayResource(R.array.paymonth_deduction_presets).toList()
            PayGroup.OTHER -> stringArrayResource(R.array.paymonth_other_presets).toList()
        }
        AddItemDialog(
            // 写死的常用项 + 用户自建（自建在后，是用户自己的习惯清单）。
            // ⚠️ distinct 不能省：自建预设若与内置项重名（如自己又建一个「餐补」）会出两个一模一样的 chip
            presets = (builtin + customPresets.of(group)).distinct(),
            existingNames = sheet.rowsOf(group).mapTo(HashSet()) { it.name },
            customPresetNames = customPresets.of(group),
            onCreatePreset = { vm.addCustomPreset(group, it) },
            onDeletePreset = { vm.removeCustomPreset(group, it) },
            onSave = { names ->
                vm.addItems(group, names)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
}

/**
 * 汇总卡（docs/20 P0-2）：实发大字 + 应发/扣款/其他构成。
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
private fun sliceColor(kind: IncomeSliceKind): Color = when (kind) {
    IncomeSliceKind.BASE -> emphasisCardInk()
    IncomeSliceKind.OVERTIME -> emphasisCardInk().copy(alpha = 0.5f)
    IncomeSliceKind.OTHER_INCOME -> emphasisCardInk().copy(alpha = 0.22f)
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
private fun AttendanceBadge(text: String) {
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
                    // ⚠️ 0 不带负号：扣款/其他组默认显示 "-0"（2026-10-09 记月页巡查发现），
                    // 零就是零。与 [SumCell] 同一口径（那里是 `negative && !zero`）
                    text = { cents ->
                        if (negative && cents != 0L) "-${Money.yuanTrimText(cents)}" else Money.yuanTrimText(cents)
                    },
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
                                // ⚠️ 0 不带负号（与分组小计、[SumCell] 同一口径）：否则空行显示 "-0"
                                if (negative && item.amountCents != 0L) "-${Money.yuanTrimText(item.amountCents)}"
                                else Money.yuanTrimText(item.amountCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                            )
                            // 金额来源（docs/20 P0-3）：引擎算的与手填的分得出来；
                            // 有推导依据的（如「调休折现」）额外附上依据，自动值可解释
                            item.source?.let { src ->
                                Spacer(Modifier.height(Spacing.xs))
                                val derivation = when {
                                    // 按日计算：最具体的依据，优先展示（「21.5 天 × ¥50」）
                                    item.derivationDays != null && item.derivationUnitCents != null -> stringResource(
                                        R.string.paymonth_daily_derivation,
                                        // 天数去掉无意义的小数尾巴：整数显示 21，半点显示 21.5
                                        Money.dayCountText(item.derivationDays),
                                        Money.yuanTrimText(item.derivationUnitCents),
                                    )
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
                                        // 实时预览下引擎值随考勤实时算（不存在「哪天同步的」），口径是「自动计算」
                                        PayMonthSource.SYNCED -> if (derivation == null) {
                                            stringResource(R.string.paymonth_source_synced)
                                        } else {
                                            stringResource(R.string.paymonth_source_synced_derived, derivation)
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
                            EngineIcons.chevron(),
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
 * 注意：**不提供「天数 × 日薪」这类手算入口**——能自动算的（如「调休折现」）由引擎口径自动出数
 * （[PayrollCalculator.compCashCents]，实时预览），用户只在结果不对时直接改金额（docs/20 设计原则）。
 */
/** 社保个人比例预设（基点）：不算 / 仅养老 8% / 北京广州等 10.2% / 多数城市 10.5% */
private val SOCIAL_RATE_PRESETS = listOf(0, 800, 1020, 1050)

/** 公积金个人比例预设（基点）：法定区间 5%–12%，取常见档 */
private val FUND_RATE_PRESETS = listOf(0, 500, 700, 800, 1000, 1200)

/** 「按日计算」天数口径的展示顺序（应出勤 → 记录 → 自然日，从最推荐到最粗） */
private val DAY_BASIS_ORDER = listOf(
    PayMonthDayBasis.STANDARD,
    PayMonthDayBasis.RECORD,
    PayMonthDayBasis.CALENDAR,
)

/** 天数口径的文案（选项名 + 一句说明它怎么算、坑在哪） */
private val PayMonthDayBasis.labelRes: Int
    get() = when (this) {
        PayMonthDayBasis.STANDARD -> R.string.paymonth_daily_basis_standard
        PayMonthDayBasis.RECORD -> R.string.paymonth_daily_basis_record
        PayMonthDayBasis.CALENDAR -> R.string.paymonth_daily_basis_calendar
    }

private val PayMonthDayBasis.hintRes: Int
    get() = when (this) {
        PayMonthDayBasis.STANDARD -> R.string.paymonth_daily_hint_standard
        PayMonthDayBasis.RECORD -> R.string.paymonth_daily_hint_record
        PayMonthDayBasis.CALENDAR -> R.string.paymonth_daily_hint_calendar
    }

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
    // ---- 「按日计算」态（v0.7.8.4：行级属性；只有「其它补贴」与用户自加行能开，见 supportsDailyRate）----
    var dailyOn by remember { mutableStateOf(item.isDailyComputed) }
    var dailyUnit by remember {
        mutableStateOf(if (item.dayRateCents > 0) Money.yuanTrimText(item.dayRateCents) else "")
    }
    var dailyBasis by remember { mutableStateOf(item.dayBasis) }
    var dailyDeductLeave by remember { mutableStateOf(item.dayDeductLeave) }
    val dailyUnitCents = Money.parseYuanToCents(dailyUnit)
    fun effRate(): Int =
        if (rateCustom) ((rateText.toDoubleOrNull() ?: 0.0) * 100).toInt().coerceIn(0, 10_000) else rateBp

    fun effBase(): Long =
        if (baseFollows) (insurance?.baseSalaryCents ?: 0L) else (Money.parseYuanToCents(baseText) ?: 0L)
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_edit_title)) },
        text = {
            Column {
                if (!item.builtin) {
                    FloatingLabelTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.paymonth_name_label),
                    )
                    Spacer(Modifier.height(Spacing.s))
                }
                FloatingLabelTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.paymonth_amount_label),
                    isError = cents == null,
                    // 按日计算时金额是算出来的，改它没有意义 —— 置灰并提示改日单价
                    enabled = !dailyOn,
                )
                if (dailyOn) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.paymonth_daily_amount_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- 按日计算（行级属性）：开关 + 日单价 + 天数口径 + 请假开关 ----
                // 只在**允许**按日的行上显示——出厂固定行里只有「其它补贴」允许
                // （其余出厂项要么有确定算法、要么本身是配置项，见 supportsDailyRate 的说明）
                if (item.supportsDailyRate) {
                    Spacer(Modifier.height(Spacing.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.paymonth_daily_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        JiabanSwitch(checked = dailyOn, onCheckedChange = { dailyOn = it })
                    }
                    if (dailyOn) {
                        Spacer(Modifier.height(Spacing.s))
                        FloatingLabelTextField(
                            value = dailyUnit,
                            onValueChange = { dailyUnit = it },
                            label = stringResource(R.string.paymonth_daily_unit_label),
                            isError = dailyUnitCents == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.m))
                        Text(
                            stringResource(R.string.paymonth_daily_basis_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            DAY_BASIS_ORDER.forEach { basis ->
                                FilterChip(
                                    colors = jiabanFilterChipColors(),
                                    selected = dailyBasis == basis,
                                    onClick = { dailyBasis = basis },
                                    label = { Text(stringResource(basis.labelRes)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(dailyBasis.hintRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.paymonth_daily_deduct_leave),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            JiabanSwitch(
                                checked = dailyDeductLeave,
                                onCheckedChange = { dailyDeductLeave = it },
                            )
                        }
                    }
                }

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
                                                                colors = jiabanFilterChipColors(),
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
                                                        colors = jiabanFilterChipColors(),
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
                        FloatingLabelTextField(
                            value = rateText,
                            onValueChange = {
                                rateText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = stringResource(R.string.payroll_rate_custom_label),
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
                    // 次要选项口径（2026-09-28 定 / 2026-09-30 确认）：基数「跟随/自定义」→ 轻量 chip
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = baseFollows,
                            onClick = {
                                baseFollows = true
                                amount = Money.yuanTrimText(insurancePreview(effRate(), ins.baseSalaryCents))
                            },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_base_follow),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = !baseFollows,
                            onClick = { baseFollows = false },
                            label = {
                                Text(
                                    stringResource(R.string.payroll_base_custom),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                    if (!baseFollows) {
                        Spacer(Modifier.height(Spacing.s))
                        FloatingLabelTextField(
                            value = baseText,
                            onValueChange = {
                                baseText = it.filter { c -> c.isDigit() || c == '.' }
                                amount = Money.yuanTrimText(insurancePreview(effRate(), effBase()))
                            },
                            label = stringResource(R.string.payroll_insurance_base_label),
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
            JiabanButton(
                text = stringResource(R.string.paymonth_save),
                onClick = {
                    onSave(
                        item.copy(
                            name = if (item.builtin) item.name else name.trim(),
                            amountCents = cents ?: 0,
                            // 按日配置随行一起存；不允许按日的行强制清零（避免残留配置被引擎继续算）
                            dayRateCents = if (dailyOn && item.supportsDailyRate) (dailyUnitCents ?: 0L) else 0L,
                            dayBasis = dailyBasis,
                            dayDeductLeave = dailyDeductLeave,
                        ),
                        effRate(),
                        if (baseFollows) 0L else (Money.parseYuanToCents(baseText) ?: 0L),
                    )
                },
                role = JiabanButtonRole.GHOST,
                enabled = cents != null && (item.builtin || name.isNotBlank()),
            )
        },
        dismissButton = {
            Row {
                if (!item.builtin) {
                    JiabanButton(
                            text = stringResource(R.string.paymonth_delete),
                            onClick = onDelete,
                            role = JiabanButtonRole.GHOST,
                            contentColorOverride = MaterialTheme.colorScheme.error,
                        )
                }
                JiabanButton(
                    text = stringResource(R.string.paymonth_cancel),
                    onClick = onDismiss,
                    role = JiabanButtonRole.GHOST,
                )
            }
        },
    )
}

/**
 * 添加条目：**只选"加哪些项"**（预设多选 + 自定义名称），**不在这里填金额**。
 *
 * 选项优先（硬规则 12）：常用工资项都在预设里点一下；预设没有时才敲名称。
 *
 * 2026-10-09 改（用户反馈"逻辑不顺畅"）：
 * - **去掉金额输入框**：原先勾多项共用同一个金额（勾 3 项填 500 → 三行各 500），
 *   提示文案只能写"金额留空则为 0，之后逐条改"——把逐条定价做成批量操作再让用户返工。
 *   现在金额一律 0，用户到列表里点行填（那里本来就有行弹窗）；按日计算的行更不该在添加时填金额。
 * - **预设与自定义不再互斥**：以前敲名称会**静默清空**已选预设（反之亦然），
 *   用户先挑几项、再想补一个自定义名，前面的选择无声消失。现在两者可以同时用。
 * - **已存在的项置灰**：预设里可能与出厂固定行同名（如补贴组的「全勤奖」），
 *   点了会加出重复行；现在已有的直接禁用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddItemDialog(
    presets: List<String>,
    /** 本组**已有**的行名（预设命中即禁用，避免加出重复行） */
    existingNames: Set<String>,
    /** 本组**自建**的预设名（预设管理面板里可删的就是这些） */
    customPresetNames: List<String>,
    /** 新建一条自定义预设（持久化）；返回 true 表示真的写入了（可自动勾选） */
    onCreatePreset: suspend (String) -> Boolean,
    /** 删掉一条自定义预设（只从候选清单移除，不动已录入的行） */
    onDeletePreset: suspend (String) -> Boolean,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    // 只在点「+」时才出现的建预设弹窗（叠在覆盖层栈上）
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val names = picked.toList()
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_add_title)) },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    presets.forEach { preset ->
                        val alreadyThere = preset in existingNames
                        val on = preset in picked
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            // 已有同名行 → 不可选（置灰），否则会加出重复项
                            enabled = !alreadyThere,
                            selected = on,
                            onClick = { picked = if (on) picked - preset else picked + preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    // 「+」：自建一条预设并持久化——建一次，以后每个月点一下就出来。
                    // （原来是就地敲一个名称，但那只是个一次性行名，用户下次还得重敲）
                    FilterChip(
                        colors = jiabanFilterChipColors(),
                        selected = false,
                        onClick = { naming = true },
                        label = {
                            Text(
                                stringResource(R.string.paymonth_add_new_preset),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
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
            JiabanButton(
                text = if (names.size > 1) stringResource(R.string.paymonth_add_count, names.size)
                    else stringResource(R.string.paymonth_save),
                onClick = { onSave(names) },
                role = JiabanButtonRole.GHOST,
                enabled = names.isNotEmpty(),
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.paymonth_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
    if (naming) {
        ManagePresetsDialog(
            /** 本组自建的预设（可删的就是这些；内置项写死在 string-array 里，不在此列） */
            custom = customPresetNames,
            onCreate = { name ->
                scope.launch {
                    // 写入成功才勾选（同名/空名会被拒）
                    if (onCreatePreset(name)) picked = picked + name.trim()
                }
            },
            onDelete = { name -> scope.launch { onDeletePreset(name) } },
            onDismiss = { naming = false },
        )
    }
}

/**
 * 预设管理：**增与删放在同一处**（用户 2026-10-09 定：新增的预设原先没有删除入口）。
 *
 * 为什么不做成"chip 上挂个 ✕"或"长按删"：
 * - chip 上的 ✕ 紧挨点选区域 → 易误删，且自建项一多 chip 行会很吵；
 * - 长按在本项目已被否过一次（记月汇总卡曾用长按切换，用户反馈"不知道能长按"）。
 *
 * 删除走 [InlineConfirmButton] 的 Compact 式样（硬规则 11：原地确认、不弹二次确认框），
 * 与「班次管理」页删自定义班次同款。因为删完这一行就消失了，**只能"只确认"**（onUndo = null）。
 */
@Composable
private fun ManagePresetsDialog(
    custom: List<String>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    JiabanAlertDialog(
        containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paymonth_manage_preset_title)) },
        text = {
            Column {
                // ---- 新建 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⚠️ 不要给这个字段强压高度：M3 的 OutlinedTextField 内部按 56dp 排版，
                    // 压到 40dp 会把占位文字**垂直裁掉**（实测踩过两次）。这里旁边是按钮不是 chip，
                    // 没有"要和 chip 同量纲"的问题，用自然高度即可。
                    FloatingLabelTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "",
                        placeholder = stringResource(R.string.paymonth_add_custom_label),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    JiabanButton(
                        text = stringResource(R.string.paymonth_add_preset_action),
                        onClick = {
                            onCreate(name)
                            name = ""
                        },
                        role = JiabanButtonRole.GHOST,
                        enabled = name.isNotBlank(),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.paymonth_manage_preset_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ---- 已建的（本组）----
                if (custom.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    custom.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        ) {
                            Text(
                                preset,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            InlineConfirmButton(
                                idleText = stringResource(R.string.paymonth_delete),
                                confirmText = stringResource(R.string.paymonth_manage_preset_confirm),
                                cancelText = stringResource(R.string.paymonth_cancel),
                                undoText = stringResource(R.string.paymonth_undo),
                                onConfirm = { onDelete(preset) },
                                style = InlineConfirmStyle.Compact,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.paymonth_done),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
