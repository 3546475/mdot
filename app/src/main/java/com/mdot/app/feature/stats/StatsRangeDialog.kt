package com.mdot.app.feature.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.DayPickDialog
import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.MonthPickContent
import com.mdot.app.core.designsystem.component.YearPickContent
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.jiabanFilterChipColors
import com.mdot.app.domain.CycleCalculator
import com.mdot.app.domain.StatsRangeKind
import com.mdot.app.domain.util.TimeUtils
import java.time.LocalDate
import java.time.YearMonth

/** 统计页区间选择弹窗的现状快照（由 VM 下发，弹窗只读） */
data class StatsRangePicker(
    val kind: StatsRangeKind,
    val current: CycleCalculator.Period?,
)

/** 年份网格向前可选年数（更早的账期请用「自定义」框） */
private const val YEAR_LOOKBACK = 30

/**
 * 统计页·时间段选择弹窗（方案 C：**点胶囊弹菜单**，取代原先常驻的维度 chip 行）。
 *
 * 结构 = 「维度选项行」+「该维度的跳转体」：
 * - 考勤周期 / 自然月 → 月份网格（复用 [MonthPickContent]，与明细页、记月页同一套）
 * - 年 → 年份网格（[YearPickContent]）
 * - 自定义 → 起止日期入钮（嵌套 [DayPickDialog]；走页内覆盖层栈，不新开系统窗口）
 *
 * 维度行用 **`FilterChip` 而非 `ChoicePillRow`**：口径/筛选切换属「次要信息」，
 * 按 docs/03 §16 的 2026-09-28 修订该走轻量 chip（`ChoicePillRow` 只留给重要的、
 * 具象图标语义的主设置项）。
 *
 * @param onPickMonth 月份网格点选（由 VM 换算成步进偏移，UI 不算日期）
 * @param onPickYear 年份网格点选
 */
@Composable
fun StatsRangeDialog(
    dimension: StatsDimension,
    picker: StatsRangePicker?,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    onDimension: (StatsDimension) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onPickYear: (Int) -> Unit,
    onCustomFrom: (LocalDate) -> Unit,
    onCustomTo: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 嵌套的起止日期选择（"from"/"to"）；DayPickDialog 走覆盖层栈，可叠在本弹窗之上
    var picking by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val current = picker?.current

    JiabanAlertDialog(containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_pick_title)) },
        text = {
            Column {
                // ---- 维度选项行（四选一）----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    listOf(
                        StatsDimension.CYCLE, StatsDimension.MONTH,
                        StatsDimension.YEAR, StatsDimension.CUSTOM,
                    ).forEach { dim ->
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = dimension == dim,
                            onClick = { onDimension(dim) },
                            label = { Text(stringResource(dim.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.m))

                // ---- 该维度的跳转体 ----
                when (dimension) {
                    StatsDimension.CYCLE, StatsDimension.MONTH -> {
                        // 两者共用月份网格；都能跳到任意月份，差异只在"点某月落在哪一期"
                        // （考勤周期按锚点月反解，自然月就是那个月）——换算在 VM 里
                        current?.let {
                            MonthPickContent(
                                selected = YearMonth.from(it.from),
                                onPick = onPickMonth,
                                // 不越今天：上限即今天所在自然月
                                maxMonth = YearMonth.from(today),
                                // 弹窗常驻：只改选中，不关闭
                                onBackToCurrent = { onPickMonth(YearMonth.from(today)) },
                            )
                        }
                    }
                    StatsDimension.YEAR -> {
                        YearPickContent(
                            selectedYear = current?.from?.year ?: today.year,
                            years = (today.year - YEAR_LOOKBACK)..today.year,
                            onPick = onPickYear,
                            maxYear = today.year,
                            onBackToCurrent = { onPickYear(today.year) },
                        )
                    }
                    StatsDimension.CUSTOM -> {
                        Text(
                            stringResource(R.string.stats_range_custom_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            JiabanButton(
                                text = stringResource(
                                    R.string.stats_custom_from,
                                    customFrom?.let(TimeUtils::mdCn)
                                        ?: stringResource(R.string.stats_custom_from_default),
                                ),
                                onClick = { picking = "from" },
                                role = JiabanButtonRole.SECONDARY,
                            )
                            JiabanButton(
                                text = stringResource(
                                    R.string.stats_custom_to,
                                    customTo?.let(TimeUtils::mdCn)
                                        ?: stringResource(R.string.stats_custom_to_default),
                                ),
                                onClick = { picking = "to" },
                                role = JiabanButtonRole.SECONDARY,
                            )
                        }
                        if (customFrom != null && customTo != null && customFrom.isAfter(customTo)) {
                            Spacer(Modifier.height(Spacing.s))
                            Text(
                                stringResource(R.string.stats_range_custom_invalid),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // 工地两个维度不走本弹窗（工地仍用 SiteRangeChips，见 StatsContent）
                    StatsDimension.SITE_PENDING, StatsDimension.SITE_SPAN -> Unit
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.ds_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
        // 本弹窗只有「取消」一个动作 ⇒ 无次要槽。⚠️ 勿写 `dismissButton = {}`：空槽在 MIUIX 分栏行里
        // 仍占半格 ⇒ 左半格空白 + 一条孤立的竖分隔线（DialogContractTest 守门）
    )

    // 起止日期选择：不关闭本弹窗，选完回到本弹窗（覆盖层栈支持弹窗叠弹窗）
    picking?.let { which ->
        DayPickDialog(
            title = stringResource(
                if (which == "from") R.string.ds_pick_start else R.string.ds_pick_end,
            ),
            initial = if (which == "from") customFrom ?: today.withDayOfMonth(1) else customTo ?: today,
            onPick = { d ->
                if (which == "from") onCustomFrom(d) else onCustomTo(d)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
