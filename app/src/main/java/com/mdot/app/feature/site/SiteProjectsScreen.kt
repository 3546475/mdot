package com.mdot.app.feature.site

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.mdot.app.R
import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.repository.SiteRepository
import com.mdot.app.core.util.AppResult
import com.mdot.app.domain.model.SiteProject
import com.mdot.app.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 项目管理共享底座：项目列表 + 当前项目 */
open class SiteBaseViewModel @Inject constructor(
    protected val siteRepo: SiteRepository,
    protected val settings: SettingsDataSource,
) : ViewModel() {

    data class ProjectsUi(val projects: List<SiteProject>, val currentId: Long)

    val projectsUi: StateFlow<ProjectsUi> = combine(
        siteRepo.observeActiveProjects(),
        settings.salaryFlow,
    ) { list, salary -> ProjectsUi(list, salary.siteCurrentProjectId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProjectsUi(emptyList(), 0))

    fun selectProject(id: Long) = viewModelScope.launch { siteRepo.setCurrentProject(id) }
}

@HiltViewModel
class SiteProjectsViewModel @Inject constructor(
    siteRepo: SiteRepository,
    settings: SettingsDataSource,
) : SiteBaseViewModel(siteRepo, settings) {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 已归档项目（归档区展示，可恢复） */
    val archivedProjects: StateFlow<List<SiteProject>> = siteRepo.observeArchivedProjects()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun restoreProject(id: Long) = viewModelScope.launch { siteRepo.restoreProject(id) }

    fun purgeProject(id: Long) = viewModelScope.launch { siteRepo.purgeProject(id) }

    fun createProject(name: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        when (val r = siteRepo.createProject(name)) {
            is AppResult.Success -> { siteRepo.setCurrentProject(r.data); onDone() }
            is AppResult.Failure -> _message.value = (r.error as? com.mdot.app.core.util.AppError.InvalidMessage)?.message ?: "创建失败"
        }
    }

    /** 选择模式：把点中的项目设为当前项目并回调（由页面 popBackStack 返回记工页） */
    fun pickProject(id: Long, onDone: () -> Unit) = viewModelScope.launch {
        siteRepo.setCurrentProject(id)
        onDone()
    }

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch {
        siteRepo.reorderProjects(orderedIds)
    }

    fun deleteProject(id: Long) = viewModelScope.launch {
        siteRepo.archiveOrDeleteProject(id)
    }

    fun clearMessage() { _message.value = null }
}

/**
 * 项目管理页（12 文档 F-S2；样式仿班次管理：六点手柄拖拽排序 + 修改/删除图标，无显示开关）。
 * 项目间不允许重名（Repository 校验，失败 Snackbar 提示）。
 *
 * pickMode（v0.6.2）：从记工页「项目」行进入的**选择模式**——点行即把该项目设为当前项目并返回记工页；
 * 普通模式点行进入项目设置。两种模式下「修改」图标都可进入项目设置。
 */
@Composable
fun SiteProjectsScreen(
    onBack: () -> Unit,
    onOpenProject: (Long) -> Unit,
    pickMode: Boolean = false,
    onPicked: () -> Unit = {},
    vm: SiteProjectsViewModel = hiltViewModel(),
) {
    val ui by vm.projectsUi.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SiteProject?>(null) }
    var purging by remember { mutableStateOf<SiteProject?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 拖拽排序草稿（仿班次管理卡）
    var draftIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(ui.projects) {
        if (draggingId == null) draftIds = ui.projects.sortedBy { it.sort }.map { it.id }
    }

    message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page),
        ) {
            JiabanTopBar(
                title = stringResource(
                    if (pickMode) R.string.site_projects_pick_title else R.string.site_projects_title
                ),
                onBack = onBack,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.site_project_drag_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // 新建：tonal 胶囊
                val createInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.pressScale(createInteraction, pressedScale = 0.92f),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(
                                interactionSource = createInteraction,
                                indication = LocalIndication.current,
                                onClick = { showCreate = true },
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_add), null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            stringResource(R.string.site_project_create),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.m))

            // ---- 项目排序卡：六点手柄 + 名称/摘要 + 修改/删除图标 ----
            SectionCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (draftIds.isEmpty()) {
                        Text(
                            stringResource(R.string.site_projects_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.l, horizontal = Spacing.l),
                        )
                    }
                    draftIds.forEachIndexed { index, id ->
                        val project = ui.projects.firstOrNull { it.id == id } ?: return@forEachIndexed
                        key(id) {
                            val currentIndex by rememberUpdatedState(index)
                            val currentSize by rememberUpdatedState(draftIds.size)
                            val isDragged = draggingId == id
                            val dragScale by animateFloatAsState(
                                targetValue = if (isDragged) 1.05f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "projDragScale",
                            )
                            val cellPx = with(density) { 56.dp.toPx() }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer {
                                        translationY = if (isDragged) dragY else 0f
                                        scaleX = dragScale
                                        scaleY = dragScale
                                    }
                                    .zIndex(if (isDragged) 1f else 0f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 六点拖动手柄：手势只挂手柄，不挡页面滚动
                                    Box(
                                        Modifier
                                            .size(width = 28.dp, height = 56.dp)
                                            .pointerInput(id) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        dragFrom = currentIndex
                                                        draggingId = id
                                                        dragY = 0f
                                                        dragMoved = false
                                                    },
                                                    onDragEnd = {
                                                        if (dragFrom >= 0) {
                                                            val moved = dragMoved
                                                            dragFrom = -1
                                                            draggingId = null
                                                            dragY = 0f
                                                            dragMoved = false
                                                            if (moved) vm.reorder(draftIds)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        if (dragFrom >= 0) {
                                                            val moved = dragMoved
                                                            dragFrom = -1
                                                            draggingId = null
                                                            dragY = 0f
                                                            dragMoved = false
                                                            if (moved) vm.reorder(draftIds)
                                                        }
                                                    },
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    dragY += dragAmount
                                                    if (cellPx > 0 && dragFrom >= 0) {
                                                        var swapped = true
                                                        while (swapped) {
                                                            swapped = false
                                                            val f = dragFrom
                                                            if (dragY > cellPx * 0.5f) {
                                                                if (f + 1 < currentSize) {
                                                                    draftIds = draftIds.toMutableList()
                                                                        .apply { add(f + 1, removeAt(f)) }
                                                                    dragFrom = f + 1
                                                                    dragY -= cellPx
                                                                    dragMoved = true
                                                                    swapped = true
                                                                } else {
                                                                    dragY = cellPx * 0.5f
                                                                }
                                                            } else if (dragY < -cellPx * 0.5f) {
                                                                if (f - 1 >= 0) {
                                                                    draftIds = draftIds.toMutableList()
                                                                        .apply { add(f - 1, removeAt(f)) }
                                                                    dragFrom = f - 1
                                                                    dragY += cellPx
                                                                    dragMoved = true
                                                                    swapped = true
                                                                } else {
                                                                    dragY = -cellPx * 0.5f
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_ms_drag_indicator),
                                            contentDescription = stringResource(R.string.appearance_drag_reorder),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(Spacing.m))
                                    // 行主体：选择模式=切换当前项目并返回；普通模式=进入项目设置
                                    val rowInteraction = remember { MutableInteractionSource() }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .pressScale(rowInteraction, pressedScale = 0.98f)
                                            .clip(RoundedCornerShape(Radius.small))
                                            .clickable(
                                                interactionSource = rowInteraction,
                                                indication = LocalIndication.current,
                                            ) {
                                                if (pickMode) vm.pickProject(project.id) { onPicked() }
                                                else onOpenProject(project.id)
                                            }
                                            .padding(vertical = Spacing.xs),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                project.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (project.id == ui.currentId) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            MaterialTheme.colorScheme.tertiaryContainer,
                                                            RoundedCornerShape(Radius.pill),
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        stringResource(R.string.site_project_current_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            stringResource(
                                                R.string.site_project_summary_format,
                                                project.baseMinutes / 60,
                                                Money.yuanText(project.dailyRateCents).replace(",", ""),
                                                project.otBaseMinutes / 60,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // 修改（进入项目设置）
                                    RowIconAction(
                                        iconRes = R.drawable.ic_ms_edit,
                                        desc = stringResource(R.string.site_project_edit),
                                    ) { onOpenProject(project.id) }
                                    // 删除（有记录→归档）
                                    RowIconAction(
                                        iconRes = R.drawable.ic_ms_delete,
                                        desc = stringResource(R.string.site_project_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    ) { deleting = project }
                                }
                            }
                        }
                    }
                }
            }
            // ---- 已归档区：折叠栏（默认收起，点击展开），归档项目不可编辑/不可选，可一键恢复 ----
            val archived by vm.archivedProjects.collectAsStateWithLifecycle()
            if (archived.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                var archivedExpanded by remember { mutableStateOf(false) }
                val expandSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                val rotation by animateFloatAsState(
                    targetValue = if (archivedExpanded) 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "archivedArrow",
                )
                SectionCard {
                    Column {
                        // 折叠头：标题 + 数量 + 展开箭头，整行可点（无涟漪/无按压缩放）
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.small))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { archivedExpanded = !archivedExpanded }
                                .padding(vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.site_projects_archived),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${archived.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Icon(
                                painterResource(R.drawable.ic_ms_expand_more), null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = rotation },
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = archivedExpanded) {
                            Column(Modifier.padding(top = Spacing.s)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                archived.forEach { p ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.l, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                stringResource(R.string.site_project_summary_format, p.baseMinutes / 60, Money.yuanText(p.dailyRateCents).replace(",", ""), p.otBaseMinutes / 60),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        // 彻底删除（连带全部数据，不可恢复）：确认弹窗后 purge
                                        RowIconAction(
                                            iconRes = R.drawable.ic_ms_delete,
                                            desc = stringResource(R.string.site_project_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        ) { purging = p }
                                        TextButton(onClick = { vm.restoreProject(p.id) }) {
                                            Text(stringResource(R.string.site_projects_restore))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ---- 彻底删除（归档项目连带全部数据）确认 ----
    purging?.let { p ->
        AlertDialog(
            onDismissRequest = { purging = null },
            title = { Text(stringResource(R.string.site_project_purge_title)) },
            text = { Text(stringResource(R.string.site_project_purge_body, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    purging = null
                    vm.purgeProject(p.id)
                }) { Text(stringResource(R.string.site_project_purge_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { purging = null }) { Text(stringResource(R.string.site_dialog_cancel)) }
            },
        )
    }

    // ---- 新建项目 ----
    if (showCreate) {
        ProjectNameDialog(
            title = stringResource(R.string.site_project_create),
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                vm.createProject(name)
            },
        )
    }

    // ---- 删除确认 ----
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.site_project_delete_title)) },
            text = { Text(stringResource(R.string.site_project_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.deleteProject(p.id)
                }) { Text(stringResource(R.string.site_project_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.site_dialog_cancel)) }
            },
        )
    }
}

/** 行内图标按钮（修改/删除） */
@Composable
private fun RowIconAction(
    iconRes: Int,
    desc: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .pressScale(interaction, pressedScale = 0.88f)
            .clip(RoundedCornerShape(Radius.small))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 项目命名对话框（新建/改名共用） */
@Composable
fun ProjectNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                label = { Text(stringResource(R.string.site_project_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.textField),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.site_dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.site_dialog_cancel)) }
        },
    )
}
