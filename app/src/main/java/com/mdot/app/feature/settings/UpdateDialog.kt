package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R

/**
 * 更新流程弹窗：检查中（转圈）→ 发现新版本（说明 + 立即更新）→ 下载中（进度条）。
 * 行内短提示（已是最新/失败/需授权）由各页自行渲染 [UpdateViewModel.notice]。
 */
@Composable
fun UpdateFlow(updateVm: UpdateViewModel) {
    val state by updateVm.state.collectAsStateWithLifecycle()
    when (state) {
        UpdateState.Checking,
        UpdateState.Idle -> if (state is UpdateState.Checking) CheckingDialog()

        is UpdateState.Available -> {
            val info = (state as UpdateState.Available).info
            AlertDialog(
                onDismissRequest = updateVm::dismiss,
                title = { Text(stringResource(R.string.update_available_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.update_available_version, info.versionName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (info.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                info.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = updateVm::downloadAndInstall) {
                        Text(stringResource(R.string.update_action_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = updateVm::dismiss) {
                        Text(stringResource(R.string.ds_cancel))
                    }
                },
            )
        }

        is UpdateState.Downloading -> {
            val p = (state as UpdateState.Downloading).progress
            AlertDialog(
                onDismissRequest = { /* 下载中不可取消 */ },
                title = { Text(stringResource(R.string.update_action_download)) },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.update_downloading, p),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {},
            )
        }
    }
}

@Composable
private fun CheckingDialog() {
    AlertDialog(
        onDismissRequest = { /* 检查中不可取消 */ },
        title = { Text(stringResource(R.string.update_checking)) },
        text = {
            Column {
                CircularProgressIndicator(Modifier.padding(4.dp))
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {},
    )
}
