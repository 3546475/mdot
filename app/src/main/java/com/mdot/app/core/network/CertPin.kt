package com.mdot.app.core.network

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 自签名证书信任（13 文档 B2-02 重写：指纹校验，替代旧 TrustAll 的"信任一切"）：
 * 用户开启「信任自签名证书」后，首个 TLS 服务器证书按 TOFU（Trust On First Use）记录
 * SHA-256 指纹；此后连接校验指纹一致才放行——MITM 换证书即失败，不再裸奔。
 * 指纹经回调持久化到该存储源凭据（certSha256），服务器正式换证时需用户删除源重配。
 */
object CertPin {

    /** 服务器叶子证书 SHA-256 指纹（64 个 hex 小写） */
    fun sha256Fingerprint(cert: Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * 构造校验指纹的 trustManager：
     * [knownFingerprint] = null → TOFU 首连：信任任何证书但把指纹回调出去（[onFingerprint]）；
     * 非空 → 严格校验叶子证书指纹一致，不一致抛 CertificateException（连接失败）。
     */
    fun trustManager(knownFingerprint: String?, onFingerprint: (String) -> Unit): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("服务器未提供证书")
                val fp = sha256Fingerprint(leaf)
                if (knownFingerprint == null) {
                    onFingerprint(fp) // TOFU：记录指纹，本次放行
                } else if (fp != knownFingerprint.lowercase()) {
                    throw CertificateException(
                        "服务器证书指纹与记录不一致（可能被劫持或服务器已换证）。" +
                            "如确认服务器已更换证书，请删除该存储源后重新添加",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

    /** hostnameVerifier：自签场景证书 CN 往往不含 IP/内网域名，放宽主机名校验（身份由指纹承担） */
    private val hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

    fun clientWith(trustManager: X509TrustManager): OkHttpClient {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .build()
    }
}
