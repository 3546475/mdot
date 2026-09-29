package com.mdot.app.core.designsystem.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign

/**
 * ══ miuix 引擎——橡皮筋回弹 overscroll（v0.7.8）═══
 *
 * 移植自开源项目 **miuix**（https://github.com/compose-miuix-ui/miuix，Apache-2.0）
 * `utils/OverscrollFactory.kt`（`MiuixOverscrollEffect`）：滚到边界后内容**跟着手指带阻尼
 * 位移**（阻尼曲线 `x - x² + x³/3`，与参考同款），松手经弹簧（folme 1.0/0.4s）归位——
 * MIUI/iOS 的橡皮筋手感，替代 Android 默认的边缘辉光。经 `JiabanTheme` 在 MIUIX 引擎下
 * `LocalOverscrollFactory provides` 全局生效。
 *
 * ⚠️ 与参考实现的两处差异（都有意为之）：
 * 1. **只做纵向**：横向余量一律不吞、透传给父级——一级页横滑切页签依赖「内层优先 +
 *    边缘接力」（`LocalPrimaryTabSwipe`，见 docs/11 041），overscroll 若把横向余量吃掉，
 *    「滑到头接力换页签」会失灵；纵向没有接力消费方，安全。
 * 2. **不接 pull-to-refresh 协同**（本 App 无下拉刷新）；弹簧结算用 Compose `Animatable`
 *    替代参考的自研 `SpringEngine`（同为 folme 参数，少移植 ~200 行数值积分）。
 *
 * 另随引擎带**边界触感**（参考 miuix `utils/ScrollEndHaptic` 的「到边界轻震一下」）：
 * 橡皮筋首次越阈值时发一次 `TextHandleMove` 触感——拖拽越界与 fling 撞边（弹簧带初速
 * 越阈值）都经 [MiuixOverscrollEffect.offsetY] 的 setter 判越界，单点触发不会连震。
 */
object MiuixOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = MiuixOverscrollEffect()

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = 1
}

/** 归位弹簧（参考 SpringEngine：临界阻尼、周期 0.4s） */
private val SettleSpring = folmeSpring<Float>(damping = 1.0f, response = 0.4f)

class MiuixOverscrollEffect : OverscrollEffect {
    private val offsetThreshold = 1f

    // 摆放按像素取整，取整值变化才重排（与参考同款）
    private var lastPlacedOffsetY = 0f
    internal var offsetY = 0f
        private set(value) {
            if (field != value) {
                val wasInProgress = abs(field) > offsetThreshold
                field = value
                val rounded = round(value)
                if (rounded != lastPlacedOffsetY) {
                    lastPlacedOffsetY = rounded
                    invalidateNodePlacement?.invoke()
                }
                // 边界触感：橡皮筋首次越阈值震一下（拖拽/ fling 撞边均经此路径，单次不连震）
                if (!wasInProgress && abs(field) > offsetThreshold) {
                    performEdgeHaptic?.invoke()
                }
            }
        }

    private var rawTouchAccumulationY = 0f

    /** 阻尼基准：滚动视口高（由 effect 节点注入），= 位移全程的量程 */
    internal var scrollRangeV = 0f

    // 由 MiuixOverscrollEffectNode 挂载时注入
    internal var invalidateNodePlacement: (() -> Unit)? = null
    internal var launchAnimation: ((suspend CoroutineScope.() -> Unit) -> Job)? = null
    internal var performEdgeHaptic: (() -> Unit)? = null

    private var animationJobY: Job? = null

    override val isInProgress: Boolean
        get() = abs(offsetY) > offsetThreshold

    override val node: DelegatableNode = MiuixOverscrollEffectNode(this)

    internal fun resetAll() {
        offsetY = 0f
        rawTouchAccumulationY = 0f
    }

    private fun resetStateY() {
        offsetY = 0f
        rawTouchAccumulationY = 0f
    }

    private fun applyDragY(delta: Float) {
        if (delta == 0f || scrollRangeV == 0f) return
        rawTouchAccumulationY += delta
        rawTouchAccumulationY = rawTouchAccumulationY.coerceIn(-scrollRangeV, scrollRangeV)
        val normalized = min(abs(rawTouchAccumulationY) / scrollRangeV, 1.0f)
        offsetY = sign(rawTouchAccumulationY) * dampingDistance(normalized, scrollRangeV)
    }

    /** 拖拽被弹簧中断后接管：由当前位移反推原始累积量（阻尼曲线求逆） */
    private fun syncRawAccumulationFromOffsetY() {
        rawTouchAccumulationY = sign(offsetY) * touchDistance(offsetY, scrollRangeV)
    }

    /** 子级能滚后释放陈旧 overscroll（保住随后的 fling） */
    private fun unwindStaleOffsetY(consumedDelta: Float) {
        if (abs(offsetY) <= offsetThreshold || consumedDelta == 0f) return
        if (rawTouchAccumulationY == 0f) syncRawAccumulationFromOffsetY()
        if (sign(consumedDelta) != sign(rawTouchAccumulationY)) return
        if (abs(rawTouchAccumulationY) <= abs(consumedDelta)) {
            resetStateY()
        } else {
            applyDragY(-consumedDelta)
        }
    }

    private fun startSpringAnimationY(initialVelocity: Float = 0f) {
        if (abs(offsetY) <= offsetThreshold && initialVelocity == 0f) {
            resetStateY()
            return
        }
        animationJobY?.cancel()
        animationJobY = launchAnimation?.invoke {
            val animatable = Animatable(offsetY)
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = SettleSpring,
                initialVelocity = initialVelocity,
            ) {
                offsetY = value
            }
            if (abs(offsetY) <= offsetThreshold) resetStateY()
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            val consumed = performScroll(delta)
            // 归位中的弹簧自己会收尾；只在被中断时回收陈旧位移
            if (animationJobY?.isActive != true) unwindStaleOffsetY(consumed.y)
            return consumed
        }

        if (delta.y != 0f) {
            if (animationJobY?.isActive == true) syncRawAccumulationFromOffsetY()
            animationJobY?.cancel()
        }

        // 先把「滚回内容」的反向位移从 overscroll 里抵扣，再滚内容
        var performScrollDeltaY = delta.y
        var extraConsumedY = 0f
        if (abs(offsetY) > offsetThreshold && delta.y != 0f && sign(delta.y) != sign(rawTouchAccumulationY)) {
            val canConsumeY = if (abs(rawTouchAccumulationY) <= abs(delta.y)) -rawTouchAccumulationY else delta.y
            if (abs(rawTouchAccumulationY) <= abs(delta.y)) {
                resetStateY()
                performScrollDeltaY = delta.y - canConsumeY
                extraConsumedY = canConsumeY
            } else {
                applyDragY(canConsumeY)
                performScrollDeltaY = 0f
                extraConsumedY = delta.y
            }
        }

        val adjustedDelta = Offset(delta.x, performScrollDeltaY)
        val scrollConsumed = performScroll(adjustedDelta)
        val scrollRemaining = adjustedDelta - scrollConsumed

        if (animationJobY?.isActive != true) unwindStaleOffsetY(scrollConsumed.y)
        if (scrollRemaining.y != 0f) applyDragY(scrollRemaining.y)

        // ⚠️ 横向余量不吞（透传父级「边缘接力」），纵向余量进橡皮筋
        return Offset(
            x = scrollConsumed.x,
            y = extraConsumedY + scrollConsumed.y + (if (scrollRemaining.y != 0f) scrollRemaining.y else 0f),
        )
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val isActiveY = abs(offsetY) > offsetThreshold
        animationJobY?.cancel()

        var performVelocity = velocity

        // 向外的速度被 overscroll 吸收；向内的速度衰减后交给内容滚动
        if (isActiveY && velocity.y != 0f) {
            startSpringAnimationY(velocity.y)
            performVelocity = if (sign(velocity.y) == sign(offsetY)) {
                Velocity(performVelocity.x, 0f)
            } else {
                Velocity(performVelocity.x, velocity.y / 2.13333f)
            }
        }

        val consumed = performFling(performVelocity)
        val remaining = performVelocity - consumed

        // 与参考一致：fling 后剩余速度按固定衰减进归位弹簧
        startSpringAnimationY(remaining.y / 1.53333f)
    }
}

/** 阻尼位移（miuix SpringMath.obtainDampingDistance 同款）：`x - x² + x³/3` 量程化 */
internal fun dampingDistance(normalizedInput: Float, range: Float): Float {
    val x = normalizedInput.coerceIn(0f, 1f).toDouble()
    val dampedFactor = x - x * x + (x * x * x / 3.0)
    return (dampedFactor * range).toFloat()
}

/** 阻尼曲线求逆（miuix SpringMath.obtainTouchDistance 同款）：`range - range^(2/3) * (range - 3|offset|)^(1/3)` */
internal fun touchDistance(currentPixelOffset: Float, range: Float): Float {
    if (currentPixelOffset == 0f || range <= 0f) return 0f
    var absPixelOffset = abs(currentPixelOffset)
    val absMaxOffset = abs(dampingDistance(1.0f, range))
    if (absPixelOffset >= absMaxOffset) absPixelOffset = absMaxOffset
    val base = range - (3.0 * absPixelOffset)
    val part2 = Math.pow(range.toDouble(), 2.0 / 3.0) * sign(base) * Math.pow(abs(base), 1.0 / 3.0)
    return (range - part2).toFloat()
}

/**
 * effect 的节点：注入协程作用源/摆放失效回调，并把 [MiuixOverscrollEffect.offsetY]
 * 以 `translationY` 落到内容摆放上（裁剪在层内，越界露出页面底色）。
 */
private class MiuixOverscrollEffectNode(
    val effect: MiuixOverscrollEffect,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutModifierNode {

    override fun onAttach() {
        super.onAttach()
        effect.launchAnimation = { block -> coroutineScope.launch(block = block) }
        effect.invalidateNodePlacement = { invalidatePlacement() }
        effect.performEdgeHaptic = {
            currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    override fun onDetach() {
        super.onDetach()
        effect.launchAnimation = null
        effect.invalidateNodePlacement = null
        effect.performEdgeHaptic = null
        effect.resetAll()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        // 阻尼量程 = 滚动视口高（effect 包住的是 scrollable 视口，不是内容）
        effect.scrollRangeV = placeable.height.toFloat().coerceAtLeast(1f)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationY = round(effect.offsetY)
                clip = true
            }
        }
    }
}
