package com.mdot.app.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.mdot.app.core.designsystem.DialogSpec
import com.mdot.app.core.designsystem.SheetBackdrop
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.sheetSpatialSpec
import com.mdot.app.core.designsystem.engineIsMiuix
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateListOf

/**
 * **页内模态覆盖层**（原型，2026-09-30）——与 `JiabanAlertDialog` 同一套内容，但**不开任何窗口**。
 *
 * 动机：K80 PRO（Android 16 / HyperOS）上 ROM 给对话框**窗口表面**加了动画，主题的
 * `windowAnimationStyle` / enter/exit 全 `@null`、运行期 `setWindowAnimations(0)`、甚至把系统动画缩放
 * 置 0 都治不住（真机打点证明视图层完全没动，见 `DialogBackdrop.kt` 的探针注释）。**不新开窗**是唯一
 * 结构上能绕开它的做法。
 *
 * ⚠️ 原型取舍：状态用**全局单例**承载（免去层层 provider）；正式化应改为 AppRoot 持有 + DI 注入。
 * ⚠️ 卡片是**简化版**（M3 观感：标题/正文 + 右对齐按钮行），未复刻 M3 AlertDialog 的逐像素样式；
 * 本原型要验证的是**动画/层级**，不是像素。
 */
object OverlayDialogs {
    /**
     * 覆盖层**栈**（后进先出）：弹窗里再开弹窗（如"删除确认"叠在某个弹窗上）时，
     * 只渲染栈顶；栈顶关闭后下面那层自动回到可见。
     * ⚠️ 不能用单槽：第二个弹窗注册会覆盖第一个，它 `onDispose` 时会把第一个一起清空（弹窗凭空消失）。
     */
    private val stack = mutableStateListOf<OverlayEntry>()

    /** 栈顶（即当前显示的覆盖层） */
    internal val current: OverlayEntry? get() = stack.lastOrNull()

    internal fun push(entry: OverlayEntry) { stack.add(entry) }

    internal fun remove(entry: OverlayEntry) { stack.remove(entry) }
}

internal data class OverlayEntry(
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/** 覆盖层宿主：挂在 AppRoot 根 Box 的**最后**（压在底栏之上）。无内容时零开销。 */
@Composable
fun OverlayDialogLayer() {
    val entry = OverlayDialogs.current ?: return
    val progress = remember { Animatable(0f) }
    val spec = sheetSpatialSpec<Float>()
    LaunchedEffect(entry) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = spec)
    }
    BackHandler(enabled = true) { entry.onDismiss() }
    Box(Modifier.fillMaxSize()) {
        // 遮罩本体**透明**：压暗/模糊/缩小由宿主的 SheetBackdropLayer 负责（与底部弹层同一约定，
        // 见 RecordSheet 里那句"遮罩本体已透明"）——此处只保留「挡板」职责：消费点击、拦截穿透。
        // 自己再叠一层压暗会与背景层叠乘成双重压暗。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(entry) { detectTapGestures { entry.onDismiss() } },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    scaleX = 0.92f + 0.08f * p
                    scaleY = 0.92f + 0.08f * p
                    alpha = p
                },
            contentAlignment = Alignment.Center,
        ) { entry.content() }
    }
}

/** 覆盖层里的卡片（简化 M3 观感；内容来源与对话框完全一致，都是调用方传进来的那几个槽） */
@Composable
internal fun OverlayDialogCard(
    containerColor: Color,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        modifier = Modifier
            .padding(horizontal = Spacing.xl)
            .widthIn(min = DialogSpec.minWidth, max = DialogSpec.maxWidth),
    ) {
        Column {
            Column(Modifier.padding(Spacing.xl)) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.height(Spacing.m))
            }
            if (title != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() }
            }
            if (text != null) {
                if (title != null) Spacer(Modifier.height(Spacing.s))
                text()
            }
            if (engineIsMiuix) {
                // MIUIX：与窗口版 [MiuixDialogCard] 同观感——细分隔线 + 整宽分栏按钮行（左取消/右确认）
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                androidx.compose.runtime.CompositionLocalProvider(LocalDialogButtonFlat provides true) {
                    Row(Modifier.fillMaxWidth().height(DialogSpec.buttonRowHeight)) {
                        if (dismissButton != null) {
                            Row(
                                Modifier.weight(1f).fillMaxHeight(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalDialogButtonNeutral provides true,
                                    LocalDialogButtonFillCell provides true,
                                ) { dismissButton() }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalDialogButtonFillCell provides true,
                            ) { confirmButton() }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(Spacing.l))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.widthIn(min = Spacing.s))
                    }
                    confirmButton()
                }
            }
            }
        }
    }
}
