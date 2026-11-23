package com.mdot.app.feature.site

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.component.LocalOverlayDialogs
import com.mdot.app.core.designsystem.component.OverlayEntry
import com.mdot.app.core.designsystem.component.OverlayKind

/**
 * 记工页通用底部弹层容器（全页弹窗统一走此容器）。跨文件共用（记工页各弹窗与项目管理页），保持 `internal`。
 *
 * **走「页内模态覆盖层」**（用户拍板：工地弹层别开窗口）——与 [com.mdot.app.core.designsystem.component.JiabanAlertDialog]
 * 同一套栈（`OverlayDialogController`，硬规则 14）：
 * 1. 不开独立窗口 ⇒ 不受 ROM 对话框窗口动画影响；
 * 2. 弹出时页面的压暗/模糊/缩小由宿主 `SheetBackdropLayer` 负责（AppRoot 以"栈非空"判定），
 *    与「记加班」弹层完全同拍；
 * 3. 弹层里再开弹窗（日期/备注等）叠在同一栈上。
 */
@Composable
internal fun SiteBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val overlayCtl = requireNotNull(LocalOverlayDialogs.current) {
        "SiteBottomSheet 需要 LocalOverlayDialogs 宿主（MainActivity 的 setContent 提供）"
    }
    SiteSheetOverlay(title = title, onDismiss = onDismiss, content = content, controller = overlayCtl)
}

/**
 * 页内模态覆盖层形态。
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
 * 弹层面板外观：顶部圆角 + surfaceContainer 底 + 标题 + 内容，
 * 宽屏四角全圆并底部留边（悬浮形态），窄屏全宽贴底。
 *
 * [onDismissRequest] 既从系统返回键/点遮罩来（覆盖层宿主），也从内容里的「取消/确定」来 ——
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
