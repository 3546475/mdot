package com.mdot.app.feature.site

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.pressScale
import java.io.File

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
