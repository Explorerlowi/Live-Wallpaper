package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** iOS file reader used only while constructing an API request. */
@OptIn(ExperimentalForeignApi::class)
class IOSImageRequestPayloadReader(
    private val dispatchers: CoroutineDispatcherProvider,
) : ImageRequestPayloadReader {
    override suspend fun read(sourceIdentifier: String, mimeType: String): ImageRequestPayload? =
        withContext(dispatchers.io) {
            runCatching {
                val data = NSData.dataWithContentsOfFile(sourceIdentifier.removePrefix("file://"))
                    ?: return@runCatching null
                val bytes = ByteArray(data.length.toInt())
                if (bytes.isNotEmpty()) {
                    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
                }
                ImageRequestPayload(bytes, mimeType)
            }.getOrNull()
        }
}
