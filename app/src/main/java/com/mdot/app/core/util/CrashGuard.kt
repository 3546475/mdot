package com.mdot.app.core.util

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 崩溃兜底（07 文档 §6）：无三方 SDK，未捕获异常写本地文件，
 * 下次启动提示用户导出或清除。
 */
object CrashGuard {

    private fun crashFile(context: Context): File =
        File(File(context.filesDir, "crash"), "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashFile(context).parentFile?.mkdirs()
                crashFile(context).writeText(
                    buildString {
                        appendLine("time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        appendLine("thread: " + thread.name)
                        appendLine(throwable.stackTraceToString())
                    }
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 上次崩溃日志（无则 null） */
    fun pendingCrashLog(context: Context): String? =
        crashFile(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        crashFile(context).delete()
    }
}
