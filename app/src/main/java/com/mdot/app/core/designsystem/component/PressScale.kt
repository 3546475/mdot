package com.mdot.app.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压缩放反馈（M3 Expressive 弹簧手感）：与 clickable/Surface 共用同一个 interactionSource，
 * 按下干脆缩小、松手带弹性回弹。
 * 用法：
 *   val interaction = remember { MutableInteractionSource() }
 *   Modifier.pressScale(interaction)
 *       .clickable(interactionSource = interaction, indication = LocalIndication.current) { }
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "pressScale",
    )
    // 必须链在接收者之后（then），否则会丢掉链中已有的布局修饰符（如 RowScope.weight）
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
