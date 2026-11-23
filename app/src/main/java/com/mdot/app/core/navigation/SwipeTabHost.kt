package com.mdot.app.core.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.compose.NavHost

/** 最近一次底栏 Tab 切换方向：+1=新页从右入（前进），-1=新页从左入（后退）。点击 Tab 默认 +1 */
internal val lastTabSwipeDir = androidx.compose.runtime.mutableStateOf(1)

/**
 * 一级 Tab「边缘接力」：内层横向滚动（如统计页的多页签 pager）滑到尽头后，页面调用它把
 * 「继续滑」的意图交给整页横滑（切相邻一级 Tab）——两套横滑不再抢手势。
 * [dir]：-1 = 上一个槽位（向右滑），+1 = 下一个槽位（向左滑）；非一级页形态下为空实现。
 */
val LocalPrimaryTabSwipe = compositionLocalOf<(Int) -> Unit> { {} }

/**
 * 一级 Tab 页滑动切换：跟手平移 + 松手后按方向滑出并切换到相邻底栏槽位页。
 * 页面内部自身的横向手势（如统计维度的横向 chip 行）优先级更高、不受影响。
 *
 * selfRoute：本宿主承载的路由；当快速滑动打断转场、页面在滑出动画途中被重新导航回来时，
 * 旧实例的 offset 可能停在 ±屏宽（内容平移出屏 → 空白页），因此「重新成为当前页」时
 * 必须停掉残留动画并把 offset 归零。
 */
@Composable
internal fun SwipeTabHost(
    orderedSlots: List<String>,
    currentBase: String?,
    selfRoute: String,
    onNavigate: (String) -> Boolean,
    content: @Composable () -> Unit,
) {
    val idx = orderedSlots.indexOf(currentBase)
    val isActive = currentBase == selfRoute.substringBefore('?')
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(0) }
    // B6-02：跟手飞出时长接入 motionScheme（原硬编码 tween(220)）
    val flyOutSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(isActive) {
        if (isActive) {
            offset.stop()
            offset.snapTo(0f)
        }
    }

    if (idx >= 0) {
        val prev = orderedSlots.getOrNull(idx - 1)
        val next = orderedSlots.getOrNull(idx + 1)
        // 返回 false = 导航被防抖忽略（转场进行中），此时页面只回弹不飞出
        val goTo = { route: String, dir: Int ->
            lastTabSwipeDir.value = dir
            onNavigate(route)
        }

        // 边缘接力：本页内的横向滚动滚到尽头后，把续滑意图交回整页横滑
        val primarySwipe: (Int) -> Unit = { dir ->
            if (dir < 0) {
                if (prev != null) goTo(prev, -1)
            } else {
                if (next != null) goTo(next, 1)
            }
        }
        CompositionLocalProvider(LocalPrimaryTabSwipe provides primarySwipe) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { pageWidth = it.width }
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo(offset.value + delta) }
                    },
                    onDragStopped = {
                        val w = pageWidth.coerceAtLeast(1)
                        val vel = it
                        // 低阈值：位移超过 12% 屏宽或甩动 > 700px/s 即切换
                        val pass = when {
                            offset.value > w * 0.12f -> vel >= -400f
                            offset.value < -w * 0.12f -> vel <= 400f
                            offset.value > 0f && vel > 700f -> true
                            offset.value < 0f && vel < -700f -> true
                            else -> false
                        }
                        val right = offset.value > 0f || (vel > 0f && offset.value > -w * 0.05f)
                        val target = when {
                            !pass -> 0f // 回弹
                            right && prev != null -> if (goTo(prev, -1)) w.toFloat() else 0f
                            !right && next != null -> if (goTo(next, 1)) -w.toFloat() else 0f
                            else -> 0f
                        }
                        scope.launch {
                            if (target == 0f) {
                                offset.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ))
                            } else {
                                // 跟手飞出交给 NavHost 转场接力；防抖保证转场不会被
                                // 新导航打断（打断会把页面定格在退出位 → 空白/偏移）
                                offset.animateTo(target, flyOutSpec)
                            }
                        }
                    },
                ),
        ) { content() }
        }
    } else {
        // 非当前页（正在滑出/转场中）：保留残留位移，避免内容在淡出途中跳回原位
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset.value }) { content() }
    }
}
