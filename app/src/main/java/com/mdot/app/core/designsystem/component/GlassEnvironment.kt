package com.mdot.app.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import com.mdot.app.domain.model.SheetBackdropMode

/**
 * 玻璃簇 / 弹层背景的运行时环境（MainActivity 在主题外层下发，出厂值 = 模糊档）。
 *
 * 玻璃相关的运行时配置全部收口在这里，别再新开 CompositionLocal —— 此前「弹层背景档位」
 * 「玻璃共享源」「弹层过渡状态」是三个各自独立的 local，其中一个已无读取点、另一个只服务
 * 已删除的系统对话框窗口回退路径。
 */
data class GlassEnvironment(
    /** 弹层背景效果档位（外观页可选）：[SheetBackdropLayer] 据此决定缩放/模糊/压暗组合 */
    val sheetMode: SheetBackdropMode = SheetBackdropMode.BLUR,
)

val LocalGlassEnvironment = compositionLocalOf { GlassEnvironment() }
