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
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.WindowSpec
import kotlinx.coroutines.delay

/**
 * 记工页通用底部弹层容器（自实现：遮罩 + 顶部圆角面板 + motionScheme 滑入滑出；全页弹窗统一走此容器）。
 * 跨文件共用（记工页各弹窗与项目管理页），保持 internal。
 */
@Composable
internal fun SiteBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 动画/结构完全对齐其它模式的「记加班」弹窗（feature/record/RecordSheet.kt）
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true
    var dismissRequested by remember { mutableStateOf(false) }

    fun requestDismiss() { dismissRequested = true }

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
                    .background(Color.Black.copy(alpha = 0.4f))
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
                        content()
                }
            }
        }
    }
}
