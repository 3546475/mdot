package com.mdot.app.core.sync

import java.time.Instant

/** 远端文件元信息 */
data class RemoteFileMeta(
    val path: String,
    val size: Long,
    val lastModified: Instant,
    val etag: String? = null,
)

data class PutResult(val etag: String?)

/**
 * 存储源抽象（06 文档 §7）：WebDAV / S3 兼容对象存储 / 未来官方云。
 * 新增一种存储 = 新增一个实现，SyncEngine 与 UI 零改动。
 * 约定：path 为相对 BaseDir 的路径（如 "backup/current.zip"）。
 */
interface StorageProvider {
    val kind: ProviderKind

    /** 确保根目录存在（WebDAV 逐级 MKCOL；S3 无需） */
    suspend fun ensureBaseDir(): Result<Unit>

    suspend fun put(path: String, bytes: ByteArray, ifMatch: String? = null): Result<PutResult>

    /** null = 404（云端无此文件） */
    suspend fun get(path: String): Result<ByteArray?>

    /** null = 404 */
    suspend fun head(path: String): Result<RemoteFileMeta?>

    suspend fun list(path: String): Result<List<RemoteFileMeta>>

    /** 404 视为成功 */
    suspend fun delete(path: String): Result<Unit>
}

enum class ProviderKind { NONE, WEBDAV, S3 }

/** 凭据配置（Keystore 加密后存 DataStore，永不出备份包） */
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** 信任自签名证书（自建 NAS HTTPS 场景，用户显式开启） */
    val trustSelfSigned: Boolean = false,
) {
    companion object {
        val EMPTY = WebDavConfig("", "", "")
    }
}

data class S3Config(
    val endpoint: String,
    val bucket: String,
    val region: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val pathPrefix: String = "mdot/backup/",
    /** 信任自签名证书（自建 MinIO 等场景，用户显式开启） */
    val trustSelfSigned: Boolean = false,
) {
    companion object {
        val EMPTY = S3Config("", "", "", "", "")
    }
}
