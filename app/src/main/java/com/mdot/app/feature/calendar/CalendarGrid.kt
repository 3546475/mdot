package com.mdot.app.feature.calendar

import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.secondaryLabelColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.CalendarCellSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import androidx.compose.foundation.layout.width
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.holiday.DAY_LABEL_MAX_CHARS
import com.mdot.app.core.holiday.RestBadge
import java.time.LocalDate

/** 月网格：左右滑动切月（阈值 50dp）+ 方向感滑动动画（contentKey 只认月份，同月改记录不重播）；
 *  长按日期进入多选（多选中点按切换选中，切月退出） */
@Composable
internal fun MonthGrid(state: CalendarUiState, vm: CalendarViewModel) {
    val batchSelecting by vm.isBatchSelecting.collectAsStateWithLifecycle()
    val batchSelected by vm.batchSelection.collectAsStateWithLifecycle()
    val dragThreshold = with(LocalDensity.current) { 50.dp.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(dragThreshold) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalDrag < -dragThreshold) vm.nextMonth()
                        else if (totalDrag > dragThreshold) vm.prevMonth()
                    },
                )
            }
    ) {
        AnimatedContent(
            targetState = MonthGridData(state.month, state.cells),
            contentKey = { it.month },
            transitionSpec = {
                val forward = targetState.month > initialState.month
                val spec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))) togetherWith
                    (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)))
            },
            label = "monthGrid",
        ) { page ->
            val today = LocalDate.now()
            // 长按后拖动快速多选（一次连续手势）：长按本身仍由单元格触发（含触觉），本手势接棒处理「拖动扫过」——
            // 拖动经过的日期依次加入选择。长按成立后本手势会 consume 拖动，故不会误触发外层横滑切月；
            // 未长按就移动则本手势自行取消，交回外层切月。
            Column(
                Modifier.pointerInput(page.month, page.cells) {
                    fun dateAt(pos: Offset): LocalDate? {
                        if (pos.x < 0f || pos.y < 0f) return null
                        val cellW = size.width / 7f
                        val cellH = cellW / CalendarCellSpec.aspectRatio   // 与单元格 aspectRatio 保持一致
                        val col = (pos.x / cellW).toInt().coerceIn(0, 6)
                        val row = (pos.y / cellH).toInt()
                        val d = page.cells.getOrNull(row * 7 + col)?.date ?: return null
                        return if (d.isAfter(LocalDate.now())) null else d
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            dateAt(pos)?.let { d ->
                                if (vm.isBatchSelecting.value) {
                                    if (d !in vm.batchSelection.value) vm.toggleBatchSelect(d)
                                } else {
                                    vm.startBatchSelect(d)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dateAt(change.position)?.let { d ->
                                if (vm.isBatchSelecting.value && d !in vm.batchSelection.value) {
                                    vm.toggleBatchSelect(d)
                                }
                            }
                        },
                    )
                }
            ) {
                page.cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { cell ->
                            CalendarCellView(
                                cell = cell,
                                isToday = cell.date == today,
                                // 多选模式下抑制单选高亮（多选格有自己的填充样式）
                                isSelected = !batchSelecting && cell.date == state.selectedDate,
                                isSite = state.workSystem == com.mdot.app.domain.model.WorkSystem.SITE,
                                isBatchSelected = batchSelecting && cell.date != null && cell.date in batchSelected,
                                // 今天之后的日期不可选：置灰且不响应点击/长按（与切月限制一致）
                                isFuture = cell.date?.isAfter(today) == true,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(CalendarCellSpec.aspectRatio),
                                onClick = {
                                    cell.date?.let { d ->
                                        if (batchSelecting) vm.toggleBatchSelect(d) else vm.selectDate(d)
                                    }
                                },
                                onLongClick = {
                                    if (!batchSelecting) cell.date?.let { vm.startBatchSelect(it) }
                                },
                            )
                        }
                        if (week.size < 7) repeat(7 - week.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCellView(
    cell: CalendarCell,
    isToday: Boolean,
    isSelected: Boolean = false,
    isSite: Boolean = false,
    isBatchSelected: Boolean = false,
    /** 今天之后：置灰不可点（未来不可记） */
    isFuture: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    // 选中态与记加班弹窗时长格同款：深主题色描边 + 浅主题色填充，颜色缓切过渡；
    // 多选格 = 半透明填充 + 描边（与单选/今天区分层级）
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bgColor by animateColorAsState(
        targetValue = when {
            isBatchSelected -> colorScheme.primaryContainer.copy(alpha = 0.55f)
            isSelected || isToday -> colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "calBg",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (isBatchSelected || isSelected) colorScheme.primary else Color.Transparent,
        animationSpec = colorSpec,
        label = "calStroke",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFuture -> colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            isBatchSelected || isSelected || isToday -> colorScheme.onPrimaryContainer
            cell.label.badge == RestBadge.REST -> colorScheme.secondary
            else -> colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "calText",
    )
    val otColor by animateColorAsState(
        targetValue = if (isBatchSelected || isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
        animationSpec = colorSpec,
        label = "calOt",
    )
    // 指示圈"绽放"：弹簧缩放 + 淡入淡出（时长格是两色块交叉渐变，日历从透明出现，
    // 纯色变会显得生硬，叠加弹簧缩放后与时长格手感一致）
    val bloomOn = isBatchSelected || isSelected || isToday
    val indicatorScale by animateFloatAsState(
        targetValue = if (bloomOn) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "calBloom",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (bloomOn) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "calBloomAlpha",
    )
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = engineShape(Radius.button)
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.93f)
            .padding(1.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.date != null) {
            // 选中/今天指示圈（填充 + 描边）在文字下层绽放，文字只做颜色缓切
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                        alpha = indicatorAlpha
                    }
                    .background(bgColor, shape)
                    .border(1.5.dp, strokeColor, shape)
            )
            // 涟漪层夹在指示圈与文字之间： indication 画在本层 children 之下，
            // 若挂在外层容器会被不透明指示圈（尤其绽放动画中）整个盖住。
            // 未来日期不可选：不挂点击层（无涟漪无触感），onLongClick 同样不响应
            if (!isFuture) {
                Box(
                    Modifier
                        .matchParentSize()
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClick()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                        )
                )
            }
            // 工地角标（v0.7.9.0）：左下角最多三颗小点 —— 照片 / 包工 / 借支。
            // 格子只有 ~40dp、第二行已被「N 工」占用，写不下字 ⇒ 用点阵表示"这天还留了证 / 另有记录"。
            // 颜色沿用全局语义：primary = 记录加速（照片）、tertiary = 包工、secondary = 借支
            if (isSite && !cell.badges.isEmpty) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (cell.badges.hasPhotos) Dot(colorScheme.primary, small = true)
                    if (cell.badges.hasPiece) Dot(colorScheme.tertiary, small = true)
                    if (cell.badges.hasAdvance) Dot(colorScheme.secondary, small = true)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 第一行：左槽「休/班」· 日期 · 右槽节日/农历
                CellMainRow(cell = cell, textColor = textColor, colorScheme = colorScheme)
                Spacer(Modifier.height(CalendarCellSpec.lineGap))
                if (isSite && cell.worksMilli > 0) {
                    // 工地：当日工数（1 工 / 1.5 工）
                    val works = cell.worksMilli / 1000f
                    val wText = if (works % 1f == 0f) works.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", works)
                    Text(
                        stringResource(R.string.calendar_site_works_badge, wText),
                        style = MaterialTheme.typography.labelSmall,
                        color = otColor,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (cell.otMinutes > 0) {
                    val hours = cell.otMinutes / 60.0
                    val text = "+" + String.format(java.util.Locale.US, "%.1f", hours).trimEnd('0').trimEnd('.')
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = otColor,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (cell.leaveMinutes > 0) {
                    Dot(colorScheme.error)
                } else if (!isSite && !cell.shiftName.isNullOrBlank()) {
                    // 「只记班次」的日子（0 时长，v0.7.9.0）：给一个**首字**标记（白 / 夜 / …），
                    // 否则用户记完在月历上看不见，等于白记；完整班次名在「X月X日 小结」里。
                    // 工地不走这条（那边 shiftName 装的是「N 工」文本，见 siteStateFlow）
                    cell.shiftName?.let { name ->
                        Text(
                            name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.tertiary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * 格子第一行（用户 2026-09-20 规格 + 当天两轮调整）：
 * **日期是主体**——居中于本列（与表头星期对齐）、字号最大；「休/班」紧贴其左、节日名/农历稍离其右。
 *
 * 布局要点（两轮反馈各修掉一个坑）：
 * 1. **不能按内容拼接后整体居中**（早先的 Row 写法）：左右槽宽度不等时日期会被挤偏约 5dp，
 *    与表头星期对不上。改为两侧占位**固定等宽**（[CalendarCellSpec.sideSlotWidth]）后，
 *    整体居中即等价于日期居中，且日期不必知道列宽。
 * 2. **两侧不能贴格子边缘**：格宽约 45dp 时左右各留约 20dp 空隙，「休」会明显更靠近左边那一格的
 *    农历，看着像别人家的信息。故左槽内容向中间右对齐、右槽向中间左对齐。
 */
@Composable
private fun CellMainRow(
    cell: CalendarCell,
    textColor: Color,
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左槽：固定宽、内容右对齐 → 紧贴日期
        Box(
            modifier = Modifier.width(CalendarCellSpec.sideSlotWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            cell.label.badge?.let { badge ->
                Text(
                    stringResource(
                        if (badge == RestBadge.REST) R.string.calendar_badge_rest
                        else R.string.calendar_badge_makeup
                    ),
                    modifier = Modifier.padding(end = CalendarCellSpec.sideBadgeGap),
                    fontSize = CalendarCellSpec.sideFontSize,
                    lineHeight = CalendarCellSpec.sideLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = if (badge == RestBadge.REST) colorScheme.error else colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            cell.date?.dayOfMonth?.toString().orEmpty(),
            fontSize = CalendarCellSpec.dateFontSize,
            lineHeight = CalendarCellSpec.dateLineHeight,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
        // 右槽：固定宽、内容左对齐 → 留在日期近旁
        Box(
            modifier = Modifier.width(CalendarCellSpec.sideSlotWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            cell.label.text?.let { label ->
                Text(
                    label.vertical(),
                    modifier = Modifier.padding(start = CalendarCellSpec.sideLabelGap),
                    fontSize = CalendarCellSpec.sideFontSize,
                    lineHeight = CalendarCellSpec.sideLineHeight,
                    // 节日与农历同色同重（用户 2026-09-20 二轮规格）：两者都是挂靠信息，不争主次
                    // 取色走引擎分发（2026-09-30 用户报 MIUIX 下太淡：MIUIX 的 outline 是分隔线级浅灰）
                    color = secondaryLabelColor(),
                    maxLines = DAY_LABEL_MAX_CHARS,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * 右槽文案竖排（用户 2026-09-20 规格）：每字一行。
 *
 * 用显式 `"\n"` 而不是 `Modifier.rotate(90f)` 或 `Column` 堆两个 `Text`：
 * 前者会把汉字本身也转 90°（成了躺着的字），后者会让每格多出语义节点、无障碍朗读拆成两段。
 * `softWrap = false` 只关自动换行，显式换行照常生效。
 */
private fun String.vertical(): String =
    if (length < 2) this else chunked(1).joinToString("\n")

@Composable
private fun Dot(color: Color, small: Boolean = false) {
    Box(
        Modifier
            .size(if (small) 4.dp else 6.dp)
            .background(color, CircleShape)
    )
}
