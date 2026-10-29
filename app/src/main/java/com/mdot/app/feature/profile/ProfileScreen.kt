package com.mdot.app.feature.profile

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.canhub.cropper.CropImageView

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.ProfileSpec

import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.KeyValue
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.designsystem.component.pressScale
import com.mdot.app.core.designsystem.component.rememberHomeDataAreaHeight
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.Routes
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.record.rememberSaveWithHaptic
import com.mdot.app.feature.settings.SettingsHubViewModel
import com.mdot.app.feature.settings.appearanceSummary
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanButtonSize
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import com.mdot.app.core.designsystem.jiabanFilterChipColors
import androidx.compose.runtime.DisposableEffect

/**
 * 我的页：资料卡（头像可换/昵称可改）+ 本年数据摘要 + 常用入口。
 * 顶栏由 AppRoot 的固定一级顶栏统一提供，这里只做顶部避让。
 */
@Composable
fun ProfileScreen(
    canBack: Boolean = false,
    onBack: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
    hub: SettingsHubViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val avatarPath by vm.avatarPath.collectAsStateWithLifecycle()
    val workSystem by vm.workSystem.collectAsStateWithLifecycle()
    val siteSummary by vm.siteSummary.collectAsStateWithLifecycle()
    val appearance by hub.appearance.collectAsStateWithLifecycle()
    val syncStatus by hub.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 资料区的唯一编辑入口 = 「编辑资料」弹窗（头像/昵称/签名都在里面）；
    // 两引擎同一套版式（铺在页面背景上、与首页数据区同高，docs/03 §5.5）
    var editProfile by remember { mutableStateOf(false) }

    // T2-2：长按重置头像触觉（docs/15）
    val saveHaptic = rememberSaveWithHaptic()
    val motto by vm.motto.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var editingMotto by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var nameOverLimit by remember { mutableStateOf(false) }
    LaunchedEffect(editingName) {
        if (editingName) {
            nameText = nickname
            nameOverLimit = false
        }
    }

    // 头像裁剪：选图 → 全屏 Compose 裁剪（1:1 固定、不拉伸、压缩保存；操作栏在底部 MD3 样式）
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        cropUri = uri
    }

    // 自定义头像（复制到私有目录后按路径解码）；无则用内置默认头像
    val custom = avatarPath?.let { p ->
        remember(p) { runCatching { BitmapFactory.decodeFile(p) }.getOrNull() }
    }
    val avatarPainter: Painter =
        if (custom != null) BitmapPainter(custom.asImageBitmap())
        else painterResource(R.drawable.default_avatar)

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = !canBack)
            .padding(horizontal = Spacing.page),
    ) {
        // 顶部避让与首页完全一致（statusBar + 56dp + Spacing.s），这样下面的资料区与首页
        // 「数据区」同一块区域、后面的卡片才能与首页「收入卡」同 y（docs/03 §5.5）
        Spacer(Modifier.height(statusBar + TopBarHeight + Spacing.s))

        // ---- 资料区（铺在页面背景上，高度对齐首页数据区；整块可点 → 编辑资料）----
        // 不加行尾箭头（用户 2026-10-23 定）：整块仍可点，但视觉上与首页数据区一致、不加可点暗示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rememberHomeDataAreaHeight())
                // 涟漪跟着**卡片圆角**走（`Radius.card`：MD3 24dp / MIUIX 16dp）——资料区虽不包卡片，
                // 但它的容器与下面那几张卡是同一套圆角，点下去的涟漪不该是直角矩形。
                // ⚠️ `clip` 必须在 `clickable` **之前**（否则涟漪不被裁）；这里区高 ≈130dp ≫ 半径 24dp，
                // 不会像 docs/11 088 那样把内容切掉（那里的判定是「区高 ≫ 圆角半径」，本条满足）。
                .clip(engineShape(Radius.card))
                .clickable { editProfile = true }
                .padding(vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = avatarPainter,
                contentDescription = stringResource(R.string.profile_avatar_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ProfileSpec.heroAvatar).clip(CircleShape),
            )
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(
                    nickname,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    motto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 与首页「数据区 → 收入卡」的间距一致（l）：本年数据卡因此落在收入卡的同一高度
        Spacer(Modifier.height(Spacing.l))

        // ---- 本年数据（按工时制度切换：工地记工=工天/完工项目/工钱，其余=加班/天数/调休） ----
        SectionCard {
            if (workSystem == WorkSystem.SITE) {
                Row {
                    StatCell(
                        label = stringResource(R.string.profile_site_days_label),
                        value = stringResource(R.string.profile_days_value, siteSummary.yearWorkDays),
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { onOpen(Routes.stats(0)) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.profile_site_projects_label),
                        value = stringResource(R.string.profile_count_value, siteSummary.completedProjects),
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { onOpen(Routes.SITE_PROJECTS) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.profile_site_pay_label),
                        value = stringResource(R.string.profile_yuan_value, Money.yuanText(siteSummary.yearPayCents)),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { onOpen(Routes.stats(0)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row {
                    StatCell(
                        label = stringResource(R.string.profile_year_ot_label),
                        value = stringResource(R.string.profile_hours_value, TimeUtils.hoursDecimal(summary.yearOtMinutes)),
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { onOpen(Routes.stats(0)) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.profile_year_days_label),
                        value = stringResource(R.string.profile_days_value, summary.yearDays),
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { onOpen(Routes.CALENDAR) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.profile_comp_balance_label),
                        // 余额为 0 时写「0」而不是 TimeUtils 给的「0分」（读着怪）
                        value = if (summary.compBalanceMinutes == 0) "0"
                        else if (summary.compBalanceMinutes < 0) "-" + TimeUtils.prettyDuration(-summary.compBalanceMinutes)
                        else TimeUtils.prettyDuration(summary.compBalanceMinutes),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { onOpen(Routes.systemTab("COMP")) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 常用入口 ----
        SectionCard {
            Column {
                SettingRow(
                    stringResource(R.string.profile_entry_sync),
                    if (syncStatus.configured) stringResource(R.string.profile_sync_configured) else stringResource(R.string.profile_sync_unconfigured),
                    painterResource(R.drawable.ic_ms_cloud_sync), onClick = { onOpen(Routes.SYNC) },
                )
                SettingRow(
                    stringResource(R.string.profile_entry_appearance), appearanceSummary(context, appearance),
                    painterResource(R.drawable.ic_ms_palette), onClick = { onOpen(Routes.APPEARANCE) },
                )
                SettingRow(
                    stringResource(R.string.profile_entry_about), null,
                    painterResource(R.drawable.ic_ms_shield), onClick = { onOpen(Routes.ABOUT) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }

    // ---- 编辑资料（MIUIX 行式资料卡的唯一入口）----
    if (editProfile) {
        ProfileEditDialog(
            hasCustomAvatar = avatarPath != null,
            onPickAvatar = {
                editProfile = false
                pickImage.launch("image/*")
            },
            onResetAvatar = {
                saveHaptic()
                vm.resetAvatar()
            },
            onEditNickname = {
                editProfile = false
                editingName = true
            },
            onEditMotto = {
                editProfile = false
                editingMotto = true
            },
            onDismiss = { editProfile = false },
        )
    }

    // ---- 昵称编辑对话框 ----
    if (editingMotto) {
        MottoDialog(
            current = motto,
            onSave = { vm.setMotto(it); editingMotto = false },
            onDismiss = { editingMotto = false },
        )
    }

    if (editingName) {
        JiabanAlertDialog(containerColor = dialogContainerColor(), 
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.profile_edit_nickname)) },
            text = {
                FloatingLabelTextField(
                    value = nameText,
                    onValueChange = { raw ->
                        nameOverLimit = raw.length > 24
                        nameText = raw.take(24)
                    },
                    label = stringResource(R.string.profile_nickname_label),
                    isError = nameOverLimit,
                    supportingText = {
                        if (nameOverLimit) {
                            Text(stringResource(R.string.profile_nickname_limit), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            },
            confirmButton = {
                JiabanButton(
                    text = stringResource(R.string.profile_save),
                    onClick = {
                    if (!nameOverLimit) {
                        vm.setNickname(nameText)
                        editingName = false
                    }
                },
                    role = JiabanButtonRole.GHOST,
                )
            },
            dismissButton = {
                JiabanButton(
                    text = stringResource(R.string.profile_cancel),
                    onClick = { editingName = false },
                    role = JiabanButtonRole.GHOST,
                )
            },
        )
    }

    // ---- 全屏头像裁剪（MD3 + 底部操作栏） ----
    cropUri?.let { uri ->
        AvatarCropDialog(
            sourceUri = uri,
            onCancel = { cropUri = null },
            onDone = { cropUri = null },
            vm = vm,
        )
    }
}

/**
 * 「编辑资料」弹窗（MIUIX 行式资料卡的入口）：把头像/昵称/签名三个入口收进一个弹层。
 *
 * 行用共享 [SettingRow]（两引擎同一结构：MIUIX = 库 ArrowPreference、MD3 = 自绘行）；
 * 弹窗走页内模态覆盖层（硬规则 14）。本弹窗**没有确认键** ⇒ `confirmButton = null`
 * （不能传空 lambda：MIUIX 卡片会按「槽在不在」排版，空槽会留出半格空白 + 孤立竖线）。
 */
@Composable
private fun ProfileEditDialog(
    hasCustomAvatar: Boolean,
    onPickAvatar: () -> Unit,
    onResetAvatar: () -> Unit,
    onEditNickname: () -> Unit,
    onEditMotto: () -> Unit,
    onDismiss: () -> Unit,
) {
    JiabanAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    stringResource(R.string.profile_pick_from_gallery),
                    icon = painterResource(R.drawable.ic_ms_photo_camera),
                    onClick = onPickAvatar,
                )
                if (hasCustomAvatar) {
                    SettingRow(
                        stringResource(R.string.profile_reset_avatar),
                        icon = painterResource(R.drawable.ic_ms_person),
                        onClick = onResetAvatar,
                    )
                }
                SettingRow(
                    stringResource(R.string.profile_edit_nickname),
                    icon = painterResource(R.drawable.ic_ms_edit),
                    onClick = onEditNickname,
                )
                SettingRow(
                    stringResource(R.string.profile_motto_title),
                    icon = painterResource(R.drawable.ic_ms_auto_awesome),
                    onClick = onEditMotto,
                )
            }
        },
        confirmButton = null,
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.profile_close),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}

/**
 * 全屏头像裁剪（MD3 自绘）：嵌入 CanHub CropImageView 引擎，顶部返回栏 + 底部操作栏
 * （旋转 / 翻转 / 裁切）。固定 1:1、压缩 JPEG 由库直写目标文件。
 */
@Composable
private fun AvatarCropDialog(
    sourceUri: android.net.Uri,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    vm: ProfileViewModel,
) {
    var ready by remember { mutableStateOf(false) }
    var cropper by remember { mutableStateOf<CropImageView?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize()) {
                // MD3 顶栏
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .statusBarsPadding().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onCancel) {
                            Icon(painterResource(R.drawable.ic_ms_close), contentDescription = stringResource(R.string.profile_close), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(R.string.profile_crop_avatar_title), style = MaterialTheme.typography.titleMedium)
                    }
                }
                // 裁剪区：AndroidView 必须内联于此 Box，才能正确参与布局
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            CropImageView(ctx).apply {
                                setImageUriAsync(sourceUri)
                                setAspectRatio(1, 1)
                                setFixedAspectRatio(true)
                                guidelines = CropImageView.Guidelines.ON
                                cropShape = CropImageView.CropShape.RECTANGLE
                                setOnSetImageUriCompleteListener { _, _, err ->
                                    ready = err == null
                                }
                                setOnCropImageCompleteListener { _, result ->
                                    if (result.error == null) {
                                        vm.confirmAvatarSaved()
                                        onDone()
                                    }
                                }
                            }
                        },
                        update = { view -> cropper = view },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // MD3 底部操作栏
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.m),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionIconButton(painterResource(R.drawable.ic_ms_rotate_90_degrees_ccw), stringResource(R.string.profile_rotate)) {
                            cropper?.rotateImage(90)
                        }
                        ActionIconButton(painterResource(R.drawable.ic_ms_flip), stringResource(R.string.profile_flip)) {
                            cropper?.flipImageHorizontally()
                        }
                        Button(
                            onClick = {
                                cropper?.croppedImageAsync(
                                    android.graphics.Bitmap.CompressFormat.JPEG,
                                    88,
                                    512, 512,
                                    CropImageView.RequestSizeOptions.RESIZE_FIT,
                                    vm.prepareAvatarTarget(),
                                )
                            },
                            enabled = ready,
                        ) { Text(stringResource(R.string.profile_crop_apply)) }
                    }
                }
            }
        }
    }
}

/**
 * 「我的」页数据条里的一格：**可点**（去统计 / 日历 / 调休余额等）。
 * 样式对齐共用组件 `KeyValue`（labelMedium 降色 + titleMedium 值）。
 * 刻意**不带箭头**（用户 2026-09-23 定：能点就行，不用引导）；靠 `pressScale` 的按压反馈表达可点。
 */
@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

/**
 * 个性签名弹窗：**选项优先**（硬规则 12）——先给几个预设，想写自己的再选「自定义」。
 * 保存键与其它弹窗同款（实心 primary pill）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MottoDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(
        stringResource(R.string.profile_motto),
        stringResource(R.string.profile_motto_preset_2),
        stringResource(R.string.profile_motto_preset_3),
        stringResource(R.string.profile_motto_preset_4),
    )
    // ⚠️ 必须自己记住「选了哪个预设」：先前 selected 拿的是外部传入的 current（弹窗开着时不会变），
    // 点 chip 等于没记录 → 看着像点不动、保存也还是旧值（用户 2026-09-23 反馈）
    var picked by remember { mutableStateOf(current) }
    var custom by remember { mutableStateOf(current !in presets) }
    var text by remember { mutableStateOf(if (current in presets) "" else current) }
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_motto_title)) },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    presets.forEach { p ->
                        FilterChip(
                            colors = jiabanFilterChipColors(),
                            selected = !custom && picked == p,
                            onClick = { custom = false; picked = p },
                            label = { Text(p, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                    FilterChip(
                        colors = jiabanFilterChipColors(),
                        selected = custom,
                        onClick = { custom = true },
                        label = {
                            Text(
                                stringResource(R.string.profile_motto_custom),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
                if (custom) {
                    Spacer(Modifier.height(Spacing.m))
                    FloatingLabelTextField(
                        value = text,
                        onValueChange = { text = it.take(24) },
                        label = stringResource(R.string.profile_motto_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.profile_save),
                onClick = { onSave(if (custom) text else picked) },
                enabled = !custom || text.isNotBlank(),
                role = JiabanButtonRole.PRIMARY,
                size = JiabanButtonSize.M,
            )
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.profile_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}

@Composable
private fun ActionIconButton(icon: Painter, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(engineShape(Radius.button))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
