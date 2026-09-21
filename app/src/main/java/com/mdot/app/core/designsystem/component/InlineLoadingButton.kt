package com.mdot.app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 内联加载按钮（docs/15 反馈「同款效果」）——按钮规范化第一批后降级为
 * [JiabanButton] 的**兼容薄壳**：行为（busy 圆环居中、原色不置灰、disabled 置灰）
 * 全部由 JiabanButton 的 loading 承接，本组件只做 filled→role 的映射。
 *
 * ⚠️ 新代码不再使用本组件：异步动作直接写
 * `JiabanButton(role = …, loading = …)`（docs/03 §按钮）。
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
    JiabanButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        role = if (filled) JiabanButtonRole.PRIMARY else JiabanButtonRole.SECONDARY,
        size = JiabanButtonSize.M,
        enabled = enabled,
        loading = busy,
    )
}
