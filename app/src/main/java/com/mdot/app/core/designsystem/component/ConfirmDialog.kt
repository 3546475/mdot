package com.mdot.app.core.designsystem.component

import com.mdot.app.core.designsystem.dialogContainerColor
import androidx.compose.ui.res.stringResource
import com.mdot.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/** 二次确认对话框（危险操作主按钮 error 色） */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = stringResource(R.string.ds_confirm_delete),
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            JiabanButton(
                text = confirmText,
                onClick = onConfirm,
                role = JiabanButtonRole.GHOST,
                contentColorOverride = if (danger) MaterialTheme.colorScheme.error else null,
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
