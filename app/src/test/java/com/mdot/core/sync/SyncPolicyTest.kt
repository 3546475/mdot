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

/**
 * 同步策略纯逻辑测试：
 * - remotePath：WebDAV 必须带 mdot/ 前缀（回归：history 清理 PROPFIND 404 永不清理）
 * - lastChangeTrigger：自动备份触发链（丢弃初始快照 / 同值去重防自触发 / 防抖取最新）
 *
 * 注：history 滚动保留算法（selectHistoryToDelete）已随"只保留 current.zip"的备份模型删除
 * （13 文档 B3-09），对应测试同步移除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPolicyTest {

    // ---- remotePath ----

    @Test
    fun `webdav 路径带 mdot 前缀而 s3 不加`() {
        assertEquals("mdot/backup/current.zip", SyncPolicy.remotePath(ProviderKind.WEBDAV, "backup/current.zip"))
        assertEquals("backup/current.zip", SyncPolicy.remotePath(ProviderKind.S3, "backup/current.zip"))
        assertEquals("backup/current.zip", SyncPolicy.remotePath(ProviderKind.NONE, "backup/current.zip"))
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
