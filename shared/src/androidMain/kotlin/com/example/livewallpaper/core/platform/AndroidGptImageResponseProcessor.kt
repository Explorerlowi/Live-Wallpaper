package com.example.livewallpaper.core.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
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

/**
 * Android 端 GPT Image 响应流式处理器
 *
 * GPT Image API 返回的 JSON 格式：
 * ```json
 * {
 *   "data": [
 *     { "b64_json": "iVBORw0KGgo..." },
 *     { "b64_json": "..." }
 *   ]
 * }
 * ```
 *
 * 处理流程：
 * 1. 将 HTTP 响应体通过 ByteReadChannel 流式写入临时文件
 * 2. 对临时文件逐字节扫描，定位 JSON 中的 "b64_json":"..." 字段
 * 3. 对每段 base64 数据按 64KB 分块解码，解码后直接写入目标 PNG 文件
 * 4. 删除临时文件，返回图片文件信息
 *
 * 峰值内存占用：~200KB（读缓冲 + base64 块 + 解码输出），与图片大小无关
 */
class AndroidGptImageResponseProcessor(
    private val context: Context
) : GptImageResponseProcessor {

    override suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String,
        outputFormat: GptOutputFormat
    ): List<GeneratedImageFile> = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "aipaint/$sessionId").apply { mkdirs() }
        val tempFile = File.createTempFile("gpt_resp_", ".tmp", context.cacheDir)
        val extension = when (outputFormat) {
            GptOutputFormat.JPEG -> "jpg"
            GptOutputFormat.WEBP -> "webp"
            GptOutputFormat.PNG -> "png"
        }

        try {
            streamResponseToFile(response, tempFile)
            extractAndSaveImages(tempFile, outputDir, messageId, extension)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 将响应体流式写入临时文件
     */
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

    /**
     * 流式解析临时文件，提取 base64 图片数据并保存
     *
     * 状态机逻辑与 Gemini 处理器类似，但匹配的标记为 "b64_json":"
     */
    private fun extractAndSaveImages(
        responseFile: File,
        outputDir: File,
        messageId: String,
        extension: String
    ): List<GeneratedImageFile> {
        val results = mutableListOf<GeneratedImageFile>()
        var imageIndex = 0

        var state = STATE_SEARCHING
        val markerBuf = ByteArray(MAX_MARKER_LEN)
        var markerBufLen = 0
        val base64Buf = ByteArray(BASE64_CHUNK_SIZE + 4)
        var base64Pos = 0
        var fileOut: BufferedOutputStream? = null
        var currentFile: File? = null

        responseFile.inputStream().buffered(BUFFER_SIZE).use { input ->
            val readBuf = ByteArray(BUFFER_SIZE)

            while (true) {
                val bytesRead = input.read(readBuf)
                if (bytesRead == -1) break

                for (i in 0 until bytesRead) {
                    val b = readBuf[i]

                    when (state) {
                        STATE_SEARCHING -> {
                            if (markerBufLen >= MAX_MARKER_LEN) {
                                System.arraycopy(markerBuf, 1, markerBuf, 0, MAX_MARKER_LEN - 1)
                                markerBuf[MAX_MARKER_LEN - 1] = b
                            } else {
                                markerBuf[markerBufLen++] = b
                            }

                            for (marker in MARKERS) {
                                if (markerBufLen >= marker.size && tailEquals(markerBuf, markerBufLen, marker)) {
                                    state = STATE_IN_DATA
                                    val fn = if (imageIndex == 0) "$messageId.$extension"
                                             else "${messageId}_$imageIndex.$extension"
                                    val file = File(outputDir, fn)
                                    currentFile = file
                                    fileOut = file.outputStream().buffered(BUFFER_SIZE)
                                    base64Pos = 0
                                    markerBufLen = 0
                                    break
                                }
                            }
                        }

                        STATE_IN_DATA -> {
                            if (b == QUOTE) {
                                try {
                                    if (base64Pos > 0) {
                                        val decoded = Base64.decode(base64Buf, 0, base64Pos, Base64.DEFAULT)
                                        fileOut!!.write(decoded)
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
                                        results.add(info)
                                    } else {
                                        file.delete()
                                    }
                                }

                                imageIndex++
                                state = STATE_SEARCHING
                                currentFile = null
                            } else {
                                base64Buf[base64Pos++] = b
                                if (base64Pos >= BASE64_CHUNK_SIZE) {
                                    val flushLen = (base64Pos / 4) * 4
                                    if (flushLen > 0) {
                                        try {
                                            val decoded = Base64.decode(base64Buf, 0, flushLen, Base64.DEFAULT)
                                            fileOut!!.write(decoded)
                                            System.arraycopy(base64Buf, flushLen, base64Buf, 0, base64Pos - flushLen)
                                            base64Pos -= flushLen
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
                        }

                        STATE_SKIP_DATA -> {
                            if (b == QUOTE) {
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

    private fun tailEquals(buf: ByteArray, bufLen: Int, marker: ByteArray): Boolean {
        if (bufLen < marker.size) return false
        val offset = bufLen - marker.size
        for (i in marker.indices) {
            if (buf[offset + i] != marker[i]) return false
        }
        return true
    }

    private fun readImageDimensions(file: File): GeneratedImageFile? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) {
            GeneratedImageFile(file.absolutePath, opts.outWidth, opts.outHeight)
        } else {
            null
        }
    }

    companion object {
        private const val BUFFER_SIZE = 65_536
        private const val BASE64_CHUNK_SIZE = 65_536
        private const val STREAM_TIMEOUT_MS = 600_000L

        private const val STATE_SEARCHING = 0
        private const val STATE_IN_DATA = 1
        private const val STATE_SKIP_DATA = 2

        private const val QUOTE: Byte = '"'.code.toByte()

        /** GPT 响应中 base64 数据的标记：`"b64_json":"` 或 `"b64_json": "` */
        private val MARKERS = listOf(
            "\"b64_json\":\"".toByteArray(Charsets.US_ASCII),
            "\"b64_json\": \"".toByteArray(Charsets.US_ASCII)
        )
        private val MAX_MARKER_LEN = MARKERS.maxOf { it.size }
    }
}
