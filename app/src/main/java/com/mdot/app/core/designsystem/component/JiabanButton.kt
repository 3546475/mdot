package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import com.mdot.app.core.designsystem.miuix.MiuixJiabanButton
import com.mdot.app.domain.model.ThemeEngine
import com.mdot.app.core.designsystem.EngineIcons
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
import androidx.compose.foundation.layout.PaddingValues
import com.mdot.app.core.designsystem.Spacing
import androidx.compose.foundation.layout.fillMaxSize

/**
 * 按钮角色：实心主操作 / 描边次要 / 文字链接。语义选择标准见 docs/03 §13——
 * 一句话：**每个页面同时最多一个 PRIMARY**；危险动作不做危险色按钮，
 * 走 [InlineConfirmButton] 两态（带撤销 / 只确认）。
 */
enum class JiabanButtonRole { PRIMARY, SECONDARY, GHOST }

/**
 * 对话框按钮行「扁平化」下发（MIUIX 对话框卡片提供，见 DialogBackdrop.MiuixDialogCard）：
 * 为 true 时 [JiabanButtonRole.PRIMARY] 降级为 [JiabanButtonRole.GHOST]——
 * HyperOS 的对话框按钮行是**整宽纯文字格**，不该在文字下再垫一层圆角主题色底。
 * ⚠️ 只在 MIUIX 的对话框卡片里 provide，故 MD3 侧与其它场景一律不受影响。
 */
val LocalDialogButtonFlat = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 对话框按钮行「次要格中性色」下发（MIUIX 对话框卡片只给**取消格**提供）：
 * HyperOS 里确认格用主题强调色、取消/次要格用中性文字色。
 * ⚠️ 只在调用方**没有显式指定** contentColorOverride（如危险操作的红色）时才生效，
 * 且 MD3 侧读到的恒为 false ⇒ 行为不变。
 */
val LocalDialogButtonNeutral = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 「对话框按钮按原生渲染」下发（**只由 [com.mdot.app.core.designsystem.component.JiabanAlertDialog]
 * 的 MD3 分支提供**）：为 true 时，MD3 下 PRIMARY/SECONDARY 渲染成 M3 `Button` / `OutlinedButton`——
 * 即 v0.7.7 之前弹窗按钮的原样式（用户 2026-09-30：「MD3 弹窗的按钮不好看，用以前的；miuix 的可以」）。
 * MIUIX 侧不提供该标记，仍走库按钮；非对话框场景也不提供，保持本 App 的标准胶囊。
 */
val LocalDialogButtonNative = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 「弹窗按钮铺满整格」下发（只由 MiuixDialogCard 的按钮格提供）：为 true 时 MIUIX 侧按钮改用
 * `fillMaxSize()` ⇒ **整格可点**。根因：HyperOS 分栏按钮行里按钮原本是内容宽居中，
 * 只有文字那片区域能点到、文字旁的空白不算（用户 2026-09-30 报告）。
 * MD3 侧不提供（M3 弹窗按钮自身有整行可点语义）。
 */
val LocalDialogButtonFillCell = androidx.compose.runtime.compositionLocalOf { false }

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
    /** 内容色覆盖（仅 SECONDARY/GHOST 生效；危险操作传 error，PRIMARY 恒用 onPrimary） */
    contentColorOverride: Color? = null,
) {
    // 对话框按钮行里 PRIMARY 降级为文字按钮（MIUIX 卡片下发，MD3 侧读到的恒为 false）
    val effectiveRole = if (LocalDialogButtonFlat.current && role == JiabanButtonRole.PRIMARY) {
        JiabanButtonRole.GHOST
    } else {
        role
    }
    // 对话框次要格（取消）用中性文字色；显式传了 contentColorOverride（危险色）时以显式为准
    val textTone = if (LocalDialogButtonNeutral.current && contentColorOverride == null) {
        MaterialTheme.colorScheme.onSurface
    } else {
        contentColorOverride
    }

    // MD3 + 弹窗/弹层按钮位（由 JiabanAlertDialog 或自绘弹层下发 LocalDialogButtonNative）：
    // **按「以前的」原生样式渲染所有角色**——弹窗里的按钮在 v0.7.7 之前本来就是直接调 M3 原生按钮，
    // 用户 2026-09-30 反馈「MD3 弹窗的按钮不好看，用以前的；miuix 的可以」。
    // PRIMARY → M3 Button；SECONDARY → M3 OutlinedButton；GHOST → M3 TextButton（含危险色透传）。
    // ⚠️ 仅弹窗语境生效：非弹窗场景（页面里的按钮）保持本 App 的标准胶囊。
    if (LocalDialogButtonNative.current && Radius.engine == ThemeEngine.MD3 && !loading && icon == null) {
        when (role) {
            JiabanButtonRole.PRIMARY -> androidx.compose.material3.Button(
                onClick = onClick, modifier = modifier, enabled = enabled,
                // 横向收紧到 Spacing.s（= 原弹窗按钮那套「压低最小宽」的写法，防窄屏把两字挤成两行）；
                // 纵向 Spacing.s = M3 默认值，按钮高度与以前一致
                contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.s),
            ) { Text(text) }
            JiabanButtonRole.SECONDARY -> androidx.compose.material3.OutlinedButton(
                onClick = onClick, modifier = modifier, enabled = enabled,
                contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.s),
            ) { Text(text) }
            JiabanButtonRole.GHOST -> androidx.compose.material3.TextButton(
                onClick = onClick, modifier = modifier, enabled = enabled,
                contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.s),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = textTone ?: MaterialTheme.colorScheme.primary,
                ),
            ) { Text(text) }
        }
        return
    }
    // MIUIX 引擎：交库 Button/TextButton（PRIMARY 填充、其余文字按钮）。
    // 库按钮没有「收缩到 ✓」与前置图标两种形态 → 这两种情况回落下方自绘实现（两引擎同款）。
    if (Radius.engine == ThemeEngine.MIUIX && !loading && icon == null) {
        MiuixJiabanButton(
            text = text,
            onClick = onClick,
            modifier = if (LocalDialogButtonFillCell.current) modifier.fillMaxSize() else modifier,   // 铺满整格=整格可点
            primary = effectiveRole == JiabanButtonRole.PRIMARY,
            large = size == JiabanButtonSize.L,
            enabled = enabled,
            textColorOverride = textTone,
        )
        return
    }
    val density = LocalDensity.current
    val shape = engineShape(Radius.pill)
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
            effectiveRole == JiabanButtonRole.PRIMARY -> cs.primary
            else -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "jiabanContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurface.copy(alpha = 0.38f)
            effectiveRole == JiabanButtonRole.PRIMARY -> cs.onPrimary
            textTone != null -> textTone
            else -> cs.primary
        },
        animationSpec = colorSpec,
        label = "jiabanContent",
    )
    val borderColor = when {
        effectiveRole != JiabanButtonRole.SECONDARY -> Color.Transparent
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
                            EngineIcons.check(),
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
