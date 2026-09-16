package com.mdot.app.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 保存/删除类重动作的触觉反馈共享扩展（docs/15 T1-1）。
 * 统一 LongPress 分级——TextHandleMove 留给输入/选择类（与日历 :901/:905 一致）。
 * 用法：val saveHaptic = rememberSaveWithHaptic(); onClick = { saveHaptic(); vm.save() }
 */
@Composable
fun rememberSaveWithHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
