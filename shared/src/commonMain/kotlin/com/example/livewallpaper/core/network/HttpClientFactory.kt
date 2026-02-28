package com.example.livewallpaper.core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * 平台日志输出，Android 走 Logcat，iOS 走 NSLog
 */
expect fun platformLog(tag: String, message: String)

/**
 * HttpClient 工厂
 * 提供统一配置的 Ktor HttpClient 实例
 */
object HttpClientFactory {

    /**
     * 将日志中超长的 base64 数据替换为摘要，避免 Logcat 被刷屏。
     * 纯字符串操作，不用正则，处理 MB 级响应也不会卡。
     */
    private fun truncateBase64(message: String): String {
        if (message.length < 200) return message

        val markers = listOf(
            "\"data\":\"", "\"data\": \"",
            "\"thoughtSignature\":\"", "\"thoughtSignature\": \""
        )
        val sb = StringBuilder(minOf(message.length, 8192))
        var cursor = 0

        while (cursor < message.length) {
            var bestIdx = -1
            var bestMarker = ""
            for (marker in markers) {
                val idx = message.indexOf(marker, cursor)
                if (idx != -1 && (bestIdx == -1 || idx < bestIdx)) {
                    bestIdx = idx
                    bestMarker = marker
                }
            }
            if (bestIdx == -1) {
                sb.append(message, cursor, message.length)
                break
            }
            val dataStart = bestIdx + bestMarker.length
            val endQuote = message.indexOf('"', dataStart)
            if (endQuote != -1 && endQuote - dataStart > 200) {
                sb.append(message, cursor, dataStart)
                sb.append("[TRUNCATED ${endQuote - dataStart} chars]")
                cursor = endQuote
            } else {
                sb.append(message, cursor, dataStart)
                cursor = dataStart
            }
        }
        return sb.toString()
    }

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
                    logger = object : Logger {
                        override fun log(message: String) {
                            platformLog("KtorHttp", truncateBase64(message))
                        }
                    }
                    level = LogLevel.ALL
                }
            }
            
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 600_000
            }

            install(HttpRequestRetry) {
                retryOnException(maxRetries = 1, retryOnTimeout = false)
                delayMillis { retry -> retry * 3000L }
                modifyRequest { request ->
                    platformLog("KtorHttp", "Retry #$retryCount: ${request.url}")
                }
            }

            defaultRequest {
                headers.append("Content-Type", "application/json")
            }
        }
    }
}
