package io.github.e1roy.jbcall

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtil
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
            val pathInfo = req.pathInfo ?: "/"
            
            // 根据请求路径设置不同的响应类型
            val isClassInfoRequest = pathInfo.startsWith("/class")
            
            if (isClassInfoRequest) {
                // 类信息请求返回纯文本
                resp.contentType = "text/plain"
            } else {
                // 其他请求返回JSON
                resp.contentType = "application/json"
            }
            
            resp.characterEncoding = "UTF-8"
            resp.setHeader("Access-Control-Allow-Origin", "*")
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            
            logger.info("收到请求: $method $pathInfo")
            
            when {
                pathInfo.startsWith("/status") -> handleStatus(req, resp)
                pathInfo.startsWith("/info") -> handleInfo(req, resp)
                pathInfo.startsWith("/echo") -> handleEcho(req, resp)
                pathInfo.startsWith("/project/classes") -> handleProjectClasses(req, resp)
                pathInfo.startsWith("/project") -> handleProject(req, resp)
                pathInfo.startsWith("/class") -> handleClassInfo(req, resp)
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
        val response = mapOf<String, Any>(
            "message" to "项目API接口",
            "available_endpoints" to listOf(
                "/api/status - 服务器状态",
                "/api/info - 服务器信息",
                "/api/echo - 回显请求",
                "/api/project - 项目信息",
                "/api/project/classes - 获取项目中的所有类列表",
                "/api/class?name=<className>&project=true - 获取项目中的类信息",
                "/api/class?name=<className>&project=false - 获取编译后的类信息"
            )
        )
        writeJsonResponse(resp, response)
    }
    
    private fun handleProjectClasses(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val projectManager = ProjectManager.getInstance()
            val openProjects = projectManager.openProjects
            
            if (openProjects.isEmpty()) {
                val response = mapOf<String, Any>(
                    "error" to "No Project",
                    "message" to "没有打开的项目",
                    "classes" to emptyList<String>()
                )
                writeJsonResponse(resp, response)
                return
            }
            
            val project = openProjects[0]
            
            // 在读取操作中执行PSI相关代码
            val result = ReadAction.compute<Map<String, Any>, Exception> {
                val javaPsiFacade = JavaPsiFacade.getInstance(project)
                val scope = GlobalSearchScope.projectScope(project)
                
                // 获取项目中的所有Java类
                val allClasses = mutableListOf<Map<String, Any>>()
                
                // 使用PsiShortNamesCache获取所有类名
                val shortNamesCache = PsiShortNamesCache.getInstance(project)
                val allClassNames = shortNamesCache.allClassNames
                
                allClassNames.forEach { className ->
                    val classes = shortNamesCache.getClassesByName(className, scope)
                    classes.forEach { psiClass ->
                        if (psiClass.qualifiedName != null) {
                            allClasses.add(mapOf<String, Any>(
                                "simpleName" to (psiClass.name ?: "Unknown"),
                                "qualifiedName" to (psiClass.qualifiedName ?: "Unknown"),
                                "packageName" to getPackageName(psiClass),
                                "isInterface" to psiClass.isInterface,
                                "isEnum" to psiClass.isEnum,
                                "isAbstract" to psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
                                "sourceFile" to (psiClass.containingFile?.name ?: "Unknown")
                            ))
                        }
                    }
                }
                
                mapOf<String, Any>(
                    "projectName" to (project.name),
                    "totalClasses" to allClasses.size,
                    "classes" to allClasses.sortedBy { it["qualifiedName"] as String }
                )
            }
            
            writeJsonResponse(resp, result)
            
        } catch (e: NoClassDefFoundError) {
            logger.warn("PSI classes not available for project class listing", e)
            resp.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            val response = mapOf<String, Any>(
                "error" to "PSI Not Available",
                "message" to "Java PSI功能不可用，请确保在Java项目中使用此功能",
                "suggestion" to "请尝试使用编译后的类查询功能"
            )
            writeJsonResponse(resp, response)
        } catch (e: Exception) {
            logger.error("获取项目类列表时发生错误", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            val response = mapOf<String, Any>(
                "error" to "Internal Server Error",
                "message" to "获取项目类列表失败: ${e.message}"
            )
            writeJsonResponse(resp, response)
        }
    }
    
    private fun handleClassInfo(req: HttpServletRequest, resp: HttpServletResponse) {
        val className = req.getParameter("name")
        val useProject = req.getParameter("project")?.toBoolean() ?: true
        
        if (className.isNullOrBlank()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            val response = mapOf<String, Any>(
                "error" to "Bad Request",
                "message" to "缺少参数 'name'，请提供完整的类名",
                "example" to "/api/class?name=com.example.MyClass&project=true"
            )
            writeJsonResponse(resp, response)
            return
        }
        
        try {
            logger.warn("🔍 开始分析类: $className, useProject: $useProject")
            
            val classInfo = if (useProject) {
                val result = analyzeProjectClass(className)
                logger.warn("📊 PSI分析结果: ${if (result != null) "有数据 (${result.size} 个字段)" else "null"}")
                result
            } else {
                val result = analyzeCompiledClass(className)
                logger.warn("📊 反射分析结果: ${if (result != null) "有数据 (${result.size} 个字段)" else "null"}")
                result
            }
            
            logger.warn("🎯 最终classInfo: ${if (classInfo != null) "有数据" else "null"}")
            
            if (classInfo != null) {
                logger.warn("✅ 准备写入文本响应")
                writeTextResponse(resp, classInfo)
                logger.warn("✅ 文本响应写入完成")
            } else {
                logger.warn("❌ classInfo为null，返回404")
                resp.status = HttpServletResponse.SC_NOT_FOUND
                val response = mapOf<String, Any>(
                    "error" to "Class Not Found",
                    "message" to "在项目中找不到类: $className",
                    "className" to className,
                    "suggestion" to "请检查类名是否正确，或尝试使用 project=false 参数查询编译后的类"
                )
                writeJsonResponse(resp, response)
            }
            
        } catch (e: Exception) {
            logger.error("分析类信息时发生错误", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            val response = mapOf<String, Any>(
                "error" to "Internal Server Error",
                "message" to "分析类信息失败: ${e.message}",
                "className" to className
            )
            writeJsonResponse(resp, response)
        }
    }
    
    private fun analyzeProjectClass(className: String): Map<String, Any>? {
        return try {
            logger.warn("🔍 analyzeProjectClass开始: $className")
            val projectManager = ProjectManager.getInstance()
            val openProjects = projectManager.openProjects
            
            if (openProjects.isEmpty()) {
                logger.warn("❌ 没有打开的项目")
                return null
            }
            
            // 使用第一个打开的项目
            val project = openProjects[0]
            logger.warn("📁 使用项目: ${project.name}")
            
            // 🔄 ReadAction.compute 是同步操作，会阻塞当前HTTP请求线程
            // 直到PSI操作完成并返回结果
            val psiResult = ReadAction.compute<Map<String, Any>?, Exception> {
                logger.warn("🔄 进入ReadAction.compute")
                // 📍 这个代码块在IntelliJ的读取线程中执行
                val psiManager = PsiManager.getInstance(project)
                
                // 尝试通过完整类名查找
                val javaPsiFacade = JavaPsiFacade.getInstance(project)
                val psiClass = javaPsiFacade.findClass(className, GlobalSearchScope.projectScope(project))
                
                if (psiClass != null) {
                    logger.warn("✅ 找到PSI类: ${psiClass.qualifiedName}")
                    // ✅ 找到类，分析并返回结果
                    val result = analyzePsiClass(psiClass)  // 返回 Map<String, Any>
                    logger.warn("📊 PSI分析完成，结果字段数: ${result.size}")
                    result
                } else {
                    logger.warn("🔍 完整类名未找到，尝试简单类名查找")
                    // 如果找不到完整类名，尝试通过简单类名查找
                    val simpleName = className.substringAfterLast('.')
                    val shortNamesCache = PsiShortNamesCache.getInstance(project)
                    val classes = shortNamesCache.getClassesByName(simpleName, GlobalSearchScope.projectScope(project))
                    
                    logger.warn("🔍 简单类名 '$simpleName' 找到 ${classes.size} 个候选")
                    
                    val matchingClass = classes.find { it.qualifiedName == className }
                    if (matchingClass != null) {
                        logger.warn("✅ 找到匹配的PSI类: ${matchingClass.qualifiedName}")
                        // ✅ 找到匹配的类，分析并返回结果
                        val result = analyzePsiClass(matchingClass)  // 返回 Map<String, Any>
                        logger.warn("📊 PSI分析完成，结果字段数: ${result.size}")
                        result
                    } else {
                        logger.warn("❌ 没找到匹配的类")
                        // ❌ 没找到类，返回null
                        null
                    }
                }
                // 📤 这里的返回值会成为 ReadAction.compute 的返回值
            }
            
            // 📥 psiResult 现在包含了PSI操作的结果
            // 🔄 HTTP请求线程继续执行，返回结果给调用者
            logger.warn("📥 ReadAction.compute完成，结果: ${if (psiResult != null) "有数据 (${psiResult.size} 字段)" else "null"}")
            psiResult
            
        } catch (e: NoClassDefFoundError) {
            // 如果PSI类不可用，回退到反射模式
            logger.warn("PSI classes not available, falling back to reflection mode", e)
            analyzeCompiledClass(className)
        } catch (e: Exception) {
            logger.error("Error analyzing project class with PSI", e)
            // 回退到反射模式
            analyzeCompiledClass(className)
        }
    }
    
    private fun analyzeCompiledClass(className: String): Map<String, Any>? {
        return try {
            val clazz = Class.forName(className)
            analyzeClass(clazz)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
    
    private fun analyzePsiClass(psiClass: PsiClass): Map<String, Any> {
        // 获取源文件路径
        val sourceFile = psiClass.containingFile
        val filePath = sourceFile?.virtualFile?.path ?: "Unknown"
        
        return mapOf<String, Any>(
            "className" to (psiClass.qualifiedName ?: psiClass.name ?: "Unknown"),
            "simpleName" to (psiClass.name ?: "Unknown"),
            "packageName" to getPackageName(psiClass),
            "superClass" to (getSuperClassName(psiClass) ?: ""),
            "interfaces" to getInterfaceNames(psiClass),
            "modifiers" to getPsiModifiers(psiClass),
            "fields" to analyzePsiFields(psiClass),
            "methods" to analyzePsiMethods(psiClass),
            "constructors" to analyzePsiConstructors(psiClass),
            "annotations" to getPsiAnnotations(psiClass),
            "isInterface" to psiClass.isInterface,
            "isEnum" to psiClass.isEnum,
            "isAbstract" to psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            "sourceFile" to (psiClass.containingFile?.name ?: "Unknown"),
            "filePath" to filePath
        )
    }
    
    private fun getPackageName(psiClass: PsiClass): String {
        val containingFile = psiClass.containingFile
        return if (containingFile is PsiJavaFile) {
            containingFile.packageName
        } else {
            psiClass.qualifiedName?.substringBeforeLast('.', "") ?: ""
        }
    }
    
    private fun getSuperClassName(psiClass: PsiClass): String? {
        return psiClass.superClass?.qualifiedName
    }
    
    private fun getInterfaceNames(psiClass: PsiClass): List<String> {
        return psiClass.interfaces.mapNotNull { it.qualifiedName }
    }
    
    private fun getPsiModifiers(element: PsiModifierListOwner): List<String> {
        val modifiers = mutableListOf<String>()
        val modifierList = element.modifierList
        
        if (modifierList != null) {
            if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
            if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
            if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
            if (modifierList.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
            if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
            if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
            if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers.add("synchronized")
            if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) modifiers.add("volatile")
            if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) modifiers.add("transient")
            if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) modifiers.add("native")
        }
        
        return modifiers
    }
    
    private fun getPsiAnnotations(element: PsiModifierListOwner): List<String> {
        val modifierList = element.modifierList
        return modifierList?.annotations?.mapNotNull { 
            it.qualifiedName?.substringAfterLast('.') 
        } ?: emptyList()
    }
    
    private fun analyzePsiFields(psiClass: PsiClass): List<Map<String, Any>> {
        return psiClass.fields.map { field: PsiField ->
            mapOf<String, Any>(
                "name" to (field.name ?: ""),
                "type" to getTypeString(field.type),  // 使用安全的类型转换
                "modifiers" to getPsiModifiers(field),
                "annotations" to getPsiAnnotations(field),
                "hasInitializer" to (field.initializer != null)
            )
        }
    }
    
    private fun analyzePsiMethods(psiClass: PsiClass): List<Map<String, Any>> {
        return psiClass.methods.map { method: PsiMethod ->
            mapOf<String, Any>(
                "name" to (method.name ?: ""),
                "returnType" to getTypeString(method.returnType),
                "parameters" to method.parameters.map { param ->
                    mapOf(
                        "name" to (param.name ?: ""),
                        "type" to getJvmParameterTypeString(param)
                    )
                },
                "modifiers" to getPsiModifiers(method),
                "exceptions" to getExceptionTypes(method),
                "annotations" to getPsiAnnotations(method),
                "isConstructor" to method.isConstructor
            )
        }
    }
    
    private fun analyzePsiConstructors(psiClass: PsiClass): List<Map<String, Any>> {
        return psiClass.constructors.map { constructor: PsiMethod ->
            mapOf<String, Any>(
                "parameters" to constructor.parameters.map { param ->
                    mapOf(
                        "name" to (param.name ?: ""),
                        "type" to getJvmParameterTypeString(param)
                    )
                },
                "modifiers" to getPsiModifiers(constructor),
                "exceptions" to getExceptionTypes(constructor),
                "annotations" to getPsiAnnotations(constructor)
            )
        }
    }
    
    private fun analyzeClass(clazz: Class<*>): Map<String, Any> {
        return mapOf<String, Any>(
            "className" to clazz.name,
            "simpleName" to clazz.simpleName,
            "packageName" to (clazz.`package`?.name ?: ""),
            "superClass" to (clazz.superclass?.name ?: ""),
            "interfaces" to clazz.interfaces.map { it.name },
            "modifiers" to getModifiers(clazz.modifiers),
            "fields" to analyzeFields(clazz),
            "methods" to analyzeMethods(clazz),
            "constructors" to analyzeConstructors(clazz),
            "annotations" to clazz.annotations.map { it.annotationClass.simpleName },
            "filePath" to "Compiled class: ${clazz.name}"
        )
    }
    
    private fun analyzeFields(clazz: Class<*>): List<Map<String, Any>> {
        return clazz.declaredFields.map { field ->
            mapOf<String, Any>(
                "name" to field.name,
                "type" to field.type.name,
                "modifiers" to getModifiers(field.modifiers),
                "annotations" to field.annotations.map { it.annotationClass.simpleName }
            )
        }
    }
    
    private fun analyzeMethods(clazz: Class<*>): List<Map<String, Any>> {
        return clazz.declaredMethods.map { method ->
            mapOf<String, Any>(
                "name" to method.name,
                "returnType" to method.returnType.name,
                "parameters" to method.parameters.map { param ->
                    mapOf<String, Any>(
                        "name" to param.name,
                        "type" to param.type.name
                    )
                },
                "modifiers" to getModifiers(method.modifiers),
                "exceptions" to method.exceptionTypes.map { it.name },
                "annotations" to method.annotations.map { it.annotationClass.simpleName }
            )
        }
    }
    
    private fun analyzeConstructors(clazz: Class<*>): List<Map<String, Any>> {
        return clazz.declaredConstructors.map { constructor ->
            mapOf<String, Any>(
                "parameters" to constructor.parameters.map { param ->
                    mapOf<String, Any>(
                        "name" to param.name,
                        "type" to param.type.name
                    )
                },
                "modifiers" to getModifiers(constructor.modifiers),
                "exceptions" to constructor.exceptionTypes.map { it.name },
                "annotations" to constructor.annotations.map { it.annotationClass.simpleName }
            )
        }
    }
    
    private fun getModifiers(modifiers: Int): List<String> {
        val modifierList = mutableListOf<String>()
        
        if (java.lang.reflect.Modifier.isPublic(modifiers)) modifierList.add("public")
        if (java.lang.reflect.Modifier.isPrivate(modifiers)) modifierList.add("private")
        if (java.lang.reflect.Modifier.isProtected(modifiers)) modifierList.add("protected")
        if (java.lang.reflect.Modifier.isStatic(modifiers)) modifierList.add("static")
        if (java.lang.reflect.Modifier.isFinal(modifiers)) modifierList.add("final")
        if (java.lang.reflect.Modifier.isAbstract(modifiers)) modifierList.add("abstract")
        if (java.lang.reflect.Modifier.isSynchronized(modifiers)) modifierList.add("synchronized")
        if (java.lang.reflect.Modifier.isVolatile(modifiers)) modifierList.add("volatile")
        if (java.lang.reflect.Modifier.isTransient(modifiers)) modifierList.add("transient")
        if (java.lang.reflect.Modifier.isNative(modifiers)) modifierList.add("native")
        
        return modifierList
    }
    
    // 安全的类型转换方法，避免PSI对象序列化问题
    private fun getTypeString(psiType: PsiType?): String {
        return try {
            psiType?.presentableText ?: "void"
        } catch (e: Exception) {
            // 如果获取presentableText失败，尝试其他方法
            psiType?.canonicalText ?: "unknown"
        }
    }
    
    // 安全的参数类型获取方法
    private fun getParameterTypeString(param: PsiParameter): String {
        return try {
            // 对于PsiParameter，直接使用type.presentableText
            param.type.presentableText
        } catch (e: Exception) {
            try {
                // 如果失败，尝试canonicalText
                param.type.canonicalText
            } catch (e2: Exception) {
                // 最后的备选方案
                "unknown"
            }
        }
    }
    
    // 安全的JVM参数类型获取方法
    private fun getJvmParameterTypeString(param: com.intellij.lang.jvm.JvmParameter): String {
        return try {
            // 对于JvmParameter，使用type的字符串表示
            param.type.toString()
        } catch (e: Exception) {
            try {
                // 如果失败，尝试其他方法
                param.type.toString()
            } catch (e2: Exception) {
                // 最后的备选方案
                "unknown"
            }
        }
    }
    
    // 安全的异常类型获取方法
    private fun getExceptionTypes(method: PsiMethod): List<String> {
        return try {
            method.throwsList.referencedTypes.mapNotNull { 
                try {
                    it.presentableText
                } catch (e: Exception) {
                    it.canonicalText
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
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
                "/api/project",
                "/api/project/classes",
                "/api/class?name=<className>&project=true"
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
            logger.warn("🔄 开始序列化JSON数据...")
            
            // 配置ObjectMapper以处理可能的序列化问题
            val json = try {
                objectMapper.writeValueAsString(data)
            } catch (e: Exception) {
                logger.error("❌ JSON序列化失败，尝试简化数据结构", e)
                // 如果序列化失败，返回错误信息
                val errorData = mapOf(
                    "error" to "Serialization Error",
                    "message" to "数据序列化失败: ${e.message}",
                    "originalDataType" to data.javaClass.simpleName
                )
                objectMapper.writeValueAsString(errorData)
            }
            
            logger.warn("📏 JSON长度: ${json.length}")
            logger.warn("📄 JSON预览: ${json.take(200)}...")
            
            logger.warn("📤 写入HTTP响应...")
            resp.writer.write(json)
            resp.writer.flush()
            logger.warn("✅ HTTP响应写入并刷新完成")
        } catch (e: IOException) {
            logger.error("❌ 写入响应失败", e)
        }
    }

    private fun writeTextResponse(resp: HttpServletResponse, classInfo: Map<String, Any>) {
        try {
            logger.warn("🔄 开始生成文本格式...")
            
            val text = formatClassInfoAsText(classInfo)
            
            logger.warn("📏 文本长度: ${text.length}")
            logger.warn("📄 文本预览: ${text.take(200)}...")
            
            logger.warn("📤 写入HTTP响应...")
            resp.writer.write(text)
            resp.writer.flush()
            logger.warn("✅ HTTP响应写入并刷新完成")
        } catch (e: IOException) {
            logger.error("❌ 写入响应失败", e)
        }
    }

    private fun formatClassInfoAsText(classInfo: Map<String, Any>): String {
        val sb = StringBuilder()
        
        // 获取文件路径
        val filePath = classInfo["filePath"] as? String ?: "Unknown"
        sb.appendLine(filePath)
        
        // 获取字段信息
        val fields = classInfo["fields"] as? List<Map<String, Any>> ?: emptyList()
        fields.forEach { field ->
            val modifiers = field["modifiers"] as? List<String> ?: emptyList()
            val type = field["type"] as? String ?: "unknown"
            val name = field["name"] as? String ?: "unknown"
            
            val modifierStr = if (modifiers.isNotEmpty()) "${modifiers.joinToString(" ")} " else ""
            sb.appendLine("- $modifierStr$type $name;")
        }
        
        // 获取方法信息
        val methods = classInfo["methods"] as? List<Map<String, Any>> ?: emptyList()
        methods.forEach { method ->
            val modifiers = method["modifiers"] as? List<String> ?: emptyList()
            val returnType = method["returnType"] as? String ?: "void"
            val name = method["name"] as? String ?: "unknown"
            val parameters = method["parameters"] as? List<Map<String, Any>> ?: emptyList()
            
            val modifierStr = if (modifiers.isNotEmpty()) "${modifiers.joinToString(" ")} " else ""
            val paramStr = parameters.joinToString(", ") { param ->
                val paramType = param["type"] as? String ?: "unknown"
                val paramName = param["name"] as? String ?: "arg"
                "$paramType $paramName"
            }
            
            sb.appendLine("- $modifierStr$returnType $name($paramStr)")
        }
        
        // 获取构造函数信息
        val constructors = classInfo["constructors"] as? List<Map<String, Any>> ?: emptyList()
        constructors.forEach { constructor ->
            val modifiers = constructor["modifiers"] as? List<String> ?: emptyList()
            val parameters = constructor["parameters"] as? List<Map<String, Any>> ?: emptyList()
            val className = classInfo["simpleName"] as? String ?: "Unknown"
            
            val modifierStr = if (modifiers.isNotEmpty()) "${modifiers.joinToString(" ")} " else ""
            val paramStr = parameters.joinToString(", ") { param ->
                val paramType = param["type"] as? String ?: "unknown"
                val paramName = param["name"] as? String ?: "arg"
                "$paramType $paramName"
            }
            
            sb.appendLine("- $modifierStr$className($paramStr)")
        }
        
        return sb.toString()
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // 处理CORS预检请求
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }
}