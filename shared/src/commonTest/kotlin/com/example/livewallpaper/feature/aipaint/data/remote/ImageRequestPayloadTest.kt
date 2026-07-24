package com.example.livewallpaper.feature.aipaint.data.remote

import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies image bytes exist only while the remote layer constructs HTTP requests. */
class ImageRequestPayloadTest {
    @Test
    fun geminiEncodesRequestBytesAsBase64Json() = runBlocking {
        var body = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    body = request.body.readRequestBytes().decodeToString()
                    respond("{}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
            }
        }

        val result = GeminiApiService(client).generateImage(
            profile = profile(),
            model = PaintModel.GEMINI_2_5_FLASH,
            prompt = "test",
            images = listOf(ImageRequestPayload("hello".encodeToByteArray(), "image/png")),
            aspectRatio = AspectRatio.RATIO_1_1,
            resolution = Resolution.RES_1K,
        )

        assertIs<AppResult.Success<*>>(result)
        assertTrue(body.contains("\"data\":\"aGVsbG8=\""))
        client.close()
    }

    @Test
    fun gptMultipartCarriesRawBytes() = runBlocking {
        var body = ByteArray(0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    body = request.body.readRequestBytes()
                    respond("{}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
            }
        }
        val expected = byteArrayOf(0, 1, 2, 3, 127)

        val result = GptApiService(client).editImage(
            profile = profile(),
            prompt = "test",
            images = listOf(ImageRequestPayload(expected, "image/png")),
            size = GptImageSize.AUTO,
            quality = GptImageQuality.AUTO,
            outputFormat = GptOutputFormat.PNG,
        )

        assertIs<AppResult.Success<*>>(result)
        val payloadOffset = body.indexOfSubArray(expected)
        assertTrue(payloadOffset >= 0)
        assertContentEquals(expected, body.copyOfRange(payloadOffset, payloadOffset + expected.size))
        client.close()
    }

    private fun profile(): ApiProfile = ApiProfile(
        id = "profile",
        name = "Test",
        baseUrl = "https://example.test",
        token = "secret",
    )

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        return indices.firstOrNull { start ->
            start + needle.size <= size && needle.indices.all { index -> this[start + index] == needle[index] }
        } ?: -1
    }

    private suspend fun OutgoingContent.readRequestBytes(): ByteArray = when (this) {
        is OutgoingContent.ByteArrayContent -> bytes()
        is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readByteArray()
        is OutgoingContent.WriteChannelContent -> coroutineScope {
            val channel = ByteChannel(autoFlush = true)
            launch {
                runCatching { writeTo(channel) }
                channel.close()
            }
            channel.readRemaining().readByteArray()
        }
        else -> error("Unsupported test request body: ${this::class.simpleName}")
    }
}
