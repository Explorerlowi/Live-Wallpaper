package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/** iOS Application Support implementation used while preserving legacy embedded images. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSLegacyPaintImageFileStore(
    private val dispatchers: CoroutineDispatcherProvider,
) : LegacyPaintImageFileStore {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun isReadable(identifier: String): Boolean = withContext(dispatchers.io) {
        fileManager.isReadableFileAtPath(identifier.removePrefix("file://"))
    }

    override suspend fun writeLegacyImage(
        sessionId: String,
        messageId: String,
        imageId: String,
        mimeType: String,
        bytes: ByteArray,
    ): String? = withContext(dispatchers.io) {
        runCatching {
            val base = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory,
                NSUserDomainMask,
                true,
            ).first() as String
            val directory = "$base/aipaint/${safeName(sessionId)}/legacy"
            fileManager.createDirectoryAtPath(directory, true, null, null)
            val path = "$directory/${safeName(messageId)}_${safeName(imageId)}.${extension(mimeType)}"
            check(bytes.toNSData().writeToFile(path, atomically = true))
            path
        }.getOrNull()
    }

    override suspend fun discardCreatedFiles(identifiers: List<String>) = withContext(dispatchers.io) {
        identifiers.forEach { identifier -> runCatching { fileManager.removeItemAtPath(identifier, null) } }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun safeName(value: String): String = value.map { if (it.isLetterOrDigit() || it in "-_") it else '_' }
        .joinToString("")

    private fun extension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
}
