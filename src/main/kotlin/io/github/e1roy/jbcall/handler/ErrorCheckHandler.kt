package io.github.e1roy.jbcall.handler

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.github.e1roy.jbcall.model.ApiError
import io.github.e1roy.jbcall.model.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 处理类错误检查相关请求
 */
class ErrorCheckHandler : BaseHandler() {

    fun handleErrorCheck(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)

        val className = getParameter(request, "class")
        if (className == null) {
            sendError(response, "缺少class参数", 400)
            return
        }

        try {
            val errorText = checkClassErrors(className)
            if (errorText == null) {
                val errorResponse = ApiResponse(
                    success = false,
                    data = null as String?,
                    error = ApiError(
                        code = "CLASS_NOT_FOUND",
                        message = "未找到类: $className",
                        details = mapOf("className" to className)
                    )
                )
                response.status = 404
                sendJsonResponse(response, errorResponse)
                return
            }

            // 直接返回文本结果
            sendJsonResponse(response, ApiResponse.success(errorText))
        } catch (e: Exception) {
            sendError(response, "检查类错误失败: ${e.message}")
        }
    }

    private fun checkClassErrors(className: String): String? {
        // 解析类名，支持文件名输入
        val resolvedClasses = resolveClassName(className)

        if (resolvedClasses.isEmpty()) {
            return buildString {
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

        // 如果找到多个类，显示所有类的错误信息
        val result = StringBuilder()
        result.appendLine("类错误检查报告")
        result.appendLine("输入: $className")
        result.appendLine("=========================")
        result.appendLine()

        resolvedClasses.forEach { psiClass ->
            // 在ReadAction外获取类名
            val currentClassName = ReadAction.compute<String, Exception> {
                psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
            } ?: "Unknown"

            if (resolvedClasses.size > 1) {
                result.appendLine("【类: $currentClassName】")
            }

            val errorText = checkClassErrorsByPsiClass(psiClass)
            if (errorText != null) {
                result.appendLine(errorText)
            } else {
                result.appendLine("❌ 无法检查此类的错误")
            }
            result.appendLine()
        }

        return result.toString().trim()
    }

    private fun checkClassErrorsByPsiClass(psiClass: PsiClass): String? {
        val startTime = System.currentTimeMillis()
        
        // 先在ReadAction外获取基本信息
        val (className, project, containingFile, virtualFile) = ReadAction.compute<Tuple4<String, com.intellij.openapi.project.Project, PsiFile?, com.intellij.openapi.vfs.VirtualFile?>, Exception> {
            val className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
            val project = psiClass.project
            val containingFile = psiClass.containingFile
            val virtualFile = containingFile?.virtualFile
            Tuple4(className, project, containingFile, virtualFile)
        } ?: return "❌ 无法获取类信息"
        
        println("开始检查类错误: $className")

        // 检查是否处于 DumbMode（索引中）
        if (DumbService.isDumb(project)) {
            println("项目正在索引中，无法进行分析")
            return "项目正在索引中，请稍后重试"
        }

        if (containingFile == null || virtualFile == null) {
            return "❌ 无法获取文件信息"
        }

        // 检查文件是否已在编辑器中打开
        val fileEditorManager = FileEditorManager.getInstance(project)
        val isFileAlreadyOpen = fileEditorManager.isFileOpen(virtualFile)
        
        var fileOpened = false
        var analysisTriggered = false
        var analysisCompleted = true // 如果文件已打开，假设分析已完成
        
        if (!isFileAlreadyOpen) {
            println("文件未打开，需要打开并等待分析")
            
            // 在编辑器中打开文件
            val openTime = System.currentTimeMillis()
            fileOpened = openFileInEditor(project, virtualFile)
            println("文件打开耗时: ${System.currentTimeMillis() - openTime}ms, 结果: $fileOpened")

            if (fileOpened) {
                // 强制触发代码分析
                val triggerTime = System.currentTimeMillis()
                analysisTriggered = triggerCodeAnalysis(project, containingFile)
                println("触发分析耗时: ${System.currentTimeMillis() - triggerTime}ms, 结果: $analysisTriggered")

                // 等待分析完成（减少到5秒，优化性能）
                val waitTime = System.currentTimeMillis()
                analysisCompleted = waitForAnalysisCompletion(project, containingFile, 5000)
                println("等待分析耗时: ${System.currentTimeMillis() - waitTime}ms, 结果: $analysisCompleted")
            }
        } else {
            println("文件已在编辑器中打开，跳过打开和分析等待步骤")
        }

        // 使用 IntelliJ 内置的错误检测功能
        val analysisTime = System.currentTimeMillis()
        val errorResults = getIDEDetectedErrors(project, containingFile)
        println("IDE错误检测耗时: ${System.currentTimeMillis() - analysisTime}ms")

        // 生成报告
        val reportTime = System.currentTimeMillis()
        val result = buildString {
            appendLine("文件: ${containingFile.name}")
            appendLine("路径: ${virtualFile.path}")
            
            if (isFileAlreadyOpen) {
                appendLine("✅ 文件已在编辑器中打开")
            } else {
                if (fileOpened) {
                    appendLine("✅ 文件已成功打开")
                } else {
                    appendLine("❌ 文件打开失败")
                }
                
                if (!analysisTriggered) {
                    appendLine("⚠️ 无法触发代码分析")
                }
                if (!analysisCompleted) {
                    appendLine("⚠️ 分析可能未完成（超时）")
                }
            }
            appendLine()

            if (errorResults.errors.isEmpty()) {
                appendLine("✅ 没有发现编译错误")
            } else {
                appendLine("## 发现 ${errorResults.errors.size} 个错误:")
                var errorIndex = 0;
                errorResults.errors.forEach { error ->
                    errorIndex++
                    appendLine("❌错误${errorIndex} :  $error")
                }
            }
            
            appendLine()
            appendLine("检查完成，总耗时: ${System.currentTimeMillis() - startTime}ms")
        }.trim()

        println("生成报告耗时: ${System.currentTimeMillis() - reportTime}ms")
        println("总耗时: ${System.currentTimeMillis() - startTime}ms")

        return result
    }

    /**
     * 四元组数据类
     */
    private data class Tuple4<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    /**
     * 强制触发代码分析
     * @param project 项目实例
     * @param psiFile PSI文件
     * @return true 如果成功触发分析，false 如果失败
     */
    private fun triggerCodeAnalysis(project: com.intellij.openapi.project.Project, psiFile: PsiFile): Boolean {
        return try {
            // 获取DaemonCodeAnalyzer实例
            val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
            
            // 在EDT线程中触发分析
            ApplicationManager.getApplication().invokeLater {
                try {
                    // 重启文件的高亮分析
                    daemonCodeAnalyzer.restart(psiFile)
                    println("已触发文件分析: ${psiFile.name}")
                } catch (e: Exception) {
                    println("触发分析失败: ${e.message}")
                }
            }
            
            // 等待触发完成
            Thread.sleep(20)
            true
        } catch (e: Exception) {
            println("触发代码分析时出错: ${e.message}")
            false
        }
    }

    /**
     * 在编辑器中打开文件
     * @return true 如果文件成功打开，false 如果文件已经打开或打开失败
     */
    private fun openFileInEditor(project: com.intellij.openapi.project.Project, virtualFile: com.intellij.openapi.vfs.VirtualFile): Boolean {
        return try {
            val fileEditorManager = FileEditorManager.getInstance(project)
            
            // 使用invokeLater避免死锁
            ApplicationManager.getApplication().invokeLater {
                try {
                    fileEditorManager.openFile(virtualFile, true)
                    println("成功在编辑器中打开文件: ${virtualFile.path}")
                } catch (e: Exception) {
                    println("打开文件失败: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // 减少等待时间，提高性能
            Thread.sleep(30)
            val opened = fileEditorManager.isFileOpen(virtualFile)
            
            if (opened) {
                println("文件打开确认成功: ${virtualFile.path}")
            } else {
                println("文件打开确认失败: ${virtualFile.path}")
            }
            
            opened
        } catch (e: Exception) {
            println("打开文件时出错: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 等待IDE分析完成
     * @param project 项目实例
     * @param psiFile PSI文件
     * @param timeoutMs 超时时间（毫秒）
     * @return true 如果分析完成，false 如果超时
     */
    private fun waitForAnalysisCompletion(
        project: com.intellij.openapi.project.Project, 
        psiFile: PsiFile, 
        timeoutMs: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastHighlighterCount = -1
        var stableCount = 0
        
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // 检查是否还在索引中
                if (DumbService.isDumb(project)) {
                    println("项目仍在索引中，等待...")
                    Thread.sleep(30)
                    continue
                }
                
                // 检查文档是否已提交
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                if (document != null) {
                    val psiDocumentManager = PsiDocumentManager.getInstance(project)
                    if (!psiDocumentManager.isCommitted(document)) {
                        println("文档未提交，等待...")
                        Thread.sleep(20)
                        continue
                    }
                    
                    // 检查高亮标记数量是否稳定
                    val markupModel = DocumentMarkupModel.forDocument(document, project, true)
                    if (markupModel != null) {
                        val currentCount = markupModel.allHighlighters.size
                        println("当前高亮标记数量: $currentCount")
                        
                        if (currentCount == lastHighlighterCount) {
                            stableCount++
                            // 如果连续2次检查高亮数量都相同，认为分析完成（减少等待时间）
                            if (stableCount >= 2) {
                                println("高亮标记数量稳定，分析应该已完成")
                                return true
                            }
                        } else {
                            stableCount = 0
                            lastHighlighterCount = currentCount
                        }
                    }
                }
                
                Thread.sleep(30) // 减少检查频率，提高性能
            }
            
            println("等待分析完成超时")
            return false
        } catch (e: Exception) {
            println("等待分析完成时出错: ${e.message}")
            return false
        }
    }

    /**
     * 使用 IntelliJ IDE 内置的错误检测功能获取错误信息
     * 基于 MarkupModelEx 获取 IDE 已经检测到的问题
     */
    private fun getIDEDetectedErrors(
        project: com.intellij.openapi.project.Project,
        psiFile: PsiFile
    ): IDEAnalysisResult {
        val errors = mutableListOf<String>()

        try {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            if (document == null) {
                errors.add("无法获取文档信息")
                return IDEAnalysisResult(errors)
            }

            // 获取 DocumentMarkupModel，这是 IDE 存储高亮信息的地方
            val markupModel = DocumentMarkupModel.forDocument(document, project, true)

            if (markupModel == null) {
                errors.add("无法获取标记模型")
                return IDEAnalysisResult(errors)
            }

            // 获取所有的 RangeHighlighter
            val allHighlighters = markupModel.allHighlighters

            println("找到 ${allHighlighters.size} 个高亮标记")
            var errorCount = 0
            var warningCount = 0
            var infoCount = 0

            for (highlighter in allHighlighters) {
                try {
                    // 检查是否是错误相关的高亮
                    val errorStripeTooltip = highlighter.errorStripeTooltip

                    if (errorStripeTooltip != null && errorStripeTooltip is HighlightInfo) {
                        val severity = errorStripeTooltip.severity
                        
                        when {
                            severity == HighlightSeverity.ERROR -> {
                                errorCount++
                                val lineNumber = document.getLineNumber(highlighter.startOffset) + 1
                                val description = errorStripeTooltip.description ?: "未知错误"
                                
                                // 优化错误信息格式
                                val cleanDescription = cleanErrorDescription(description)
                                
                                // 获取错误代码上下文
                                val codeContext = getCodeContext(document, lineNumber, 3)
                                val errorInfo = buildString {
                                    appendLine("第 $lineNumber 行: $cleanDescription")
                                    if (codeContext.isNotEmpty()) {
                                        appendLine("代码上下文:")
                                        codeContext.forEach { (num, code) ->
                                            val marker = if (num == lineNumber) ">>> " else "    "
                                            appendLine("$marker$num: $code")
                                        }
                                    }
                                }
                                
                                errors.add(errorInfo.trim())
                                println("发现ERROR: 第 $lineNumber 行 - $cleanDescription")
                            }
                            severity == HighlightSeverity.WARNING -> {
                                warningCount++
                            }
                            else -> {
                                infoCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("处理高亮标记时出错: ${e.message}")
                }
            }

            println("统计: ERROR=$errorCount, WARNING=$warningCount, INFO=$infoCount")

            // 如果没有找到任何错误，尝试检查基本的语法错误
            if (errors.isEmpty()) {
                println("未找到ERROR级别问题，检查基本语法错误")
                checkBasicSyntaxErrors(psiFile, errors)
            }

        } catch (e: Exception) {
            println("获取IDE错误信息时出错: ${e.message}")
            e.printStackTrace()
            errors.add("获取IDE错误信息时出错: ${e.message}")
        }

        return IDEAnalysisResult(errors)
    }

    /**
     * 获取指定行的代码上下文
     * @param document 文档对象
     * @param errorLine 错误行号（1-based）
     * @param contextLines 上下文行数
     * @return 包含行号和代码的列表
     */
    private fun getCodeContext(document: com.intellij.openapi.editor.Document, errorLine: Int, contextLines: Int): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        
        try {
            val totalLines = document.lineCount
            val startLine = maxOf(1, errorLine - contextLines)
            val endLine = minOf(totalLines, errorLine + contextLines)
            
            for (lineNum in startLine..endLine) {
                val lineStartOffset = document.getLineStartOffset(lineNum - 1)
                val lineEndOffset = document.getLineEndOffset(lineNum - 1)
                val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
                result.add(Pair(lineNum, lineText))
            }
        } catch (e: Exception) {
            println("获取代码上下文时出错: ${e.message}")
        }
        
        return result
    }

    /**
     * 清理错误描述信息，提取关键内容
     */
    private fun cleanErrorDescription(description: String): String {
        // 移除不必要的前缀和后缀
        var cleaned = description
        
        // 移除常见的前缀
        cleaned = cleaned.removePrefix("Error: ")
        cleaned = cleaned.removePrefix("错误: ")
        
        // 如果描述太长，截取主要部分
        if (cleaned.length > 200) {
            cleaned = cleaned.take(200) + "..."
        }
        
        return cleaned
    }

    
    /**
     * 检查基本的语法错误作为备用方案
     */
    private fun checkBasicSyntaxErrors(psiFile: PsiFile, errors: MutableList<String>) {
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
        
        psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                super.visitErrorElement(element)
                val message = element.errorDescription ?: "语法错误"
                val line = getLineNumber(psiFile, element.textRange.startOffset)
                
                // 获取错误代码上下文
                val errorInfo = if (document != null) {
                    val codeContext = getCodeContext(document, line, 3)
                    buildString {
                        appendLine("第 $line 行: $message")
                        if (codeContext.isNotEmpty()) {
                            appendLine("代码上下文:")
                            codeContext.forEach { (num, code) ->
                                val marker = if (num == line) ">>> " else "    "
                                appendLine("$marker$num: $code")
                            }
                        }
                    }.trim()
                } else {
                    "第 $line 行: $message"
                }
                
                errors.add(errorInfo)
            }
        })
    }

    /**
     * IDE 分析结果数据类（简化版，只包含错误）
     */
    private data class IDEAnalysisResult(
        val errors: List<String>
    )

    /**
     * 获取元素在文件中的行号
     */
    private fun getLineNumber(psiFile: PsiFile, offset: Int): Int {
        return try {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
            if (document != null && offset >= 0 && offset < document.textLength) {
                document.getLineNumber(offset) + 1
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 解析类名，支持完整类名和文件名输入
     */
    private fun resolveClassName(input: String): List<PsiClass> {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return emptyList()

        val project = projects.first()

        return ReadAction.compute<List<PsiClass>, Exception> {
            val classes = mutableListOf<PsiClass>()
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)

            // 1. 首先尝试作为完整类名查找
            val directClass = psiFacade.findClass(input, scope)
            if (directClass != null) {
                classes.add(directClass)
                return@compute classes
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

            classes.distinct()
        } ?: emptyList()
    }
}