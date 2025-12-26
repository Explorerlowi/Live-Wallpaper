package com.example.livewallpaper.core.error

/**
 * 统一错误类型定义
 */
sealed class AppError : Throwable() {
    
    /** 网络连接错误 */
    data object Network : AppError() {
        override val message: String = "网络连接失败"
    }
    
    /** 服务器错误 */
    data class Server(val code: Int, override val message: String?) : AppError()
    
    /** 未授权 / Token 无效 */
    data object Unauthorized : AppError() {
        override val message: String = "授权失败，请检查 API 配置"
    }
    
    /** 未知错误 */
    data class Unknown(override val cause: Throwable? = null) : AppError() {
        override val message: String = cause?.message ?: "未知错误"
    }
}

/**
 * 统一结果封装
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: AppError) : AppResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Error)?.error
    
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (AppError) -> Unit): AppResult<T> {
        if (this is Error) action(error)
        return this
    }
}
