package com.mdot.app.domain

import com.mdot.app.domain.model.ChangelogEntry
import com.mdot.app.domain.model.ChangelogGroup
import com.mdot.app.domain.model.ChangelogItem

/**
 * 解析仓库根目录的 CHANGELOG.md（Markdown）→ 结构化版本段。
 *
 * 纯 Kotlin（domain 层零 Android 依赖，AGENTS 硬规则 1）。
 *
 * 识别的写法（与本仓 CHANGELOG.md 的实际格式一一对应）：
 * - `## v0.7.10.0（2026-11-23）` / `## v0.6.13 (2026-09-15)`：**版本段**（半/全角括号都认，日期可缺）
 * - `**新增**` 独占一行：**分组标题**（可带后缀，如 `**新增「miuix」主题引擎（可选）**`）
 * - `- **标题**：说明`：**条目**（标题 + 正文）
 * - `- **整句加粗**`：**条目**（整句当标题，无正文）
 * - `- 纯文本`：**条目**（无标题，整行当正文）
 * - 缩进 2 空格起的行：上一条目的**续行**；缩进的 `- ` / `· `：上一条目的**子项**（并入正文）
 * - 其余（`#` 标题、`>` 引用块、`---` 分隔线）：忽略
 */
object ChangelogParser {

    /** 版本段标题：`## v0.7.10.0（2026-11-23）`；括号全/半角都认，日期可缺 */
    private val versionHeading =
        Regex("^##\\s+(v[0-9][^\\s（(]*)\\s*(?:[（(]\\s*(\\d{4}-\\d{2}-\\d{2})\\s*[）)])?\\s*$")

    /** 分组标题：整行被 `**` 包住 */
    private val groupHeading = Regex("^\\*\\*(.+?)\\*\\*\\s*$")

    /**
     * 解析整份 Markdown，返回按**原文顺序**（最新在前）的版本段列表。
     *
     * 解析不出任何版本段时返回空列表（调用方据此判定「这份内容不是更新日志」）。
     */
    fun parse(markdown: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version = ""
        var date = ""
        var groups = mutableListOf<ChangelogGroup>()
        var groupTitle: String? = null
        var items = mutableListOf<ChangelogItem>()

        fun flushGroup() {
            val title = groupTitle
            if (title != null && items.isNotEmpty()) {
                groups.add(ChangelogGroup(title, items.toList()))
            }
            items = mutableListOf()
        }

        fun flushEntry() {
            if (version.isNotEmpty()) {
                flushGroup()
                entries.add(ChangelogEntry(version, date, groups.toList()))
            }
            groups = mutableListOf()
            groupTitle = null
            items = mutableListOf()
        }

        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            val versionMatch = versionHeading.matchEntire(line)
            if (versionMatch != null) {
                flushEntry()
                version = versionMatch.groupValues[1]
                date = versionMatch.groupValues[2]
                return@forEach
            }
            // 其余标题（h1/h3）、引用块（发版流程说明）、分隔线：忽略
            if (line.startsWith("#") || line.startsWith(">") || line.startsWith("---")) return@forEach

            val groupMatch = groupHeading.matchEntire(line)
            if (groupMatch != null) {
                flushGroup()
                groupTitle = clean(groupMatch.groupValues[1])
                return@forEach
            }

            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            when {
                trimmed.startsWith("- ") -> {
                    val content = trimmed.removePrefix("- ").trim()
                    if (indent == 0) {
                        items.add(parseItem(content))
                    } else {
                        // 缩进的 `- `：上一条目的子项
                        appendSubItem(items, content)
                    }
                }

                trimmed.startsWith("· ") -> appendSubItem(items, trimmed.removePrefix("· ").trim())
                // 其余（缩进续行 / 顶格散句）：并入上一条目正文
                else -> appendContinuation(items, trimmed.trim())
            }
        }
        flushEntry()
        return entries
    }

    /** 去掉 markdown 强调标记（`**`）并去首尾空白 */
    private fun clean(text: String): String = text.replace("**", "").trim()

    /**
     * 一条目 → 标题/正文：
     * - `**标题**：说明` ⇒ 标题 + 说明
     * - `**整句**` ⇒ 整句当标题
     * - 纯文本 ⇒ 整行当正文（无标题）
     */
    private fun parseItem(content: String): ChangelogItem {
        if (content.startsWith("**")) {
            val close = content.indexOf("**", 2)
            if (close > 0) {
                val bold = content.substring(2, close)
                val rest = content.substring(close + 2).trimStart()
                val separator = when {
                    rest.startsWith("：") -> "："
                    rest.startsWith(":") -> ":"
                    else -> null
                }
                if (separator != null) {
                    return ChangelogItem(
                        title = clean(bold),
                        body = clean(rest.substring(separator.length)),
                    )
                }
                return ChangelogItem(title = clean(bold + rest), body = "")
            }
        }
        return ChangelogItem(title = "", body = clean(content))
    }

    /** 子项（原文缩进的 `- ` / `· ` 行）：并入上一条目的正文 */
    private fun appendSubItem(items: MutableList<ChangelogItem>, text: String) {
        if (items.isEmpty()) return
        val last = items.removeAt(items.size - 1)
        val line = "· ${clean(text)}"
        val body = if (last.body.isBlank()) line else last.body + "\n" + line
        items.add(last.copy(body = body))
    }

    /** 续行（原文缩进的纯文本行）：并入上一条目的正文 */
    private fun appendContinuation(items: MutableList<ChangelogItem>, text: String) {
        if (items.isEmpty() || text.isBlank()) return
        val last = items.removeAt(items.size - 1)
        val body = if (last.body.isBlank()) clean(text) else last.body + "\n" + clean(text)
        items.add(last.copy(body = body))
    }

    /** 版本号归一：去掉 `v` 前缀（`v0.7.10.0` 与 `0.7.10.0` 视为同一个） */
    fun normalizeVersion(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

    /** 在版本段列表里找指定版本（[versionName] 可带可不带 `v` 前缀）；找不到返回 null */
    fun findByVersion(entries: List<ChangelogEntry>, versionName: String): ChangelogEntry? {
        val target = normalizeVersion(versionName)
        if (target.isEmpty()) return null
        return entries.firstOrNull { normalizeVersion(it.version) == target }
    }
}
