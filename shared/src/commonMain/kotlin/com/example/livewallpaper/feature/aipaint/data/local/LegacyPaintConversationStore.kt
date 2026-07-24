package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Same-process fallback writer for the retained Preferences conversation format.
 *
 * It is activated with one strictly decoded snapshot and never exposes legacy DTOs outside data/local.
 */
class LegacyPaintConversationStore(
    private val settings: Settings,
    private val dispatchers: CoroutineDispatcherProvider,
) : PaintConversationDataSource {
    private val json = Json { encodeDefaults = true }
    private val lock = Mutex()
    private val snapshot = MutableStateFlow(PaintDataSnapshot(emptyList(), emptyList()))

    /** Installs the snapshot produced by the migration reader before this backend is selected. */
    suspend fun activate(data: PaintDataSnapshot) = withContext(dispatchers.io) {
        lock.withLock { snapshot.value = data.sortedForStorage() }
    }

    /** Returns the current normalized fallback snapshot. */
    suspend fun readSnapshot(): PaintDataSnapshot = withContext(dispatchers.io) { snapshot.value }

    /** Merges a validated snapshot and persists it in the retained independent-message format. */
    suspend fun mergeSnapshot(imported: PaintDataSnapshot): PaintDataSnapshot = withContext(dispatchers.io) {
        lock.withLock {
            val current = snapshot.value
            val sessions = mergeById(current.sessions, imported.sessions, PaintSession::id)
            val messages = mergeById(current.messages, imported.messages, PaintMessage::id)
            val merged = PaintDataSnapshot(sessions, messages).sortedForStorage()
            persistSnapshot(merged)
            snapshot.value = merged
            merged
        }
    }

    /** Replaces the retained snapshot exactly. */
    suspend fun replaceSnapshot(replacement: PaintDataSnapshot): Boolean = withContext(dispatchers.io) {
        runCatching {
            lock.withLock {
                val normalized = replacement.sortedForStorage()
                persistSnapshot(normalized)
                snapshot.value = normalized
            }
        }.isSuccess
    }

    override fun getSessions(): Flow<List<PaintSession>> = snapshot.map { it.sessions.sortedForDisplay() }

    override fun getSession(sessionId: String): Flow<PaintSession?> =
        snapshot.map { data -> data.sessions.firstOrNull { it.id == sessionId } }

    override suspend fun createSession(session: PaintSession): String {
        updateSession(session)
        return session.id
    }

    override suspend fun updateSession(session: PaintSession) = mutate { data ->
        val sessions = data.sessions.filterNot { it.id == session.id } + session
        data.copy(sessions = sessions)
    }

    override suspend fun deleteSession(sessionId: String) = mutate { data ->
        data.copy(
            sessions = data.sessions.filterNot { it.id == sessionId },
            messages = data.messages.filterNot { it.sessionId == sessionId },
        )
    }

    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = snapshot.map { data ->
        data.messages.filter { it.sessionId == sessionId }.sortedBy(PaintMessage::createdAt)
    }

    override fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>> =
        getMessages(sessionId).map { messages -> messages.drop(offset).take(limit) }

    override suspend fun addMessage(message: PaintMessage) = updateMessage(message)

    override suspend fun updateMessage(message: PaintMessage) = mutate { data ->
        check(data.sessions.any { it.id == message.sessionId }) { "Unknown painting session" }
        val messages = data.messages.filterNot { it.id == message.id } + message
        data.copy(messages = messages)
    }

    override suspend fun deleteMessage(messageId: String) = mutate { data ->
        data.copy(messages = data.messages.filterNot { it.id == messageId })
    }

    override suspend fun getMessageCount(sessionId: String): Int = withContext(dispatchers.io) {
        snapshot.value.messages.count { it.sessionId == sessionId }
    }

    override suspend fun getMessage(messageId: String): PaintMessage? = withContext(dispatchers.io) {
        snapshot.value.messages.firstOrNull { it.id == messageId }
    }

    override suspend fun getMessagesByVersionGroup(
        sessionId: String,
        versionGroup: String,
    ): List<PaintMessage> = withContext(dispatchers.io) {
        snapshot.value.messages.filter { it.sessionId == sessionId && it.versionGroup == versionGroup }
    }

    override suspend fun getVersionCount(sessionId: String, versionGroup: String): Int =
        getMessagesByVersionGroup(sessionId, versionGroup).size

    private suspend fun mutate(transform: (PaintDataSnapshot) -> PaintDataSnapshot) = withContext(dispatchers.io) {
        lock.withLock {
            val updated = transform(snapshot.value).sortedForStorage()
            persistSnapshot(updated)
            snapshot.value = updated
        }
    }

    private fun persistSnapshot(data: PaintDataSnapshot) {
        val previous = snapshot.value
        val currentSessionIds = data.sessions.mapTo(mutableSetOf(), PaintSession::id)
        val currentMessageIds = data.messages.mapTo(mutableSetOf(), PaintMessage::id)

        settings.putString(KEY_SESSIONS, json.encodeToString(data.sessions))
        data.sessions.forEach { session ->
            val messages = data.messages.filter { it.sessionId == session.id }
            settings.putString(KEY_MESSAGE_IDS_PREFIX + session.id, json.encodeToString(messages.map(PaintMessage::id)))
            messages.forEach { message ->
                settings.putString(KEY_MESSAGE_PREFIX + message.id, json.encodeToString(message))
            }
        }
        previous.messages.filterNot { it.id in currentMessageIds }.forEach { message ->
            settings.remove(KEY_MESSAGE_PREFIX + message.id)
        }
        previous.sessions.filterNot { it.id in currentSessionIds }.forEach { session ->
            settings.remove(KEY_MESSAGE_IDS_PREFIX + session.id)
        }
    }

    private fun PaintDataSnapshot.sortedForStorage(): PaintDataSnapshot = copy(
        sessions = sessions.sortedBy(PaintSession::createdAt),
        messages = messages.sortedWith(compareBy(PaintMessage::sessionId).thenBy(PaintMessage::createdAt)),
    )

    private fun List<PaintSession>.sortedForDisplay(): List<PaintSession> = sortedWith(
        compareByDescending<PaintSession> { it.isPinned }
            .thenByDescending { it.pinnedAt ?: 0L }
            .thenByDescending(PaintSession::updatedAt),
    )

    private fun <T> mergeById(existing: List<T>, imported: List<T>, id: (T) -> String): List<T> {
        val importedById = imported.associateBy(id)
        val existingIds = existing.mapTo(mutableSetOf(), id)
        return existing.map { value -> importedById[id(value)] ?: value } +
            imported.filterNot { id(it) in existingIds }
    }

    private companion object {
        const val KEY_SESSIONS = "PAINT_SESSIONS"
        const val KEY_MESSAGE_IDS_PREFIX = "PAINT_MESSAGE_IDS_"
        const val KEY_MESSAGE_PREFIX = "PAINT_MESSAGE_"
    }
}
