package com.mdot.app.core.sync

import com.mdot.app.core.network.executeWithBackoff
import com.mdot.app.core.network.withTrustAllCerts

import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 存储源（05 文档 §4）：MKCOL 建目录、PUT 上传、GET 下载、
 * PROPFIND 列目录、DELETE 删除；Basic 认证；坚果云须用应用密码。
 * trustSelfSigned 开启时用信任所有证书的派生 client（自建 NAS 自签 HTTPS）。
 */
@Singleton
class WebDavProvider @Inject constructor(
    private val client: OkHttpClient,
    @com.mdot.app.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageProvider {

    override val kind: ProviderKind = ProviderKind.WEBDAV

    private lateinit var config: WebDavConfig

    @Volatile
    private var trustAllClient: OkHttpClient? = null

    fun configure(config: WebDavConfig): WebDavProvider {
        this.config = config
        trustAllClient = if (config.trustSelfSigned) client.withTrustAllCerts() else null
        return this
    }

    private fun httpClient(): OkHttpClient = trustAllClient ?: client

    private fun requireConfig(): WebDavConfig =
        if (::config.isInitialized) config else throw IOException("WebDAV 未配置")

    private fun url(path: String): String {
        val cfg = requireConfig()
        val base = cfg.baseUrl.trimEnd('/')
        val rel = path.trimStart('/')
        return "$base/$rel"
    }

    private fun authHeader(): String {
        val cfg = requireConfig()
        val raw = "${cfg.username}:${cfg.password}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private suspend fun exec(request: Request): Pair<Int, okhttp3.Response> =
        withContext(ioDispatcher) {
            httpClient().newCall(request).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful && resp.code != 207) {
                    throw mapError(resp.code, body)
                }
                resp.code to resp
            }
        } as Pair<Int, okhttp3.Response>

    private fun mapError(code: Int, body: String?): IOException {
        val hint = when (code) {
            401 -> "账号或密码（应用密码）不正确"
            403 -> "该账号无写入权限"
            429 -> "网盘限流，请稍后再试"
            in 500..599 -> "网盘服务异常，稍后重试"
            else -> "HTTP $code"
        }
        return IOException(hint + if (body.isNullOrBlank()) "" else "（$code）")
    }

    override suspend fun ensureBaseDir(): Result<Unit> = runCatching {
        // 逐级 MKCOL：mdot/ → mdot/backup/ → history/（405=已存在 视为成功）
        val cfg = requireConfig()
        val base = cfg.baseUrl.trimEnd('/')
        val segments = listOf("mdot", "mdot/backup", "mdot/backup/history")
        segments.forEach { seg ->
            val req = Request.Builder()
                .url("$base/$seg/")
                .method("MKCOL", null)
                .header("Authorization", authHeader())
                .build()
            httpClient().executeWithBackoff(req, ioDispatcher).use { resp ->
                val code = resp.code
                if (!(code in 200..299 || code == 405)) {
                    throw mapError(code, resp.body?.string())
                }
            }
        }
        Unit
    }

    override suspend fun put(path: String, bytes: ByteArray, ifMatch: String?): Result<PutResult> =
        runCatching {
            val builder = Request.Builder()
                .url(url(path))
                .put(bytes.toRequestBody("application/zip".toMediaType()))
                .header("Authorization", authHeader())
            ifMatch?.let { builder.header("If-Match", it) }
            val (code, resp) = exec(builder.build())
            PutResult(etag = resp.header("ETag"))
        }

    override suspend fun get(path: String): Result<ByteArray?> = runCatching {
        val req = Request.Builder()
            .url(url(path))
            .get()
            .header("Authorization", authHeader())
            .build()
        httpClient().executeWithBackoff(req, ioDispatcher).use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> resp.body?.bytes()
                else -> throw mapError(resp.code, resp.body?.string())
            }
        }
    }

    override suspend fun head(path: String): Result<RemoteFileMeta?> = runCatching {
        val req = Request.Builder()
            .url(url(path))
            .head()
            .header("Authorization", authHeader())
            .build()
        httpClient().executeWithBackoff(req, ioDispatcher).use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> RemoteFileMeta(
                    path = path,
                    size = resp.header("Content-Length")?.toLongOrNull() ?: 0,
                    lastModified = resp.header("Last-Modified")
                        ?.let { runCatching { ZonedDateTime.parse(it, httpDate).toInstant() }.getOrNull() }
                        ?: Instant_now(),
                    etag = resp.header("ETag"),
                )
                else -> throw mapError(resp.code, resp.body?.string())
            }
        }
    }

    override suspend fun list(path: String): Result<List<RemoteFileMeta>> = runCatching {
        val body = """<?xml version="1.0"?>
<d:propfind xmlns:d="DAV:">
  <d:prop><d:getlastmodified/><d:getcontentlength/><d:getetag/></d:prop>
</d:propfind>"""
        val req = Request.Builder()
            .url(url(path).trimEnd('/') + "/")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", authHeader())
            .header("Depth", "1")
            .build()
        httpClient().executeWithBackoff(req, ioDispatcher).use { resp ->
            if (!resp.isSuccessful && resp.code != 207) {
                if (resp.code == 404) return@use emptyList<RemoteFileMeta>()
                throw mapError(resp.code, resp.body?.string())
            }
            parseMultistatus(resp.body?.string().orEmpty(), url(path))
        }
    }

    override suspend fun delete(path: String): Result<Unit> = runCatching {
        val req = Request.Builder()
            .url(url(path))
            .delete()
            .header("Authorization", authHeader())
            .build()
        httpClient().executeWithBackoff(req, ioDispatcher).use { resp ->
            val code = resp.code
            if (!(code in 200..299 || code == 404)) {
                throw mapError(code, resp.body?.string())
            }
        }
        Unit
    }

    /** 极简 multistatus 解析：提取 <d:href>（兼容 <D:href> 等前缀写法） */
    private fun parseMultistatus(xml: String, baseUrl: String): List<RemoteFileMeta> {
        val result = mutableListOf<RemoteFileMeta>()
        val regex = Regex("<(?:[A-Za-z0-9]+:)?response>(.*?)</(?:[A-Za-z0-9]+:)?response>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("<(?:[A-Za-z0-9]+:)?href>(.*?)</(?:[A-Za-z0-9]+:)?href>")
        val sizeRegex = Regex("<(?:[A-Za-z0-9]+:)?getcontentlength>(\\d+)</(?:[A-Za-z0-9]+:)?getcontentlength>")
        val modifiedRegex = Regex("<(?:[A-Za-z0-9]+:)?getlastmodified>(.*?)</(?:[A-Za-z0-9]+:)?getlastmodified>")
        val etagRegex = Regex("<(?:[A-Za-z0-9]+:)?getetag>&?lt;!\\[CDATA\\[(.*?)\\]\\]>|(?:<[A-Za-z0-9]+:)?getetag>(.*?)</(?:[A-Za-z0-9]+:)?getetag>")

        regex.findAll(xml).forEach { m ->
            val chunk = m.groupValues[1]
            val href = hrefRegex.find(chunk)?.groupValues?.get(1) ?: return@forEach
            val decoded = java.net.URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
            if (decoded.endsWith("/")) return@forEach // 目录
            val size = sizeRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            val modified = modifiedRegex.find(chunk)?.groupValues?.get(1)
                ?.let { runCatching { ZonedDateTime.parse(it, httpDate).toInstant() }.getOrNull() }
                ?: Instant_now()
            val etag = etagRegex.find(chunk)?.groupValues?.get(2)
                ?: etagRegex.find(chunk)?.groupValues?.get(1)
            result += RemoteFileMeta(decoded, size, modified, etag)
        }
        return result
    }

    companion object {
        private val httpDate: DateTimeFormatter =
            DateTimeFormatter.RFC_1123_DATE_TIME

        fun Instant_now(): java.time.Instant = java.time.Instant.now()
    }
}
