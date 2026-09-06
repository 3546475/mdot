package com.mdot.app.core.database

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
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
 * Keystore 异常时直接抛出（不降级明文库）。
 */
@Singleton
class DbKeyManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun getOrCreatePassphrase(): ByteArray {
        val existing = dataStore.data.firstOrNull()?.get(KEY_CIPHER)
        if (existing != null) return unwrap(decode(existing))
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(passphrase)
        dataStore.edit { it[KEY_CIPHER] = encode(wrapped) }
        return passphrase
    }

    private fun masterKey(): SecretKey {
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
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "mdot_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private val KEY_CIPHER = stringPreferencesKey("db_key_cipher")
    }
}
