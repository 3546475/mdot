package com.mdot.app.core.util

import android.content.Context
import java.io.File

/**
 * 冷启动阶段面包屑：给「冷启动闪退却拿不到堆栈」留证（2026-11 用户反馈：国产 ROM 声称 Android 17，冷启动闪退）。
 *
 * 每过一阶段追加一行，**首帧画完就删文件**；下次启动文件还在 ⇒ 上次没走到首帧，最后一行即卡住的阶段。
 * 这样连 native 崩溃（tombstone、没有 Java 堆栈，[CrashGuard] 抓不到）也能定位。
 *
 * 阶段名是稳定英文标识（不是 UI 文案），随崩溃日志一起导出。失败一律静默——取证不能反过来弄崩启动。
 */
object StartupTrace {

    private fun file(context: Context): File =
        File(File(context.filesDir, "crash"), "startup.txt")

    fun mark(context: Context, stage: String) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.appendText(stage + "\n")
        }
    }

    /** 首帧已画出：本次启动正常，清掉面包屑（此后被杀/被划掉都不算启动异常） */
    fun done(context: Context) {
        runCatching { file(context).delete() }
    }

    /** 上次启动没走完首帧则返回其阶段序列；正常启动返回 null */
    fun abortedStages(context: Context): List<String>? {
        val f = file(context)
        if (!f.exists()) return null
        val stages = runCatching { f.readLines().filter { it.isNotBlank() } }.getOrNull().orEmpty()
        return stages.ifEmpty { null }
    }

    /** 本次启动已走过的阶段（写崩溃日志时附上） */
    fun stages(context: Context): List<String> =
        runCatching { file(context).readLines().filter { it.isNotBlank() } }.getOrNull().orEmpty()

    fun clear(context: Context) = done(context)
}
