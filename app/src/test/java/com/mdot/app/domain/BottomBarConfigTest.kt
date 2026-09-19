package com.mdot.app.domain

import com.mdot.app.domain.model.BottomBarConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底栏配置：单列表模型（完整顺序 + 关闭集合；开关不影响顺序）——v0.6.21 起 */
class BottomBarConfigTest {

    @Test
    fun `出厂默认：列表顺序按用户定、但只启用 首页 + 我的`() {
        val cfg = BottomBarConfig()
        // 列表顺序（用户定 2026-09-20；与开关无关）
        assertEquals(listOf("sync", "stats", "calendar", "export", "profile"), cfg.order)
        // 出厂默认关闭：除「我的」外全关 → 底栏只有 首页 + 我的
        assertEquals(listOf("sync", "stats", "calendar", "export"), cfg.disabled)
        assertEquals(listOf("home", "profile"), cfg.slots)
    }

    @Test
    fun `开关不影响顺序：slots 只是 order 去掉 disabled 后前挂首页`() {
        val cfg = BottomBarConfig(
            order = listOf("profile", "export", "calendar", "stats", "sync"),
            disabled = listOf("calendar", "sync"),
        )
        assertEquals(listOf("home", "profile", "export", "stats"), cfg.slots)
        // 顺序未被开关改动
        assertEquals(listOf("profile", "export", "calendar", "stats", "sync"), cfg.order)
    }

    @Test
    fun `不在池内的顺序项被忽略`() {
        val cfg = BottomBarConfig(order = listOf("bogus", "stats", "export"), disabled = emptyList())
        assertEquals(listOf("home", "stats", "export"), cfg.slots)
    }

    @Test
    fun `旧配置迁移：启用项在前、其余补后、未启用进 disabled`() {
        val legacy = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<BottomBarConfig>("""{"slots":["home","profile"]}""")
        val migrated = legacy.migrated()
        assertEquals(listOf("profile", "sync", "stats", "calendar", "export"), migrated.order)
        assertEquals(listOf("sync", "stats", "calendar", "export"), migrated.disabled)
        assertEquals(listOf("home", "profile"), migrated.slots)
    }
}
