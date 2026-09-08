package com.mdot.app.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 响应式断点判定单测（docs 03 §3.2）：600dp / 840dp 两档官方 Material 断点。
 * windowSpecForWidth 为纯函数（无 Android 依赖），可直接 JVM 运行。
 */
class WindowSpecTest {

    @Test
    fun `compact below 600dp`() {
        assertEquals(WindowSpec.COMPACT, windowSpecForWidth(0.dp))
        assertEquals(WindowSpec.COMPACT, windowSpecForWidth(411.dp))
        assertEquals(WindowSpec.COMPACT, windowSpecForWidth(599.dp))
    }

    @Test
    fun `medium from 600dp inclusive`() {
        assertEquals(WindowSpec.MEDIUM, windowSpecForWidth(600.dp))
        assertEquals(WindowSpec.MEDIUM, windowSpecForWidth(720.dp))
        assertEquals(WindowSpec.MEDIUM, windowSpecForWidth(800.dp))
        assertEquals(WindowSpec.MEDIUM, windowSpecForWidth(839.dp))
    }

    @Test
    fun `expanded from 840dp inclusive`() {
        assertEquals(WindowSpec.EXPANDED, windowSpecForWidth(840.dp))
        assertEquals(WindowSpec.EXPANDED, windowSpecForWidth(1280.dp))
        assertEquals(WindowSpec.EXPANDED, windowSpecForWidth(1920.dp))
    }

    @Test
    fun `breakpoint values match docs 03 spec`() {
        assertEquals(600.dp, AdaptiveSpecs.mediumBreakpoint)
        assertEquals(840.dp, AdaptiveSpecs.expandedBreakpoint)
        assertEquals(600.dp, AdaptiveSpecs.contentMaxWidth)
        assertEquals(720.dp, AdaptiveSpecs.twoPaneMaxWidth)
        assertEquals(640.dp, AdaptiveSpecs.sheetMaxWidth)
    }
}
