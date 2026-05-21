package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

class DesktopImageResponseProcessor : ImageResponseProcessor {
    override suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String
    ): List<GeneratedImageFile> = processImageResponse(
        response = response,
        sessionId = sessionId,
        messageId = messageId,
        tempPrefix = "gemini_resp_",
        extension = "png",
        markers = listOf("\"data\":\"", "\"data\": \"")
    )
}

class DesktopGptImageResponseProcessor : GptImageResponseProcessor {
    override suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String,
        outputFormat: GptOutputFormat
    ): List<GeneratedImageFile> {
        val extension = when (outputFormat) {
            GptOutputFormat.JPEG -> "jpg"
            GptOutputFormat.WEBP -> "webp"
            GptOutputFormat.PNG -> "png"
        }
        return processImageResponse(
            response = response,
            sessionId = sessionId,
            messageId = messageId,
            tempPrefix = "gpt_resp_",
            extension = extension,
            markers = listOf("\"b64_json\":\"", "\"b64_json\": \"")
        )
    }
}

private suspend fun processImageResponse(
    response: HttpResponse,
    sessionId: String,
    messageId: String,
    tempPrefix: String,
    extension: String,
    markers: List<String>
): List<GeneratedImageFile> = withContext(Dispatchers.IO) {
    val outputDir = File(desktopAiPaintRoot(), sessionId).apply { mkdirs() }
    val tempFile = File.createTempFile(tempPrefix, ".tmp", desktopAiPaintCacheRoot())
    try {
        streamResponseToFile(response, tempFile)
        extractAndSaveImages(tempFile, outputDir, messageId, extension, markers)
    } finally {
        tempFile.delete()
    }
}

private fun desktopAiPaintRoot(): File =
    DesktopAiPaintStoragePaths.generatedImagesDirectory()

private fun desktopAiPaintCacheRoot(): File =
    DesktopAiPaintStoragePaths.responseCacheDirectory()

private suspend fun streamResponseToFile(response: HttpResponse, file: File) {
    withTimeout(STREAM_TIMEOUT_MS) {
        val channel = response.bodyAsChannel()
        file.outputStream().buffered(BUFFER_SIZE).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead <= 0) break
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}

private fun extractAndSaveImages(
    responseFile: File,
    outputDir: File,
    messageId: String,
    extension: String,
    markers: List<String>
): List<GeneratedImageFile> {
    val markerBytes = markers.map { it.toByteArray(Charsets.US_ASCII) }
    val maxMarkerLength = markerBytes.maxOf { it.size }
    val results = mutableListOf<GeneratedImageFile>()
    var imageIndex = 0
    var state = STATE_SEARCHING
    val markerBuffer = ByteArray(maxMarkerLength)
    var markerLength = 0
    val base64Buffer = ByteArray(BASE64_CHUNK_SIZE + 4)
    var base64Position = 0
    var fileOut: BufferedOutputStream? = null
    var currentFile: File? = null
    val decoder = Base64.getMimeDecoder()

    responseFile.inputStream().buffered(BUFFER_SIZE).use { input ->
        val readBuffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val bytesRead = input.read(readBuffer)
            if (bytesRead == -1) break
            for (i in 0 until bytesRead) {
                val byte = readBuffer[i]
                when (state) {
                    STATE_SEARCHING -> {
                        if (markerLength >= maxMarkerLength) {
                            System.arraycopy(markerBuffer, 1, markerBuffer, 0, maxMarkerLength - 1)
                            markerBuffer[maxMarkerLength - 1] = byte
                        } else {
                            markerBuffer[markerLength++] = byte
                        }
                        if (markerBytes.any { marker -> tailEquals(markerBuffer, markerLength, marker) }) {
                            state = STATE_IN_DATA
                            val fileName = if (imageIndex == 0) {
                                "$messageId.$extension"
                            } else {
                                "${messageId}_$imageIndex.$extension"
                            }
                            currentFile = File(outputDir, fileName)
                            fileOut = currentFile.outputStream().buffered(BUFFER_SIZE)
                            markerLength = 0
                            base64Position = 0
                        }
                    }

                    STATE_IN_DATA -> {
                        if (byte == QUOTE) {
                            try {
                                if (base64Position > 0) {
                                    fileOut!!.write(decoder.decode(base64Buffer.copyOf(base64Position)))
                                }
                                fileOut!!.close()
                            } catch (_: Exception) {
                                runCatching { fileOut?.close() }
                                currentFile?.delete()
                                currentFile = null
                            }
                            fileOut = null
                            currentFile?.let { file ->
                                val info = readImageDimensions(file)
                                if (info != null) {
                                    results += info
                                } else {
                                    file.delete()
                                }
                            }
                            currentFile = null
                            imageIndex++
                            state = STATE_SEARCHING
                        } else {
                            base64Buffer[base64Position++] = byte
                            if (base64Position >= BASE64_CHUNK_SIZE) {
                                val flushLength = (base64Position / 4) * 4
                                try {
                                    fileOut!!.write(decoder.decode(base64Buffer.copyOf(flushLength)))
                                    System.arraycopy(base64Buffer, flushLength, base64Buffer, 0, base64Position - flushLength)
                                    base64Position -= flushLength
                                } catch (_: Exception) {
                                    runCatching { fileOut?.close() }
                                    currentFile?.delete()
                                    fileOut = null
                                    currentFile = null
                                    state = STATE_SKIP_DATA
                                }
                            }
                        }
                    }

                    STATE_SKIP_DATA -> {
                        if (byte == QUOTE) {
                            imageIndex++
                            state = STATE_SEARCHING
                        }
                    }
                }
            }
        }
    }

    runCatching { fileOut?.close() }
    return results
}

private fun tailEquals(buffer: ByteArray, bufferLength: Int, marker: ByteArray): Boolean {
    if (bufferLength < marker.size) return false
    val offset = bufferLength - marker.size
    for (i in marker.indices) {
        if (buffer[offset + i] != marker[i]) return false
    }
    return true
}

private fun readImageDimensions(file: File): GeneratedImageFile? {
    val image = ImageIO.read(file) ?: return null
    return GeneratedImageFile(file.absolutePath, image.width, image.height)
}

private const val BUFFER_SIZE = 65_536
private const val BASE64_CHUNK_SIZE = 65_536
private const val STREAM_TIMEOUT_MS = 600_000L
private const val STATE_SEARCHING = 0
private const val STATE_IN_DATA = 1
private const val STATE_SKIP_DATA = 2
private const val QUOTE: Byte = '"'.code.toByte()
