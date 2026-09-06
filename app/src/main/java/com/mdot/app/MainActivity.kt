package com.mdot.app

import com.mdot.app.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.core.designsystem.JiabanTheme
import com.mdot.app.core.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MdJiabanApp() }
    }
}

@Composable
fun MdJiabanApp(appVm: AppViewModel = hiltViewModel()) {
    // 崩溃兜底提示（07 文档 §6）：上次异常退出的本地日志
    val context = androidx.compose.ui.platform.LocalContext.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashLog = com.mdot.app.core.util.CrashGuard.pendingCrashLog(context)
    }
    crashLog?.let { log ->
        val exportLabel = stringResource(R.string.crash_export_log)
        AlertDialog(
            onDismissRequest = {
                com.mdot.app.core.util.CrashGuard.clear(context)
                crashLog = null
            },
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = { Text(stringResource(R.string.crash_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val file = java.io.File(
                        java.io.File(context.filesDir, "crash"),
                        "last_crash.txt",
                    )
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, context.packageName + ".fileprovider", file,
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(intent, exportLabel),
                    )
                    com.mdot.app.core.util.CrashGuard.clear(context)
                    crashLog = null
                }) { Text(stringResource(R.string.crash_export_log)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.mdot.app.core.util.CrashGuard.clear(context)
                    crashLog = null
                }) { Text(stringResource(R.string.crash_clear)) }
            },
        )
    }

    val appearance by appVm.appearance.collectAsStateWithLifecycle()
    val firstLaunchDone by appVm.firstLaunchDone.collectAsStateWithLifecycle()

    JiabanTheme(appearance = appearance) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppRoot(firstLaunchDone = firstLaunchDone, appVm = appVm)
        }
    }
}
