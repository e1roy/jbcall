package io.github.e1roy.jbcall

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.InetSocketAddress

@Service(Service.Level.APP)
class HttpServerComponent {
    private val logger = Logger.getInstance(HttpServerComponent::class.java)
    private var server: Server? = null
    private val port = 8080
    
    companion object {
        fun getInstance(): HttpServerComponent {
            return ApplicationManager.getApplication().getService(HttpServerComponent::class.java)
        }
    }
    
    fun startServer() {
        if (server?.isStarted == true) {
            logger.warn("HTTP服务器已经在运行中，端口: $port")
            println("JBCall: HTTP服务器已经在运行中，端口: $port")
            return
        }
        
        try {
            logger.warn("开始启动HTTP服务器...")
            println("JBCall: 开始启动HTTP服务器...")
            
            server = Server(InetSocketAddress("localhost", port))
            
            // 创建Servlet上下文处理器
            val context = ServletContextHandler(ServletContextHandler.SESSIONS)
            context.contextPath = "/"
            
            // 首先添加API Servlet (更具体的路径匹配)
            val apiServlet = ServletHolder(ApiServlet())
            context.addServlet(apiServlet, "/api/*")
            
            // 然后添加静态文件Servlet (作为默认处理器)
            val staticServlet = ServletHolder(StaticFileServlet())
            context.addServlet(staticServlet, "/")
            
            server?.handler = context
            server?.start()
            
            logger.warn("HTTP服务器启动成功，端口: $port")
            logger.warn("访问地址: http://localhost:$port")
            println("JBCall: HTTP服务器启动成功，端口: $port")
            println("JBCall: 访问地址: http://localhost:$port")
            
        } catch (e: Exception) {
            logger.error("启动HTTP服务器失败", e)
            println("JBCall ERROR: 启动HTTP服务器失败 - ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun stopServer() {
        try {
            server?.stop()
            server = null
            logger.warn("HTTP服务器已停止")
            println("JBCall: HTTP服务器已停止")
        } catch (e: Exception) {
            logger.error("停止HTTP服务器失败", e)
            println("JBCall ERROR: 停止HTTP服务器失败 - ${e.message}")
        }
    }
    
    fun isRunning(): Boolean {
        return server?.isStarted == true
    }
    
    fun getPort(): Int = port
    
    fun getServerUrl(): String = "http://localhost:$port"
}