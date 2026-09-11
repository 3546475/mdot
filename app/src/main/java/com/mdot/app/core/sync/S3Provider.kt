package com.mdot.app.core.sync

import com.mdot.app.core.network.executeWithBackoff
import com.mdot.app.core.network.withTrustAllCerts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3 兼容对象存储源（05 文档 §5）：PutObject/GetObject/HeadObject/ListObjectsV2/DeleteObject，
 * SigV4 手写签名（path-style 寻址，兼容 OSS/COS/R2/MinIO）。404 = 云端无此文件。
 * trustSelfSigned 开启时用信任所有证书的派生 client（自建 MinIO 自签 HTTPS）。
 */
@Singleton
class S3Provider @Inject constructor(
    private val client: OkHttpClient,
    @com.mdot.app.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageProvider {

    override val kind: ProviderKind = ProviderKind.S3

    private lateinit var config: S3Config

    @Volatile
    private var trustAllClient: OkHttpClient? = null

    fun configure(config: S3Config): S3Provider {
        this.config = config
        trustAllClient = if (config.trustSelfSigned) client.withTrustAllCerts() else null
        return this
    }

    private fun httpClient(): OkHttpClient = trustAllClient ?: client

    private fun requireConfig(): S3Config =
        if (::config.isInitialized) config else throw IOException("S3 未配置")

    private val prefix: String
        get() = requireConfig().pathPrefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }

    private fun objectUrl(key: String): okhttp3.HttpUrl {
        val cfg = requireConfig()
        // path-style：https://<endpoint-host>/<bucket>/<key>
        val host = cfg.endpoint
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/')
        val scheme = if (cfg.endpoint.startsWith("http://")) "http" else "https"
        return "$scheme://$host/${cfg.bucket}/${key.trimStart('/')}".toHttpUrl()
    }

    /** 桶级 URL（ListObjectsV2 必须请求桶路径：GET /<bucket>?list-type=2——
     *  挂在 bucket/key 上会被服务端当作 GetObject(key) 而非列举） */
    private fun bucketUrl(): okhttp3.HttpUrl {
        val cfg = requireConfig()
        val host = cfg.endpoint
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/')
        val scheme = if (cfg.endpoint.startsWith("http://")) "http" else "https"
        return "$scheme://$host/${cfg.bucket}".toHttpUrl()
    }

    private fun amzDate(): Pair<String, String> {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        return dateStamp to amzDate
    }

    private suspend fun exec(request: Request): okhttp3.Response =
        withContext(ioDispatcher) {
            val cfg = requireConfig()
            val url = request.url
            val payload = if (request.body != null) {
                val buf = okio.Buffer()
                request.body!!.writeTo(buf)
                buf.readByteArray()
            } else ByteArray(0)
            val payloadHash = SigV4.sha256HexBytes(payload)
            val (dateStamp, amzDate) = amzDate()

            val headers = run {
                // SigV4 规范：host 头必须含非默认端口（http≠80 / https≠443），
                // 否则 MinIO/OSS 等自建端口场景 SignatureDoesNotMatch
                val hostHeader =
                    if ((url.scheme == "http" && url.port == 80) ||
                        (url.scheme == "https" && url.port == 443)
                    ) url.host else "${url.host}:${url.port}"
                mapOf(
                    "host" to hostHeader,
                    "x-amz-content-sha256" to payloadHash,
                    "x-amz-date" to amzDate,
                )
            }
            val query = url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }
            val auth = SigV4.authorizationHeader(
                method = request.method,
                canonicalPath = url.encodedPath,
                query = query,
                headers = headers,
                payloadHash = payloadHash,
                accessKeyId = cfg.accessKeyId,
                secretAccessKey = cfg.secretAccessKey,
                region = cfg.region.ifBlank { "us-east-1" },
                service = "s3",
                amzDate = amzDate,
            )
            val signed = request.newBuilder()
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", auth)
                .build()
            httpClient().executeWithBackoff(signed, ioDispatcher)
        }

    private fun mapError(code: Int, body: String?): IOException {
        val hint = when (code) {
            401, 403 -> "密钥不正确或无该桶权限"
            404 -> "桶不存在，请检查 Bucket 名"
            301 -> "Endpoint 与区域不匹配，请检查"
            429, 503 -> "对象存储限流，稍后再试"
            else -> "HTTP $code"
        }
        return IOException(hint + if (body.isNullOrBlank()) "" else "（$code）")
    }

    /** 连接检测 = 对桶发 ListObjectsV2（max-keys=1）：凭据/桶/区域任一无效即失败 */
    override suspend fun ensureBaseDir(): Result<Unit> = runCatching {
        val probeUrl = bucketUrl().newBuilder()
            .addQueryParameter("list-type", "2")
            .addQueryParameter("max-keys", "1")
            .addQueryParameter("prefix", prefix)
            .build()
        val req = Request.Builder().url(probeUrl).get().build()
        exec(req).use { resp ->
            if (!resp.isSuccessful) throw mapError(resp.code, resp.body?.string())
        }
    }

    override suspend fun put(path: String, bytes: ByteArray, ifMatch: String?): Result<PutResult> =
        runCatching {
            val key = prefix + path.trimStart('/')
            val req = Request.Builder()
                .url(objectUrl(key))
                .put(bytes.toRequestBody("application/zip".toMediaType()))
                .build()
            exec(req).use { resp ->
                if (!resp.isSuccessful) throw mapError(resp.code, resp.body?.string())
                PutResult(etag = resp.header("ETag"))
            }
        }

    override suspend fun get(path: String): Result<ByteArray?> = runCatching {
        val key = prefix + path.trimStart('/')
        val req = Request.Builder().url(objectUrl(key)).get().build()
        exec(req).use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> resp.body?.bytes()
                else -> throw mapError(resp.code, resp.body?.string())
            }
        }
    }

    override suspend fun head(path: String): Result<RemoteFileMeta?> = runCatching {
        val key = prefix + path.trimStart('/')
        val req = Request.Builder().url(objectUrl(key)).head().build()
        exec(req).use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> RemoteFileMeta(
                    path = key,
                    size = resp.header("Content-Length")?.toLongOrNull() ?: 0,
                    lastModified = resp.header("Last-Modified")
                        ?.let { runCatching { ZonedDateTime.parse(it, httpDate).toInstant() }.getOrNull() }
                        ?: Instant.now(),
                    etag = resp.header("ETag"),
                )
                else -> throw mapError(resp.code, resp.body?.string())
            }
        }
    }

    override suspend fun list(path: String): Result<List<RemoteFileMeta>> = runCatching {
        val dirPrefix = (prefix + path.trimStart('/')).trimEnd('/')
        val result = mutableListOf<RemoteFileMeta>()
        var token: String? = null
        var pages = 0
        do {
            val builder = bucketUrl().newBuilder()
                .addQueryParameter("list-type", "2")
                .addQueryParameter("max-keys", "1000")
                .addQueryParameter("prefix", "$dirPrefix/")
            token?.let { builder.addQueryParameter("continuation-token", it) }
            val req = Request.Builder().url(builder.build()).get().build()
            var truncated = false
            exec(req).use { resp ->
                if (!resp.isSuccessful) throw mapError(resp.code, resp.body?.string())
                val (page, more, next) = parseListBucket(resp.body?.string().orEmpty())
                result += page
                truncated = more
                token = next
            }
            pages++
        } while (truncated && pages < 50) // 50 页×1000 对象的保险上限，防异常服务端死循环
        result
    }

    override suspend fun delete(path: String): Result<Unit> = runCatching {
        val key = prefix + path.trimStart('/')
        val req = Request.Builder().url(objectUrl(key)).delete().build()
        exec(req).use { resp ->
            val code = resp.code
            if (!(code in 200..299 || code == 404)) {
                throw mapError(code, resp.body?.string())
            }
        }
        Unit
    }

    /** ListBucketResult 解析：返回 (对象列表, IsTruncated, NextContinuationToken)。
     *  key 中的 XML 实体（&amp; 等）不反转义——备份 key 为 ASCII 日期/uuid 路径，不含特殊字符 */
    private fun parseListBucket(xml: String): Triple<List<RemoteFileMeta>, Boolean, String?> {
        val result = mutableListOf<RemoteFileMeta>()
        val contentRegex = Regex("<Contents>(.*?)</Contents>", RegexOption.DOT_MATCHES_ALL)
        val keyRegex = Regex("<Key>(.*?)</Key>")
        val sizeRegex = Regex("<Size>(\\d+)</Size>")
        val modifiedRegex = Regex("<LastModified>(.*?)</LastModified>")
        contentRegex.findAll(xml).forEach { m ->
            val chunk = m.groupValues[1]
            val key = keyRegex.find(chunk)?.groupValues?.get(1) ?: return@forEach
            result += RemoteFileMeta(
                path = key,
                size = sizeRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: 0,
                lastModified = modifiedRegex.find(chunk)?.groupValues?.get(1)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.now(),
            )
        }
        val truncated = Regex("<IsTruncated>\\s*true\\s*</IsTruncated>").containsMatchIn(xml)
        val nextToken = Regex("<NextContinuationToken>(.*?)</NextContinuationToken>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)
        return Triple(result, truncated, nextToken)
    }

    companion object {
        private val httpDate: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
    }
}
