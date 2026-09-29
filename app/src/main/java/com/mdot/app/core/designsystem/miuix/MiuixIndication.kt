package com.mdot.app.core.designsystem.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ══ miuix 引擎——按压高亮 Indication（v0.7.8）═══
 *
 * 移植自开源项目 **miuix**（https://github.com/compose-miuix-ui/miuix，Apache-2.0）
 * `utils/MiuixIndication.kt`：按压/悬停/聚焦时在元素上叠一层**矩形高亮**（不是 Android
 * 的水波纹）——MIUI/iOS 式「按下变暗」，配 `pressScale` 缩放就是 MIUI 的按压手感。
 *
 * 与参考实现的差异：去掉 `HoldDownInteraction`（miuix 组件库私有交互）与手势库耦合；
 * 弹簧走本引擎 [folmeSpring]（参数与参考一致：按压进 1.0/0.2s、出 0.95/0.35s）。
 *
 * 注入点：`JiabanTheme` 在 MIUIX 引擎下 `LocalIndication provides` 本实现——
 * 全 App 用 `LocalIndication.current` 的 clickable 自动切换（约 200 处），无需逐点改。
 */

private const val HOVER_ALPHA_DELTA = 0.06f
private const val FOCUS_ALPHA_DELTA = 0.08f
private const val PRESS_ALPHA_DELTA = 0.10f

private val PressEnterSpring: SpringSpec<Float> = folmeSpring(damping = 1.0f, response = 0.2f)
private val PressExitSpring: SpringSpec<Float> = folmeSpring(damping = 0.95f, response = 0.35f)
private val HoverEnterSpring: SpringSpec<Float> = folmeSpring(damping = 1.0f, response = 0.6f)
private val HoverExitSpring: SpringSpec<Float> = folmeSpring(damping = 0.96f, response = 0.2f)

/**
 * miuix 默认 [Indication]：按压时矩形高亮叠加（颜色取主题 `onBackground`——
 * 浅色主题 = 变暗、深色主题 = 变亮，与参考实现同款）。
 */
@Immutable
class MiuixIndication(
    private val color: Color = Color.Black,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        MiuixIndicationInstance(interactionSource, color)

    override fun hashCode(): Int = color.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MiuixIndication) return false
        return color == other.color
    }

    private class MiuixIndicationInstance(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var isPressed = false
        private var isHovered = false
        private var isFocused = false
        private val animatedAlpha = Animatable(0f)
        private var pressedAnimation: Job? = null
        private var restingAnimation: Job? = null

        private fun targetAlpha(): Float {
            var targetAlpha = 0f
            if (isHovered) targetAlpha += HOVER_ALPHA_DELTA
            if (isFocused) targetAlpha += FOCUS_ALPHA_DELTA
            if (isPressed) targetAlpha += PRESS_ALPHA_DELTA
            return targetAlpha
        }

        private fun animateOverlay(spring: SpringSpec<Float>, fromPressRelease: Boolean) {
            val target = targetAlpha()
            if (fromPressRelease || target == 0f) {
                restingAnimation?.cancel()
                restingAnimation = coroutineScope.launch {
                    pressedAnimation?.join()
                    animatedAlpha.animateTo(targetValue = target, animationSpec = spring)
                }
            } else {
                pressedAnimation?.cancel()
                restingAnimation?.cancel()
                pressedAnimation = coroutineScope.launch {
                    animatedAlpha.animateTo(targetValue = target, animationSpec = spring)
                }
            }
        }

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    val previousPressed = isPressed
                    val previousHovered = isHovered
                    val previousFocused = isFocused

                    when (interaction) {
                        is PressInteraction.Press -> isPressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
                        is HoverInteraction.Enter -> isHovered = true
                        is HoverInteraction.Exit -> isHovered = false
                        is FocusInteraction.Focus -> isFocused = true
                        is FocusInteraction.Unfocus -> isFocused = false
                        else -> return@collect
                    }

                    val spring = when {
                        previousPressed != isPressed -> if (isPressed) PressEnterSpring else PressExitSpring
                        previousHovered != isHovered -> if (isHovered) HoverEnterSpring else HoverExitSpring
                        previousFocused != isFocused -> if (isFocused) HoverEnterSpring else HoverExitSpring
                        else -> return@collect
                    }
                    animateOverlay(spring, fromPressRelease = previousPressed && !isPressed)
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = animatedAlpha.value
            if (alpha > 0f) {
                drawRect(color = color, alpha = alpha, size = size)
            }
        }
    }
}
