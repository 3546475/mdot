package com.mdot.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.key
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanBottomBar
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SlotRegistry
import com.mdot.app.core.designsystem.component.SlotSpec
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.BottomBarConfig

/** 底栏配置：预览纯展示；功能卡片整合开关 + 垂直拖拽排序；首页固定不在卡片内 */
@Composable
fun BottomBarScreen(
    onBack: () -> Unit,
    vm: BottomBarViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val iconOnly by vm.iconOnly.collectAsStateWithLifecycle()

    // 本地编辑草稿：拖动实时换位先改草稿，松手一次性持久化
    var draft by remember { mutableStateOf(config.slots) }
    LaunchedEffect(config.slots) {
        if (draft != config.slots) draft = config.slots
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.appearance_bottom_bar_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.s))

        // 仅图标开关
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.l, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_icon_only), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.appearance_icon_only_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = iconOnly, onCheckedChange = vm::setIconOnly)
            }
        }
        Spacer(Modifier.height(Spacing.m))

        // ---- 预览（纯展示，不可交互） ----
        Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.l))
        BottomBarPreview(slots = draft, iconOnly = iconOnly)
        Spacer(Modifier.height(Spacing.l))

        // ---- 功能配置卡片：开关 + 拖拽排序 ----
        Text(stringResource(R.string.appearance_functions), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        SlotConfigCard(
            draft = draft,
            onSwap = { from, to ->
                val next = draft.toMutableList()
                next.add(to, next.removeAt(from))
                draft = next
            },
            onDragEnd = {
                vm.applySlots(draft)
            },
            onToggle = { id -> vm.toggle(id) },
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 底栏预览：直接复用 JiabanBottomBar，与真实底栏完全一致（宽度随槽位数自适应） */
@Composable
private fun BottomBarPreview(slots: List<String>, iconOnly: Boolean) {
    val density = LocalDensity.current
    val slotSpecs = slots.mapNotNull { SlotRegistry.resolve(it) }
    // JiabanBottomBar 内部有 bottomMargin padding，预览中向上偏移抵消
    val offsetY = with(density) { -BottomBarSpec.bottomMargin.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        JiabanBottomBar(
            slots = slotSpecs,
            selectedRoute = slotSpecs.firstOrNull()?.route,
            visible = true,
            onSlotClick = {},
            iconOnly = iconOnly,
            showIndicator = true,
            modifier = Modifier.graphicsLayer { translationY = offsetY },
        )
    }
}

/**
 * 功能配置卡片：on 的项在上（可拖拽排序），off 的项在下（不可拖）。
 * 首页固定不在此卡片内。拖拽手柄直接拖动（无需长按），只有 on 的项可拖。
 * off→on 自动移到最后一个 on 后面。
 */
@Composable
private fun SlotConfigCard(
    draft: List<String>,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: (id: String) -> Unit,
) {
    // 首页固定在 slots[0]，不参与卡片配置
    val onItems = draft.filter { it != "home" }
    val offItems = BottomBarConfig.POOL.filter { it != "home" && it !in draft }

    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeight = 0 }, // 重置，由首行测量
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            // on 的项（可拖拽排序）——用 key(id) 保持 item 身份，避免换位时 pointerInput 协程被取消
            onItems.forEachIndexed { index, id ->
                val spec = SlotRegistry.resolve(id) ?: return@forEachIndexed
                key(id) {
                    val currentIndex by rememberUpdatedState(index)
                    val currentOnSize by rememberUpdatedState(onItems.size)
                    val isDragged = draggingId == id
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragged) 1.05f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dragScale",
                    )
                    val cellPx = with(density) { 56.dp.toPx() }

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
                                    dragY += dragAmount
                                    if (cellPx > 0 && dragFrom >= 0) {
                                        var swapped = true
                                        while (swapped) {
                                            swapped = false
                                            val f = dragFrom
                                            if (dragY > cellPx * 0.5f) {
                                                if (f + 1 < currentOnSize) {
                                                    onSwap(f + 1, f + 2)
                                                    dragFrom = f + 1
                                                    dragY -= cellPx
                                                    dragMoved = true
                                                    swapped = true
                                                } else {
                                                    dragY = cellPx * 0.5f
                                                }
                                            } else if (dragY < -cellPx * 0.5f) {
                                                if (f - 1 >= 0) {
                                                    onSwap(f + 1, f)
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
                        SlotConfigRowContent(
                            spec = spec,
                            isOn = true,
                            isDragged = isDragged,
                            draggable = true,
                            onToggle = { onToggle(id) },
                        )
                    }
                }
            }

            // 分隔线（on/off 之间）
            if (offItems.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
            }

            // off 的项（不可拖）
            offItems.forEach { id ->
                val spec = SlotRegistry.resolve(id) ?: return@forEach
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SlotConfigRowContent(
                        spec = spec,
                        isOn = false,
                        isDragged = false,
                        draggable = false,
                        onToggle = { onToggle(id) },
                    )
                }
            }
        }
    }
}

/** 单行内容：拖拽手柄 + 图标文字 + switch */
@Composable
private fun SlotConfigRowContent(
    spec: SlotSpec,
    isOn: Boolean,
    isDragged: Boolean,
    draggable: Boolean,
    onToggle: () -> Unit,
) {
    val contentColor = if (isOn) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val handleColor = if (draggable) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 拖拽手柄（仅 on 的项可拖）
        Icon(
            painterResource(R.drawable.ic_ms_drag_indicator),
            contentDescription = if (draggable) stringResource(R.string.appearance_drag_reorder) else null,
            tint = handleColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Icon(
            painterResource(spec.iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            stringResource(spec.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isOn,
            onCheckedChange = { onToggle() },
            enabled = true,
        )
    }
}


