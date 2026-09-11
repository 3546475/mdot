package com.mdot.app.core.designsystem

import com.mdot.app.core.designsystem.component.HomeCardRegistry
import com.mdot.app.domain.model.HomeCardsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页卡片注册表解析（v0.6.0 首页卡片可编辑） */
class HomeCardRegistryTest {

    @Test
    fun `null 配置回退默认池顺序`() {
        val specs = HomeCardRegistry.resolve(null)
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
    fun `少于最少张数回退默认`() {
        val specs = HomeCardRegistry.resolve(emptyList())
        assertEquals(HomeCardsConfig.POOL, specs.map { it.id })
    }

    @Test
    fun `配置模型默认未配置`() {
        assertTrue(HomeCardsConfig().cards == null)
        assertEquals(6, HomeCardsConfig.POOL.size)
        assertEquals(3, HomeCardsConfig.DEFAULT_CARDS.size)
        assertTrue(HomeCardsConfig.MIN_CARDS >= 1)
    }
}
