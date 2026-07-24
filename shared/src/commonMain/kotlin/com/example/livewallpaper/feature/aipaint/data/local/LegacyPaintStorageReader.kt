package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyBase64
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintImageV1
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintMessageV1
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.toDomain
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.russhwolf.settings.Settings
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Strictly reads retained preference conversations without mutating the legacy keys. */
class LegacyPaintStorageReader(
    private val settings: Settings,
    private val imageFileStore: LegacyPaintImageFileStore,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Decodes and normalizes every referenced legacy message. */
    suspend fun read(): LegacyPaintStorageReadResult = read(strictEmbeddedImages = true)

    /** Best-effort mapping used only when the strict migration cannot commit this process. */
    suspend fun readForFallback(): LegacyPaintStorageReadResult = read(strictEmbeddedImages = false)

    private suspend fun read(strictEmbeddedImages: Boolean): LegacyPaintStorageReadResult = withContext(dispatchers.io) {
        val createdFiles = mutableListOf<String>()
        try {
            val sessionsContent = if (settings.hasKey(KEY_SESSIONS)) settings.getString(KEY_SESSIONS, "") else "[]"
            val sessions = decodeList<PaintSession>(sessionsContent)
                ?: return@withContext LegacyPaintStorageReadResult.Corrupted(createdFiles)
            val sessionIds = sessions.map(PaintSession::id)
            if (sessionIds.any(String::isBlank) || sessionIds.size != sessionIds.distinct().size) {
                return@withContext LegacyPaintStorageReadResult.Corrupted(createdFiles)
            }
            val messages = mutableListOf<com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage>()
            for (session in sessions) {
                val legacyMessages = readSessionMessages(session.id)
                    ?: return@withContext LegacyPaintStorageReadResult.Corrupted(createdFiles)
                for (legacyMessage in legacyMessages) {
                    if (legacyMessage.sessionId != session.id || legacyMessage.id.isBlank()) {
                        return@withContext LegacyPaintStorageReadResult.Corrupted(createdFiles)
                    }
                    val images = legacyMessage.images.map { image ->
                        normalizeImage(legacyMessage, image, createdFiles, strictEmbeddedImages)
                            ?: return@withContext LegacyPaintStorageReadResult.Corrupted(createdFiles)
                    }
                    messages += legacyMessage.toDomain(images)
                }
            }
            val messageIds = messages.map { it.id }
            if (messageIds.size != messageIds.distinct().size) {
                LegacyPaintStorageReadResult.Corrupted(createdFiles)
            } else {
                LegacyPaintStorageReadResult.Success(PaintDataSnapshot(sessions, messages), createdFiles)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            LegacyPaintStorageReadResult.Corrupted(createdFiles)
        }
    }

    private fun readSessionMessages(sessionId: String): List<LegacyPaintMessageV1>? {
        val idsKey = KEY_MESSAGE_IDS_PREFIX + sessionId
        if (settings.hasKey(idsKey)) {
            val idsContent = settings.getString(idsKey, "")
            val ids = decodeList<String>(idsContent) ?: return null
            if (ids.any(String::isBlank) || ids.size != ids.distinct().size) return null
            return ids.map { messageId ->
                val content = settings.getString(KEY_MESSAGE_PREFIX + messageId, "")
                if (content.isBlank()) return null
                decodeValue<LegacyPaintMessageV1>(content)?.takeIf { it.id == messageId } ?: return null
            }
        }
        val messagesKey = KEY_MESSAGES_PREFIX + sessionId
        return if (settings.hasKey(messagesKey)) {
            decodeList(settings.getString(messagesKey, ""))
        } else {
            emptyList()
        }
    }

    private suspend fun normalizeImage(
        message: LegacyPaintMessageV1,
        image: LegacyPaintImageV1,
        createdFiles: MutableList<String>,
        strictEmbeddedImages: Boolean,
    ): PaintImage? {
        val existingPath = image.localPath
        if (!existingPath.isNullOrBlank() && imageFileStore.isReadable(existingPath)) {
            return image.toDomain(existingPath)
        }
        val embedded = image.base64Data
        if (!embedded.isNullOrBlank()) {
            val bytes = LegacyBase64.decode(embedded)
                ?: return if (strictEmbeddedImages) null else image.toDomain(existingPath)
            val restored = imageFileStore.writeLegacyImage(
                sessionId = message.sessionId,
                messageId = message.id,
                imageId = image.id,
                mimeType = image.mimeType,
                bytes = bytes,
            ) ?: return if (strictEmbeddedImages) null else image.toDomain(existingPath)
            createdFiles += restored
            return image.toDomain(restored)
        }
        return image.toDomain(existingPath)
    }

    private inline fun <reified T> decodeList(content: String): List<T>? {
        return runCatching { json.decodeFromString<List<T>>(content) }.getOrNull()
    }

    private inline fun <reified T> decodeValue(content: String): T? =
        runCatching { json.decodeFromString<T>(content) }.getOrNull()

    private companion object {
        const val KEY_SESSIONS = "PAINT_SESSIONS"
        const val KEY_MESSAGES_PREFIX = "PAINT_MESSAGES_"
        const val KEY_MESSAGE_IDS_PREFIX = "PAINT_MESSAGE_IDS_"
        const val KEY_MESSAGE_PREFIX = "PAINT_MESSAGE_"
    }
}

/** Result of reading and materializing the retained legacy conversation snapshot. */
sealed interface LegacyPaintStorageReadResult {
    data class Success(
        val snapshot: PaintDataSnapshot,
        val createdFiles: List<String>,
    ) : LegacyPaintStorageReadResult

    data class Corrupted(val createdFiles: List<String>) : LegacyPaintStorageReadResult
}
