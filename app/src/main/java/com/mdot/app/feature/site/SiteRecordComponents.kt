package com.mdot.app.feature.site

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 记工页共享组件：分段胶囊、tonal 卡片行（大卡内行/整卡行/裸行）、大字直填行、
 * 工钱 hero 卡、选择瓦片、徽标胶囊与金额格式化工具。
 */

/** 分段胶囊（子页签共用：点工/包工、借支/结算、单位选择等；顶栏主 Tab 已改为滑块式 RecordHeader） */
@Composable
internal fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "segBg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "segFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "segScale",
    )
    Text(
        text,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radius.pill))
            .background(bgColor)
            .pressScale(interaction, pressedScale = 0.94f)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fgColor,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectDateCard(
    state: SiteRecordUiState,
    onOpenProjectPick: () -> Unit,
    onShowDatePicker: () -> Unit,
) {
    SectionCard {
        Column {
            // 项目行：点击进入项目管理页「选择模式」，选完返回按新项目刷新；尾注展示当前日价
            TappableTonalRow(
                iconRes = R.drawable.ic_ms_dashboard,
                label = stringResource(R.string.site_row_project),
                value = state.projectName,
                trailingText = state.project?.takeIf { it.dailyRateCents > 0 }
                    ?.let { stringResource(R.string.site_project_rate_hint, yuanShort(it.dailyRateCents)) }
                    ?: "",
                onClick = onOpenProjectPick,
            )
            // 日期行：今天徽标 + 可多选尾注
            val isToday = state.date == LocalDate.now()
            val dateLabel = state.date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
            TappableTonalRow(
                iconRes = R.drawable.ic_ms_more_time,
                label = stringResource(R.string.site_row_date),
                value = if (state.extraDates.isEmpty()) dateLabel else "$dateLabel +${state.extraDates.size}",
                badge = {
                    if (isToday) {
                        BadgePill(
                            stringResource(R.string.site_today),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                },
                // 行上**只有一个入口**（点行 → 选择器；多选在选择器内切换）——
                // 曾在这里加过「多选日期」胶囊，把日期挤成两行、还与「今天」徽标抢视觉（用户反馈突兀）
                onClick = onShowDatePicker,
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = Spacing.s),
    )
}

/** tonal 圆角卡片行（SectionCard 24dp）：图标方块 + 标签 + 值（大字可选）+ 徽标/尾注 + chevron */
@Composable
internal fun TonalRowCard(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueLarge: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SectionCard(onClick = onClick) {
        TonalRow(
            iconRes = iconRes, label = label, value = value, valueColor = valueColor,
            valueLarge = valueLarge, badge = badge, trailingText = trailingText,
            trailingActive = trailingActive, onTrailing = onTrailing,
            showChevron = onClick != null,
        )
    }
}

/**
 * 大卡内的可点行（一张 SectionCard 内多行时用）：行自身带按压反馈与 chevron，
 * 与 TonalRowCard 共用同一套行渲染（TonalRow）。
 */
@Composable
internal fun TappableTonalRow(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TonalRow(
        iconRes = iconRes, label = label, value = value, valueColor = valueColor,
        badge = badge, trailingText = trailingText, trailingActive = trailingActive,
        onTrailing = onTrailing, showChevron = true,
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(Radius.button))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = Spacing.xs),
    )
}

/** 行内容：图标 tonal 方块 + 标签 + 值 + 徽标/尾注 + chevron */
@Composable
private fun TonalRow(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueLarge: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标 tonal 方块（03 §3.4）
        Box(
            Modifier
                .size(IconBoxSpec.tile.box)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes), null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconBoxSpec.tile.icon),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.m))
        Text(
            value,
            style = if (valueLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (valueLarge) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            maxLines = if (valueLarge) 1 else 2,
            modifier = Modifier.weight(1f),
        )
        badge?.invoke()
        if (trailingText.isNotEmpty()) {
            if (onTrailing != null) {
                // 行内动作：轻量 tone 胶囊（与行内徽标同款）+ **48dp 触控区**。
                // 规则见 docs/03 §5.4.4：不描边、不用主色加粗——描边款会把值挤换行、与徽标抢视觉。
                val trailInteraction = remember { MutableInteractionSource() }
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trailingActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .pressScale(trailInteraction, pressedScale = 0.94f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(
                            if (trailingActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable(interactionSource = trailInteraction, indication = LocalIndication.current, onClick = onTrailing)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            } else {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(Spacing.xs))
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 分 → 短元文本：整数去小数（260），非整数两位小数（260.50） */
private fun yuanShort(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else String.format(java.util.Locale.US, "%.2f", cents / 100.0)

/**
 * 工钱 hero 卡：primaryContainer 底色突出页面主角；¥ + 大字数字带滚动动画
 * （金额增加时新值自下滑入、旧值上滑淡出，减少则反向）；尾注为点工标准摘要；点击弹标准设置。
 */
@Composable
internal fun PayHeroCard(
    previewCents: Long,
    summary: String,
    onClick: () -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    SectionCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(IconBoxSpec.tile.box)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ms_paid), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconBoxSpec.tile.icon),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                stringResource(R.string.site_pay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.m))
            AnimatedContent(
                targetState = previewCents,
                transitionSpec = {
                    val up = targetState > initialState
                    (
                        slideInVertically(spatialSpec) { if (up) it else -it } + fadeIn(effectsSpec)
                        ) togetherWith (
                        slideOutVertically(spatialSpec) { if (up) -it else it } + fadeOut(effectsSpec)
                        )
                },
                label = "payHeroAmount",
            ) { cents ->
                Text(
                    stringResource(R.string.site_yuan_symbol) + " " + Money.yuanText(cents).replace(",", ""),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 选择瓦片：M3E 弹簧缩放 + tonal 变色 */
@Composable
internal fun SelectTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileBg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "tileScale",
    )
    Box(
        modifier
            .scale(scale)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.button))
            .background(bgColor)
            .pressScale(interaction, pressedScale = 0.92f)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fgColor,
        )
        // 选中角标：右上角小对勾，强化互斥选中确认感
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_ms_check), null,
                tint = fgColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(14.dp),
            )
        }
    }
}

@Composable
private fun PlannedSegment(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(4.dp))
            BadgePill(
                stringResource(R.string.site_planned),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgePill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .padding(start = Spacing.s)
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

/**
 * 大字直填行（工钱样式）：图标方块 + 标签 + 大数字输入 + 尾部控件。
 * fallback：未聚焦且未输入时的回显（如量×价自动值/0.00）。
 */
@Composable
internal fun BigAmountRow(
    iconRes: Int,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fallback: String = "",
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(IconBoxSpec.tile.box)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconBoxSpec.tile.icon),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.m))
            BasicTextField(
                value = if (focused) value else value.ifEmpty { fallback },
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
            )
            trailing()
        }
    }
}

/** 数值 + 单位文案：整数不带小数（如 2），非整数一位小数（如 2.5）；在 Composable 上下文调用 */
@Composable
internal fun fmtAmount(v: Double, labelRes: Int): String =
    stringResource(labelRes, if (v % 1.0 == 0.0) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v))
