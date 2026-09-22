package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing

/**
 * 内联操作结果提示的统一 Snackbar 化（docs/15 T4-3，缺口 #18）。
 *
 * 把「页面内 Text 型操作结果提示」迁移为悬浮底部 Snackbar，消除生灭挤版问题；
 * 渲染层配套 [MessageSnackbarHost]（悬浮不占布局流，BottomCenter 对齐，见日历/记月页范式）。
 *
 * 视觉（docs/15 用户反馈：「黑黢黢一条」改版）：浅色悬浮胶囊——
 * `surfaceContainerHighest` 底跟随主题/多配色/动态取色，主色或 error 色细描边 + 胶囊圆角，
 * 前置语义图标（✓ 成功/普通、⚠ 错误）区分；比 M3 默认 inverseSurface 深色条柔和、融入页面。
 *
 * 用法（页面根必须是 Box 才能 align）：
 * ```
 * val (snackbarHostState, isError) = rememberMessageSnackbar(
 *     message = state.message,
 *     onClear = vm::clearMessage,
 *     isError = state.isError,
 * )
 * MessageSnackbarHost(snackbarHostState, isError, Modifier.align(Alignment.BottomCenter))
 * ```
 *
 * @param message 待提示文本；null 时不显示。变化一次弹一条，显示后回调 [onClear]。
 * @param isError 是否错误语义；决定描边/图标配色（error）与图标（⚠），普通/成功用主色 ✓。
 * @param actionLabel 可选动作文案（如「查看记录」，docs/15 T6 #22）；点了动作回调 [onAction] 且不调 [onClear]。
 */
@Composable
fun rememberMessageSnackbar(
    message: String?,
    onClear: () -> Unit,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): Pair<SnackbarHostState, Boolean> {
    val hostState = remember { SnackbarHostState() }
    var errorFlag by remember { mutableStateOf(isError) }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        errorFlag = isError
        val result = hostState.showSnackbar(
            message = text,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onAction?.invoke()
            else -> onClear()
        }
    }
    return hostState to errorFlag
}

/** 与 [rememberMessageSnackbar] 配套的宿主；浅色悬浮胶囊（见文件头 KDoc）。 */
@Composable
fun MessageSnackbarHost(
    hostState: SnackbarHostState,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState,
        // 上移避让（v0.6.19）：原先 0 底距＝贴着屏幕/内容下缘，看上去贴边且可能被系统导航条压住；
        // 统一在此补导航条 inset + 一段间距，各调用点无需各自处理
        modifier.navigationBarsPadding().padding(bottom = Spacing.l),
    ) { data ->
        MessageSnackbarCapsule(data, isError)
    }
}

/** 浅色悬浮胶囊本体：图标 + 消息（+ 可选动作），圆角胶囊 + 语义描边。 */
@Composable
private fun MessageSnackbarCapsule(data: SnackbarData, isError: Boolean) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = Spacing.m)
            .widthIn(max = 560.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Icon(
                painterResource(if (isError) R.drawable.ic_ms_warning else R.drawable.ic_ms_check_circle),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(IconSpec.dense),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                Spacer(Modifier.width(Spacing.m))
                TextButton(onClick = data::performAction) {
                    Text(actionLabel, color = accent)
                }
            }
        }
    }
}

/** [MessageSnackbarHost] 的 [BoxScope] 快捷入口：底中对齐，一行挂载。 */
@Composable
fun BoxScope.MessageSnackbarHost(
    hostState: SnackbarHostState,
    isError: Boolean,
) {
    MessageSnackbarHost(hostState, isError, Modifier.align(Alignment.BottomCenter))
}
