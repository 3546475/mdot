package com.mdot.app.feature.tax

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.component.ShrinkFeedbackButton
import com.mdot.app.feature.record.rememberSaveWithHaptic
import kotlinx.coroutines.delay
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Radius
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.mdot.app.core.designsystem.HeroAmountTier
import com.mdot.app.core.designsystem.heroAmountStyle
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.AnimatedMoneyText
import com.mdot.app.core.designsystem.component.AnimatedNumberText
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanButtonSize
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import com.mdot.app.domain.TaxCalculator
import com.mdot.app.domain.util.Money
import java.time.YearMonth

/**
 * 个税估算页（docs/20 P3-5）：入口是记月的「个人所得税（新）」行。
 *
 * 独立成页的理由：累计预扣法要看**全年累计**，塞进一个行内弹窗说不清楚；
 * 而它又依赖「累计收入 / 累计社保公积金 / 累计已预缴」这些**App 自己就有**的数（硬规则 12），
 * 所以这里只让用户勾一次专项附加扣除。
 *
 * ⚠️ 结果只是估算（各单位专项扣除口径、年终奖计税方式不同），页面顶部写明仅供参考。
 */
@Composable
fun TaxEstimateScreen(
    onBack: () -> Unit,
    vm: TaxEstimateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val out = state.estimate
    val appliedText = state.manualText ?: Money.yuanTrimText(out.currentTaxCents)
    val appliedCents = Money.parseYuanToCents(appliedText) ?: out.currentTaxCents
    val opMessage by vm.messageFlow.collectAsStateWithLifecycle()
    val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
        message = opMessage,
        onClear = vm::clearMessage,
    )
    // 「填入记月」的保存反馈（触感 + 按钮收缩到 ✓）：与工资页保存同一套
    val applyHaptic = rememberSaveWithHaptic()
    var applyFlash by remember { mutableStateOf(false) }
    LaunchedEffect(applyFlash) {
        if (applyFlash) {
            delay(900)
            applyFlash = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .widthIn(max = AdaptiveSpecs.contentMaxWidth)
                .padding(horizontal = Spacing.page),
        ) {
            JiabanTopBar(title = stringResource(R.string.tax_title), onBack = onBack)
            Text(
                stringResource(R.string.tax_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
            Spacer(Modifier.height(Spacing.m))

            // 月份导航**独立成行**（与记月页同款）：原先挤在结果卡标题行里，和「9月个税」抢位置
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.onMonth(state.month.minusMonths(1)) }) {
                    Icon(
                        painterResource(R.drawable.ic_ms_keyboard_arrow_left),
                        stringResource(R.string.paymonth_prev_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    taxMonthLabel(state.month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Spacing.s),
                )
                IconButton(onClick = { vm.onMonth(state.month.plusMonths(1)) }) {
                    Icon(
                        painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                        stringResource(R.string.paymonth_next_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))

            // ---- 结果（hero）：与首页/明细页 hero 卡**同一套**（primaryContainer + labelSmall 说明 +
            // headlineMedium 粗体金额 + labelMedium 副行）----
            SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.tax_result_label, state.month.year, state.month.monthValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    // 金额「展示 + 输入」二合一（同记工·包工的 BigAmountRow）：点它就地编辑，
                    // 不再单独占一块输入框（vm.onManualTax 是现成的落库口）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "¥",
                            style = heroAmountStyle(HeroAmountTier.Standard),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        BasicTextField(
                            value = appliedText,
                            onValueChange = vm::onManualTax,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = heroAmountStyle(HeroAmountTier.Standard).copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(
                            R.string.tax_result_breakdown,
                            Money.yuanTrimText(out.taxableCents),
                            out.ratePercent,
                            Money.yuanTrimText(out.quickDeductionCents),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    // 「填入记月」并进 hero 卡（2026-09-23 瘦身）：结果与动作不分家，
                    // 省掉一张独立卡的标题与间距（÷ 分隔线区隔两个语义）
                    Spacer(Modifier.height(Spacing.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), thickness = DividerThickness)
                    Spacer(Modifier.height(Spacing.m))
                    // 保存类动作统一走 ShrinkFeedbackButton（收缩到 ✓ 再展开）
                    ShrinkFeedbackButton(
                        text = stringResource(R.string.tax_apply_action),
                        busy = applyFlash,
                        onClick = {
                            applyHaptic()
                            applyFlash = true
                            vm.applyToMonth(appliedCents, out.currentTaxCents)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.m))

            // ---- 数据来源（从记月自动汇总，不用手填）：默认**折叠成一行摘要**，要核对时展开 ----
            var sourcesExpanded by rememberSaveable { mutableStateOf(false) }
            SectionCard {
                Column(Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.small))
                            .clickable { sourcesExpanded = !sourcesExpanded }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.tax_sources_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(
                                if (sourcesExpanded) R.drawable.ic_ms_expand_less else R.drawable.ic_ms_expand_more,
                            ),
                            contentDescription = stringResource(R.string.tax_sources_toggle_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 折叠时的摘要一行：只给两个最关键的数（收入 / 已缴）
                    Text(
                        stringResource(
                            R.string.tax_sources_summary,
                            Money.yuanWithSign(state.estimateInput.cumulativeIncomeCents),
                            Money.yuanWithSign(state.estimateInput.cumulativePaidCents),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sourcesExpanded) {
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            stringResource(R.string.tax_sources_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        TaxSourceRow(
                            stringResource(R.string.tax_source_income, state.month.monthValue),
                            Money.yuanWithSign(state.estimateInput.cumulativeIncomeCents),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), thickness = DividerThickness)
                        TaxSourceRow(
                            stringResource(R.string.tax_source_basic, state.month.monthValue),
                            Money.yuanWithSign(TaxCalculator.BASIC_DEDUCTION_MONTHLY_CENTS * state.month.monthValue),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), thickness = DividerThickness)
                        TaxSourceRow(
                            stringResource(R.string.tax_source_special),
                            Money.yuanWithSign(state.estimateInput.cumulativeSpecialCents),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), thickness = DividerThickness)
                        TaxSourceRow(
                            stringResource(R.string.tax_source_additional),
                            Money.yuanWithSign(state.estimateInput.cumulativeAdditionalCents),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), thickness = DividerThickness)
                        TaxSourceRow(
                            stringResource(R.string.tax_source_paid),
                            Money.yuanWithSign(state.estimateInput.cumulativePaidCents),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.m))

            // ---- 专项附加扣除（选项优先：勾选，不让人查标准）----
            DeductionSection(state = state, onToggle = vm::toggleDeduction)

            Spacer(Modifier.height(Spacing.l))
        }

        MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TaxSourceRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeductionSection(
    state: TaxUiState,
    onToggle: (String, Long) -> Unit,
) {
    // 一个月只需改一次的东西不摊在主页面：卡内只留摘要 + 已选项名，点「修改」弹层多选
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tax_deduction_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.tax_deduction_edit))
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(
                    R.string.tax_deduction_summary,
                    state.deductionKeys.size,
                    Money.yuanTrimText(state.additionalMonthlyCents),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val selectedNames = TaxDeductionItems.ALL
                .filter { it.key in state.deductionKeys }
                .joinToString("、") { it.label }
            if (selectedNames.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    selectedNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (editing) {
        DeductionDialog(
            selectedKeys = state.deductionKeys,
            onApply = { next ->
                // 只把**差异**落回 VM（VM 的 API 是 toggle）：新增的补 toggle、取消的再 toggle
                TaxDeductionItems.ALL.forEach { item ->
                    val want = item.key in next
                    val has = item.key in state.deductionKeys
                    if (want != has) onToggle(item.key, item.monthlyCents)
                }
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * 扣除项的一组 chip（带分组小标题）：一行两个（`maxItemsInEachRow = 2`）、名称在上金额在下。
 *
 * ⚠️ **不给 leadingIcon 勾**（用户 2026-09-23 定）：勾一出现 chip 就变长，两列网格里会折行、
 * 整个弹窗高度跟着跳；选中态由 FilterChip 自身的底色/描边表达（无障碍语义仍是 `selected`，读屏会念）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeductionChipGroup(
    label: String,
    items: List<TaxDeductionItem>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = 2,
        ) {
            items.forEach { item ->
                FilterChip(
                    selected = item.key in selected,
                    onClick = { onToggle(item.key) },
                    label = {
                        Column {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.tax_deduction_item_amount,
                                    Money.yuanTrimText(item.monthlyCents),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 专项附加扣除多选弹层（2026-09-23 瘦身）：主页面只留摘要，8 项在弹层里一次勾完。
 * 弹层内先改**本地副本**、点「保存」才落回 VM——中途取消能真取消。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeductionDialog(
    selectedKeys: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = remember { mutableStateListOf<String>().apply { addAll(selectedKeys) } }
    val monthly = TaxDeductionItems.ALL.filter { it.key in local }.sumOf { it.monthlyCents }
    AlertDialog(
        onDismissRequest = onDismiss,
        // 标题区 = 标题 + **实时合计**：合计是「选择的结果」，放最上面才看得见自己在改什么
        //（原先把合计当正文底部的脚注 + 一个正文里的小字，选的时候看不到结果）
        title = {
            Column {
                Text(
                    stringResource(R.string.tax_deduction_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(
                        R.string.tax_deduction_summary,
                        local.size,
                        Money.yuanTrimText(monthly),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.tax_deduction_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.m))
                // 分两组：**常用置顶**（先把最可能用到的摆眼前），其余归「其他」——8 项一眼排开太堆
                DeductionChipGroup(
                    label = stringResource(R.string.tax_deduction_group_common),
                    items = TaxDeductionItems.COMMON,
                    selected = local,
                    onToggle = { key -> if (key in local) local.remove(key) else local.add(key) },
                )
                Spacer(Modifier.height(Spacing.m))
                DeductionChipGroup(
                    label = stringResource(R.string.tax_deduction_group_other),
                    items = TaxDeductionItems.OTHER,
                    selected = local,
                    onToggle = { key -> if (key in local) local.remove(key) else local.add(key) },
                )
            }
        },
        confirmButton = {
            // 保存键用实心 primary pill（与 App 其它保存类动作一致）；取消保持普通文字键
            JiabanButton(
                text = stringResource(R.string.paymonth_save),
                onClick = {
                    onApply(local.toSet())
                    onDismiss()
                },
                role = JiabanButtonRole.PRIMARY,
                size = JiabanButtonSize.M,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paymonth_cancel)) }
        },
    )
}

/** 行分隔线粗细（0.5dp）：纯图形装饰、非间距语义，就地常量（同分组卡行分隔线） */
private val DividerThickness = 0.5.dp

/** 目标月份标签（`2026年9月`）——与记月页同一套文案 */
@Composable
fun taxMonthLabel(month: YearMonth): String = stringResource(R.string.paymonth_month, month.year, month.monthValue)
