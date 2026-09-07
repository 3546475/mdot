package com.mdot.app.feature.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.contentBottomPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.mdot.app.core.sync.SyncPhase
import com.mdot.app.domain.util.TimeUtils
import java.time.Instant
import java.time.ZoneId

private fun selectedSourceName2(state: SyncUiState): String? {
    val selected = state.sources.selectedId ?: return null
    return state.sources.webdav.firstOrNull { it.id == selected }?.name
        ?: state.sources.s3.firstOrNull { it.id == selected }?.name
}

/** 同步备份页（M6 / 06 文档）：备份状态（入口）+ 备份/恢复 + 自动备份；存储源配置在独立页面 */
@Composable
fun SyncScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    vm: SyncViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 本地文件备份：导出选择位置 / 导入打开文件
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportLocalFile) }
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::prepareLocalRestore) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = !canBack)
            .padding(horizontal = Spacing.page),
    ) {
        if (canBack) {
            JiabanTopBar(title = stringResource(R.string.sync_screen_title), onBack = onBack)
        } else {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + Spacing.m))
        }

        if (!state.loaded) {
            CircularProgressIndicator(Modifier.padding(Spacing.xl))
            return@Column
        }

        // ---- 状态卡（点按进入存储源设置） ----
        SectionCard(onClick = onOpenStorage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sync_status_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.s))
                    if (state.status.configured) {
                        val srcName = selectedSourceName2(state)
                        if (srcName != null) {
                            Text(
                                stringResource(R.string.sync_status_source, srcName),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                        }
                    }
                    Text(
                        when {
                            !state.status.configured -> stringResource(R.string.sync_status_not_configured)
                            state.status.busy -> when (state.status.phase) {
                                SyncPhase.UPLOADING -> stringResource(R.string.sync_status_uploading)
                                SyncPhase.RESTORING -> stringResource(R.string.sync_status_restoring)
                                else -> stringResource(R.string.sync_status_processing)
                            }

                            state.status.conflict -> stringResource(R.string.sync_status_conflict)
                            state.status.lastBackupAt > 0 ->
                                stringResource(
                                    R.string.sync_status_last_backup_at,
                                    TimeUtils.dateLabel(
                                        Instant.ofEpochMilli(state.status.lastBackupAt)
                                            .atZone(ZoneId.systemDefault()).toLocalDate(),
                                        java.time.LocalDate.now(),
                                    ),
                                )

                            else -> stringResource(R.string.sync_status_never)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.status.conflict) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    state.status.lastError?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 备份 / 恢复操作 ----
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.sync_section_backup_restore), style = MaterialTheme.typography.titleSmall)
                if (!state.status.configured) {
                    Text(
                        stringResource(R.string.sync_backup_restore_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = vm::backupNow,
                    enabled = state.status.configured && !state.status.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_backup_now_cloud)) }

                OutlinedButton(
                    onClick = vm::prepareRestore,
                    enabled = state.status.configured && !state.status.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_restore_from_cloud)) }

                if (state.status.busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sync_auto_backup_title), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sync_auto_backup_desc), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.autoBackup, onCheckedChange = vm::onAutoBackup)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sync_history_copy_title), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sync_history_copy_desc), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.historyCopy, onCheckedChange = vm::onHistoryCopy)
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 本地文件备份 / 恢复（不依赖网盘） ----
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.sync_local_backup_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.sync_local_backup_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { createDoc.launch(SyncViewModel.defaultLocalFileName()) },
                    enabled = !state.localBusy && !state.status.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_local_export)) }
                OutlinedButton(
                    onClick = { openDoc.launch(arrayOf("application/zip", "*/*")) },
                    enabled = !state.localBusy && !state.status.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_local_restore)) }
                if (state.localBusy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                }
            }
        }

        state.message?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                vm.clearMessage()
            }
            Text(
                msg,
                color = if (state.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }

    // ---- 云端恢复确认卡 ----
    state.confirmRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.sync_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.sync_restore_confirm_text,
                        summary.createdAt,
                        summary.recordCount,
                        summary.deviceModel,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = vm::confirmRestore) { Text(stringResource(R.string.sync_action_restore)) }
            },
            dismissButton = {
                OutlinedButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.sync_action_cancel)) }
            },
        )
    }

    // ---- 本地文件恢复确认卡 ----
    state.pendingLocal?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelLocalRestore,
            title = { Text(stringResource(R.string.sync_local_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.sync_local_restore_confirm_text,
                        pending.recordCount,
                        pending.createdAt,
                        pending.appVersion,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::confirmLocalRestore,
                ) { Text(stringResource(R.string.sync_local_restore_confirm_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelLocalRestore) { Text(stringResource(R.string.sync_action_cancel)) }
            },
        )
    }

    // ---- 本地恢复完成：提示重启 ----
    if (state.restartRequired) {
        AlertDialog(
            onDismissRequest = vm::dismissRestart,
            title = { Text(stringResource(R.string.sync_restart_title)) },
            text = {
                Text(stringResource(R.string.sync_restart_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissRestart()
                    restartApp(context)
                }) { Text(stringResource(R.string.sync_restart_now)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissRestart) { Text(stringResource(R.string.sync_action_later)) }
            },
        )
    }
}

private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
