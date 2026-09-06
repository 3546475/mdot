package com.mdot.app.core.sync

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 手写实现（05 文档 §5.0，纯 Kotlin 零依赖）。
 * 用 AWS 官方 SigV4 测试套件向量（get-vanilla）做单元测试。
 */
object SigV4 {

    private val HEX_LOWER = "0123456789abcdef".toCharArray()
    private val HEX_UPPER = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[i * 2] = HEX_LOWER[v ushr 4]
            out[i * 2 + 1] = HEX_LOWER[v and 0x0F]
        }
        return String(out)
    }

    fun sha256Hex(data: String): String =
        bytesToHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8)))

    fun sha256HexBytes(data: ByteArray): String =
        bytesToHex(java.security.MessageDigest.getInstance("SHA-256").digest(data))

    fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    /** kSigning = HMAC(HMAC(HMAC(HMAC("AWS4"+SK, date), region), service), "aws4_request") */
    fun deriveSigningKey(secretKey: String, date: String, region: String, service: String): ByteArray {
        val kDate = hmacSha256(("AWS4$secretKey").toByteArray(Charsets.UTF_8), date)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    /** RFC3986 百分号编码（AWS 规则：仅 A-Za-z0-9 - _ . ~ 不转义，十六进制大写） */
    fun awsUriEncode(text: String, encodeSlash: Boolean = true): String {
        val sb = StringBuilder()
        for (b in text.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            when {
                c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code ||
                    c in '0'.code..'9'.code || c == '-'.code || c == '.'.code ||
                    c == '_'.code || c == '~'.code -> sb.append(c.toChar())
                c == '/'.code && !encodeSlash -> sb.append('/')
                else -> sb.append('%').append(HEX_UPPER[c ushr 4]).append(HEX_UPPER[c and 0x0F])
            }
        }
        return sb.toString()
    }

    /** 规范请求（独立可测）：CanonicalHeaders 自身以换行结尾，故 signedHeaders 前有一个空行 */
    internal fun canonicalRequest(
        method: String,
        canonicalPath: String,
        canonicalQuery: String,
        headers: Map<String, String>,
        signedHeaders: String,
        payloadHash: String,
    ): String = buildString {
        append(method).append('\n')
        append(canonicalPath).append('\n')
        append(canonicalQuery).append('\n')
        headers.entries
            .sortedBy { it.key }
            .forEach { (k, v) ->
                append(k.lowercase()).append(':').append(v.trim().replace(Regex(" +"), " ")).append('\n')
            }
        append('\n')
        append(signedHeaders).append('\n')
        append(payloadHash)
    }

    /** 字符串到签名（独立可测） */
    internal fun stringToSign(amzDate: String, scope: String, canonicalRequest: String): String =
        buildString {
            append("AWS4-HMAC-SHA256").append('\n')
            append(amzDate).append('\n')
            append(scope).append('\n')
            append(sha256Hex(canonicalRequest))
        }

    /**
     * 计算 Authorization 头。
     * @param canonicalPath 已按服务规则编码好的路径（S3 不二次编码，逐段编码）
     * @param query 查询参数（内部按 key 排序并编码）
     * @param headers 参与签名的头（key 需为小写；host / x-amz-date / x-amz-content-sha256 必签）
     */
    fun authorizationHeader(
        method: String,
        canonicalPath: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        payloadHash: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        service: String,
        amzDate: String,
    ): String {
        val canonicalQuery = query.entries
            .sortedBy { awsUriEncode(it.key) }
            .joinToString("&") { (k, v) ->
                "${awsUriEncode(k)}=${awsUriEncode(v)}"
            }
        val signedHeaders = headers.keys.sorted().joinToString(";")

        val creq = canonicalRequest(method, canonicalPath, canonicalQuery, headers, signedHeaders, payloadHash)
        val dateStamp = amzDate.substringBefore('T')
        val scope = "$dateStamp/$region/$service/aws4_request"
        val sts = stringToSign(amzDate, scope, creq)

        val kSigning = deriveSigningKey(secretAccessKey, dateStamp, region, service)
        val signature = bytesToHex(hmacSha256(kSigning, sts))

        return "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
    }
}
