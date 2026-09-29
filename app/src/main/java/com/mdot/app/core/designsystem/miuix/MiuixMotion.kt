package com.mdot.app.core.designsystem.miuix

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme
import kotlin.math.PI

/**
 * ══ miuix 引擎——弹簧动效（v0.7.8）═══
 *
 * 移植自开源项目 **miuix**（https://github.com/compose-miuix-ui/miuix，Apache-2.0）
 * `anim/MiuixEasing.kt` 的 `folmeSpring`（MIUI「folme」弹簧：给 **阻尼比 + 响应时间**，
 * 刚度由响应时间反推）与按压/overscroll 的弹簧参数。
 *
 * [MiuixMotionScheme] 是**自定义 MotionScheme**（M3 `MotionScheme` 为接口，官方支持
 * 自定义方案）：MIUIX 引擎下全 App 组件动画（`MaterialTheme.motionScheme` 三档）换成
 * 这套弹簧——空间类（位移/尺寸）留 <1 阻尼轻微回弹，效果类（颜色/透明）临界阻尼不回弹。
 * 硬规则 7 不受影响：组件动画仍统一走 motionScheme，只是方案随引擎切换。
 */

/**
 * miuix 式弹簧（folme 参数化）：
 * @param damping 阻尼比：1.0 = 临界（不过冲），<1 = 欠阻尼（回弹），>1 = 过阻尼
 * @param response 响应时间（秒），越小越快；刚度 = (2π / response)²
 */
internal fun <T> folmeSpring(damping: Float, response: Float): SpringSpec<T> {
    val stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
    return spring(dampingRatio = damping, stiffness = stiffness)
}

/**
 * MIUIX 引擎的 MotionScheme（三档 × 空间/效果）。
 *
 * 档位取自 miuix 组件实际用的 folme 参数：按压进出 damping 1.0 / response 0.2–0.35，
 * 归并成「快 0.2 / 默认 0.3 / 慢 0.5」三档；空间档 damping 0.9 保留 MIUI 标志性的
 * 轻微回弹（欠阻尼），效果档 damping 1.0 平滑收尾。
 */
object MiuixMotionScheme : MotionScheme {
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.2f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 1f, response = 0.18f)
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.3f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 1f, response = 0.28f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 0.9f, response = 0.5f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = folmeSpring(damping = 1f, response = 0.45f)
}
