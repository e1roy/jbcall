package io.github.e1roy.jbcall

import com.intellij.openapi.diagnostic.Logger
import io.github.e1roy.jbcall.handler.StatusHandler
import io.github.e1roy.jbcall.handler.ProjectHandler
import io.github.e1roy.jbcall.handler.ClassAnalysisHandler
import io.github.e1roy.jbcall.handler.ErrorCheckHandler
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 重构后的API Servlet，使用处理器模式
 */
class ApiServlet : HttpServlet() {
    private val logger = Logger.getInstance(ApiServlet::class.java)
    
    // 处理器实例
    private val statusHandler = StatusHandler()
    private val projectHandler = ProjectHandler()
    private val classAnalysisHandler = ClassAnalysisHandler()
    private val errorCheckHandler = ErrorCheckHandler()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp)
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp)
    }

    override fun doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp)
    }

    override fun doDelete(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp)
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // 处理CORS预检请求
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleRequest(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val pathInfo = req.pathInfo ?: "/"
            logger.info("收到请求: ${req.method} $pathInfo")
            
            when {
                pathInfo.startsWith("/status") -> statusHandler.handleStatus(req, resp)
                pathInfo.startsWith("/info") -> statusHandler.handleInfo(req, resp)
                pathInfo.startsWith("/echo") -> statusHandler.handleEcho(req, resp)
                pathInfo.startsWith("/error-check") -> statusHandler.handleErrorCheckPage(req, resp)
                pathInfo.startsWith("/project/classes") -> projectHandler.handleProjectClasses(req, resp)
                pathInfo.startsWith("/project") -> projectHandler.handleProjectInfo(req, resp)
                pathInfo.startsWith("/method") -> classAnalysisHandler.handleMethodBody(req, resp)
                pathInfo.startsWith("/class/errors") -> errorCheckHandler.handleErrorCheck(req, resp)
                pathInfo.startsWith("/class") -> classAnalysisHandler.handleClassAnalysis(req, resp)
                else -> handleNotFound(resp)
            }
        } catch (e: Exception) {
            logger.error("处理请求时发生错误", e)
            handleError(resp, e)
        }
    }

    private fun handleNotFound(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        
        val response = mapOf(
            "error" to "Not Found",
            "message" to "请求的API端点不存在",
            "available_endpoints" to listOf(
                "/api/status - 服务器状态",
                "/api/info - 系统信息",
                "/api/echo - 请求回显",
                "/api/error-check - 错误检查工具页面",
                "/api/project - 项目信息",
                "/api/project/classes - 项目类列表",
                "/api/class?class=<className>&format=json - 类分析",
                "/api/class/errors?class=<className> - 类错误检查",
                "/api/method?class=<className>&method=<methodName> - 方法体获取"
            )
        )
        
        resp.writer.write(com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response))
    }

    private fun handleError(resp: HttpServletResponse, e: Exception) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        
        val response = mapOf(
            "error" to "Internal Server Error",
            "message" to (e.message ?: "未知错误"),
            "timestamp" to System.currentTimeMillis()
        )
        
        resp.writer.write(com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response))
    }
}
