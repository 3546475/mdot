package com.mdot.app.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.domain.model.ThemeEngine

/**
 * 悬浮操作钮（引擎分发）：MIUIX = 库 `basic.FloatingActionButton`（60dp 圆钮 + 库的主色/投影）；
 * MD3 = M3 `FloatingActionButton`。
 *
 * ⚠️ 默认色取 **M3 FAB 的默认**（primaryContainer / onPrimaryContainer），故 MD3 侧与迁移前逐像素一致；
 * MIUIX 侧把同一 color 交给库（库默认是 primary，此处显式传本 App 的容器色以免引擎间跳色）。
 *
 * ⚠️ `contentColor` 必须由本组件**显式下发**（MIUIX 分支尤其）：库 FAB 既无 `contentColor` 形参、
 * 内部也**不下发** `LocalContentColor`（字节码里连 `CompositionLocalProvider` 调用都没有，见 docs/11 097）
 * ⇒ 内容色退化成页面环境的 `onBackground`（浅色主题近黑）。填充换深色（如日历页用 `primary`）时
 * 图标就成了「深底近黑图标」，用户报「FAB 图标恒为黑色」。**改填充色时必须同时传 contentColor**。
 */
@Composable
fun JiabanFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    /** null = 各引擎用各自默认外形（MIUIX = 库 CircleShape；MD3 = M3 FloatingActionButtonDefaults.shape） */
    shape: Shape? = null,
    content: @Composable () -> Unit,
) {
    if (Radius.engine == ThemeEngine.MIUIX) {
        top.yukonga.miuix.kmp.basic.FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape ?: CircleShape,
            containerColor = containerColor,
        ) {
            // 库不认内容色（见 KDoc）⇒ 这里补上，与 MD3 侧 M3 FAB 的 Surface 行为对齐
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        androidx.compose.material3.FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape ?: androidx.compose.material3.FloatingActionButtonDefaults.shape,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content,
        )
    }
}
