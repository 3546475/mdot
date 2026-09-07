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

            val headers = mapOf(
                "host" to url.host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate,
            )
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

    /** 连接检测 = 对存储桶发 ListObjectsV2（max-keys=1）：凭据/桶/区域任一无效即失败 */
    override suspend fun ensureBaseDir(): Result<Unit> = runCatching {
        val probeUrl = objectUrl("probe").newBuilder()
            .query("list-type=2&max-keys=1&prefix=${SigV4.awsUriEncode(prefix)}")
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
        val key = "list" // 仅用于 URL 组装；实际以查询参数请求
        val listUrl = objectUrl(key).newBuilder()
            .query("list-type=2&max-keys=100&prefix=${SigV4.awsUriEncode("$dirPrefix/")}")
            .build()
        val req = Request.Builder().url(listUrl).get().build()
        exec(req).use { resp ->
            if (!resp.isSuccessful) throw mapError(resp.code, resp.body?.string())
            parseListBucket(resp.body?.string().orEmpty())
        }
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

    /** 极简 ListBucketResult 解析（IsTruncated 场景本地仅 5 份历史，单页必够） */
    private fun parseListBucket(xml: String): List<RemoteFileMeta> {
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
        return result
    }

    companion object {
        private val httpDate: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
    }
}
