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
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.HomeCardsConfig

/**
 * 首页卡片配置（v0.6.0 首页卡片可编辑）：显示中（拖拽排序 + 开关）/ 已隐藏（开关回开）。
 * 交互与视觉对齐底栏配置页；拖拽缩放动效走 MaterialTheme.motionScheme 弹簧 specs。
 */
@Composable
fun HomeCardsScreen(
    onBack: () -> Unit,
    vm: HomeCardsViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()

    // 本地编辑草稿：拖动实时换位先改草稿，松手一次性持久化（同底栏配置页）。
    // 未配置时以 DEFAULT_CARDS 为基线（与首页实际显示一致：热点图/本周柱状默认隐藏）
    var draft by remember { mutableStateOf(config.cards ?: HomeCardsConfig.DEFAULT_CARDS) }
    LaunchedEffect(config.cards) {
        val saved = config.cards
        if (saved != null && draft != saved) draft = saved
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.home_cards_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.s))

        Text(
            stringResource(R.string.home_cards_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))

        Text(stringResource(R.string.home_cards_visible_heading), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        HomeCardsConfigCard(
            draft = draft,
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
            onDragEnd = { vm.applyOrder(draft) },
            onToggle = { id -> vm.toggle(id) },
        )

        // 已隐藏（POOL 中不在 draft 的项，顺序按 POOL）
        val hidden = HomeCardsConfig.POOL.filter { it !in draft }
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.l))
            Text(stringResource(R.string.home_cards_hidden_heading), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.s))
            SectionCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    hidden.forEach { id ->
                        val spec = HomeCardRegistry.resolveSpec(id) ?: return@forEach
                        Box(
                            Modifier.fillMaxWidth().height(56.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            HomeCardRow(
                                spec = spec,
                                isOn = false,
                                draggable = false,
                                onToggle = { vm.toggle(id) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 显示中卡片：拖拽排序（手柄直接拖，无需长按）+ 开关；至少保留一张（VM 层兜底） */
@Composable
private fun HomeCardsConfigCard(
    draft: List<String>,
    onSwap: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: (id: String) -> Unit,
) {
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    SectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            draft.forEachIndexed { index, id ->
                val spec = HomeCardRegistry.resolveSpec(id) ?: return@forEachIndexed
                key(id) {
                    val currentIndex by rememberUpdatedState(index)
                    val currentSize by rememberUpdatedState(draft.size)
                    val isDragged = draggingId == id
                    // M3E：拖拽放大走 motionScheme 弹簧（空间类）
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
                                isOn = true,
                                draggable = true,
                                onToggle = { onToggle(id) },
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
    onToggle: () -> Unit,
) {
    val contentColor = if (isOn) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val handleColor = if (draggable) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

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
                .background(
                    if (isOn) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(Radius.small),
                ),
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
        Switch(checked = isOn, onCheckedChange = { onToggle() })
    }
}
