package com.mdot.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 用**仓库真实的 CHANGELOG.md** 跑一遍解析器。
 *
 * CI 的 Release 说明用 awk 抽同一份文件，App 内「更新日志」页用本解析器——两边必须对得上：
 * 版本顺序一致、最新版本段有分组有条目。否则页面上会出现「版本对不上 / 条目丢失」，
 * 而单测里手写的样例是发现不了的（样例格式 ≠ 真实文件格式）。
 *
 * 文件取不到时跳过（CI 的公开仓里 CHANGELOG.md 在仓根，本地也在仓根）。
 */
class ChangelogRealFileTest {

    private val file = File("../CHANGELOG.md")

    /** 仓根 CHANGELOG.md 的版本标题：与 [ChangelogParser] 的版本段正则保持同一取法 */
    private val heading = Regex("^##\\s+(v[0-9][^\\s（(]*)", RegexOption.MULTILINE)

    @Test
    fun `解析出的版本顺序与文件里的版本标题一致`() {
        assumeTrue("仓根没有 CHANGELOG.md", file.exists())
        val markdown = file.readText()
        val fromFile = heading.findAll(markdown).map { it.groupValues[1] }.toList()
        val fromParser = ChangelogParser.parse(markdown).map { it.version }
        assertTrue("CHANGELOG.md 里没有版本段", fromFile.isNotEmpty())
        assertEquals(fromFile, fromParser)
    }

    @Test
    fun `最新版本段有分组也有条目`() {
        assumeTrue("仓根没有 CHANGELOG.md", file.exists())
        val latest = ChangelogParser.parse(file.readText()).firstOrNull()
        assumeTrue("CHANGELOG.md 解析不出版本段", latest != null)
        assertTrue("最新版本段没有分组", latest!!.groups.isNotEmpty())
        assertTrue("最新版本段没有条目", latest.groups.any { it.items.isNotEmpty() })
    }

    @Test
    fun `按版本号能查到对应段落`() {
        assumeTrue("仓根没有 CHANGELOG.md", file.exists())
        val entries = ChangelogParser.parse(file.readText())
        val latest = entries.first()
        assertEquals(latest, ChangelogParser.findByVersion(entries, latest.version.removePrefix("v")))
    }
}
