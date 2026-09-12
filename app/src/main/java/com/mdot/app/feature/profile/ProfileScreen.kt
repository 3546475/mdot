package com.mdot.app.feature.profile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.canhub.cropper.CropImageView

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.mdot.app.core.designsystem.Radius

import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.KeyValue
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.designsystem.component.TopBarHeight
import com.mdot.app.core.navigation.Routes
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.domain.model.WorkSystem
import com.mdot.app.domain.util.Money
import com.mdot.app.domain.util.TimeUtils
import com.mdot.app.feature.settings.SettingsHubViewModel
import com.mdot.app.feature.settings.appearanceSummary
import kotlinx.coroutines.launch

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

    var avatarMenu by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
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
        Spacer(Modifier.height(statusBar + TopBarHeight + Spacing.m))

        // ---- 资料卡 ----
        SectionCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.s))

                // 头像：点击弹更换菜单；长按直接恢复默认头像
                Box {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .combinedClickable(
                                onClick = { avatarMenu = true },
                                onLongClick = {
                                    if (avatarPath != null) vm.resetAvatar()
                                },
                            ),
                    ) {
                        Image(
                            painter = avatarPainter,
                            contentDescription = stringResource(R.string.profile_avatar_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // 右下角小相机角标提示可换
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(R.drawable.ic_ms_photo_camera), contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = avatarMenu,
                        onDismissRequest = { avatarMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_pick_from_gallery)) },
                            onClick = {
                                avatarMenu = false
                                pickImage.launch("image/*")
                            },
                        )
                        if (avatarPath != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_reset_avatar)) },
                                onClick = {
                                    avatarMenu = false
                                    vm.resetAvatar()
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.m))

                // 昵称：点击可编辑
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { editingName = true },
                ) {
                    Text(
                        nickname,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_ms_edit), contentDescription = stringResource(R.string.profile_edit_nickname),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.profile_motto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
            }
        }

        Spacer(Modifier.height(Spacing.m))

        // ---- 本年数据（按工时制度切换：工地记工=工天/完工项目/工钱，其余=加班/天数/调休） ----
        SectionCard {
            if (workSystem == WorkSystem.SITE) {
                Row {
                    KeyValue(
                        stringResource(R.string.profile_site_days_label),
                        stringResource(R.string.profile_days_value, siteSummary.yearWorkDays),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                    KeyValue(
                        stringResource(R.string.profile_site_projects_label),
                        stringResource(R.string.profile_count_value, siteSummary.completedProjects),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                    KeyValue(
                        stringResource(R.string.profile_site_pay_label),
                        stringResource(R.string.profile_yuan_value, Money.yuanText(siteSummary.yearPayCents)),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                }
            } else {
                Row {
                    KeyValue(
                        stringResource(R.string.profile_year_ot_label), stringResource(R.string.profile_hours_value, TimeUtils.hoursDecimal(summary.yearOtMinutes)),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                    KeyValue(
                        stringResource(R.string.profile_year_days_label), stringResource(R.string.profile_days_value, summary.yearDays),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                    KeyValue(
                        stringResource(R.string.profile_comp_balance_label),
                        if (summary.compBalanceMinutes < 0) "-" + TimeUtils.prettyDuration(-summary.compBalanceMinutes)
                        else TimeUtils.prettyDuration(summary.compBalanceMinutes),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
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

    // ---- 昵称编辑对话框 ----
    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.profile_edit_nickname)) },
            text = {
                OutlinedTextField(
                shape = RoundedCornerShape(Radius.textField),
                    value = nameText,
                    onValueChange = { raw ->
                        nameOverLimit = raw.length > 24
                        nameText = raw.take(24)
                    },
                    label = { Text(stringResource(R.string.profile_nickname_label)) },
                    singleLine = true,
                    isError = nameOverLimit,
                    supportingText = {
                        if (nameOverLimit) {
                            Text(stringResource(R.string.profile_nickname_limit), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!nameOverLimit) {
                        vm.setNickname(nameText)
                        editingName = false
                    }
                }) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.profile_cancel)) }
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
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = 12.dp),
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

@Composable
private fun ActionIconButton(icon: Painter, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.button))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
