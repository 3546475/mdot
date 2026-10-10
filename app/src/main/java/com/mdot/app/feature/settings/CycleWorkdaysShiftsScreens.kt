package com.mdot.app.feature.settings

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.component.JiabanSwitch
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.InlineConfirmButton
import com.mdot.app.core.designsystem.component.InlineConfirmStyle
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.ShiftDefaults
import com.mdot.app.domain.model.Shift
import java.time.DayOfWeek
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanCheckbox
import com.mdot.app.core.designsystem.component.FloatingLabelTextField

/** 考勤周期内容主体（设定多页签「周期」页签复用；F7-3：1–29 号起始日，29 在天数不足的月份自动落到月末） */
@Composable
fun CyclePane(vm: CycleViewModel = hiltViewModel()) {
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val example = stringResource(R.string.cycle_period_example, remember(anchor) { buildExample(anchor) })

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))

        // 说明卡 + 当前周期示例
        SectionCard {
            Column {
                Text(stringResource(R.string.cycle_start_day_heading), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.cycle_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                Surface(
                    shape = engineShape(Radius.card),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        example,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // 起始日选择
        SectionCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.cycle_month_anchor_heading),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.cycle_anchor_summary, anchor),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Spacing.m))
                // 起始日 1–29（30/31 用不到已移除；2 月不足 29 时 CycleCalculator 自动落月末）
                (1..29).chunked(7).forEach { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                selected = anchor == day,
                                modifier = Modifier.weight(1f),
                            ) { vm.set(day) }
                        }
                        // 末行补齐空位：保持 7 列同宽，否则末行格子会均分整行（比上面宽且不与上方对齐）
                        repeat(7 - week.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = engineShape(Radius.button)
    val interaction = remember { MutableInteractionSource() }
    // 选中态平滑过渡 + 按压缩放
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dayBg",
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dayStroke",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dayText",
    )
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.9f)
            .height(40.dp)
            .background(bg, shape)
            .border(1.5.dp, stroke, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

private fun buildExample(anchor: Int): String {
    val today = java.time.LocalDate.now()
    val period = com.mdot.app.domain.CycleCalculator.periodContaining(today, anchor)
    return period.toString()
}

/** 工作日设定内容主体（设定多页签「工作日」页签复用；F7-4：影响档位自动判定的兜底） */
@Composable
fun WorkdaysPane(vm: WorkdaysViewModel = hiltViewModel()) {
    val workdays by vm.workdays.collectAsStateWithLifecycle()
    val days = listOf(
        DayOfWeek.MONDAY to stringResource(R.string.workdays_mon),
        DayOfWeek.TUESDAY to stringResource(R.string.workdays_tue),
        DayOfWeek.WEDNESDAY to stringResource(R.string.workdays_wed),
        DayOfWeek.THURSDAY to stringResource(R.string.workdays_thu),
        DayOfWeek.FRIDAY to stringResource(R.string.workdays_fri),
        DayOfWeek.SATURDAY to stringResource(R.string.workdays_sat),
        DayOfWeek.SUNDAY to stringResource(R.string.workdays_sun),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))
        Text(
            stringResource(R.string.workdays_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        SectionCard {
            Column {
                days.forEach { (day, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        JiabanCheckbox(checked = day in workdays, onCheckedChange = { vm.toggle(day) })
                    }
                }
            }
        }
    }
}

/** 班次管理内容主体（设定多页签「班次」页签复用；F7-5：预置可隐藏不可删；自定义可增删改、排序）；
 *  拖拽手柄排序，Switch 控制显示/隐藏，**点行主体改名**，行尾原地确认删除 + 底部提示窗撤销 */
@Composable
fun ShiftsPane(vm: ShiftsViewModel = hiltViewModel()) {
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    // 默认班次 = 排序后第一个**未隐藏**班次（ShiftDefaults）：拖到第一位即成为默认，
    // 列表行上要标出来，否则用户不知道「拖排序」改的就是它（记加班预选同一个函数的口径）
    val defaultShiftId = remember(shifts) { ShiftDefaults.idOf(shifts) }
    val message by vm.message.collectAsStateWithLifecycle()
    val messageIsError by vm.messageIsError.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Shift?>(null) }
    var renameText by remember { mutableStateOf("") }
    val messageCanUndo by vm.messageCanUndo.collectAsStateWithLifecycle()
    // 结果提示：浅色悬浮胶囊（与关于页检查更新同范式；错误/成功自动选 ⚠/✓ 描边；删除班次时带「撤销」）
    val undoLabel = stringResource(R.string.shifts_undo)
    val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
        message = message,
        onClear = vm::clearMessage,
        isError = messageIsError,
        actionLabel = if (messageCanUndo) undoLabel else null,
        onAction = vm::undoDelete,
    )

    // 拖拽排序状态（仿底栏配置卡）
    var draftIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // 数据源变化且未在拖拽中时同步草稿顺序
    LaunchedEffect(shifts) {
        if (draggingId == null) draftIds = shifts.sortedBy { it.sort }.map { it.id }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page),
        ) {
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shifts_drag_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // 新建：tonal 胶囊
                val createInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = engineShape(Radius.pill),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.pressScale(createInteraction, pressedScale = 0.92f),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(
                                interactionSource = createInteraction,
                                indication = LocalIndication.current,
                                onClick = { showCreate = true },
                            )
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_add), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(IconSpec.inline),
                        )
                        Text(
                            stringResource(R.string.shifts_add),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.m))

            // 班次排序卡：长按拖动（整行）+ 显示开关 + ⋮ 菜单
            SectionCard {
                Column(Modifier.padding(vertical = Spacing.xs)) {
                    if (draftIds.isEmpty()) {
                        Text(
                            stringResource(R.string.shifts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.l, horizontal = Spacing.l),
                        )
                    }
                    draftIds.forEachIndexed { index, id ->
                        val shift = shifts.firstOrNull { it.id == id } ?: return@forEachIndexed
                        key(id) {
                            val currentIndex by rememberUpdatedState(index)
                            val currentSize by rememberUpdatedState(draftIds.size)
                            val isDragged = draggingId == id
                            val dragScale by animateFloatAsState(
                                targetValue = if (isDragged) 1.05f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "shiftDragScale",
                            )
                            val cellPx = with(density) { 56.dp.toPx() }

                            Column {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragY else 0f
                                            scaleX = dragScale
                                            scaleY = dragScale
                                        }
                                        .zIndex(if (isDragged) 1f else 0f)
                                        // 整行都可以长按拖动（原六点手柄已删，见 docs/03 §14）
                                        .pointerInput(id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dragFrom = currentIndex
                                                    draggingId = id
                                                    dragY = 0f
                                                    dragMoved = false
                                                },
                                                onDragEnd = {
                                                    if (dragFrom >= 0) {
                                                        val moved = dragMoved
                                                        dragFrom = -1
                                                        draggingId = null
                                                        dragY = 0f
                                                        dragMoved = false
                                                        if (moved) vm.reorder(draftIds)
                                                    }
                                                },
                                                onDragCancel = {
                                                    if (dragFrom >= 0) {
                                                        val moved = dragMoved
                                                        dragFrom = -1
                                                        draggingId = null
                                                        dragY = 0f
                                                        dragMoved = false
                                                        if (moved) vm.reorder(draftIds)
                                                    }
                                                },
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragY += dragAmount.y
                                                if (cellPx > 0 && dragFrom >= 0) {
                                                    var swapped = true
                                                    while (swapped) {
                                                        swapped = false
                                                        val f = dragFrom
                                                        if (dragY > cellPx * 0.5f) {
                                                            if (f + 1 < currentSize) {
                                                                draftIds = draftIds.toMutableList()
                                                                    .apply { add(f + 1, removeAt(f)) }
                                                                dragFrom = f + 1
                                                                dragY -= cellPx
                                                                dragMoved = true
                                                                swapped = true
                                                            } else {
                                                                dragY = cellPx * 0.5f
                                                            }
                                                        } else if (dragY < -cellPx * 0.5f) {
                                                            if (f - 1 >= 0) {
                                                                draftIds = draftIds.toMutableList()
                                                                    .apply { add(f - 1, removeAt(f)) }
                                                                dragFrom = f - 1
                                                                dragY += cellPx
                                                                dragMoved = true
                                                                swapped = true
                                                            } else {
                                                                dragY = -cellPx * 0.5f
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 行主体点击 = 改名（把原 ⋮ 菜单里的「改名」提到明面；行整块长按才拖，与点按不冲突）
                                        val bodyInteraction = remember { MutableInteractionSource() }
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .pressScale(bodyInteraction, pressedScale = 0.98f)
                                                // ⚠️ 这里**不能**加 `.clip(engineShape(Radius.small))`（v0.7.8.5 删除）：
                                                //    这个点击区**没有底色/描边**，圆角本来就看不出，但 clip 会把整个区域
                                                //    裁成圆角矩形 —— 而本区高度只有名称+副标题两行（≈36dp）、圆角半径 12dp，
                                                //    左上圆弧恰好切进**名称首字**（用户报「班次名称显示不全，被圆角切割了」，
                                                //    且「即使没被挤压也会被切割」）。区高远大于圆角时才无感，此处不是。
                                                //    去掉后涟漪变成直角矩形——该区本就无底色，观感无碍。
                                                .clickable(
                                                    interactionSource = bodyInteraction,
                                                    indication = LocalIndication.current,
                                                ) {
                                                    renaming = shift
                                                    renameText = shift.name
                                                },
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                            ) {
                                                Text(
                                                    shift.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    // 名称限一行 + 省略号 + `weight(1f, fill = false)`：
                                                    // 徽标先被量、永不被长名字挤出，长名也不会把 56dp 拖拽格子撑破
                                                    // （同工地项目行，见 docs/03 §14）
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                // 默认班次徽标：告诉用户「拖到第一个就是默认」（记加班预选的就是它）
                                                if (shift.id == defaultShiftId) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.tertiaryContainer,
                                                                engineShape(Radius.pill),
                                                            )
                                                            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.shifts_default_badge),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        )
                                                    }
                                                }
                                                if (shift.rest) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                engineShape(Radius.pill),
                                                            )
                                                            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.shifts_rest_badge),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                buildString {
                                                    append(if (shift.builtin) stringResource(R.string.shifts_builtin) else stringResource(R.string.shifts_custom))
                                                    if (shift.hidden) append(stringResource(R.string.shifts_hidden_suffix))
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                // 副标题同样恰好一行：删除钮展开成「取消/确认删除」时会挤窄本列，
                                                // 折行就超出 56dp 拖拽格（与名称同一个坑）
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        // 删除（仅自定义班次）：原地确认（只确认，不原地撤销：删除后本行即从列表消失）
                                        // → 撤销由底部信息提示窗（Snackbar）承担
                                        if (!shift.builtin) {
                                            InlineConfirmButton(
                                                idleText = stringResource(R.string.shifts_delete),
                                                confirmText = stringResource(R.string.shifts_delete_confirm),
                                                cancelText = stringResource(R.string.shifts_cancel),
                                                undoText = stringResource(R.string.shifts_undo),
                                                onConfirm = { vm.delete(shift) },
                                                style = InlineConfirmStyle.Compact,
                                            )
                                            Spacer(Modifier.width(Spacing.xs))
                                        }
                                        // 显示开关：开=显示，关=隐藏
                                        JiabanSwitch(
                                            checked = !shift.hidden,
                                            onCheckedChange = { vm.setHidden(shift.id, !shift.hidden) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        // 结果提示：悬浮在页面底部（而非内联在列表流里）
        MessageSnackbarHost(
            hostState = snackbarHostState,
            isError = snackbarIsError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 新建
    if (showCreate) {
        ShiftNameDialog(
            title = stringResource(R.string.shifts_create_title),
            label = stringResource(R.string.shifts_name_label),
            value = newName,
            onValueChange = { newName = it.take(10) },
            onConfirm = {
                vm.create(newName)
                newName = ""
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    // 改名
    renaming?.let { target ->
        ShiftNameDialog(
            title = stringResource(R.string.shifts_rename_title),
            label = stringResource(R.string.shifts_rename_label),
            value = renameText,
            onValueChange = { renameText = it.take(10) },
            onConfirm = {
                vm.rename(target.id, renameText)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    // 删除（二次确认；预置班次不提供入口）
}


/** 班次名称输入弹窗（新建/改名共用）：20dp 圆角 + 浮动 label + 单行输入 */
@Composable
private fun ShiftNameDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FloatingLabelTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.shifts_ok),
                onClick = onConfirm,
                role = JiabanButtonRole.GHOST,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.shifts_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
