package io.github.e1roy.jbcall.handler

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.github.e1roy.jbcall.model.ApiResponse
import io.github.e1roy.jbcall.model.ApiError
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
                return buildString {
                    appendLine("找到多个同名类，请使用全限定名指定具体的类：")
                    appendLine()
                    matchingClasses.forEach { match ->
                        appendLine("- $match")
                    }
                    appendLine()
                    appendLine("建议：请使用以下全限定名之一重新查询：${matchingClasses.joinToString(", ")}")
                }
            } else {
                // 只有一个匹配，直接检查
                return checkClassErrorsByFullName(matchingClasses.first())
            }
        } else {
            // 全限定名，直接检查
            return checkClassErrorsByFullName(className)
        }
    }

    private fun checkClassErrorsByFullName(className: String): String? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return null

        val project = projects.first()
        return ReadAction.compute<String?, Exception> {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.allScope(project)
            val psiClass = psiFacade.findClass(className, scope) ?: return@compute null

            val containingFile = psiClass.containingFile ?: return@compute null
            val virtualFile = containingFile.virtualFile ?: return@compute null

            // 获取文档
            val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                ?: return@compute null

            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            try {
                // 方法1: 直接检查语法错误
                containingFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                    override fun visitErrorElement(element: PsiErrorElement) {
                        super.visitErrorElement(element)

                        val startLine = document.getLineNumber(element.textRange.startOffset) + 1
                        val message = element.errorDescription ?: "语法错误"
                        val problemText = element.text.ifEmpty { "<位置错误>" }
                        
                        // 获取该行代码上下文（前后3行）
                        val contextLines = getContextLines(document, startLine - 1, 3)
                        val contextInfo = if (contextLines.isNotEmpty()) {
                            "\n    上下文:\n" + contextLines.mapIndexed { index, line ->
                                val lineNum = startLine - 3 + index
                                val marker = if (lineNum == startLine) ">>> " else "    "
                                "    $marker$lineNum: $line"
                            }.joinToString("\n")
                        } else ""
                        
                        errors.add("第 $startLine 行: $message (问题代码: '$problemText')$contextInfo")
                    }
                })

                // 方法2: 使用 DaemonCodeAnalyzer 获取高亮信息
                try {
                    val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
                    daemonCodeAnalyzer.restart(containingFile)
                    Thread.sleep(500) // 减少等待时间，提高响应速度

                    val highlightInfos = if (daemonCodeAnalyzer is DaemonCodeAnalyzerImpl) {
                        try {
                            daemonCodeAnalyzer.getFileLevelHighlights(project, containingFile)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    highlightInfos.forEach { highlightInfo ->
                        val severity = highlightInfo.severity
                        val startLine = document.getLineNumber(highlightInfo.startOffset) + 1
                        val message = highlightInfo.description ?: "未知问题"
                        val problemText = document.getText(
                            com.intellij.openapi.util.TextRange(highlightInfo.startOffset, highlightInfo.endOffset)
                        ).take(50) // 限制长度

                        // 获取该行代码上下文（前后3行）
                        val contextLines = getContextLines(document, startLine - 1, 3)
                        val contextInfo = if (contextLines.isNotEmpty()) {
                            "\n    上下文:\n" + contextLines.mapIndexed { index, line ->
                                val lineNum = startLine - 3 + index + 1
                                val marker = if (lineNum == startLine) ">>> " else "    "
                                "    $marker$lineNum: $line"
                            }.joinToString("\n")
                        } else ""
                        
                        val errorMsg = "第 $startLine 行: $message" + 
                            if (problemText.isNotEmpty()) " (问题代码: '$problemText')" else "" +
                            contextInfo


                        when {
                            severity == HighlightSeverity.ERROR -> {
                                // 避免重复添加相同的错误
                                if (!errors.any { it.contains("第 $startLine 行") }) {
                                    errors.add(errorMsg)
                                }
                            }
                            severity == HighlightSeverity.WARNING || severity == HighlightSeverity.WEAK_WARNING -> {
                                warnings.add(errorMsg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略高亮信息获取失败
                }

            } catch (e: Exception) {
                return@compute "检查过程中发生错误: ${e.message}"
            }

            // 生成简洁的文本报告
            buildString {
                appendLine("类错误检查报告: $className")
                appendLine("文件: ${containingFile.name}")
                appendLine("路径: ${virtualFile.path}")
                appendLine()

                if (errors.isEmpty() && warnings.isEmpty()) {
                    appendLine("✅ 没有发现编译错误或警告")
                } else {
                    if (errors.isNotEmpty()) {
                        appendLine("❌ 发现 ${errors.size} 个错误:")
                        errors.forEach { error ->
                            appendLine("  - $error")
                        }
                        appendLine()
                    }

                    if (warnings.isNotEmpty()) {
                        appendLine("⚠️ 发现 ${warnings.size} 个警告:")
                        warnings.forEach { warning ->
                            appendLine("  - $warning")
                        }
                    }
                }
            }.trim()
        }
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

            // 方法2: 使用 PsiShortNamesCache 搜索
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

    /**
     * 获取指定行的上下文代码行
     * @param document 文档对象
     * @param centerLine 中心行号（0-based）
     * @param contextSize 上下文行数
     * @return 上下文代码行列表
     */
    private fun getContextLines(document: Document, centerLine: Int, contextSize: Int): List<String> {
        val totalLines = document.lineCount
        val startLine = maxOf(0, centerLine - contextSize)
        val endLine = minOf(totalLines - 1, centerLine + contextSize)
        
        return (startLine..endLine).mapNotNull { lineNum ->
            try {
                val lineStart = document.getLineStartOffset(lineNum)
                val lineEnd = document.getLineEndOffset(lineNum)
                document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            } catch (e: Exception) {
                null
            }
        }
    }
}