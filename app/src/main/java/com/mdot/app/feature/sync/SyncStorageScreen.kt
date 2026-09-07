package com.mdot.app.feature.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.ConfirmDialog
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.sync.ProviderKind
import com.mdot.app.core.sync.S3Creds
import com.mdot.app.core.sync.S3Source
import com.mdot.app.core.sync.WebDavCreds
import com.mdot.app.core.sync.WebDavSource

/** 存储源页：类型切换 + 多存储源列表（新增/切换/删除/断开），从同步备份页顶部卡进入 */
@Composable
fun SyncStorageScreen(
    onBack: () -> Unit,
    vm: SyncViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.sync_storage_title), showBack = true, onBack = onBack)

        if (!state.loaded) {
            CircularProgressIndicator(Modifier.padding(Spacing.xl))
            return@Column
        }

        // 类型切换 + 右上角新增。S3 入口暂藏（未充分测试）：类型区仅 WebDAV；
        // 历史 S3 配置兜底可见（kind 初始化=选中源类型），可编辑/删除/断开但不可新增（+ 仅 WebDAV 下可用）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(
                selected = state.kind == ProviderKind.WEBDAV,
                onClick = { vm.onKind(ProviderKind.WEBDAV) },
                label = { Text("WebDAV") },
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = vm::openAddDialog,
                enabled = state.kind == ProviderKind.WEBDAV && !state.adding,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sync_storage_add))
            }
        }

        Spacer(Modifier.height(Spacing.m))

        when (state.kind) {
            ProviderKind.WEBDAV -> {
                if (state.sources.webdav.isEmpty()) {
                    EmptySourcesText(stringResource(R.string.sync_storage_empty_webdav))
                } else {
                    state.sources.webdav.forEach { source ->
                        SourceCard(
                            name = source.name,
                            lines = listOf(source.creds.baseUrl, source.creds.username),
                            selected = source.id == state.sources.selectedId,
                            testing = state.testingId == source.id,
                            onClick = { vm.onSelectSource(source.id) },
                            onEdit = { vm.openEditDialog(source.id) },
                            onDelete = { vm.requestDelete(source.id) },
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                }
            }

            ProviderKind.S3 -> {
                if (state.sources.s3.isEmpty()) {
                    EmptySourcesText(stringResource(R.string.sync_storage_empty_s3))
                } else {
                    state.sources.s3.forEach { source ->
                        SourceCard(
                            name = source.name,
                            lines = listOf(source.creds.endpoint, source.creds.bucket),
                            selected = source.id == state.sources.selectedId,
                            testing = state.testingId == source.id,
                            onClick = { vm.onSelectSource(source.id) },
                            onEdit = { vm.openEditDialog(source.id) },
                            onDelete = { vm.requestDelete(source.id) },
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                }
            }

            ProviderKind.NONE -> Unit
        }

        // 当前使用中的存储源
        val selectedName = selectedSourceName(state)
        if (selectedName != null) {
            Text(
                stringResource(R.string.sync_storage_in_use, selectedName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.m),
            )
            OutlinedButton(
                onClick = vm::requestDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.m),
            ) { Text(stringResource(R.string.sync_storage_disconnect)) }
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

        Text(
            stringResource(R.string.sync_storage_tip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
        Spacer(Modifier.height(Spacing.xl))
    }

    // 新增存储源弹窗
    state.dialog?.let { dialog ->
        AddSourceDialog(
            kind = dialog.kind,
            name = dialog.name,
            editing = dialog.editingId != null,
            error = dialog.error,
            onNameChange = vm::onDialogName,
            webdavForm = state.webdavForm,
            onWebdavChange = vm::onWebdavForm,
            s3Form = state.s3Form,
            onS3Change = vm::onS3Form,
            saving = state.adding,
            onConfirm = vm::confirmAdd,
            onDismiss = vm::closeAddDialog,
        )
    }

    // 删除确认
    val pendingDelete = state.pendingDeleteId?.let { id ->
        state.sources.webdav.firstOrNull { it.id == id }?.name
            ?: state.sources.s3.firstOrNull { it.id == id }?.name
    }
    if (pendingDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.sync_storage_delete_confirm_title),
            text = stringResource(R.string.sync_storage_delete_confirm_text, pendingDelete),
            onConfirm = vm::confirmDelete,
            onDismiss = vm::cancelDelete,
        )
    }

    // 断开确认
    if (state.disconnectConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.sync_storage_disconnect_confirm_title),
            text = stringResource(R.string.sync_storage_disconnect_confirm_text),
            confirmText = stringResource(R.string.sync_storage_disconnect_confirm_btn),
            danger = false,
            onConfirm = vm::confirmDisconnect,
            onDismiss = vm::cancelDisconnect,
        )
    }
}

private fun selectedSourceName(state: SyncUiState): String? {
    val selected = state.sources.selectedId ?: return null
    return state.sources.webdav.firstOrNull { it.id == selected }?.name
        ?: state.sources.s3.firstOrNull { it.id == selected }?.name
}

@Composable
private fun EmptySourcesText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.m),
    )
}

/** 存储源卡片：名称 + 副行 + 编辑/删除 + 选中圆点；点击卡片 = 测试并切换 */
@Composable
private fun SourceCard(
    name: String,
    lines: List<String>,
    selected: Boolean,
    testing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.sync_storage_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.sync_storage_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                if (selected) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** 新增存储源弹窗（类型跟随当前浏览类型；保存后自动测试并切换） */
@Composable
private fun AddSourceDialog(
    kind: ProviderKind,
    name: String,
    editing: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    webdavForm: WebDavCreds,
    onWebdavChange: (WebDavCreds) -> Unit,
    s3Form: S3Creds,
    onS3Change: (S3Creds) -> Unit,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    editing && kind == ProviderKind.WEBDAV -> stringResource(R.string.sync_storage_edit_title_webdav)
                    editing && kind == ProviderKind.S3 -> stringResource(R.string.sync_storage_edit_title_s3)
                    kind == ProviderKind.WEBDAV -> stringResource(R.string.sync_storage_add_title_webdav)
                    kind == ProviderKind.S3 -> stringResource(R.string.sync_storage_add_title_s3)
                    else -> ""
                }
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.sync_storage_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (kind) {
                    ProviderKind.WEBDAV -> {
                        OutlinedTextField(
                            value = webdavForm.baseUrl,
                            onValueChange = { onWebdavChange(webdavForm.copy(baseUrl = it)) },
                            label = { Text(stringResource(R.string.sync_storage_webdav_url_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = webdavForm.username,
                            onValueChange = { onWebdavChange(webdavForm.copy(username = it)) },
                            label = { Text(stringResource(R.string.sync_storage_username_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = webdavForm.password,
                            onValueChange = { onWebdavChange(webdavForm.copy(password = it)) },
                            label = { Text(stringResource(R.string.sync_storage_password_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TrustSelfSignedRow(
                            checked = webdavForm.trustSelfSigned,
                            onChange = { onWebdavChange(webdavForm.copy(trustSelfSigned = it)) },
                        )
                    }

                    ProviderKind.S3 -> {
                        OutlinedTextField(
                            value = s3Form.endpoint,
                            onValueChange = { onS3Change(s3Form.copy(endpoint = it)) },
                            label = { Text(stringResource(R.string.sync_storage_endpoint_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s3Form.bucket,
                            onValueChange = { onS3Change(s3Form.copy(bucket = it)) },
                            label = { Text(stringResource(R.string.sync_storage_bucket_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s3Form.region,
                            onValueChange = { onS3Change(s3Form.copy(region = it)) },
                            label = { Text(stringResource(R.string.sync_storage_region_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s3Form.accessKeyId,
                            onValueChange = { onS3Change(s3Form.copy(accessKeyId = it)) },
                            label = { Text(stringResource(R.string.sync_storage_access_key_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s3Form.secretAccessKey,
                            onValueChange = { onS3Change(s3Form.copy(secretAccessKey = it)) },
                            label = { Text(stringResource(R.string.sync_storage_secret_key_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = s3Form.pathPrefix,
                            onValueChange = { onS3Change(s3Form.copy(pathPrefix = it)) },
                            label = { Text(stringResource(R.string.sync_storage_path_prefix_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TrustSelfSignedRow(
                            checked = s3Form.trustSelfSigned,
                            onChange = { onS3Change(s3Form.copy(trustSelfSigned = it)) },
                        )
                    }

                    ProviderKind.NONE -> Unit
                }
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(Spacing.s))
                }
                Text(stringResource(R.string.sync_storage_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.sync_storage_cancel))
            }
        },
    )
}

/** HTTPS 自签证书信任开关（NAS/MinIO 等自建服务；默认关闭走系统校验） */
@Composable
private fun TrustSelfSignedRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sync_storage_trust_title), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.sync_storage_trust_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
