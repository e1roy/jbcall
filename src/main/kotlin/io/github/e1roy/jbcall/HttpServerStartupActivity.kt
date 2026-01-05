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
        // 插件启动时不再自动启动HTTP服务器
        // 用户需要通过底部工具栏的按钮手动启动
        logger.info("=== JBCall插件已启动 ===")
        println("JBCall: 插件已启动，HTTP服务器需要手动启动")
        System.out.println("JBCall: 插件已启动，请使用底部工具栏按钮启动HTTP服务器")
        
        // 显示启动通知
        GlobalScope.launch {
            try {
                Thread.sleep(1000) // 等待1秒确保IDE完全启动
                
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("JBCall.Notifications")

                val notification = notificationGroup.createNotification(
                    "JBCall插件已启动",
                    "HTTP服务器已就绪，请点击底部状态栏的'HTTP:OFF'按钮启动服务器",
                    NotificationType.INFORMATION
                )

                notification.notify(project)
                
            } catch (e: Exception) {
                logger.error("显示启动通知时发生错误", e)
            }
        }
    }
}