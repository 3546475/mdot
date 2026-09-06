package com.mdot.app.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.sync.ProviderKind
import com.mdot.app.core.sync.S3Creds
import com.mdot.app.core.sync.WebDavCreds

/** 存储源设置页：从同步备份页顶部"备份状态"卡进入 */
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

        SectionCard {
            Column {
                Text(stringResource(R.string.sync_storage_select_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                // v0.5.x 前仅开放 WebDAV（S3 待真实网盘测试后放开）；未配置时直接展示 WebDAV 表单
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.kind == ProviderKind.WEBDAV,
                        onClick = { vm.onKind(ProviderKind.WEBDAV) },
                        label = { Text("WebDAV") },
                    )
                }

                when (state.kind) {
                    // 历史已配置 S3 的兜底展示（选择入口已隐藏）
                    ProviderKind.S3 -> {
                        Spacer(Modifier.height(Spacing.m))
                        S3Form(state.s3Form, vm::onS3Form)
                    }

                    else -> {
                        Spacer(Modifier.height(Spacing.m))
                        WebDavForm(state.webdavForm, vm::onWebdavForm)
                    }
                }

                Spacer(Modifier.height(Spacing.m))
                OutlinedButton(
                    onClick = {
                        // 未配置状态点保存 = 直接保存 WebDAV
                        if (state.kind == ProviderKind.NONE) vm.onKind(ProviderKind.WEBDAV)
                        vm.saveAndTest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_storage_save_test)) }
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

        Text(
            stringResource(R.string.sync_storage_tip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** HTTPS 自签证书信任开关（NAS/MinIO 等自建服务；默认关闭走系统校验） */
@Composable
private fun TrustSelfSignedRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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

@Composable
private fun WebDavForm(creds: WebDavCreds, onChange: (WebDavCreds) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = creds.baseUrl,
            onValueChange = { onChange(creds.copy(baseUrl = it)) },
            label = { Text(stringResource(R.string.sync_storage_webdav_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.username,
            onValueChange = { onChange(creds.copy(username = it)) },
            label = { Text(stringResource(R.string.sync_storage_username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.password,
            onValueChange = { onChange(creds.copy(password = it)) },
            label = { Text(stringResource(R.string.sync_storage_password_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.s))
        TrustSelfSignedRow(
            checked = creds.trustSelfSigned,
            onChange = { onChange(creds.copy(trustSelfSigned = it)) },
        )
    }
}

@Composable
private fun S3Form(creds: S3Creds, onChange: (S3Creds) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = creds.endpoint,
            onValueChange = { onChange(creds.copy(endpoint = it)) },
            label = { Text(stringResource(R.string.sync_storage_endpoint_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.bucket,
            onValueChange = { onChange(creds.copy(bucket = it)) },
            label = { Text("Bucket") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.region,
            onValueChange = { onChange(creds.copy(region = it)) },
            label = { Text(stringResource(R.string.sync_storage_region_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.accessKeyId,
            onValueChange = { onChange(creds.copy(accessKeyId = it)) },
            label = { Text("AccessKeyId") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.secretAccessKey,
            onValueChange = { onChange(creds.copy(secretAccessKey = it)) },
            label = { Text("SecretAccessKey") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = creds.pathPrefix,
            onValueChange = { onChange(creds.copy(pathPrefix = it)) },
            label = { Text(stringResource(R.string.sync_storage_path_prefix_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.s))
        TrustSelfSignedRow(
            checked = creds.trustSelfSigned,
            onChange = { onChange(creds.copy(trustSelfSigned = it)) },
        )
    }
}
