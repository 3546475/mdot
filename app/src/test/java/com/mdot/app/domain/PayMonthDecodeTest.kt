package com.mdot.app.domain

import com.mdot.app.domain.model.PayMonthSheet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** 记月 JSON 往返（DataStore decode 用 nullable T 复现） */
class PayMonthDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `旧版本 JSON 缺来源字段时降级为 null 不崩溃`() {
        // v0.7.3 及更早写入的 JSON：没有 source / engineCents / syncedAt，且行名还是「调休」
        val legacy = """{"basic":[{"id":1,"name":"基本工资","amountCents":230000,"builtin":true},""" +
            """{"id":2,"name":"加班工资","amountCents":19830,"builtin":true},""" +
            """{"id":3,"name":"调休","amountCents":0,"builtin":true}],""" +
            """"subsidy":[{"id":4,"name":"其它补贴","amountCents":0,"builtin":true}],""" +
            """"deduction":[{"id":5,"name":"其它扣款","amountCents":0,"builtin":true}],""" +
            """"other":[{"id":8,"name":"社保","amountCents":0,"builtin":true}]}"""
        val back = json.decodeFromString<PayMonthSheet?>(legacy)
        assertEquals(230_000, back!!.basic.first { it.id == 1L }.amountCents)
        assertEquals(null, back.basic.first { it.id == 2L }.source)
        assertEquals(null, back.basic.first { it.id == 2L }.engineCents)
        assertEquals(null, back.basic.first { it.id == 2L }.syncedAt)
        // 行名是用户数据，不做迁移：历史月份仍叫「调休」，只有新出厂单叫「调休折现」
        assertEquals("调休", back.basic.first { it.id == PayMonthSheet.COMP_ROW_ID }.name)
    }

    @Test
    fun `nullable解码往返`() {
        val sheet = PayMonthSheet.default().let {
            it.copy(basic = it.basic.map { if (it.id == 1L) it.copy(amountCents = 230000) else it })
        }
        val text = json.encodeToString(sheet)
        val back = json.decodeFromString<PayMonthSheet?>(text)
        assertEquals(230000, back!!.basic.first { it.id == 1L }.amountCents)
    }
}
