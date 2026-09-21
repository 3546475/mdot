package com.mdot.app.domain

import com.mdot.app.domain.model.AppearanceConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外观配置：「隐藏农历日期」开关默认关（= 默认显示农历），且老配置不因新增字段失效 */
class AppearanceConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `出厂默认不隐藏农历`() {
        assertFalse(AppearanceConfig().hideLunarDate)
    }

    @Test
    fun `老配置（无该字段）反序列化后仍显示农历`() {
        val old = json.decodeFromString<AppearanceConfig>("""{"themeMode":"DARK","paletteId":"teal"}""")
        assertFalse(old.hideLunarDate)
        assertEquals(AppearanceConfig.PALETTE_TEAL, old.paletteId)
    }

    @Test
    fun `开关序列化往返保持`() {
        val cfg = AppearanceConfig(hideLunarDate = true)
        val back = json.decodeFromString<AppearanceConfig>(json.encodeToString(AppearanceConfig.serializer(), cfg))
        assertTrue(back.hideLunarDate)
        // 其余字段不受影响
        assertEquals(cfg.themeMode, back.themeMode)
        assertEquals(cfg.paletteId, back.paletteId)
        assertEquals(cfg.dynamicColor, back.dynamicColor)
        assertEquals(cfg.sheetBackdropMode, back.sheetBackdropMode)
    }
}
