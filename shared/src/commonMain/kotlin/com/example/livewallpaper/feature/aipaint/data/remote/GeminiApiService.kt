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
        images: List<ImageRequestPayload>,
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

    // ========== 私有方法 ==========

    private fun HttpRequestBuilder.applyAuth(profile: ApiProfile) {
        when (profile.authMode) {
            AuthMode.BEARER -> header("Authorization", "Bearer ${profile.token}")
            AuthMode.OFFICIAL -> header("x-goog-api-key", profile.token)
        }
    }

    private fun buildGenerateImageRequest(
        prompt: String,
        images: List<ImageRequestPayload>,
        model: PaintModel,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): JsonObject {
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", prompt) })
            images.forEach { image ->
                add(buildJsonObject {
                    put("inlineData", buildJsonObject {
                        put("mimeType", image.mimeType)
                        put("data", encodeBase64(image.bytes))
                    })
                })
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

    private fun encodeBase64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        return buildString((bytes.size + 2) / 3 * 4) {
            var index = 0
            while (index < bytes.size) {
                val first = bytes[index].toInt() and 0xFF
                val second = bytes.getOrNull(index + 1)?.toInt()?.and(0xFF)
                val third = bytes.getOrNull(index + 2)?.toInt()?.and(0xFF)
                append(alphabet[first shr 2])
                append(alphabet[((first and 0x03) shl 4) or ((second ?: 0) shr 4)])
                append(if (second == null) '=' else alphabet[((second and 0x0F) shl 2) or ((third ?: 0) shr 6)])
                append(if (third == null) '=' else alphabet[third and 0x3F])
                index += 3
            }
        }
    }

    private fun mapException(e: Exception): AppResult<Nothing> {
        // CancellationException 必须重新抛出，不能被吞掉
        if (e is kotlinx.coroutines.CancellationException) throw e
        return when {
            e.message?.contains("timeout", ignoreCase = true) == true -> 
                AppResult.Error(AppError.Timeout)
            e.message?.contains("connect", ignoreCase = true) == true -> 
                AppResult.Error(AppError.Network)
            else -> AppResult.Error(AppError.Unknown(e))
        }
    }
}
