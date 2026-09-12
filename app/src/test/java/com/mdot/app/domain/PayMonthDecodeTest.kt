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
    fun `nullable解码往返`() {
        val sheet = PayMonthSheet.default().let {
            it.copy(basic = it.basic.map { if (it.id == 1L) it.copy(amountCents = 230000) else it })
        }
        val text = json.encodeToString(sheet)
        val back = json.decodeFromString<PayMonthSheet?>(text)
        assertEquals(230000, back!!.basic.first { it.id == 1L }.amountCents)
    }
}
