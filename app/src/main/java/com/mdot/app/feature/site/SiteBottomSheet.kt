package com.mdot.app.feature.site

import com.mdot.app.core.designsystem.engineShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SheetBackdrop
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.component.LocalOverlayDialogs
import com.mdot.app.core.designsystem.component.LocalSheetBackdropState
import com.mdot.app.core.designsystem.component.OverlayEntry
import com.mdot.app.core.designsystem.component.OverlayKind
import kotlinx.coroutines.delay

/**
 * 记工页通用底部弹层容器（全页弹窗统一走此容器）。跨文件共用（记工页各弹窗与项目管理页），保持 `internal`。
 *
 * **v0.7.9.0 起默认走「页内模态覆盖层」**（用户拍板：工地弹层别再开窗口）——
 * 与 `JiabanAlertDialog(asOverlay = true)` 同一套栈（`OverlayDialogController`，硬规则 14）：
 * 1. 不开独立窗口 ⇒ 不再受 ROM 对话框窗口动画影响（`DialogBackdrop.kt` 里那堆逐帧门控正是为此）；
 * 2. 弹出时页面的压暗/模糊/缩小由宿主 `SheetBackdropLayer` 负责（AppRoot 以"栈非空"判定），
 *    与「记加班」弹层完全同拍；
 * 3. 弹层里再开弹窗（日期/备注等）会**叠在同一栈上**，不再需要 `asOverlay = false` 那种特殊处理。
 *
 * 兜底：`LocalOverlayDialogs == null`（预览 / 单测 / 不在 App 组合内）时退回原来的 Dialog 实现，
 * 功能与动画都不丢。
 */
@Composable
internal fun SiteBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val overlayCtl = LocalOverlayDialogs.current
    if (overlayCtl != null) {
        SiteSheetOverlay(title = title, onDismiss = onDismiss, content = content, controller = overlayCtl)
        return
    }
    SiteSheetWindow(title = title, onDismiss = onDismiss, content = content)
}

/**
 * 页内模态覆盖层形态（默认路径）。
 *
 * entry 只 push 一次（身份固定才能精确出栈），因此**标题/内容/关闭回调都用 `rememberUpdatedState`
 * 取最新值**——把首帧 lambda 钉进 entry 会让后续状态变化（如"选了几工天"）在弹层里看不到。
 * 关闭走 [dismissNow]：先从栈里移除（覆盖层会保留最后一条渲染到退场动画结束），再通知调用方。
 */
@Composable
private fun SiteSheetOverlay(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    controller: com.mdot.app.core.designsystem.component.OverlayDialogController,
) {
    val latestTitle by rememberUpdatedState(title)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestContent = rememberUpdatedState(content)

    // 内容里的 dismiss 指向这个可变 lambda：entry 先建、再赋值，避免自引用
    val overlayDismiss = remember { mutableStateOf<() -> Unit>({ latestOnDismiss() }) }
    val entry = remember(controller) {
        OverlayEntry(
            kind = OverlayKind.SHEET,
            onDismiss = { latestOnDismiss() },
            content = {
                SiteSheetPanel(
                    title = latestTitle,
                    onDismissRequest = { overlayDismiss.value() },
                ) { dismiss -> latestContent.value.invoke(this, dismiss) }
            },
        )
    }
    overlayDismiss.value = { controller.remove(entry); latestOnDismiss() }

    DisposableEffect(entry) {
        controller.push(entry)
        onDispose { controller.remove(entry) }
    }
}

/**
 * 弹层面板外观（页内覆盖层与 Dialog 兜底共用）：顶部圆角 + surfaceContainer 底 + 标题 + 内容，
 * 宽屏四角全圆并底部留边（悬浮形态），窄屏全宽贴底。
 *
 * [onDismissRequest] 既从系统返回键/点遮罩来（兜底路径），也从内容里的「取消/确定」来 ——
 * 走这里能保证**先播退场动画再真正关闭**（直接摘组合会吞掉 exit，docs/11 039）。
 */
@Composable
private fun SiteSheetPanel(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val wide = LocalWindowSpec.current == WindowSpec.EXPANDED
    Column(
        Modifier
            .widthIn(max = AdaptiveSpecs.sheetMaxWidth)
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp)
            .padding(bottom = if (wide) Spacing.l else 0.dp)
            .clip(
                if (wide) engineShape(Radius.sheet)
                else engineShape(topStart = Radius.sheet, topEnd = Radius.sheet, bottomEnd = 0.dp, bottomStart = 0.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = false) { }
            .imePadding()
            .padding(Spacing.l),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.m))
        content { onDismissRequest() }
    }
}

/**
 * Dialog 兜底实现（无覆盖层宿主时）：原样保留 v0.6.18 那套「遮罩 + 面板两个 AnimatedVisibility +
 * 共享过渡状态」——开工单窗口时背景压暗/模糊由宿主 `SheetBackdropLayer` 呈现。
 */
@Composable
private fun SiteSheetWindow(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val hostState = LocalSheetBackdropState.current
    val fallbackState = remember { MutableTransitionState(false) }
    val visibleState = hostState ?: fallbackState
    LaunchedEffect(Unit) { visibleState.targetState = true }
    var dismissRequested by remember { mutableStateOf(false) }

    fun requestDismiss() { dismissRequested = true }

    DisposableEffect(visibleState) {
        onDispose { visibleState.targetState = false }
    }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visibleState.targetState = false
            while (!visibleState.isIdle) delay(16)
            onDismiss()
        }
    }
    BackHandler(onBack = { requestDismiss() })

    val sheetSlideSpec = com.mdot.app.core.designsystem.sheetSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val sheetFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(sheetFadeSpec),
            exit = fadeOut(sheetFadeSpec),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (hostState == null) Modifier.background(Color.Black.copy(alpha = SheetBackdrop.contentScrimNoBlur))
                        else Modifier
                    )
                    .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
            )
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(sheetSlideSpec) { it },
            exit = slideOutVertically(sheetSlideSpec) { it } + fadeOut(sheetFadeSpec),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                    SiteSheetPanel(
                        title = title,
                        onDismissRequest = { requestDismiss() },
                        content = content,
                    )
                }
            }
        }
    }
}
