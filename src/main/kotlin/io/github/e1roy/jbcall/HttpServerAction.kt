package io.github.e1roy.jbcall

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import java.awt.Desktop
import java.net.URI

class HttpServerAction : AnAction() {
    private val logger = Logger.getInstance(HttpServerAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val serverComponent = HttpServerComponent.getInstance()
        
        val isRunning = serverComponent.isRunning()
        val port = serverComponent.getPort()
        val url = serverComponent.getServerUrl()
        
        val status = if (isRunning) "运行中" else "已停止"
        val message = """
            HTTP服务器状态: $status
            端口: $port
            地址: $url
            
            ${if (isRunning) "服务器正在运行，您可以:" else "服务器已停止，您可以:"}
        """.trimIndent()
        
        val options = if (isRunning) {
            arrayOf("打开浏览器", "重启服务器", "停止服务器", "取消")
        } else {
            arrayOf("启动服务器", "取消")
        }
        
        val choice = Messages.showDialog(
            project,
            message,
            "JBCall HTTP服务器状态",
            options,
            0,
            Messages.getInformationIcon()
        )
        
        try {
            when {
                isRunning -> {
                    when (choice) {
                        0 -> openBrowser(url, project) // 打开浏览器
                        1 -> restartServer(project) // 重启服务器
                        2 -> stopServer(project) // 停止服务器
                        // 3 -> 取消，不做任何操作
                    }
                }
                else -> {
                    when (choice) {
                        0 -> startServer(project) // 启动服务器
                        // 1 -> 取消，不做任何操作
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("执行HTTP服务器操作时发生错误", ex)
            showErrorNotification(project, "操作失败: ${ex.message}")
        }
    }
    
    private fun openBrowser(url: String, project: com.intellij.openapi.project.Project) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
                showInfoNotification(project, "已在默认浏览器中打开: $url")
            } else {
                showErrorNotification(project, "无法打开浏览器，请手动访问: $url")
            }
        } catch (e: Exception) {
            logger.error("打开浏览器失败", e)
            showErrorNotification(project, "打开浏览器失败: ${e.message}")
        }
    }
    
    private fun startServer(project: com.intellij.openapi.project.Project) {
        val serverComponent = HttpServerComponent.getInstance()
        serverComponent.startServer()
        showInfoNotification(project, "HTTP服务器已启动，地址: ${serverComponent.getServerUrl()}")
    }
    
    private fun stopServer(project: com.intellij.openapi.project.Project) {
        val serverComponent = HttpServerComponent.getInstance()
        serverComponent.stopServer()
        showInfoNotification(project, "HTTP服务器已停止")
    }
    
    private fun restartServer(project: com.intellij.openapi.project.Project) {
        val serverComponent = HttpServerComponent.getInstance()
        serverComponent.stopServer()
        Thread.sleep(1000) // 等待1秒确保服务器完全停止
        serverComponent.startServer()
        showInfoNotification(project, "HTTP服务器已重启，地址: ${serverComponent.getServerUrl()}")
    }
    
    private fun showInfoNotification(project: com.intellij.openapi.project.Project, message: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("JBCall.Notifications")
        
        val notification = notificationGroup.createNotification(
            "JBCall HTTP服务器",
            message,
            NotificationType.INFORMATION
        )
        
        notification.notify(project)
    }
    
    private fun showErrorNotification(project: com.intellij.openapi.project.Project, message: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("JBCall.Notifications")
        
        val notification = notificationGroup.createNotification(
            "JBCall HTTP服务器错误",
            message,
            NotificationType.ERROR
        )
        
        notification.notify(project)
    }
}