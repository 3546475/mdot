package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.navigation.contentBottomPadding

/** 更新与数据源（F7-9 / F8-1） */
@Composable
fun DataSourceScreen(
    onBack: () -> Unit,
    vm: DataSourceViewModel = hiltViewModel(),
    updateVm: UpdateViewModel = hiltViewModel(),
) {
    val holidayVersion by vm.holidayVersion.collectAsStateWithLifecycle()
    val holidayUrl by vm.holidayUrl.collectAsStateWithLifecycle()
    val updateUrl by vm.updateUrl.collectAsStateWithLifecycle()
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
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.datasource_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        SectionCard {
            Column {
                Text(stringResource(R.string.datasource_section_update), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                SettingRow(stringResource(R.string.datasource_current_version), versionText)
                OutlinedButton(onClick = updateVm::check, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.datasource_check_update))
                }
                updateNotice?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3000)
                        updateVm.clearNotice()
                    }
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                OutlinedTextField(
                shape = RoundedCornerShape(Radius.textField),
                    value = updateUrl,
                    onValueChange = vm::setUpdateUrl,
                    label = { Text(stringResource(R.string.datasource_update_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(Spacing.m))

        SectionCard {
            Column {
                Text(stringResource(R.string.datasource_section_holiday), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                SettingRow(stringResource(R.string.datasource_current_lib_version), holidayVersion ?: stringResource(R.string.datasource_builtin_default))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::refreshHoliday, enabled = !busy) { Text(stringResource(R.string.datasource_refresh_now)) }
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(start = 12.dp)
                                .height(20.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.datasource_builtin_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(Spacing.s))
                OutlinedTextField(
                shape = RoundedCornerShape(Radius.textField),
                    value = holidayUrl,
                    onValueChange = vm::setHolidayUrl,
                    label = { Text(stringResource(R.string.datasource_holiday_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
    UpdateFlow(updateVm)
}
