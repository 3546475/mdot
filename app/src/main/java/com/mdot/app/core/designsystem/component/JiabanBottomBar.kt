package com.mdot.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val pillColor = MaterialTheme.colorScheme.secondaryContainer
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
        val maxWpx = (screenW - 2 * marginPx).roundToInt().coerceAtLeast(0)
        val cells = slots.size + if (centerAction != null) 1 else 0
        val idealWpx = (cells * with(density) { cellDp.toPx() }).roundToInt()
        // 首帧 screenW 尚未测量：先按理想宽渲染，随后收进可用宽度
        val barWpx = if (screenW > 0) idealWpx.coerceAtMost(maxWpx) else idealWpx
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .width(with(density) { barWpx.toDp() })
                .height(BottomBarSpec.height)
                .clip(shape)
                .background(barColor)
                .onSizeChanged { barWidth = it.width },
        ) {
            // 滑动胶囊：M3 Expressive 活动指示，弹簧滑动到目标槽位
            if (showIndicator && barWidth > 0 && selectedIndex >= 0 && slots.isNotEmpty()) {
                val centerW = if (centerAction != null) barWidth.toFloat() / cells else 0f
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
