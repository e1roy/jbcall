package io.github.e1roy.jbcall.handler

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import io.github.e1roy.jbcall.model.ApiResponse
import io.github.e1roy.jbcall.model.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 处理方法调用链查询相关请求
 */
class CallChainHandler : BaseHandler() {

    fun handleCallChain(request: HttpServletRequest, response: HttpServletResponse) {
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
            val callChainText = getMethodCallChain(className, methodName)
            if (callChainText == null) {
                val errorResponse = ApiResponse(
                    success = false,
                    data = null as String?,
                    error = ApiError(
                        code = "METHOD_NOT_FOUND",
                        message = "未找到方法: $className.$methodName",
                        details = mapOf("className" to className, "methodName" to methodName)
                    )
                )
                response.status = 404
                sendJsonResponse(response, errorResponse)
                return
            }

            // 直接返回文本结果
            sendJsonResponse(response, ApiResponse.success(callChainText))
        } catch (e: Exception) {
            sendError(response, "查询调用链失败: ${e.message}")
        }
    }

    private fun getMethodCallChain(className: String, methodName: String): String? {
        val startTime = System.currentTimeMillis()
        println("开始查询调用链: $className.$methodName")
        
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) {
            println("没有打开的项目，耗时: ${System.currentTimeMillis() - startTime}ms")
            return null
        }

        val project = projects.first()
        
        // 检查是否处于 DumbMode（索引中）
        if (DumbService.isDumb(project)) {
            println("项目正在索引中，无法进行分析")
            return "项目正在索引中，请稍后重试"
        }
        
        return ReadAction.compute<String?, Exception> {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)
            
            // 尝试解析类名，支持文件名输入
            val resolvedClasses = resolveClassName(className, psiFacade, scope, project)
            
            if (resolvedClasses.isEmpty()) {
                println("未找到类: $className")
                return@compute buildString {
                    appendLine("❌ 未找到类: $className")
                    appendLine()
                    appendLine("提示:")
                    appendLine("• 如果输入的是文件名，请确保文件存在于项目中")
                    appendLine("• 如果输入的是完整类名，请检查包路径是否正确")
                    appendLine("• 支持的输入格式:")
                    appendLine("  - 完整类名: com.example.TestMain")
                    appendLine("  - 文件名: TestMain.java 或 TestMain")
                }
            }
            
            // 处理多个匹配的类
            val result = StringBuilder()
            result.appendLine("方法调用链查询结果")
            result.appendLine("输入: $className.$methodName")
            
            if (resolvedClasses.size > 1) {
                result.appendLine("找到多个匹配的类:")
                resolvedClasses.forEach { psiClass ->
                    result.appendLine("  - ${psiClass.qualifiedName ?: psiClass.name}")
                }
                result.appendLine()
            }
            
            result.appendLine("=========================")
            result.appendLine()
            
            var foundAnyMethod = false
            
            resolvedClasses.forEach { psiClass ->
                val currentClassName = psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
                
                if (resolvedClasses.size > 1) {
                    result.appendLine("【类: $currentClassName】")
                }
                
                // 查找指定的方法
                val targetMethods = psiClass.findMethodsByName(methodName, false)
                if (targetMethods.isEmpty()) {
                    result.appendLine("❌ 在类 $currentClassName 中未找到方法: $methodName")
                    result.appendLine()
                } else {
                    foundAnyMethod = true
                    
                    // 如果有多个重载方法，处理所有的
                    targetMethods.forEachIndexed { index, method ->
                        if (targetMethods.size > 1) {
                            result.appendLine("【方法重载 ${index + 1}】")
                            result.appendLine("方法签名: ${getMethodSignature(method)}")
                            result.appendLine()
                        }
                        
                        val callers = findMethodCallers(method, project)
                        if (callers.isEmpty()) {
                            result.appendLine("❌ 没有找到调用此方法的地方")
                        } else {
                            result.appendLine("✅ 找到 ${callers.size} 个调用点:")
                            callers.forEachIndexed { callerIndex, caller ->
                                result.appendLine("  ${callerIndex + 1}. ${caller}")
                            }
                        }
                        result.appendLine()
                    }
                }
            }
            
            if (!foundAnyMethod) {
                result.appendLine("提示:")
                result.appendLine("• 请检查方法名是否正确")
                result.appendLine("• 方法名区分大小写")
            }
            
            result.appendLine("查询完成，耗时: ${System.currentTimeMillis() - startTime}ms")
            result.toString().trim()
        }
    }

    /**
     * 解析类名，支持完整类名和文件名输入
     */
    private fun resolveClassName(input: String, psiFacade: JavaPsiFacade, scope: GlobalSearchScope, project: com.intellij.openapi.project.Project): List<PsiClass> {
        val classes = mutableListOf<PsiClass>()
        
        // 1. 首先尝试作为完整类名查找
        val directClass = psiFacade.findClass(input, scope)
        if (directClass != null) {
            classes.add(directClass)
            return classes
        }
        
        // 2. 如果没找到，尝试作为文件名处理
        val simpleClassName = input.removeSuffix(".java").removeSuffix(".kt")
        
        // 3. 在项目中搜索所有匹配的类名
        val allClasses = psiFacade.findClasses(simpleClassName, scope)
        classes.addAll(allClasses)
        
        // 4. 如果还是没找到，尝试模糊匹配（类名包含输入的字符串）
        if (classes.isEmpty()) {
            val projectScope = GlobalSearchScope.projectScope(project)
            val shortName = PsiShortNamesCache.getInstance(project)
            val matchingClassNames = shortName.allClassNames.filter { 
                it.contains(simpleClassName, ignoreCase = true) 
            }
            
            matchingClassNames.forEach { className ->
                val matchedClasses = shortName.getClassesByName(className, projectScope)
                classes.addAll(matchedClasses)
            }
        }
        
        return classes.distinct()
    }

    /**
     * 查找调用指定方法的地方（一层调用）
     */
    private fun findMethodCallers(method: PsiMethod, project: com.intellij.openapi.project.Project): List<String> {
        val callers = mutableListOf<String>()
        
        try {
            val references = ReferencesSearch.search(method, GlobalSearchScope.projectScope(project)).findAll()
            
            for (reference in references) {
                val element = reference.element
                val callerMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                val callerClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                
                if (callerMethod != null && callerClass != null) {
                    val callerInfo = buildString {
                        append("${callerClass.qualifiedName ?: callerClass.name}.${callerMethod.name}")
                        
                        // 获取调用位置的行号
                        val containingFile = element.containingFile
                        if (containingFile != null) {
                            val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                            if (document != null) {
                                val lineNumber = document.getLineNumber(element.textOffset) + 1
                                append(" (${containingFile.name}:$lineNumber)")
                            }
                        }
                        
                        // 获取调用的上下文代码
                        val callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java)
                        if (callExpression != null) {
                            val callText = callExpression.text.take(100) // 限制长度
                            append(" -> $callText")
                        }
                    }
                    
                    callers.add(callerInfo)
                } else {
                    // 如果不在方法内，可能是字段初始化或其他地方
                    val containingFile = element.containingFile
                    if (containingFile != null && callerClass != null) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                        val lineNumber = if (document != null) {
                            document.getLineNumber(element.textOffset) + 1
                        } else {
                            "?"
                        }
                        
                        callers.add("${callerClass.qualifiedName ?: callerClass.name} (${containingFile.name}:$lineNumber) -> 非方法调用")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("查找调用者时出错: ${e.message}")
            callers.add("查找调用者时出错: ${e.message}")
        }
        
        return callers.distinct() // 去重
    }

    /**
     * 获取方法签名
     */
    private fun getMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "$returnType ${method.name}($params)"
    }
}