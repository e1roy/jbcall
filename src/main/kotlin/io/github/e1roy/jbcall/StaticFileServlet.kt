package io.github.e1roy.jbcall

import com.intellij.openapi.diagnostic.Logger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class StaticFileServlet : HttpServlet() {
    private val logger = Logger.getInstance(StaticFileServlet::class.java)
    
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val pathInfo = req.pathInfo ?: "/"
        
        // 如果是API请求，跳过静态文件处理
        if (pathInfo.startsWith("/api/")) {
            resp.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        
        try {
            // 默认首页
            val requestedPath = if (pathInfo == "/") "/index.html" else pathInfo
            
            // 简单的静态内容响应
            when (requestedPath) {
                "/index.html" -> serveIndexPage(resp)
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
        
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JBCall HTTP服务器</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .container {
                        background: white;
                        padding: 30px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    h1 {
                        color: #333;
                        text-align: center;
                    }
                    .api-list {
                        background: #f8f9fa;
                        padding: 20px;
                        border-radius: 5px;
                        margin: 20px 0;
                    }
                    .api-item {
                        margin: 10px 0;
                        padding: 10px;
                        background: white;
                        border-radius: 3px;
                        border-left: 4px solid #007acc;
                    }
                    .method {
                        font-weight: bold;
                        color: #007acc;
                    }
                    button {
                        background: #007acc;
                        color: white;
                        border: none;
                        padding: 10px 20px;
                        border-radius: 5px;
                        cursor: pointer;
                        margin: 5px;
                    }
                    button:hover {
                        background: #005a9e;
                    }
                    #result {
                        background: #f8f9fa;
                        padding: 15px;
                        border-radius: 5px;
                        margin-top: 20px;
                        white-space: pre-wrap;
                        font-family: monospace;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🚀 JBCall HTTP服务器</h1>
                    <p>欢迎使用JBCall IntelliJ IDEA插件的HTTP服务器！</p>
                    
                    <div class="api-list">
                        <h3>可用的API接口：</h3>
                        <div class="api-item">
                            <span class="method">GET</span> /api/status - 服务器状态检查
                            <button onclick="testApi('/api/status')">测试</button>
                        </div>
                        <div class="api-item">
                            <span class="method">GET</span> /api/info - 服务器详细信息
                            <button onclick="testApi('/api/info')">测试</button>
                        </div>
                        <div class="api-item">
                            <span class="method">POST</span> /api/echo - 回显请求内容
                            <button onclick="testEcho()">测试</button>
                        </div>
                        <div class="api-item">
                            <span class="method">GET</span> /api/project - 项目信息
                            <button onclick="testApi('/api/project')">测试</button>
                        </div>
                    </div>
                    
                    <div id="result"></div>
                </div>
                
                <script>
                    async function testApi(endpoint) {
                        try {
                            const response = await fetch(endpoint);
                            const data = await response.json();
                            document.getElementById('result').textContent = 
                                'Response from ' + endpoint + ':\n' + JSON.stringify(data, null, 2);
                        } catch (error) {
                            document.getElementById('result').textContent = 
                                'Error: ' + error.message;
                        }
                    }
                    
                    async function testEcho() {
                        try {
                            const testData = {
                                message: 'Hello from JBCall!',
                                timestamp: new Date().toISOString()
                            };
                            
                            const response = await fetch('/api/echo', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify(testData)
                            });
                            
                            const data = await response.json();
                            document.getElementById('result').textContent = 
                                'Response from /api/echo:\n' + JSON.stringify(data, null, 2);
                        } catch (error) {
                            document.getElementById('result').textContent = 
                                'Error: ' + error.message;
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        resp.writer.write(html)
        resp.writer.flush()
    }
    
    private fun serveFavicon(resp: HttpServletResponse) {
        // 返回一个简单的404，避免favicon请求错误
        resp.status = HttpServletResponse.SC_NOT_FOUND
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
                    body { font-family: Arial, sans-serif; text-align: center; margin-top: 100px; }
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
        resp.writer.flush()
    }
}