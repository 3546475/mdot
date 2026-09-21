package com.mdot.app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 「保存 / 提交」类按钮的收缩反馈（v0.6.19 起的语义名）——按钮规范化后降级为
 * [JiabanButton] 的**语义薄壳**：`busy = true` 收缩到 ✓、回 false 展开回文案，
 * 动画与视觉全部由 JiabanButton 的 loading 承接（docs/03 §13.3）。
 *
 * 保留本名是为了调用点的**语义**（保存/提交类 vs 普通异步）；历史参数
 * （pillHeight/minWidth/contentPadding/iconSize）恰好都是 JiabanButton L 档默认值，
 * 已随规范化收敛删除。
 *
 * 用于：记工页「保存」、调休余额「增加调休 / 扣减」、工资页「保存」。
 * ⚠️ 新代码可直接写 `JiabanButton(role = PRIMARY, size = L, loading = …)`——
 * 二者现在完全同物；用哪个名只看调用点想表达什么。
 */
@Composable
fun ShrinkFeedbackButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    JiabanButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        role = JiabanButtonRole.PRIMARY,
        size = JiabanButtonSize.L,
        enabled = enabled,
        loading = busy,
    )
}
