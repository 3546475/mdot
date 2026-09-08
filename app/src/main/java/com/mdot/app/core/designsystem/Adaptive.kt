package com.mdot.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口宽度档位（docs 03 §3.2 响应式布局）：
 * - COMPACT  <600dp：手机竖屏，全宽布局（现状单列）
 * - MEDIUM   600–839dp：平板竖屏/折叠屏展开，内容限宽 [AdaptiveSpecs.contentMaxWidth] 居中
 * - EXPANDED ≥840dp：平板横屏/桌面窗口，双栏布局 + 限宽 [AdaptiveSpecs.twoPaneMaxWidth]
 */
enum class WindowSpec { COMPACT, MEDIUM, EXPANDED }

/** 全局窗口档位：AppRoot 顶部提供，页面/组件读取判定布局形态 */
val LocalWindowSpec = staticCompositionLocalOf { WindowSpec.COMPACT }

/** 由宽度（dp）判定档位；纯函数（供单测） */
fun windowSpecForWidth(width: Dp): WindowSpec = when {
    width >= AdaptiveSpecs.expandedBreakpoint -> WindowSpec.EXPANDED
    width >= AdaptiveSpecs.mediumBreakpoint -> WindowSpec.MEDIUM
    else -> WindowSpec.COMPACT
}

/** 读取当前窗口宽度档位（LocalWindowInfo.containerSize：分屏/自由窗口/桌面模式下取真实窗口宽） */
@Composable
fun rememberWindowSpec(): WindowSpec {
    val density = LocalDensity.current
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = remember(density, containerWidthPx) {
        with(density) { containerWidthPx.toDp() }
    }
    return windowSpecForWidth(widthDp)
}

/**
 * 宽屏限宽居中容器（docs 03 §3.2「内容最大宽度居中」）：
 * - COMPACT：内容原样挂载（全宽）
 * - MEDIUM/EXPANDED：外层 Box 撑满窗口、内容限宽 [maxWidth] 水平居中
 *
 * 页面根布局（Column/LazyColumn fillMaxSize）放在 content 里原样迁移即可；
 * 需要双栏的页面传入 [AdaptiveSpecs.twoPaneMaxWidth] 并自行按 LocalWindowSpec 分支。
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveSpecs.contentMaxWidth,
    content: @Composable () -> Unit,
) {
    val spec = LocalWindowSpec.current
    if (spec == WindowSpec.COMPACT) {
        Box(modifier) { content() }
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().widthIn(max = maxWidth)) { content() }
        }
    }
}

/**
 * 宽屏时内容两侧留出的空边 =（窗口宽 − 限宽）/2；COMPACT 返回 0。
 * 用于 FAB 等绝对定位元素跟随内容右缘对齐（AppRoot 日历 FAB）。
 */
@Composable
fun rememberContentSideInset(maxWidth: Dp = AdaptiveSpecs.contentMaxWidth): Dp {
    val density = LocalDensity.current
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    return remember(density, containerWidthPx, maxWidth) {
        val widthDp = with(density) { containerWidthPx.toDp() }
        ((widthDp - maxWidth) / 2).coerceAtLeast(0.dp)
    }
}
