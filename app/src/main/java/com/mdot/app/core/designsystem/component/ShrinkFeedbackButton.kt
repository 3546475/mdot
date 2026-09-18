package com.mdot.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing

/**
 * 「保存 / 提交」类按钮的收缩反馈（v0.6.19；记工页保存 / 调休余额「增加·扣减」/ 工资页保存同款做法）：
 * 点击后按钮**收缩到「✓ 内容的自然宽度」**，片刻后展开回文案态宽度。
 *
 * ⚠️ 收缩目标**不是**固定圆钮，而是**自适应内容宽度**：宽度动画到「当前内容的自然宽度」——
 * 文案态 = max([minWidth], 文字宽 + 2×[contentPadding])，✓ 态 = 图标宽 + 2×[contentPadding]。
 *
 * 实现要点（与 `InlineConfirmButton` 同一套做法，踩过的坑都写在注释里）：
 * - **量的是内容、不是胶囊**：内容放在 `wrapContentWidth(unbounded = true)` 里按无界宽测量，
 *   `onSizeChanged` 落在内容上 → 胶囊宽度动画到它。若反过来去量胶囊自身宽度，
 *   收缩/展开动画途中的中间宽度会被当成「静止宽」记下来 → 展开后卡在半途不复原。
 * - 内容容器**不能用 `fillMaxSize()`/`fillMaxWidth()`**：那会让胶囊撑满外壳（宽度失控）。
 * - 外壳固定（调用方给 `fillMaxWidth`）+ 内容居中 → 位置 =（外壳 − 胶囊宽）/ 2 是宽度的纯函数，
 *   左右每帧同步、绝对对称；**禁用 `Modifier.animateContentSize`**（尺寸/位置两套机制不同步，docs/11 034）。
 * - 内容被胶囊 `clip` 裁切 + 无界测量 → 宽度动画期间只「揭示」不换行、不挤压。
 * - 量纲默认对齐记月页「同步本月考勤」（`InlineConfirmButton` Standalone）：高 48dp、
 *   最小宽 144dp、水平内边距 24dp、`labelLarge`、实心主色无阴影。
 *
 * @param busy 反馈态：true = 收缩到 ✓ 内容宽；false = 展开回文案（停留时长由调用方控制）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShrinkFeedbackButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 胶囊高度（默认 48dp） */
    pillHeight: Dp = Spacing.xl * 2,
    /** 文案态最小宽度（默认 144dp）：避免文案被压窄换行 */
    minWidth: Dp = Spacing.xl * 6,
    /** 水平内边距（默认 24dp） */
    contentPadding: Dp = Spacing.xl,
    iconSize: Dp = 20.dp,
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(Radius.pill)
    val widthSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // 当前内容的自然宽 → 胶囊宽度动画到它（相位切换即自适应收缩/展开）
    var naturalWpx by remember { mutableStateOf(0) }
    var widthReady by remember { mutableStateOf(false) }
    val widthPx = remember { Animatable(0f) }
    LaunchedEffect(naturalWpx) {
        if (naturalWpx > 0) {
            if (widthReady) {
                widthPx.animateTo(naturalWpx.toFloat(), widthSpec)
            } else {
                widthPx.snapTo(naturalWpx.toFloat()) // 首帧直接落位，避免从 0 弹出
                widthReady = true
            }
        }
    }
    val flashAlpha by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = fadeSpec,
        label = "shrinkFeedbackFlash",
    )
    val interaction = remember { MutableInteractionSource() }
    // 禁用态视觉（M3 规范：容器 onSurface 12% / 内容 onSurface 38%）——如调休页未选时长时
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = colorSpec,
        label = "shrinkFeedbackContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        animationSpec = colorSpec,
        label = "shrinkFeedbackContent",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .then(
                    if (widthReady) Modifier.width(with(density) { widthPx.value.toDp() })
                    else Modifier,
                )
                .height(pillHeight)
                .clip(shape)
                .background(containerColor)
                .pressScale(interaction)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)) {
                Box(Modifier.onSizeChanged { naturalWpx = it.width }) {
                    if (busy) {
                        Icon(
                            painterResource(R.drawable.ic_ms_check),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .padding(horizontal = contentPadding)
                                .size(iconSize)
                                .graphicsLayer { alpha = flashAlpha },
                        )
                    } else {
                        Text(
                            text,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            // 文案在 [minWidth] 保护下盒子比文字宽：必须居中，否则文字贴左
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = minWidth)
                                .padding(horizontal = contentPadding)
                                .graphicsLayer { alpha = 1f - flashAlpha },
                        )
                    }
                }
            }
        }
    }
}
