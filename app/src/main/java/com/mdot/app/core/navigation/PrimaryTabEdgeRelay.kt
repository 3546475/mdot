package com.mdot.app.core.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 一级 Tab「边缘接力」（统计页与同步备份页共用）：内层 pager 已在第一/最后一页时，继续拖动的横向余量
 * 累计超过阈值就把「继续滑」交给整页横滑（[LocalPrimaryTabSwipe]）；同一次手势只触发一次（甩动结束复位）。
 * 用 nestedScroll 的 **post 阶段**取余量：内层能滚就内层滚，只有到尽头才轮到外层（docs 11 026/027）。
 *
 * ⚠️ 放在 core/navigation：它依赖 [LocalPrimaryTabSwipe]（同包），页面层（stats / sync）各自 import 使用。
 */
@Composable
fun Modifier.primaryTabEdgeRelay(pagerState: PagerState): Modifier {
    val swipe = rememberUpdatedState(LocalPrimaryTabSwipe.current)
    val thresholdPx = with(LocalDensity.current) { 28.dp.toPx() }
    val acc = remember { mutableStateOf(0f) }
    val fired = remember { mutableStateOf(false) }
    return this.nestedScroll(
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.x == 0f) return Offset.Zero
                    // 只看「当前页 + 拖动方向」：向右拖（available.x > 0）且已在第一页 → 上一个一级 Tab；
                    // 向左拖且已在末页 → 下一个。不用 currentPageOffsetFraction 的符号（约定易搞反，反而静默失效）
                    val atFirst = pagerState.currentPage == 0
                    val atLast = pagerState.currentPage == pagerState.pageCount - 1
                    val dir = when {
                        available.x > 0f && atFirst -> -1
                        available.x < 0f && atLast -> 1
                        else -> return Offset.Zero
                    }
                    // 方向反了就重新累计
                    if (acc.value != 0f && (acc.value > 0f) != (available.x > 0f)) acc.value = 0f
                    acc.value += available.x
                    if (!fired.value && abs(acc.value) >= thresholdPx) {
                        fired.value = true
                        swipe.value(dir)
                    }
                    return Offset(available.x, 0f) // 消费余量：不再上抛
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    acc.value = 0f // 一次手势只接力一次
                    fired.value = false
                    return Velocity.Zero
                }
            }
        }
    )
}
