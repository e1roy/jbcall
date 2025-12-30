# 🔍 JBCall 类错误检查功能

## 📋 功能概述

新增了类错误检查功能，可以检测IntelliJ IDEA编辑器中的编译错误和警告。

## 🚀 功能特性

### 1. 智能类名匹配
- 支持简单类名搜索（如：`TestMain`）
- 支持全限定类名（如：`org.example.TestMain`）
- 当找到多个同名类时，会列出所有匹配的类供选择

### 2. 详细错误信息
- **错误类型**：编译错误、警告
- **位置信息**：行号、偏移量
- **问题代码**：具体的问题代码片段
- **上下文代码**：问题代码的上下文（前后2行）
- **文件信息**：文件名和完整路径

### 3. 多种访问方式
- **主页集成**：在主页面直接测试
- **专用页面**：功能更丰富的独立页面
- **API接口**：可编程调用

## 🌐 Web界面

### 主页面测试
访问：`http://127.0.0.1:8080`

在"🔍 类错误检查测试"部分：
1. 输入类名（如：`TestMain`）
2. 点击"检查错误"按钮
3. 查看结果

### 专用错误检查页面
访问：`http://127.0.0.1:8080/api/error-check`

功能特性：
- 🎨 美观的界面设计
- 📝 详细的使用示例
- 🔍 智能多类匹配处理
- 📊 结构化的错误展示
- 💡 上下文代码高亮

## 🔧 API接口

### 端点
```
GET /api/class/errors?class=<类名>
```

### 请求示例
```bash
# 检查简单类名
curl "http://127.0.0.1:8080/api/class/errors?class=TestMain"

# 检查全限定类名
curl "http://127.0.0.1:8080/api/class/errors?class=org.example.TestMain"
```

### 响应格式

#### 成功响应（无错误）
```json
{
  "success": true,
  "data": {
    "className": "org.example.TestMain",
    "fileName": "TestMain.java",
    "filePath": "/path/to/TestMain.java",
    "hasErrors": false,
    "hasWarnings": false,
    "errorCount": 0,
    "warningCount": 0,
    "errors": [],
    "warnings": [],
    "summary": "✅ 类 org.example.TestMain 没有发现编译错误或警告"
  }
}
```

#### 成功响应（有错误）
```json
{
  "success": true,
  "data": {
    "className": "org.example.TestMain",
    "fileName": "TestMain.java",
    "filePath": "/path/to/TestMain.java",
    "hasErrors": true,
    "hasWarnings": true,
    "errorCount": 1,
    "warningCount": 1,
    "errors": [
      {
        "message": "Cannot resolve symbol 'unknownMethod'",
        "startLine": 15,
        "endLine": 15,
        "startOffset": 245,
        "endOffset": 258,
        "problemText": "unknownMethod",
        "contextText": "public void test() {\n    unknownMethod();\n}",
        "contextStartLine": 14,
        "contextEndLine": 16,
        "severity": "ERROR",
        "fileName": "TestMain.java",
        "filePath": "/path/to/TestMain.java"
      }
    ],
    "warnings": [
      {
        "message": "Unused variable 'unused'",
        "startLine": 12,
        "endLine": 12,
        "startOffset": 198,
        "endOffset": 204,
        "problemText": "unused",
        "contextText": "String unused = \"test\";\nSystem.out.println(\"Hello\");",
        "contextStartLine": 12,
        "contextEndLine": 13,
        "severity": "WARNING",
        "fileName": "TestMain.java",
        "filePath": "/path/to/TestMain.java"
      }
    ],
    "summary": "类 org.example.TestMain 检查结果：\n❌ 发现 1 个错误\n⚠️ 发现 1 个警告"
  }
}
```

#### 多类匹配响应
```json
{
  "success": true,
  "data": {
    "type": "multiple_matches",
    "message": "找到多个同名类，请使用全限定名指定具体的类：",
    "matches": ["org.e1roy.TestMain", "org.example.TestMain"],
    "suggestion": "请使用以下全限定名之一重新查询：org.e1roy.TestMain, org.example.TestMain"
  }
}
```

#### 错误响应
```json
{
  "success": false,
  "error": {
    "code": "CLASS_NOT_FOUND",
    "message": "未找到类: NonExistentClass",
    "details": {
      "className": "NonExistentClass"
    }
  }
}
```

## 🛠️ 技术实现

### 核心组件
1. **ErrorCheckHandler.kt** - 错误检查处理器
2. **ApiServlet.kt** - 路由配置
3. **HtmlGenerator.kt** - Web界面生成
4. **error-check.html** - 专用页面

### 关键技术
- **DaemonCodeAnalyzer** - IntelliJ IDEA代码分析器
- **HighlightInfo** - 高亮信息获取
- **PSI API** - 程序结构接口
- **ReadAction** - 安全的读取操作

## 📚 使用场景

### 1. 开发调试
- 快速检查类的编译状态
- 定位具体的错误位置
- 查看详细的错误信息

### 2. 代码审查
- 批量检查类的质量
- 识别潜在的问题
- 生成错误报告

### 3. CI/CD集成
- 自动化错误检查
- 构建前验证
- 质量门禁

## 🎯 最佳实践

### 1. 使用全限定类名
```bash
# 推荐
curl "http://127.0.0.1:8080/api/class/errors?class=com.example.service.UserService"

# 避免（可能有歧义）
curl "http://127.0.0.1:8080/api/class/errors?class=UserService"
```

### 2. 处理多类匹配
当返回多类匹配时，选择正确的全限定类名重新查询。

### 3. 错误处理
始终检查响应的 `success` 字段，并适当处理错误情况。

## 🔗 相关链接

- 主页面：`http://127.0.0.1:8080`
- 错误检查页面：`http://127.0.0.1:8080/api/error-check`
- API文档：`http://127.0.0.1:8080/api`（404页面包含所有端点列表）

---

**注意**：此功能需要IntelliJ IDEA项目处于打开状态，并且代码分析器已完成初始扫描。