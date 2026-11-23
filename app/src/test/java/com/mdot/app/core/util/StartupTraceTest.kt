package com.mdot.app.core.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 启动面包屑单测：冷启动闪退（尤其 native 崩溃无 Java 堆栈）靠它定位卡在哪一阶段。
 * 口径：首帧画完删文件；没删 ⇒ 下次启动视为「上次启动异常」并合成一条崩溃记录。
 */
@RunWith(RobolectricTestRunner::class)
class StartupTraceTest {

    private lateinit var context: Context

    private fun crashFile() = File(File(context.filesDir, "crash"), "last_crash.txt")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "crash").deleteRecursively()
    }

    @Test
    fun `走到首帧则视为正常启动`() {
        StartupTrace.mark(context, "application")
        StartupTrace.mark(context, "activity")
        StartupTrace.done(context)
        assertNull(StartupTrace.abortedStages(context))
    }

    @Test
    fun `未走到首帧则返回卡住的阶段序列`() {
        StartupTrace.mark(context, "application")
        StartupTrace.mark(context, "activity")
        assertEquals(listOf("application", "activity"), StartupTrace.abortedStages(context))
    }

    @Test
    fun `没有面包屑时不合成崩溃记录`() {
        CrashGuard.captureStartupAbort(context)
        assertTrue(!crashFile().exists())
    }

    @Test
    fun `有面包屑且无 Java 堆栈时合成崩溃记录`() {
        StartupTrace.mark(context, "application")
        StartupTrace.mark(context, "activity")
        CrashGuard.captureStartupAbort(context)
        val text = crashFile().readText()
        assertTrue(text.contains("startup: application > activity"))
        assertTrue(text.contains("无 Java 堆栈"))
    }

    @Test
    fun `已有崩溃日志时不覆盖`() {
        crashFile().parentFile?.mkdirs()
        crashFile().writeText("time: old\njava.lang.RuntimeException: boom")
        StartupTrace.mark(context, "application")
        CrashGuard.captureStartupAbort(context)
        assertEquals("time: old\njava.lang.RuntimeException: boom", crashFile().readText())
    }
}
