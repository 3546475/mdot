package com.mdot.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing

/** 原地确认按钮三相：普通 → 询问（取消/确认）→ 完成（撤销 + 倒计时）。 */
enum class InlineConfirmPhase { Idle, Asking, Done }

/**
 * 外观式样：
 * - [Standalone] 独立/整行：固定外壳（宽度动画左右对称）+ 居中 + 实心主色 idle（如记月页「导入上月」）；
 * - [Compact]    行内紧凑：外壳随内容（不预留、不挤压同行信息）+ 右对齐 + idle 为 error 色朴素文字
 *                （如列表行「删除」，与原先的 TextButton 观感一致）。
 */
enum class InlineConfirmStyle { Standalone, Compact }

/** 胶囊最小高度（48dp = Spacing.xl × 2） */
private val ButtonHeight = Spacing.xl * 2

/** 收缩态最小宽度（144dp = Spacing.xl × 6）：最小宽度保护，避免文字被压窄换行 */
private val MinWidth = Spacing.xl * 6

/**
 * 固定外壳宽度（240dp = Spacing.xl × 10）：最宽相（询问态）+ 余量。
 * 外壳固定不动、胶囊在其内居中 → 位置恒等于 (外壳宽 − 胶囊宽) / 2，与宽度动画天然同步（见下）。
 * 仅 [InlineConfirmStyle.Standalone] 使用。
 */
private val ShellWidth = Spacing.xl * 10

/** 倒计时进度条细高度（3dp）：纯图形装饰、非间距语义，故就地常量（同 InlineLoadingButton 的 18/2dp） */
private val BurnBarHeight = 3.dp

/**
 * 原地确认按钮（Bencho InlineConfirm 交互移植 + 本项目 MD3E 视觉/动效）。
 *
 * 交互（按钮即它自己的对话框：不弹层、不顶开周围布局，选择留在刚点它的光标处）：
 * - [InlineConfirmPhase.Idle]   普通按钮（[idleText]），点击**原地展开**进入询问；
 * - [InlineConfirmPhase.Asking] 展开「[cancelText] / [confirmText]」两键；确认执行 [onConfirm]；
 * - [InlineConfirmPhase.Done]   仅当给了 [onUndo] 时进入：显示「[undoText]」+ 底部倒计时进度条
 *   （宽度 100% → 0 线性，自两侧向中间收拢），期间点撤销执行 [onUndo]；倒计时结束自动回 Idle。
 *
 * **撤销能力是可选的**——这是本组件的关键分界：
 * - 传了 [onUndo]：撤销住在发起它的按钮占地里，破坏性路径因此**不需要额外 toast**
 *   （要求触发元素在操作后**依然存在**，如常驻的页面级按钮/卡片）；
 * - 不传 [onUndo]（null）：只保留「原地确认」，确认后直接回 Idle——用于**操作后触发元素会消失**
 *   的场景（列表行删除、菜单项），此时撤销由调用方的**底部信息提示窗（Snackbar）**承担。
 *
 * 宽度动画（**对齐关于页「检查更新」按钮**的做法，见 DataSourceScreen UpdateButton）：
 * 外壳宽度**固定** [ShellWidth] + `contentAlignment = Center`，胶囊宽度由**单一** [Animatable]
 * 驱动（首次测量 `snapTo` 自然宽，之后相位切换 `animateTo`）；位置 =（外壳 − 胶囊）/2 是宽度的
 * 纯函数，故宽度与位置**每帧同步、左右绝对对称**。
 * ⚠️ 特意**不用** `Modifier.animateContentSize`：它会同时动画容器尺寸与子级约束，
 * 尺寸/位置两条动画不同步（参考实现录屏实测：尺寸瞬间缩到位、位置随后慢漂 → 一侧瞬时空洞）。
 *
 * 最小宽度保护：文字 `softWrap = false` + `maxLines = 1`；内容按**无界宽**测量取自然宽
 * （`wrapContentWidth(unbounded = true)`）、被胶囊裁切——宽度展开中只可能「揭示」，**永不换行/挤扁**。
 *
 * ⚠️ 硬规则 7：宽度/配色走 motionScheme spec（交互反馈）；倒计时进度条是**功能性时长动画**，
 * 三档 spec 无对应档位（同 GlassCard / docs 11 020 例外），故用显式时长的线性 tween——
 * 「线性」也正是倒计时的语义（匀速流逝）。
 *
 * 风格走本项目 MD3E：胶囊形 [Radius.pill]、色角色分层（idle 主色或 error / asking error 强调危险 /
 * done 中性面 + 主色可撤销），按压反馈统一 [pressScale]。
 *
 * @param onUndo 撤销回调；null = 只做原地确认（撤销交给调用方的信息提示窗）。
 * @param resetKey 变化时强制回到 [startPhase]（如翻月/换目标），未完成的撤销窗口随之作废。
 * @param undoWindowMs 撤销窗口时长（进度条 1 → 0 线性走完的时长）。
 * @param startPhase 初始相位；[InlineConfirmPhase.Asking] 用于「由外部动作触发确认」
 *   （如点选工时卡后就地弹出确认），此时 [onSettled] 会在取消/倒计时结束时回调，供调用方收尾。
 * @param onSettled 相位回到 [InlineConfirmPhase.Idle] 时回调（取消、倒计时结束、撤销之后）。
 * @param resetOnSettle 收尾时是否自动回 Idle。默认 true（常驻按钮：该自己复位）；
 *   传 false 时收尾**保留当前相**，由调用方用进退场动画（如 AnimatedVisibility）卸载——
 *   避免退场途中内容先跳回 Idle 再消失（本项目的工时制度切换卡片即用此模式）。
 */
@Composable
fun InlineConfirmButton(
    idleText: String,
    confirmText: String,
    cancelText: String,
    undoText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
    style: InlineConfirmStyle = InlineConfirmStyle.Standalone,
    resetKey: Any? = null,
    undoWindowMs: Long = 5_000L,
    startPhase: InlineConfirmPhase = InlineConfirmPhase.Idle,
    onSettled: (() -> Unit)? = null,
    resetOnSettle: Boolean = true,
) {
    var phase by remember(resetKey) { mutableStateOf(startPhase) }
    val burn = remember(resetKey) { Animatable(1f) }
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(Radius.pill)
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    // fastSpatialSpec 是 @Composable 泛型函数，须显式 <Float> 且在 Composable 上下文先取值
    val widthSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    val standalone = style == InlineConfirmStyle.Standalone
    val shellWidth: Dp? = if (standalone) ShellWidth else null
    val cellMinWidth: Dp = if (standalone) MinWidth else 0.dp

    // ---- 宽度：单一 Animatable 驱动；首次测量 snap、之后相位切换动画 ----
    var naturalWidthPx by remember(resetKey) { mutableStateOf(0) }
    var widthReady by remember(resetKey) { mutableStateOf(false) }
    val widthPx = remember(resetKey) { Animatable(0f) }
    LaunchedEffect(naturalWidthPx) {
        if (naturalWidthPx > 0) {
            if (widthReady) {
                widthPx.animateTo(naturalWidthPx.toFloat(), widthSpec)
            } else {
                widthPx.snapTo(naturalWidthPx.toFloat()) // 首帧直接落位，避免从 0 弹出的闪动
                widthReady = true
            }
        }
    }

    /** 收尾：回 Idle（[resetOnSettle] = false 时保留当前相）并通知调用方 */
    fun settle() {
        if (resetOnSettle) phase = InlineConfirmPhase.Idle
        onSettled?.invoke()
    }

    // Done 倒计时：1 → 0 匀速；到点自动收尾。撤销点击改 phase 会取消本协程（进度归位）。
    LaunchedEffect(phase) {
        if (phase == InlineConfirmPhase.Done) {
            burn.snapTo(1f)
            burn.animateTo(0f, tween(durationMillis = undoWindowMs.toInt(), easing = LinearEasing))
            settle()
        } else {
            burn.snapTo(1f)
        }
    }

    val isIdle = phase == InlineConfirmPhase.Idle
    val container by animateColorAsState(
        targetValue = when {
            isIdle && standalone -> cs.primary
            isIdle -> Color.Transparent
            else -> cs.surfaceContainerHighest
        },
        animationSpec = colorSpec,
        label = "inlineConfirmContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when (phase) {
            InlineConfirmPhase.Idle -> Color.Transparent
            InlineConfirmPhase.Asking -> cs.error.copy(alpha = 0.45f)
            InlineConfirmPhase.Done -> cs.primary.copy(alpha = 0.45f)
        },
        animationSpec = colorSpec,
        label = "inlineConfirmBorder",
    )
    val idleContentColor = if (standalone) cs.onPrimary else cs.error

    // 固定外壳（Standalone）：胶囊在其内居中 → 位置随宽度自动对称。
    // Compact：外壳随内容（不预留），交给同行布局吸收。
    Box(
        modifier = if (shellWidth != null) modifier.width(shellWidth) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (widthReady) {
                        Modifier.width(with(density) { widthPx.value.toDp() })
                    } else {
                        Modifier
                    },
                )
                .height(ButtonHeight)
                .clip(pill)
                .background(container)
                .border(BorderStroke(1.dp, borderColor), pill),
            contentAlignment = Alignment.Center,
        ) {
            // 内容：无界宽测量取自然宽（onSizeChanged 落在无界侧），居中、被胶囊裁切 → 只揭示不挤压
            Box(Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)) {
                Box(Modifier.onSizeChanged { naturalWidthPx = it.width }) {
                    when (phase) {
                        InlineConfirmPhase.Idle -> IdleRow(
                            text = idleText,
                            contentColor = idleContentColor,
                            minWidth = cellMinWidth,
                            compact = !standalone,
                            onClick = { phase = InlineConfirmPhase.Asking },
                        )

                        InlineConfirmPhase.Asking -> AskingRow(
                            cancelText = cancelText,
                            confirmText = confirmText,
                            compact = !standalone,
                            onCancel = { settle() },
                            onConfirm = {
                                onConfirm()
                                if (onUndo != null) {
                                    phase = InlineConfirmPhase.Done
                                } else {
                                    settle() // 只确认：撤销由调用方的信息提示窗承担
                                }
                            },
                        )

                        InlineConfirmPhase.Done -> DoneRow(
                            text = undoText,
                            contentColor = cs.primary,
                            minWidth = cellMinWidth,
                            compact = !standalone,
                            onClick = {
                                onUndo?.invoke()
                                settle()
                            },
                        )
                    }
                }
            }

            if (phase == InlineConfirmPhase.Done) {
                // 倒计时进度条：贴胶囊底部，轨道整宽 + 前景按 burn 比例 100% → 0，
                // 居中收拢（contentAlignment=Center）→ 宽度自两侧同时向中间收，而非单侧右缩
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(BurnBarHeight)
                        .background(cs.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(burn.value)
                            .fillMaxHeight()
                            .background(cs.primary),
                    )
                }
            }
        }
    }
}

/** Idle：整条胶囊即按钮（宽度铺满最小宽，按压反馈 + 点击原地展开）。 */
@Composable
private fun IdleRow(
    text: String,
    contentColor: Color,
    minWidth: Dp,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .widthIn(min = minWidth)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = ButtonHeight)
            .padding(horizontal = if (compact) Spacing.m else Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CellText(text, contentColor)
    }
}

/** Asking：中性底上的两键——取消（低强调）+ 确认（error 实心小胶囊，强调危险）。 */
@Composable
private fun AskingRow(
    cancelText: String,
    confirmText: String,
    compact: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(
            horizontal = if (compact) Spacing.xs else Spacing.m,
            vertical = Spacing.xs,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else Spacing.s),
    ) {
        val cancelInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .pressScale(cancelInteraction)
                .clickable(
                    interactionSource = cancelInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onCancel,
                )
                .heightIn(min = ButtonHeight - Spacing.s)
                .padding(horizontal = if (compact) Spacing.m else Spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            CellText(cancelText, cs.onSurfaceVariant)
        }

        val confirmInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .pressScale(confirmInteraction)
                .background(cs.error)
                .clickable(
                    interactionSource = confirmInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onConfirm,
                )
                .heightIn(min = ButtonHeight - Spacing.s)
                .padding(horizontal = if (compact) Spacing.m else Spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            CellText(confirmText, cs.onError)
        }
    }
}

/** Done：整条胶囊即撤销按钮（主色文字），底部叠倒计时进度条。 */
@Composable
private fun DoneRow(
    text: String,
    contentColor: Color,
    minWidth: Dp,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .widthIn(min = minWidth)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = ButtonHeight)
            .padding(horizontal = if (compact) Spacing.m else Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CellText(text, contentColor)
    }
}

/** 单元格文字：单行不换行（宽度动画期间宁裁不折），保证最小宽度保护的语义闭环。 */
@Composable
private fun CellText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}
