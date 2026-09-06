package com.mdot.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ConfirmDialog
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.model.Shift
import java.time.DayOfWeek

/** 考勤周期（F7-3：1–31 号起始日；29–31 在天数不足的月份自动落到月末） */
@Composable
fun CycleScreen(onBack: () -> Unit, vm: CycleViewModel = hiltViewModel()) {
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val example = stringResource(R.string.cycle_period_example, remember(anchor) { buildExample(anchor) })

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.cycle_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

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
                    shape = RoundedCornerShape(Radius.card),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        example,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = 10.dp),
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
                (1..31).chunked(7).forEach { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                selected = anchor == day,
                                modifier = Modifier.weight(1f),
                            ) { vm.set(day) }
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
    val shape = RoundedCornerShape(Radius.button)
    val interaction = remember { MutableInteractionSource() }
    // 选中态平滑过渡 + 按压缩放
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(Duration.normal),
        label = "dayBg",
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(Duration.normal),
        label = "dayStroke",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(Duration.normal),
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

/** 紧凑 34dp 图标按钮（班次行排序/菜单用）；涟漪已裁剪到圆角内 */
@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .pressScale(interaction, pressedScale = 0.85f)
            .clip(RoundedCornerShape(Radius.small))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 工作日设定（F7-4：影响档位自动判定的兜底） */
@Composable
fun WorkdaysScreen(onBack: () -> Unit, vm: WorkdaysViewModel = hiltViewModel()) {
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
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.workdays_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.xs))
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
                        Checkbox(checked = day in workdays, onCheckedChange = { vm.toggle(day) })
                    }
                }
            }
        }
    }
}

/** 班次管理（F7-5：预置可隐藏不可删；自定义可增删改、排序）；
 *  仿底栏配置卡：左侧六点手柄拖动排序，Switch 控制显示/隐藏，⋮ 菜单收纳改名/删除 */
@Composable
fun ShiftsScreen(onBack: () -> Unit, vm: ShiftsViewModel = hiltViewModel()) {
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Shift?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Shift?>(null) }
    var menuFor by remember { mutableStateOf<Shift?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 拖拽排序状态（仿底栏配置卡）
    var draftIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // 数据源变化且未在拖拽中时同步草稿顺序
    LaunchedEffect(shifts) {
        if (draggingId == null) draftIds = shifts.sortedBy { it.sort }.map { it.id }
    }

    message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page),
        ) {
            JiabanTopBar(title = stringResource(R.string.shifts_title), onBack = onBack)
            Spacer(Modifier.height(Spacing.xs))
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
                    shape = RoundedCornerShape(Radius.pill),
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
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Add, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp),
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

            // 班次排序卡：六点手柄拖动 + 显示开关 + ⋮ 菜单
            SectionCard {
                Column(Modifier.padding(vertical = 4.dp)) {
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
                                        .zIndex(if (isDragged) 1f else 0f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 六点拖动手柄：拖动手势只挂手柄上，不挡页面滚动
                                        Box(
                                            Modifier
                                                .size(width = 28.dp, height = 56.dp)
                                                .pointerInput(id) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = {
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
                                                        dragY += dragAmount
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
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.DragIndicator,
                                                contentDescription = stringResource(R.string.shifts_drag_reorder),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(Spacing.m))
                                        Column(Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(shift.name, style = MaterialTheme.typography.titleSmall)
                                                if (shift.rest) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                RoundedCornerShape(Radius.pill),
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 2.dp),
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
                                            )
                                        }
                                        // 显示开关：开=显示，关=隐藏
                                        Switch(
                                            checked = !shift.hidden,
                                            onCheckedChange = { vm.setHidden(shift.id, !shift.hidden) },
                                        )
                                        Spacer(Modifier.width(Spacing.xs))
                                        Box {
                                            CompactIconButton(Icons.Outlined.MoreVert, stringResource(R.string.shifts_more_actions)) { menuFor = shift }
                                            DropdownMenu(
                                                expanded = menuFor?.id == shift.id,
                                                onDismissRequest = { menuFor = null },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.shifts_rename)) },
                                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                                    onClick = {
                                                        menuFor = null
                                                        renaming = shift
                                                        renameText = shift.name
                                                    },
                                                )
                                                if (!shift.builtin) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.shifts_delete), color = MaterialTheme.colorScheme.error) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Outlined.Delete, null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        onClick = {
                                                            menuFor = null
                                                            deleting = shift
                                                        },
                                                    )
                                                }
                                            }
                                        }
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.l),
        )
    }

    // 新建
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.shifts_create_title)) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it.take(10) }, label = { Text(stringResource(R.string.shifts_name_label)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.create(newName)
                    newName = ""
                    showCreate = false
                }) { Text(stringResource(R.string.shifts_ok)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.shifts_cancel)) } },
        )
    }

    // 改名
    renaming?.let { target ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.shifts_rename_title, target.name)) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it.take(10) })
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(target.id, renameText)
                    renaming = null
                }) { Text(stringResource(R.string.shifts_ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.shifts_cancel)) } },
        )
    }

    // 删除（二次确认；预置班次不提供入口）
    deleting?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.shifts_delete_title, target.name),
            text = stringResource(R.string.shifts_delete_text),
            onConfirm = {
                vm.delete(target.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
