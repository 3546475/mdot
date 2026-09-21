package com.mdot.app.feature.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.primaryTabEdgeRelay
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanButtonSize
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.InlineLoadingButton
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.sync.SyncPhase
import com.mdot.app.domain.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

private fun selectedSourceName2(state: SyncUiState): String? {
    val selected = state.sources.selectedId ?: return null
    return state.sources.webdav.firstOrNull { it.id == selected }?.name
        ?: state.sources.s3.firstOrNull { it.id == selected }?.name
}

/** 同步备份页（M6 / 06 文档）：v0.6.9 起与存储源合并为双页签（备份 / 存储源）；备份状态展示移入备份与恢复卡 */
@Composable
fun SyncScreen(
    canBack: Boolean = false,
    initialTab: Int = 0,
    onBack: () -> Unit = {},
    vm: SyncViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 1), pageCount = { 2 })

    // 本地文件备份：导出选择位置 / 导入打开文件
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportLocalFile) }
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::prepareLocalRestore) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // 一级 Tab 形态的「边缘接力」：内层 pager 到尽头后把余量交给整页横滑切相邻一级 Tab
                // （与统计页同款，见 docs/11 041）
                .primaryTabEdgeRelay(pagerState),
        ) {
            if (canBack) {
                // 二级页形态：本页自带顶栏 + 返回，页签条放顶栏 titleContent
                JiabanTopBar(
                    title = null,
                    titleContent = { SyncTabBar(pagerState) },
                    showBack = true,
                    onBack = onBack,
                )
            } else {
                // 一级 Tab 形态：AppRoot 的固定顶栏（工时制度 + 设置齿轮）压在本页之上，
                // 故页签条下移一行（避让状态栏 + 固定顶栏高度），否则会被固定顶栏遮住（同统计页方案）
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Spacer(Modifier.height(statusBar + TopBarHeight))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    SyncTabBar(pagerState)
                }
            }

            // 页签用 Pager 横滑（与统计/外观合并页一致）；各页根必须 fillMaxSize 顶对齐——
            // Pager 会把不足一屏的页在视口内垂直居中（实测存储源页上方留白 ~330dp）。
            // 页内自带垂直滚动、严禁再嵌套：NavHost 转场 forceMeasure 传无界约束时
            // 内层滚动会抛「infinite maximum height」（实测高概率崩溃，见 c43f078）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                SyncTabPage(
                    page = page,
                    state = state,
                    vm = vm,
                    canBack = canBack,
                    createDoc = createDoc,
                    openDoc = openDoc,
                    onGoToStorage = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            }
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
                            JiabanButton(
                                text = stringResource(R.string.sync_action_restore),
                                onClick = vm::confirmRestore,
                                role = JiabanButtonRole.PRIMARY,
                                size = JiabanButtonSize.M,
                            )
                        },
                        dismissButton = {
                            JiabanButton(
                                text = stringResource(R.string.sync_action_cancel),
                                onClick = vm::cancelRestore,
                                role = JiabanButtonRole.GHOST,
                                size = JiabanButtonSize.M,
                            )
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
                            // 危险确认（error 色）暂留原样：危险色按钮变体随第三批对话框统一收敛
                            TextButton(
                                onClick = vm::confirmLocalRestore,
                            ) { Text(stringResource(R.string.sync_local_restore_confirm_action), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            JiabanButton(
                                text = stringResource(R.string.sync_action_cancel),
                                onClick = vm::cancelLocalRestore,
                                role = JiabanButtonRole.GHOST,
                                size = JiabanButtonSize.M,
                            )
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
                            JiabanButton(
                                text = stringResource(R.string.sync_restart_now),
                                onClick = {
                                    vm.dismissRestart()
                                    restartApp(context)
                                },
                                role = JiabanButtonRole.PRIMARY,
                                size = JiabanButtonSize.M,
                            )
                        },
                        dismissButton = {
                            JiabanButton(
                                text = stringResource(R.string.sync_action_later),
                                onClick = vm::dismissRestart,
                                role = JiabanButtonRole.GHOST,
                                size = JiabanButtonSize.M,
                            )
                        },
                    )
                }

        val (snackbarHostState, snackbarIsError) = rememberMessageSnackbar(
            message = state.message,
            onClear = vm::clearMessage,
            isError = state.isError,
        )
        MessageSnackbarHost(snackbarHostState, snackbarIsError, Modifier.align(Alignment.BottomCenter))
    }
}

/** 页签条（备份 / 存储源）：一级形态居中独立一行、二级形态放顶栏 titleContent —— 两处共用，避免两份定义漂移 */
@Composable
private fun SyncTabBar(pagerState: androidx.compose.foundation.pager.PagerState) {
    val scope = rememberCoroutineScope()
    SegmentBar(
        labels = listOf(
            stringResource(R.string.sync_tab_backup),
            stringResource(R.string.sync_tab_storage),
        ),
        selected = pagerState.currentPage,
        onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
        segWidth = 112.dp,
        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
    )
}

/** 页内容（备份 / 存储源）：pager 页与转场静态形态共用 */
@Composable
private fun SyncTabPage(
    page: Int,
    state: SyncUiState,
    vm: SyncViewModel,
    canBack: Boolean,
    createDoc: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    openDoc: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    onGoToStorage: () -> Unit,
) {
    Column(
        Modifier
            // 必须撑满 pager 视口：内容不足一屏时 pager 会把页垂直居中，顶上留大片空白
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = !canBack)
            .padding(horizontal = Spacing.page),
    ) {
        if (!state.loaded) {
            Box(
                Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.padding(Spacing.xl))
            }
        } else if (page == 0) {
            // ---- 备份页签：备份与恢复（云端，未配置时收敛为空态直达）+ 本地文件 ----
            // （自动备份并入云端卡尾部：它只对云端生效，不单独占卡；未配置时不展示，
            //   避免出现「开着却没在跑」的说谎开关，设置值仍保留、配好源即恢复）
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.sync_section_backup_restore), style = MaterialTheme.typography.titleSmall)
                    if (!state.status.configured) {
                        // 未配置：空态 + 直达 CTA（不再展示两个不可点的死按钮）
                        EmptyState(
                            icon = painterResource(R.drawable.ic_ms_cloud_sync),
                            title = stringResource(R.string.sync_backup_empty_title),
                            hint = stringResource(R.string.sync_status_not_configured),
                        )
                        JiabanButton(
                            text = stringResource(R.string.sync_backup_go_storage),
                            onClick = onGoToStorage,
                            role = JiabanButtonRole.PRIMARY,
                            size = JiabanButtonSize.L,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // 备份状态（原独立状态卡内容移此展示）
                        val srcName = selectedSourceName2(state)
                        if (srcName != null) {
                            Text(
                                stringResource(R.string.sync_status_source, srcName),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                        }
                        Text(
                            when {
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
                        // 同组并排同高（docs/03 §13.1）：weight 等分外壳——单个收缩✓在自身
                        // 外壳内完成，不挤占邻居
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            JiabanButton(
                                text = stringResource(R.string.sync_backup_now_cloud),
                                onClick = vm::backupNow,
                                role = JiabanButtonRole.PRIMARY,
                                size = JiabanButtonSize.L,
                                loading = state.status.busy,
                                enabled = !state.status.busy,
                                modifier = Modifier.weight(1f),
                            )
                            JiabanButton(
                                text = stringResource(R.string.sync_restore_from_cloud),
                                onClick = vm::prepareRestore,
                                role = JiabanButtonRole.SECONDARY,
                                size = JiabanButtonSize.L,
                                enabled = !state.status.busy,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.sync_auto_backup_title), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.sync_auto_backup_desc), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = state.autoBackup, onCheckedChange = vm::onAutoBackup)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.m))

            // ---- 本地文件备份 / 恢复（不依赖网盘）；两钮并排压卡高 ----
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.sync_local_backup_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.sync_local_backup_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        JiabanButton(
                            text = stringResource(R.string.sync_local_export),
                            onClick = { createDoc.launch(SyncViewModel.defaultLocalFileName()) },
                            role = JiabanButtonRole.PRIMARY,
                            size = JiabanButtonSize.L,
                            loading = state.localBusy,
                            enabled = !state.localBusy && !state.status.busy,
                            modifier = Modifier.weight(1f),
                        )
                        JiabanButton(
                            text = stringResource(R.string.sync_local_restore),
                            onClick = { openDoc.launch(arrayOf("application/zip", "*/*")) },
                            role = JiabanButtonRole.SECONDARY,
                            size = JiabanButtonSize.L,
                            enabled = !state.localBusy && !state.status.busy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            } else {
                // ---- 存储源页签 ----
                SyncStoragePane(state = state, vm = vm)
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }

private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
}
