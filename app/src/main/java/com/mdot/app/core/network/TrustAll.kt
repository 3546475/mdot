package com.mdot.app.core.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 自签名证书信任（06 文档 §6 同步打磨）：
 * 仅在用户显式开启"信任自签名证书"时使用——面向自建 NAS/MinIO 等自签 HTTPS 场景。
 * 关闭时走系统默认校验（默认路径不变）。
 */
fun OkHttpClient.withTrustAllCerts(): OkHttpClient {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), SecureRandom())
    return newBuilder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}
