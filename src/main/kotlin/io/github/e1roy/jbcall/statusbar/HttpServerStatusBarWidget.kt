package io.github.e1roy.jbcall.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import io.github.e1roy.jbcall.HttpServerComponent
import java.awt.event.MouseEvent
import javax.swing.Timer

/**
 * HTTP服务器状态栏组件
 */
class HttpServerStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    
    companion object {
        const val WIDGET_ID = "JBCall.HttpServerStatus"
        private val logger = Logger.getInstance(HttpServerStatusBarWidget::class.java)
    }
    
    private val serverComponent = HttpServerComponent.getInstance()
    private var statusTimer: Timer? = null
    private var myStatusBar: StatusBar? = null
    
    override fun ID(): String = WIDGET_ID
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    
    override fun getText(): String {
        return if (serverComponent.isRunning()) {
            "JbCall:ON"
        } else {
            "JbCall:OFF"
        }
    }
    
    override fun getAlignment(): Float = 0.5f
    
    override fun getTooltipText(): String {
        return if (serverComponent.isRunning()) {
            "HTTP服务器运行中 (${serverComponent.getServerUrl()}) - 点击停止"
        } else {
            "HTTP服务器已停止 - 点击启动"
        }
    }
    
    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { toggleServer() }
    }
    
    private fun toggleServer() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (serverComponent.isRunning()) {
                    serverComponent.stopServer()
                    logger.info("用户通过状态栏停止HTTP服务器")
                } else {
                    serverComponent.startServer()
                    logger.info("用户通过状态栏启动HTTP服务器")
                }
                
                // 更新状态栏显示
                ApplicationManager.getApplication().invokeLater {
                    myStatusBar?.updateWidget(ID())
                }
                
            } catch (e: Exception) {
                logger.error("切换HTTP服务器状态失败", e)
            }
        }
    }
    
    override fun install(statusBar: StatusBar) {
        myStatusBar = statusBar
        startStatusTimer()
    }
    
    override fun dispose() {
        stopStatusTimer()
        myStatusBar = null
    }
    
    private fun startStatusTimer() {
        // 每3秒更新一次状态
        statusTimer = Timer(3000) {
            ApplicationManager.getApplication().invokeLater {
                myStatusBar?.updateWidget(ID())
            }
        }
        statusTimer?.start()
    }
    
    private fun stopStatusTimer() {
        statusTimer?.stop()
        statusTimer = null
    }
}

/**
 * HTTP服务器状态栏组件工厂
 */
class HttpServerStatusBarWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId(): String = HttpServerStatusBarWidget.WIDGET_ID
    
    override fun getDisplayName(): String = "HTTP Server Status"
    
    override fun isAvailable(project: Project): Boolean = true
    
    override fun createWidget(project: Project): StatusBarWidget {
        return HttpServerStatusBarWidget(project)
    }
    
    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}