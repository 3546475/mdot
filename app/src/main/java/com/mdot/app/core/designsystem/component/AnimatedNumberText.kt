package com.mdot.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.mdot.app.domain.util.Money

/**
 * 数字滚动文本：数值变化时新值沿垂直方向滑入 + 淡入、旧值反向滑出 + 淡出
 * （与工地记工 PayHeroCard 工钱 hero 同款动画，docs/15 统一数字反馈）。
 * 增加时新值自下而上、减少时自上而下；缓动走 motionScheme default 规格。
 */
@Composable
fun <T : Comparable<T>> AnimatedNumberText(
    value: T,
    text: @Composable (T) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    label: String = "animatedNumberText",
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (
                slideInVertically(spatialSpec) { if (up) it else -it } + fadeIn(effectsSpec)
                ) togetherWith (
                slideOutVertically(spatialSpec) { if (up) -it else it } + fadeOut(effectsSpec)
                )
        },
        label = label,
    ) { v ->
        Text(
            text(v),
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            textAlign = textAlign,
            modifier = modifier,
        )
    }
}

/** 金额滚动便捷版：分 → ¥文本（千分位），增减方向随金额 */
@Composable
fun AnimatedMoneyText(
    cents: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    label: String = "animatedMoney",
) = AnimatedNumberText(
    value = cents,
    text = { Money.yuanWithSign(it) },
    style = style,
    color = color,
    modifier = modifier,
    fontWeight = fontWeight,
    maxLines = maxLines,
    label = label,
)
