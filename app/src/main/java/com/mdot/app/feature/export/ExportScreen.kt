package com.mdot.app.feature.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.DatePick
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.util.PayslipRenderer
import java.io.File
import java.time.LocalDate

/** 导出页：先生成预览，预览弹窗内可「保存」（系统选择位置）或「分享」 */
@Composable
fun ExportScreen(canBack: Boolean = false, onBack: () -> Unit = {}, vm: ExportViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf<String?>(null) } // "from" | "to"

    val colorScheme = MaterialTheme.colorScheme
    val payslipPalette = PayslipRenderer.Palette(
        primary = colorScheme.primary.toArgb(),
        onSurface = colorScheme.onSurface.toArgb(),
        muted = colorScheme.onSurfaceVariant.toArgb(),
        surface = colorScheme.surfaceContainer.toArgb(),
        line = colorScheme.outlineVariant.toArgb(),
    )

    // 导入 CSV：系统文件选择器
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importCsv) }

    Column(
        Modifier
            .fillMaxSize()
            .contentBottomPadding(showBottomBar = !canBack)
            .padding(horizontal = Spacing.page)
            .verticalScroll(rememberScrollState()),
    ) {
        JiabanTopBar(title = if (canBack) stringResource(R.string.export_screen_title) else null, showBack = canBack, onBack = onBack)
        Text(
            stringResource(R.string.export_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
        Spacer(Modifier.height(Spacing.m))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExportDimension.entries.forEach { dim ->
                FilterChip(
                    selected = state.dimension == dim,
                    onClick = { vm.onDimension(dim) },
                    label = { Text(stringResource(dim.labelRes)) },
                )
            }
        }

        if (state.dimension == ExportDimension.CUSTOM) {
            Spacer(Modifier.height(Spacing.s))
            val fromDefault = stringResource(R.string.export_custom_from_default)
            val toDefault = stringResource(R.string.export_custom_to_default)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "from" }) {
                    Text(stringResource(R.string.export_custom_from_label, state.customFrom?.let { "$it" } ?: fromDefault))
                }
                OutlinedButton(onClick = { picking = "to" }) {
                    Text(stringResource(R.string.export_custom_to_label, state.customTo?.let { "$it" } ?: toDefault))
                }
            }
        }

        Spacer(Modifier.height(Spacing.s))
        Text(
            state.range?.toString() ?: stringResource(R.string.export_range_invalid),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.l))

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedButton(
                    onClick = { openDoc.launch(arrayOf("text/*", "text/csv", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_import_csv)) }
                Button(
                    onClick = { vm.prepareCsvPreview() },
                    enabled = !state.busy && state.range != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_csv_detail)) }
                OutlinedButton(
                    onClick = { vm.preparePayslipPreview(payslipPalette) },
                    enabled = !state.busy && state.range != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_payslip_generate)) }
                if (state.busy) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                }
                state.doneText?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                state.errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // 预览弹窗
    state.preview?.let { artifact ->
        ExportPreviewDialog(
            artifact = artifact,
            onDismiss = vm::dismissPreview,
        )
    }

    picking?.let { which ->
        DatePick(
            initial = if (which == "from") state.customFrom ?: LocalDate.now().withDayOfMonth(1)
            else state.customTo ?: LocalDate.now(),
            onPick = { d ->
                if (which == "from") vm.onCustomFrom(d) else vm.onCustomTo(d)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** 导出预览弹窗：工资单显示长图，CSV 显示 Excel 图标；底部「分享」「保存」 */
@Composable
private fun ExportPreviewDialog(
    artifact: PreviewArtifact,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val fileName = artifact.file.name
    val shareLabel = stringResource(R.string.export_action_share)
    val mime = when (artifact) {
        is PreviewArtifact.Csv -> "text/csv"
        is PreviewArtifact.Payslip -> "image/png"
    }

    // 保存：系统“创建文档”选择位置
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime)
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                artifact.file.inputStream().use { it.copyTo(out) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (artifact is PreviewArtifact.Payslip) stringResource(R.string.export_preview_payslip_title) else stringResource(R.string.export_preview_csv_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (artifact) {
                    is PreviewArtifact.Payslip -> {
                        val bitmap = remember(artifact.file) {
                            BitmapFactory.decodeFile(artifact.file.absolutePath)
                        }
                        if (bitmap != null) {
                            // 长图：限高可竖向滚动查看
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(360.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.export_preview_payslip_cd),
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    is PreviewArtifact.Csv -> {
                        // CSV 非图片：展示 Excel 图标
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(96.dp)
                                        .background(Color(0xFF217346), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_ms_table_chart),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp),
                                    )
                                }
                                Spacer(Modifier.height(Spacing.m))
                                Text(
                                    fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.export_preview_csv_type),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                createDoc.launch(fileName)
            }) { Text(stringResource(R.string.export_action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_action_close)) }
                TextButton(onClick = { share(context, uriFor(context, artifact.file), mime, shareLabel) }) { Text(shareLabel) }
            }
        },
    )
}

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun share(context: Context, uri: Uri, mime: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
