package com.mdot.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.navigation.Routes
import com.mdot.app.domain.model.WorkSystem
import kotlinx.coroutines.launch


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
            desc = stringResource(R.string.site_system_desc),
            enabled = true,
            isSelected = currentSystem == WorkSystem.SITE,
            onClick = {
                if (currentSystem != WorkSystem.SITE) pendingSystem = WorkSystem.SITE
            },
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
                        WorkSystem.SITE -> stringResource(R.string.site_switch_confirm)
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
    // #14：选中态过渡——容器色/border 色/描边宽动画 + 勾选角标 scaleIn（motionScheme defaultSpatial）
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            enabled -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "systemCardContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "systemCardBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "systemCardBorderWidth",
    )
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
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
                // 选中勾选角标（scaleIn）
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(
                        initialScale = 0f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = scaleOut(
                        targetScale = 0f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
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

/** 关于/设置 双页签页（v0.6.12：「设置」页签=原设置中心内容，独立设置中心页移除；我的页入口更名「关于与设置」） */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    updateVm: UpdateViewModel = hiltViewModel(),
    dsVm: DataSourceViewModel = hiltViewModel(),
) {
    val hub: SettingsHubViewModel = hiltViewModel()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        JiabanTopBar(
            title = null,
            titleContent = {
                SegmentBar(
                    labels = listOf(
                        stringResource(R.string.settings_about_title),
                        stringResource(R.string.about_tab_setup),
                    ),
                    selected = pagerState.currentPage,
                    onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                    segWidth = 84.dp,
                    position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                )
            },
            showBack = true,
            onBack = onBack,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> AboutPane(updateVm = updateVm, dsVm = dsVm, hub = hub)
                else -> SettingsHubPane(hub = hub, onOpen = onOpen)
            }
        }
    }
    UpdateFlow(updateVm)
}

/** 「关于」页签内容主体：hero 版本卡（自更新+数据源）+ 隐私说明 + 开源许可 + 项目地址 */
@Composable
private fun AboutPane(
    updateVm: UpdateViewModel,
    dsVm: DataSourceViewModel,
    hub: SettingsHubViewModel,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.m))

        // hero 版本卡（自更新与数据源页迁入）：当前版本大字 + 检查更新 + from 源选择胶囊
        UpdateHeroCard(updateVm = updateVm, dsVm = dsVm)
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
        Spacer(Modifier.height(Spacing.m))
        // 致谢名单（开源许可下方）：名单在 strings 的 settings_ack_names 维护
        AcknowledgementCard()
        Spacer(Modifier.height(Spacing.m))
        // 项目开源地址（公开仓 3546475/mdot），点击跳转 GitHub（置于开源许可之后）
        SectionCard {
            SettingRow(
                stringResource(R.string.settings_row_github),
                "github.com/3546475/mdot",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/3546475/mdot"))
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** 「设置」页签内容主体（原设置中心单列版）：查看与分享/数据与备份/个性化 分组入口 */
@Composable
private fun SettingsHubPane(hub: SettingsHubViewModel, onOpen: (String) -> Unit) {
    val appearance by hub.appearance.collectAsStateWithLifecycle()
    val syncStatus by hub.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = AdaptiveSpecs.contentMaxWidth)
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        Spacer(Modifier.height(Spacing.s))
        // ---- 查看与分享 ----
        SettingsGroup(stringResource(R.string.settings_group_view_share)) {
            SettingRow(stringResource(R.string.settings_row_calendar), null, painterResource(R.drawable.ic_ms_calendar_month), onClick = { onOpen(Routes.CALENDAR_PATTERN) })
            SettingRow(stringResource(R.string.settings_row_stats), null, painterResource(R.drawable.ic_ms_bar_chart), onClick = { onOpen(Routes.STATS) })
            SettingRow(stringResource(R.string.settings_row_share), null, painterResource(R.drawable.ic_ms_file_download), onClick = { onOpen(Routes.EXPORT) })
        }
        Spacer(Modifier.height(Spacing.m))
        // ---- 数据与备份 ----
        SettingsGroup(stringResource(R.string.settings_group_data_backup)) {
            SettingRow(
                stringResource(R.string.settings_row_sync_backup),
                if (syncStatus.configured) stringResource(R.string.settings_sync_configured)
                else stringResource(R.string.settings_sync_not_configured),
                painterResource(R.drawable.ic_ms_cloud_sync), onClick = { onOpen(Routes.SYNC) },
            )
        }
        Spacer(Modifier.height(Spacing.m))
        // ---- 个性化 ----
        SettingsGroup(stringResource(R.string.settings_group_personalization)) {
            SettingRow(
                stringResource(R.string.settings_row_appearance), appearanceSummary(context, appearance),
                painterResource(R.drawable.ic_ms_palette), onClick = { onOpen(Routes.APPEARANCE) },
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
