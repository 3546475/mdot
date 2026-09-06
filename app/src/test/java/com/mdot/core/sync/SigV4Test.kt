package com.mdot.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SigV4 单测：AWS 官方 SigV4 测试套件 get-vanilla 向量
 * （AKIDEXAMPLE / 20150830T123600Z / us-east-1 / service "service"）。
 * kSigning 值经独立工具链复核。
 */
class SigV4Test {

    private val accessKey = "AKIDEXAMPLE"
    private val secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
    private val amzDate = "20150830T123600Z"
    private val region = "us-east-1"
    private val service = "service"
    private val emptyPayloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun `sha256 空串为标准摘要`() {
        assertEquals(emptyPayloadHash, SigV4.sha256Hex(""))
    }

    @Test
    fun `HMAC-SHA256 符合 RFC4231 用例2`() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            SigV4.bytesToHex(SigV4.hmacSha256("Jefe".toByteArray(), "what do ya want for nothing?")),
        )
    }

    @Test
    fun `派生签名密钥与独立计算一致`() {
        val kSigning = SigV4.deriveSigningKey(secretKey, "20150830", region, service)
        assertEquals(
            "938127b5336810ddb6a5d6af445fcac9e371f9ed418ed386b022aed82901be75",
            SigV4.bytesToHex(kSigning),
        )
    }

    @Test
    fun `get-vanilla 签名与 AWS 官方向量一致`() {
        val auth = SigV4.authorizationHeader(
            method = "GET",
            canonicalPath = "/",
            query = emptyMap(),
            headers = mapOf(
                "host" to "example.amazonaws.com",
                "x-amz-date" to amzDate,
            ),
            payloadHash = emptyPayloadHash,
            accessKeyId = accessKey,
            secretAccessKey = secretKey,
            region = region,
            service = service,
            amzDate = amzDate,
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, " +
                "SignedHeaders=host;x-amz-date, " +
                "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31",
            auth,
        )
    }

    @Test
    fun `规范请求格式`() {
        val creq = SigV4.canonicalRequest(
            method = "GET",
            canonicalPath = "/",
            canonicalQuery = "",
            headers = mapOf(
                "host" to "example.amazonaws.com",
                "x-amz-date" to amzDate,
            ),
            signedHeaders = "host;x-amz-date",
            payloadHash = emptyPayloadHash,
        )
        assertEquals(
            "GET\n/\n\nhost:example.amazonaws.com\nx-amz-date:20150830T123600Z\n\nhost;x-amz-date\n" +
                emptyPayloadHash,
            creq,
        )
    }

    @Test
    fun `URI 编码遵循 AWS 规则（大写十六进制）`() {
        assertEquals("a-b_c.d~e", SigV4.awsUriEncode("a-b_c.d~e"))
        assertEquals("a%2Fb", SigV4.awsUriEncode("a/b"))
        assertEquals("a/b", SigV4.awsUriEncode("a/b", encodeSlash = false))
        assertEquals("%E4%B8%AD", SigV4.awsUriEncode("中"))
        assertEquals("a%2Bb%20c", SigV4.awsUriEncode("a+b c"))
    }
}
