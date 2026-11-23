package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.engineShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.miuix.MiuixSectionCard
import com.mdot.app.domain.model.ThemeEngine

/** 统一卡片容器：16dp 圆角、surfaceContainer 色阶（03 文档 §3.3）；可点卡片带按压缩放；containerColor 可覆盖底色（如工钱 hero 卡） */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    // MIUIX 引擎：容器改 miuix Card（库的圆角/底色/层级语言）；显式覆盖底色时才把颜色带过去
    if (Radius.engine == ThemeEngine.MIUIX) {
        val override = if (containerColor == MaterialTheme.colorScheme.surfaceContainer) null else containerColor
        MiuixSectionCard(modifier, onClick, override, content)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier),
        shape = engineShape(Radius.card),
        color = containerColor,
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interaction,
    ) {
        Box(Modifier.padding(Spacing.l)) { content() }
    }
}
