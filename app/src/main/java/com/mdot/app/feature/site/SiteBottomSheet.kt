package com.mdot.app.feature.site

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mdot.app.core.designsystem.component.LocalSheetBackdropState
import kotlinx.coroutines.delay

/**
 * 记工页通用底部弹层容器（自实现：遮罩 + 顶部圆角面板 + motionScheme 滑入滑出；全页弹窗统一走此容器）。
 * 跨文件共用（记工页各弹窗与项目管理页），保持 internal。
 */
@Composable
internal fun SiteBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    // 动画/结构完全对齐其它模式的「记加班」弹窗（feature/record/RecordSheet.kt）
    // 过渡状态：优先用宿主经 LocalSheetBackdropState 下发的共享状态（本弹层是 Dialog，
    // 独立窗口，拿不到宿主 remember 的状态）——背景层 SheetBackdropLayer 与面板因此同拍。
    // 无宿主提供时（单独调用）自建状态，并在弹层内自绘兑底压暗
    val hostState = LocalSheetBackdropState.current
    val fallbackState = remember { MutableTransitionState(false) }
    val visibleState = hostState ?: fallbackState
    // 置 true 放在一次性 effect 里：原写法在组合体内无条件置 true，退场途中任何一次重组都会把动画拉回
    LaunchedEffect(Unit) { visibleState.targetState = true }
    var dismissRequested by remember { mutableStateOf(false) }

    fun requestDismiss() { dismissRequested = true }

    // 兜底复位：任何路径摘除本弹层（含内容按钮直接调 onDismiss、点格/确定直连宿主的 onPick/onApply
    // 等）都会让共享过渡状态留在「展开」态——宿主的背景层（SheetBackdropLayer）因此一直停在
    // 模糊/缩小不消失（docs/11 039）。这里在组合销毁时一律拉回关闭态；requestDismiss 路径早已
    // 置 false，故幂等、不影响正常退出动画。
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

    // 遮罩与面板拆成两个 AnimatedVisibility（共享同一 visibleState）：遮罩原地淡入淡出、
    // 面板自下而上滑入/下滑淡出——避免遮罩跟随上推，且保证退出动画完整播放
    // （原外层 if 会在 dismiss 同帧移除组合，吞掉 exit 动画，导致弹层突兀消失）
    // v0.6.18：背景效果（压暗/模糊/缩小）改由宿主窗口的 SheetBackdropLayer 负责——
    // 本弹层是独立窗口，透明区域会露出宿主窗口，故遮罩盒子只留「挡板」职责（消费点击、
    // 拦截穿透）；仅当宿主未提供共享状态时自绘兑底压暗，避免弹层失去层级感
    val sheetSlideSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val sheetFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Dialog(
        onDismissRequest = { requestDismiss() },
        // decorFitsSystemWindows=false：IME insets 走 Compose 侧，imePadding 精准避让，
        // 否则弹窗窗口被系统 pan/双重补偿，输入法弹出后输入框与键盘间留大片空白
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 遮罩：原地淡入淡出
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(sheetFadeSpec),
            exit = fadeOut(sheetFadeSpec),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        // 兑底（无宿主背景层时）：自绘压暗。平台压暗已被 themes.xml 的
                        // FloatingDialogWindowTheme 覆盖关掉（见 themes.xml 注释），故只需一处压暗
                        if (hostState == null) Modifier.background(Color.Black.copy(alpha = SheetBackdrop.contentScrimNoBlur))
                        else Modifier
                    )
                    .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
            )
        }
        // 面板：自下而上滑入 / 下滑淡出
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(sheetSlideSpec) { it },
            exit = slideOutVertically(sheetSlideSpec) { it } + fadeOut(sheetFadeSpec),
        ) {
            // 面板（Box 提供 BottomCenter 对齐作用域）
            Box(Modifier.fillMaxSize()) {
            // 面板：宽屏四角全圆 + 底部留边（悬浮形态）；窄屏全宽贴底
            val wide = LocalWindowSpec.current == WindowSpec.EXPANDED
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = AdaptiveSpecs.sheetMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                            .padding(bottom = if (wide) Spacing.l else 0.dp)
                            .clip(
                                if (wide) RoundedCornerShape(Radius.sheet)
                                else RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(enabled = false) { }
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(Spacing.l),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.m))
                        // 内容按钮应通过 dismiss（= requestDismiss）关闭：走退出动画 + 复位共享状态；
                        // 勿在内容里直接调 onDismiss（会跳过动画并让背景层卡住，docs/11 039）
                        content { requestDismiss() }
                }
            }
        }
    }
}
