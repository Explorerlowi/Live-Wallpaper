package com.example.livewallpaper.feature.aipaint.data.repository

import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.data.remote.GeminiApiService
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PaintRepositoryImpl(
    private val settings: ObservableSettings,
    private val geminiApiService: GeminiApiService
) : PaintRepository {

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true  // 确保默认值也被序列化，避免时间戳丢失
    }
    
    companion object {
        private const val KEY_SESSIONS = "PAINT_SESSIONS"
        private const val KEY_MESSAGES_PREFIX = "PAINT_MESSAGES_" // legacy
        private const val KEY_MESSAGE_IDS_PREFIX = "PAINT_MESSAGE_IDS_"
        private const val KEY_MESSAGE_PREFIX = "PAINT_MESSAGE_"
        private const val KEY_API_PROFILES = "PAINT_API_PROFILES"
        private const val KEY_ACTIVE_PROFILE = "PAINT_ACTIVE_PROFILE"
    }

    // ========== 会话管理 ==========
    
    override fun getSessions(): Flow<List<PaintSession>> = callbackFlow {
        val listener = settings.addStringListener(KEY_SESSIONS, "") { jsonString ->
            launch(Dispatchers.Default) {
                trySend(parseSessions(jsonString).sortedByDescending { it.updatedAt })
            }
        }
        launch(Dispatchers.Default) {
            trySend(parseSessions(settings.getString(KEY_SESSIONS, "")).sortedByDescending { it.updatedAt })
        }
        awaitClose { listener.deactivate() }
    }

    override fun getSession(sessionId: String): Flow<PaintSession?> = 
        getSessions().map { sessions -> sessions.find { it.id == sessionId } }

    override suspend fun createSession(session: PaintSession): String {
        return withContext(Dispatchers.Default) {
            val sessions = getCurrentSessions().toMutableList()
            sessions.add(0, session)
            saveSessions(sessions)
            session.id
        }
    }

    override suspend fun updateSession(session: PaintSession) {
        withContext(Dispatchers.Default) {
            val sessions = getCurrentSessions().toMutableList()
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) {
                sessions[index] = session.copy(updatedAt = TimeProvider.currentTimeMillis())
                saveSessions(sessions)
            }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.Default) {
            val sessions = getCurrentSessions().filter { it.id != sessionId }
            saveSessions(sessions)
            migrateLegacyMessagesIfNeeded(sessionId)
            val idsKey = KEY_MESSAGE_IDS_PREFIX + sessionId
            val ids = getCurrentMessageIds(sessionId)
            ids.forEach { messageId ->
                settings.remove(KEY_MESSAGE_PREFIX + messageId)
            }
            settings.remove(idsKey)
            settings.remove(KEY_MESSAGES_PREFIX + sessionId)
        }
    }

    // ========== 消息管理 ==========
    
    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = callbackFlow {
        val idsKey = KEY_MESSAGE_IDS_PREFIX + sessionId
        var messageListeners = emptyList<com.russhwolf.settings.SettingsListener>()

        suspend fun emitCurrentMessages() {
            migrateLegacyMessagesIfNeeded(sessionId)
            trySend(loadMessagesByIds(sessionId))
        }

        fun clearMessageListeners() {
            messageListeners.forEach { it.deactivate() }
            messageListeners = emptyList()
        }

        fun registerMessageListeners(messageIds: List<String>) {
            clearMessageListeners()
            messageListeners = messageIds.distinct().map { messageId ->
                settings.addStringListener(KEY_MESSAGE_PREFIX + messageId, "") {
                    launch(Dispatchers.Default) {
                        emitCurrentMessages()
                    }
                }
            }
        }

        val idsListener = settings.addStringListener(idsKey, "") { jsonString ->
            launch(Dispatchers.Default) {
                migrateLegacyMessagesIfNeeded(sessionId)
                val ids = parseMessageIds(jsonString)
                registerMessageListeners(ids)
                emitCurrentMessages()
            }
        }

        launch(Dispatchers.Default) {
            migrateLegacyMessagesIfNeeded(sessionId)
            val ids = parseMessageIds(settings.getString(idsKey, ""))
            registerMessageListeners(ids)
            emitCurrentMessages()
        }

        awaitClose {
            idsListener.deactivate()
            clearMessageListeners()
        }
    }

    override fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>> =
        getMessages(sessionId).map { messages ->
            // 按时间倒序排列，然后分页
            messages.sortedByDescending { it.createdAt }
                .drop(offset)
                .take(limit)
        }

    override suspend fun addMessage(message: PaintMessage) {
        withContext(Dispatchers.Default) {
            migrateLegacyMessagesIfNeeded(message.sessionId)
            val idsKey = KEY_MESSAGE_IDS_PREFIX + message.sessionId
            val currentIds = getCurrentMessageIds(message.sessionId).toMutableList()
            currentIds.add(message.id)
            settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(message)
            settings[idsKey] = json.encodeToString(currentIds)
        }
    }

    override suspend fun updateMessage(message: PaintMessage) {
        withContext(Dispatchers.Default) {
            migrateLegacyMessagesIfNeeded(message.sessionId)
            settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(
                message.copy(updatedAt = TimeProvider.currentTimeMillis())
            )
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        withContext(Dispatchers.Default) {
            val sessions = getCurrentSessions()
            for (session in sessions) {
                migrateLegacyMessagesIfNeeded(session.id)
                val idsKey = KEY_MESSAGE_IDS_PREFIX + session.id
                val ids = getCurrentMessageIds(session.id)
                if (messageId in ids) {
                    val filtered = ids.filter { it != messageId }
                    settings[idsKey] = json.encodeToString(filtered)
                    settings.remove(KEY_MESSAGE_PREFIX + messageId)
                    break
                }
            }
        }
    }

    override suspend fun getMessageCount(sessionId: String): Int =
        withContext(Dispatchers.Default) {
            migrateLegacyMessagesIfNeeded(sessionId)
            getCurrentMessageIds(sessionId).size
        }

    // ========== API配置管理 ==========
    
    override fun getApiProfiles(): Flow<List<ApiProfile>> = callbackFlow {
        val listener = settings.addStringListener(KEY_API_PROFILES, "") { jsonString ->
            launch(Dispatchers.Default) {
                trySend(parseProfiles(jsonString))
            }
        }
        launch(Dispatchers.Default) {
            trySend(parseProfiles(settings.getString(KEY_API_PROFILES, "")))
        }
        awaitClose { listener.deactivate() }
    }

    override fun getActiveProfile(): Flow<ApiProfile?> = callbackFlow {
        val profilesListener = settings.addStringListener(KEY_API_PROFILES, "") { _ ->
            launch(Dispatchers.Default) {
                trySend(findActiveProfile())
            }
        }
        val activeListener = settings.addStringOrNullListener(KEY_ACTIVE_PROFILE) { _ ->
            launch(Dispatchers.Default) {
                trySend(findActiveProfile())
            }
        }
        launch(Dispatchers.Default) {
            trySend(findActiveProfile())
        }
        awaitClose {
            profilesListener.deactivate()
            activeListener.deactivate()
        }
    }

    override suspend fun saveApiProfile(profile: ApiProfile) {
        withContext(Dispatchers.Default) {
            val profiles = getCurrentProfiles().toMutableList()
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                profiles[index] = profile
            } else {
                profiles.add(profile)
                if (profiles.size == 1) {
                    settings[KEY_ACTIVE_PROFILE] = profile.id
                }
            }
            saveProfiles(profiles)
        }
    }

    override suspend fun deleteApiProfile(profileId: String) {
        withContext(Dispatchers.Default) {
            val profiles = getCurrentProfiles().filter { it.id != profileId }
            saveProfiles(profiles)
            if (settings.getStringOrNull(KEY_ACTIVE_PROFILE) == profileId) {
                settings[KEY_ACTIVE_PROFILE] = profiles.firstOrNull()?.id
            }
        }
    }

    override suspend fun setActiveProfile(profileId: String) {
        settings[KEY_ACTIVE_PROFILE] = profileId
    }

    // ========== AI 绘画功能 ==========
    
    override suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): AppResult<String> {
        return geminiApiService.generateImage(
            profile = profile,
            model = model,
            prompt = prompt,
            images = images,
            aspectRatio = aspectRatio,
            resolution = resolution
        )
    }
    
    override suspend fun enhancePrompt(
        profile: ApiProfile,
        prompt: String
    ): AppResult<String> {
        return geminiApiService.enhancePrompt(profile, prompt)
    }

    // ========== 私有方法 ==========
    
    private fun parseSessions(jsonString: String): List<PaintSession> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseMessages(jsonString: String): List<PaintMessage> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseMessageIds(jsonString: String): List<String> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseProfiles(jsonString: String): List<ApiProfile> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCurrentSessions(): List<PaintSession> =
        parseSessions(settings.getString(KEY_SESSIONS, ""))

    private fun getCurrentMessages(sessionId: String): List<PaintMessage> =
        parseMessages(settings.getString(KEY_MESSAGES_PREFIX + sessionId, ""))

    private fun getCurrentMessageIds(sessionId: String): List<String> =
        parseMessageIds(settings.getString(KEY_MESSAGE_IDS_PREFIX + sessionId, ""))

    private fun loadMessagesByIds(sessionId: String): List<PaintMessage> {
        val ids = getCurrentMessageIds(sessionId)
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { messageId ->
            val jsonString = settings.getString(KEY_MESSAGE_PREFIX + messageId, "")
            if (jsonString.isBlank()) return@mapNotNull null
            try {
                json.decodeFromString<PaintMessage>(jsonString)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun migrateLegacyMessagesIfNeeded(sessionId: String) {
        val idsKey = KEY_MESSAGE_IDS_PREFIX + sessionId
        val alreadyMigrated = settings.getString(idsKey, "").isNotBlank()
        if (alreadyMigrated) return

        val legacyKey = KEY_MESSAGES_PREFIX + sessionId
        val legacyJson = settings.getString(legacyKey, "")
        if (legacyJson.isBlank()) return

        val legacyMessages = parseMessages(legacyJson)
        if (legacyMessages.isEmpty()) {
            settings.remove(legacyKey)
            return
        }

        val ids = legacyMessages.map { it.id }
        legacyMessages.forEach { msg ->
            settings[KEY_MESSAGE_PREFIX + msg.id] = json.encodeToString(msg)
        }
        settings[idsKey] = json.encodeToString(ids)
        settings.remove(legacyKey)
    }

    private fun getCurrentProfiles(): List<ApiProfile> =
        parseProfiles(settings.getString(KEY_API_PROFILES, ""))

    private fun findActiveProfile(): ApiProfile? {
        val activeId = settings.getStringOrNull(KEY_ACTIVE_PROFILE)
        return getCurrentProfiles().find { it.id == activeId }
    }

    private fun saveSessions(sessions: List<PaintSession>) {
        settings[KEY_SESSIONS] = json.encodeToString(sessions)
    }

    private fun saveProfiles(profiles: List<ApiProfile>) {
        settings[KEY_API_PROFILES] = json.encodeToString(profiles)
    }
}
