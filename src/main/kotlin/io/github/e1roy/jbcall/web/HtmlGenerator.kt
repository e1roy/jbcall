package io.github.e1roy.jbcall.web

/**
 * HTML页面生成器
 */
object HtmlGenerator {
    
    fun generateIndexPage(): String = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>JBCall HTTP服务器</title>
            <style>
                ${getStyles()}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🚀 JBCall HTTP服务器</h1>
                <p class="subtitle">IntelliJ IDEA 项目分析工具</p>
                
                <div class="section">
                    <h2>📋 API接口列表</h2>
                    <div class="api-list">
                        ${getApiList()}
                    </div>
                </div>
                
                <div class="section">
                    <h2>🔧 快速测试</h2>
                    <div class="test-buttons">
                        <button onclick="testApi('/api/status')">测试状态</button>
                        <button onclick="testApi('/api/info')">系统信息</button>
                        <button onclick="testApi('/api/project')">项目信息</button>
                        <button onclick="testApi('/api/project/classes')">项目类列表</button>
                    </div>
                    <div class="test-form">
                        <h3>类分析测试</h3>
                        <input type="text" id="className" placeholder="输入类名，如: java.lang.String" />
                        <button onclick="analyzeClass()">分析类</button>
                        <button onclick="analyzeClassSimple()">分析类-简洁版</button>
                    </div>
                    <div class="test-form">
                        <h3>🔍 类错误检查测试</h3>
                        <input type="text" id="errorCheckClassName" placeholder="输入类名，如: TestMain" />
                        <button onclick="checkClassErrors()">检查错误</button>
                        <button onclick="openErrorCheckPage()" style="background: #28a745;">打开专用页面</button>
                    </div>
                    <div class="test-form">
                        <h3>方法体获取测试</h3>
                        <input type="text" id="methodClassName" placeholder="输入类名，如: TestMain" />
                        <input type="text" id="methodName" placeholder="输入方法名，如: test" />
                        <button onclick="getMethodBody()">获取方法体</button>
                    </div>
                </div>
                
                <div class="section">
                    <h2>📊 响应结果</h2>
                    <pre id="result">点击上方按钮测试API接口...</pre>
                </div>
            </div>
            
            <script>
                ${getJavaScript()}
            </script>
        </body>
        </html>
    """.trimIndent()
    
    private fun getStyles(): String = """
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 1000px;
            margin: 0 auto;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.1);
        }
        h1 {
            color: #333;
            text-align: center;
            margin-bottom: 10px;
        }
        .subtitle {
            text-align: center;
            color: #666;
            margin-bottom: 30px;
        }
        .section {
            margin: 30px 0;
        }
        .api-list {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
        }
        .api-item {
            margin: 8px 0;
            padding: 12px;
            background: white;
            border-radius: 6px;
            border-left: 4px solid #007acc;
            font-family: 'Courier New', monospace;
            font-size: 14px;
        }
        .method {
            font-weight: bold;
            color: #007acc;
        }
        .test-buttons, .test-form {
            margin: 15px 0;
        }
        button {
            background: #007acc;
            color: white;
            border: none;
            padding: 10px 16px;
            border-radius: 6px;
            cursor: pointer;
            margin: 4px;
            font-size: 14px;
        }
        button:hover {
            background: #005a9e;
        }
        input[type="text"] {
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 6px;
            width: 300px;
            margin-right: 10px;
        }
        #result {
            background: #2d3748;
            color: #e2e8f0;
            padding: 20px;
            border-radius: 8px;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            line-height: 1.4;
            max-height: 400px;
            overflow-y: auto;
            white-space: pre-wrap;
        }
    """.trimIndent()
    
    private fun getApiList(): String = """
        <div class="api-item"><span class="method">GET</span> /api/status - 服务器状态检查</div>
        <div class="api-item"><span class="method">GET</span> /api/info - 系统信息查询</div>
        <div class="api-item"><span class="method">GET</span> /api/echo - 请求回显测试</div>
        <div class="api-item"><span class="method">GET</span> /api/project - 项目基本信息</div>
        <div class="api-item"><span class="method">GET</span> /api/project/classes - 项目类列表</div>
        <div class="api-item"><span class="method">GET</span> /api/class?class=&lt;类名&gt;&format=json - 类详细分析(JSON格式)</div>
        <div class="api-item"><span class="method">GET</span> /api/class?class=&lt;类名&gt;&format=simple - 类简洁分析(文本格式)</div>
        <div class="api-item"><span class="method">GET</span> /api/class/errors?class=&lt;类名&gt; - 类错误检查</div>
        <div class="api-item"><span class="method">GET</span> /api/method?class=&lt;类名&gt;&method=&lt;方法名&gt; - 方法体获取</div>
        <div class="api-item"><span class="method">GET</span> /api/error-check - 错误检查工具页面</div>
    """.trimIndent()
    
    private fun getJavaScript(): String = """
        async function testApi(endpoint) {
            const resultElement = document.getElementById('result');
            resultElement.textContent = '请求中...';
            
            try {
                const response = await fetch(endpoint);
                const data = await response.json();
                resultElement.textContent = JSON.stringify(data, null, 2);
            } catch (error) {
                resultElement.textContent = '请求失败: ' + error.message;
            }
        }
        
        async function analyzeClass() {
            const className = document.getElementById('className').value.trim();
            if (!className) {
                alert('请输入类名');
                return;
            }
            
            const endpoint = `/api/class?class=${'$'}{encodeURIComponent(className)}&format=json`;
            await testApi(endpoint);
        }
        
        async function analyzeClassSimple() {
            const className = document.getElementById('className').value.trim();
            if (!className) {
                alert('请输入类名');
                return;
            }
            
            const resultElement = document.getElementById('result');
            resultElement.textContent = '请求中...';
            
            try {
                const endpoint = `/api/class?class=${'$'}{encodeURIComponent(className)}&format=simple`;
                const response = await fetch(endpoint);
                const data = await response.text();
                resultElement.textContent = data;
            } catch (error) {
                resultElement.textContent = '请求失败: ' + error.message;
            }
        }
        
        async function getMethodBody() {
            const className = document.getElementById('methodClassName').value.trim();
            const methodName = document.getElementById('methodName').value.trim();
            
            if (!className) {
                alert('请输入类名');
                return;
            }
            
            if (!methodName) {
                alert('请输入方法名');
                return;
            }
            
            const resultElement = document.getElementById('result');
            resultElement.textContent = '请求中...';
            
            try {
                const endpoint = `/api/method?class=${'$'}{encodeURIComponent(className)}&method=${'$'}{encodeURIComponent(methodName)}`;
                const response = await fetch(endpoint);
                const data = await response.json();
                
                if (data.success && data.data.methods && data.data.methods.length > 0) {
                    let output = `找到 ${'$'}{data.data.methods.length} 个匹配的方法:\n\n`;
                    
                    data.data.methods.forEach((method, index) => {
                        output += `=== 方法 ${'$'}{index + 1} ===\n`;
                        output += `类名: ${'$'}{method.className}\n`;
                        output += `方法签名: ${'$'}{method.signature}\n`;
                        
                        if (method.fileName) {
                            output += `文件: ${'$'}{method.fileName}`;
                            if (method.startLine) {
                                output += ` (行 ${'$'}{method.startLine}-${'$'}{method.endLine})`;
                            }
                            output += '\n';
                        }
                        
                        if (method.hasBody) {
                            output += `\n完整方法代码:\n${'$'}{method.fullMethodText}\n\n`;
                        } else {
                            output += `\n注意: ${'$'}{method.note || '无方法体'}\n`;
                            output += `方法声明: ${'$'}{method.fullMethodText}\n\n`;
                        }
                    });
                    
                    resultElement.textContent = output;
                } else {
                    resultElement.textContent = JSON.stringify(data, null, 2);
                }
            } catch (error) {
                resultElement.textContent = '请求失败: ' + error.message;
            }
        }
        
        async function checkClassErrors() {
            const className = document.getElementById('errorCheckClassName').value.trim();
            if (!className) {
                alert('请输入类名');
                return;
            }
            
            const resultElement = document.getElementById('result');
            resultElement.textContent = '正在检查类错误...';
            
            try {
                const endpoint = `/api/class/errors?class=${'$'}{encodeURIComponent(className)}`;
                const response = await fetch(endpoint);
                const data = await response.json();
                
                if (data.success) {
                    // 直接显示返回的文本
                    resultElement.textContent = data.data;
                } else {
                    resultElement.textContent = `错误: ${'$'}{data.error?.message || '未知错误'}`;
                }
            } catch (error) {
                resultElement.textContent = `请求失败: ${'$'}{error.message}\n\n请确保 JBCall 服务正在运行`;
            }
        }
        
        function openErrorCheckPage() {
            window.open('/api/error-check', '_blank');
        }
    """.trimIndent()
}