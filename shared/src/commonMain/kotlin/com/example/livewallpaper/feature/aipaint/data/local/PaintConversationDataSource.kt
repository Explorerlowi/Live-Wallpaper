package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import kotlinx.coroutines.flow.Flow

/** Local structured storage used by the painting repository. */
interface PaintConversationDataSource {
    /** Observes all sessions in display order. */
    fun getSessions(): Flow<List<PaintSession>>

    /** Observes one session by stable ID. */
    fun getSession(sessionId: String): Flow<PaintSession?>

    /** Persists [session] and returns its stable ID. */
    suspend fun createSession(session: PaintSession): String

    /** Replaces the stored fields for [session]. */
    suspend fun updateSession(session: PaintSession)

    /** Deletes a session and its related messages. */
    suspend fun deleteSession(sessionId: String)

    /** Observes all messages in one session in chronological order. */
    fun getMessages(sessionId: String): Flow<List<PaintMessage>>

    /** Observes one reverse-chronological page of messages. */
    fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>>

    /** Adds a message and its image metadata. */
    suspend fun addMessage(message: PaintMessage)

    /** Replaces a message and its image metadata. */
    suspend fun updateMessage(message: PaintMessage)

    /** Deletes one message by stable ID. */
    suspend fun deleteMessage(messageId: String)

    /** Returns the number of messages in a session. */
    suspend fun getMessageCount(sessionId: String): Int

    /** Returns one message by stable ID, or null when absent. */
    suspend fun getMessage(messageId: String): PaintMessage?

    /** Returns all variants in a regeneration version group. */
    suspend fun getMessagesByVersionGroup(sessionId: String, versionGroup: String): List<PaintMessage>

    /** Returns the number of variants in a regeneration version group. */
    suspend fun getVersionCount(sessionId: String, versionGroup: String): Int
}
