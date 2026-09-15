package com.mdot.app.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 明文 http 守门（13 文档 B2-04）：
 * network_security_config 因用户自配 NAS 地址只能全局放行明文（base-config 无法按动态域名收敛），
 * 这里在代码层收窄——仅「用户显式配置的存储源 / 数据源」的 host 允许 http；
 * 其余明文请求（未来新代码误用、被篡改的 update.json 指向的 http APK 等）一律在拦截器处拒绝。
 * 登记方：SyncEngine（存储源，含临时测试）、UpdateRepository（更新源及同源 APK）、HolidayRepository（节假日源）。
 */
@Singleton
class CleartextGate @Inject constructor() {

    private val allowedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 登记允许明文的 host（幂等，大小写不敏感） */
    fun allowHost(host: String) {
        if (host.isNotBlank()) allowedHosts += host.lowercase()
    }

    /** 登记允许明文的 URL 的 host（解析失败静默忽略——格式非法的 URL 自会死于后续请求） */
    fun allowUrl(url: String) {
        url.toHttpUrlOrNull()?.host?.let { allowHost(it) }
    }

    fun isAllowed(host: String): Boolean = host.lowercase() in allowedHosts
}
