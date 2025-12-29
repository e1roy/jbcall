package io.github.e1roy.jbcall.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.e1roy.jbcall.model.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 处理器基类，提供通用功能
 */
abstract class BaseHandler {
    protected val objectMapper = ObjectMapper()
    
    protected fun sendJsonResponse(response: HttpServletResponse, data: Any) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(data))
    }
    
    protected fun sendError(response: HttpServletResponse, message: String, status: Int = 500) {
        response.status = status
        sendJsonResponse(response, ApiResponse.error<Any>(message))
    }
    
    protected fun getParameter(request: HttpServletRequest, name: String): String? {
        return request.getParameter(name)?.takeIf { it.isNotBlank() }
    }
    
    protected fun enableCors(response: HttpServletResponse) {
        response.setHeader("Access-Control-Allow-Origin", "*")
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }
}