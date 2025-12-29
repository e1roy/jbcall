package io.github.e1roy.jbcall.handler

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.github.e1roy.jbcall.model.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 处理项目相关请求
 */
class ProjectHandler : BaseHandler() {
    
    fun handleProjectInfo(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        try {
            val projects = ProjectManager.getInstance().openProjects
            if (projects.isEmpty()) {
                sendError(response, "没有打开的项目", 404)
                return
            }
            
            val project = projects.first()
            val projectData = ReadAction.compute<Map<String, Any>, Exception> {
                mapOf<String, Any>(
                    "name" to project.name,
                    "basePath" to (project.basePath ?: ""),
                    "isOpen" to !project.isDisposed,
                    "modules" to getModuleInfo(project)
                )
            }
            
            sendJsonResponse(response, ApiResponse.success(projectData))
        } catch (e: Exception) {
            sendError(response, "获取项目信息失败: ${e.message}")
        }
    }
    
    fun handleProjectClasses(request: HttpServletRequest, response: HttpServletResponse) {
        enableCors(response)
        
        try {
            val projects = ProjectManager.getInstance().openProjects
            if (projects.isEmpty()) {
                sendError(response, "没有打开的项目", 404)
                return
            }
            
            val project = projects.first()
            val classes = ReadAction.compute<List<Map<String, String>>, Exception> {
                getProjectClasses(project)
            }
            
            sendJsonResponse(response, ApiResponse.success(mapOf("classes" to classes)))
        } catch (e: Exception) {
            sendError(response, "获取项目类列表失败: ${e.message}")
        }
    }
    
    private fun getModuleInfo(project: Project): List<Map<String, Any>> {
        val projectRootManager = ProjectRootManager.getInstance(project)
        return projectRootManager.contentRoots.map { root ->
            mapOf<String, Any>(
                "name" to root.name,
                "path" to root.path,
                "isDirectory" to root.isDirectory
            )
        }
    }
    
    private fun getProjectClasses(project: Project): List<Map<String, String>> {
        return try {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)
            val shortNamesCache = PsiShortNamesCache.getInstance(project)
            val classes = mutableListOf<Map<String, String>>()
            
            shortNamesCache.allClassNames.forEach { className ->
                val psiClasses = shortNamesCache.getClassesByName(className, scope)
                psiClasses.forEach { psiClass ->
                    if (psiClass.qualifiedName != null) {
                        classes.add(mapOf<String, String>(
                            "name" to (psiClass.qualifiedName ?: "Unknown"),
                            "package" to (psiClass.qualifiedName?.substringBeforeLast('.') ?: ""),
                            "type" to getClassType(psiClass)
                        ))
                    }
                }
            }
            
            classes.sortedBy { it["name"] }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getClassType(psiClass: PsiClass): String = when {
        psiClass.isInterface -> "interface"
        psiClass.isEnum -> "enum"
        psiClass.isAnnotationType -> "annotation"
        else -> "class"
    }
}