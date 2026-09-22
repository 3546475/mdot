package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * ══ 统一的日期 / 月份选择（docs/03 §日期选择）═════════════════════════
 *
 * 此前 App 里有**四套各不相同的选法**，同一个动作长得不一样、便捷程度也不同：
 * 记加班弹窗内联一份 M3 `DatePickerDialog`、导出页用 `DatePick`（另一份 M3）、
 * 记月页是月份 chip 弹窗、记工是「日期 chip 流」（1..今天 一排 chip，看不出星期几）。
 *
 * 现在统一为**同一套骨架 + 三种粒度**（不必一模一样，但导航、表头、格子、按钮一致）：
 * ```
 * [‹] 2026年9月 [›]  回今天      ← 同一个月份导航（月份粒度把标题换成 ‹ 2026 年 ›）
 *  一  二  三  四  五  六  日     ← 周一起，复用日历页同一套表头文案
 *  …日期网格：今天带圈、选中填充、未来可禁用…
 * [取消]  [完成 / 已选 N 天]      ← 同一套按钮
 * ```
 *
 * **便捷优先的三条约定**：
 * 1. **单日与月份：点一下即生效并关闭**——不再需要再点「确定」（少一次点击）；
 * 2. 多选：点选切换 + 标题旁「已选 N 天」，底部「完成」；
 * 3. 不在本月/今天时，导航行右侧给「回今天 / 回本月」一次点击回来。
 *
 * 本文件无业务依赖（不引 holiday / repository），只画日期——需要标记由调用方后续注入。
 */

/**
 * 单日选择：**点一下即生效并关闭**（`confirmRequired = true` 时才需要确认）。
 *
 * [multiSelectable] 为 true 时，标题行右侧多一个「多选」切换——切过去后点日期是**勾选/取消**，
 * 底部变「清空 / 完成」，[onPickDates] 回传整组日期（记工补记前几天用）。
 *
 * 为什么把「多选」放进弹窗而不是行上：一行的点击区只能有一个主动作，再塞一个胶囊按钮
 * 会把值挤换行、也和行内徽标抢视觉（2026-09-22 用户反馈「突兀」）。
 */
@Composable
fun DayPickDialog(
    title: String,
    initial: LocalDate,
    allowFuture: Boolean = false,
    confirmRequired: Boolean = false,
    multiSelectable: Boolean = false,
    initialSelection: Set<LocalDate> = emptySet(),
    onPick: (LocalDate) -> Unit,
    onPickDates: (Set<LocalDate>) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var pending by remember { mutableStateOf(initial) }
    var multi by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(initialSelection.ifEmpty { setOf(initial) }) }
    val maxDate = if (allowFuture) null else today

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                if (multi) {
                    Text(
                        stringResource(R.string.ds_multi_selected_count, selection.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Spacing.s))
                }
                if (multiSelectable) {
                    ModeChip(
                        text = if (multi) stringResource(R.string.ds_pick_single)
                        else stringResource(R.string.ds_pick_multi),
                        onClick = { multi = !multi },
                    )
                }
            }
        },
        text = {
            Column {
                MonthNavBar(
                    month = month,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    shortcut = if (month != YearMonth.from(today)) {
                        {
                            ShortcutChip(stringResource(R.string.ds_back_today)) {
                                month = YearMonth.from(today)
                                if (multi) {
                                    selection = selection + today
                                } else if (!confirmRequired) {
                                    onPick(today)
                                } else {
                                    pending = today
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(Spacing.s))
                MonthDayGrid(
                    month = month,
                    selected = if (multi) selection else setOf(pending),
                    today = today,
                    maxDate = maxDate,
                    onDay = { d ->
                        when {
                            multi -> selection = if (d in selection) selection - d else selection + d
                            confirmRequired -> pending = d
                            else -> onPick(d)
                        }
                    },
                )
            }
        },
        confirmButton = {
            if (multi) {
                TextButton(onClick = { onPickDates(selection) }) { Text(stringResource(R.string.ds_pick_done)) }
            } else if (confirmRequired) {
                TextButton(onClick = { onPick(pending) }) { Text(stringResource(R.string.ds_pick_confirm)) }
            }
        },
        dismissButton = {
            if (multi) {
                TextButton(onClick = { selection = setOf(initial) }) { Text(stringResource(R.string.ds_clear_selection)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_cancel)) }
        },
    )
}

/** 弹窗标题旁的小切换（多选 ⇄ 单选）：与「回今天」同一套轻量样式 */
@Composable
private fun ModeChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/** 月份粒度：**点一下即生效并关闭**；月份格子与日期格子同一套视觉 */
@Composable
fun MonthPickDialog(
    title: String,
    selected: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val thisMonth = YearMonth.now()
    var year by remember { mutableStateOf(selected.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MonthNavBar(
                    month = YearMonth.of(year, selected.monthValue),
                    onPrev = { year -= 1 },
                    onNext = { year += 1 },
                    titleText = stringResource(R.string.ds_year_title, year),
                    shortcut = if (selected != thisMonth) {
                        { ShortcutChip(stringResource(R.string.ds_back_this_month)) { onPick(thisMonth) } }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(Spacing.s))
                (1..12).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { m ->
                            MonthCell(
                                text = stringResource(R.string.ds_month_short, m),
                                selected = year == selected.year && m == selected.monthValue,
                                modifier = Modifier.weight(1f),
                                onClick = { onPick(YearMonth.of(year, m)) },
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_cancel)) }
        },
    )
}

// ── 共用零件 ─────────────────────────────────────────────────────────────

/** 月份导航：[‹] 标题 [回今天] [›]。标题可换成自定义文案（月份粒度显示年份） */
@Composable
private fun MonthNavBar(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    titleText: String = stringResource(R.string.ds_month_title, month.year, month.monthValue),
    shortcut: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                painterResource(R.drawable.ic_ms_keyboard_arrow_left),
                contentDescription = stringResource(R.string.ds_prev_month),
                modifier = Modifier.size(IconSpec.boxed),
            )
        }
        Text(
            titleText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        shortcut?.let {
            it()
            Spacer(Modifier.width(Spacing.xs))
        }
        IconButton(onClick = onNext) {
            Icon(
                painterResource(R.drawable.ic_ms_keyboard_arrow_right),
                contentDescription = stringResource(R.string.ds_next_month),
                modifier = Modifier.size(IconSpec.boxed),
            )
        }
    }
}

/** 一次点击回今天/回本月的胶囊（非本月才出现） */
@Composable
private fun ShortcutChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/** 月份网格：表头（一~日，复用日历页文案）+ 日期格；[maxDate] 非空则其后不可选 */
@Composable
private fun MonthDayGrid(
    month: YearMonth,
    selected: Set<LocalDate>,
    today: LocalDate,
    maxDate: LocalDate?,
    onDay: (LocalDate) -> Unit,
    primaryDay: LocalDate? = null,
) {
    val labels = listOf(
        R.string.calendar_weekday_mon,
        R.string.calendar_weekday_tue,
        R.string.calendar_weekday_wed,
        R.string.calendar_weekday_thu,
        R.string.calendar_weekday_fri,
        R.string.calendar_weekday_sat,
        R.string.calendar_weekday_sun,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value // 周一起
        val cells = buildList {
            repeat(leading) { add(null) }
            (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date in selected,
                            isPrimary = date == primaryDay,
                            enabled = maxDate == null || !date.isAfter(maxDate),
                            modifier = Modifier.weight(1f),
                            onClick = { onDay(date) },
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 单个日期格：今天带圈、选中填充、未来/不可选变淡、主日期填充但不可点 */
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isPrimary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val filled = isSelected || isPrimary
    val container = when {
        filled -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val content = when {
        filled -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(Spacing.xs / 2)
            .clip(CircleShape)
            .background(container)
            .then(
                if (isToday && !filled) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .then(if (enabled && !isPrimary) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            fontWeight = if (filled || isToday) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 月份格：与日期格同一套视觉（选中填充圆形底） */
@Composable
private fun MonthCell(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .padding(Spacing.xs / 2)
            .aspectRatio(2.2f)
            .clip(RoundedCornerShape(Radius.small))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
