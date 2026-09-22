package com.mdot.app.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ══ 字符串资源守门 ═══════════════════════════════════════════════
 * 与 [com.mdot.app.core.designsystem.IconContractTest] 的"引用层"对称：
 * **不允许存在未被任何 Kotlin / XML / Manifest 引用的字符串资源**。
 *
 * 为什么需要它：文案是删改最频繁的东西，"旧弹窗文案 / 旧入口标签"特别容易被漏在
 * `strings_*.xml` 里越攒越多（2026-09-22 一次就清出 8 条：项目行的旧编辑/删除弹窗文案、
 * 一条已是死文案的切入确认、以及 `payroll_saved_cd` / `paymonth_ok` / `sync_storage_in_use`）。
 * 更隐蔽的是：**改一条死文案等于白改**——那次精简说明文字就动过其中一条。
 *
 * 口径：只要出现 `R.string.xxx` / `@string/xxx` / manifest 里的 `@string/xxx` 即算被引用。
 * ⚠️ 本项目**禁用按名字动态查找资源**（release 的 `isShrinkResources` 会裁掉），
 * 所以静态扫描在这里是完备的；确需保留的（如只由 manifest 引用）会自动通过。
 */
class StringContractTest {

    private fun srcMain(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("找不到 src/main（单测工作目录应在 app/ 或仓库根）")

    private val 引用池: String by lazy {
        val kt = File(srcMain(), "java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText(Charsets.UTF_8) }
        val xml = File(srcMain(), "res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .joinToString("\n") { it.readText(Charsets.UTF_8) }
        val manifest = File(srcMain(), "AndroidManifest.xml").let {
            if (it.isFile) it.readText(Charsets.UTF_8) else ""
        }
        kt + "\n" + xml + "\n" + manifest
    }

    @Test
    fun `引用层_没有未被引用的字符串资源`() {
        val declared = File(srcMain(), "res").walkTopDown()
            .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
            .flatMap { f ->
                Regex("""<string name="([^"]+)"""").findAll(f.readText(Charsets.UTF_8))
                    .map { f.name to it.groupValues[1] }
            }
            .toList()

        assertTrue("没扫到任何字符串资源，路径不对？${srcMain().absolutePath}", declared.isNotEmpty())

        val 孤儿 = declared.filter { (_, n) ->
            !Regex("""R\.string\.$n(?![A-Za-z0-9_])""").containsMatchIn(引用池) &&
                !Regex("""@string/$n(?![A-Za-z0-9_])""").containsMatchIn(引用池)
        }

        assertTrue(
            "存在未被引用的字符串（要么用起来，要么删掉 —— 改一条死文案等于白改）：\n" +
                孤儿.joinToString("\n") { "  ${it.first}: ${it.second}" },
            孤儿.isEmpty(),
        )
    }
}
