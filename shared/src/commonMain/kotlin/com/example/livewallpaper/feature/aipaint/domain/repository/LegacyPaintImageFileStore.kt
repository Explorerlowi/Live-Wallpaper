package com.example.livewallpaper.feature.aipaint.domain.repository

/** Platform boundary for preserving legacy embedded images as private files. */
interface LegacyPaintImageFileStore {
    /** @return Whether the existing platform identifier can currently be read. */
    suspend fun isReadable(identifier: String): Boolean

    /** Writes one decoded legacy image atomically and returns its persistent identifier. */
    suspend fun writeLegacyImage(
        sessionId: String,
        messageId: String,
        imageId: String,
        mimeType: String,
        bytes: ByteArray,
    ): String?

    /** Removes files created by a migration transaction that did not commit. */
    suspend fun discardCreatedFiles(identifiers: List<String>)
}
