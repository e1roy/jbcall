package io.github.e1roy.jbcall

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class HttpServerStartupActivity : ProjectActivity {
    private val logger = Logger.getInstance(HttpServerStartupActivity::class.java)

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun execute(project: Project) {
        // 使用多种方式输出日志，确保可见性
        logger.warn("=== JBCall项目启动活动开始 ===")
        println("JBCall: 项目启动，准备启动HTTP服务器...")
        System.out.println("JBCall: 项目启动，准备启动HTTP服务器...")
        
        GlobalScope.launch {
            try {
                Thread.sleep(2000) // 等待2秒确保IDE完全启动

                logger.warn("JBCall插件启动，准备启动HTTP服务器...")
                println("JBCall: 开始启动HTTP服务器...")
                System.out.println("JBCall: 开始启动HTTP服务器...")

                // 获取HTTP服务器组件实例
                val serverComponent = HttpServerComponent.getInstance()

                // 启动服务器
                serverComponent.startServer()

                logger.warn("JBCall HTTP服务器启动成功，端口: ${serverComponent.getPort()}")
                println("JBCall: HTTP服务器启动成功，端口: ${serverComponent.getPort()}")
                System.out.println("JBCall: HTTP服务器启动成功，端口: ${serverComponent.getPort()}")

                // 显示通知
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("JBCall.Notifications")

                val notification = notificationGroup.createNotification(
                    "JBCall HTTP服务器",
                    "HTTP服务器已启动，端口: ${serverComponent.getPort()}<br>" +
                            "访问地址: <a href=\"${serverComponent.getServerUrl()}\">${serverComponent.getServerUrl()}</a>",
                    NotificationType.INFORMATION
                )

                notification.notify(project)

                logger.warn("HTTP服务器启动完成，通知已发送")
                println("JBCall: HTTP服务器启动完成")
                System.out.println("JBCall: HTTP服务器启动完成")

            } catch (e: Exception) {
                logger.error("启动HTTP服务器时发生错误", e)
                println("JBCall ERROR: 启动HTTP服务器失败 - ${e.message}")
                System.err.println("JBCall ERROR: 启动HTTP服务器失败 - ${e.message}")
                e.printStackTrace()

                // 显示错误通知
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("JBCall.Notifications")

                val errorNotification = notificationGroup.createNotification(
                    "JBCall HTTP服务器启动失败",
                    "错误信息: ${e.message}",
                    NotificationType.ERROR
                )

                errorNotification.notify(project)
            }
        }
    }
}