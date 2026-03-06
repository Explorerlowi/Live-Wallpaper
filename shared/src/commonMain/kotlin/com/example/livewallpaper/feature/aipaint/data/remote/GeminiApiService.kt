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
     *
     * 成功时返回原始 [HttpResponse]，由调用方使用 [ImageResponseProcessor] 流式处理响应体。
     * 这样做是为了避免 bodyAsText() 将完整响应（含大量 base64 图片数据）一次性加载到内存，
     * 防止在高分辨率 / 多图场景下 OOM 崩溃。
     */
    suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): AppResult<HttpResponse> {
        return try {
            val endpoint = "${profile.baseUrl}/v1beta/models/${model.endpoint}:generateContent"
            val requestBody = buildGenerateImageRequest(prompt, images, model, aspectRatio, resolution)
            
            val response = httpClient.post(endpoint) {
                applyAuth(profile)
                setBody(requestBody.toString())
            }
            
            val responseCode = response.status.value
            if (responseCode !in 200..299) {
                val errorBody = response.bodyAsText()
                AppResult.Error(AppError.Server(responseCode, errorBody))
            } else {
                AppResult.Success(response)
            }
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
