package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.mdot.app.core.designsystem.Spacing

/**
 * 单行横向滚动容器：内容超出时横滑，永不换行、不显滚动条（行高恒定 → 卡片尺寸不随内容增减变化）。
 *
 * **本容器是横向手势的"终点"**：在行内滑动时，无论是否已滑到名单两端，
 * 都不会把横向手势交给外层容器（外层若是 `HorizontalPager`，即不会触发页签切换）。
 * 纵向手势完全不拦，页面垂直滚动不受影响。
 *
 * 实现说明（踩过的两个坑，见 docs/11 025/026）：
 * - 只在 **onPostScroll** 里消费余量：preScroll 阶段消费会连内层滚动一起吃掉
 *   （该阶段父级先于子级滚动容器拿到位移），导致列表根本滚不动；
 * - 只在 **onPostFling** 里消费余量速度：早期版本把 `onPreFling` 收到的
 *   **Velocity（像素/秒）** 误当 **位移** 传给 `dispatchRawDelta`，导致松手跳变 + 颤抖。
 *   此处 `available` 与返回值都是 Velocity，单位一致。
 */
@Composable
fun HorizontalScrollRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.s),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()

    val nestedScrollConnection = object : NestedScrollConnection {
        /**
         * 内层滚动容器消费后剩下的横向位移，在此全部吃掉 → 不再上抛给外层（Pager 收不到位移就不会换页）。
         * 纵向分量原样返回 0（不消费），保证页面垂直滚动照常。
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = if (available.x != 0f) Offset(available.x, 0f) else Offset.Zero

        /** 惯性阶段同理：吃掉剩余横向速度，避免外层接着惯性换页 */
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            Velocity(available.x, 0f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(nestedScrollConnection)
            .horizontalScroll(scrollState),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}
