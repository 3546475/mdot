package com.mdot.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.ButtonSpec
import com.mdot.app.core.designsystem.Radius

/**
 * 按钮角色：实心主操作 / 描边次要 / 文字链接。语义选择标准见 docs/03 §13——
 * 一句话：**每个页面同时最多一个 PRIMARY**；危险动作不做危险色按钮，
 * 走 [InlineConfirmButton] 两态（带撤销 / 只确认）。
 */
enum class JiabanButtonRole { PRIMARY, SECONDARY, GHOST }

/** 按钮尺寸：[JiabanButtonSize.L] 页面级主操作 48dp；[JiabanButtonSize.M] 次要/弹窗内 40dp */
enum class JiabanButtonSize { L, M }

/**
 * 全 app 按钮唯一入口（按钮规范化，docs/03 §13）。
 *
 * 统一了此前 91 处裸 M3 按钮的四个漂移维度：
 * - **高度**：L=48dp（[ButtonSpec.heightL]，对齐保存类按钮量纲）/ M=40dp（M3 默认档）；
 * - **层级**：[JiabanButtonRole] 三角色；
 * - **按压反馈**：pressScale + 涟漪叠加（工资设置保存按钮基准，共用同一
 *   interactionSource，规则 7）；
 * - **加载/成功反馈**：[loading] 时**收缩到 ✓ 的内容自然宽、完成后展开回文案**
 *   （工资页保存 / 关于页检查更新同款动画，原 InlineLoadingButton 圆环形态废弃）。
 *
 * 实现为自绘胶囊（不再套 M3 Button）：宽度动画需要「外壳固定 + 内容无界测量 +
 * 居中」的自控布局（docs/11 034 同族坑：禁 `animateContentSize`，位置必须是宽度的
 * 纯函数）。M3 的涟漪经 `clickable(indication = LocalIndication)` 保留。
 *
 * 反馈形态的选择标准（写全在 docs/03 §13.3）：破坏性 = [InlineConfirmButton] 两态；
 * 其余一律本组件。
 *
 * @param loading 进行中/刚完成：收缩到 ✓；回到 false 展开回文案。期间按钮保持可点拦截
 * @param icon 文案态的前置图标（L 档少量场景使用）
 */
@Composable
fun JiabanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: JiabanButtonRole = JiabanButtonRole.PRIMARY,
    size: JiabanButtonSize = JiabanButtonSize.M,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: Painter? = null,
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(Radius.pill)
    val motion = MaterialTheme.motionScheme
    val widthSpec = motion.fastSpatialSpec<Float>()
    val fadeSpec = motion.defaultEffectsSpec<Float>()
    val colorSpec = motion.defaultEffectsSpec<Color>()

    val pillHeight = if (size == JiabanButtonSize.L) ButtonSpec.heightL else ButtonSpec.heightM
    val minWidth = if (size == JiabanButtonSize.L) ButtonSpec.minWidthL else 0.dp
    // L 档水平内边距 24dp（ButtonSpec）；M 档取 M3 Button 默认 ContentPadding 的水平值，
    // 与迁移前 M3 按钮的观感逐像素一致
    val hPadding = if (size == JiabanButtonSize.L) ButtonSpec.contentPaddingL else 16.dp

    // 当前内容的自然宽 → 胶囊宽度动画到它（相位切换即自适应收缩/展开；
    // 首帧 snapTo 直接落位，避免从 0 弹出——ShrinkFeedbackButton 同款机制）
    var naturalWpx by remember { mutableStateOf(0) }
    var widthReady by remember { mutableStateOf(false) }
    val widthPx = remember { Animatable(0f) }
    LaunchedEffect(naturalWpx) {
        if (naturalWpx > 0) {
            if (widthReady) {
                widthPx.animateTo(naturalWpx.toFloat(), widthSpec)
            } else {
                widthPx.snapTo(naturalWpx.toFloat())
                widthReady = true
            }
        }
    }
    val flashAlpha by animateFloatAsState(
        targetValue = if (loading) 1f else 0f,
        animationSpec = fadeSpec,
        label = "jiabanFlash",
    )
    val interaction = remember { MutableInteractionSource() }

    val cs = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurface.copy(alpha = 0.12f)
            role == JiabanButtonRole.PRIMARY -> cs.primary
            else -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "jiabanContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurface.copy(alpha = 0.38f)
            role == JiabanButtonRole.PRIMARY -> cs.onPrimary
            else -> cs.primary
        },
        animationSpec = colorSpec,
        label = "jiabanContent",
    )
    val borderColor = when {
        role != JiabanButtonRole.SECONDARY -> Color.Transparent
        enabled -> cs.outline
        else -> cs.onSurface.copy(alpha = 0.12f)
    }

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
                .then(
                    if (role == JiabanButtonRole.SECONDARY) {
                        Modifier.border(1.dp, borderColor, shape)
                    } else Modifier,
                )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onSizeChanged { naturalWpx = it.width },
                ) {
                    if (loading) {
                        Icon(
                            painterResource(R.drawable.ic_ms_check),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .padding(horizontal = hPadding)
                                .size(ButtonSpec.iconSize)
                                .graphicsLayer { alpha = flashAlpha },
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // minWidth 保护下盒子比文字宽：内容必须居中排列，否则短文案（如「保存」）贴左
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = minWidth)
                                .padding(horizontal = hPadding)
                                .graphicsLayer { alpha = 1f - flashAlpha },
                        ) {
                            icon?.let {
                                Icon(
                                    it,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(ButtonSpec.iconSize),
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(
                                text,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                // 文案在 minWidth 保护下盒子比文字宽：必须居中，否则文字贴左
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 行内图标按钮（48dp 热区 + 图标 20dp 统一）：行内编辑/删除/导航等入口的唯一形态 */
@Composable
fun IconGhostButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(
            painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(ButtonSpec.iconSize),
        )
    }
}
