package com.mdot.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.Radius
import kotlin.math.roundToInt

/** 底栏功能池槽位定义（03 文档 §4.2）；MD3E：图标为 Material Symbols Rounded 单色形状，选中态由 tint（onSecondaryContainer/primary）+ 弹性放大区分。label 为资源 id，显示点用 stringResource 解析 */
data class SlotSpec(
    val id: String,
    val labelRes: Int,
    val route: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val iconFilledRes: Int = iconRes,
)

object SlotRegistry {
    val ALL = mapOf(
        "home" to SlotSpec("home", R.string.ds_slot_home, "home", R.drawable.ic_ms_home),
        "calendar" to SlotSpec("calendar", R.string.ds_slot_calendar, "calendar", R.drawable.ic_ms_calendar_month),
        "stats" to SlotSpec("stats", R.string.ds_slot_stats, "stats", R.drawable.ic_ms_bar_chart),
        "payroll" to SlotSpec("payroll", R.string.ds_slot_payroll, "payroll", R.drawable.ic_ms_paid),
        "export" to SlotSpec("export", R.string.ds_slot_export, "export", R.drawable.ic_ms_file_download),
        "sync" to SlotSpec("sync", R.string.ds_slot_sync, "sync", R.drawable.ic_ms_cloud_sync),
        "settings" to SlotSpec("settings", R.string.ds_slot_settings, "settings", R.drawable.ic_ms_settings),
        "profile" to SlotSpec("profile", R.string.ds_slot_profile, "profile", R.drawable.ic_ms_person),
    )

    fun resolve(id: String): SlotSpec? = ALL[id]
}

/**
 * 悬浮胶囊底栏（M3 Expressive 导航形态）：功能槽位等宽排列；
 * 选中槽位由一枚「滑动胶囊」标示（secondaryContainer，弹簧滑动），图标切换为填充变体并弹性放大。
 * 仅图标模式只显示图标；点击无涟漪无阴影（保留按压缩放）。
 *
 * 两种布局（外观页可切换）：
 * - 居中动作（[centerAction]，默认）：胶囊内为「左槽位 + 中央按钮 + 右槽位」；
 * - 侧边圆钮（[sideAction]）：胶囊内只有槽位，右侧另放一个独立圆钮（如「记加班」，始终仅图标）。
 *   两者互斥，调用方二选一传入。
 *
 * 毛玻璃（[backdropBlur] 非空）：胶囊改半透明底色 + 模糊身后内容（API≥31；更低版本回退
 * 高不透明底色 + 内高光 + 衬托渐变，见 BottomBarSpec.frosted* 系列令牌）。预览等无源场景传 null。
 */
@Composable
fun JiabanBottomBar(
    slots: List<SlotSpec>,
    selectedRoute: String?,
    visible: Boolean,
    onSlotClick: (SlotSpec) -> Unit,
    modifier: Modifier = Modifier,
    /** 仅图标模式：隐藏槽位文字标签 */
    iconOnly: Boolean = false,
    /** 是否显示选中胶囊指示（预览场景可关闭） */
    showIndicator: Boolean = true,
    /** 底栏中央固定操作（如「记加班」主按钮）；null = 无（配置页预览）。槽位左右分组让出中央位 */
    centerAction: (@Composable () -> Unit)? = null,
    /** 右侧独立圆钮（v0.6.19「记加班按钮置右」布局）：与 [centerAction] 互斥；非空时胶囊只放槽位、
     *  整组（胶囊 + 间距 + 圆钮）居中 */
    sideAction: (@Composable () -> Unit)? = null,
    /** 背景模糊（毛玻璃）状态：非空时胶囊半透明 + 模糊身后内容；null = 实色底（配置页预览） */
    backdropBlur: BackdropBlurState? = null,
    /** 「固定长度」档：>0 时胶囊宽固定为该个数槽位宽（仍受可用宽上限约束），槽位在固定宽内等分；
     *  0 = 随槽位数自适应（默认）。见 BottomBarSpec.fixedCellCount */
    fixedCells: Int = 0,
) {
    // MD3E 出入场（双向对称）：一级→二级弹簧下沉退出、二级→一级弹簧浮入（defaultSpatialSpec）；
    // 进度归零且动画结束后跳过绘制。motionScheme 仅 composable 可调用——先取 spec 再传入（03 文档规则 7）
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val progressSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, progressSpec)
    }
    // 组合期不得逐帧读 progress.value（否则动画期间整个底栏每帧重组=转场高峰掉帧）；
    // derivedStateOf 收敛为布尔翻转：仅「完全隐藏」瞬间重组一次
    val fullyHidden by remember {
        androidx.compose.runtime.derivedStateOf { progress.value <= 0f }
    }
    if (!visible && fullyHidden) return
    val shape = RoundedCornerShape(Radius.bar)
    val cs = MaterialTheme.colorScheme
    // 毛玻璃：半透明渐变底色透出模糊内容；无模糊能力（API<31）回退高不透明保证可读性
    val frosted = backdropBlur != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val barColor = cs.surfaceContainer
    val tintTop = cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedAlphaTop)
    val tintBottom = cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedAlphaBottom)
    val tintFallback = cs.surfaceContainer.copy(alpha = BottomBarSpec.frostedFallbackAlpha)
    // 底色画刷：毛玻璃=垂直渐变（上缘更透、下缘更实）；无模糊能力（API<31）=高不透明回退；非毛玻璃=实色
    val tintBrush = when {
        frosted -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tintTop, tintBottom))
        backdropBlur == null -> androidx.compose.ui.graphics.SolidColor(barColor)
        else -> androidx.compose.ui.graphics.SolidColor(tintFallback)
    }
    // 内高光：上缘内侧白色渐变（模拟玻璃边缘反光）；衬托渐变：模糊层内淡色（空内容时给玻璃可糊之物）
    val highlightBrush = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = BottomBarSpec.frostedHighlightAlpha), androidx.compose.ui.graphics.Color.Transparent),
        )
    }
    val scrimBrush = remember(cs) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(
                androidx.compose.ui.graphics.Color.Transparent,
                cs.surfaceContainerHighest.copy(alpha = BottomBarSpec.frostedScrimAlpha),
            ),
        )
    }
    val pillColor = cs.secondaryContainer
    val selectedIndex = slots.indexOfFirst { it.route == selectedRoute }
    var barWidth by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var screenW by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
                translationY = (1f - progress.value) * 24.dp.toPx()
            }
            .fillMaxWidth()
            .onSizeChanged { screenW = it.width }
            .padding(horizontal = BottomBarSpec.horizontalMargin)
            .navigationBarsPadding()
            .padding(bottom = BottomBarSpec.bottomMargin),
    ) {
        val cellDp = if (iconOnly) BottomBarSpec.slotWidthIconOnly else BottomBarSpec.slotWidth
        val marginPx = with(density) { BottomBarSpec.horizontalMargin.toPx() }
        // 侧边圆钮占位（圆钮直径 + 间距）先从可用宽里扣掉，胶囊按剩余宽收窄
        val sideReservePx = if (sideAction != null) {
            with(density) { BottomBarSpec.sideActionGap.toPx() + BottomBarSpec.sideActionSize.toPx() }
        } else 0f
        val maxWpx = (screenW - 2 * marginPx - sideReservePx).roundToInt().coerceAtLeast(0)
        // 宽度档：固定长度（fixedCells>0）按固定槽位数取宽；自适应按实际槽位数（含中央按钮位）
        val actualCells = slots.size + if (centerAction != null) 1 else 0
        val widthCells = if (fixedCells > 0) fixedCells else actualCells
        val idealWpx = (widthCells * with(density) { cellDp.toPx() }).roundToInt()
        // 首帧 screenW 尚未测量：先按理想宽渲染，随后收进可用宽度
        val barWpx = if (screenW > 0) idealWpx.coerceAtMost(maxWpx) else idealWpx
        // 有侧边圆钮时：胶囊左移、圆钮右移，等价于「胶囊 + 间距 + 圆钮」整组居中（无需改成 Row 结构）
        val sideShiftPx = if (sideAction != null) {
            with(density) { BottomBarSpec.sideActionGap.toPx() + BottomBarSpec.sideActionSize.toPx() }
        } else 0f
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset((-sideShiftPx / 2f).roundToInt(), 0) }
                .width(with(density) { barWpx.toDp() })
                .height(BottomBarSpec.height)
                // 底栏浮层效果（v0.6.20）：投影 + 细描边；毛玻璃开启时**去投影**（玻璃不投影，
                // 空白背景时阴影光晕观感差）+ 描边降档弱化硬边
                .shadow(elevation = if (frosted) BottomBarSpec.frostedElevation else BottomBarSpec.barElevation, shape = shape)
                .clip(shape)
                .border(
                    BottomBarSpec.barBorderWidth,
                    if (frosted) cs.outlineVariant.copy(alpha = BottomBarSpec.frostedBorderAlpha) else cs.outlineVariant,
                    shape,
                )
                .onSizeChanged { barWidth = it.width },
        ) {
            // 毛玻璃采样层（胶囊最底层子层）：只画「身后内容」窗口并整层模糊；
            // 模糊层内叠衬托渐变（空内容时给玻璃可糊之物）；底色与图标在兄弟层 → 图标保持锐利
            if (frosted) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBlur(backdropBlur!!, BottomBarSpec.frostBlurRadius, backdrop = scrimBrush),
                )
            }
            // 底色：叠在模糊之上、图标之下（毛玻璃渐变 / 非毛玻璃实色 / API<31 高不透明回退）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(tintBrush),
            )
            // 内高光（仅毛玻璃）：上缘内侧白色渐变，模拟玻璃边缘反光
            if (frosted) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(highlightBrush),
                )
            }

            // 滑动胶囊：M3 Expressive 活动指示，弹簧滑动到目标槽位
            if (showIndicator && barWidth > 0 && selectedIndex >= 0 && slots.isNotEmpty()) {
                // 胶囊数学用实际槽位数（固定长度档下槽位在固定宽内等分，与 Row 的 weight 一致）
                val centerW = if (centerAction != null) barWidth.toFloat() / actualCells else 0f
                val cellPx = (barWidth.toFloat() - centerW) / slots.size
                val pillW = cellPx - with(density) { 12.dp.toPx() }
                val pillH = with(density) { (if (iconOnly) 44.dp else 54.dp).toPx() }
                val leftCount = if (centerAction != null) slots.size / 2 else 0
                val flowIdx = selectedIndex + if (selectedIndex >= leftCount && centerAction != null) 1 else 0
                val targetLeft = (flowIdx + 0.5f) * cellPx - pillW / 2
                // MD3E：胶囊滑动走 motionScheme 空间 spec（expressive 弹簧过冲），禁硬编码 spring（03 文档规则 7）
                val pillSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                val left by animateFloatAsState(
                    targetValue = targetLeft,
                    animationSpec = pillSpec,
                    label = "navPillSlide",
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(left.roundToInt(), 0) }
                        .size(width = with(density) { pillW.toDp() }, height = with(density) { pillH.toDp() })
                        .background(pillColor, RoundedCornerShape(Radius.pill)),
                )
            }

            Row(Modifier.fillMaxSize()) {
                val leftCount = if (centerAction != null) slots.size / 2 else 0
                slots.take(leftCount).forEachIndexed { idx, slot ->
                    SlotCell(slot, idx == selectedIndex, iconOnly) { onSlotClick(slot) }
                }
                if (centerAction != null) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) { centerAction() }
                }
                slots.drop(leftCount).forEachIndexed { idx, slot ->
                    SlotCell(slot, leftCount + idx == selectedIndex, iconOnly) { onSlotClick(slot) }
                }
            }
        }
        if (sideAction != null) {
            // 右侧独立圆钮（如记加班）：容器与胶囊同高、内容居中 → 圆钮与胶囊垂直居中对齐；
            // 水平紧贴胶囊右侧，与胶囊一起构成居中组
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .height(BottomBarSpec.height)
                    .offset {
                        IntOffset(
                            ((barWpx + with(density) { BottomBarSpec.sideActionGap.toPx() }) / 2f).roundToInt(),
                            0,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                sideAction()
            }
        }
    }
}

/** 底栏槽位单元：等分宽 + 按压缩放 + 点击 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SlotCell(
    slot: SlotSpec,
    selected: Boolean,
    iconOnly: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressScale(interaction, pressedScale = 0.9f)
            .clickable(indication = null, interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SlotBody(slot, selected = selected, iconOnly = iconOnly)
    }
}

/** 槽位内容：图标（选中切填充变体 + 弹性放大）+ 可选文字，整体在槽内居中 */
@Composable
private fun SlotBody(
    slot: SlotSpec,
    selected: Boolean,
    iconOnly: Boolean,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "slotTint",
    )
    // MD3E：图标放大走 motionScheme 空间 spec
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = scaleSpec,
        label = "slotIconScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (selected) slot.iconFilledRes else slot.iconRes),
            contentDescription = stringResource(slot.labelRes),
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        if (!iconOnly) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(slot.labelRes),
                style = if (selected) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
            )
        }
    }
}
