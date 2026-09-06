package com.mdot.app.core.network

import com.mdot.app.core.network.executeWithBackoff
import com.mdot.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 轻量 JSON/文本 GET（更新检查、节假日库）。超时与错误映射见 05 文档 §1。 */
@Singleton
class JsonFetcher @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 返回响应体文本；非 2xx 抛 IOException */
    suspend fun fetchText(url: String): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.executeWithBackoff(request, ioDispatcher).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("空响应体")
        }
    }
}
