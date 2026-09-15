package com.mdot.app.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.GLASS_AMBIENT_PERIOD_MS
import com.mdot.app.core.designsystem.component.GlassCard
import com.mdot.app.core.designsystem.component.HorizontalScrollRow
import com.mdot.app.core.designsystem.component.StarBand
import com.mdot.app.core.designsystem.component.pressScale

/**
 * 致谢者头像映射（名字 → drawable 资源）。
 *
 * ⚠️ 必须用**编译期资源引用**：release 开启了 `isShrinkResources`，若用
 * `resources.getIdentifier("ack_avatar_xxx", ...)` 动态查找，静态分析看不到引用，
 * 头像资源会在打 release 包时被裁掉（debug 正常、release 丢图的经典坑）。
 * 新增致谢者时在此登记；未登记者 UI 自动回退为首字母徽章。
 */
private fun avatarResFor(name: String): Int? =
    when (name.trimStart('@').lowercase()) {
        "littlele" -> R.drawable.ack_avatar_littlele
        "ting" -> R.drawable.ack_avatar_ting
        "faye" -> R.drawable.ack_avatar_faye
        else -> null
    }

/** 一条致谢项：名字 + 可选短标签（名单里用 `名字|标签` 语法提供，标签可省略） */
internal data class Acknowledgement(val name: String, val tag: String?)

internal fun parseAckEntries(raw: List<String>): List<Acknowledgement> =
    raw.map { entry ->
        val parts = entry.split('|', limit = 2)
        Acknowledgement(
            name = parts[0].trim(),
            tag = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }.filter { it.name.isNotEmpty() }

/**
 * 致谢名单卡（关于页「开源许可」下方）。
 *
 * 卡体视觉（渐变玻璃底/双光源/磨砂面/入场光扫/上边彩虹跑马灯）复用 [GlassCard]，
 * 与「检查更新卡」保持完全同款；卡内为：标题行（描边环徽章 + 标题 + 闪光 + 星光带）
 * → 致谢砖（**单行横向滚动**，卡片长宽不随人数变化）→ 包容句。
 *
 * 名单维护：改 strings 的 `settings_ack_names`（`@名字` 或 `@名字|短标签`）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AcknowledgementCard(
    modifier: Modifier = Modifier,
) {
    val entries = parseAckEntries(stringArrayResource(R.array.settings_ack_names).toList())
    val cs = MaterialTheme.colorScheme
    val onSheet = cs.onPrimaryContainer

    // 入场进度（砖块错峰用，与 GlassCard 内部入场动画同源语义）
    val scheme = MaterialTheme.motionScheme
    val enterSpec = scheme.defaultSpatialSpec<Float>()
    val enter = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    val progress = enter.value

    // 星光带相位（周期与卡体彩虹共用常量）
    val flow = rememberInfiniteTransition(label = "ackFlow")
    val starShift by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = GLASS_AMBIENT_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ackStarShift",
    )

    GlassCard(modifier = modifier) {
        // ---- 标题行：徽章 + 「致谢 ✦」+ 紧随其后的星光带 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientRingBadge(
                size = 38.dp,
                ringWidth = 2.dp,
                ringColors = listOf(cs.primary, cs.tertiary, cs.primary),
                fillColors = listOf(cs.primary, cs.tertiary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ms_favorite),
                    contentDescription = null, // 装饰性：语义由相邻文字承载
                    tint = cs.onPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.size(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_ack_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onSheet,
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Icon(
                        painter = painterResource(R.drawable.ic_ms_auto_awesome),
                        contentDescription = null,
                        tint = cs.tertiary.copy(alpha = 0.9f),
                        modifier = Modifier.size(15.dp),
                    )
                    // 星光带紧跟「致谢」标题，向右铺满余下宽度
                    Spacer(Modifier.size(Spacing.s))
                    StarBand(
                        shift = starShift,
                        tint = onSheet,
                        accent = cs.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.settings_ack_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSheet.copy(alpha = 0.70f),
                )
            }
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.m))
            // 名单恒为**单行横向滚动**（不用 FlowRow 换行）：
            // 换行会让卡片随人数变高，破坏「砖块尺寸/卡片长宽不随人数变化」；
            // 横向滚动则行高恒定、无滚动条。
            // 用 HorizontalScrollRow 而非裸 horizontalScroll：外层「关于/设置」是
            // HorizontalPager，裸滚动会被它抢手势（实测在名单内左滑直接切页签）；
            // 该容器经 nestedScroll 让内层优先消费横向手势，滚到边缘才放行给 Pager。
            HorizontalScrollRow {
                entries.forEachIndexed { index, entry ->
                    ContributorChip(
                        entry = entry,
                        progress = progress,
                        index = index,
                        total = entries.size,
                    )
                }
            }
        }

        // 包容句：名单之外，把"每一位"也谢到
        Spacer(Modifier.height(Spacing.m))
        Text(
            stringResource(R.string.settings_ack_footer),
            style = MaterialTheme.typography.labelSmall,
            color = onSheet.copy(alpha = 0.62f),
        )
    }
}

/** 渐变描边环徽章：外圈渐变描边 + 内部渐变填充（比纯色圆更精致） */
@Composable
private fun GradientRingBadge(
    size: androidx.compose.ui.unit.Dp,
    ringWidth: androidx.compose.ui.unit.Dp,
    ringColors: List<Color>,
    fillColors: List<Color>,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val stroke = ringWidth.toPx()
                drawCircle(
                    brush = Brush.sweepGradient(ringColors),
                    radius = this.size.minDimension / 2f - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            }
            .padding(ringWidth + 1.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(fillColors)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 单块致谢砖：半透磨砂玻璃（无投影）——半透明白垂直渐变 + 顶部高光边 + 外光晕，
 * 左上弧形高光、描边环徽章、尾部小爱心、短标签。
 *
 * 交互反馈：按压整体轻微缩小（pressScale，与 clickable 共用 interactionSource）
 * 且弧形高光顺压向位移；悬停（鼠标/手写笔）只提亮高光、不改尺寸（避免布局抖动）。
 * 入场按索引错峰（进度插值，非独立动画）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContributorChip(
    entry: Acknowledgement,
    progress: Float,
    index: Int,
    total: Int,
) {
    val cs = MaterialTheme.colorScheme
    val initial = remember(entry.name) { entry.name.trimStart('@').take(1).uppercase() }

    // 交互态：与 clickable 共用同一 interactionSource（硬规则 7）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val hiAlpha = when {
        pressed -> 0.72f
        hovered -> 0.54f
        else -> 0.42f
    }
    // 非 DrawScope 作用域：dp→px 需经 LocalDensity
    val density = LocalDensity.current
    val hiShiftPx = if (pressed) with(density) { 3.dp.toPx() } else 0f

    // 错峰：整段进度切 total 份窗口，第 index 块在自己的窗口内完成 0→1
    val window = 0.5f / total.coerceAtLeast(1)
    val local = ((progress - index * window) / 0.5f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            // 顺序：graphicsLayer(alpha) 在最前，其后仅 clip/背景/边框/绘制——无 shadow，安全
            .graphicsLayer {
                alpha = local
                translationY = (1f - local) * 10.dp.toPx()
            }
            // ⚠️ pressScale 必须放在所有视觉层（clip/background/border/drawBehind）之前：
            // 它内部是 graphicsLayer，只作用于链中其后的绘制——放后面则背景与边框不缩放，
            // 表现为"按下去没反应"（真机实测砖块宽度恒为 144px，见 docs/11 020）
            .pressScale(interaction, pressedScale = 0.965f)
            .clip(RoundedCornerShape(Radius.small))
            .drawBehind {
                // 外光晕：极低 alpha 白色扩散，替代阴影提供"浮起"暗示
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    cornerRadius = CornerRadius(Radius.small.toPx() + 2.dp.toPx()),
                    topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.62f), // 上：受光更亮
                        Color.White.copy(alpha = 0.34f), // 下：渐隐，玻璃厚度感
                    ),
                ),
            )
            // 弧形高光：左上角受光曲面反光；按压时提亮并顺压向位移，悬停提亮
            .drawBehind {
                val r = size.height * 1.5f
                drawArc(
                    color = Color.White.copy(alpha = hiAlpha),
                    startAngle = 195f,
                    sweepAngle = 55f,
                    useCenter = false,
                    topLeft = Offset(
                        -size.height * 0.35f + hiShiftPx,
                        -size.height * 1.05f + hiShiftPx,
                    ),
                    size = Size(r, r),
                    style = Stroke(width = 2.2f),
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f), // 顶部高光边
                        Color.White.copy(alpha = 0.25f), // 底部弱边
                    ),
                ),
                shape = RoundedCornerShape(Radius.small),
            )
            // 无长按/点击语义，仅保留按压反馈；interactionSource 仍由 pressScale 共用
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            )
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarRes = avatarResFor(entry.name)
        GradientRingBadge(
            size = 26.dp,
            ringWidth = 1.5.dp,
            ringColors = listOf(cs.tertiary, cs.primary, cs.tertiary),
            fillColors = listOf(cs.tertiary, cs.primary),
        ) {
            if (avatarRes != null) {
                // 头像：圆形裁切铺满徽章内圈（ContentScale.Crop 保证不变形）
                Image(
                    painter = painterResource(avatarRes),
                    contentDescription = null, // 装饰性：名字紧随其后，无需重复朗读
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 未配头像者回退为首字母（名单可自由增删，不强制每人都有图）
                Text(
                    initial,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onTertiary,
                )
            }
        }
        Spacer(Modifier.size(Spacing.s))
        Column {
            Text(
                entry.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                // 半透明白底上沿用卡面主色文字，保证对比度（onSurface 在此底上偏灰）
                color = cs.onPrimaryContainer,
            )
            // 短标签（名单里用 `名字|标签` 提供，缺省不显示）
            entry.tag?.let { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.66f),
                )
            }
        }
        Spacer(Modifier.size(Spacing.xs))
        // 尾部小爱心：给砖块一个温柔收尾（不与名字竞争，尺寸很小、低对比）
        Icon(
            painter = painterResource(R.drawable.ic_ms_favorite),
            contentDescription = null,
            tint = cs.primary.copy(alpha = 0.30f),
            modifier = Modifier.size(11.dp),
        )
    }
}
