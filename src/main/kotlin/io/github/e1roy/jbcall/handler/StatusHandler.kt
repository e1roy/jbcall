package io.github.e1roy.jbcall.handler

import io.github.e1roy.jbcall.config.ServerConfig
import io.github.e1roy.jbcall.model.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 处理服务器状态相关请求
 */
class StatusHandler : BaseHandler() {
    
    fun handleStatus(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        val statusData = mapOf(
            "status" to "running",
            "port" to ServerConfig.getInstance().port,
            "timestamp" to System.currentTimeMillis(),
            "uptime" to System.currentTimeMillis() // 简化版，实际应该记录启动时间
        )
        
        sendJsonResponse(response, ApiResponse.success(statusData))
    }
    
    fun handleInfo(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        val runtime = Runtime.getRuntime()
        val infoData = mapOf(
            "java" to mapOf(
                "version" to System.getProperty("java.version"),
                "vendor" to System.getProperty("java.vendor"),
                "runtime" to System.getProperty("java.runtime.name")
            ),
            "system" to mapOf(
                "os" to System.getProperty("os.name"),
                "arch" to System.getProperty("os.arch"),
                "version" to System.getProperty("os.version")
            ),
            "memory" to mapOf(
                "total" to runtime.totalMemory(),
                "free" to runtime.freeMemory(),
                "max" to runtime.maxMemory(),
                "used" to runtime.totalMemory() - runtime.freeMemory()
            ),
            "server" to mapOf(
                "port" to ServerConfig.getInstance().port,
                "host" to ServerConfig.getInstance().host
            )
        )
        
        sendJsonResponse(response, ApiResponse.success(infoData))
    }
    
    fun handleEcho(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        val echoData = mapOf(
            "method" to request.method,
            "uri" to request.requestURI,
            "query" to request.queryString,
            "headers" to request.headerNames.asSequence().associateWith { request.getHeader(it) },
            "parameters" to request.parameterMap.mapValues { it.value.toList() },
            "timestamp" to System.currentTimeMillis()
        )
        
        sendJsonResponse(response, ApiResponse.success(echoData))
    }
    
    fun handleErrorCheckPage(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        try {
            val htmlContent = this::class.java.classLoader.getResourceAsStream("web/error-check.html")?.use { 
                it.bufferedReader().readText() 
            } ?: """
                <!DOCTYPE html>
                <html>
                <head><title>Error Check Tool</title></head>
                <body>
                    <h1>JBCall Error Check Tool</h1>
                    <p>HTML file not found. Please ensure error-check.html is in the resources/web directory.</p>
                </body>
                </html>
            """.trimIndent()
            
            response.contentType = "text/html"
            response.characterEncoding = "UTF-8"
            response.writer.write(htmlContent)
        } catch (e: Exception) {
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.contentType = "text/html"
            response.characterEncoding = "UTF-8"
            response.writer.write("""
                <!DOCTYPE html>
                <html>
                <head><title>Error</title></head>
                <body>
                    <h1>Error</h1>
                    <p>Failed to load error check page: ${e.message}</p>
                </body>
                </html>
            """.trimIndent())
        }
    }
}