package com.mdot.app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ══ 图标规范守门 ═══════════════════════════════════════════════
 * [docs/03 §3.4](../../../../../../../../docs/03-UI-UX设计规范.md) 的可执行版本：
 * 纯 JVM 读源码与资源，不依赖设备、不依赖 UI。三层断言各拦一类漂移。
 *
 * 1. **资产层**：每个 `ic_ms_*.xml` 必须 24dp×24dp + viewport 24×24 + 单条 `<path>`
 *    + 无描边 + 唯一 `fillColor` + 合规 `fillType`
 *    → 拦住"随手塞进来一个 20dp / 带描边 / 带颜色的图标"。
 * 2. **尺寸层**：`Icon(...)` 上写的**裸数字**必须落在已知档位里（角色档或装饰档）
 *    → 拦住"某页顺手写个 17dp"（令牌表达式如 `ButtonSpec.iconSize` 不受限，跳过）。
 *    ⚠️ **覆盖全部 `Icon(...)`**，包括 `Icon(painter 参数, …)`（如 `SettingRow`/`EmptyState` 这类
 *    把 Painter 当参数收进来的共用组件）——早先只扫 `painterResource(...)` 开头的，漏了 3 处（已补）。
 * 3. **引用层**：不允许存在未被任何 Kotlin / XML 引用的图标资源
 *    → 2026-09-22 就靠人工清出两个孤儿（`ic_ms_drag_indicator` / `ic_ms_more_vert`），靠这条防复发。
 *
 * ⚠️ **加新档位时先改 docs/03 §3.4 与 [允许的裸数字]**——测试是规范的执行者，不是规范本身。
 */
class IconContractTest {

    // ── 路径：Gradle/IDE 跑单测时工作目录是模块目录（app/），兜底仓库根 ──────────
    private fun srcMain(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("找不到 src/main（单测工作目录应在 app/ 或仓库根）")

    private val 图标目录: File get() = File(srcMain(), "res/drawable")

    private fun 图标文件(): List<File> =
        图标目录.listFiles { f -> f.name.startsWith("ic_ms_") && f.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?: error("读不到图标目录：${图标目录.absolutePath}")

    private fun 源码文件(): List<File> =
        File(srcMain(), "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun 全部资源文件(): List<File> =
        File(srcMain(), "res").walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()

    private fun attr(t: String, name: String): String? =
        Regex("""$name="([^"]*)"""").find(t)?.groupValues?.get(1)

    /**
     * 尺寸层允许出现在 `Icon(...)` 里的**裸数字**（dp 整数档）。
     *
     * 阶段 3（2026-09-22）完成后本集合**只剩豁免项**：
     * - **选中角标 14**：选项卡右上角的小对勾（[SiteRecordComponents]），它是角标不是行内图标
     * - **装饰档 11·15·21（致谢卡跑马灯）/ 28（首页收入卡）/ 30（弹层拖拽箭头）**：一次性装饰尺寸，**刻意的**
     *
     * ⚠️ **角色档一律走令牌**（`IconSpec.inline/dense/boxed/bar/hero`、`IconBoxSpec.*`）——
     * 写裸 16/18/20/22/24/52 会被本测试拦下。要加新档位：先改 docs/03 §3.4，再把值加到这里。
     * **24dp 没有令牌**：M3 的 `Icon` 默认就是 24dp，默认档**不写 `.size()`**（18 处如此）——
     * 原先的 `IconSpec.standard` 因零引用已于 2026-09-22 删除，确需显式 24 时按上面流程加档。
     */
    private val 允许的裸数字 = setOf(
        14,                   // 选中角标
        11, 15, 21, 28, 30,   // 装饰档（附豁免理由见上）
    )

    // ── 1. 资产层 ─────────────────────────────────────────────
    @Test
    fun `资产层_每个图标都符合统一资产规范`() {
        val files = 图标文件()
        assertTrue("没扫到任何 ic_ms_* 图标，路径不对？${图标目录.absolutePath}", files.isNotEmpty())

        files.forEach { f ->
            val t = f.readText(Charsets.UTF_8)
            val n = f.name

            assertEquals("$n：声明尺寸必须 24dp×24dp", listOf("24dp", "24dp"),
                listOf(attr(t, "android:width"), attr(t, "android:height")))
            assertEquals("$n：viewport 必须 24×24", listOf("24", "24"),
                listOf(attr(t, "android:viewportWidth"), attr(t, "android:viewportHeight")))
            assertEquals("$n：只允许一条 <path>（多条请合并，保持单形状）",
                1, Regex("""<path\b""").findAll(t).count())
            assertFalse("$n：不允许描边——图标只做纯填充（strokeColor 会随缩放失真）",
                t.contains("android:strokeColor"))
            assertEquals("$n：fillColor 必须只有 #FF000000 —— 颜色永不画进图标，由运行时 tint 注入",
                setOf("#FF000000"),
                Regex("""android:fillColor="([^"]+)"""").findAll(t).map { it.groupValues[1] }.toSet())
            val fillTypes = Regex("""android:fillType="([^"]+)"""").findAll(t).map { it.groupValues[1] }.toSet()
            assertTrue("$n：fillType 只能是 nonZero / evenOdd，实际 $fillTypes",
                fillTypes.all { it in setOf("nonZero", "evenOdd") })
        }
    }

    // ── 2. 尺寸层 ─────────────────────────────────────────────
    @Test
    fun `尺寸层_Icon 上的裸数字必须是已知档位`() {
        val 违规 = mutableListOf<String>()

        源码文件().forEach { f ->
            val t = f.readText(Charsets.UTF_8)
            // 直接扫所有 Icon( 调用（而不是只扫 painterResource 开头的）——
            // 共用组件里 Icon(painter 参数, …) 也归这里管，否则盒+图标的尺寸没人守。
            Regex("""\bIcon\(""").findAll(t).forEach { m ->
                val call = t.substring(m.range.first, callEnd(t, m.range.first + 4) + 1)
                val expr = Regex("""\.size\(([^)]+)\)""").find(call)?.groupValues?.get(1)?.trim()
                    ?: return@forEach
                // 只审"裸数字"；令牌/变量表达式（ButtonSpec.iconSize / IconBoxSpec.hero.box 等）跳过
                val num = Regex("""^(\d+)(?:\.\d+)?\.dp$""").find(expr)?.groupValues?.get(1)?.toInt()
                    ?: return@forEach
                if (num !in 允许的裸数字) {
                    val line = t.take(m.range.first).count { it == '\n' } + 1
                    val 图标 = Regex("""R\.drawable\.(ic_ms_\w+)""").find(call)?.groupValues?.get(1) ?: "(painter 参数)"
                    违规 += "  ${f.name}:$line  $图标  用了 ${num}dp"
                }
            }
        }

        assertTrue(
            "以下图标尺寸不在规范档位（要加档位请先改 docs/03 §3.4 与 允许的裸数字）：\n" +
                (违规.joinToString("\n").ifEmpty { "（无）" }),
            违规.isEmpty(),
        )
    }

    // ── 3. 引用层 ─────────────────────────────────────────────
    @Test
    fun `引用层_没有未被引用的图标资源`() {
        val 引用池 = (源码文件() + 全部资源文件()).joinToString("\n") { it.readText(Charsets.UTF_8) }

        val 孤儿 = 图标文件()
            .map { it.nameWithoutExtension }
            .filter {
                !Regex("""R\.drawable\.$it(?![A-Za-z0-9_])""").containsMatchIn(引用池) &&
                    !Regex("""@drawable/$it(?![A-Za-z0-9_])""").containsMatchIn(引用池)
            }

        assertTrue(
            "存在未被引用的图标资源（要么用起来，要么删掉 —— 别再攒孤儿）：$孤儿",
            孤儿.isEmpty(),
        )
    }

    // ── 工具：配对右括号 ───────────────────────────────────────
    private fun callEnd(t: String, open: Int): Int {
        var depth = 0
        for (k in open until t.length) {
            when (t[k]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return k
                }
            }
        }
        return t.length - 1
    }
}
