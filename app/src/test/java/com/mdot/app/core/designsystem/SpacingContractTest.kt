package com.mdot.app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ══ 间距规范守门 ═══════════════════════════════════════════════
 * [docs/03 §3.2](../../../../../../../../docs/03-UI-UX设计规范.md) 的可执行版本：
 * 纯 JVM 读源码，不依赖设备、不依赖 UI。
 *
 * **规则：间距只能来自 `Spacing` 令牌。**
 * `padding` / `PaddingValues` / `absolutePadding` / `spacedBy` / `offset` 这五类调用里
 * **不允许出现 `.dp` 字面量**（除下方两个白名单值）——要间距就 `Spacing.xs/s/m/l/xl/page`。
 *
 * 为什么卡这么死：2026-09-22 审计发现档位表写着 4/8/12/16/24，实际却散着
 * **6dp×27、10dp×13、2dp×6、3/5/7/9/14/20dp…** 共 57 处越档值，且同一屏里
 * 「10dp 标签内边距 + 12dp 卡片内边距」混着用，肉眼看着就是"哪里不太齐"。
 * 现在全部收敛进档位（6→8、10→12、2→4…），本测试防止再漂回去。
 *
 * ⚠️ **加新档位时先改 docs/03 §3.2 与 [白名单]**——测试是规范的执行者，不是规范本身。
 * 注意：`.size()/.height()/.width()` 等**尺寸**不归本测试管（尺寸走各自 spec），
 * 圆角归 `RoundedCornerShape` 那条规则管（见 docs/03 §3.3）。
 */
class SpacingContractTest {

    // ── 路径：Gradle/IDE 跑单测时工作目录是模块目录（app/），兜底仓库根 ──────────
    private fun srcMain(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("找不到 src/main（单测工作目录应在 app/ 或仓库根）")

    private fun 源码文件(): List<File> =
        File(srcMain(), "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * 白名单：这两个值在"间距位"上是**结构性的**，不是随手挑的档位。
     *
     * - **0dp**：条件占位（`if (wide) Spacing.l else 0.dp`、`spacedBy(0.dp)` 占位）——
     *   语义是"这里不要间距"，换成任何正数都改变行为。
     * - **1dp**：**描边内衬**——内容与 1dp 边框之间留 1dp，避免边框贴着字
     *   （`ringWidth + 1.dp`、标签的 `vertical = 1.dp`）。用 `Spacing.xs`(4dp) 会明显撑开。
     *
     * ⚠️ 白名单是**穷举**的：加值必须同时改这里与 docs/03 §3.2，并写清为什么它不是间距档位。
     */
    private val 白名单 = setOf("0", "1")

    /** 这五类调用所在行受管（尺寸类 `.size/.height/.width` 不受管） */
    private val 间距调用 = Regex(
        """\b(padding|PaddingValues|absolutePadding|spacedBy|offset|absoluteOffset)\s*\(""",
    )

    private val 字面量dp = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp""")

    private fun 是注释行(l: String): Boolean {
        val s = l.trim()
        return s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")
    }

    /** 收集违规：返回 "文件:行号  该行内容" */
    private fun 越档字面量(): List<String> {
        val 违规 = mutableListOf<String>()
        源码文件().forEach { f ->
            f.readText(Charsets.UTF_8).lines().forEachIndexed { i, l ->
                if (是注释行(l) || !间距调用.containsMatchIn(l)) return@forEachIndexed
                字面量dp.findAll(l).forEach { m ->
                    if (m.groupValues[1] !in 白名单) {
                        违规 += "${f.name}:${i + 1}  ${l.trim()}"
                    }
                }
            }
        }
        return 违规
    }

    // ── 1. 间距只能来自令牌 ────────────────────────────────────
    @Test
    fun `间距位不允许出现 dp 字面量_除白名单外`() {
        val 违规 = 越档字面量()
        assertTrue(
            "间距位出现 dp 字面量（改用 Spacing.xs/s/m/l/xl/page，或按 docs/03 §3.2 加档）：\n" +
                违规.joinToString("\n"),
            违规.isEmpty(),
        )
    }

    // ── 2. 档位表本身别被改散 ──────────────────────────────────
    @Test
    fun `Spacing 档位表就是文档写的那六档`() {
        val tokens = File(srcMain(), "java/com/mdot/app/core/designsystem/Tokens.kt")
        assertTrue("读不到 Tokens.kt：${tokens.absolutePath}", tokens.isFile)
        val body = Regex("""object Spacing \{(.*?)\n\}""", RegexOption.DOT_MATCHES_ALL)
            .find(tokens.readText(Charsets.UTF_8))?.groupValues?.get(1)
            ?: error("Tokens.kt 里找不到 object Spacing")

        val 实际 = Regex("""val (\w+)\s*=\s*(\d+)\.dp""")
            .findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val 期望 = mapOf("xs" to "4", "s" to "8", "m" to "12", "l" to "16", "xl" to "24", "page" to "16")

        assertEquals("Spacing 档位表与 docs/03 §3.2 不一致（改了档位请同步文档与白名单）", 期望, 实际)
    }

    // ── 3. 白名单不许悄悄长大 ──────────────────────────────────
    @Test
    fun `白名单只有 0dp 与 1dp 两项`() {
        assertEquals(
            "间距白名单被人加长了——每个新值都要在 docs/03 §3.2 写清它为什么不是间距档位",
            setOf("0", "1"),
            白名单,
        )
    }
}
