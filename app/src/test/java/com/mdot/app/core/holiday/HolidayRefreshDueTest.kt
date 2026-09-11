package com.mdot.app.core.holiday

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 节假日库自动刷新节流判定（纯函数） */
class HolidayRefreshDueTest {

    private val DAY = 24 * 60 * 60 * 1000L
    private val WEEK = 7L * DAY
    private val now = 1_000_000L * DAY // 任意基准

    @Test
    fun `从未拉取过立即到期`() {
        assertTrue(holidayRefreshDue(0L, now))
    }

    @Test
    fun `未满7天不到期`() {
        assertFalse(holidayRefreshDue(now - 6L * DAY, now))
        assertFalse(holidayRefreshDue(now - 1L, now))
    }

    @Test
    fun `满7天到期`() {
        assertTrue(holidayRefreshDue(now - 7L * DAY, now))
        assertTrue(holidayRefreshDue(now - WEEK - 1L, now))
    }

    @Test
    fun `失败短节流回退语义`() {
        // 失败后把 lastFetchAt 记为 now-6天 → 1 天后到期（now' = now+DAY）
        val afterFailure = now - (WEEK - DAY)
        assertFalse("失败当天不重试", holidayRefreshDue(afterFailure, now))
        assertTrue("失败 1 天后可重试", holidayRefreshDue(afterFailure, now + DAY))
    }

    @Test
    fun `时钟异常（未来时间戳）不崩溃`() {
        assertFalse(holidayRefreshDue(now + WEEK, now))
    }
}
