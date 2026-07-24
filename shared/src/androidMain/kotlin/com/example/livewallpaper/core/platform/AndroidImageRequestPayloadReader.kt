package com.example.livewallpaper.core.platform

import android.content.Context
import android.net.Uri
import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import java.io.File
import kotlinx.coroutines.withContext

/** Android private-file and content-URI reader used only while constructing an API request. */
class AndroidImageRequestPayloadReader(
    private val context: Context,
    private val dispatchers: CoroutineDispatcherProvider,
) : ImageRequestPayloadReader {
    override suspend fun read(sourceIdentifier: String, mimeType: String): ImageRequestPayload? =
        withContext(dispatchers.io) {
            runCatching {
                val bytes = if (sourceIdentifier.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(sourceIdentifier))?.use { it.readBytes() }
                } else {
                    File(sourceIdentifier.removePrefix("file://")).takeIf(File::isFile)?.readBytes()
                } ?: return@runCatching null
                ImageRequestPayload(bytes, mimeType)
            }.getOrNull()
        }
}
