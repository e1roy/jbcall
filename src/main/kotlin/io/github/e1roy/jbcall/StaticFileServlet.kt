package io.github.e1roy.jbcall

import com.intellij.openapi.diagnostic.Logger
import io.github.e1roy.jbcall.web.HtmlGenerator
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 静态文件服务器 - 提供Web管理界面
 */
class StaticFileServlet : HttpServlet() {
    private val logger = Logger.getInstance(StaticFileServlet::class.java)
    
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val pathInfo = req.pathInfo ?: "/"
        
        // API请求应该由ApiServlet处理
        if (pathInfo.startsWith("/api/")) {
            resp.status = HttpServletResponse.SC_NOT_FOUND
            resp.contentType = "application/json"
            resp.writer.write("""{"error": "API endpoint not found"}""")
            return
        }
        
        try {
            when (pathInfo) {
                "/", "/index.html" -> serveIndexPage(resp)
                "/favicon.ico" -> serveFavicon(resp)
                else -> serveNotFound(resp)
            }
        } catch (e: Exception) {
            logger.error("处理静态文件请求失败", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        }
    }
    
    private fun serveIndexPage(resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        resp.writer.write(HtmlGenerator.generateIndexPage())
    }
    
    private fun serveFavicon(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_NO_CONTENT
    }
    
    private fun serveNotFound(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>404 - 页面未找到</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                    h1 { color: #e74c3c; }
                </style>
            </head>
            <body>
                <h1>404 - 页面未找到</h1>
                <p>请求的页面不存在</p>
                <a href="/">返回首页</a>
            </body>
            </html>
        """.trimIndent()
        
        resp.writer.write(html)
    }
}