package com.mdot.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.navigation.contentBottomPadding

/** 更新与数据源（F7-9 / F8-1）：hero 版本卡（检查更新）+ 更新源卡 + 节假日库卡，与首页/统计 hero 同视觉体系 */
@Composable
fun DataSourceScreen(
    onBack: () -> Unit,
    vm: DataSourceViewModel = hiltViewModel(),
    updateVm: UpdateViewModel = hiltViewModel(),
) {
    val holidayVersion by vm.holidayVersion.collectAsStateWithLifecycle()
    val holidayUrl by vm.holidayUrl.collectAsStateWithLifecycle()
    val updateUrl by vm.updateUrl.collectAsStateWithLifecycle()
    val holidayUrls by vm.holidayUrls.collectAsStateWithLifecycle()
    val updateUrls by vm.updateUrls.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val updateNotice by updateVm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionText = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            "${pi.versionName} (${pi.versionCode})"
        }.getOrDefault("-")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.datasource_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        // ---- hero：当前版本 + 检查更新（primaryContainer，与首页收入卡同体系） ----
        SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.datasource_current_version),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    versionText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(Spacing.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = updateVm::check,
                        shape = RoundedCornerShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.datasource_check_update))
                    }
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(start = Spacing.m)
                                .height(20.dp),
                        )
                    }
                }
                updateNotice?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3000)
                        updateVm.clearNotice()
                    }
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 更新源：多选一（点选即生效，可添加/删除候选） ----
        SectionCard {
            Column {
                Text(stringResource(R.string.datasource_section_update), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                UrlPicker(
                    urls = updateUrls,
                    selected = updateUrl,
                    onSelect = vm::setUpdateUrl,
                    onRemove = vm::removeUpdateUrl,
                    onAdd = vm::addUpdateUrl,
                )
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 节假日库 ----
        SectionCard {
            Column {
                Text(stringResource(R.string.datasource_section_holiday), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                SettingRow(
                    stringResource(R.string.datasource_current_lib_version),
                    holidayVersion ?: stringResource(R.string.datasource_builtin_default),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = vm::refreshHoliday,
                        enabled = !busy,
                        shape = RoundedCornerShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.datasource_refresh_now))
                    }
                }
                Text(
                    stringResource(R.string.datasource_builtin_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.s))
                UrlPicker(
                    urls = holidayUrls,
                    selected = holidayUrl,
                    onSelect = vm::setHolidayUrl,
                    onRemove = vm::removeHolidayUrl,
                    onAdd = vm::addHolidayUrl,
                )
            }
        }

        message?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                vm.clearMessage()
            }
            Text(
                msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
    UpdateFlow(updateVm)
}


/** URL 多选一列表：RadioButton 点选即生效；多项时可删除；底部添加（对话框校验 http(s) 前缀与重复） */
@Composable
private fun UrlPicker(
    urls: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // 候选列表限高（约 3 行），多地址时内部滚动避免整页拉长
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState())
        ) {
        urls.forEach { url ->
            val isSel = url == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.small))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .clickable(onClick = { onSelect(url) })
                    .padding(horizontal = Spacing.s, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSel, onClick = { onSelect(url) })
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (urls.size > 1) {
                    IconButton(onClick = { onRemove(url) }) {
                        Icon(
                            painterResource(R.drawable.ic_ms_delete),
                            contentDescription = stringResource(R.string.ds_confirm_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(Spacing.s))
        OutlinedButton(
            onClick = { showAdd = true },
            shape = RoundedCornerShape(Radius.pill),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.datasource_add_url), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.datasource_add_url)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.datasource_add_url_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        onAdd(text)
                        showAdd = false
                    },
                ) { Text(stringResource(R.string.datasource_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.datasource_cancel)) }
            },
        )
    }
}
