package com.mdot.app.feature.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import com.mdot.app.core.designsystem.Spacing

/**
 * 循环无限滚轮（时分选择器）：
 * - itemCount 个取值循环排列（极大 itemCount + 中段对齐制造"无限"错觉）；
 * - 中心 3 格，中心格高亮放大，上下格渐隐；
 * - 停止后吸附中心格并回调 value；
 * - 受控：外部 value 变化（如初始化/另一轮联动）时，滚轮平滑滚动到最近的同值格。
 */
@Composable
fun LoopWheel(
    value: Int,
    itemCount: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Int = 44,
    visibleCount: Int = 3,
    label: (Int) -> String = { it.toString().padStart(2, '0') },
) {
    require(itemCount > 0)
    val safeValue = value.coerceIn(0, itemCount - 1)
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val loopCount = 1_000_000 / itemCount
    fun indexOf(value: Int, near: Int): Int {
        val mod = ((near % itemCount) + itemCount) % itemCount
        var delta = ((value - mod) % itemCount + itemCount) % itemCount
        if (delta > itemCount / 2) delta -= itemCount // 选更近的方向
        return near + delta
    }

    // 当前吸附（中心）格的绝对 index
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: 0
        }
    }

    // 首次：定位到中段表示 safeValue 的格（无动画）；后续 value 外部变化：平滑滚动到最近同值格
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(safeValue) {
        if (!initialized) {
            listState.scrollToItem((loopCount / 2) * itemCount + safeValue)
            initialized = true
        } else {
            val centerValue = ((centerIndex % itemCount) + itemCount) % itemCount
            if (centerValue != safeValue) {
                listState.animateScrollToItem(indexOf(safeValue, centerIndex))
            }
        }
    }

    // 停止滚动时把中心格换算成取值回调；#9：每次用户滚动定档触发一次轻触觉
    // （snapshotFlow 降沿一次性；首次 collect 的初始化定位不振动）
    // ⚠️ 本 LaunchedEffect 生命周期内只 launch 一次，必须经 rememberUpdatedState 读
    // 最新 value/onValueChange——直接捕获会在闭包里永久留下首次组合的值：
    // ① 滚回初始值时 v == 旧 safeValue，回调不触发，两轮/摘要失同步；
    // ② 联动另一轮时用过期 totalMinutes 计算，出现自行扫动（实测分钟轮被小时轮
    //    联动后从 30 全轮扫到 00）。
    // 另：初始化定位（scrollToItem）完成前不回调——首帧 layout 未就绪时 centerIndex
    // 兕底为 0，会把 value=0 误报给外部。
    val haptic = LocalHapticFeedback.current
    var firstIdle by remember { mutableStateOf(true) }
    val currentSafeValue by rememberUpdatedState(safeValue)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(listState) {
        snapshotFlowIsIdle(listState) {
            if (!initialized) return@snapshotFlowIsIdle
            val v = ((centerIndex % itemCount) + itemCount) % itemCount
            if (v != currentSafeValue) currentOnValueChange(v)
            if (firstIdle) firstIdle = false
            else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((itemHeight * visibleCount).dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 上下各留半区（1 格）内边距，使目标格能吸附到 viewport 正中
            contentPadding = PaddingValues(vertical = itemHeight.dp),
        ) {
            items(loopCount * itemCount) { index ->
                val v = ((index % itemCount) + itemCount) % itemCount
                val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val viewportCenter =
                    (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                val distance = itemInfo?.let {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter).toFloat() /
                        it.size.toFloat().coerceAtLeast(1f)
                } ?: 2f
                // 字号恒定 22sp，大小变化全由 scale 连续驱动（中心 22×1.18≈26sp，
                // 相邻格 22×0.82≈18sp）。此前 fontSize 在 18↔22 间随 selected 阈值
                // 瞬时跳变（宽 +22%），滚动中数字左右笔画一帧内消失/出现，
                // 视觉上像被切割；静止不跨阈值故只在滚动时出现。
                val scale by animateFloatAsState(
                    targetValue = (1.18f - 0.36f * distance).coerceAtLeast(0.7f),
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "wheelScale",
                )
                val fadeAlpha by animateFloatAsState(
                    targetValue = (1f - 0.45f * distance).coerceIn(0.25f, 1f),
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "wheelAlpha",
                )
                val selected = distance < 0.5f
                Box(
                    modifier = Modifier
                        .height(itemHeight.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(v),
                        fontSize = 22.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fadeAlpha },
                    )
                }
            }
        }
    }
}

/** 滚动停止（吸附完成）时回调一次 */
private suspend fun snapshotFlowIsIdle(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onIdle: () -> Unit,
) {
    snapshotFlow { listState.isScrollInProgress }
        .distinctUntilChanged()
        .collect { scrolling -> if (!scrolling) onIdle() }
}
