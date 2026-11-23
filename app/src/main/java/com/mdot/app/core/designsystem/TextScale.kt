package com.mdot.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.takeOrElse

/**
 * 固定高度的文字容器用它算高度：文字行高（sp，`toDp()` 已含系统字体缩放）+ 上下各 [verticalPadding]，
 * 且不低于 [min]。
 *
 * 字号走 sp、容器高度走 dp 时，系统字体放大后文字会被裁掉——凡「固定 dp 高 + 单行文字」的容器，
 * 高度都应经本函数得出：缩放 1.0 时得到设计基准值，放大后跟着长。
 *
 * 不用 `rememberTextMeasurer` 预测量：首帧字体解析/缓存未就绪时结果偏小（见 `ChartTapHint`）。
 */
@Composable
fun textFittedHeight(style: TextStyle, verticalPadding: Dp, min: Dp = 0.dp): Dp {
    val lineHeight = style.lineHeight.takeOrElse { style.fontSize * 1.35f }
    if (!lineHeight.isSpecified) return min
    return fittedHeight(with(LocalDensity.current) { lineHeight.toDp() }, verticalPadding, min)
}

/** [textFittedHeight] 的纯算式（行高已换算成 dp），单独拆出以便单测。 */
fun fittedHeight(lineHeight: Dp, verticalPadding: Dp, min: Dp = 0.dp): Dp =
    maxOf(min, lineHeight + verticalPadding * 2)
