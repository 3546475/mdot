package com.mdot.app.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 同步策略纯逻辑测试：
 * - remotePath：WebDAV 必须带 mdot/ 前缀（回归：history 清理 PROPFIND 404 永不清理）
 * - selectHistoryToDelete：保留最新 5 份语义
 * - lastChangeTrigger：同值重发射不触发（回归：backupNow 写 DataStore 自触发 30s 死循环）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPolicyTest {

    // ---- remotePath：WebDAV rel 前缀 ----

    @Test
    fun `remotePath adds mdot prefix for WebDAV only`() {
        assertEquals(
            "mdot/backup/history",
            SyncPolicy.remotePath(ProviderKind.WEBDAV, "backup/history"),
        )
        assertEquals(
            "backup/history",
            SyncPolicy.remotePath(ProviderKind.S3, "backup/history"),
        )
        assertEquals(
            "backup/history",
            SyncPolicy.remotePath(ProviderKind.NONE, "backup/history"),
        )
    }

    // ---- selectHistoryToDelete：滚动保留 ----

    @Test
    fun `keeps newest history and deletes oldest beyond limit`() {
        val files = (1L..7L).map { n ->
            RemoteFileMeta(
                path = "mdot/backup/history/backup-2026090$n.zip",
                size = 1,
                lastModified = Instant.ofEpochSecond(n),
            )
        }
        val toDelete = SyncPolicy.selectHistoryToDelete(files, maxHistory = 5)
        // 时间最旧的两份（epoch 1、2）应被删，最新 5 份保留
        assertEquals(listOf("backup-20260902.zip", "backup-20260901.zip"), toDelete)
    }

    @Test
    fun `no deletion at or below limit and non-zip or dirs ignored`() {
        val files = listOf(
            RemoteFileMeta("mdot/backup/history/a.zip", 1, Instant.ofEpochSecond(3)),
            RemoteFileMeta("mdot/backup/history/b.zip", 1, Instant.ofEpochSecond(2)),
            RemoteFileMeta("mdot/backup/history/c.zip", 1, Instant.ofEpochSecond(1)),
            // 非 zip 与目录不应参与计数/删除
            RemoteFileMeta("mdot/backup/history/current.json", 1, Instant.ofEpochSecond(9)),
            RemoteFileMeta("mdot/backup/history/", 0, Instant.ofEpochSecond(8)),
        )
        assertTrue(SyncPolicy.selectHistoryToDelete(files, maxHistory = 5).isEmpty())
    }

    @Test
    fun `sorts by lastModified descending not by listing order`() {
        // 列表乱序返回，仍应按时间删最旧
        val files = listOf(
            RemoteFileMeta("mdot/backup/history/old.zip", 1, Instant.ofEpochSecond(1)),
            RemoteFileMeta("mdot/backup/history/new.zip", 1, Instant.ofEpochSecond(10)),
            RemoteFileMeta("mdot/backup/history/mid.zip", 1, Instant.ofEpochSecond(5)),
        )
        assertEquals(listOf("old.zip"), SyncPolicy.selectHistoryToDelete(files, maxHistory = 2))
    }

    // ---- lastChangeTrigger：自动备份触发链 ----

    /** MutableSharedFlow 每次 emit 都会重发给订阅者（即使同值）——模拟 DataStore 每次 edit 重发射。 */
    @Test
    fun `same value re-emission does not re-trigger`() = runTest {
        val upstream = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 16)
        val triggered = mutableListOf<Long>()
        // 初始快照：DataStore 订阅后先发射当前值，被 drop(1) 丢弃（不是变更）
        upstream.emit(0L)
        backgroundScope.launch {
            SyncPolicy.lastChangeTrigger(upstream, debounceMs = 30_000).collect { triggered += it }
        }
        runCurrent()

        // 用户记一条加班 → 变更时间戳
        upstream.emit(100L)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(listOf(100L), triggered)

        // backupNow 写 DataStore（锚点/时间）→ 同值重发射 → 不得再触发
        upstream.emit(100L)
        advanceTimeBy(120_000); runCurrent()
        assertEquals("同值重发射不应再触发备份", listOf(100L), triggered)

        // 真正的新变更 → 触发
        upstream.emit(200L)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(listOf(100L, 200L), triggered)
    }

    @Test
    fun `rapid changes debounce to the latest value only`() = runTest {
        val upstream = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 16)
        val triggered = mutableListOf<Long>()
        upstream.emit(0L)
        backgroundScope.launch {
            SyncPolicy.lastChangeTrigger(upstream, debounceMs = 30_000).collect { triggered += it }
        }
        runCurrent()

        // 30s 防抖窗口内的连续变更只触发最后一次
        upstream.emit(1L); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        upstream.emit(2L); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        upstream.emit(3L); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertTrue("窗口内不应触发", triggered.isEmpty())

        advanceTimeBy(25_000); runCurrent()
        assertEquals(listOf(3L), triggered)
    }
}
