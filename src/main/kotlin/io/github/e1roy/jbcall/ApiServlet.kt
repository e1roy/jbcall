package io.github.e1roy.jbcall

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

class ApiServlet : HttpServlet() {
    private val logger = Logger.getInstance(ApiServlet::class.java)
    private val objectMapper = ObjectMapper()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp, "GET")
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp, "POST")
    }

    override fun doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp, "PUT")
    }

    override fun doDelete(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest(req, resp, "DELETE")
    }

    private fun handleRequest(req: HttpServletRequest, resp: HttpServletResponse, method: String) {
        try {
            // 设置响应头
            resp.contentType = "application/json"
            resp.characterEncoding = "UTF-8"
            resp.setHeader("Access-Control-Allow-Origin", "*")
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")

            val pathInfo = req.pathInfo ?: "/"
            logger.info("收到请求: $method $pathInfo")

            when {
                pathInfo.startsWith("/status") -> handleStatus(req, resp)
                pathInfo.startsWith("/info") -> handleInfo(req, resp)
                pathInfo.startsWith("/echo") -> handleEcho(req, resp)
                pathInfo.startsWith("/project") -> handleProject(req, resp)
                else -> handleNotFound(resp)
            }

        } catch (e: Exception) {
            logger.error("处理请求时发生错误", e)
            handleError(resp, e)
        }
    }

    private fun handleStatus(req: HttpServletRequest, resp: HttpServletResponse) {
        val response = mapOf<String, Any>(
            "status" to "ok",
            "message" to "JBCall HTTP服务器运行正常",
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0.0"
        )
        writeJsonResponse(resp, response)
    }

    private fun handleInfo(req: HttpServletRequest, resp: HttpServletResponse) {
        val serverComponent = HttpServerComponent.getInstance()
        val response = mapOf<String, Any>(
            "server" to mapOf<String, Any>(
                "port" to serverComponent.getPort(),
                "url" to serverComponent.getServerUrl(),
                "running" to serverComponent.isRunning()
            ),
            "system" to mapOf<String, Any>(
                "java_version" to System.getProperty("java.version"),
                "os_name" to System.getProperty("os.name"),
                "user_dir" to System.getProperty("user.dir")
            )
        )
        writeJsonResponse(resp, response)
    }

    private fun handleEcho(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestBody = req.reader.readText()
        val response = mapOf<String, Any?>(
            "method" to req.method,
            "path" to req.pathInfo,
            "query" to req.queryString,
            "headers" to req.headerNames.asSequence().associateWith { req.getHeader(it) },
            "body" to requestBody,
            "timestamp" to System.currentTimeMillis()
        )
        writeJsonResponse(resp, response)
    }

    private fun handleProject(req: HttpServletRequest, resp: HttpServletResponse) {
        // 这里可以添加项目相关的API，比如获取项目信息、文件列表等
        val response = mapOf<String, Any>(
            "message" to "项目API接口",
            "available_endpoints" to listOf(
                "/api/status - 服务器状态",
                "/api/info - 服务器信息",
                "/api/echo - 回显请求",
                "/api/project - 项目信息"
            )
        )
        writeJsonResponse(resp, response)
    }

    private fun handleNotFound(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        val response = mapOf<String, Any>(
            "error" to "Not Found",
            "message" to "请求的API端点不存在",
            "available_endpoints" to listOf(
                "/api/status",
                "/api/info",
                "/api/echo",
                "/api/project"
            )
        )
        writeJsonResponse(resp, response)
    }

    private fun handleError(resp: HttpServletResponse, e: Exception) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        val response = mapOf<String, Any>(
            "error" to "Internal Server Error",
            "message" to (e.message ?: "未知错误"),
            "timestamp" to System.currentTimeMillis()
        )
        writeJsonResponse(resp, response)
    }

    private fun writeJsonResponse(resp: HttpServletResponse, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(data)
            resp.writer.write(json)
            resp.writer.flush()
        } catch (e: IOException) {
            logger.error("写入响应失败", e)
        }
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // 处理CORS预检请求
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }
}