package com.mdot.app.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdot.app.domain.model.ThemeEngine
import com.mdot.app.core.designsystem.Radius
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** M3 CircularProgressIndicator 的默认描边宽（4dp）——本 App 侧默认值，写在一处以免散落魔法值 */
private val M3CircularStroke = 4.dp

/**
 * 环形进度（引擎分发）：MIUIX = 库 `basic.CircularProgressIndicator`；MD3 = M3 同名控件。
 *
 * ⚠️ 两处语义换算（库与 M3 1.5 的差异）：
 *  1. **进度形态**：M3 1.5 用 `progress: (() -> Float)?`（lambda，便于动画态），库用 `progress: Float?`
 *     → 此处取一次值传入；null = 不确定态（两者一致）。
 *  2. **颜色**：M3 是 `color`/`trackColor` 两个参数，库是 `ProgressIndicatorColors`
 *     （foregroundColor/backgroundColor）→ 未指定时分别落到各引擎的默认色（库的 primary /
 *     secondaryContainer），指定时透传。
 */
@Composable
fun JiabanCircularProgress(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    strokeWidth: Dp? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.CircularProgressIndicator(
            modifier = modifier,
            progress = progress?.invoke(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = if (color == Color.Unspecified) MiuixTheme.colorScheme.primary else color,
                backgroundColor = if (trackColor == Color.Unspecified) MiuixTheme.colorScheme.secondaryContainer else trackColor,
            ),
            strokeWidth = strokeWidth ?: ProgressIndicatorDefaults.DefaultCircularProgressIndicatorStrokeWidth,
        )
    } else if (progress == null) {
        // M3：不确定态是独立重载（progress 参数非空）——不带 trackColor
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth ?: M3CircularStroke,
        )
    } else {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            progress = progress,
            color = color,
            strokeWidth = strokeWidth ?: M3CircularStroke,
            trackColor = trackColor,
        )
    }
}

/** 线性进度（引擎分发）：MIUIX = 库 `basic.LinearProgressIndicator`；MD3 = M3 同名控件（同上换算） */
@Composable
fun JiabanLinearProgress(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
            modifier = modifier,
            progress = progress?.invoke(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = if (color == Color.Unspecified) MiuixTheme.colorScheme.primary else color,
                backgroundColor = if (trackColor == Color.Unspecified) MiuixTheme.colorScheme.secondaryContainer else trackColor,
            ),
        )
    } else if (progress == null) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            progress = progress,
            color = color,
            trackColor = trackColor,
        )
    }
}
