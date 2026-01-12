package com.example.livewallpaper.feature.aipaint.data.remote

import com.example.livewallpaper.core.error.AppError
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.data.remote.dto.*
import com.example.livewallpaper.feature.aipaint.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*

/**
 * Gemini API 服务
 * 负责与 Gemini API 进行网络通信
 */
class GeminiApiService(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成图片
     */
    suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): AppResult<String> {
        return try {
            val endpoint = "${profile.baseUrl}/v1beta/models/${model.endpoint}:generateContent"
            val requestBody = buildGenerateImageRequest(prompt, images, model, aspectRatio, resolution)
            
            val response = httpClient.post(endpoint) {
                applyAuth(profile)
                setBody(requestBody.toString())
            }
            
            parseGenerateImageResponse(response)
        } catch (e: Exception) {
            mapException(e)
        }
    }

    /**
     * 优化提示词
     */
    suspend fun enhancePrompt(
        profile: ApiProfile,
        prompt: String
    ): AppResult<String> {
        return try {
            val endpoint = "${profile.baseUrl}/v1beta/models/gemini-2.5-flash:generateContent"
            val requestBody = buildEnhancePromptRequest(prompt)
            
            val response = httpClient.post(endpoint) {
                applyAuth(profile)
                setBody(requestBody.toString())
            }
            
            parseEnhancePromptResponse(response)
        } catch (e: Exception) {
            mapException(e)
        }
    }


    // ========== 私有方法 ==========

    private fun HttpRequestBuilder.applyAuth(profile: ApiProfile) {
        when (profile.authMode) {
            AuthMode.BEARER -> header("Authorization", "Bearer ${profile.token}")
            AuthMode.OFFICIAL -> header("x-goog-api-key", profile.token)
        }
    }

    private fun buildGenerateImageRequest(
        prompt: String,
        images: List<PaintImage>,
        model: PaintModel,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): JsonObject {
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", prompt) })
            images.forEach { img ->
                img.base64Data?.let { data ->
                    add(buildJsonObject {
                        put("inlineData", buildJsonObject {
                            put("mimeType", img.mimeType)
                            put("data", data)
                        })
                    })
                }
            }
        }

        val imageConfig = buildJsonObject {
            put("aspectRatio", aspectRatio.value)
            if (model == PaintModel.GEMINI_3_PRO) {
                put("imageSize", resolution.value)
            }
        }

        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", parts)
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add("IMAGE") })
                put("imageConfig", imageConfig)
            })
        }
    }

    private fun buildEnhancePromptRequest(prompt: String): JsonObject {
        val systemPrompt = "You are an expert AI image generation prompt engineer. Output ONLY the enhanced prompt text in English."

        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "Enhance this description: $prompt") })
                    })
                })
            })
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemPrompt) })
                })
            })
        }
    }

    /**
     * 流式解析图片响应，避免一次性加载整个响应体导致 OOM
     * 通过逐块读取并查找 Base64 数据来降低内存占用
     */
    private suspend fun parseGenerateImageResponse(response: HttpResponse): AppResult<String> {
        val responseCode = response.status.value

        if (responseCode !in 200..299) {
            // 错误响应通常较小，可以直接读取
            val errorBody = response.bodyAsText()
            return AppResult.Error(AppError.Server(responseCode, errorBody))
        }

        return try {
            // 使用流式方式读取响应体
            val channel: ByteReadChannel = response.bodyAsChannel()
            val base64Data = extractBase64FromStream(channel)
            
            if (base64Data != null) {
                AppResult.Success(base64Data)
            } else {
                AppResult.Error(AppError.Server(responseCode, "未返回图片数据"))
            }
        } catch (e: Exception) {
            AppResult.Error(AppError.Unknown(e))
        }
    }

    /**
     * 从流中提取 Base64 图片数据
     * 使用状态机方式逐步解析，避免将整个响应加载到内存
     */
    private suspend fun extractBase64FromStream(channel: ByteReadChannel): String? {
        val buffer = StringBuilder()
        val chunkSize = 8192 // 8KB 块
        val byteArray = ByteArray(chunkSize)
        
        // 查找 "data" 字段的标记
        val dataMarkers = listOf("\"data\":\"", "\"data\": \"")
        var foundDataStart = false
        var base64Builder: StringBuilder? = null
        
        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(byteArray, 0, chunkSize)
            if (bytesRead <= 0) break
            
            val chunk = byteArray.decodeToString(0, bytesRead)
            buffer.append(chunk)
            
            if (!foundDataStart) {
                // 查找 data 字段开始位置
                for (marker in dataMarkers) {
                    val markerIndex = buffer.indexOf(marker)
                    if (markerIndex != -1) {
                        foundDataStart = true
                        val dataStartIndex = markerIndex + marker.length
                        base64Builder = StringBuilder()
                        
                        // 提取 marker 之后的内容
                        val remaining = buffer.substring(dataStartIndex)
                        val endQuoteIndex = remaining.indexOf('"')
                        
                        if (endQuoteIndex != -1) {
                            // 在当前块中找到了结束引号
                            return remaining.substring(0, endQuoteIndex)
                        } else {
                            // 继续累积 Base64 数据
                            base64Builder.append(remaining)
                            buffer.clear()
                        }
                        break
                    }
                }
                
                // 保留最后一部分以防标记被分割
                if (!foundDataStart && buffer.length > 20) {
                    val keepFrom = buffer.length - 20
                    val kept = buffer.substring(keepFrom)
                    buffer.clear()
                    buffer.append(kept)
                }
            } else {
                // 已找到 data 开始，继续累积直到找到结束引号
                base64Builder?.let { builder ->
                    val endQuoteIndex = chunk.indexOf('"')
                    if (endQuoteIndex != -1) {
                        builder.append(chunk.substring(0, endQuoteIndex))
                        return builder.toString()
                    } else {
                        builder.append(chunk)
                    }
                }
            }
        }
        
        return base64Builder?.toString()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun parseEnhancePromptResponse(response: HttpResponse): AppResult<String> {
        val responseCode = response.status.value
        val responseBody = response.bodyAsText()

        if (responseCode !in 200..299) {
            return AppResult.Error(AppError.Server(responseCode, "API错误"))
        }

        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        val candidates = jsonResponse["candidates"]?.jsonArray
        val content = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray

        val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
        return if (text != null) {
            AppResult.Success(text)
        } else {
            AppResult.Error(AppError.Server(responseCode, "未返回优化结果"))
        }
    }

    private fun mapException(e: Exception): AppResult<Nothing> {
        return when {
            e.message?.contains("timeout", ignoreCase = true) == true -> 
                AppResult.Error(AppError.Network)
            e.message?.contains("connect", ignoreCase = true) == true -> 
                AppResult.Error(AppError.Network)
            else -> AppResult.Error(AppError.Unknown(e))
        }
    }
}
