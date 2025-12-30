package io.github.e1roy.jbcall.handler

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.github.e1roy.jbcall.model.ApiError
import io.github.e1roy.jbcall.model.ApiResponse
import io.github.e1roy.jbcall.util.TypeUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.lang.reflect.Modifier

/**
 * 处理类分析相关请求
 */
class ClassAnalysisHandler : BaseHandler() {

    fun handleClassAnalysis(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)

        val className = getParameter(request, "class")
        if (className == null) {
            sendError(response, "缺少class参数", 400)
            return
        }

        val format = getParameter(request, "format") ?: "json"

        try {
            val classInfo = analyzeClass(className)
            if (classInfo == null) {
                sendError(response, "未找到类: $className", 404)
                return
            }

            // 检查是否是多个匹配的情况
            if (classInfo["error"] == "multiple_matches") {
                when (format.lowercase()) {
                    "simple" -> sendMultipleClassMatchesResponse(response, classInfo)
                    else -> {
                        val successResponse = ApiResponse(
                            success = true,
                            data = mapOf(
                                "type" to "multiple_matches",
                                "message" to (classInfo["message"] as String),
                                "matches" to (classInfo["matches"] ?: emptyList<String>()),
                                "suggestion" to (classInfo["suggestion"] ?: "")
                            )
                        )
                        sendJsonResponse(response, successResponse)
                    }
                }
                return
            }

            when (format.lowercase()) {
                "text" -> sendTextResponse(response, classInfo, className)
                "simple" -> sendSimpleResponse(response, classInfo, className)
                else -> sendJsonResponse(response, ApiResponse.success(classInfo))
            }
        } catch (e: Exception) {
            sendError(response, "分析类失败: ${e.message}")
        }
    }

    fun handleMethodBody(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)

        val className = getParameter(request, "class")
        val methodName = getParameter(request, "method")

        if (className == null) {
            sendError(response, "缺少class参数", 400)
            return
        }

        if (methodName == null) {
            sendError(response, "缺少method参数", 400)
            return
        }

        try {
            val methodBodies = getMethodBodies(className, methodName)
            if (methodBodies.isEmpty()) {
                // 返回错误响应，使用标准API格式
                val errorText = buildString {
                    appendLine("ERROR: 未找到方法 $className.$methodName")
                    appendLine()
                    
                    // 检查是否能找到类
                    val foundClasses = if (!className.contains('.') || className.endsWith(".java")) {
                        val searchName = if (className.endsWith(".java")) {
                            className.removeSuffix(".java")
                        } else {
                            className
                        }
                        findClassesBySimpleName(searchName)
                    } else {
                        listOf(className)
                    }
                    
                    if (foundClasses.isEmpty()) {
                        appendLine("未找到类: $className")
                    } else {
                        appendLine("找到的类:")
                        foundClasses.forEach { appendLine("- $it") }
                        appendLine()
                        
                        // 显示每个类中的可用方法
                        for (fullClassName in foundClasses) {
                            val classInfo = analyzePsiClass(fullClassName)
                            if (classInfo != null) {
                                val methods = (classInfo["methods"] as? List<*>)?.map { method ->
                                    val methodMap = method as Map<*, *>
                                    methodMap["name"]
                                } ?: emptyList<Any>()
                                
                                if (methods.isNotEmpty()) {
                                    appendLine("类 $fullClassName 中的方法:")
                                    methods.forEach { appendLine("  - $it") }
                                    appendLine()
                                }
                            }
                        }
                    }
                }
                
                val errorResponse = ApiResponse(
                    success = false,
                    data = null as String?,
                    error = ApiError(
                        code = "METHOD_NOT_FOUND",
                        message = errorText.trim(),
                        details = mapOf(
                            "className" to className,
                            "methodName" to methodName
                        )
                    )
                )
                response.status = 404
                sendJsonResponse(response, errorResponse)
                return
            }

            // 返回方法体文本格式
            sendMethodBodyTextResponse(response, methodBodies)
        } catch (e: Exception) {
            sendError(response, "获取方法体失败: ${e.message}")
        }
    }

    private fun analyzeClass(className: String): Map<String, Any>? {
        // 如果是简单类名（不包含包名），先搜索所有匹配的类
        if (!className.contains('.') || className.endsWith(".java")) {
            val searchName = if (className.endsWith(".java")) {
                className.removeSuffix(".java")
            } else {
                className
            }

            val matchingClasses = findClassesBySimpleName(searchName)

            if (matchingClasses.isEmpty()) {
                return null
            } else if (matchingClasses.size > 1) {
                // 返回多个匹配的类信息
                return mapOf(
                    "error" to "multiple_matches",
                    "message" to "找到多个同名类，请使用全限定名指定具体的类：",
                    "matches" to matchingClasses,
                    "suggestion" to "请使用以下全限定名之一重新查询：${matchingClasses.joinToString(", ")}"
                )
            } else {
                // 只有一个匹配，直接分析
                return analyzeClassByFullName(matchingClasses.first())
            }
        } else {
            // 全限定名，直接分析
            return analyzeClassByFullName(className)
        }
    }

    private fun analyzeClassByFullName(className: String): Map<String, Any>? {
        // 首先尝试PSI分析
        val psiAnalysis = analyzePsiClass(className)
        if (psiAnalysis != null) return psiAnalysis

        // 如果PSI分析失败，尝试反射分析
        return analyzeReflectionClass(className)
    }

    private fun findClassesBySimpleName(simpleName: String): List<String> {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return emptyList()

        val project = projects.first()
        return ReadAction.compute<List<String>, Exception> {
            val results = mutableSetOf<String>()

            // 方法1: 使用 JavaPsiFacade 搜索
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)
            val allClasses = psiFacade.findClasses(simpleName, scope)
            allClasses.mapNotNullTo(results) { it.qualifiedName }

            // 方法2: 使用 PsiShortNamesCache 搜索（类似于 Ctrl+Shift+N）
            val shortNamesCache = PsiShortNamesCache.getInstance(project)
            val classesByName = shortNamesCache.getClassesByName(simpleName, scope)
            classesByName.mapNotNullTo(results) { it.qualifiedName }

            // 方法3: 搜索文件名匹配的 Java 文件
            val psiManager = PsiManager.getInstance(project)
            try {
                com.intellij.psi.search.FilenameIndex.processFilesByName(
                    "$simpleName.java",
                    false,
                    { psiFileSystemItem ->
                        try {
                            val virtualFile = psiFileSystemItem.virtualFile
                            if (virtualFile != null && scope.contains(virtualFile)) {
                                val psiFile = psiManager.findFile(virtualFile)
                                if (psiFile is PsiJavaFile) {
                                    psiFile.classes.forEach { psiClass ->
                                        if (psiClass.name == simpleName) {
                                            psiClass.qualifiedName?.let { results.add(it) }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略单个文件的处理错误
                        }
                        true
                    },
                    scope,
                    project,
                    null
                )
            } catch (e: Exception) {
                // 忽略索引错误，继续其他搜索方法
            }

            results.toList().sorted()
        } ?: emptyList()
    }

    private fun analyzePsiClass(className: String): Map<String, Any>? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return null

        val project = projects.first()
        return ReadAction.compute<Map<String, Any>?, Exception> {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)
            val psiClass = psiFacade.findClass(className, scope) ?: return@compute null

            buildClassInfo(psiClass, "psi")
        }
    }

    private fun analyzeReflectionClass(className: String): Map<String, Any>? {
        return try {
            val clazz = Class.forName(className)
            buildReflectionClassInfo(clazz)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private fun buildClassInfo(psiClass: PsiClass, source: String): Map<String, Any> {
        return mapOf<String, Any>(
            "name" to (psiClass.qualifiedName ?: psiClass.name ?: "Unknown"),
            "package" to (psiClass.qualifiedName?.substringBeforeLast('.') ?: ""),
            "type" to getClassType(psiClass),
            "modifiers" to getModifiers(psiClass),
            "superClass" to (psiClass.superClass?.qualifiedName ?: ""),
            "interfaces" to psiClass.interfaces.mapNotNull { it.qualifiedName },
            "fields" to psiClass.fields.map { buildFieldInfo(it) },
            "methods" to psiClass.methods.map { buildMethodInfo(it) },
            "constructors" to psiClass.constructors.map { buildMethodInfo(it) },
            "innerClasses" to psiClass.innerClasses.mapNotNull { it.qualifiedName },
            "source" to source
        )
    }

    private fun buildReflectionClassInfo(clazz: Class<*>): Map<String, Any> {
        return mapOf<String, Any>(
            "name" to clazz.name,
            "package" to (clazz.`package`?.name ?: ""),
            "type" to when {
                clazz.isInterface -> "interface"
                clazz.isEnum -> "enum"
                clazz.isAnnotation -> "annotation"
                else -> "class"
            },
            "modifiers" to Modifier.toString(clazz.modifiers).split(" ").filter { it.isNotEmpty() },
            "superClass" to (clazz.superclass?.name ?: ""),
            "interfaces" to clazz.interfaces.map { it.name },
            "fields" to clazz.declaredFields.map { field ->
                mapOf<String, Any>(
                    "name" to field.name,
                    "type" to field.type.name,
                    "modifiers" to Modifier.toString(field.modifiers).split(" ").filter { it.isNotEmpty() }
                )
            },
            "methods" to clazz.declaredMethods.map { method ->
                mapOf<String, Any>(
                    "name" to method.name,
                    "returnType" to method.returnType.name,
                    "parameters" to method.parameters.map { param ->
                        mapOf<String, Any>(
                            "name" to param.name,
                            "type" to param.type.name
                        )
                    },
                    "modifiers" to Modifier.toString(method.modifiers).split(" ").filter { it.isNotEmpty() }
                )
            },
            "constructors" to clazz.declaredConstructors.map { constructor ->
                mapOf<String, Any>(
                    "parameters" to constructor.parameters.map { param ->
                        mapOf<String, Any>(
                            "name" to param.name,
                            "type" to param.type.name
                        )
                    },
                    "modifiers" to Modifier.toString(constructor.modifiers).split(" ").filter { it.isNotEmpty() }
                )
            },
            "source" to "reflection"
        )
    }

    private fun getClassType(psiClass: PsiClass): String = when {
        psiClass.isInterface -> "interface"
        psiClass.isEnum -> "enum"
        psiClass.isAnnotationType -> "annotation"
        else -> "class"
    }

    private fun getModifiers(psiClass: PsiClass): List<String> {
        val modifiers = mutableListOf<String>()
        psiClass.modifierList?.let { modifierList ->
            if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
            if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
            if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
            if (modifierList.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
            if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
            if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
        }
        return modifiers
    }

    private fun buildFieldInfo(field: PsiField): Map<String, Any> {
        return mapOf(
            "name" to field.name,
            "type" to TypeUtils.getTypeQualifiedName(field.type),
            "modifiers" to getModifiers(field)
        )
    }

    private fun buildMethodInfo(method: PsiMethod): Map<String, Any> {
        return mapOf(
            "name" to method.name,
            "returnType" to TypeUtils.getTypeQualifiedName(method.returnType),
            "parameters" to method.parameters.map { param ->
                mapOf(
                    "name" to param.name,
                    "type" to TypeUtils.getTypeQualifiedName(param.type)
                )
            },
            "modifiers" to getModifiers(method)
        )
    }

    private fun getModifiers(element: PsiModifierListOwner): List<String> {
        val modifiers = mutableListOf<String>()
        element.modifierList?.let { modifierList ->
            if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
            if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
            if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
            if (modifierList.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
            if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
            if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
        }
        return modifiers
    }

    private fun sendTextResponse(response: HttpServletResponse, classInfo: Map<String, Any>, className: String) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        val text = buildString {
            appendLine("类分析报告: $className")
            appendLine("=".repeat(50))
            appendLine("包名: ${classInfo["package"]}")
            appendLine("类型: ${classInfo["type"]}")
            appendLine("修饰符: ${(classInfo["modifiers"] as? List<*>)?.joinToString(" ") ?: ""}")
            appendLine("父类: ${classInfo["superClass"] ?: "无"}")
            appendLine("接口: ${(classInfo["interfaces"] as? List<*>)?.joinToString(", ") ?: "无"}")
            appendLine("数据源: ${classInfo["source"]}")
            appendLine()

            (classInfo["fields"] as? List<*>)?.let { fields ->
                if (fields.isNotEmpty()) {
                    appendLine("字段 (${fields.size}):")
                    fields.forEach { field ->
                        val fieldMap = field as Map<*, *>
                        appendLine("  ${fieldMap["modifiers"]} ${fieldMap["type"]} ${fieldMap["name"]}")
                    }
                    appendLine()
                }
            }

            (classInfo["methods"] as? List<*>)?.let { methods ->
                if (methods.isNotEmpty()) {
                    appendLine("方法 (${methods.size}):")
                    methods.forEach { method ->
                        val methodMap = method as Map<*, *>
                        val params = (methodMap["parameters"] as? List<*>)?.joinToString(", ") { param ->
                            val paramMap = param as Map<*, *>
                            "${paramMap["type"]} ${paramMap["name"]}"
                        } ?: ""
                        appendLine("  ${methodMap["modifiers"]} ${methodMap["returnType"]} ${methodMap["name"]}($params)")
                    }
                }
            }
        }

        val apiResponse = ApiResponse.success(text)
        sendJsonResponse(response, apiResponse)
    }

    private fun sendMultipleClassMatchesResponse(response: HttpServletResponse, classInfo: Map<String, Any>) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        val text = buildString {
            appendLine("找到多个同名类")
            appendLine("请使用以下全限定名之一重新查询：")
            appendLine()

            val matches = classInfo["matches"] as? List<*>
            matches?.forEach { match ->
                appendLine("- $match")
            }
            appendLine()
            appendLine("全限定名示例：使用 /api/class?class=com.example.MyClass&format=simple")
        }

        val successResponse = ApiResponse(
            success = true,
            data = text.trim()
        )
        sendJsonResponse(response, successResponse)
    }

    private fun sendMethodBodyTextResponse(response: HttpServletResponse, methodBodies: List<Map<String, Any>>) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        val text = buildString {
            appendLine("找到 ${methodBodies.size} 个匹配的方法:")
            appendLine()

            methodBodies.forEachIndexed { index, methodInfo ->
                appendLine("=== 方法 ${index + 1} ===")
                appendLine("类名: ${methodInfo["className"]}")
                appendLine("方法签名: ${methodInfo["signature"]}")
                
                if (methodInfo["fileName"] != null) {
                    val fileName = methodInfo["fileName"]
                    val startLine = methodInfo["startLine"]
                    val endLine = methodInfo["endLine"]
                    if (startLine != null && endLine != null) {
                        appendLine("文件: $fileName (行 $startLine-$endLine)")
                    } else {
                        appendLine("文件: $fileName")
                    }
                }
                
                appendLine()
                
                if (methodInfo["hasBody"] == true) {
                    appendLine("完整方法代码:")
                    val fullMethodText = methodInfo["fullMethodText"] as? String
                    if (fullMethodText != null) {
                        // 添加缩进使代码更清晰
                        fullMethodText.lines().forEach { line ->
                            appendLine(line)
                        }
                    }
                } else {
                    appendLine("注意: ${methodInfo["note"] ?: "该方法无方法体"}")
                }
                
                if (index < methodBodies.size - 1) {
                    appendLine()
                }
            }
        }

        val apiResponse = ApiResponse.success(text)
        sendJsonResponse(response, apiResponse)
    }

    private fun sendSimpleResponse(response: HttpServletResponse, classInfo: Map<String, Any>, className: String) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        val text = buildString {
            appendLine(className)

            // 输出字段
            (classInfo["fields"] as? List<*>)?.forEach { field ->
                val fieldMap = field as Map<*, *>
                val modifiers = (fieldMap["modifiers"] as? List<*>)?.joinToString(" ") ?: "".trim()
                val type = fieldMap["type"] ?: "unknown".trim()
                val name = fieldMap["name"] ?: "unknown".trim()
                appendLine("- $modifiers $type $name;")
            }

            // 输出方法
            (classInfo["methods"] as? List<*>)?.forEach { method ->
                val methodMap = method as Map<*, *>
                val modifiers = (methodMap["modifiers"] as? List<*>)?.joinToString(" ") ?: ""
                val returnType = methodMap["returnType"] ?: "void"
                val methodName = methodMap["name"] ?: "unknown"
                val params = (methodMap["parameters"] as? List<*>)?.joinToString(", ") { param ->
                    val paramMap = param as Map<*, *>
                    "${paramMap["type"]} ${paramMap["name"]}"
                } ?: ""
                appendLine("- $modifiers $returnType $methodName($params);")
            }
        }

        val apiResponse = ApiResponse.success(text)
        sendJsonResponse(response, apiResponse)
    }

    private fun getMethodBodies(className: String, methodName: String): List<Map<String, Any>> {
        // 如果是简单类名（不包含包名），先搜索所有匹配的类
        val classNames = if (!className.contains('.') || className.endsWith(".java")) {
            val searchName = if (className.endsWith(".java")) {
                className.removeSuffix(".java")
            } else {
                className
            }
            findClassesBySimpleName(searchName)
        } else {
            listOf(className)
        }

        val results = mutableListOf<Map<String, Any>>()

        for (fullClassName in classNames) {
            val methodBodies = getMethodBodiesFromClass(fullClassName, methodName)
            results.addAll(methodBodies)
        }

        return results
    }

    private fun getMethodBodiesFromClass(className: String, methodName: String): List<Map<String, Any>> {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return emptyList()

        val project = projects.first()
        return ReadAction.compute<List<Map<String, Any>>, Exception> {
            val results = mutableListOf<Map<String, Any>>()

            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)
            val psiClass = psiFacade.findClass(className, scope)

            if (psiClass != null) {
                // 查找所有匹配的方法（包括重载方法）
                val methods = psiClass.methods.filter { it.name == methodName }

                for (method in methods) {
                    val methodInfo = mutableMapOf<String, Any>(
                        "className" to className,
                        "methodName" to methodName,
                        "signature" to buildMethodSignature(method),
                        "modifiers" to getModifiers(method),
                        "returnType" to TypeUtils.getTypeQualifiedName(method.returnType),
                        "parameters" to method.parameters.map { param ->
                            mapOf(
                                "name" to param.name,
                                "type" to TypeUtils.getTypeQualifiedName(param.type)
                            )
                        }
                    )

                    // 获取方法体代码
                    val methodBody = method.body
                    if (methodBody != null) {
                        methodInfo["hasBody"] = true
                        methodInfo["bodyText"] = methodBody.text

                        // 获取完整的方法代码（包括方法声明）
                        methodInfo["fullMethodText"] = method.text

                        // 获取方法在文件中的位置信息
                        val containingFile = method.containingFile
                        if (containingFile != null) {
                            methodInfo["fileName"] = containingFile.name
                            methodInfo["filePath"] = containingFile.virtualFile?.path ?: ""

                            // 获取行号信息
                            val document =
                                com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(containingFile)
                            if (document != null) {
                                val startOffset = method.textRange.startOffset
                                val endOffset = method.textRange.endOffset
                                val startLine = document.getLineNumber(startOffset) + 1
                                val endLine = document.getLineNumber(endOffset) + 1

                                methodInfo["startLine"] = startLine
                                methodInfo["endLine"] = endLine
                            }
                        }
                    } else {
                        methodInfo["hasBody"] = false
                        methodInfo["bodyText"] = ""
                        methodInfo["fullMethodText"] = method.text
                        methodInfo["note"] = "抽象方法或接口方法，无方法体"
                    }

                    results.add(methodInfo)
                }
            }

            results
        } ?: emptyList()
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameters.joinToString(", ") { param ->
            "${TypeUtils.getTypeQualifiedName(param.type)} ${param.name}"
        }
        val modifiers = getModifiers(method).joinToString(" ")
        val returnType = TypeUtils.getTypeQualifiedName(method.returnType)

        return "$modifiers $returnType ${method.name}($params)"
    }
}