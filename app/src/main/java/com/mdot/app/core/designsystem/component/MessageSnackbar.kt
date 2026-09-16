package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 内联操作结果提示的统一 Snackbar 化（docs/15 T4-3，缺口 #18）。
 *
 * 把「页面内 Text 型操作结果提示」迁移为悬浮底部 Snackbar，消除生灭挤版问题；
 * 渲染层配套 [MessageSnackbarHost]（悬浮不占布局流，BottomCenter 对齐，见日历/记月页范式）。
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
 * @param isError 是否错误语义；决定 [MessageSnackbarHost] 的配色（errorContainer）。
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

/** 与 [rememberMessageSnackbar] 配套的宿主；错误语义用 errorContainer 配色区分。 */
@Composable
fun MessageSnackbarHost(
    hostState: SnackbarHostState,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.inverseOnSurface,
        )
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
