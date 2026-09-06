package com.mdot.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.model.RateTier
import com.mdot.app.domain.model.SalaryMode
import com.mdot.app.domain.util.Money
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.navigation.Routes
import com.mdot.app.domain.util.TimeUtils

/** 设置中心——二级页面：全部页面入口统一收纳、分组布局 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    hub: SettingsHubViewModel = hiltViewModel(),
) {
    val appearance by hub.appearance.collectAsStateWithLifecycle()
    val syncStatus by hub.syncStatus.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        // ---- 查看与分享 ----
        SettingsGroup(stringResource(R.string.settings_group_view_share)) {
            SettingRow(stringResource(R.string.settings_row_calendar), null, Icons.Outlined.CalendarMonth, onClick = { onOpen(Routes.CALENDAR_PATTERN) })
            SettingRow(stringResource(R.string.settings_row_stats), null, Icons.Outlined.BarChart, onClick = { onOpen(Routes.STATS) })
            SettingRow(stringResource(R.string.settings_row_share), null, Icons.Outlined.FileDownload, onClick = { onOpen(Routes.EXPORT) })
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 数据与备份 ----
        SettingsGroup(stringResource(R.string.settings_group_data_backup)) {
            SettingRow(
                stringResource(R.string.settings_row_sync_backup),
                if (syncStatus.configured) stringResource(R.string.settings_sync_configured)
                else stringResource(R.string.settings_sync_not_configured),
                Icons.Outlined.CloudSync, onClick = { onOpen(Routes.SYNC) },
            )
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 个性化 ----
        SettingsGroup(stringResource(R.string.settings_group_personalization)) {
            SettingRow(
                stringResource(R.string.settings_row_appearance), appearanceSummary(appearance),
                Icons.Outlined.Palette, onClick = { onOpen(Routes.APPEARANCE) },
            )
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 其他 ----
        SettingsGroup(stringResource(R.string.settings_group_other)) {
            SettingRow(stringResource(R.string.settings_row_about_privacy), null, Icons.Outlined.Shield, onClick = { onOpen(Routes.ABOUT) })
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 设置分组：小标题 + 卡片容器 */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.s, bottom = Spacing.xs),
    )
    SectionCard {
        Column {
            content()
        }
    }
}

/** 工时设置（03 文档 §5.5）——二级页面：顶栏 + 无底栏 */
@Composable
fun SystemScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenPayroll: () -> Unit,
    hub: SettingsHubViewModel = hiltViewModel(),
) {
    val anchorDay by hub.cycleAnchorDay.collectAsStateWithLifecycle()
    val workdays by hub.workdays.collectAsStateWithLifecycle()
    val shiftCount by hub.shiftCount.collectAsStateWithLifecycle()
    val salary by hub.salary.collectAsStateWithLifecycle()
    val compBalance by hub.compBalance.collectAsStateWithLifecycle()
    val isHourly = salary.workSystem == WorkSystem.HOURLY
    val isStandard = salary.workSystem == WorkSystem.STANDARD
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.settings_worktime_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(salary.workSystem.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (salary.workSystem) {
                            WorkSystem.HOURLY -> stringResource(R.string.settings_system_desc_hourly)
                            WorkSystem.COMPREHENSIVE -> stringResource(R.string.settings_system_desc_comprehensive)
                            WorkSystem.STANDARD -> stringResource(R.string.settings_system_desc_standard)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onOpen(Routes.SYSTEM_SWITCH) }) { Text(stringResource(R.string.settings_switch_action)) }
            }
        }

        Spacer(Modifier.height(Spacing.m))
        SectionCard {
            Column {
                SettingRow(
                    stringResource(R.string.settings_row_salary),
                    if (isHourly) {
                        val hourlyCents = salary.hourlyRatesCents[RateTier.WEEKDAY] ?: 0
                        if (hourlyCents > 0) stringResource(R.string.settings_salary_per_hour, Money.yuanText(hourlyCents)) else stringResource(R.string.settings_not_set)
                    } else if (salary.hasBaseSalary) stringResource(R.string.settings_salary_per_month, Money.yuanText(salary.baseSalaryCents))
                    else if (salary.mode == SalaryMode.MANUAL) stringResource(R.string.settings_manual_rate)
                    else stringResource(R.string.settings_not_set),
                    onClick = onOpenPayroll,
                )
                if (isStandard) {
                    SettingRow(
                        stringResource(R.string.settings_row_comp_balance),
                        if (compBalance < 0) "-" + TimeUtils.prettyDuration(-compBalance)
                        else TimeUtils.prettyDuration(compBalance),
                        onClick = { onOpen(Routes.COMP) },
                    )
                }
                SettingRow(stringResource(R.string.settings_row_cycle), stringResource(R.string.settings_cycle_anchor_summary, anchorDay), onClick = { onOpen(Routes.CYCLE) })
                if (!isHourly) {
                    SettingRow(
                        stringResource(R.string.settings_row_workdays),
                        if (workdays == SettingsHubDefaults.STANDARD_WORKDAYS) stringResource(R.string.settings_workdays_standard)
                        else stringResource(R.string.settings_workdays_custom_count, workdays.size),
                        onClick = { onOpen(Routes.WORKDAYS) },
                    )
                }
                SettingRow(stringResource(R.string.settings_row_shifts), stringResource(R.string.settings_shift_count_summary, shiftCount), onClick = { onOpen(Routes.SHIFTS) })
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 切换工时制度（v0.5.x：标准工时/小时工/综合工时可选，工地记工规划中） */
@Composable
fun SystemSwitchScreen(onBack: () -> Unit) {
    val hub: SettingsHubViewModel = hiltViewModel()
    val salary by hub.salary.collectAsStateWithLifecycle()
    val currentSystem = salary.workSystem
    var pendingSystem by remember { mutableStateOf<WorkSystem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.settings_switch_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        SystemCard(
            title = stringResource(R.string.settings_system_standard),
            desc = stringResource(R.string.settings_system_standard_desc),
            enabled = true,
            isSelected = currentSystem == WorkSystem.STANDARD,
            onClick = {
                if (currentSystem != WorkSystem.STANDARD) pendingSystem = WorkSystem.STANDARD
            },
        )
        Spacer(Modifier.height(Spacing.m))

        SystemCard(
            title = stringResource(R.string.settings_system_hourly),
            desc = stringResource(R.string.settings_system_desc_hourly),
            enabled = true,
            isSelected = currentSystem == WorkSystem.HOURLY,
            onClick = {
                if (currentSystem != WorkSystem.HOURLY) pendingSystem = WorkSystem.HOURLY
            },
        )
        Spacer(Modifier.height(Spacing.m))

        SystemCard(
            title = stringResource(R.string.settings_system_comprehensive),
            desc = stringResource(R.string.settings_system_comprehensive_desc),
            enabled = true,
            isSelected = currentSystem == WorkSystem.COMPREHENSIVE,
            onClick = {
                if (currentSystem != WorkSystem.COMPREHENSIVE) pendingSystem = WorkSystem.COMPREHENSIVE
            },
        )
        Spacer(Modifier.height(Spacing.m))

        SystemCard(
            title = stringResource(R.string.settings_system_construction),
            desc = stringResource(R.string.settings_system_construction_desc),
            enabled = false,
            isSelected = false,
            onClick = {},
        )
        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.settings_switch_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ---- 切换确认对话框 ----
    pendingSystem?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSystem = null },
            title = { Text(stringResource(R.string.settings_switch_confirm_title, target.displayName)) },
            text = {
                Text(
                    when (target) {
                        WorkSystem.HOURLY -> stringResource(R.string.settings_switch_confirm_hourly)
                        WorkSystem.COMPREHENSIVE -> stringResource(R.string.settings_switch_confirm_comprehensive)
                        WorkSystem.STANDARD -> stringResource(R.string.settings_switch_confirm_standard)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hub.switchWorkSystem(target)
                        pendingSystem = null
                    },
                ) { Text(stringResource(R.string.settings_confirm_switch)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSystem = null }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun SystemCard(
    title: String,
    desc: String,
    enabled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            enabled -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(Radius.card),
                ) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                when {
                    isSelected -> Text(
                        stringResource(R.string.settings_in_use),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    !enabled -> Text(
                        stringResource(R.string.settings_planned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 关于页（03 文档 §5.5：隐私说明、版本、检查更新） */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    update: DataSourceViewModel = hiltViewModel(),
) {
    val updateMsg by update.message.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.settings_about_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        SectionCard {
            Column {
                SettingRow(stringResource(R.string.settings_row_app), stringResource(R.string.app_name))
                SettingRow(
                    stringResource(R.string.settings_row_version),
                    runCatching {
                        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                        @Suppress("DEPRECATION")
                        "${pi.versionName} (${pi.versionCode})"
                    }.getOrDefault("-"),
                )
                // 更新 JSON 托管完成后（v0.5.x）此行接真实更新检查
                SettingRow(stringResource(R.string.settings_row_check_update), null, onClick = update::checkUpdate)
                updateMsg?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2500)
                        update.clearMessage()
                    }
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.s, top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        SectionCard {
            Column {
                Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        SectionCard {
            Column {
                Text(stringResource(R.string.settings_oss_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.settings_oss_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
