package com.example.livewallpaper.feature.aipaint.data.remote

import com.example.livewallpaper.core.error.AppError
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.data.remote.dto.*
import com.example.livewallpaper.feature.aipaint.domain.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
    ): AppResult<List<String>> {
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
            if (model.supportsResolution) {
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
     * 解析图片生成响应
     *
     * 使用 bodyAsText() 一次性读取完整响应，而非 bodyAsChannel() 流式读取。
     * 流式读取 (ByteReadChannel) 在 app 退到后台时，通道可能无法正确感知响应结束，
     * 导致 readAvailable() 永久挂起，直到 socket 超时（600 秒）才抛出异常。
     * 一次性读取由 Ktor/OkHttp 在传输层完成缓冲，不受 app 前后台状态影响。
     */
    private suspend fun parseGenerateImageResponse(response: HttpResponse): AppResult<List<String>> {
        val responseCode = response.status.value

        if (responseCode !in 200..299) {
            val errorBody = response.bodyAsText()
            return AppResult.Error(AppError.Server(responseCode, errorBody))
        }

        return try {
            val responseBody = response.bodyAsText()
            val base64DataList = extractAllBase64FromText(responseBody)

            if (base64DataList.isNotEmpty()) {
                AppResult.Success(base64DataList)
            } else {
                AppResult.Error(AppError.Server(responseCode, "未返回图片数据"))
            }
        } catch (e: Exception) {
            AppResult.Error(AppError.Unknown(e))
        }
    }

    /**
     * 从完整响应文本中提取所有 Base64 图片数据
     * 扫描 JSON 中的 "data":"..." 字段，支持提取多张图片
     */
    private fun extractAllBase64FromText(text: String): List<String> {
        val result = mutableListOf<String>()
        val markers = listOf("\"data\":\"", "\"data\": \"")
        var searchFrom = 0

        while (searchFrom < text.length) {
            var markerEnd = -1
            for (marker in markers) {
                val idx = text.indexOf(marker, searchFrom)
                if (idx != -1) {
                    markerEnd = idx + marker.length
                    break
                }
            }
            if (markerEnd == -1) break

            val endQuote = text.indexOf('"', markerEnd)
            if (endQuote == -1) break

            val base64Data = text.substring(markerEnd, endQuote)
            if (base64Data.isNotEmpty()) {
                result.add(base64Data)
            }
            searchFrom = endQuote + 1
        }

        return result
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
