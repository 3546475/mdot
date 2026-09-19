package com.mdot.app.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.mdot.app.core.designsystem.component.RecordCircleButton
import com.mdot.app.core.designsystem.component.RecordPillButton
import com.mdot.app.core.designsystem.component.rememberBackdropBlurState
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SlotRegistry
import com.mdot.app.core.designsystem.component.SlotSpec
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.BottomBarConfig

/** 底栏配置：预览纯展示；功能卡片整合开关 + 垂直拖拽排序；首页固定不在卡片内 */
@Composable
fun BottomBarPane(
    vm: BottomBarViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val iconOnly by vm.iconOnly.collectAsStateWithLifecycle()
    val sideAction by vm.sideAction.collectAsStateWithLifecycle()
    val fixedWidth by vm.fixedWidth.collectAsStateWithLifecycle()
    val frosted by vm.frosted.collectAsStateWithLifecycle()

    // 本地编辑草稿（**完整顺序**，含已关闭项）：拖动实时换位先改草稿，松手一次性持久化
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

        // ---- 外观选项（可折叠卡：标题行 + 四行开关）----
        // 三条动画**共用同一条曲线**（motionScheme.fastSpatialSpec）：高度（expand/shrinkVertically）、
        // 内容透明度（fadeIn/fadeOut）、箭头角度（rotationZ）——三者同拍，收展不脱节。
        // 箭头角度在 graphicsLayer（绘制期）读取，避免逐帧重组；展开状态用 rememberSaveable 跨旋转保留。
        // 出厂默认**折叠**（用户定，2026-09-20）
        var optionsExpanded by rememberSaveable { mutableStateOf(false) }
        // 同一条曲线，按值域取（expand/shrinkVertically 需要 IntSize 版本的 spec）
        val expandSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
        val arrowRotation = animateFloatAsState(
            targetValue = if (optionsExpanded) 180f else 0f,
            animationSpec = expandSpec,
            label = "barOptionsArrow",
        )
        SectionCard {
            Column {
                // 整行可点但**不要涟漪**：折叠卡是容器，整卡涟漪会显得吵（用户要求）
                val headerInteraction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = headerInteraction,
                            indication = null,
                        ) { optionsExpanded = !optionsExpanded }
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.appearance_options),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_ms_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = arrowRotation.value },
                    )
                }
                AnimatedVisibility(
                    visible = optionsExpanded,
                    enter = fadeIn(expandSpec) +
                        expandVertically(animationSpec = sizeSpec),
                    exit = fadeOut(expandSpec) +
                        shrinkVertically(animationSpec = sizeSpec),
                ) {
                    Column {
                        BarOptionSwitch(R.string.appearance_frosted, frosted, vm::setFrosted)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BarOptionSwitch(R.string.appearance_icon_only, iconOnly, vm::setIconOnly)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BarOptionSwitch(R.string.appearance_side_action, sideAction, vm::setSideAction)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BarOptionSwitch(R.string.appearance_fixed_width, fixedWidth, vm::setFixedWidth)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))

        // ---- 预览（纯展示，不可交互） ----
        Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.l))
        BottomBarPreview(
            // ⚠️ 传「实际显示槽位」而不是完整顺序：draft 是含已关闭项的完整顺序，
            // 直接传会把关掉的项也画出来（用户反馈的预览 bug）。这里复用模型计算（含固定首页）。
            slots = BottomBarConfig(order = draft, disabled = config.disabled).slots,
            iconOnly = iconOnly,
            sideAction = sideAction,
            fixedWidth = fixedWidth,
            frosted = frosted,
        )
        Spacer(Modifier.height(Spacing.l))

        // ---- 功能配置卡片：开关 + 拖拽排序（v0.6.9 起去掉上方「功能」标题） ----
        SlotConfigCard(
            order = draft,
            disabled = config.disabled,
            onSwap = { from, to ->
                val next = draft.toMutableList()
                next.add(to, next.removeAt(from))
                draft = next
            },
            onDragEnd = { vm.setOrder(draft) },
            onToggle = { id, enabled -> vm.setEnabled(id, enabled) },
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 底栏外观开关行：整行可点（`toggleable` role=Switch），Switch 自身不接管点击（避免双重语义） */
@Composable
private fun BarOptionSwitch(
    labelRes: Int,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * 底栏预览：**与真实底栏完全同一套实现**——槽位与胶囊复用 [JiabanBottomBar]，
 * 记加班按钮复用 [RecordPillButton] / [RecordCircleButton]（曾因预览另画简化版，
 * 改了真实底栏就漏改预览：实心蓝圆钮 + 实色胶囊）。
 *
 * 毛玻璃：传入的 [previewBlurState] **不挂源**（只呈现玻璃底色渐变 + 内高光 + 去投影 + 衬托渐变）——
 * 与真实底栏在「身后无内容」（contentBottomPadding 让出的空白区）时的观感一致；
 * 预览区无法真实采样页面（挂源会把预览自身也录进去 → 自采样），故不接源。
 */
@Composable
private fun BottomBarPreview(
    slots: List<String>,
    iconOnly: Boolean,
    sideAction: Boolean,
    fixedWidth: Boolean,
    frosted: Boolean,
) {
    val density = LocalDensity.current
    val slotSpecs = slots.mapNotNull { SlotRegistry.resolve(it) }
    // JiabanBottomBar 内部有 bottomMargin padding，预览中向上偏移抵消
    val offsetY = with(density) { -BottomBarSpec.bottomMargin.toPx() }
    // 毛玻璃源（空源：只走玻璃样式，不采样）
    val previewBlurState = rememberBackdropBlurState()
    val blurState = if (frosted) previewBlurState else null
    // 记加班按钮：与真实底栏同一实现（不播放入场动画）
    val recordPill: @Composable () -> Unit = {
        RecordPillButton(onRecord = {}, playEntrance = false, onEntranceDone = {})
    }
    val recordCircle: @Composable () -> Unit = {
        RecordCircleButton(
            onRecord = {},
            playEntrance = false,
            onEntranceDone = {},
            backdropBlur = blurState,
        )
    }
    // ⚠️ 预览容器宽度 = **真实屏幕宽度**：底栏的自适应宽度按容器宽算，若用卡片/页面内边距后的窄容器，
    // 算出的底栏会比真机窄（固定 4 格 + 右置时实测差 ≈13dp）→ 破坏「预览 = 真实底栏」。
    // 超出卡片的部分是透明背景、被卡片裁掉，不影响观感。
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        // requiredWidth（而非 width）：必须**突破卡片内边距的父约束**，否则会被钳到卡片内宽（实测 992px），
        // 底栏自适应宽度仍按窄容器算 → 与真实底栏不一致
        Box(Modifier.requiredWidth(screenWidth)) {
            JiabanBottomBar(
                slots = slotSpecs,
                selectedRoute = slotSpecs.firstOrNull()?.route,
                visible = true,
                onSlotClick = {},
                iconOnly = iconOnly,
                showIndicator = true,
                centerAction = if (sideAction) null else recordPill,
                sideAction = if (sideAction) recordCircle else null,
                backdropBlur = blurState,
                fixedCells = if (fixedWidth) BottomBarSpec.fixedCellCount else 0,
                modifier = Modifier.graphicsLayer { translationY = offsetY },
            )
        }
    }
}

/**
 * 槽位卡片：**单列表**（对齐班次管理页卡片）——全部槽位同列，每行都能拖拽排序 + 开关。
 * 开关只影响是否显示（关闭的只是不在底栏出现），**不影响顺序**；首页固定首位、不在此列表内。
 */
@Composable
private fun SlotConfigCard(
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

    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(R.string.appearance_slots_home_fixed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = 6.dp),
            )
            // 单 key 循环：开关切换**不移动行**（顺序只由拖拽改），Switch 身份稳定 → 切换动画正常
            order.forEachIndexed { index, id ->
                val spec = SlotRegistry.resolve(id) ?: return@forEachIndexed
                val isOn = id !in disabled
                key(id) {
                    val currentIndex by rememberUpdatedState(index)
                    val currentSize by rememberUpdatedState(order.size)
                    val isDragged = draggingId == id
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragged) 1.05f else 1f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "slotDragScale",
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
                        SlotConfigRowContent(
                            spec = spec,
                            isOn = isOn,
                            isDragged = isDragged,
                            draggable = true,
                            onToggle = { onToggle(id, !isOn) },
                        )
                    }
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
    // 颜色随开关切换过渡（motionScheme effects spec，禁硬编码，硬规则 7）——原先瞬间跳变
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val contentColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = colorSpec,
        label = "slotRowContent",
    )
    val handleColor by animateColorAsState(
        targetValue = if (draggable) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        animationSpec = colorSpec,
        label = "slotRowHandle",
    )

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


