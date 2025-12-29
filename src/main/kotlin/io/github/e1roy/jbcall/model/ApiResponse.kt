package io.github.e1roy.jbcall.model

/**
 * 统一的API响应格式
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(true, data = data)
        fun <T> error(message: String, code: String = "ERROR"): ApiResponse<T> = 
            ApiResponse(false, error = ApiError(code, message))
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)