package com.mdot.app.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
            content = content,
        )
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
