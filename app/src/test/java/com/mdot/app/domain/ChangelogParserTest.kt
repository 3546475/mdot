package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHANGELOG.md 解析器（纯 Kotlin，无 Android 依赖）单测。
 *
 * 样本直接取自本仓 CHANGELOG.md 的真实写法：新版用全角括号、旧版用半角；
 * 条目有「标题+说明」「整句加粗」「纯文本」三种形态，另有缩进续行与缩进子项。
 */
class ChangelogParserTest {

    @Test
    fun `解析版本标题_全角括号与半角括号都认`() {
        val md = """
            ## v0.7.10.0（2026-11-23）

            **新增**

            - 内容

            ## v0.6.13 (2026-09-15)

            **修复**

            - 内容

            ## v0.5.0

            **说明**

            - 内容
        """.trimIndent()

        val entries = ChangelogParser.parse(md)
        assertEquals(3, entries.size)
        assertEquals("v0.7.10.0", entries[0].version)
        assertEquals("2026-11-23", entries[0].date)
        assertEquals("v0.6.13", entries[1].version)
        assertEquals("2026-09-15", entries[1].date)
        assertEquals("v0.5.0", entries[2].version)
        assertEquals("", entries[2].date)
    }

    @Test
    fun `解析分组与三种条目形态`() {
        val md = """
            ## v0.7.10.0（2026-11-23）

            **新增**

            - **加班费可以分段算了**：工资设定 → 加班倍率 → 分段计价
            - **导出页重做：只留一种工资单**
            - 历史记录会按当前规则重算

            **修复**

            - **备份打不开**：含「只记班次」记录的备份无法恢复
        """.trimIndent()

        val entries = ChangelogParser.parse(md)
        assertEquals(1, entries.size)
        val groups = entries[0].groups
        assertEquals(2, groups.size)
        assertEquals("新增", groups[0].title)
        assertEquals("修复", groups[1].title)

        val items = groups[0].items
        assertEquals(3, items.size)
        // 标题 + 说明
        assertEquals("加班费可以分段算了", items[0].title)
        assertEquals("工资设定 → 加班倍率 → 分段计价", items[0].body)
        // 整句加粗 ⇒ 整句当标题、无正文
        assertEquals("导出页重做：只留一种工资单", items[1].title)
        assertEquals("", items[1].body)
        // 纯文本 ⇒ 无标题、整行当正文
        assertEquals("", items[2].title)
        assertEquals("历史记录会按当前规则重算", items[2].body)
    }

    @Test
    fun `缩进续行并入上一条目正文`() {
        val md = """
            ## v0.7.10.0（2026-11-23）

            **新增**

            - **记月新增「按日补贴」**
              天数口径改叫「本月天数」——算法没变
        """.trimIndent()

        val item = ChangelogParser.parse(md)[0].groups[0].items[0]
        assertEquals("记月新增「按日补贴」", item.title)
        assertTrue(item.body.contains("天数口径改叫"))
        assertTrue(item.body.contains("算法没变"))
    }

    @Test
    fun `缩进子项并入上一条目正文`() {
        val md = """
            ## v0.7.10.0（2026-11-23）

            **新增**

            - **导出页重做**
              · 只留一个下载入口
              - **调休折现**：休息日打卡也算
        """.trimIndent()

        val item = ChangelogParser.parse(md)[0].groups[0].items[0]
        assertEquals("导出页重做", item.title)
        assertTrue(item.body.contains("· 只留一个下载入口"))
        assertTrue(item.body.contains("· 调休折现：休息日打卡也算"))
    }

    @Test
    fun `忽略引用块_分隔线与其它标题`() {
        val md = """
            # 更新日志（CHANGELOG）

            > 发版流程：先写本文，再改版本号。

            ---

            ## v0.7.10.0（2026-11-23）

            **新增**

            - **有内容**

            ### 小标题

            散句
        """.trimIndent()

        val entries = ChangelogParser.parse(md)
        assertEquals(1, entries.size)
        assertEquals(1, entries[0].groups.size)
        assertEquals(1, entries[0].groups[0].items.size)
    }

    @Test
    fun `空内容与非法内容返回空列表`() {
        assertTrue(ChangelogParser.parse("").isEmpty())
        assertTrue(ChangelogParser.parse("随便一段文字").isEmpty())
    }

    @Test
    fun `按版本号找版本段_带不带v前缀都行`() {
        val md = """
            ## v0.7.10.0（2026-11-23）

            **新增**

            - **一**

            ## v0.7.9.1（2026-10-30）

            **修复**

            - **二**
        """.trimIndent()

        val entries = ChangelogParser.parse(md)
        assertEquals("v0.7.9.1", ChangelogParser.findByVersion(entries, "0.7.9.1")?.version)
        assertEquals("v0.7.9.1", ChangelogParser.findByVersion(entries, "v0.7.9.1")?.version)
        assertNull(ChangelogParser.findByVersion(entries, "0.7.11.0"))
        assertNull(ChangelogParser.findByVersion(entries, ""))
    }
}
