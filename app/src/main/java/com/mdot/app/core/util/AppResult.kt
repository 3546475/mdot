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
