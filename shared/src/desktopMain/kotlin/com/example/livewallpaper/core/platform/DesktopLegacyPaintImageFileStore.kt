package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Desktop private-file implementation used while preserving legacy embedded images. */
class DesktopLegacyPaintImageFileStore(
    private val dispatchers: CoroutineDispatcherProvider,
) : LegacyPaintImageFileStore {
    override suspend fun isReadable(identifier: String): Boolean = withContext(dispatchers.io) {
        File(identifier.removePrefix("file://")).isFile
    }

    override suspend fun writeLegacyImage(
        sessionId: String,
        messageId: String,
        imageId: String,
        mimeType: String,
        bytes: ByteArray,
    ): String? = withContext(dispatchers.io) {
        runCatching {
            val directory = File(DesktopAiPaintStoragePaths.generatedImagesDirectory(), "$sessionId/legacy")
                .apply { mkdirs() }
            val target = File(directory, "${safeName(messageId)}_${safeName(imageId)}.${extension(mimeType)}")
            val temporary = File(directory, ".${target.name}.${UUID.randomUUID()}.tmp")
            try {
                temporary.writeBytes(bytes)
                runCatching {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                }.getOrElse {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temporary.delete()
            }
            target.absolutePath
        }.getOrNull()
    }

    override suspend fun discardCreatedFiles(identifiers: List<String>) = withContext(dispatchers.io) {
        identifiers.forEach { identifier -> runCatching { File(identifier).delete() } }
    }

    private fun safeName(value: String): String = value.map { if (it.isLetterOrDigit() || it in "-_") it else '_' }
        .joinToString("")

    private fun extension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
}
