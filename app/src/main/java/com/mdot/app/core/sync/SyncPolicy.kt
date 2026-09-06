package com.mdot.app.core.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * 同步策略的纯 Kotlin 语义（05/06 文档）：
 * - 路径约定：WebDAV 远端需 `mdot/` 前缀；**list 必须与 put/delete 同路径**，
 *   否则 PROPFIND 打到 `/dav/backup/history/` 返回 404 → 清理永不执行（历史堆积）。
 * - history 滚动：按 lastModified 降序保留最新 maxHistory 份 .zip，删除更旧的。
 * - 自动备份触发链：上游是 DataStore 数据流，任何一次写入（含 backupNow 自身的锚点/
 *   时间写入）都会重发射**相同值**；必须先 distinctUntilChanged 过滤，否则备份会自触发
 *   形成 30s 死循环。
 */
object SyncPolicy {

    /** 远端相对路径：WebDAV 加 `mdot/` 前缀（S3 的 pathPrefix 已含该前缀）。 */
    fun remotePath(kind: ProviderKind, path: String): String =
        if (kind == ProviderKind.WEBDAV) "mdot/$path" else path

    /**
     * 计算 history 滚动清理应删除的文件名（不含目录）。
     * 只统计 .zip，按 lastModified 降序，超出 maxHistory 的最旧部分返回删除。
     */
    fun selectHistoryToDelete(files: List<RemoteFileMeta>, maxHistory: Int): List<String> =
        files
            .filter { it.path.substringAfterLast('/').endsWith(".zip") }
            .sortedByDescending { it.lastModified }
            .drop(maxHistory)
            .map { it.path.substringAfterLast('/') }

    /** 自动备份触发链：丢弃初始快照 → 同值去重（防自触发）→ 防抖。 */
    @OptIn(FlowPreview::class)
    fun lastChangeTrigger(upstream: Flow<Long>, debounceMs: Long): Flow<Long> =
        upstream.drop(1).distinctUntilChanged().debounce(debounceMs)
}
