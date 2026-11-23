package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.domain.model.ThemeEngine

/**
 * 浮动 label 输入框（全 App 输入框统一入口）：label 在空且未聚焦时缩在框内作占位，聚焦或有值后浮到顶边框
 * （M3 标准动效）；输入框高度固定，不随 label 状态变化。
 * 基于 OutlinedTextField 实现（MD3）/ 库 basic.TextField（MIUIX），圆角统一走 [Radius.textField]；
 * [suffix] 渲染为右侧尾随内容。
 *
 * 2026-09-30 拓宽 API：加入 placeholder / enabled / readOnly / isError / maxLines / leadingIcon / textStyle，
 * 目的是让其余 20 处“裸 OutlinedTextField”能逐步迁进来、统一两引擎观感。
 * ⚠️ 库端两处语义差异（已在下面映射）：
 *  1. **库没有独立 placeholder**——无标签时把 placeholder 当 label + `useLabelAsPlaceholder` 表达；
 *  2. **库没有 isError**——用 `textFieldColors(labelColor/borderColor = error)` 表达。
 */
@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    suffix: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    /** 辅助/报错文案（M3 supportingText）；MIUIX 无此参数属性 → 包一层 Column 画在输入框下方 */
    supportingText: (@Composable () -> Unit)? = null,
    /** 自定义外形；null = 本 App 统一圆角 [Radius.textField]。⚠️ MIUIX 只能取圆角值，非圆角形状会退化为该令牌 */
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val style = textStyle ?: MaterialTheme.typography.titleMedium
    // 引擎分发：MIUIX 用库 TextField（库自带 label 浮动/占位行为，与 M3 浮动标签等价）；
    // MD3 保持 M3 OutlinedTextField（原样，不动被点名的另一侧）
    if (com.mdot.app.core.designsystem.Radius.engine == com.mdot.app.domain.model.ThemeEngine.MIUIX) {
        val cs = MaterialTheme.colorScheme
        val field: @Composable () -> Unit = {
            top.yukonga.miuix.kmp.basic.TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = if (label.isNotBlank()) label else (placeholder ?: ""),
                // 空且未聚焦时把 label 当占位（≈ M3 浮动标签的空态），聚焦/有值后上浮
                useLabelAsPlaceholder = true,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = style,
                keyboardOptions = keyboardOptions,
                singleLine = maxLines == 1,
                maxLines = maxLines,
                leadingIcon = leadingIcon,
                trailingIcon = suffix,
                colors = if (isError) {
                    top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
                        backgroundColor = cs.surfaceContainerHigh,
                        labelColor = cs.error,
                        borderColor = cs.error,
                    )
                } else {
                    // 2026-09-30 用户口径「输入框颜色太多」：
                    // ① **底色改中性**——库默认 secondaryContainer 属**强调色角色**（由本 App 配色方案注入）
                    //   ⇒ 每个输入框都带一层强调色调，这才是「花」的主因（用户第二轮反馈）；
                    //   改用本 App 的中性凹陷灰 surfaceContainerHigh（浅#F0F0F0 / 深#2D2D2D）。
                    // ② 标签用中性次级文字色（onSurfaceVariant）。
                    // 聚焦边框/光标仍保留主题色（用户认可的那一抹聚焦反馈）。
                    top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
                        backgroundColor = cs.surfaceContainerHigh,
                        labelColor = cs.onSurfaceVariant,
                    )
                },
            )
        }
        // 库无 supportingText：自行包一层 Column 画在下方（保持与 MD3 同信息层级）
        if (supportingText != null) {
            Column(modifier = Modifier) { field(); supportingText() }
        } else {
            field()
        }
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // 标签锁单行：三栏窄容器下避免「平时」被后缀挤成两行撑高卡片
        label = if (label.isNotBlank()) {
            { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip) }
        } else {
            null
        },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier,
        shape = shape ?: engineShape(Radius.textField),
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
        singleLine = maxLines == 1,
        maxLines = maxLines,
        textStyle = style,
        leadingIcon = leadingIcon,
        trailingIcon = suffix,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
    )
}
