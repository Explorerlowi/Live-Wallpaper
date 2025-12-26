package com.example.livewallpaper.core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * HttpClient 工厂
 * 提供统一配置的 Ktor HttpClient 实例
 */
object HttpClientFactory {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    /**
     * 创建 HttpClient 实例
     * @param enableLogging 是否启用日志（仅 debug 模式开启）
     */
    fun create(enableLogging: Boolean = false): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            
            if (enableLogging) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.BODY
                }
            }
            
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000 // 图片生成可能需要较长时间，设置为600秒
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 600_000
            }
            
            defaultRequest {
                headers.append("Content-Type", "application/json")
            }
        }
    }
}
