package com.mdot.app.core.designsystem

import com.mdot.app.core.designsystem.component.HomeCardRegistry
import com.mdot.app.domain.model.HomeCardContents
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

    // ---- 卡片内容自定义（卡片内容编辑；暂只「数据区」开放）----

    @Test
    fun `内容自定义：没配置时用出厂默认`() {
        val cfg = HomeCardsConfig()
        assertEquals(HomeCardContents.DATA_OT_HOURS, cfg.contentOf(HomeCardsConfig.DATA))
        // 不支持自定义的卡回落到空串（UI 据此不给编辑入口）
        assertEquals("", cfg.contentOf("income"))
    }

    @Test
    fun `内容自定义：配置后按配置显示`() {
        val cfg = HomeCardsConfig(content = mapOf(HomeCardsConfig.DATA to listOf(HomeCardContents.DATA_NET_PAY)))
        assertEquals(HomeCardContents.DATA_NET_PAY, cfg.contentOf(HomeCardsConfig.DATA))
    }

    @Test
    fun `内容自定义：存了未知内容项时回落到出厂默认`() {
        val cfg = HomeCardsConfig(content = mapOf(HomeCardsConfig.DATA to listOf("bogus")))
        assertEquals(HomeCardContents.DATA_OT_HOURS, cfg.contentOf(HomeCardsConfig.DATA))
    }

    @Test
    fun `内容自定义迁移：丢掉已下线卡片与不属于该卡的内容项`() {
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<HomeCardsConfig>(
                """{"content":{"data":["netPay"],"income":["netPay"],"gone":["netPay"],"heatmap":["bogus"]}}"""
            )
        val migrated = old.migrated()
        // 只留「data → netPay」：income 不支持自定义、gone 已下线、heatmap 的 bogus 不是合法内容项
        assertEquals(mapOf(HomeCardsConfig.DATA to listOf(HomeCardContents.DATA_NET_PAY)), migrated.content)
        assertEquals(HomeCardContents.DATA_NET_PAY, migrated.contentOf(HomeCardsConfig.DATA))
        assertEquals("", migrated.contentOf("income"))
    }

    // ---- 快捷入口（多选）----

    @Test
    fun `快捷入口出厂默认是日历与统计`() {
        val cfg = HomeCardsConfig()
        assertEquals(
            listOf(HomeCardContents.ENTRY_CALENDAR, HomeCardContents.ENTRY_STATS),
            cfg.contentList(HomeCardsConfig.ENTRIES),
        )
        assertTrue(HomeCardContents.isMultiSelect(HomeCardsConfig.ENTRIES))
    }

    @Test
    fun `快捷入口多选且保序`() {
        val picked = listOf(
            HomeCardContents.ENTRY_PAYMONTH,
            HomeCardContents.ENTRY_CALENDAR,
            HomeCardContents.ENTRY_PROFILE,
        )
        val cfg = HomeCardsConfig(content = mapOf(HomeCardsConfig.ENTRIES to picked))
        // 顺序即显示顺序，不重排
        assertEquals(picked, cfg.contentList(HomeCardsConfig.ENTRIES))
    }

    @Test
    fun `快捷入口混入非法项时被过滤，全非法则回落出厂默认`() {
        val mixed = HomeCardsConfig(
            content = mapOf(HomeCardsConfig.ENTRIES to listOf(HomeCardContents.ENTRY_PAYMONTH, "bogus")),
        )
        assertEquals(listOf(HomeCardContents.ENTRY_PAYMONTH), mixed.contentList(HomeCardsConfig.ENTRIES))

        val allBad = HomeCardsConfig(content = mapOf(HomeCardsConfig.ENTRIES to listOf("bogus")))
        assertEquals(HomeCardContents.defaultsOf(HomeCardsConfig.ENTRIES), allBad.contentList(HomeCardsConfig.ENTRIES))
    }
}
