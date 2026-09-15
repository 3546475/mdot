package com.mdot.app.core.database

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

/**
 * DbKeyManager 换钥自愈路径单测（13 文档 B2-01 修复的回归防线）：
 * DataStore 密文完好但主密钥已换（系统备份恢复/OEM 迁移场景）→ GCM 解密 AEADBadTagException
 * → 不抛出、生成新钥覆盖、reset=true；由调用方（Modules.quarantineOldDatabase）隔离旧库。
 *
 * Robolectric 不带 AndroidKeyStore provider——经 testMasterKey 注入纯软件 AES 钥
 * 覆盖决策矩阵；KeyStore 真链路由真机冒烟覆盖。
 *
 * 覆盖矩阵：
 * - 新装：无密文 → 生成 32B 密钥，reset=false
 * - 密文完好：解开 → 原样返回，reset=false（幂等，不换钥）
 * - 主密钥被换（GCM tag 必败）→ 新钥覆盖，reset=true，且新密文可正常解开（再次读 reset=false 同钥）
 */
@RunWith(RobolectricTestRunner::class)
class DbKeyManagerTest {

    private lateinit var context: Context
    private val key = stringPreferencesKey("db_key_cipher")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newManager(fileName: String, master: javax.crypto.SecretKey): DbKeyManager =
        DbKeyManager(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile(fileName) },
            )
        ).apply { testMasterKey = master }

    private fun newManagerWithStore(fileName: String, master: javax.crypto.SecretKey): Pair<DbKeyManager, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(fileName) },
        )
        return DbKeyManager(store).apply { testMasterKey = master } to store
    }

    private fun aesKey(): javax.crypto.SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `新装生成密钥且不标记reset`() = runBlocking {
        val m = newManager("t_new_${System.nanoTime()}", aesKey())
        val out = m.getOrCreateDbKey()
        assertEquals(32, out.passphrase.size)
        assertFalse(out.reset)
    }

    @Test
    fun `密文完好时幂等返回同钥`() = runBlocking {
        val m = newManager("t_idem_${System.nanoTime()}", aesKey())
        val first = m.getOrCreateDbKey()
        val second = m.getOrCreateDbKey()
        assertFalse(second.reset)
        assertArrayEquals(first.passphrase, second.passphrase)
    }

    @Test
    fun `主密钥被换后读取触发换钥自愈`() = runBlocking {
        val fileName = "t_rekey_${System.nanoTime()}"
        val keyA = aesKey()
        val (mA, store) = newManagerWithStore(fileName, keyA)
        val original = mA.getOrCreateDbKey()
        assertFalse(original.reset)

        // 主密钥被换：同一 DataStore、另一把软钥（模拟新设备的 KeyStore）
        val mB = DbKeyManager(store).apply { testMasterKey = aesKey() }
        val healed = mB.getOrCreateDbKey()
        assertTrue("主密钥被换后应触发 reset", healed.reset)
        assertEquals(32, healed.passphrase.size)

        // 换钥后密文可正常解开：同钥再读不 reset
        val again = mB.getOrCreateDbKey()
        assertFalse(again.reset)
        assertArrayEquals(healed.passphrase, again.passphrase)

        // 覆盖后的密文确实是新钥 wrap 的（旧钥 manager 再读也 reset——密文已被换）
        val mB2 = DbKeyManager(store).apply { testMasterKey = keyA }
        assertTrue(mB2.getOrCreateDbKey().reset)
    }

    @Test
    fun `密文为随机垃圾时触发换钥自愈`() = runBlocking {
        val fileName = "t_garbage_${System.nanoTime()}"
        val (m, store) = newManagerWithStore(fileName, aesKey())
        m.getOrCreateDbKey() // 占键
        store.edit { it[key] = android.util.Base64.encodeToString(
            ByteArray(60) { (it * 13 + 5).toByte() }, android.util.Base64.NO_WRAP) }
        val out = m.getOrCreateDbKey()
        assertTrue("垃圾密文应触发 reset", out.reset)
        assertEquals(32, out.passphrase.size)
    }
}
