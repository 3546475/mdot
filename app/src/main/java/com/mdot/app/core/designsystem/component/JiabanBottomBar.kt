package com.mdot.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.getValue
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
) {
    if (!visible) return
    val shape = RoundedCornerShape(Radius.bar)
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val pillColor = MaterialTheme.colorScheme.secondaryContainer
    val selectedIndex = slots.indexOfFirst { it.route == selectedRoute }
    var barWidth by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var screenW by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { screenW = it.width }
            .padding(horizontal = BottomBarSpec.horizontalMargin)
            .navigationBarsPadding()
            .padding(bottom = BottomBarSpec.bottomMargin),
    ) {
        val cellDp = if (iconOnly) BottomBarSpec.slotWidthIconOnly else BottomBarSpec.slotWidth
        val marginPx = with(density) { BottomBarSpec.horizontalMargin.toPx() }
        val maxWpx = (screenW - 2 * marginPx).roundToInt().coerceAtLeast(0)
        val idealWpx = (slots.size * with(density) { cellDp.toPx() }).roundToInt()
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
                val cellPx = barWidth.toFloat() / slots.size
                val pillW = cellPx - with(density) { 12.dp.toPx() }
                val pillH = with(density) { (if (iconOnly) 44.dp else 54.dp).toPx() }
                val targetLeft = (selectedIndex + 0.5f) * cellPx - pillW / 2
                val left by animateFloatAsState(
                    targetValue = targetLeft,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
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
                slots.forEachIndexed { idx, slot ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(interaction, pressedScale = 0.9f)
                            .clickable(indication = null, interactionSource = interaction) {
                                onSlotClick(slot)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SlotBody(slot, selected = idx == selectedIndex, iconOnly = iconOnly)
                    }
                }
            }
        }
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
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
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
