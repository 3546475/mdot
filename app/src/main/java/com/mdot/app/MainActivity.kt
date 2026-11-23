package com.mdot.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.JiabanTheme
import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.GlassEnvironment
import com.mdot.app.core.designsystem.component.LocalOverlayDialogs
import com.mdot.app.core.designsystem.component.LocalGlassEnvironment
import com.mdot.app.core.designsystem.component.OverlayDialogController
import com.mdot.app.core.designsystem.component.rememberBackdropBlurState
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.navigation.AppRoot
import com.mdot.app.core.util.CrashGuard
import com.mdot.app.core.util.StartupTrace
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 10+ 默认会在浅色内容上给系统导航条/状态栏叠一层对比底色（表现为一条偏白的带状底色）；
        // 应用已 edge-to-edge 自绘背景，关掉该自动对比色，让导航条区域与页面背景一致。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // 上次启动没走到首帧的取证在 Application 里做（须早于第一次面包屑）；这里只记本轮阶段。
        StartupTrace.mark(this, "activity")
        setContent {
            StartupTrace.mark(this, "content")
            // 覆盖层控制器按**组合**持有（不是进程级单例）：随 Activity 重建换新，
            // 预览/测试/多窗口互不串；控制器缺失时弹窗自动回退窗口实现（见 OverlayDialog.kt）
            val overlayDialogs = remember { OverlayDialogController() }
            CompositionLocalProvider(
                LocalOverlayDialogs provides overlayDialogs,
            ) {
                MdJiabanApp()
            }
        }
    }
}

@Composable
fun MdJiabanApp(appVm: AppViewModel = hiltViewModel()) {
    // 崩溃兜底提示（07 文档 §6）：上次异常退出的本地日志
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashLog = CrashGuard.pendingCrashLog(context)
    }
    crashLog?.let { log ->
        val exportLabel = stringResource(R.string.crash_export_log)
        JiabanAlertDialog(containerColor = dialogContainerColor(), 
            onDismissRequest = {
                CrashGuard.clear(context)
                crashLog = null
            },
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = { Text(stringResource(R.string.crash_dialog_text)) },
            confirmButton = {
                JiabanButton(
                    text = stringResource(R.string.crash_export_log),
                    onClick = {
                    val file = File(File(context.filesDir, "crash"), "last_crash.txt")
                    val uri = FileProvider.getUriForFile(
                        context, context.packageName + ".fileprovider", file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, exportLabel))
                    CrashGuard.clear(context)
                    crashLog = null
                },
                    role = JiabanButtonRole.GHOST,
                )
            },
            dismissButton = {
                JiabanButton(
                    text = stringResource(R.string.crash_clear),
                    onClick = {
                    CrashGuard.clear(context)
                    crashLog = null
                },
                    role = JiabanButtonRole.GHOST,
                )
            },
        )
    }

    val appearance by appVm.appearance.collectAsStateWithLifecycle()
    // 玻璃共享源（底栏/圆钮/底部悬浮提示共用采样；MainActivity 创建供全局下发）
    val backdropBlurState = rememberBackdropBlurState()
    val backdropFullState = rememberBackdropBlurState()
    val firstLaunchDone by appVm.firstLaunchDone.collectAsStateWithLifecycle()

    JiabanTheme(appearance = appearance) {
        // 首帧画完即视为启动成功：之后被划掉/被系统回收都不算「启动异常」
        LaunchedEffect(Unit) {
            withFrameNanos { }
            StartupTrace.done(context)
        }
        // 玻璃簇运行时环境（弹层背景档位等）全局下发：SheetBackdropLayer 据此决定缩放/模糊/压暗组合
        CompositionLocalProvider(
            LocalGlassEnvironment provides GlassEnvironment(sheetMode = appearance.sheetBackdropMode),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                AppRoot(firstLaunchDone = firstLaunchDone, appVm = appVm, backdropBlurState = backdropBlurState, backdropFullState = backdropFullState)
            }
        }
    }
}
