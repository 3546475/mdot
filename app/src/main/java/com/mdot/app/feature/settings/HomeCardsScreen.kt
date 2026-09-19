package com.mdot.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.HomeCardRegistry
import com.mdot.app.core.designsystem.component.HomeCardSpec
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.HomeCardsConfig

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

        Text(
            stringResource(R.string.home_cards_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))

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
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * 卡片总表：**单列表**（对齐底栏配置页 / 班次管理页卡片）——全部卡片同列，每行都能拖拽排序 + 开关。
 * 开关只影响是否显示（隐藏的只是不在首页出现），**不影响顺序**；「数据区」固定显示、不可隐藏。
 */
@Composable
private fun HomeCardsConfigCard(
    order: List<String>,
    disabled: List<String>,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    SectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(R.string.home_cards_data_fixed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = 6.dp),
            )
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
                            draggable = true,
                            closable = closable,
                            onToggle = { onToggle(id, !isOn) },
                        )
                    }
                }
            }
        }
    }
}

/** 单行：拖拽手柄 + 图标 tonal 方块 + 名称 + 开关（视觉对齐底栏配置行，开关切换） */
@Composable
private fun HomeCardRow(
    spec: HomeCardSpec,
    isOn: Boolean,
    draggable: Boolean,
    /** 可关闭（「数据区」=false：开关置灰，保证首页至少一张卡） */
    closable: Boolean,
    onToggle: () -> Unit,
) {
    // 颜色随开关切换过渡（原先瞬间跳变）
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val contentColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = colorSpec,
        label = "homeCardRowContent",
    )
    val handleColor by animateColorAsState(
        targetValue = if (draggable) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        animationSpec = colorSpec,
        label = "homeCardRowHandle",
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_ms_drag_indicator),
            contentDescription = if (draggable) stringResource(R.string.appearance_drag_reorder) else null,
            tint = handleColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        // 图标 tonal 容器（03 文档 §3.4）：secondaryContainer 底 + primary 图标
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(iconBoxColor, RoundedCornerShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(spec.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            stringResource(spec.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = isOn, onCheckedChange = if (closable) { { onToggle() } } else null, enabled = closable)
    }
}
