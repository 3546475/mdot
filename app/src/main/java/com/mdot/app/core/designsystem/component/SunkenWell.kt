package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.Radius

/**
 * 凹陷托盘（sunken well）：让内容区域看起来**嵌进所在表面**的底座。
 *
 * 三个视觉线索：
 * 1. **底色暗一档**——用 `onSurface` 低透明度直接叠在宿主表面上，明暗主题都稳定地
 *    比所在表面深一层（不挑宿主用的 surface 档位；MD3 容器色在明暗主题方向相反，不可用）；
 * 2. **顶部内阴影**——凹面上缘背光，黑色渐变自上而下淡出（刻意比高光浅且窄：只作提示）；
 * 3. **底部内高光**——下缘内壁受光，白色渐变自下而上淡出（反向阴影，凹陷的核心线索）。
 *
 * 用法（记加班弹窗的时长选择区）：
 * ```
 * SunkenWell(Modifier.fillMaxWidth().height(164.dp)) {   // 内容区 = 164 - Spacing.s×2 = 148（3 行 44 格 + 2×Spacing.s 行距）
 * // ⚠️ 改 innerPadding 或 DurationGrid 行距时必须同步这个高度，否则第 3 行会被裁掉
 *     if (分钟) MinutesWheels(...) else DurationGrid(...)
 * }
 * ```
 * 内容绘制在底色/光影**之上**，不受压暗影响；格子原色浮在凹面里即「浮起」感。
 */
@Composable
fun SunkenWell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.textField),
    innerPadding: PaddingValues = PaddingValues(Spacing.s),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape)) {
        // 凹陷光影：暗底 + 顶部浅阴影 + 底部内高光（与 [sunkenWell] 同一套规格）
        Box(Modifier.matchParentSize().sunkenWell(shape))
        Box(Modifier.padding(innerPadding)) { content() }
    }
}

/**
 * 凹陷光影修饰符：在自身边界内画出 [SunkenWell] 同款的三层光影
 * （暗底 + 顶部浅阴影 + 底部内高光），供不便换成容器 Box 的场景内联使用
 * （如 [SegmentBar] 的选中滑块「沉下去」）。
 *
 * 暗底用 `onSurface` 低透明度叠在**调用者已有的底色**上——比所在表面深一层的
 * 保证由调用点的底色提供（本修饰符不再另铺底色）。
 */
@Composable
fun Modifier.sunkenWell(
    shape: Shape,
    depth: Dp = SunkenWellSpec.depth,
    shadowDepth: Dp = SunkenWellSpec.shadowDepth,
): Modifier {
    val dim = MaterialTheme.colorScheme.onSurface
    return drawBehind {
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        drawPath(path, dim.copy(alpha = SunkenWellSpec.dimAlpha))
        clipPath(path) {
            val sd = shadowDepth.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = SunkenWellSpec.shadowAlpha), Color.Transparent),
                    startY = 0f,
                    endY = sd,
                ),
                size = Size(size.width, sd),
            )
            val d = depth.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = SunkenWellSpec.highlightAlpha)),
                    startY = size.height - d,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - d),
                size = Size(size.width, d),
            )
        }
    }
}

/** 凹陷三要素的规格。透明度刻意压低——「沉下去」是环境感，不是装饰边框 */
internal object SunkenWellSpec {
    val dimAlpha = 0.05f
    val shadowAlpha = 0.06f
    val highlightAlpha = 0.09f
    val depth: Dp = 8.dp
    val shadowDepth: Dp = 5.dp
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}
