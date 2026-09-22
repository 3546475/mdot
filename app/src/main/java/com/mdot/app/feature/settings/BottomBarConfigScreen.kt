package com.mdot.app.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanBottomBar
import com.mdot.app.core.designsystem.component.OptionPillCard
import com.mdot.app.core.designsystem.component.OptionPillItem
import com.mdot.app.core.designsystem.component.RecordCircleButton
import com.mdot.app.core.designsystem.component.RecordPillButton
import com.mdot.app.core.designsystem.component.rememberBackdropBlurState
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
        Spacer(Modifier.height(Spacing.l))

        // ---- More（原「更多选项」，2026-09-22 改名；同日定：**移到页面最下方**）----
        // 形态移植自 Bencho 的 Assignees 组件（数值/令牌映射/每个数字的理由见 OptionPillSpec）。
        // 药丸**就是读数**：左侧叠放已开启项的圆形图标，全关时写「More」；点开后在下方就地撑出列表卡。
        // ⚠️ **不包分区卡、也不加分区标题**（用户定）；曾经把它放最上方并居中试过（“方案 C”），
        // 用户看真机样张后改为**放最下方**：它是这一页最细的调参（四项开关），
        // 排在「预览」与「槽位列表」之后不打断主流程的阅读；收起/展开的动效不变。
        // 出厂默认**折叠**（用户定，2026-09-20），由组件内部 rememberSaveable 保存。
        OptionPillCard(
            title = stringResource(R.string.appearance_options),
            items = listOf(
                OptionPillItem(
                    id = "frosted",
                    iconRes = R.drawable.ic_ms_blur_on,
                    label = stringResource(R.string.appearance_frosted),
                    checked = frosted,
                ),
                OptionPillItem(
                    id = "iconOnly",
                    iconRes = R.drawable.ic_ms_grid_view,
                    label = stringResource(R.string.appearance_icon_only),
                    checked = iconOnly,
                ),
                OptionPillItem(
                    id = "sideAction",
                    iconRes = R.drawable.ic_ms_align_horizontal_right,
                    label = stringResource(R.string.appearance_side_action),
                    checked = sideAction,
                ),
                OptionPillItem(
                    id = "fixedWidth",
                    iconRes = R.drawable.ic_ms_straighten,
                    label = stringResource(R.string.appearance_fixed_width),
                    checked = fixedWidth,
                ),
            ),
            onToggle = { id, enabled ->
                when (id) {
                    "frosted" -> vm.setFrosted(enabled)
                    "iconOnly" -> vm.setIconOnly(enabled)
                    "sideAction" -> vm.setSideAction(enabled)
                    "fixedWidth" -> vm.setFixedWidth(enabled)
                }
            },
        )
        Spacer(Modifier.height(Spacing.xl))
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
 *
 * **纯展示**：槽位传 `onSlotClick = null`，不挂 clickable、不播按压缩放——
 * 预览是展示件，不做「按了会缩但无响应」的假可点。
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
    // 记加班按钮：与真实底栏同一实现（不播放入场动画）；onRecord = null → 预览中纯展示、不可点
    val recordPill: @Composable () -> Unit = {
        RecordPillButton(onRecord = null, playEntrance = false, onEntranceDone = {})
    }
    val recordCircle: @Composable () -> Unit = {
        RecordCircleButton(
            onRecord = null,
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
                // null = 纯展示：预览槽位不响应点击（原先传空 lambda，会出现
                // 「按下去缩一下、松手什么都不发生」的假可点）
                onSlotClick = null,
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
 *
 * ⚠️ **拖动 = 长按后拖、且整行可拖、关闭项也能拖**（2026-09-22 用户定）：
 * 原先直接按下就拖，行首还画了个六点抓手。现在：
 * ① 抓手**移除**（它白占 20+12dp 把每行文字推窄，而长按手势不需要“抓手”来指认可拖区域）；
 * ② 手势改 `detectDragGesturesAfterLongPress`（长按前不消费事件 → 快速滑动仍归页面滚动）；
 * ③ **关闭项也能拖**（原来是 `if (!isOn) return@pointerInput` 拦住的）：
 *    抓手降权曾是“这行拖不动”的唯一信号，抓手一删就没了信号，而长按本身已是个明确的起手式，
 *    误拖风险小——于是选“全都能拖”而不是“留一堆不吭声的行”（顺序只影响它将来的位置）。
 * 长按触发时给一次 [HapticFeedbackType.LongPress]（与日历长按多选、热力图长按同款手感）。
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
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = Spacing.xs)) {
            Text(
                stringResource(R.string.appearance_slots_home_fixed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
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
                            // 整行可拖、关闭项也可拖（长按后拖，见本版块 KDoc）
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
                        SlotConfigRowContent(
                            spec = spec,
                            isOn = isOn,
                            onToggle = { onToggle(id, !isOn) },
                        )
                    }
                }
            }
        }
    }
}

/** 单行内容：图标方块 + 文字 + switch */
@Composable
private fun SlotConfigRowContent(
    spec: SlotSpec,
    isOn: Boolean,
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
    // 图标方块底色随开关浅深（secondaryContainer ⇄ surfaceContainerHighest）
    // ——**与首页卡片配置行完全一致**（同一屏的两个页签，视觉必须同款；用户 2026-09-22 指出缺底）
    val iconBoxColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = colorSpec,
        label = "slotRowIconBox",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标方块：盒+图标成对（IconBoxSpec.tile = 36dp + 20dp）+ 圆角 Radius.small
        Box(
            modifier = Modifier
                .size(IconBoxSpec.tile.box)
                .background(iconBoxColor, RoundedCornerShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(spec.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconBoxSpec.tile.icon),
            )
        }
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


