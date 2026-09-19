package com.mdot.app.core.designsystem

import com.mdot.app.core.designsystem.component.HomeCardRegistry
import com.mdot.app.domain.model.HomeCardsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页卡片注册表解析（v0.6.0 首页卡片可编辑；v0.6.21 起单列表模型） */
class HomeCardRegistryTest {

    @Test
    fun `空列表回退整池顺序`() {
        val specs = HomeCardRegistry.resolve(emptyList())
        assertEquals(HomeCardsConfig.POOL, specs.map { it.id })
    }

    @Test
    fun `按配置顺序解析`() {
        val specs = HomeCardRegistry.resolve(listOf("entries", "data"))
        assertEquals(listOf("entries", "data"), specs.map { it.id })
    }

    @Test
    fun `未知 id 被剔除`() {
        val specs = HomeCardRegistry.resolve(listOf("heatmap", "bogus", "income"))
        assertEquals(listOf("heatmap", "income"), specs.map { it.id })
    }

    @Test
    fun `重复 id 去重`() {
        val specs = HomeCardRegistry.resolve(listOf("income", "income", "data"))
        assertEquals(listOf("income", "data"), specs.map { it.id })
    }

    @Test
    fun `配置模型：出厂默认顺序与默认隐藏`() {
        val cfg = HomeCardsConfig()
        // 出厂默认顺序（用户定 2026-09-20）：数据区 - 收入卡 - 日历统计入口 - 周柱状 - 月柱状 - 热点图
        assertEquals(
            listOf("data", "income", "entries", "weekbar", "monthbar", "heatmap"),
            cfg.order,
        )
        assertEquals(6, HomeCardsConfig.POOL.size)
        // 出厂默认隐藏（保持既有首页布局）：月柱状 / 热点图
        assertEquals(listOf("monthbar", "heatmap"), cfg.disabled)
        assertEquals(listOf("data", "income", "entries", "weekbar"), cfg.enabledCards)
        // 数据区不可隐藏
        assertEquals("data", HomeCardsConfig.DATA)
    }

    @Test
    fun `开关不影响顺序：enabledCards 只是 order 去掉 disabled`() {
        val cfg = HomeCardsConfig(
            order = listOf("data", "income", "entries", "weekbar", "monthbar", "heatmap"),
            disabled = listOf("income", "heatmap"),
        )
        assertEquals(listOf("data", "entries", "weekbar", "monthbar"), cfg.enabledCards)
        // 顺序未被开关改动
        assertEquals(listOf("data", "income", "entries", "weekbar", "monthbar", "heatmap"), cfg.order)
    }

    @Test
    fun `数据区即使被写进 disabled 也始终显示`() {
        val cfg = HomeCardsConfig(disabled = listOf("data", "income"))
        assertTrue("data" in cfg.enabledCards)
        assertEquals(
            listOf("data", "entries", "weekbar", "monthbar", "heatmap"),
            cfg.enabledCards,
        )
    }

    @Test
    fun `旧配置迁移：启用项在前、其余补后、未启用进 disabled`() {
        // 旧格式显式配置过（cards 非空）
        val legacy = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<HomeCardsConfig>("""{"cards":["data","income","entries","weekbar"]}""")
        val migrated = legacy.migrated()
        assertEquals(listOf("data", "income", "entries", "weekbar", "monthbar", "heatmap"), migrated.order)
        assertEquals(listOf("monthbar", "heatmap"), migrated.disabled)
        assertEquals(listOf("data", "income", "entries", "weekbar"), migrated.enabledCards)
    }
}
