package com.mdot.app.core.update

import com.mdot.app.core.datastore.SettingsDataSource
import com.mdot.app.core.network.CleartextGate
import com.mdot.app.core.network.JsonFetcher
import com.mdot.app.domain.ChangelogParser
import com.mdot.app.domain.model.ChangelogEntry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新日志（CHANGELOG.md）来源：**只从线上拉**（不内置 assets 兜底）。
 *
 * 线上有两处同一份文件（CNB raw 与 GitHub Pages），按候选顺序试，
 * 第一个能解析出**版本段**的就算成功；全失败则回落本地缓存原文（上次拉到的）。
 *
 * 候选来源：① 用户当前更新源同目录的 `CHANGELOG.md`（自定义镜像也能跟着换）
 * ② 出厂默认两条（见 [SettingsDataSource.DEFAULT_CHANGELOG_URLS]）。
 */
@Singleton
class ChangelogRepository @Inject constructor(
    private val settings: SettingsDataSource,
    private val jsonFetcher: JsonFetcher,
    private val cleartextGate: CleartextGate,
) {

    companion object {
        /** 缓存有效期：这段时间内直接用缓存，不打网络 */
        const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

        /** Markdown 的 Accept（更新日志不是 JSON） */
        private const val ACCEPT = "text/markdown, text/plain, */*"
    }

    /** 拉取结果 */
    sealed interface LoadResult {
        /** [fromCache] = true 表示用的是本地缓存（网络失败或缓存仍新鲜） */
        data class Success(val entries: List<ChangelogEntry>, val fromCache: Boolean) : LoadResult

        /** 线上与缓存都没有可用内容 */
        data class Failure(val message: String) : LoadResult
    }

    /**
     * 取更新日志。
     *
     * @param forceRefresh true = 无视缓存有效期，直接打网络（首启「本次更新」引导用：
     *   升级后要看到**本次**的日志，不能拿旧缓存凑）
     */
    suspend fun load(forceRefresh: Boolean = false): LoadResult {
        val cachedText = settings.changelogCacheFlow.first()
        val cachedEntries = if (cachedText.isBlank()) emptyList() else ChangelogParser.parse(cachedText)

        if (!forceRefresh && cachedEntries.isNotEmpty()) {
            val cachedAt = settings.changelogCacheAtFlow.first()
            if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return LoadResult.Success(cachedEntries, fromCache = true)
            }
        }

        var lastError = ""
        for (url in candidateUrls()) {
            val text = runCatching {
                cleartextGate.allowUrl(url)
                jsonFetcher.fetchText(url, acceptHeader = ACCEPT)
            }.getOrElse { e ->
                lastError = e.message ?: "拉取失败"
                null
            } ?: continue
            if (text.isBlank()) {
                lastError = "内容为空"
                continue
            }
            val entries = ChangelogParser.parse(text)
            if (entries.isEmpty()) {
                lastError = "内容不是更新日志"
                continue
            }
            settings.setChangelogCache(text, System.currentTimeMillis())
            return LoadResult.Success(entries, fromCache = false)
        }

        // 线上全失败 → 回落缓存（有缓存就用缓存，别把页面打成空白）
        if (cachedEntries.isNotEmpty()) {
            return LoadResult.Success(cachedEntries, fromCache = true)
        }
        return LoadResult.Failure(lastError.ifBlank { "拉取失败" })
    }

    /** 候选 URL：当前更新源同目录的 CHANGELOG.md → 出厂默认两条（去重、保序） */
    private suspend fun candidateUrls(): List<String> {
        val result = LinkedHashSet<String>()
        siblingOf(settings.updateUrlFlow.first())?.let { result.add(it) }
        result.addAll(SettingsDataSource.DEFAULT_CHANGELOG_URLS)
        return result.toList()
    }

    /** 把 `.../update.json` 换成同目录的 `.../CHANGELOG.md`；取不到目录时返回 null */
    private fun siblingOf(url: String): String? {
        val slash = url.lastIndexOf('/')
        if (slash <= 0 || slash == url.length - 1) return null
        return url.substring(0, slash + 1) + "CHANGELOG.md"
    }
}
