package com.mdot.app.feature.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
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
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.IconGhostButton
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanButtonSize
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.sync.ProviderKind
import com.mdot.app.core.sync.S3Creds
import com.mdot.app.core.sync.S3Source
import com.mdot.app.core.sync.WebDavCreds
import com.mdot.app.core.sync.WebDavSource
import kotlinx.coroutines.launch

/** 存储源页签内容（同步备份合并页第 2 页签）：类型子页签（滑块式、内容横滑切换，同记工页点工/包工）+ 多存储源列表（新增/切换/删除/断开） */
@Composable
fun SyncStoragePane(
    state: SyncUiState,
    vm: SyncViewModel,
) {
    val scope = rememberCoroutineScope()
    // 类型子页签 = 横滑 Pager（与记工页点工/包工一致）：滑块连续跟随手势，
    // 内容区横滑切类型；WebDAV 尽头继续滑经嵌套滚动自然联动上级（↔ 备份页签）
    val kindPager = rememberPagerState(
        initialPage = if (state.kind == ProviderKind.S3) 1 else 0,
        pageCount = { 2 },
    )
    // VM kind（初始载入等外部变化）→ pager 归位；以 targetPage 判重，避免打断进行中的拖拽
    LaunchedEffect(state.kind) {
        val target = if (state.kind == ProviderKind.S3) 1 else 0
        if (kindPager.targetPage != target) kindPager.scrollToPage(target)
    }
    // pager 停靠页 → VM kind（onKind 仅更新本地状态，无副作用；新增弹窗类型跟随此值）
    LaunchedEffect(kindPager) {
        snapshotFlow { kindPager.currentPage }.collect { page ->
            vm.onKind(if (page == 1) ProviderKind.S3 else ProviderKind.WEBDAV)
        }
    }

    // 外层 SyncTabPage 已提供垂直滚动，此处不可再套滚动（滚动嵌套会让内层收到无限高约束而崩溃）
    Column(Modifier.fillMaxWidth()) {
        // 类型子页签（滑块式，对齐统计/记月子页签）+ 右上角新增。v0.6.3 起 S3 放开；
        // 新增/编辑/删除/断开与 WebDAV 同一套交互，弹窗表单按类型切换
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentBar(
                labels = listOf("WebDAV", "S3"),
                selected = kindPager.currentPage,
                onSelect = { index ->
                    vm.onKind(if (index == 1) ProviderKind.S3 else ProviderKind.WEBDAV)
                    scope.launch { kindPager.animateScrollToPage(index) }
                },
                segWidth = 112.dp,
                position = kindPager.currentPage + kindPager.currentPageOffsetFraction,
            )
            Spacer(Modifier.weight(1f))
            IconGhostButton(
                painter = painterResource(R.drawable.ic_ms_add),
                contentDescription = stringResource(R.string.sync_storage_add),
                onClick = vm::openAddDialog,
                enabled = state.kind != ProviderKind.NONE && !state.adding,
            )
        }

        Spacer(Modifier.height(Spacing.m))

        // 内容横滑切类型；页内禁套垂直滚动（垂直滚动归外层 SyncTabPage，高度随内容自适应）。
        // 使用中/断开/提示都在**页内**：两页数量不同时 Pager 高度变化只影响自身，
        // 不会让页外元素上下跳动（用户反馈：切换时各元素跳来跳去）
        // 页面顶部对齐（Pager 默认垂直居中）：两页高度不同时，切换过渡中内容不居中漂浮、
        // 不在上方留出空白（用户反馈：切到 WebDAV 源卡片上方有空白然后消失）
        HorizontalPager(
            state = kindPager,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val kind = if (page == 1) ProviderKind.S3 else ProviderKind.WEBDAV
            Column {
                when (kind) {
                    ProviderKind.WEBDAV -> {
                        if (state.sources.webdav.isEmpty()) {
                            EmptySourcesState(title = stringResource(R.string.sync_storage_empty_webdav))
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
                            EmptySourcesState(title = stringResource(R.string.sync_storage_empty_s3))
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

                // 断开当前存储源：页面底部（选中状态已由源卡的圆点表达，不再另加「当前使用」文字）
                val inUseName = selectedSourceIn(state, kind)

                Text(
                    stringResource(R.string.sync_storage_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                if (inUseName != null) {
                    JiabanButton(
                        text = stringResource(R.string.sync_storage_disconnect),
                        onClick = vm::requestDisconnect,
                        role = JiabanButtonRole.SECONDARY,
                        size = JiabanButtonSize.L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.m),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
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

/** 指定类型下、当前选中（使用中）的存储源名（null = 未选中或选中源不属于该类型） */
private fun selectedSourceIn(state: SyncUiState, kind: ProviderKind): String? {
    val selected = state.sources.selectedId ?: return null
    return when (kind) {
        ProviderKind.WEBDAV -> state.sources.webdav.firstOrNull { it.id == selected }?.name
        ProviderKind.S3 -> state.sources.s3.firstOrNull { it.id == selected }?.name
        ProviderKind.NONE -> null
    }
}

/**
 * 存储源空态：居中图标 + 一句话（新增入口 = 右上角 +，不另设按钮）。
 */
@Composable
private fun EmptySourcesState(title: String) {
    EmptyState(
        icon = painterResource(R.drawable.ic_ms_cloud_sync),
        title = title,
        hint = stringResource(R.string.sync_storage_empty_hint),
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
                IconGhostButton(
                    painter = painterResource(R.drawable.ic_ms_edit),
                    contentDescription = stringResource(R.string.sync_storage_edit),
                    onClick = onEdit,
                )
                IconGhostButton(
                    painter = painterResource(R.drawable.ic_ms_delete),
                    contentDescription = stringResource(R.string.sync_storage_delete),
                    onClick = onDelete,
                )
            }
            Icon(
                if (selected) painterResource(R.drawable.ic_ms_radio_button_checked) else painterResource(R.drawable.ic_ms_radio_button_unchecked),
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
                FloatingLabelTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.sync_storage_name_label),
                    modifier = Modifier.fillMaxWidth(),
                                    )
                when (kind) {
                    ProviderKind.WEBDAV -> {
                        FloatingLabelTextField(
                            value = webdavForm.baseUrl,
                            onValueChange = { onWebdavChange(webdavForm.copy(baseUrl = it)) },
                            label = stringResource(R.string.sync_storage_webdav_url_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = webdavForm.username,
                            onValueChange = { onWebdavChange(webdavForm.copy(username = it)) },
                            label = stringResource(R.string.sync_storage_username_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = webdavForm.password,
                            onValueChange = { onWebdavChange(webdavForm.copy(password = it)) },
                            label = stringResource(R.string.sync_storage_password_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        TrustSelfSignedRow(
                            checked = webdavForm.trustSelfSigned,
                            onChange = { onWebdavChange(webdavForm.copy(trustSelfSigned = it)) },
                        )
                    }

                    ProviderKind.S3 -> {
                        FloatingLabelTextField(
                            value = s3Form.endpoint,
                            onValueChange = { onS3Change(s3Form.copy(endpoint = it)) },
                            label = stringResource(R.string.sync_storage_endpoint_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = s3Form.bucket,
                            onValueChange = { onS3Change(s3Form.copy(bucket = it)) },
                            label = stringResource(R.string.sync_storage_bucket_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = s3Form.region,
                            onValueChange = { onS3Change(s3Form.copy(region = it)) },
                            label = stringResource(R.string.sync_storage_region_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = s3Form.accessKeyId,
                            onValueChange = { onS3Change(s3Form.copy(accessKeyId = it)) },
                            label = stringResource(R.string.sync_storage_access_key_label),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = s3Form.secretAccessKey,
                            onValueChange = { onS3Change(s3Form.copy(secretAccessKey = it)) },
                            label = stringResource(R.string.sync_storage_secret_key_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                                                    )
                        FloatingLabelTextField(
                            value = s3Form.pathPrefix,
                            onValueChange = { onS3Change(s3Form.copy(pathPrefix = it)) },
                            label = stringResource(R.string.sync_storage_path_prefix_label),
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
            JiabanButton(
                text = stringResource(R.string.sync_storage_save),
                onClick = onConfirm,
                role = JiabanButtonRole.PRIMARY,
                size = JiabanButtonSize.M,
                loading = saving,
                enabled = !saving,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.sync_storage_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
                size = JiabanButtonSize.M,
                enabled = !saving,
            )
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
