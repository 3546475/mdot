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

/** 一个 WebDAV 存储源（v0.5fix 多存储源模型） */
@Serializable
data class WebDavSource(
    val id: String = "",
    val name: String = "",
    val creds: WebDavCreds = WebDavCreds(),
)

/** 一个 S3 存储源（v0.5fix 多存储源模型） */
@Serializable
data class S3Source(
    val id: String = "",
    val name: String = "",
    val creds: S3Creds = S3Creds(),
)

/** 全部存储源 + 当前选中（全局唯一正在使用的源；id 跨类型唯一） */
@Serializable
data class StorageSources(
    val webdav: List<WebDavSource> = emptyList(),
    val s3: List<S3Source> = emptyList(),
    val selectedId: String? = null,
) {
    fun selectedWebDav(): WebDavSource? =
        selectedId?.let { id -> webdav.firstOrNull { it.id == id } }

    fun selectedS3(): S3Source? =
        selectedId?.let { id -> s3.firstOrNull { it.id == id } }
}

/**
 * 凭据保险库（06 文档 §8）：配置 JSON → AndroidKeyStore AES-256-GCM 加密 → 密文存 DataStore。
 * 与数据库密钥共用同一主密钥（alias = mdot_master）。
 */
@Singleton
class CredentialVault @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- 多存储源（v0.5fix；旧单源密文首次读取时自动迁移） ----

    /** 读取全部存储源；首次（新 key 不存在）时迁移旧版单 WebDAV/S3 配置 */
    suspend fun loadSources(): StorageSources {
        val current = dataStore.data.first().get(SOURCES_KEY)
        if (current != null) {
            return runCatching { decryptDto(SOURCES_KEY, StorageSources.serializer()) }
                .getOrNull() ?: StorageSources()
        }
        // 迁移旧单源配置（WebDAV 优先于 S3，与旧 reloadConfiguration 语义一致）
        val legacyWebDav = decryptDto(WEBDAV_KEY, WebDavCreds.serializer())
        val legacyS3 = decryptDto(S3_KEY, S3Creds.serializer())
        val webdav = legacyWebDav?.takeIf { it.baseUrl.isNotBlank() }
            ?.let { listOf(WebDavSource(id = "w-legacy", name = "WebDAV 存储源", creds = it)) }
            ?: emptyList()
        val s3 = legacyS3?.takeIf { it.endpoint.isNotBlank() }
            ?.let { listOf(S3Source(id = "s-legacy", name = "S3 存储源", creds = it)) }
            ?: emptyList()
        if (webdav.isEmpty() && s3.isEmpty()) return StorageSources()
        val selectedId = when {
            webdav.isNotEmpty() -> "w-legacy"
            s3.isNotEmpty() -> "s-legacy"
            else -> null
        }
        val migrated = StorageSources(webdav = webdav, s3 = s3, selectedId = selectedId)
        encryptDto(SOURCES_KEY, StorageSources.serializer(), migrated)
        // 清旧 key，避免重复迁移
        dataStore.edit {
            it.remove(WEBDAV_KEY)
            it.remove(S3_KEY)
        }
        return migrated
    }

    suspend fun saveSources(sources: StorageSources) {
        encryptDto(SOURCES_KEY, StorageSources.serializer(), sources)
    }

    // ---- 旧单源读取（仅供迁移与兼容检查） ----

    suspend fun loadWebDav(): WebDavCreds? = decryptDto(WEBDAV_KEY, WebDavCreds.serializer())

    suspend fun loadS3(): S3Creds? = decryptDto(S3_KEY, S3Creds.serializer())

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
        private val SOURCES_KEY = stringPreferencesKey("storage_sources_cipher")
    }
}
