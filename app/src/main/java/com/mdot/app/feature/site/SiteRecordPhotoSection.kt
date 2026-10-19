package com.mdot.app.feature.site

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdot.app.R
import com.mdot.app.core.designsystem.IconBoxSpec
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import java.io.File
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk

/**
 * 记工页照片区（PickVisualMedia 免权限；本地保存，备份包不含，D10）与备注/照片卡。
 */

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
                            .size(IconBoxSpec.tile.box)
                            .background(MaterialTheme.colorScheme.secondaryContainer, engineShape(Radius.small)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_photo_camera), null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconBoxSpec.tile.icon),
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
                PhotoSection(photos = photos, onAdd = onAddPhotos, onRemove = onRemovePhoto)
            }
        }
    }
}

/** 备注 + 照片（每表单页独立一份，编辑弹窗随页绑定 note / advanceNote） */
@Composable
internal fun NotePhotoSection(
    note: String,
    onNote: (String) -> Unit,
    photos: List<String>,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    var showNoteEdit by remember { mutableStateOf(false) }
    NotePhotoCard(
        note = note,
        onEditNote = { showNoteEdit = true },
        photos = photos,
        onAddPhotos = onAddPhotos,
        onRemovePhoto = onRemovePhoto,
    )
    if (showNoteEdit) {
        NoteDialog(initial = note, onDismiss = { showNoteEdit = false }, onConfirm = onNote)
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
                .clip(engineShape(Radius.button))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, engineShape(Radius.button))
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
                color = emphasisCardInk(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs)
                    .clip(engineShape(Radius.pill))
                    .background(emphasisCardSurface())
                    .padding(horizontal = Spacing.s, vertical = 1.dp),
            )
        }
    }

    viewing?.let { path ->
        JiabanAlertDialog(containerColor = dialogContainerColor(), 
            onDismissRequest = { viewing = null },
            // 照片查看：只有「取消」⇒ 无确认键，传 null（不要传 `{}`，否则 MIUIX 会留半格空白 + 孤立竖线）
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
                JiabanButton(
                        text = stringResource(R.string.site_delete),
                        onClick = { viewing = null; onRemove(path) },
                        role = JiabanButtonRole.GHOST,
                        contentColorOverride = MaterialTheme.colorScheme.error,
                    )
            },
        )
    }
}

@Composable
private fun PhotoThumb(path: String, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    val bitmap = remember(path) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onLong != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = onLong,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    }
    Box(
        Modifier
            .size(76.dp)
            .pressScale(interaction, pressedScale = 0.92f)
            .clip(engineShape(Radius.button))
            .then(clickModifier),
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

// ---- 明细 / 记录行里的照片可见性（v0.7.9.0）------------------------------------------
// 背景：照片 12 文档 D10 起就存了，但**只在编辑弹层里能看到**——记完去翻明细、看月历都看不见，
// 等于"留证留了个寂寞"。这里补两件东西：行上的张数小徽章 + 只读大图查看。

/** 照片张数小徽章（明细/记录行用）：只读，点开看大图；0 张不渲染，调用方无需判空 */
@Composable
internal fun PhotoCountBadge(count: Int, onClick: () -> Unit) {
    if (count <= 0) return
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(engineShape(Radius.pill))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_ms_photo_camera), null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(IconSpec.inline),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * 留证照片查看（明细/记录行点小徽章打开）：**两级**——
 * 第一级先把这次记录的全部照片列成缩略图，点某一张才进大图；多张时在大图里左右滑动切换。
 * 只读：删除仍回编辑弹层做（不在流水列表里误删留证）。
 */
@Composable
internal fun PhotoGalleryDialog(paths: List<String>, onDismiss: () -> Unit) {
    if (paths.isEmpty()) return
    var viewing by remember { mutableStateOf<Int?>(null) }
    JiabanAlertDialog(
        containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_photos)) },
        // 只读：无确认键 ⇒ 传 null（传 `{}` 会留半格空白 + 孤立竖线）
        text = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                paths.forEachIndexed { index, path ->
                    PhotoThumb(path = path, onClick = { viewing = index })
                }
            }
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.site_photo_close),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
    viewing?.let { index ->
        PhotoPagerDialog(paths = paths, startIndex = index, onDismiss = { viewing = null })
    }
}

/** 大图：横向分页（多张可左右滑动），标题显示「第 i / N 张」 */
@Composable
private fun PhotoPagerDialog(paths: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, paths.lastIndex)) { paths.size }
    JiabanAlertDialog(
        containerColor = dialogContainerColor(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.site_photo_index,
                    pager.currentPage + 1,
                    paths.size,
                )
            )
        },
        text = {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(420.dp),
            ) { page ->
                val path = paths[page]
                val bmp = remember(path) {
                    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        dismissButton = {
            JiabanButton(
                text = stringResource(R.string.site_photo_close),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
}
