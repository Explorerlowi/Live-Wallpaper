package com.example.livewallpaper.feature.aipaint.data.remote

import com.example.livewallpaper.core.error.AppError
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.domain.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * GPT Image API 服务
 *
 * 支持 OpenAI 兼容的图片生成和编辑接口：
 * - 无参考图时调用 `/v1/images/generations`（生成）
 * - 有参考图时调用 `/v1/images/edits`（编辑）
 *
 * 支持 gpt-image-2 模型：
 * - 尺寸：任意分辨率（需满足约束），常用 1024x1024、1536x1024、2048x2048、3840x2160 等
 * - 质量：low / medium / high / auto
 * - 输出格式：png（默认）/ jpeg（更快）/ webp
 * - 压缩：jpeg/webp 支持 0-100% 压缩级别
 * - 不支持透明背景
 *
 * 响应中图片数据以 base64 编码返回（`b64_json` 字段）。
 */
class GptApiService(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成图片（无参考图）
     *
     * 调用 POST /v1/images/generations
     * 返回原始 [HttpResponse]，由调用方使用处理器提取 base64 图片数据。
     */
    suspend fun generateImage(
        profile: ApiProfile,
        prompt: String,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat = GptOutputFormat.PNG
    ): AppResult<HttpResponse> {
        return try {
            val endpoint = "${profile.baseUrl}/v1/images/generations"
            val requestBody = buildGenerateRequest(prompt, size, quality, outputFormat)

            val response = httpClient.post(endpoint) {
                header("Authorization", "Bearer ${profile.token}")
                contentType(ContentType.Application.Json)
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
     * 编辑图片（有参考图）
     *
     * 调用 POST /v1/images/edits
     * 使用 multipart/form-data 格式上传图片和参数。
     * 返回原始 [HttpResponse]。
     */
    suspend fun editImage(
        profile: ApiProfile,
        prompt: String,
        images: List<PaintImage>,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat = GptOutputFormat.PNG
    ): AppResult<HttpResponse> {
        return try {
            val endpoint = "${profile.baseUrl}/v1/images/edits"

            val response = httpClient.post(endpoint) {
                header("Authorization", "Bearer ${profile.token}")
                setBody(MultiPartFormDataContent(formData {
                    append("model", "gpt-image-2")
                    append("prompt", prompt)
                    append("size", size.value)
                    append("quality", quality.value)
                    append("output_format", outputFormat.value)

                    // 上传参考图片
                    images.forEachIndexed { index, img ->
                        val imageBytes = img.base64Data?.let { data ->
                            try {
                                decodeBase64(data)
                            } catch (_: Exception) {
                                null
                            }
                        } ?: return@forEachIndexed

                        val extension = when {
                            img.mimeType.contains("jpeg") || img.mimeType.contains("jpg") -> "jpg"
                            img.mimeType.contains("webp") -> "webp"
                            else -> "png"
                        }
                        val fileName = "image_$index.$extension"

                        append("image", imageBytes, Headers.build {
                            append(HttpHeaders.ContentType, img.mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                }))
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

    /**
     * 构建图片生成请求体
     */
    private fun buildGenerateRequest(
        prompt: String,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat
    ): JsonObject {
        return buildJsonObject {
            put("model", "gpt-image-2")
            put("prompt", prompt)
            put("n", 1)
            put("size", size.value)
            put("quality", quality.value)
            put("output_format", outputFormat.value)
        }
    }

    /**
     * 纯 Kotlin base64 解码（commonMain 兼容）
     */
    private fun decodeBase64(input: String): ByteArray {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val lookup = IntArray(256) { -1 }
        for (i in table.indices) lookup[table[i].code] = i

        val clean = input.filter { it != '\n' && it != '\r' && it != ' ' }
        val padding = clean.count { it == '=' }
        val outputLen = clean.length * 3 / 4 - padding
        val output = ByteArray(outputLen)

        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val a = lookup[clean[i].code and 0xFF]
            val b = if (i + 1 < clean.length) lookup[clean[i + 1].code and 0xFF] else 0
            val c = if (i + 2 < clean.length) lookup[clean[i + 2].code and 0xFF] else 0
            val d = if (i + 3 < clean.length) lookup[clean[i + 3].code and 0xFF] else 0

            val triple = (a shl 18) or (b shl 12) or (c shl 6) or d

            if (outIdx < outputLen) output[outIdx++] = (triple shr 16 and 0xFF).toByte()
            if (outIdx < outputLen) output[outIdx++] = (triple shr 8 and 0xFF).toByte()
            if (outIdx < outputLen) output[outIdx++] = (triple and 0xFF).toByte()

            i += 4
        }
        return output
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
