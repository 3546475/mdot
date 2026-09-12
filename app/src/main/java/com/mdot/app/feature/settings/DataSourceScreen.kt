package com.mdot.app.feature.settings

import android.net.Uri
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
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

/** 更新与数据源（F7-9 / F8-1）：hero 版本卡（检查更新 + from 源选择胶囊，弹窗内选择/添加更新源）+ 节假日库卡，与首页/统计 hero 同视觉体系 */
@Composable
fun DataSourceScreen(
    onBack: () -> Unit,
    vm: DataSourceViewModel = hiltViewModel(),
    updateVm: UpdateViewModel = hiltViewModel(),
) {
    val holidayVersion by vm.holidayVersion.collectAsStateWithLifecycle()
    val holidayUrl by vm.holidayUrl.collectAsStateWithLifecycle()
    val holidayUrls by vm.holidayUrls.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.datasource_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        // ---- hero：当前版本 + 检查更新 + from 源选择（与关于页共用，见 UpdateHeroCard） ----
        UpdateHeroCard(updateVm = updateVm, dsVm = vm)

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


/** hero 版本卡：当前版本大字 + 检查更新 + 花体 from 当前源胶囊（点开弹窗选择/添加/删除更新源）。
 *  更新与数据源页与关于页共用 */
@Composable
internal fun UpdateHeroCard(
    updateVm: UpdateViewModel,
    dsVm: DataSourceViewModel,
) {
    val updateUrl by dsVm.updateUrl.collectAsStateWithLifecycle()
    val updateUrls by dsVm.updateUrls.collectAsStateWithLifecycle()
    val updateNotice by updateVm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionText = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            "${pi.versionName} (${pi.versionCode})"
        }.getOrDefault("-")
    }
    var showSourcePicker by remember { mutableStateOf(false) }
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
                if (updateUrls.isNotEmpty()) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        stringResource(R.string.datasource_update_from),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Cursive,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Button(
                        onClick = { showSourcePicker = true },
                        shape = RoundedCornerShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(
                            sourceDisplayName(updateUrl),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
    if (showSourcePicker) {
        UpdateSourcePickerDialog(
            urls = updateUrls,
            selected = updateUrl,
            onSelect = { url ->
                dsVm.setUpdateUrl(url)
                showSourcePicker = false
            },
            onAdd = dsVm::addUpdateUrl,
            onRemove = dsVm::removeUpdateUrl,
            onDismiss = { showSourcePicker = false },
        )
    }
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

/** 更新数据源选择弹窗：名称 + 地址点选即生效，行内可删（多项时）、底部可添加新地址（更新源唯一管理入口） */
@Composable
private fun UpdateSourcePickerDialog(
    urls: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.datasource_source_picker_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                urls.forEach { url ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.small))
                            .clickable(onClick = { onSelect(url) })
                            .padding(horizontal = Spacing.xs, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = url == selected, onClick = null)
                        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
                            Text(
                                sourceDisplayName(url),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                Spacer(Modifier.height(Spacing.xs))
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.datasource_cancel)) }
        },
    )
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        val t = text.trim()
        val dup = t.isNotEmpty() && t in urls
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
                    isError = dup,
                    supportingText = if (dup) {
                        { Text(stringResource(R.string.datasource_add_url_dup)) }
                    } else null,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (t.startsWith("http://") || t.startsWith("https://")) && !dup,
                    onClick = {
                        onAdd(t)
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

/** 数据源展示名：知名域名用品牌名（专有名词非文案，不入 strings.xml），自定义地址取域名，供胶囊按钮与弹窗显示 */
private fun sourceDisplayName(url: String): String = when {
    url.contains("cnb.cool") -> "CNB"
    url.contains("github.io") || url.contains("github.com") || url.contains("raw.githubusercontent.com") -> "GitHub"
    else -> Uri.parse(url).host ?: url
}
