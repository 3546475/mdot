package com.mdot.app.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 内联加载按钮（docs/15 反馈「同款效果」：与设置页 hero「检查更新」按钮一致）。
 *
 * busy 时按钮内只显示**居中圆环、无文字**——不再在按钮下方另起一行独立转圈；
 * busy 保持按钮原色不置灰（实心=primary 底白环，描边=透明底 contentColor 环）。
 * 圆环淡入淡出走 motionScheme.fastEffectsSpec。
 *
 * 与设置页检查更新按钮的区别：那是紧凑自适应宽、带中心锚定收缩动画；
 * 本组件用于 fillMaxWidth 整行按钮，宽度固定，只复用「busy 圆环居中」视觉。
 *
 * @param busy    是否进行中（只显示圆环）
 * @param text    非 busy 时按钮文字
 * @param onClick 点击
 * @param enabled 调用方算好的可用态（busy 时通常已含 !busy）；busy 置 false 仍保持原色
 * @param filled  true=实心 primary 按钮；false=描边按钮
 */
@Composable
fun InlineLoadingButton(
    busy: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val spinnerColor = if (filled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    // 仅 busy 时覆盖 disabled 色为原色（按钮仍拦截点击但不置灰，圆环可读）；
    // 非 busy 的 disabled（未配置/无区间等）用默认置灰，保留「不可用」视觉提示。
    val colors = if (busy) {
        if (filled) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ) else ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        if (filled) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors()
    }
    val content: @Composable RowScope.() -> Unit = {
        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = spinnerColor,
            )
        }
        if (!busy) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            modifier = modifier,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            modifier = modifier,
            content = content,
        )
    }
}
