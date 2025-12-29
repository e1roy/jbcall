package io.github.e1roy.jbcall

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.github.e1roy.jbcall.config.ServerConfig
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.InetSocketAddress

/**
 * HTTP服务器组件 - 管理Jetty服务器生命周期
 */
@Service(Service.Level.APP)
class HttpServerComponent {
    private val logger = Logger.getInstance(HttpServerComponent::class.java)
    private var server: Server? = null
    private val config = ServerConfig.getInstance()
    
    companion object {
        fun getInstance(): HttpServerComponent {
            return ApplicationManager.getApplication().getService(HttpServerComponent::class.java)
        }
    }
    
    fun startServer() {
        if (server?.isStarted == true) {
            logger.info("HTTP服务器已在运行，端口: ${config.port}")
            return
        }
        
        try {
            logger.info("启动HTTP服务器，端口: ${config.port}")
            
            server = Server(InetSocketAddress(config.host, config.port))
            
            val context = ServletContextHandler(ServletContextHandler.SESSIONS)
            context.contextPath = "/"
            
            // 添加API处理器
            context.addServlet(ServletHolder(ApiServlet()), "/api/*")
            // 添加静态文件处理器
            context.addServlet(ServletHolder(StaticFileServlet()), "/")
            
            server?.handler = context
            server?.start()
            
            logger.info("HTTP服务器启动成功: ${config.getServerUrl()}")
            
        } catch (e: Exception) {
            logger.error("启动HTTP服务器失败", e)
            throw e
        }
    }
    
    fun stopServer() {
        try {
            server?.stop()
            server = null
            logger.info("HTTP服务器已停止")
        } catch (e: Exception) {
            logger.error("停止HTTP服务器失败", e)
        }
    }
    
    fun restartServer() {
        logger.info("重启HTTP服务器")
        stopServer()
        startServer()
    }
    
    fun isRunning(): Boolean = server?.isStarted == true
    
    fun getPort(): Int = config.port
    
    fun getServerUrl(): String = config.getServerUrl()
}