package com.mdot.app.feature.site

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.mdot.app.feature.record.rememberSaveWithHaptic
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.DatePick
import com.mdot.app.core.designsystem.component.LocalSheetBackdropState
import com.mdot.app.core.designsystem.component.SegmentBar
import com.mdot.app.core.designsystem.component.SheetBackdropLayer
import com.mdot.app.core.designsystem.component.ShrinkFeedbackButton
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.navigation.bottomBarContentPaddingValues
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 工地记工页（12 文档 F-S3/F-S5；M3 Expressive 重设计版式）：
 * tonal 胶囊分段顶 Tab（记账 | 记借支·结算）+ 24dp 圆角卡片行（项目/日期/工钱/备注）+
 * 弹簧缩放选择瓦片（上班四态/加班三态/上下午）+ 大字工钱 + 照片留证瓦片 + 胶囊主按钮。
 * 动效统一 MaterialTheme.motionScheme 弹簧 specs，按压反馈 pressScale。
 *
 * 本文件只保留页面骨架（顶栏/项目日期固定区/表单分页/悬浮保存）；
 * 四表单见 SiteRecordForms，弹层见 SiteRecordSheets，行与瓦片组件见 SiteRecordComponents，
 * 备注照片区见 SiteRecordPhotoSection，底部弹层容器见 SiteBottomSheet。
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

    // 表单分页（扁平 4 页）：0=记账·点工 1=记账·包工 2=记借支·借支 3=记借支·结算。
    // 手势分区：顶栏横滑/点选切上级页签（记账↔记借支/结算）；内容区横滑切子页签，
    // 子页签尽头继续滑由扁平序列自然联动上级（包工→借支、借支→包工）
    val formPager = rememberPagerState(pageCount = { 4 })
    var showUnitSheet by remember { mutableStateOf(false) }
    // 日期选择弹窗：项目+日期卡上移为固定区（四种表单共用），弹窗随之提升到弹层级
    var showDatePicker by remember { mutableStateOf(false) }
    var showMultiDate by remember { mutableStateOf(false) }
    // 保存反馈：单颗「保存」＝原地保存并留在本页（原「保存 并再记一笔」行为），
    // 反馈动效＝按钮对称收缩成 ✓ 圆钮再展开（与工资页保存同款，实现在 ShrinkFeedbackButton）
    var saveFlash by remember { mutableStateOf(false) }
    val saveHaptic = rememberSaveWithHaptic()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) { if (state.error != null) saveFlash = false }
    // ✓ 收起：本页不关闭，必须自行回退（停留一拍给足确认感）
    LaunchedEffect(saveFlash) {
        if (saveFlash) {
            delay(900)
            saveFlash = false
        }
    }
    // motionScheme 仅 composable 可调用：先取 spec 再传入 transitionSpec（非 Composable 上下文）
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // 保存分发：记账·点工 / 记账·包工 / 借支 / 结算（本次结算金额）四种表单（悬浮保存按钮使用）
    fun saveCurrent(keepOpen: Boolean, onDone: () -> Unit = {}) {
        when (formPager.currentPage) {
            1 -> vm.savePieceWork(keepOpen, onDone)
            2 -> vm.saveAdvance(keepOpen, onDone)
            3 -> vm.saveSettlementAmount(keepOpen, onDone)
            else -> vm.saveAttendance(keepOpen, onDone)
        }
    }

    /** 顶栏横滑/点选切换上级页签：记账→记借支落「借支」，记借支→记账保持当前子页 */
    fun switchTop(target: Int) {
        val targetPage = if (target == 1) 2 else minOf(formPager.currentPage, 1)
        scope.launch { formPager.animateScrollToPage(targetPage) }
    }

    /** 保存（原地保存并留在本页继续记）+ 反馈：触觉 + 收缩成 ✓ 再展开 */
    fun saveWithFeedback() {
        saveHaptic()
        saveFlash = true
        saveCurrent(keepOpen = true)
    }

    // 弹层背景层：本页所有底部弹层（多选日期/工量单位/选工天/选小时/备注/点工标准）
    // 都是独立窗口的 Dialog，状态经 LocalSheetBackdropState 下发与背景层共享、进出场同拍
    val sheetVisible = remember { MutableTransitionState(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CompositionLocalProvider(LocalSheetBackdropState provides sheetVisible) {
            // 背景层只包「会被弹层压住的页面内容」（顶部固定区/表单分页/悬浮保存）——
            // 弹层是独立窗口，留在层外更清晰（放里面也无影响，但语义上它们不属于背景）
            SheetBackdropLayer(visible = sheetVisible.targetState) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.page),
                ) {
                    RecordHeader(formPager, onBack = onBack, onSwitchTop = ::switchTop)

                    // 项目 + 日期：固定区（四种表单共用，横滑手势只发生在其下方）
                    ProjectDateCard(
                        state = state,
                        onOpenProjectPick = onOpenProjectPick,
                        onShowDatePicker = { showDatePicker = true },
                        onShowMultiDate = { showMultiDate = true },
                    )
                    Spacer(Modifier.height(Spacing.s))

                    // 子页签滑条：跟随表单分页（记账→点工/包工；记借支/结算→借支/结算），跨界时整组切换
                    val p = formPager.currentPage + formPager.currentPageOffsetFraction
                    val topTab = if (formPager.currentPage >= 2) 1 else 0
                    val subPos = (if (topTab == 0) p else p - 2f).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SegmentBar(
                            labels = if (topTab == 0) listOf(
                                stringResource(R.string.site_sub_day),
                                stringResource(R.string.site_sub_piece),
                            ) else listOf(
                                stringResource(R.string.site_sub_advance),
                                stringResource(R.string.site_sub_settle),
                            ),
                            selected = formPager.currentPage % 2,
                            onSelect = { target ->
                                scope.launch { formPager.animateScrollToPage(topTab * 2 + target) }
                            },
                            segWidth = 112.dp,
                            position = subPos,
                        )
                    }
                    Spacer(Modifier.height(Spacing.s))

                    // 表单分页：内容区横滑切子页签；子页签尽头继续滑自然联动上级页签（包工→借支、借支→包工）
                    HorizontalPager(
                        state = formPager,
                        modifier = Modifier.weight(1f),
                        beyondViewportPageCount = 3,
                    ) { page ->
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
                            } else when (page) {
                                0 -> AttendanceForm(state, vm)
                                1 -> PieceForm(state, vm, onOpenUnitSheet = { showUnitSheet = true })
                                2 -> AdvanceForm(state, vm)
                                else -> SettleForm(state, vm, onOpenSettlement)
                            }
                            // 表单与备注/照片卡之间留间距（此前缺失导致卡片贴边重叠观感）
                            Spacer(Modifier.height(Spacing.s))
                            val onCashPage = page >= 2
                            NotePhotoSection(
                                note = if (onCashPage) state.advanceNote else state.note,
                                onNote = { if (onCashPage) vm.onAdvanceNote(it) else vm.onNote(it) },
                                photos = state.photos,
                                onAddPhotos = { vm.addPhotos(it) },
                                onRemovePhoto = { vm.removePhoto(it) },
                            )
                            // 底部操作条避让：用全局标准值（底栏高 + 边距 + 导航条），且必须位于滚动内容内部
                            Spacer(Modifier.height(bottomBarContentPaddingValues().calculateBottomPadding()))
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.l)
                        .padding(bottom = Spacing.m),
                ) {
                    // 单颗「保存」：尺寸对齐记月「同步本月考勤」（48dp 高 / 24dp 内边距 / 最小宽 144dp）；
                    // 行为＝原「保存 并再记一笔」（原地保存并留在本页继续记）；反馈动效与工资页保存同款
                    ShrinkFeedbackButton(
                        text = stringResource(R.string.site_record_save),
                        busy = saveFlash,
                        onClick = ::saveWithFeedback,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- 弹层（独立窗口：DatePick 是 M3 DatePickerDialog，其余走 SiteBottomSheet）----
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
        }
    }
}

// ---- 顶栏：tonal 圆形返回 + 胶囊分段 ----

@Composable
private fun RecordHeader(formPager: PagerState, onBack: () -> Unit, onSwitchTop: (Int) -> Unit) {
    val backInteraction = remember { MutableInteractionSource() }
    var dragX by remember { mutableStateOf(0f) }
    // 顶栏整行响应横滑：左滑切下一上级页签、右滑切上一上级页签（子页签上方的手势分区）
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.m)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX <= -60) onSwitchTop(1) else if (dragX >= 60) onSwitchTop(0)
                        dragX = 0f
                    },
                ) { change, _ ->
                    dragX += change.positionChange().x
                    change.consume()
                }
            },
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
                modifier = Modifier.size(IconSpec.boxed),
            )
        }
        // 分段胶囊：滑块连续跟随表单分页（顶栏整行手势已负责横滑切上级，此处不再重复挂手势）
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(Spacing.xs),
        ) {
            SegmentBar(
                labels = listOf(
                    stringResource(R.string.site_tab_book),
                    stringResource(R.string.site_tab_advance_settle),
                ),
                selected = if (formPager.currentPage >= 2) 1 else 0,
                onSelect = onSwitchTop,
                segWidth = 112.dp,
                position = ((formPager.currentPage + formPager.currentPageOffsetFraction) - 1f).coerceIn(0f, 1f),
            )
        }
    }
}
