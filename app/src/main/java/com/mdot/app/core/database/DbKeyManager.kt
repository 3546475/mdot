package com.mdot.app.core.database

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库密钥管理（08 文档 §6）：
 * 随机 32B 库密钥由 AndroidKeyStore 中 AES-256-GCM 主密钥包裹后存 DataStore，明文不落盘。
 * 密文解不开（系统备份/OEM 换机把 DataStore 迁了过来，而 KeyStore 主密钥设备绑定不随迁，
 * 13 文档 B2-01）时不直抛：生成新钥覆盖密文并标记 reset，由调用方隔离旧加密库——保启动，
 * 旧数据视同丢失（可从应用内备份恢复）。
 * Keystore 自身异常（masterKey/wrap 失败）仍直接抛出（无法安全持久化，不降级明文库）。
 */
@Singleton
class DbKeyManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * 测试注入点：Robolectric 不带 AndroidKeyStore provider，单测注入纯软件密钥
     * 以覆盖换钥决策矩阵；生产恒 null 走 KeyStore 真链路（真机冒烟覆盖）。
     */
    @Volatile
    var testMasterKey: SecretKey? = null

    /** B3-16：读-判-写原子化——并发首调会各自生成密钥、后写者胜出，先返回者拿到"幻影密钥" */
    private val keyMutex = Mutex()

    suspend fun getOrCreateDbKey(): DbKeyOutcome = keyMutex.withLock {
        val existing = dataStore.data.firstOrNull()?.get(KEY_CIPHER)
        if (existing != null) {
            val unwrapped = runCatching { unwrap(decode(existing)) }
            if (unwrapped.isSuccess) return DbKeyOutcome(unwrapped.getOrThrow(), reset = false, cipherExisted = true)
            // 密文在但解不开：主密钥已换（allowBackup=false 落地前的系统备份恢复、OEM 特权迁移）。
            // 旧钥不可恢复——生成新钥覆盖密文，reset 标记交由调用方隔离旧库，防启动崩溃死循环
            android.util.Log.w(TAG, "库密钥密文解不开（主密钥已换），生成新钥——旧加密库将由调用方隔离")
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(passphrase)
        dataStore.edit { it[KEY_CIPHER] = encode(wrapped) }
        DbKeyOutcome(passphrase, reset = existing != null, cipherExisted = existing != null)
    }

    private fun masterKey(): SecretKey =
        testMasterKey ?: keyStoreMasterKey()

    private fun keyStoreMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, IV_SIZE)
        val ct = wrapped.copyOfRange(IV_SIZE, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val TAG = "DbKeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "mdot_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private val KEY_CIPHER = stringPreferencesKey("db_key_cipher")
    }
}

/** getOrCreateDbKey 的结果 */
class DbKeyOutcome(
    val passphrase: ByteArray,
    /** true = 原 DataStore 密文存在但当前主密钥解不开（已生成新钥覆盖）——旧加密库与新钥不匹配，调用方须隔离重建 */
    val reset: Boolean,
    /** true = 读取时 DataStore 里存在密钥密文（reset=false 时即"成功解开"；reset=true 亦为 true） */
    val cipherExisted: Boolean,
)
