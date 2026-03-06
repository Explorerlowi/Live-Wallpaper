package com.example.livewallpaper.core.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.File

/**
 * Android 端图片响应流式处理器
 *
 * 处理流程：
 * 1. 将 HTTP 响应体通过 ByteReadChannel 流式写入临时文件（避免在内存中持有完整响应字符串）
 * 2. 对临时文件逐字节扫描，定位 JSON 中的 "data":"..." 字段
 * 3. 对每段 base64 数据按 64KB 分块解码，解码后直接写入目标 PNG 文件
 * 4. 删除临时文件，返回图片文件信息
 *
 * 峰值内存占用：~200KB（读缓冲 + base64 块 + 解码输出），与图片大小无关
 */
class AndroidImageResponseProcessor(
    private val context: Context
) : ImageResponseProcessor {

    override suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String
    ): List<GeneratedImageFile> = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "aipaint/$sessionId").apply { mkdirs() }
        val tempFile = File.createTempFile("gemini_resp_", ".tmp", context.cacheDir)

        try {
            streamResponseToFile(response, tempFile)
            extractAndSaveImages(tempFile, outputDir, messageId)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 将响应体流式写入临时文件
     * 使用 ByteReadChannel 按 64KB 块读取，不会一次性加载完整响应
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
     * 状态机逻辑：
     * - SEARCHING：逐字节扫描，在滑动窗口中匹配 "data":"  或 "data": " 标记
     * - IN_DATA：收集 base64 字节，每满 64KB 解码一次写入文件，遇到 " 结束
     * - SKIP_DATA：解码出错时跳过剩余数据直到 "
     */
    private fun extractAndSaveImages(
        responseFile: File,
        outputDir: File,
        messageId: String
    ): List<GeneratedImageFile> {
        val results = mutableListOf<GeneratedImageFile>()
        var imageIndex = 0

        // 搜索状态：0=SEARCHING, 1=IN_DATA, 2=SKIP_DATA
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
                                    val fn = if (imageIndex == 0) "$messageId.png"
                                             else "${messageId}_$imageIndex.png"
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

    /**
     * 判断 buf 末尾是否与 marker 完全匹配
     */
    private fun tailEquals(buf: ByteArray, bufLen: Int, marker: ByteArray): Boolean {
        if (bufLen < marker.size) return false
        val offset = bufLen - marker.size
        for (i in marker.indices) {
            if (buf[offset + i] != marker[i]) return false
        }
        return true
    }

    /**
     * 仅读取图片尺寸，不加载完整像素数据
     */
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
        private const val BASE64_CHUNK_SIZE = 65_536 // 必须是 4 的倍数
        private const val STREAM_TIMEOUT_MS = 600_000L

        private const val STATE_SEARCHING = 0
        private const val STATE_IN_DATA = 1
        private const val STATE_SKIP_DATA = 2

        private const val QUOTE: Byte = '"'.code.toByte()

        private val MARKERS = listOf(
            "\"data\":\"".toByteArray(Charsets.US_ASCII),
            "\"data\": \"".toByteArray(Charsets.US_ASCII)
        )
        private val MAX_MARKER_LEN = MARKERS.maxOf { it.size }
    }
}
