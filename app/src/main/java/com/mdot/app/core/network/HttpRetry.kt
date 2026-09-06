package com.mdot.app.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 统一的网络重试策略（05 文档 §1）：
 * - 网络错误 / 5xx：指数退避重试 3 次（1s / 4s / 16s）
 * - 429 限流：读取 Retry-After（至少 60s），最多重试 1 次
 * 其余状态码原样返回，由调用方按错误映射表转换文案。
 */
suspend fun OkHttpClient.executeWithBackoff(
    request: Request,
    ioDispatcher: CoroutineDispatcher,
    maxIoRetries: Int = 3,
): Response {
    val delays = listOf(1000L, 4000L, 16000L)
    var ioAttempt = 0
    var rateLimitAttempt = 0
    while (true) {
        val response = try {
            withContext(ioDispatcher) { newCall(request).execute() }
        } catch (e: IOException) {
            if (ioAttempt < maxIoRetries) {
                delay(delays[ioAttempt])
                ioAttempt++
                continue
            }
            throw e
        }
        when {
            // 429：读 Retry-After，至少 60s，只重试一次
            response.code == 429 && rateLimitAttempt < 1 -> {
                val waitSec = response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(60) ?: 60
                response.close()
                delay(waitSec * 1000)
                rateLimitAttempt++
            }

            // 5xx：指数退避
            response.code in 500..599 && ioAttempt < maxIoRetries -> {
                response.close()
                delay(delays[ioAttempt])
                ioAttempt++
            }

            else -> return response
        }
    }
}
