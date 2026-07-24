package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import java.io.File
import java.net.URI
import kotlinx.coroutines.withContext

/** Desktop file reader used only while constructing an API request. */
class DesktopImageRequestPayloadReader(
    private val dispatchers: CoroutineDispatcherProvider,
) : ImageRequestPayloadReader {
    override suspend fun read(sourceIdentifier: String, mimeType: String): ImageRequestPayload? =
        withContext(dispatchers.io) {
            runCatching {
                val file = if (sourceIdentifier.startsWith("file:", ignoreCase = true)) {
                    File(URI(sourceIdentifier))
                } else {
                    File(sourceIdentifier)
                }
                file.takeIf(File::isFile)?.let { ImageRequestPayload(it.readBytes(), mimeType) }
            }.getOrNull()
        }
}
