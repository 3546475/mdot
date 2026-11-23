package com.mdot.app.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import com.mdot.app.core.designsystem.component.InlineConfirmStyle
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.MonthPickDialog
import com.mdot.app.core.designsystem.component.MonthStepper
import com.mdot.app.domain.PayrollCalculator
import com.mdot.app.domain.model.InsuranceKind
import com.mdot.app.domain.model.PayGroup
import com.mdot.app.domain.model.PayMonthItem
import com.mdot.app.domain.model.PayMonthSheet
import com.mdot.app.domain.model.rowsOf
import com.mdot.app.domain.model.insuranceKindOf
import java.time.YearMonth

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
