package com.example.livewallpaper.feature.aipaint.data.repository

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.error.AppError
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.core.platform.ImageResponseProcessor
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.data.local.ApiProfileBackupDecodeResult
import com.example.livewallpaper.feature.aipaint.data.local.ApiProfileBackupJson
import com.example.livewallpaper.feature.aipaint.data.local.PaintConversationDataSource
import com.example.livewallpaper.feature.aipaint.data.remote.GeminiApiService
import com.example.livewallpaper.feature.aipaint.data.remote.GptApiService
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
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
    private val geminiApiService: GeminiApiService,
    private val imageResponseProcessor: ImageResponseProcessor,
    private val gptApiService: GptApiService,
    private val gptImageResponseProcessor: GptImageResponseProcessor,
    private val localDataSource: PaintConversationDataSource,
    private val dispatchers: CoroutineDispatcherProvider,
) : PaintRepository {

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true  // 确保默认值也被序列化，避免时间戳丢失
    }
    
    companion object {
        private const val KEY_API_PROFILES = "PAINT_API_PROFILES"
        private const val KEY_ACTIVE_PROFILE = "PAINT_ACTIVE_PROFILE"
    }

    // ========== Local structured data ==========

    override fun getSessions(): Flow<List<PaintSession>> = localDataSource.getSessions()

    override fun getSession(sessionId: String): Flow<PaintSession?> = localDataSource.getSession(sessionId)

    override suspend fun createSession(session: PaintSession): String = localDataSource.createSession(session)

    override suspend fun updateSession(session: PaintSession) = localDataSource.updateSession(session)

    override suspend fun deleteSession(sessionId: String) = localDataSource.deleteSession(sessionId)

    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = localDataSource.getMessages(sessionId)

    override fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>> =
        localDataSource.getMessagesPaged(sessionId, limit, offset)

    override suspend fun addMessage(message: PaintMessage) = localDataSource.addMessage(message)

    override suspend fun updateMessage(message: PaintMessage) = localDataSource.updateMessage(message)

    override suspend fun deleteMessage(messageId: String) = localDataSource.deleteMessage(messageId)

    override suspend fun getMessageCount(sessionId: String): Int = localDataSource.getMessageCount(sessionId)

    override suspend fun getMessage(messageId: String): PaintMessage? = localDataSource.getMessage(messageId)

    override suspend fun getMessagesByVersionGroup(sessionId: String, versionGroup: String): List<PaintMessage> =
        localDataSource.getMessagesByVersionGroup(sessionId, versionGroup)

    override suspend fun getVersionCount(sessionId: String, versionGroup: String): Int =
        localDataSource.getVersionCount(sessionId, versionGroup)

    // ========== API配置管理 ==========
    
    override fun getApiProfiles(): Flow<List<ApiProfile>> = callbackFlow {
        val listener = settings.addStringListener(KEY_API_PROFILES, "") { jsonString ->
            launch(dispatchers.computation) {
                trySend(parseProfiles(jsonString))
            }
        }
        launch(dispatchers.computation) {
            trySend(parseProfiles(settings.getString(KEY_API_PROFILES, "")))
        }
        awaitClose { listener.deactivate() }
    }

    override fun getActiveProfile(): Flow<ApiProfile?> = callbackFlow {
        val profilesListener = settings.addStringListener(KEY_API_PROFILES, "") { _ ->
            launch(dispatchers.computation) {
                trySend(findActiveProfile())
            }
        }
        val activeListener = settings.addStringOrNullListener(KEY_ACTIVE_PROFILE) { _ ->
            launch(dispatchers.computation) {
                trySend(findActiveProfile())
            }
        }
        launch(dispatchers.computation) {
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
        withContext(dispatchers.computation) {
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
        withContext(dispatchers.computation) {
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
        withContext(dispatchers.computation) {
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
        images: List<ImageRequestPayload>,
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
                        response = responseResult.data,
                        sessionId = sessionId,
                        messageId = messageId,
                        fileNamePrefix = buildGeminiImageFileNamePrefix(model, aspectRatio, resolution)
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
        model: PaintModel,
        prompt: String,
        images: List<ImageRequestPayload>,
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
                model = model,
                prompt = prompt,
                images = images,
                size = size,
                quality = quality,
                outputFormat = outputFormat
            )
        } else {
            gptApiService.generateImage(
                profile = profile,
                model = model,
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
                        response = responseResult.data,
                        sessionId = sessionId,
                        messageId = messageId,
                        outputFormat = outputFormat,
                        fileNamePrefix = buildGptImageFileNamePrefix(model, size, quality, outputFormat)
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

    /** Builds a readable, filesystem-safe prefix for a Gemini-generated image file. */
    private fun buildGeminiImageFileNamePrefix(
        model: PaintModel,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): String = listOf(
        model.endpoint,
        "ratio-${aspectRatio.value.replace(':', 'x')}",
        "resolution-${resolution.value.lowercase()}",
        "format-png"
    ).joinToString("_")

    /** Builds a readable, filesystem-safe prefix for a GPT-generated image file. */
    private fun buildGptImageFileNamePrefix(
        model: PaintModel,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat
    ): String = listOf(
        model.endpoint,
        "size-${size.value}",
        "quality-${quality.value}",
        "format-${outputFormat.value}"
    ).joinToString("_")
    
    private fun parseProfiles(jsonString: String): List<ApiProfile> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCurrentProfiles(): List<ApiProfile> =
        parseProfiles(settings.getString(KEY_API_PROFILES, ""))

    private fun findActiveProfile(): ApiProfile? {
        val activeId = settings.getStringOrNull(KEY_ACTIVE_PROFILE)
        return getCurrentProfiles().find { it.id == activeId }
    }

    private fun saveProfiles(profiles: List<ApiProfile>) {
        settings[KEY_API_PROFILES] = json.encodeToString(profiles)
    }
}
