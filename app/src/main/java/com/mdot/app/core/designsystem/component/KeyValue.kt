package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 键值展示行（汇总卡等） */
@Composable
fun KeyValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    animatedCents: Long? = null,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (animatedCents != null) {
            AnimatedMoneyText(
                animatedCents,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                label = "keyValue",
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}
