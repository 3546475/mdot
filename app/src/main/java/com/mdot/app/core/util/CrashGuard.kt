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
            writeReport(context, thread = thread.name, stack = throwable.stackTraceToString())
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 上次启动没走到首帧（native 崩溃只留 tombstone、被杀、Application 期崩溃前 CrashGuard 还没装好）
     * 而本端没有任何 Java 堆栈时，用启动面包屑合成一条记录——否则这类闪退对用户和我们都是全黑。
     *
     * **必须在本次启动第一次 [StartupTrace.mark] 之前调用**（`JiabanApplication.onCreate` 里）：
     * 晚于它读到的就是本轮自己刚写的面包屑，会每次都误报一次「上次异常退出」。
     */
    fun captureStartupAbort(context: Context) {
        val stages = StartupTrace.abortedStages(context) ?: return
        // 面包屑只代表上一次运行：读完即清，否则下次再中断会把两轮的阶段叠在一起
        StartupTrace.clear(context)
        if (crashFile(context).exists()) return
        writeReport(
            context,
            thread = "<无 Java 堆栈：上次启动未走到首帧>",
            stack = null,
            stages = stages,
        )
    }

    /** 崩溃日志的落盘格式（导出按此文本给用户看，也解析 `startup:` 判阶段） */
    private fun writeReport(
        context: Context,
        thread: String,
        stack: String?,
        stages: List<String> = StartupTrace.stages(context),
    ) {
        runCatching {
            val file = crashFile(context)
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    appendLine("thread: $thread")
                    appendLine("startup: " + stages.joinToString(" > "))
                    if (stack != null) appendLine(stack)
                }
            )
        }
    }

    /** 上次崩溃日志（无则 null） */
    fun pendingCrashLog(context: Context): String? =
        crashFile(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        crashFile(context).delete()
    }
}
