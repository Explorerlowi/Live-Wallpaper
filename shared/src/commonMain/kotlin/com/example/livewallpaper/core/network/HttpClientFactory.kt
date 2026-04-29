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
     * 将日志中超长的 base64 / 二进制数据替换为摘要，避免 Logcat 被刷屏。
     * 纯字符串操作，不用正则，处理 MB 级响应也不会卡。
     *
     * 处理三类场景：
     * 1. JSON 中的 base64 字段（Gemini 的 "data"、"thoughtSignature"，GPT 的 "b64_json"）
     * 2. Multipart 请求体中的大段二进制数据（Ktor 日志直接输出原始字节）
     * 3. 其他任何超长日志消息
     */
    private fun truncateBase64(message: String): String {
        if (message.length < 200) return message

        // 第一步：截断 JSON 中已知的 base64 字段
        val markers = listOf(
            "\"data\":\"", "\"data\": \"",
            "\"thoughtSignature\":\"", "\"thoughtSignature\": \"",
            "\"b64_json\":\"", "\"b64_json\": \""
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

        val result = sb.toString()

        // 第二步：兜底——如果经过标记截断后仍然超长，说明包含无法用标记匹配的
        // 二进制数据（如 multipart 请求体中的原始图片字节）。
        // 正常的请求头 + URL + 文本 body 不会超过此阈值。
        val maxLogLen = 4000
        if (result.length > maxLogLen) {
            return result.take(maxLogLen) + "... [TRUNCATED total ${result.length} chars]"
        }

        return result
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
