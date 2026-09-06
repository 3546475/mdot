package com.mdot.app.core.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mdot.app.core.util.AppResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 网盘凭据（永不出备份包）；旧密文 JSON 缺 trustSelfSigned 字段时按默认 false 解码 */
@Serializable
data class WebDavCreds(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
)

@Serializable
data class S3Creds(
    val endpoint: String = "",
    val bucket: String = "",
    val region: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val pathPrefix: String = "mdot/backup/",
    val trustSelfSigned: Boolean = false,
)

/**
 * 凭据保险库（06 文档 §8）：配置 JSON → AndroidKeyStore AES-256-GCM 加密 → 密文存 DataStore。
 * 与数据库密钥共用同一主密钥（alias = mdot_master）。
 */
@Singleton
class CredentialVault @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- WebDAV ----

    suspend fun loadWebDav(): WebDavCreds? = decryptDto(WEBDAV_KEY, WebDavCreds.serializer())

    suspend fun saveWebDav(creds: WebDavCreds) {
        encryptDto(WEBDAV_KEY, WebDavCreds.serializer(), creds)
    }

    suspend fun clearWebDav() = dataStore.edit { it.remove(WEBDAV_KEY) }

    // ---- S3 ----

    suspend fun loadS3(): S3Creds? = decryptDto(S3_KEY, S3Creds.serializer())

    suspend fun saveS3(creds: S3Creds) {
        encryptDto(S3_KEY, S3Creds.serializer(), creds)
    }

    suspend fun clearS3() = dataStore.edit { it.remove(S3_KEY) }

    // ---- 通用加解密 ----

    private suspend fun <T> decryptDto(key: Preferences.Key<String>, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val cipher = dataStore.data.first().get(key) ?: return null
        return runCatching {
            val (iv, ct) = unwrap(decode(cipher))
            val plain = cipherForDecrypt(iv).doFinal(ct)
            json.decodeFromString(serializer, plain.decodeToString())
        }.getOrNull()
    }

    private suspend fun <T> encryptDto(key: Preferences.Key<String>, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        val cipherForEncrypt = cipherForEncrypt()
        val ct = cipherForEncrypt.doFinal(json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
        val wrapped = cipherForEncrypt.iv + ct
        dataStore.edit { it[key] = encode(wrapped) }
    }

    private fun cipherForEncrypt(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher
    }

    private fun cipherForDecrypt(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher
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

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    private fun unwrap(wrapped: ByteArray): Pair<ByteArray, ByteArray> =
        wrapped.copyOfRange(0, IV_SIZE) to wrapped.copyOfRange(IV_SIZE, wrapped.size)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "mdot_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private val WEBDAV_KEY = stringPreferencesKey("webdav_cred_cipher")
        private val S3_KEY = stringPreferencesKey("s3_cred_cipher")
    }
}
