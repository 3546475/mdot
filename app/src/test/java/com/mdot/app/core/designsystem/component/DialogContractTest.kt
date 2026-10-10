package com.mdot.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ══ 弹窗按钮槽契约守门 ═════════════════════════════════════════
 * 纯 JVM 读源码，不依赖设备、不依赖 UI（与 `SpacingContractTest`/`IconContractTest` 同族）。
 *
 * **规则：没有那个按钮就传 `null`，不要传空 lambda `{}`。**
 *
 * 为什么：MIUIX 的对话框卡片按「槽**在不在**」排版按钮行——`confirmButton == null` 时
 * 「取消」铺满整行；而空 lambda 的槽**仍在**，于是照旧占出半格，表现为
 * **半格空白 + 一条孤立的竖分隔线**（2026-10-09 真机报「日期选择弹窗右下是空的」：
 * 单选日期/选择月份这类「点一下即生效」的弹窗没有确认键，当时传的是 `{}`）。
 *
 * 空 lambda 在 MD3 侧看不出问题（M3 只有"有则右对齐、无则只剩取消"），所以这类缺陷
 * **只在 MIUIX 下可见、编译与其余单测都抓不到**——故用本测试把它钉在源码层。
 *
 * 契约的权威描述在 `JiabanAlertDialog` 的 KDoc（`DialogBackdrop.kt`）：
 * `confirmButton = null` ⇒ 取消铺满整行；两个槽都是 null ⇒ 整条按钮行与横分隔线都不画。
 */
class DialogContractTest {

    // ── 路径：Gradle/IDE 跑单测时工作目录是模块目录（app/），兜底仓库根 ──────────
    private fun srcMain(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("找不到 src/main（单测工作目录应在 app/ 或仓库根）")

    private fun 源码文件(): List<File> =
        File(srcMain(), "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** `confirmButton = {}` / `confirmButton = { }` / 同理 dismissButton —— 空槽必须写 null */
    private val 空槽赋值 = Regex("""\b(confirmButton|dismissButton)\s*=\s*\{\s*\}""")

    private fun 是注释行(l: String): Boolean {
        val s = l.trim()
        return s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")
    }

    @Test
    fun `没有按钮的槽必须传 null 而不是空 lambda`() {
        val 违规 = mutableListOf<String>()
        val 文件 = 源码文件()
        // 自检：扫描面不能为空（路径错了会让本测试变成"永远绿"）
        assertTrue("没扫到任何 Kotlin 源码，检查 srcMain 路径", 文件.size > 50)
        文件.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (是注释行(line)) return@forEachIndexed
                if (空槽赋值.containsMatchIn(line)) {
                    违规 += "${f.relativeTo(srcMain())}:${i + 1}  ${line.trim()}"
                }
            }
        }
        assertEquals(
            "弹窗的空按钮槽必须传 null（空 lambda 会在 MIUIX 下留出半格空白 + 孤立竖分隔线）：\n" +
                违规.joinToString("\n"),
            emptyList<String>(),
            违规,
        )
    }
}
