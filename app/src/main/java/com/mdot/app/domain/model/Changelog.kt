package com.mdot.app.domain.model

/**
 * 更新日志的一个版本段（对应 CHANGELOG.md 里的一个 `## vX.Y.Z（日期）`）。
 */
data class ChangelogEntry(
    /** 版本号，含 `v` 前缀，如 `v0.7.10.0` */
    val version: String,
    /** 发布日期（yyyy-MM-dd）；旧版本标题无日期时为空串 */
    val date: String,
    /** 该版本下的分组（新增/修复/…） */
    val groups: List<ChangelogGroup>,
)

/** 一个分组，如「新增」「修复」 */
data class ChangelogGroup(
    /** 分组标题，如「新增」 */
    val title: String,
    /** 分组下的条目 */
    val items: List<ChangelogItem>,
)

/**
 * 一个条目。
 *
 * 原文有三种写法（解析见 `com.mdot.app.domain.ChangelogParser`）：
 * - `- **标题**：说明` ⇒ [title] = 标题，[body] = 说明
 * - `- **整句加粗**` ⇒ [title] = 整句，[body] = 空串
 * - `- 纯文本` ⇒ [title] = 空串，[body] = 整行
 *
 * [body] 里可能含多行（原文的缩进续行与子项会并进来）。
 */
data class ChangelogItem(
    /** 条目标题（已去掉 markdown 的 `**`）；无标题时为空串 */
    val title: String,
    /** 条目正文（已去掉 markdown 的 `**`）；无正文时为空串 */
    val body: String,
)
