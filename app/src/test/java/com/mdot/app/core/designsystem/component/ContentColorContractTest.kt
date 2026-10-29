package com.mdot.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ══ 内容色契约守门 ═════════════════════════════════════════════
 * 纯 JVM 读源码，不依赖设备、不依赖 UI（与 `DialogContractTest`/`SpacingContractTest` 同族）。
 *
 * **规则：包装组件若受理 `contentColor: Color` 形参，就必须自己把它下发下去**
 * （`CompositionLocalProvider(LocalContentColor provides contentColor)` 或直接写到内容上）。
 *
 * 为什么：MIUIX 的库组件**不认内容色**——库 `FloatingActionButton` 连 `contentColor` 形参都没有，
 * 内部也不下发 `LocalContentColor`（字节码里没有 `CompositionLocalProvider` 调用，见 docs/11 097）。
 * 于是塞进 FAB 的 `Icon` 退回**页面环境色**（根 `Surface` 给的 `onBackground`，浅色主题近黑），
 * 一旦填充换成深色（日历页 FAB 用 `primary`）就成了「深底近黑图标」——用户 2026-10 报
 * 「日历页 FAB 图标恒为黑色，即使填充色较深」。
 *
 * 该类缺陷**只在 MIUIX 下可见**（MD3 的 M3 组件自己有 `contentColor` 且会往下发），
 * 编译与其余单测都抓不到 ⇒ 用本测试把它钉在源码层（同硬规则 13 ④「库 Button 不下发
 * contentColor」那条的泛化版）。
 */
class ContentColorContractTest {

    // ── 路径：Gradle/IDE 跑单测时工作目录是模块目录（app/），兜底仓库根 ──────────
    private fun srcMain(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("找不到 src/main（单测工作目录应在 app/ 或仓库根）")

    private fun 包装组件目录(): File =
        File(srcMain(), "java/com/mdot/app/core/designsystem/component")

    private fun 包装组件文件(): List<File> =
        包装组件目录().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** 形参声明（不要匹配注释里的提及） */
    private val 内容色形参 = Regex("""\bcontentColor\s*:\s*Color\b""")
    private val 引擎分叉 = Regex("""ThemeEngine\.MIUIX""")
    private val 下发内容色 = "LocalContentColor provides"

    private fun 是注释行(l: String): Boolean {
        val s = l.trim()
        return s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")
    }

    @Test
    fun `受理 contentColor 形参的包装组件必须自己下发内容色`() {
        val 违规 = mutableListOf<String>()
        val 文件 = 包装组件文件()
        // 自检：扫描面不能为空（路径错了会让本测试变成"永远绿"）
        assertTrue("没扫到包装组件源码，检查 srcMain 路径", 文件.size > 20)
        var 命中 = 0
        文件.forEach { f ->
            val 行 = f.readLines()
            val 声明 = 行.any { !是注释行(it) && 内容色形参.containsMatchIn(it) }
            if (!声明) return@forEach
            val 有引擎分叉 = 行.any { !是注释行(it) && 引擎分叉.containsMatchIn(it) }
            if (!有引擎分叉) return@forEach // 无引擎分叉 = 只喂给 M3 组件，M3 自己会下发
            命中++
            val 全文 = 行.filterNot(::是注释行).joinToString("\n")
            if (!全文.contains(下发内容色)) {
                违规 += "${f.relativeTo(srcMain())}：受理 contentColor 但未 `$下发内容色`"
            }
        }
        // 自检：规则至少命中一个文件（今天就是 JiabanFab），否则等于没测
        assertTrue("规则没命中任何包装组件：形参正则可能已与源码脱节", 命中 > 0)
        assertEquals(
            "MIUIX 库组件不认内容色（FAB 连 contentColor 形参都没有）⇒ 包装组件必须自己 " +
                "`LocalContentColor provides contentColor`，否则图标会退化成环境 onBackground（近黑）：\n" +
                违规.joinToString("\n"),
            emptyList<String>(),
            违规,
        )
    }

    /**
     * 定点锁本次缺陷：库 FAB 无 `contentColor` 形参、内部也不下发 ⇒ [JiabanFab] 的 MIUIX 分支
     * 必须包一层 `LocalContentColor`，否则日历页（填充 = `primary`）图标恒为近黑。
     */
    @Test
    fun `JiabanFab 的 MIUIX 分支显式下发 contentColor`() {
        val f = File(包装组件目录(), "JiabanFab.kt")
        assertTrue("找不到 JiabanFab.kt（路径规范变了吗）", f.isFile)
        val 代码 = f.readLines().filterNot(::是注释行).joinToString("\n")
        assertTrue(
            "JiabanFab 的 MIUIX 分支缺少 `$下发内容色`：库 FAB 不认内容色，" +
                "填充为深色时图标会退化成环境 onBackground（近黑）",
            代码.contains(下发内容色),
        )
        // M3 侧仍走 M3 FAB 自己的 contentColor 形参（不下发也不该删这个实参）
        assertTrue("M3 分支必须继续把 contentColor 传给 M3 FloatingActionButton", 代码.contains("contentColor = contentColor"))
    }
}
