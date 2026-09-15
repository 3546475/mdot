package com.mdot.app.core.util

/** 轻量 Result（07 文档 §6：不用 kotlin.Result 的泛异常） */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** 时长非法（≤0 或 >1440） */
    data object InvalidDuration : AppError

    /** 转调休超过本条加班时长 */
    data object InvalidComp : AppError

    /** 禁止选择未来日期 */
    data object FutureDate : AppError

    /** 班次重名 */
    data object DuplicateName : AppError

    /** 名称非法（空/超长） */
    data object InvalidName : AppError

    /** 带用户可读文案的校验失败（工地记工 12 文档：项目上限/已结算锁定等） */
    data class InvalidMessage(val message: String) : AppError

    /** 存储/网络等可重试错误 */
    data class Storage(val reason: String) : AppError

    data object Unexpected : AppError
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/**
 * runCatching 的挂起安全版（13 文档 B3-06）：CancellationException 穿透——协程取消不得被吞成 Failure。
 * 旧模式（runCatching / catch(Exception) 包挂起调用）会在取消时跳过锚点写入等后续步骤，
 * 曾致 WebDAV If-Match 412 死锁。lambda 内含挂起调用的一律用本函数。
 */
suspend fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (c: kotlin.coroutines.cancellation.CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t)
}

/** 在 catch(Exception) 块内调用：若是协程取消则重新抛出（语义同 [runCatchingSuspend]，用于不便改结构的 try/catch） */
fun Exception.rethrowIfCancellation() {
    if (this is kotlin.coroutines.cancellation.CancellationException) throw this
}
