package com.mdot.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 致谢名单解析测试（关于页致谢卡）。
 *
 * 名单维护接口：`strings_settings.xml` 的 `settings_ack_names` string-array，
 * 每项语法为 `@名字` 或 `@名字|短标签`。本测试锁住解析契约——标签是可选增强，
 * 缺失/空标签/多余分隔符都不得导致名单丢失或错位。
 */
class AckEntryParseTest {

    @Test
    fun `无标签时只解析名字`() {
        val list = parseAckEntries(listOf("@Littlele", "@Ting"))
        assertEquals(2, list.size)
        assertEquals("@Littlele", list[0].name)
        assertNull(list[0].tag)
        assertNull(list[1].tag)
    }

    @Test
    fun `带标签时拆分名字与标签`() {
        val list = parseAckEntries(listOf("@Littlele|早期陪伴", "@Ting|灵感来源"))
        assertEquals("@Littlele", list[0].name)
        assertEquals("早期陪伴", list[0].tag)
        assertEquals("灵感来源", list[1].tag)
    }

    @Test
    fun `标签为空串视为无标签`() {
        val list = parseAckEntries(listOf("@A|", "@B|   "))
        assertNull(list[0].tag)
        assertNull(list[1].tag)
    }

    @Test
    fun `名字内多余竖线不破坏解析（limit=2 保留剩余部分）`() {
        val list = parseAckEntries(listOf("@A|x|y"))
        assertEquals("@A", list[0].name)
        assertEquals("x|y", list[0].tag)
    }

    @Test
    fun `空白项被过滤且两侧空格被裁剪`() {
        val list = parseAckEntries(listOf("  ", "@C  |  测试  "))
        assertEquals(1, list.size)
        assertEquals("@C", list[0].name)
        assertEquals("测试", list[0].tag)
    }

    @Test
    fun `空名单返回空列表（UI 需据此隐藏名单区）`() {
        assertTrue(parseAckEntries(emptyList()).isEmpty())
    }
}
