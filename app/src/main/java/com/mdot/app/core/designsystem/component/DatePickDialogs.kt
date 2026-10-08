package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.EngineIcons
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
import androidx.compose.foundation.layout.fillMaxHeight

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
    /** 见 [JiabanAlertDialog]：从底部弹层里打开的对话框传 false，避免盖掉弹层自己的背景 */
    backdrop: Boolean = true,
    /** 见 [JiabanAlertDialog]：**独立 Dialog 窗口里的弹层**（如工地弹层）内打开的弹窗必须传 false ——
     *  否则页内覆盖层会渲染到那个窗口**下面**（看不见）。等该弹层改成页内再一并转过来。 */
    asOverlay: Boolean = true,
) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var pending by remember { mutableStateOf(initial) }
    var multi by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(initialSelection.ifEmpty { setOf(initial) }) }
    val maxDate = if (allowFuture) null else today

    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        asOverlay = asOverlay,
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
                JiabanButton(
                    text = stringResource(R.string.ds_pick_done),
                    onClick = { onPickDates(selection) },
                    role = JiabanButtonRole.GHOST,
                )
            } else if (confirmRequired) {
                JiabanButton(
                    text = stringResource(R.string.ds_pick_confirm),
                    onClick = { onPick(pending) },
                    role = JiabanButtonRole.GHOST,
                )
            }
        },
        dismissButton = {
            if (multi) {
                // 多选态该槽放**两颗**（清除选择 + 取消）：各包一层等宽 Box，配合卡片的「整格可点」
                // 标记 ⇒ 各占半格、各自整格可点，互不挤占（2026-09-30：此前只下发到单颗场景）
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        JiabanButton(
                            text = stringResource(R.string.ds_clear_selection),
                            onClick = { selection = setOf(initial) },
                            role = JiabanButtonRole.GHOST,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        JiabanButton(
                            text = stringResource(R.string.ds_cancel),
                            onClick = onDismiss,
                            role = JiabanButtonRole.GHOST,
                        )
                    }
                }
            } else {
                JiabanButton(
                    text = stringResource(R.string.ds_cancel),
                    onClick = onDismiss,
                    role = JiabanButtonRole.GHOST,
                )
            }
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
            .clip(engineShape(Radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/**
 * 月份粒度：**点一下即生效并关闭**；月份格子与日期格子同一套视觉。
 *
 * @param maxMonth 可选月份上限（含），null = 不封顶（默认，记月页/个税页需能看未来月做预算）。
 *   需要「不越今天」时由调用方传 `YearMonth.now()`——**注意上限是「哪个越界点」而不是布尔**：
 *   明细页曾用布尔 `disableFutureMonths`（内部按"今天"判定）而细节与调用方预期不符，见 docs/11 074。
 */
@Composable
fun MonthPickDialog(
    title: String,
    selected: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    maxMonth: YearMonth? = null,
) {
    val thisMonth = YearMonth.now()

    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            MonthPickContent(
                selected = selected,
                onPick = onPick,
                maxMonth = maxMonth,
                // 月份粒度下「回本月」一次点掉整件事，故直接关闭
                onBackToCurrent = { onPick(thisMonth) },
            )
        },
        confirmButton = {},
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.ds_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}

/**
 * 月份网格主体（**弹窗内容**，不含外壳）：`[‹] 2026 年 [回本月] [›]` + 12 个月格。
 *
 * 抽出来是为了让**统计页的区间选择弹窗**复用同一套网格与导航（那里的弹窗还要额外放
 * 「维度选择 + 自定义起止」，不能整个套一个月份弹窗）。
 * 调用方自己决定 [onBackToCurrent] 的行为：月份粒度下是"选中本月并关闭"，
 * 统计页那种常驻弹窗里则应"只选中不关闭"。
 *
 * @param maxMonth 可选月份上限（含），null = 不封顶
 * @param onBackToCurrent null = 不显示「回本月」胶囊
 */
@Composable
fun MonthPickContent(
    selected: YearMonth,
    onPick: (YearMonth) -> Unit,
    maxMonth: YearMonth? = null,
    onBackToCurrent: (() -> Unit)? = null,
) {
    val thisMonth = YearMonth.now()
    var year by remember { mutableStateOf(selected.year) }

    Column {
        MonthNavBar(
            month = YearMonth.of(year, selected.monthValue),
            onPrev = { year -= 1 },
            onNext = { year += 1 },
            nextEnabled = maxMonth == null || year < maxMonth.year,
            titleText = stringResource(R.string.ds_year_title, year),
            shortcut = if (selected != thisMonth && onBackToCurrent != null) {
                { ShortcutChip(stringResource(R.string.ds_back_this_month)) { onBackToCurrent() } }
            } else {
                null
            },
        )
        Spacer(Modifier.height(Spacing.s))
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { m ->
                    val ym = YearMonth.of(year, m)
                    val beyond = maxMonth != null && ym > maxMonth
                    MonthCell(
                        text = stringResource(R.string.ds_month_short, m),
                        selected = year == selected.year && m == selected.monthValue,
                        enabled = !beyond,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(ym) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 年份网格主体（**弹窗内容**，不含外壳）：`[‹] 年份 [回今年] [›]` + 12 个年份胶囊。
 * 与 [MonthPickContent] 同款骨架，供统计页「年」维度跳转用。
 *
 * @param years 可选年份范围（含端点）
 * @param maxYear 可选年份上限（含），null = 不封顶
 */
@Composable
fun YearPickContent(
    selectedYear: Int,
    years: IntRange,
    onPick: (Int) -> Unit,
    maxYear: Int? = null,
    onBackToCurrent: (() -> Unit)? = null,
) {
    val thisYear = java.time.Year.now().value
    var pageStart by remember(selectedYear) { mutableStateOf(pageStartOf(selectedYear, years)) }

    Column {
        MonthNavBar(
            month = YearMonth.of(pageStart, 1),
            onPrev = { pageStart -= YEARS_PER_PAGE },
            onNext = { pageStart += YEARS_PER_PAGE },
            nextEnabled = maxYear == null || pageStart + YEARS_PER_PAGE - 1 < maxYear,
            titleText = stringResource(
                R.string.ds_year_span,
                pageStart,
                pageStart + YEARS_PER_PAGE - 1,
            ),
            shortcut = if (selectedYear != thisYear && onBackToCurrent != null) {
                { ShortcutChip(stringResource(R.string.ds_back_this_year)) { onBackToCurrent() } }
            } else {
                null
            },
        )
        Spacer(Modifier.height(Spacing.s))
        (0 until YEARS_PER_PAGE).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val y = pageStart + i
                    val inRange = y in years
                    val beyond = maxYear != null && y > maxYear
                    MonthCell(
                        text = stringResource(R.string.ds_year_short, y),
                        selected = y == selectedYear,
                        enabled = inRange && !beyond,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(y) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 年份网格每页年数（3 列 × 4 行，与月份网格同为 4 行高） */
private const val YEARS_PER_PAGE = 12

/** 让 [selectedYear] 落在当前页内的页首年份 */
private fun pageStartOf(selectedYear: Int, years: IntRange): Int {
    val span = YEARS_PER_PAGE
    val base = years.first
    val offset = ((selectedYear - base).coerceAtLeast(0) / span) * span
    return base + offset
}


// ── 共用零件 ─────────────────────────────────────────────────────────────

/** 月份导航：[‹] 标题 [回今天] [›]。标题可换成自定义文案（月份粒度显示年份）；[nextEnabled]=false 封顶未来 */
@Composable
private fun MonthNavBar(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    titleText: String = stringResource(R.string.ds_month_title, month.year, month.monthValue),
    nextEnabled: Boolean = true,
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
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                EngineIcons.chevron(),
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
            .clip(engineShape(Radius.pill))
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

/** 月份格：与日期格同一套视觉（选中填充圆形底）；[enabled]=false 置灰不可点（封顶未来月） */
@Composable
private fun MonthCell(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .padding(Spacing.xs / 2)
            .aspectRatio(2.2f)
            .clip(engineShape(Radius.small))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
