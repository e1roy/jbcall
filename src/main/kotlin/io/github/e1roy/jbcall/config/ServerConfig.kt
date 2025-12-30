package io.github.e1roy.jbcall.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * 服务器配置管理
 */
@Service
class ServerConfig {
    companion object {
        fun getInstance(): ServerConfig = service()
    }
    
    val port: Int = 8080
    val host: String = "127.0.0.1"
    val enableCors: Boolean = true
    val maxRequestSize: Long = 10 * 1024 * 1024 // 10MB
    
    fun getServerUrl(): String = "http://$host:$port"
}