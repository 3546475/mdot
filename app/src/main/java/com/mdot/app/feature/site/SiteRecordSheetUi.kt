package com.mdot.app.feature.site

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.BottomBarSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.AdaptiveSpecs
import com.mdot.app.core.designsystem.WindowSpec
import com.mdot.app.core.designsystem.LocalWindowSpec
import com.mdot.app.core.designsystem.Duration
import com.mdot.app.core.navigation.bottomBarContentPaddingValues
import com.mdot.app.core.designsystem.component.DatePick
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.domain.model.AdvancePurpose
import com.mdot.app.domain.model.SiteDayStatus
import com.mdot.app.domain.model.SiteOtMode
import com.mdot.app.domain.util.Money
import com.mdot.app.feature.record.DurationGrid
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 工地记工页（12 文档 F-S3/F-S5；M3 Expressive 重设计版式）：
 * tonal 胶囊分段顶 Tab（记账 | 记借支·结算）+ 24dp 圆角卡片行（项目/日期/工钱/备注）+
 * 弹簧缩放选择瓦片（上班四态/加班三态/上下午）+ 大字工钱 + 照片留证瓦片 + 胶囊主按钮。
 * 动效统一 MaterialTheme.motionScheme 弹簧 specs，按压反馈 pressScale。
 */
@Composable
fun SiteRecordScreen(
    onBack: () -> Unit,
    onOpenProjectSettings: (Long) -> Unit = {},
    onOpenProjectPick: () -> Unit = {},
    onOpenSettlement: () -> Unit = {},
    vm: SiteRecordViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.open(LocalDate.now()) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    var topTab by remember { mutableStateOf(0) } // 0=记账 1=记借支/结算
    var subTab by remember { mutableStateOf(0) } // 记账内：0=点工 1=包工/工量
    var cashKind by remember { mutableStateOf(0) } // 记借支/结算内：0=借支 1=结算
    var showUnitSheet by remember { mutableStateOf(false) }
    // 保存打勾反馈：点保存后按钮短暂变 ✓ 再执行保存（成功后经 saved 状态返回）
    var saveFlash by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) { if (state.error != null) saveFlash = false }
    // motionScheme 仅 composable 可调用：先取 spec 再传入 transitionSpec（非 Composable 上下文）
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // 保存分发：记账·点工 / 记账·包工 / 借支 / 结算（本次结算金额）四种表单（悬浮保存按钮使用）
    fun saveCurrent(keepOpen: Boolean, onDone: () -> Unit = {}) {
        when {
            topTab == 1 && cashKind == 1 -> vm.saveSettlementAmount(keepOpen, onDone)
            topTab == 1 -> vm.saveAdvance(keepOpen, onDone)
            subTab == 1 -> vm.savePieceWork(keepOpen, onDone)
            else -> vm.saveAttendance(keepOpen, onDone)
        }
    }

    /** 保存 + 触觉/打勾反馈：延迟极短一拍展示 ✓，再真正执行保存 */
    fun saveWithFeedback(keepOpen: Boolean, onDone: () -> Unit = {}) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (keepOpen) {
            saveCurrent(keepOpen = true)
        } else {
            saveFlash = true
            scope.launch {
                delay(350)
                saveCurrent(keepOpen = false, onDone = onDone)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.page),
        ) {
            RecordHeader(selected = topTab, onSelect = { topTab = it }, onBack = onBack)

            AnimatedContent(
                targetState = topTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spatialSpec) { it / 6 * dir } + fadeIn(effectsSpec)) togetherWith
                        (slideOutHorizontally(spatialSpec) { -it / 6 * dir } + fadeOut(effectsSpec))
                },
                label = "recordTab",
                modifier = Modifier.weight(1f),
            ) { tab ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.loading) {
                        Text(
                            stringResource(R.string.site_settlement_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    } else if (tab == 0) {
                        BookkeepingContent(
                            state = state,
                            vm = vm,
                            subTab = subTab,
                            onSubTab = { subTab = it },
                            onOpenProjectSettings = onOpenProjectSettings,
                            onOpenProjectPick = onOpenProjectPick,
                            onOpenUnitSheet = { showUnitSheet = true },
                        )
                    } else {
                        AdvanceContent(state, vm, onOpenProjectPick, onOpenSettlement, cashKind) { cashKind = it }
                    }
                    // 底部操作条避让：用全局标准值（底栏高 + 边距 + 导航条），且必须位于滚动内容内部
                    Spacer(Modifier.height(bottomBarContentPaddingValues().calculateBottomPadding()))
                }
            }
        }

        // 工量单位选择：自实现底部弹层（M3 ModalBottomSheet 锚点随内容高度变化会误判滑出，故不用）
        if (showUnitSheet) {
            UnitPickerSheet(
                current = state.pieceUnit,
                onSelect = {
                    vm.onPieceUnit(it)
                    showUnitSheet = false
                },
                onDismiss = { showUnitSheet = false },
            )
        }

        // ---- 底部悬浮区：错误横幅（就近提示）+ 两颗保存药丸 ----
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.l)
                .padding(bottom = Spacing.m),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingSaveButton(
                    text = stringResource(R.string.site_save_again),
                    primary = false,
                    onClick = { saveWithFeedback(keepOpen = true) },
                )
                FloatingSaveButton(
                    text = stringResource(R.string.site_record_save),
                    primary = true,
                    flash = saveFlash,
                    onClick = { saveWithFeedback(keepOpen = false, onDone = onBack) },
                )
            }
        }
    }
}

/** 悬浮保存按钮：胶囊 + 阴影 + 按压缩放（M3E 动效走 motionScheme）；flash=true 时内容变 ✓ 表示已保存 */
@Composable
private fun FloatingSaveButton(text: String, primary: Boolean, flash: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val elevSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateFloatAsState(
        targetValue = if (pressed) 3f else 10f,
        animationSpec = elevSpec,
        label = "saveFabElevation",
    )
    val shape = RoundedCornerShape(Radius.pill)
    Surface(
        shape = shape,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = elevation.dp,
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        if (flash) {
            Icon(
                painterResource(R.drawable.ic_ms_check), null,
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.xl, vertical = 20.dp)
                    .size(24.dp),
            )
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 20.dp),
            )
        }
    }
}

// ---- 顶栏：tonal 圆形返回 + 胶囊分段 ----

@Composable
private fun RecordHeader(selected: Int, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    val backInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        // 返回按钮固定在左，顶部 Tab 胶囊整体居中
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .pressScale(backInteraction, pressedScale = 0.88f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(interactionSource = backInteraction, indication = LocalIndication.current, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_arrow_back),
                contentDescription = stringResource(R.string.site_back),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SegmentPill(stringResource(R.string.site_tab_book), selected == 0) { onSelect(0) }
                SegmentPill(stringResource(R.string.site_tab_advance_settle), selected == 1) { onSelect(1) }
            }
        }
    }
}

@Composable
internal fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "segBg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "segFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "segScale",
    )
    Text(
        text,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radius.pill))
            .background(bgColor)
            .pressScale(interaction, pressedScale = 0.94f)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fgColor,
    )
}

// ---- 记账内容 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectDateCard(
    state: SiteRecordUiState,
    onOpenProjectPick: () -> Unit,
    onShowDatePicker: () -> Unit,
    onShowMultiDate: () -> Unit,
) {
    SectionCard {
        Column {
            // 项目行：点击进入项目管理页「选择模式」，选完返回按新项目刷新；尾注展示当前日价
            TappableTonalRow(
                iconRes = R.drawable.ic_ms_dashboard,
                label = stringResource(R.string.site_row_project),
                value = state.projectName,
                trailingText = state.project?.takeIf { it.dailyRateCents > 0 }
                    ?.let { stringResource(R.string.site_project_rate_hint, yuanShort(it.dailyRateCents)) }
                    ?: "",
                onClick = onOpenProjectPick,
            )
            // 日期行：今天徽标 + 可多选尾注
            val isToday = state.date == LocalDate.now()
            val dateLabel = state.date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
            TappableTonalRow(
                iconRes = R.drawable.ic_ms_more_time,
                label = stringResource(R.string.site_row_date),
                value = if (state.extraDates.isEmpty()) dateLabel else "$dateLabel +${state.extraDates.size}",
                badge = {
                    if (isToday) {
                        BadgePill(
                            stringResource(R.string.site_today),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                },
                trailingText = stringResource(R.string.site_multi_select),
                trailingActive = state.extraDates.isNotEmpty(),
                onTrailing = onShowMultiDate,
                onClick = onShowDatePicker,
            )
        }
    }
}

/** 备注 + 照片：同一张 SectionCard 两行（记账/记借支·结算共用） */
@Composable
private fun NotePhotoCard(
    note: String,
    onEditNote: () -> Unit,
    photos: List<String>,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    SectionCard {
        Column {
            TappableTonalRow(
                iconRes = R.drawable.ic_ms_edit,
                label = stringResource(R.string.site_note),
                value = note.ifEmpty { stringResource(R.string.site_note_hint) },
                valueColor = if (note.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                onClick = onEditNote,
            )
            Column(Modifier.padding(vertical = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_photo_camera), null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.m))
                    Text(
                        stringResource(R.string.site_photos),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.site_photo_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                PhotoSection(photos = photos, onAdd = onAddPhotos, onRemove = onRemovePhoto)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookkeepingContent(
    state: SiteRecordUiState,
    vm: SiteRecordViewModel,
    subTab: Int,
    onSubTab: (Int) -> Unit,
    onOpenProjectSettings: (Long) -> Unit,
    onOpenProjectPick: () -> Unit,
    onOpenUnitSheet: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showMultiDate by remember { mutableStateOf(false) }
    var showWorkDayPicker by remember { mutableStateOf(false) }
    var showWorkHourPicker by remember { mutableStateOf(false) }
    var showOtDayPicker by remember { mutableStateOf(false) }
    var showOtHourPicker by remember { mutableStateOf(false) }
    var showNoteEdit by remember { mutableStateOf(false) }
    var showStandardDialog by remember { mutableStateOf(false) }

    Spacer(Modifier.height(Spacing.s))

    // ---- 项目 + 日期：同一张 SectionCard 两行（共用组件） ----
    ProjectDateCard(
        state = state,
        onOpenProjectPick = onOpenProjectPick,
        onShowDatePicker = { showDatePicker = true },
        onShowMultiDate = { showMultiDate = true },
    )
    Spacer(Modifier.height(Spacing.s))

    // ---- 子 Tab：点工 | 包工/工量（与顶部 Tab 同款胶囊容器，整体水平居中） ----
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SegmentPill(stringResource(R.string.site_sub_day), subTab == 0) { onSubTab(0) }
                SegmentPill(stringResource(R.string.site_sub_piece), subTab == 1) { onSubTab(1) }
            }
        }
    }
    Spacer(Modifier.height(Spacing.s))

    val base = state.project?.baseMinutes ?: 480
    val otBase = state.project?.otBaseMinutes ?: 360

    if (subTab == 1) {
        PieceForm(state, vm, onOpenUnitSheet)
    } else {

    // ---- 上班 + 加班：一张卡两段（段间分隔线），减少散块 ----
    SectionCard {
        Column {
            Text(
                stringResource(R.string.site_work),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SelectTile(
            label = stringResource(R.string.site_one_work),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 0,
            modifier = Modifier.weight(1f),
        ) { vm.onWorkMinutes(base); vm.onHalfOfDay(null) }
        SelectTile(
            label = if (state.dayStatus == SiteDayStatus.WORK && state.workTile == 1)
                fmtAmount(state.workMinutes.toDouble() / base, R.string.site_picked_days_fmt)
            else stringResource(R.string.site_pick_days),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 1,
            modifier = Modifier.weight(1f),
        ) { showWorkDayPicker = true }
        SelectTile(
            label = if (state.dayStatus == SiteDayStatus.WORK && state.workTile == 2)
                fmtAmount(state.workMinutes / 60.0, R.string.site_picked_hours_fmt)
            else stringResource(R.string.site_pick_hours),
            selected = state.dayStatus == SiteDayStatus.WORK && state.workTile == 2,
            modifier = Modifier.weight(1f),
        ) { showWorkHourPicker = true }
        SelectTile(
            label = stringResource(R.string.site_rest),
            selected = state.dayStatus == SiteDayStatus.REST,
            modifier = Modifier.weight(1f),
        ) { vm.onDayStatus(SiteDayStatus.REST) }
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(R.string.site_ot),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SelectTile(
                    label = stringResource(R.string.site_no_ot),
                    selected = state.otTile == 0,
                    modifier = Modifier.weight(1f),
                ) { vm.onOtMinutes(0) }
                SelectTile(
                    label = if (state.otTile == 1) fmtAmount(state.otMinutes.toDouble() / otBase, R.string.site_picked_days_fmt)
                    else stringResource(R.string.site_pick_days),
                    selected = state.otTile == 1,
                    modifier = Modifier.weight(1f),
                ) { showOtDayPicker = true }
                SelectTile(
                    label = if (state.otTile == 2) fmtAmount(state.otMinutes / 60.0, R.string.site_picked_hours_fmt)
                    else stringResource(R.string.site_pick_hours),
                    selected = state.otTile == 2,
                    modifier = Modifier.weight(1f),
                ) { showOtHourPicker = true }
            }
        }
    }

    Spacer(Modifier.height(Spacing.s))

    // ---- 工钱 hero 卡（primaryContainer 底色突出主角；¥ 大字滚动动画；点击 → 点工标准设置） ----
    PayHeroCard(
        previewCents = state.previewCents(),
        summary = state.project?.let {
            stringResource(R.string.site_pay_summary_work, Money.yuanText(it.dailyRateCents).replace(",", "")) +
                "\n" + stringResource(R.string.site_pay_summary_ot, it.otBaseMinutes / 60)
        } ?: "",
        onClick = { showStandardDialog = true },
    )
    }

    Spacer(Modifier.height(Spacing.s))

    // ---- 备注 + 照片：同一张 SectionCard 两行（共用组件） ----
    NotePhotoCard(
        note = state.note,
        onEditNote = { showNoteEdit = true },
        photos = state.photos,
        onAddPhotos = { vm.addPhotos(it) },
        onRemovePhoto = { vm.removePhoto(it) },
    )

    // ---- 对话框 ----
    if (showDatePicker) {
        DatePick(initial = state.date, onPick = { vm.onDate(it) }, onDismiss = { showDatePicker = false })
    }
    if (showMultiDate) {
        MultiDateDialog(
            primary = state.date,
            selected = state.extraDates,
            onToggle = { vm.onToggleExtraDate(it) },
            onDismiss = { showMultiDate = false },
        )
    }
    if (showWorkDayPicker) {
        DayCountDialog(
            title = stringResource(R.string.site_pick_days),
            current = state.workMinutes,
            unitMinutes = base,
            onPick = { minutes -> vm.pickWorkDays(minutes); showWorkDayPicker = false },
            onDismiss = { showWorkDayPicker = false },
        )
    }
    if (showWorkHourPicker) {
        HourInputDialog(
            title = stringResource(R.string.site_pick_hours),
            initialHours = state.workMinutes.takeIf { m -> m > 0 }?.let { m -> m / 60.0 },
            onPick = { hours -> vm.pickWorkHours((hours * 60).toInt()); showWorkHourPicker = false },
            onDismiss = { showWorkHourPicker = false },
        )
    }
    if (showOtDayPicker) {
        DayCountDialog(
            title = stringResource(R.string.site_pick_days),
            current = state.otMinutes,
            unitMinutes = otBase,
            onPick = { minutes -> vm.pickOtDays(minutes); showOtDayPicker = false },
            onDismiss = { showOtDayPicker = false },
        )
    }
    if (showOtHourPicker) {
        HourInputDialog(
            title = stringResource(R.string.site_pick_hours),
            initialHours = state.otMinutes.takeIf { m -> m > 0 }?.let { m -> m / 60.0 },
            onPick = { hours -> vm.pickOtHours((hours * 60).toInt()); showOtHourPicker = false },
            onDismiss = { showOtHourPicker = false },
        )
    }
    if (showStandardDialog) {
        state.project?.let { p ->
            fun hoursText(minutes: Int): String =
                if (minutes % 60 == 0) "${minutes / 60}"
                else String.format(java.util.Locale.US, "%.1f", minutes / 60.0)
            ProjectStandardDialog(
                initial = SiteProjectEditUi(
                    id = p.id,
                    name = p.name,
                    baseHoursText = hoursText(p.baseMinutes),
                    rateYuanText = Money.yuanText(p.dailyRateCents).replace(",", "").removeSuffix(".00"),
                    otMode = runCatching { SiteOtMode.valueOf(p.otMode) }.getOrDefault(SiteOtMode.BY_DAY),
                    otBaseHoursText = hoursText(p.otBaseMinutes),
                    otHourlyYuanText = if (p.otHourlyCents > 0) {
                        Money.yuanText(p.otHourlyCents).replace(",", "").removeSuffix(".00")
                    } else "",
                ),
                onApply = { baseHours, rateYuan, mode, otBaseHours, otHourlyYuan ->
                    vm.applyStandard(baseHours, rateYuan, mode, otBaseHours, otHourlyYuan)
                    showStandardDialog = false
                },
                onDismiss = { showStandardDialog = false },
            )
        }
    }
    if (showNoteEdit) {
        NoteDialog(initial = state.note, onDismiss = { showNoteEdit = false }, onConfirm = { vm.onNote(it); showNoteEdit = false })
    }
}

// ---- 借支内容 ----

@Composable
private fun AdvanceContent(
    state: SiteRecordUiState,
    vm: SiteRecordViewModel,
    onOpenProjectPick: () -> Unit,
    onOpenSettlement: () -> Unit,
    cashKind: Int,
    onCashKind: (Int) -> Unit,
) {
    var showNoteEdit by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMultiDate by remember { mutableStateOf(false) }

    Spacer(Modifier.height(Spacing.s))

    // 项目 + 日期：与记账页同一张组合卡片（共用组件）
    ProjectDateCard(
        state = state,
        onOpenProjectPick = onOpenProjectPick,
        onShowDatePicker = { showDatePicker = true },
        onShowMultiDate = { showMultiDate = true },
    )
    Spacer(Modifier.height(Spacing.m))

    // 借支 | 结算：与记账页「点工/包工」同位置、同款居中胶囊
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SegmentPill(stringResource(R.string.site_sub_advance), cashKind == 0) { onCashKind(0) }
                SegmentPill(stringResource(R.string.site_sub_settle), cashKind == 1) { onCashKind(1) }
            }
        }
    }
    Spacer(Modifier.height(Spacing.m))

    if (cashKind == 0) {
        // ---- 借支：本次金额（大字直填，样式同包工工钱） ----
        BigAmountRow(
            iconRes = R.drawable.ic_ms_paid,
            label = stringResource(R.string.site_advance_amount_label),
            value = state.advanceYuanText,
            fallback = "0.00",
            onValueChange = vm::onAdvanceAmount,
        ) {
            Text(
                stringResource(R.string.site_advance_yuan_unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.m))
    } else {
        // ---- 结算：本次结算金额（部分结算，像借支一样拿走一笔；结清入口见页面底部） ----
        SectionLabel(stringResource(R.string.site_settlement_partial_title))
        BigAmountRow(
            iconRes = R.drawable.ic_ms_paid,
            label = stringResource(R.string.site_settlement_partial_amount_label),
            value = state.settleAmountText,
            fallback = "0.00",
            onValueChange = vm::onSettleAmount,
        ) {
            Text(
                stringResource(R.string.site_yuan_symbol),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.m))
    }

    // 备注 + 照片：与记账页同一张组合卡片（共用组件）
    NotePhotoCard(
        note = state.advanceNote,
        onEditNote = { showNoteEdit = true },
        photos = state.photos,
        onAddPhotos = { vm.addPhotos(it) },
        onRemovePhoto = { vm.removePhoto(it) },
    )

    if (cashKind == 1) {
        // ---- 结清（原结算页主流程）：锁定全部未结算记录并归档快照，一次清零待结余额 ----
        Spacer(Modifier.height(Spacing.m))
        TonalRowCard(
            iconRes = R.drawable.ic_ms_flip,
            label = stringResource(R.string.site_settlement_settle_all),
            value = stringResource(R.string.site_settlement_settle_all_hint),
            onClick = onOpenSettlement,
        )
    }

    if (showDatePicker) {
        DatePick(initial = state.date, onPick = { vm.onDate(it) }, onDismiss = { showDatePicker = false })
    }
    if (showMultiDate) {
        MultiDateDialog(
            primary = state.date,
            selected = state.extraDates,
            onToggle = { vm.onToggleExtraDate(it) },
            onDismiss = { showMultiDate = false },
        )
    }
    if (showNoteEdit) {
        NoteDialog(
            initial = state.advanceNote,
            onDismiss = { showNoteEdit = false },
            onConfirm = { vm.onAdvanceNote(it); showNoteEdit = false },
        )
    }
}

// ---- M3E 组件 ----

/**
 * 大字直填行（工钱样式）：图标方块 + 标签 + 大数字输入 + 尾部控件。
 * fallback：未聚焦且未输入时的回显（如量×价自动值/0.00）。
 */
@Composable
internal fun BigAmountRow(
    iconRes: Int,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fallback: String = "",
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.m))
            BasicTextField(
                value = if (focused) value else value.ifEmpty { fallback },
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
            )
            trailing()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = Spacing.s),
    )
}

/** tonal 圆角卡片行（SectionCard 24dp）：图标方块 + 标签 + 值（大字可选）+ 徽标/尾注 + chevron */
@Composable
private fun TonalRowCard(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueLarge: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SectionCard(onClick = onClick) {
        TonalRow(
            iconRes = iconRes, label = label, value = value, valueColor = valueColor,
            valueLarge = valueLarge, badge = badge, trailingText = trailingText,
            trailingActive = trailingActive, onTrailing = onTrailing,
            showChevron = onClick != null,
        )
    }
}

/**
 * 大卡内的可点行（一张 SectionCard 内多行时用）：行自身带按压反馈与 chevron，
 * 与 TonalRowCard 共用同一套行渲染（TonalRow）。
 */
@Composable
private fun TappableTonalRow(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TonalRow(
        iconRes = iconRes, label = label, value = value, valueColor = valueColor,
        badge = badge, trailingText = trailingText, trailingActive = trailingActive,
        onTrailing = onTrailing, showChevron = true,
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(Radius.button))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = Spacing.xs),
    )
}

/** 行内容：图标 tonal 方块 + 标签 + 值 + 徽标/尾注 + chevron */
@Composable
private fun TonalRow(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueLarge: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailingText: String = "",
    trailingActive: Boolean = false,
    onTrailing: (() -> Unit)? = null,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标 tonal 方块（03 §3.4）
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes), null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.m))
        Text(
            value,
            style = if (valueLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (valueLarge) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            maxLines = if (valueLarge) 1 else 2,
            modifier = Modifier.weight(1f),
        )
        badge?.invoke()
        if (trailingText.isNotEmpty()) {
            if (onTrailing != null) {
                val trailInteraction = remember { MutableInteractionSource() }
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trailingActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .pressScale(trailInteraction, pressedScale = 0.92f)
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(interactionSource = trailInteraction, indication = LocalIndication.current, onClick = onTrailing)
                        .padding(horizontal = Spacing.s, vertical = 6.dp),
                )
            } else {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(Spacing.xs))
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 分 → 短元文本：整数去小数（260），非整数两位小数（260.50） */
internal fun yuanShort(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else String.format(java.util.Locale.US, "%.2f", cents / 100.0)

/**
 * 工钱 hero 卡：primaryContainer 底色突出页面主角；¥ + 大字数字带滚动动画
 * （金额增加时新值自下滑入、旧值上滑淡出，减少则反向）；尾注为点工标准摘要；点击弹标准设置。
 */
@Composable
private fun PayHeroCard(
    previewCents: Long,
    summary: String,
    onClick: () -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    SectionCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ms_paid), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                stringResource(R.string.site_pay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.m))
            AnimatedContent(
                targetState = previewCents,
                transitionSpec = {
                    val up = targetState > initialState
                    (
                        slideInVertically(spatialSpec) { if (up) it else -it } + fadeIn(effectsSpec)
                        ) togetherWith (
                        slideOutVertically(spatialSpec) { if (up) -it else it } + fadeOut(effectsSpec)
                        )
                },
                label = "payHeroAmount",
            ) { cents ->
                Text(
                    stringResource(R.string.site_yuan_symbol) + " " + Money.yuanText(cents).replace(",", ""),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 选择瓦片：M3E 弹簧缩放 + tonal 变色 */
@Composable
private fun SelectTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileBg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "tileScale",
    )
    Box(
        modifier
            .scale(scale)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.button))
            .background(bgColor)
            .pressScale(interaction, pressedScale = 0.92f)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fgColor,
        )
        // 选中角标：右上角小对勾，强化互斥选中确认感
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_ms_check), null,
                tint = fgColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
private fun PlannedSegment(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = Spacing.l, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(4.dp))
            BadgePill(
                stringResource(R.string.site_planned),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgePill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .padding(start = Spacing.s)
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---- 对话框 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiDateDialog(
    primary: LocalDate,
    selected: List<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val days = (1..today.dayOfMonth).map { today.withDayOfMonth(it) }.filter { it != primary }
        SiteBottomSheet(title = stringResource(R.string.site_multi_select), onDismiss = onDismiss) {
            Text(
                stringResource(R.string.site_multi_select_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { d ->
                    FilterChip(
                        selected = d in selected,
                        onClick = { onToggle(d) },
                        label = { Text("${d.dayOfMonth}") },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.site_dialog_ok))
            }
        }
}

/** 数值 + 单位文案：整数不带小数（如 2），非整数一位小数（如 2.5）；在 Composable 上下文调用 */
@Composable
private fun fmtAmount(v: Double, labelRes: Int): String =
    stringResource(labelRes, if (v % 1.0 == 0.0) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v))

/** 选工天弹窗：同记加班的时长网格（预设 0.5–3 工 + 末位“…”自定义格），点格即生效并关窗 */
@Composable
private fun DayCountDialog(
    title: String,
    current: Int,
    unitMinutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SiteBottomSheet(title = title, onDismiss = onDismiss) {
        DurationGrid(
            selectedHours = current.takeIf { it > 0 }?.let { it / unitMinutes.toDouble() },
            presetSteps = 6,
            onPreset = { onPick((it * unitMinutes).toInt()) },
            onCustomCommit = { text ->
                text.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { onPick((it * unitMinutes).toInt()) }
            },
        )
    }
}

@Composable
private fun HourInputDialog(
    title: String,
    initialHours: Double?,
    onPick: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // 复用记加班弹窗的时长网格（预设 0.5–24 + 末位“…”自定义输入格），点格即生效并关窗
    SiteBottomSheet(title = title, onDismiss = onDismiss) {
        DurationGrid(
            selectedHours = initialHours?.takeIf { it > 0.0 },
            onPreset = onPick,
            onCustomCommit = { text -> text.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onPick) },
        )
    }
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    SiteBottomSheet(title = stringResource(R.string.site_note), onDismiss = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(100) },
            placeholder = { Text(stringResource(R.string.site_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.textField),
        )
        Spacer(Modifier.height(Spacing.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_cancel))
            }
            Button(onClick = { onConfirm(text) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_ok))
            }
        }
    }
}

// ---- 照片区（PickVisualMedia 免权限；本地保存，备份包不含，D10） ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSection(photos: List<String>, onAdd: (List<String>) -> Unit, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val dir = File(context.filesDir, "site_photos").apply { mkdirs() }
        val saved = uris.mapIndexedNotNull { i, uri ->
            runCatching {
                val dest = File(dir, "p_${System.currentTimeMillis()}_$i.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                dest.absolutePath
            }.getOrNull()
        }
        onAdd(saved)
    }
    var viewing by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        photos.forEach { path ->
            // 单击看大图，长按删除
            PhotoThumb(path = path, onClick = { viewing = path }, onLong = { onRemove(path) })
        }
        val addInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(76.dp)
                .pressScale(addInteraction, pressedScale = 0.92f)
                .clip(RoundedCornerShape(Radius.button))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.button))
                .clickable(interactionSource = addInteraction, indication = LocalIndication.current) {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_ms_add), null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.site_add_photo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 留证角标（tonal 胶囊）
            Text(
                stringResource(R.string.site_photo_evidence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }

    viewing?.let { path ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = {},
            text = {
                val bmp = remember(path) {
                    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewing = null; onRemove(path) }) {
                    Text(stringResource(R.string.site_delete), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun PhotoThumb(path: String, onClick: () -> Unit, onLong: () -> Unit) {
    val bitmap = remember(path) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(76.dp)
            .pressScale(interaction, pressedScale = 0.92f)
            .clip(RoundedCornerShape(Radius.button))
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLong,
            ),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---- 包工/工量表单（Phase 2，D4-rev：量价齐自动算钱，否则直填） ----

/**
 * 工量单位候选（v0.6.2 D-单位扩展）：非 Composable 数据用 labelRes 携带，
 * 显示点 stringResource 解析（硬规则 10）。
 */
private val pieceUnitOptions = listOf(
    R.string.site_unit_sq_meter,
    R.string.site_unit_cubic,
    R.string.site_unit_ton,
    R.string.site_unit_meter,
    R.string.site_unit_inch,
    R.string.site_unit_piece,
    R.string.site_unit_time,
    R.string.site_unit_day,
    R.string.site_unit_block,
    R.string.site_unit_group,
    R.string.site_unit_machine,
    R.string.site_unit_bundle,
    R.string.site_unit_case,
    R.string.site_unit_item,
    R.string.site_unit_plant,
    R.string.site_unit_household,
    R.string.site_unit_car,
    R.string.site_unit_sheet,
    R.string.site_unit_other,
)

@Composable
private fun PieceForm(state: SiteRecordUiState, vm: SiteRecordViewModel, onOpenUnitSheet: () -> Unit) {
    val directCents = Money.parseYuanToCents(state.pieceAmountText) ?: 0
    // 量价齐自动：仅在未手填工钱时生效（手填工钱直接在下方工钱数字上编辑）
    val autoAmount = directCents <= 0 && state.pieceQuantityMilli > 0 &&
        (Money.parseYuanToCents(state.piecePriceText) ?: 0) > 0

    // ---- 工程量：大字直填 + 右侧单位按钮（点击弹底部弹层选单位） ----
    BigAmountRow(
        iconRes = R.drawable.ic_ms_dashboard,
        label = stringResource(R.string.site_piece_quantity),
        value = state.pieceQuantityText,
        fallback = "0.00",
        onValueChange = vm::onPieceQuantity,
    ) { UnitPickButton(current = state.pieceUnit, onClick = onOpenUnitSheet) }
    Spacer(Modifier.height(Spacing.s))

    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_piece_price),
        value = state.piecePriceText,
        fallback = "0.00",
        onValueChange = vm::onPiecePrice,
    ) {
        Text(
            stringResource(R.string.site_piece_price_unit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Spacing.m))

    // 工钱：预览数字本身可编辑（样式不变）：点大字直接键入手填工钱；
    // 未填时展示量×价自动值，清空后回到自动
    BigAmountRow(
        iconRes = R.drawable.ic_ms_paid,
        label = stringResource(R.string.site_pay),
        value = state.pieceAmountText,
        fallback = Money.yuanText(state.piecePreviewCents()).replace(",", ""),
        onValueChange = vm::onPieceAmount,
    ) {
        if (autoAmount) {
            Text(
                stringResource(R.string.site_piece_auto_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 单位按钮：显示当前单位 + 下拉箭头，点击打开自实现底部弹层 */
@Composable
private fun UnitPickButton(current: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(44.dp)
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(RoundedCornerShape(Radius.textField))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            current,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            painterResource(R.drawable.ic_ms_expand_more), null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 工量单位选择底部弹层：与选工天/选小时同款 SiteBottomSheet 容器（同款滑入动画/面板结构/取消确定），
 * 内容为现有单位候选（点选暂存，确定生效）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitPickerSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf(current) }
    SiteBottomSheet(title = stringResource(R.string.site_unit_picker_title), onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            pieceUnitOptions.forEach { labelRes ->
                val unit = stringResource(labelRes)
                SegmentPill(unit, selected = unit == pending) { pending = unit }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_cancel))
            }
            Button(onClick = { onSelect(pending) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.site_dialog_ok))
            }
        }
    }
}

/** 记工页通用底部弹层容器（自实现：遮罩 + 顶部圆角面板 + motionScheme 滑入滑出；全页弹窗统一走此容器） */
@Composable
internal fun SiteBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 动画/结构完全对齐其它模式的「记加班」弹窗（feature/record/RecordSheet.kt）
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true
    var dismissRequested by remember { mutableStateOf(false) }

    fun requestDismiss() { dismissRequested = true }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visibleState.targetState = false
            while (!visibleState.isIdle) delay(16)
            onDismiss()
        }
    }
    BackHandler(onBack = { requestDismiss() })

    if (!dismissRequested || !visibleState.isIdle) {
        Dialog(
            onDismissRequest = { requestDismiss() },
            // decorFitsSystemWindows=false：IME insets 走 Compose 侧，imePadding 精准避让，
            // 否则弹窗窗口被系统 pan/双重补偿，输入法弹出后输入框与键盘间留大片空白
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(tween(Duration.slow)) { it },
                exit = slideOutVertically(tween(Duration.normal)) { it } + fadeOut(tween(Duration.normal)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // 遮罩
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
                    )
                    // 面板：宽屏四角全圆 + 底部留边（悬浮形态）；窄屏全宽贴底
                    val wide = LocalWindowSpec.current == WindowSpec.EXPANDED
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = AdaptiveSpecs.sheetMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                            .padding(bottom = if (wide) Spacing.l else 0.dp)
                            .clip(
                                if (wide) RoundedCornerShape(Radius.sheet)
                                else RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(enabled = false) { }
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(Spacing.l),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.m))
                        content()
                    }
                }
            }
        }
    }
}
