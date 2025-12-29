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
        
        // 如果是API请求，返回404让客户端知道这里不处理API
        if (pathInfo.startsWith("/api/")) {
            resp.status = HttpServletResponse.SC_NOT_FOUND
            resp.contentType = "application/json"
            resp.writer.write("""{"error": "API endpoint not found", "message": "API requests should go to /api/* path"}""")
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
                        max-height: 500px;
                        overflow-y: auto;
                    }
                    input[type="text"] {
                        width: 300px;
                        padding: 8px;
                        border: 1px solid #ddd;
                        border-radius: 3px;
                        font-size: 14px;
                    }
                    .example-buttons {
                        margin-top: 10px;
                    }
                    .example-btn {
                        background: #28a745;
                        font-size: 12px;
                        padding: 5px 10px;
                        margin: 2px;
                    }
                    .example-btn:hover {
                        background: #218838;
                    }
                    .project-classes {
                        max-height: 200px;
                        overflow-y: auto;
                        border: 1px solid #ddd;
                        border-radius: 3px;
                        margin-top: 10px;
                        padding: 10px;
                        background: white;
                        display: none;
                    }
                    .class-item {
                        padding: 5px;
                        cursor: pointer;
                        border-radius: 3px;
                        margin: 2px 0;
                    }
                    .class-item:hover {
                        background: #f0f0f0;
                    }
                    .class-type {
                        font-size: 10px;
                        color: #666;
                        margin-left: 5px;
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
                        <div class="api-item">
                            <span class="method">GET</span> /api/project/classes - 获取项目中的所有类列表
                            <button onclick="testApi('/api/project/classes')">获取项目类列表</button>
                        </div>
                        <div class="api-item">
                            <span class="method">GET</span> /api/class?name=&lt;className&gt;&amp;project=true - 获取项目中的类信息（简化文本格式）
                            <div style="margin-top: 10px;">
                                <input type="text" id="classNameInput" placeholder="输入项目中的类名，如: com.example.MyClass" 
                                       style="width: 350px; padding: 8px; border: 1px solid #ddd; border-radius: 3px;">
                                <button onclick="testProjectClassInfo()">查询项目类</button>
                                <button onclick="testCompiledClassInfo()">查询编译类</button>
                            </div>
                            <div class="example-buttons">
                                <button class="example-btn" onclick="setClassName('java.lang.String')">String</button>
                                <button class="example-btn" onclick="setClassName('java.util.ArrayList')">ArrayList</button>
                                <button class="example-btn" onclick="setClassName('java.io.File')">File</button>
                                <button class="example-btn" onclick="setClassName('java.util.HashMap')">HashMap</button>
                                <button class="example-btn" onclick="setClassName('java.lang.Thread')">Thread</button>
                                <button class="example-btn" onclick="loadProjectClasses()">加载项目类</button>
                            </div>
                            <div style="margin-top: 5px; font-size: 12px; color: #666;">
                                <strong>新格式:</strong> 现在返回简化的文本格式，适合LLM处理<br>
                                格式: 文件路径 + 字段列表 + 方法列表<br>
                                点击"加载项目类"获取项目中的类列表，然后点击类名快速查询
                            </div>
                        </div>
                    </div>
                    
                    <div id="projectClasses" class="project-classes"></div>
                    
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
                    
                    async function testProjectClassInfo() {
                        const className = document.getElementById('classNameInput').value.trim();
                        if (!className) {
                            document.getElementById('result').textContent = 
                                'Error: 请输入类名';
                            return;
                        }
                        
                        try {
                            const response = await fetch('/api/class?name=' + encodeURIComponent(className) + '&project=true');
                            
                            if (response.ok) {
                                // 检查响应类型
                                const contentType = response.headers.get('content-type');
                                if (contentType && contentType.includes('text/plain')) {
                                    // 文本格式响应
                                    const text = await response.text();
                                    document.getElementById('result').textContent = 
                                        'Project Class Info for ' + className + ':\n' + text;
                                } else {
                                    // JSON格式响应（错误情况）
                                    const data = await response.json();
                                    document.getElementById('result').textContent = 
                                        'Project Class Info for ' + className + ':\n' + JSON.stringify(data, null, 2);
                                }
                            } else {
                                // 错误响应，尝试解析JSON
                                try {
                                    const data = await response.json();
                                    document.getElementById('result').textContent = 
                                        'Error (' + response.status + '): ' + JSON.stringify(data, null, 2);
                                } catch (e) {
                                    const text = await response.text();
                                    document.getElementById('result').textContent = 
                                        'Error (' + response.status + '): ' + text;
                                }
                            }
                        } catch (error) {
                            document.getElementById('result').textContent = 
                                'Error: ' + error.message;
                        }
                    }
                    
                    async function testCompiledClassInfo() {
                        const className = document.getElementById('classNameInput').value.trim();
                        if (!className) {
                            document.getElementById('result').textContent = 
                                'Error: 请输入类名';
                            return;
                        }
                        
                        try {
                            const response = await fetch('/api/class?name=' + encodeURIComponent(className) + '&project=false');
                            
                            if (response.ok) {
                                // 检查响应类型
                                const contentType = response.headers.get('content-type');
                                if (contentType && contentType.includes('text/plain')) {
                                    // 文本格式响应
                                    const text = await response.text();
                                    document.getElementById('result').textContent = 
                                        'Compiled Class Info for ' + className + ':\n' + text;
                                } else {
                                    // JSON格式响应（错误情况）
                                    const data = await response.json();
                                    document.getElementById('result').textContent = 
                                        'Compiled Class Info for ' + className + ':\n' + JSON.stringify(data, null, 2);
                                }
                            } else {
                                // 错误响应，尝试解析JSON
                                try {
                                    const data = await response.json();
                                    document.getElementById('result').textContent = 
                                        'Error (' + response.status + '): ' + JSON.stringify(data, null, 2);
                                } catch (e) {
                                    const text = await response.text();
                                    document.getElementById('result').textContent = 
                                        'Error (' + response.status + '): ' + text;
                                }
                            }
                        } catch (error) {
                            document.getElementById('result').textContent = 
                                'Error: ' + error.message;
                        }
                    }
                    
                    async function loadProjectClasses() {
                        try {
                            const response = await fetch('/api/project/classes');
                            const data = await response.json();
                            
                            if (response.ok && data.classes) {
                                const projectClassesDiv = document.getElementById('projectClasses');
                                projectClassesDiv.innerHTML = '<h4>项目类列表 (共 ' + data.totalClasses + ' 个类):</h4>';
                                
                                data.classes.forEach(cls => {
                                    const classDiv = document.createElement('div');
                                    classDiv.className = 'class-item';
                                    
                                    let typeLabel = '';
                                    if (cls.isInterface) typeLabel = '[Interface]';
                                    else if (cls.isEnum) typeLabel = '[Enum]';
                                    else if (cls.isAbstract) typeLabel = '[Abstract]';
                                    else typeLabel = '[Class]';
                                    
                                    classDiv.innerHTML = cls.qualifiedName + 
                                        '<span class="class-type">' + typeLabel + '</span>';
                                    
                                    classDiv.onclick = () => {
                                        document.getElementById('classNameInput').value = cls.qualifiedName;
                                        testProjectClassInfo();
                                    };
                                    
                                    projectClassesDiv.appendChild(classDiv);
                                });
                                
                                projectClassesDiv.style.display = 'block';
                                document.getElementById('result').textContent = 
                                    'Project Classes Loaded:\n' + JSON.stringify(data, null, 2);
                            } else {
                                document.getElementById('result').textContent = 
                                    'Error loading project classes: ' + JSON.stringify(data, null, 2);
                            }
                        } catch (error) {
                            document.getElementById('result').textContent = 
                                'Error: ' + error.message;
                        }
                    }
                    
                    function setClassName(className) {
                        document.getElementById('classNameInput').value = className;
                    }
                    
                    // 支持回车键查询
                    document.addEventListener('DOMContentLoaded', function() {
                        document.getElementById('classNameInput').addEventListener('keypress', function(e) {
                            if (e.key === 'Enter') {
                                testProjectClassInfo();
                            }
                        });
                    });
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