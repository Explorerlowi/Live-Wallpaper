package com.example.livewallpaper.feature.aipaint.data.repository

import com.example.livewallpaper.core.error.AppError
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.core.platform.ImageResponseProcessor
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.data.local.ApiProfileBackupDecodeResult
import com.example.livewallpaper.feature.aipaint.data.local.ApiProfileBackupJson
import com.example.livewallpaper.feature.aipaint.data.remote.GeminiApiService
import com.example.livewallpaper.feature.aipaint.data.remote.GptApiService
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PaintRepositoryImpl(
    private val settings: ObservableSettings,
    private val geminiApiService: GeminiApiService,
    private val imageResponseProcessor: ImageResponseProcessor,
    private val gptApiService: GptApiService,
    private val gptImageResponseProcessor: GptImageResponseProcessor
) : PaintRepository, PaintDataRepository {

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true  // 确保默认值也被序列化，避免时间戳丢失
    }
    private val paintDataMutex = Mutex()
    
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
                trySend(parseSessions(jsonString).sortedForDisplay())
            }
        }
        launch(Dispatchers.Default) {
            trySend(parseSessions(settings.getString(KEY_SESSIONS, "")).sortedForDisplay())
        }
        awaitClose { listener.deactivate() }
    }

    override fun getSession(sessionId: String): Flow<PaintSession?> = 
        getSessions().map { sessions -> sessions.find { it.id == sessionId } }

    override suspend fun createSession(session: PaintSession): String {
        return withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                val sessions = getCurrentSessions().toMutableList()
                sessions.add(0, session)
                saveSessions(sessions)
                session.id
            }
        }
    }

    override suspend fun updateSession(session: PaintSession) {
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                val sessions = getCurrentSessions().toMutableList()
                val index = sessions.indexOfFirst { it.id == session.id }
                if (index >= 0) {
                    sessions[index] = session.copy(updatedAt = TimeProvider.currentTimeMillis())
                    saveSessions(sessions)
                }
            }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
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
    }

    // ========== 消息管理 ==========
    
    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = callbackFlow {
        val idsKey = KEY_MESSAGE_IDS_PREFIX + sessionId
        var messageListeners = emptyList<com.russhwolf.settings.SettingsListener>()

        suspend fun emitCurrentMessages() {
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(sessionId)
                trySend(loadMessagesByIds(sessionId))
            }
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

        val idsListener = settings.addStringListener(idsKey, "") { _ ->
            launch(Dispatchers.Default) {
                val ids = paintDataMutex.withLock {
                    migrateLegacyMessagesIfNeeded(sessionId)
                    getCurrentMessageIds(sessionId)
                }
                registerMessageListeners(ids)
                emitCurrentMessages()
            }
        }

        launch(Dispatchers.Default) {
            val ids = paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(sessionId)
                getCurrentMessageIds(sessionId)
            }
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
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(message.sessionId)
                val idsKey = KEY_MESSAGE_IDS_PREFIX + message.sessionId
                val currentIds = getCurrentMessageIds(message.sessionId).toMutableList()
                if (message.id !in currentIds) {
                    currentIds.add(message.id)
                }
                settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(message)
                settings[idsKey] = json.encodeToString(currentIds)
            }
        }
    }

    override suspend fun updateMessage(message: PaintMessage) {
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(message.sessionId)
                settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(
                    message.copy(updatedAt = TimeProvider.currentTimeMillis())
                )
            }
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
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
    }

    override suspend fun getMessageCount(sessionId: String): Int =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(sessionId)
                getCurrentMessageIds(sessionId).size
            }
        }

    override suspend fun getMessage(messageId: String): PaintMessage? =
        withContext(Dispatchers.Default) {
            val jsonString = settings.getString(KEY_MESSAGE_PREFIX + messageId, "")
            if (jsonString.isBlank()) return@withContext null
            try {
                json.decodeFromString<PaintMessage>(jsonString)
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun getMessagesByVersionGroup(sessionId: String, versionGroup: String): List<PaintMessage> =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(sessionId)
                loadMessagesByIds(sessionId).filter { it.versionGroup == versionGroup }
                    .sortedBy { it.versionIndex }
            }
        }

    override suspend fun getVersionCount(sessionId: String, versionGroup: String): Int =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                migrateLegacyMessagesIfNeeded(sessionId)
                loadMessagesByIds(sessionId).count { it.versionGroup == versionGroup }
            }
        }

    // ========== 绘画数据备份与恢复 ==========

    override suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                readPaintDataSnapshot()
            }
        }

    override suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                val existingSnapshot = when (val result = readPaintDataSnapshot()) {
                    PaintDataSnapshotReadResult.Corrupted -> {
                        return@withLock PaintDataMergeResult.CorruptedExistingData
                    }
                    is PaintDataSnapshotReadResult.Success -> result.snapshot
                }
                val existingSessions = existingSnapshot.sessions
                val existingMessages = existingSnapshot.messages
                val importedSessionIds = snapshot.sessions.mapTo(mutableSetOf()) { it.id }
                val importedMessageIds = snapshot.messages.mapTo(mutableSetOf()) { it.id }
                val importedMessagesBySession = snapshot.messages.groupBy { it.sessionId }
                val existingMessagesBySession = existingMessages.groupBy { it.sessionId }
                val mergedSessions = snapshot.sessions + existingSessions.filterNot { it.id in importedSessionIds }

                val affectedSessionIds = buildSet {
                    addAll(importedSessionIds)
                    existingMessages.filterTo(mutableListOf()) { it.id in importedMessageIds }
                        .mapTo(this) { it.sessionId }
                }

                affectedSessionIds.forEach { sessionId ->
                    val existing = existingMessagesBySession[sessionId].orEmpty()
                    val imported = importedMessagesBySession[sessionId].orEmpty()
                    val importedById = imported.associateBy { it.id }
                    val existingIds = existing.mapTo(mutableSetOf()) { it.id }
                    val mergedMessages = existing.mapNotNull { message ->
                        when {
                            message.id !in importedMessageIds -> message
                            message.id in importedById -> importedById.getValue(message.id)
                            else -> null
                        }
                    } + imported.filterNot { it.id in existingIds }

                    mergedMessages.forEach { message ->
                        settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(message)
                    }
                    settings[KEY_MESSAGE_IDS_PREFIX + sessionId] = json.encodeToString(
                        mergedMessages.map { it.id },
                    )
                    settings.remove(KEY_MESSAGES_PREFIX + sessionId)
                }
                saveSessions(mergedSessions)

                PaintDataMergeResult.Success(
                    PaintDataImportSummary(
                        importedSessionCount = snapshot.sessions.size,
                        importedMessageCount = snapshot.messages.size,
                        totalSessionCount = mergedSessions.size,
                    ),
                )
            }
        }

    override suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean =
        withContext(Dispatchers.Default) {
            paintDataMutex.withLock {
                runCatching {
                    settings.keys
                        .filter { key ->
                            key.startsWith(KEY_MESSAGE_PREFIX) ||
                                key.startsWith(KEY_MESSAGE_IDS_PREFIX) ||
                                key.startsWith(KEY_MESSAGES_PREFIX)
                        }
                        .forEach(settings::remove)

                    snapshot.messages.forEach { message ->
                        settings[KEY_MESSAGE_PREFIX + message.id] = json.encodeToString(message)
                    }
                    val messagesBySession = snapshot.messages.groupBy { message -> message.sessionId }
                    snapshot.sessions.forEach { session ->
                        settings[KEY_MESSAGE_IDS_PREFIX + session.id] = json.encodeToString(
                            messagesBySession[session.id].orEmpty().map { message -> message.id },
                        )
                    }
                    saveSessions(snapshot.sessions)
                }.isSuccess
            }
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

    override fun getApiProfilesSync(): List<ApiProfile> = getCurrentProfiles()

    override fun getActiveProfileSync(): ApiProfile? = findActiveProfile()

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

    override fun exportApiProfilesJson(): String = ApiProfileBackupJson.encode(
        ApiProfileBackup(
            activeProfileId = settings.getStringOrNull(KEY_ACTIVE_PROFILE),
            profiles = getCurrentProfiles(),
        ),
    )

    override suspend fun importApiProfilesJson(content: String): ApiProfileImportResult =
        withContext(Dispatchers.Default) {
            when (val decoded = ApiProfileBackupJson.decode(content)) {
                is ApiProfileBackupDecodeResult.Failure -> ApiProfileImportResult.Failure(decoded.error)
                is ApiProfileBackupDecodeResult.Success -> {
                    val importedProfiles = decoded.backup.profiles
                    val importedById = importedProfiles.associateBy { it.id }
                    val existingProfiles = getCurrentProfiles()
                    val existingIds = existingProfiles.mapTo(mutableSetOf()) { it.id }
                    val mergedProfiles = existingProfiles.map { profile ->
                        importedById[profile.id] ?: profile
                    } + importedProfiles.filterNot { it.id in existingIds }

                    saveProfiles(mergedProfiles)
                    decoded.backup.activeProfileId?.let { activeProfileId ->
                        settings[KEY_ACTIVE_PROFILE] = activeProfileId
                    }
                    ApiProfileImportResult.Success(
                        importedCount = importedProfiles.size,
                        totalCount = mergedProfiles.size,
                    )
                }
            }
        }

    // ========== AI 绘画功能 ==========
    
    override suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution,
        sessionId: String,
        messageId: String
    ): AppResult<List<GeneratedImageFile>> {
        val responseResult = geminiApiService.generateImage(
            profile = profile,
            model = model,
            prompt = prompt,
            images = images,
            aspectRatio = aspectRatio,
            resolution = resolution
        )
        return when (responseResult) {
            is AppResult.Success -> {
                try {
                    val files = imageResponseProcessor.processResponse(
                        responseResult.data, sessionId, messageId
                    )
                    if (files.isNotEmpty()) {
                        AppResult.Success(files)
                    } else {
                        AppResult.Error(AppError.Server(200, "未返回图片数据"))
                    }
                } catch (e: Exception) {
                    AppResult.Error(AppError.Unknown(e))
                }
            }
            is AppResult.Error -> responseResult
        }
    }
    
    override suspend fun generateGptImage(
        profile: ApiProfile,
        prompt: String,
        images: List<PaintImage>,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat,
        sessionId: String,
        messageId: String
    ): AppResult<List<GeneratedImageFile>> {
        // 有参考图时调用编辑接口，无参考图时调用生成接口
        val responseResult = if (images.isNotEmpty()) {
            gptApiService.editImage(
                profile = profile,
                prompt = prompt,
                images = images,
                size = size,
                quality = quality,
                outputFormat = outputFormat
            )
        } else {
            gptApiService.generateImage(
                profile = profile,
                prompt = prompt,
                size = size,
                quality = quality,
                outputFormat = outputFormat
            )
        }
        return when (responseResult) {
            is AppResult.Success -> {
                try {
                    val files = gptImageResponseProcessor.processResponse(
                        responseResult.data, sessionId, messageId, outputFormat
                    )
                    if (files.isNotEmpty()) {
                        AppResult.Success(files)
                    } else {
                        AppResult.Error(AppError.Server(200, "未返回图片数据"))
                    }
                } catch (e: Exception) {
                    AppResult.Error(AppError.Unknown(e))
                }
            }
            is AppResult.Error -> responseResult
        }
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

    private fun readPaintDataSnapshot(): PaintDataSnapshotReadResult {
        val sessionsJson = settings.getString(KEY_SESSIONS, "")
        val sessions = decodeStoredList<PaintSession>(sessionsJson)
            ?: return PaintDataSnapshotReadResult.Corrupted
        val sessionIds = sessions.map { it.id }
        if (sessionIds.any { it.isBlank() } || sessionIds.size != sessionIds.distinct().size) {
            return PaintDataSnapshotReadResult.Corrupted
        }

        val messages = mutableListOf<PaintMessage>()
        sessions.forEach { session ->
            val sessionMessages = readStoredSessionMessages(session.id)
                ?: return PaintDataSnapshotReadResult.Corrupted
            messages += sessionMessages
        }
        val messageIds = messages.map { it.id }
        if (messageIds.size != messageIds.distinct().size) {
            return PaintDataSnapshotReadResult.Corrupted
        }
        return PaintDataSnapshotReadResult.Success(
            PaintDataSnapshot(
                sessions = sessions,
                messages = messages,
            ),
        )
    }

    private fun readStoredSessionMessages(sessionId: String): List<PaintMessage>? {
        val idsJson = settings.getString(KEY_MESSAGE_IDS_PREFIX + sessionId, "")
        val messages = if (idsJson.isNotBlank()) {
            val ids = decodeStoredList<String>(idsJson) ?: return null
            if (ids.any { it.isBlank() } || ids.size != ids.distinct().size) return null
            ids.map { messageId ->
                val content = settings.getString(KEY_MESSAGE_PREFIX + messageId, "")
                if (content.isBlank()) return null
                val message = decodeStoredValue<PaintMessage>(content) ?: return null
                if (message.id != messageId) return null
                message
            }
        } else {
            decodeStoredList(settings.getString(KEY_MESSAGES_PREFIX + sessionId, "")) ?: return null
        }
        if (messages.any { it.id.isBlank() || it.sessionId != sessionId }) return null
        return messages
    }

    private inline fun <reified T> decodeStoredList(content: String): List<T>? {
        if (content.isBlank()) return emptyList()
        return try {
            json.decodeFromString(content)
        } catch (_: Exception) {
            null
        }
    }

    private inline fun <reified T> decodeStoredValue(content: String): T? = try {
        json.decodeFromString(content)
    } catch (_: Exception) {
        null
    }

    private fun List<PaintSession>.sortedForDisplay(): List<PaintSession> =
        sortedWith(
            compareByDescending<PaintSession> { it.isPinned }
                .thenByDescending { it.pinnedAt ?: 0L }
                .thenByDescending { it.updatedAt }
        )
}
