package com.mdot.app.feature.settings

import com.mdot.app.core.designsystem.miuix.ConfigRowIcon
import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.component.JiabanSwitch
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.EngineIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ChoicePillOption
import com.mdot.app.core.designsystem.component.ChoicePillRow
import com.mdot.app.core.designsystem.component.HomeCardContentRegistry
import com.mdot.app.core.designsystem.component.HomeCardContentUi
import com.mdot.app.core.designsystem.component.HomeCardRegistry
import com.mdot.app.core.designsystem.component.HomeCardSpec
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.HomeCardContents
import com.mdot.app.domain.model.HomeCardsConfig
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole

/**
 * 首页卡片配置（v0.6.0 首页卡片可编辑）：显示中（拖拽排序 + 开关）/ 已隐藏（开关回开）。
 * 交互与视觉对齐底栏配置页；拖拽缩放动效走 MaterialTheme.motionScheme 弹簧 specs。
 */
/** 首页卡片配置内容页（首页卡片+底栏合并页的第 1 页签） */
@Composable
fun HomeCardsPane(
    vm: HomeCardsViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()

    // 正在编辑内容的卡片 id（卡片内容编辑；null = 没在编辑）
    var editingCard by remember { mutableStateOf<String?>(null) }

    // 本地编辑草稿（**完整顺序**，含已隐藏项）：拖动实时换位先改草稿，松手一次性持久化（同底栏配置页）
    var draft by remember { mutableStateOf(config.order) }
    LaunchedEffect(config.order) {
        if (draft != config.order) draft = config.order
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))

        // 提示行：**只写手势本身**（用户 2026-09-22 要「言简意赅」）——
        // 与班次页 / 工地页同款（labelSmall + onSurfaceVariant），不再复述“数据区固定显示”那种自我说明
        Text(
            stringResource(R.string.appearance_drag_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))

        // 卡片总表：单列表（对齐底栏配置页 / 班次卡片）——全部卡片同列，每行都能拖拽 + 开关
        HomeCardsConfigCard(
            order = draft,
            disabled = config.disabled,
            // 把 from 处的卡片移动到 to（均为 0-based；先取后插，越界双向钳制）
            onSwap = { from, to ->
                val next = draft.toMutableList()
                val f = from.coerceIn(0, next.lastIndex)
                val t = to.coerceIn(0, next.lastIndex)
                if (f != t) {
                    next.add(t, next.removeAt(f))
                    draft = next
                }
            },
            onDragEnd = { vm.setOrder(draft) },
            onToggle = { id, enabled -> vm.setEnabled(id, enabled) },
            onEditContent = { editingCard = it },
        )
        Spacer(Modifier.height(Spacing.xl))
    }

    // 卡片内容选项：单选点一下即生效并收起；多选（快捷入口）改完点「确定」
    editingCard?.let { cardId ->
        HomeCardContentDialog(
            cardId = cardId,
            current = config.contentList(cardId),
            onApply = { vm.setContents(cardId, it) },
            onDismiss = { editingCard = null },
        )
    }
}

/**
 * 卡片内容选项（卡片内容编辑）：
 * - **单选**（数据区）= `ChoicePillRow` 图标药丸（docs/03 §16），点一下即生效并收起；
 * - **多选**（快捷入口）= 勾选行，改完点「确定」。
 * 多选不用 `ChoicePillRow`：那是多选一的形状，五个选项挤一行也会把药丸压糊。
 */
@Composable
private fun HomeCardContentDialog(
    cardId: String,
    current: List<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = HomeCardContentRegistry.of(cardId)
    if (options.isEmpty()) return
    val multi = HomeCardContents.isMultiSelect(cardId)
    // 多选用本地草稿（弹层内先改、确定才落盘，取消能真取消）
    var draft by remember(cardId) { mutableStateOf(current) }
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (multi) R.string.home_card_content_entries_title else R.string.home_card_content_title))
        },
        text = {
            if (multi) {
                Column {
                    options.forEach { opt ->
                        val on = opt.id in draft
                        EntryChoiceRow(
                            ui = opt,
                            checked = on,
                            // 至少留一个：全关掉首页那张卡就空了
                            onToggle = {
                                draft = if (on) (draft - opt.id).ifEmpty { draft } else draft + opt.id
                            },
                        )
                    }
                }
            } else {
                ChoicePillRow(
                    options = options.map { ChoicePillOption(iconRes = it.iconRes, labelRes = it.labelRes) },
                    selected = options.indexOfFirst { it.id in draft }.coerceAtLeast(0),
                    onSelect = { index ->
                        onApply(listOf(options[index].id))
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {
            if (multi) {
                JiabanButton(
                    text = stringResource(R.string.home_card_content_done),
                    onClick = { onApply(draft); onDismiss() },
                    role = JiabanButtonRole.GHOST,
                )
            } else {
                JiabanButton(
                    text = stringResource(R.string.home_card_content_cancel),
                    onClick = onDismiss,
                    role = JiabanButtonRole.GHOST,
                )
            }
        },
    )
}

/** 多选用的勾选行：图标 tonal 方块 + 名称 + 右侧勾（整行可点） */
@Composable
private fun EntryChoiceRow(ui: HomeCardContentUi, checked: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onValueChange = { onToggle() },
            )
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 行图标：MIUIX 裸图标（与「我的」页统一，用户 2026-09-30）/ MD3 瓦片（底色随开关浅深）
        ConfigRowIcon(
            icon = painterResource(ui.iconRes),
            tileColor = if (checked) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.width(Spacing.m))
        Text(stringResource(ui.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (checked) {
            Icon(
                EngineIcons.check(), null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSpec.inline),
            )
        }
    }
}

/**
 * 卡片总表：**单列表**（对齐底栏配置页 / 班次管理页卡片）——全部卡片同列，每行都能拖拽排序 + 开关。
 * 开关只影响是否显示（隐藏的只是不在首页出现），**不影响顺序**；「数据区」固定显示、不可隐藏。
 *
 * ⚠️ **拖动 = 长按后拖**（2026-09-22 用户定）：行首的六点抓手图标已**移除**，手势改
 * `detectDragGesturesAfterLongPress`——快速滑动仍归页面滚动，只有按住不动才进入拖拽；
 * 长按触发时给一次 `HapticFeedbackType.LongPress`（与底栏槽位卡、日历长按多选同手感）。
 * 两个好处与代价详见底栏页 `SlotConfigCard` 的同名注释。
 */
@Composable
private fun HomeCardsConfigCard(
    order: List<String>,
    disabled: List<String>,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    /** 点某张卡 → 改它显示什么（只对支持内容自定义的卡生效） */
    onEditContent: (String) -> Unit,
) {
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    SectionCard {
        Column(Modifier.padding(vertical = Spacing.xs)) {
            // 「数据区固定显示，不可关闭」的说明文字已删（2026-09-20）：该行本身就是一个
            // **已开启且不可点击**的开关，禁用态已经把这条规则说完了，再写一行灰字是重复。
            order.forEachIndexed { index, id ->
                val spec = HomeCardRegistry.resolveSpec(id) ?: return@forEachIndexed
                val isOn = id !in disabled
                // 「数据区」固定显示、不可关闭（保证首页至少有一张卡）
                val closable = id != HomeCardsConfig.DATA
                key(id) {
                    val currentIndex by rememberUpdatedState(index)
                    val currentSize by rememberUpdatedState(order.size)
                    val isDragged = draggingId == id
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragged) 1.05f else 1f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "homeCardDragScale",
                    )
                    val cellPx = with(density) { 56.dp.toPx() }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(com.mdot.app.core.designsystem.component.TopBarHeight)
                            .graphicsLayer {
                                translationY = if (isDragged) dragY else 0f
                                scaleX = dragScale
                                scaleY = dragScale
                            }
                            .zIndex(if (isDragged) 1f else 0f)
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
                                            if (moved) onDragEnd()
                                        }
                                    },
                                    onDragCancel = {
                                        if (dragFrom >= 0) {
                                            val moved = dragMoved
                                            dragFrom = -1
                                            draggingId = null
                                            dragY = 0f
                                            dragMoved = false
                                            if (moved) onDragEnd()
                                        }
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    // 长按版给的是 Offset（两个轴），只取纵向
                                    dragY += dragAmount.y
                                    if (cellPx > 0 && dragFrom >= 0) {
                                        var swapped = true
                                        while (swapped) {
                                            swapped = false
                                            val f = dragFrom
                                            if (dragY > cellPx * 0.5f) {
                                                if (f + 1 < currentSize) {
                                                    onSwap(f, f + 1)
                                                    dragFrom = f + 1
                                                    dragY -= cellPx
                                                    dragMoved = true
                                                    swapped = true
                                                } else {
                                                    dragY = cellPx * 0.5f
                                                }
                                            } else if (dragY < -cellPx * 0.5f) {
                                                if (f - 1 >= 0) {
                                                    onSwap(f, f - 1)
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
                        HomeCardRow(
                            spec = spec,
                            isOn = isOn,
                            closable = closable,
                            onToggle = { onToggle(id, !isOn) },
                            onEdit = if (HomeCardContents.supports(id)) ({ onEditContent(id) }) else null,
                        )
                    }
                }
            }
        }
    }
}

/** 单行：图标 tonal 方块 + 名称 + 开关（视觉对齐底栏配置行，开关切换）。
 *  可自定义内容的卡片（当前只「数据区」）**图标方块 + 名称 + 行尾铅笔整块可点**打开内容选项。
 *  铅笔也要能点——只当指示图标的写法被用户实测当成坏按钮（“修改按钮实际上不能点击”）。
 *  刻意不加 IconButton：行高被拖拽换位的 56dp 判定单元锁住，加 48dp 热区会把行撑高。 */
@Composable
private fun HomeCardRow(
    spec: HomeCardSpec,
    isOn: Boolean,
    /** 可关闭（「数据区」=false：开关置灰，保证首页至少一张卡） */
    closable: Boolean,
    onToggle: () -> Unit,
    /** 内容可自定义时给编辑入口；null = 该卡不支持内容自定义 */
    onEdit: (() -> Unit)? = null,
) {
    // 颜色随开关切换过渡（原先瞬间跳变）
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val contentColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = colorSpec,
        label = "homeCardRowContent",
    )
    val iconBoxColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = colorSpec,
        label = "homeCardRowIconBox",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rowInteraction = remember { MutableInteractionSource() }
        // 点击区 = 图标方块 + 名称 + 行尾铅笔（开关留在区外，免得想点编辑却拨了开关）
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onEdit != null) {
                        Modifier.clickable(
                            interactionSource = rowInteraction,
                            indication = LocalIndication.current,
                            onClick = onEdit,
                        )
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 行图标：MIUIX 裸图标（与「我的」页统一，用户 2026-09-30）/ MD3 瓦片（底色随开关浅深）
            ConfigRowIcon(icon = painterResource(spec.iconRes), tileColor = iconBoxColor)
            Spacer(Modifier.width(Spacing.m))
            Text(
                stringResource(spec.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            // 内容自定义的可见指示（点击走上方整块点击区，它自己也是那块的一部分）
            if (onEdit != null) {
                Icon(
                    painterResource(R.drawable.ic_ms_edit),
                    contentDescription = stringResource(R.string.home_card_content_edit_cd),
                    tint = contentColor,
                    modifier = Modifier.size(IconSpec.dense),
                )
                Spacer(Modifier.width(Spacing.s))
            }
        }
        JiabanSwitch(checked = isOn, onCheckedChange = if (closable) { { onToggle() } } else null, enabled = closable)
    }
}
