package com.mdot.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.engineShape
import java.util.Locale

/** 分钟数（0..1439）→ HH:mm */
fun formatMinuteOfDay(minute: Int): String {
    if (minute == 24 * 60) return "24:00"
    val m = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

/**
 * 时间字段：显示 HH:mm，点击弹出「时 / 分」两个滚轮。
 * 统一走页内覆盖层（硬规则 14），不用系统 TimePickerDialog。
 */
@Composable
fun TimeField(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Surface(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp),
            shape = engineShape(Radius.textField),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ms_more_time),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.height(IconSpec.inline).width(IconSpec.inline),
                )
                Text(
                    text = formatMinuteOfDay(minuteOfDay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    if (open) {
        TimePickDialog(
            initial = minuteOfDay,
            onConfirm = {
                onChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun TimePickDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableStateOf(((initial / 60) % 24 + 24) % 24) }
    var minute by remember { mutableStateOf(((initial % 60) + 60) % 60) }

    JiabanAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_pick_title)) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoopWheel(
                    value = hour,
                    itemCount = 24,
                    onValueChange = { hour = it },
                    modifier = Modifier.width(78.dp),
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LoopWheel(
                    value = minute,
                    itemCount = 60,
                    onValueChange = { minute = it },
                    modifier = Modifier.width(78.dp),
                )
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.ds_pick_confirm),
                onClick = { onConfirm(hour * 60 + minute) },
                role = JiabanButtonRole.PRIMARY,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.ds_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
