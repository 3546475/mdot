package com.mdot.app.domain

import com.mdot.app.domain.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/** Money.yuanTrimText（记月条目去尾零展示）用例 */
class MoneyYuanTrimTextTest {

    @Test
    fun `整数元不带小数`() {
        assertEquals("2300", Money.yuanTrimText(230_000))
        assertEquals("0", Money.yuanTrimText(0))
    }

    @Test
    fun `一位小数去尾零`() {
        assertEquals("0.1", Money.yuanTrimText(10))
        assertEquals("2300.5", Money.yuanTrimText(230_050))
    }

    @Test
    fun `两位小数保留`() {
        assertEquals("951.73", Money.yuanTrimText(95_173))
        assertEquals("0.05", Money.yuanTrimText(5))
    }

    @Test
    fun `负数带符号`() {
        assertEquals("-0.1", Money.yuanTrimText(-10))
        assertEquals("-2300", Money.yuanTrimText(-230_000))
    }
}
