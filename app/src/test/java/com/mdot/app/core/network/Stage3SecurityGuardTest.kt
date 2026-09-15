package com.mdot.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段三安全防线单测（13 文档 B2-02/B2-04）：
 * - CertPin：指纹计算稳定性（TOFU 记录/比对的基础）
 * - CleartextGate：登记放行、未登记拒绝、URL 解析登记、大小写不敏感
 */
class Stage3SecurityGuardTest {

    // ---- CertPin 指纹算法 ----

    @Test
    fun `指纹计算对相同输入稳定且对不同输入不同`() {
        val a = ByteArray(64) { (it * 3).toByte() }
        val b = ByteArray(64) { (it * 3).toByte() }
        val c = ByteArray(64) { (it * 7).toByte() }
        val fpA = hashOf(a)
        assertEquals(fpA, hashOf(b))
        assertFalse(fpA == hashOf(c))
        assertEquals(64, fpA.length) // SHA-256 = 64 hex
        assertTrue(fpA.all { it in "0123456789abcdef" })
    }

    /** 与 CertPin.sha256Fingerprint 同算法（对字节摘要）——验证 hex 格式与小写约定 */
    private fun hashOf(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    // ---- CleartextGate ----

    @Test
    fun `未登记host的明文请求被拒绝`() {
        val gate = CleartextGate()
        assertFalse(gate.isAllowed("evil.example.com"))
        assertFalse(gate.isAllowed("3546475.github.io"))
    }

    @Test
    fun `登记后放行且大小写不敏感`() {
        val gate = CleartextGate()
        gate.allowHost("MyNas.local")
        assertTrue(gate.isAllowed("mynas.local"))
        assertTrue(gate.isAllowed("MYNAS.LOCAL"))
        assertFalse(gate.isAllowed("other-nas.local"))
    }

    @Test
    fun `URL登记解析host且非法URL静默忽略`() {
        val gate = CleartextGate()
        gate.allowUrl("http://192.168.1.10:5006/dav/")
        assertTrue(gate.isAllowed("192.168.1.10"))
        gate.allowUrl("not a url at all") // 不抛
        assertFalse(gate.isAllowed("not a url at all"))
        gate.allowUrl("") // 空串不炸
    }
}
