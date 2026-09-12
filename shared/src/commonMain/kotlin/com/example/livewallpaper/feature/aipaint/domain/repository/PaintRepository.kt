package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.domain.model.*
import kotlinx.coroutines.flow.Flow

interface PaintRepository {
    // 会话管理
    fun getSessions(): Flow<List<PaintSession>>
    fun getSession(sessionId: String): Flow<PaintSession?>
    suspend fun createSession(session: PaintSession): String
    suspend fun updateSession(session: PaintSession)
    suspend fun deleteSession(sessionId: String)
    
    // 消息管理
    fun getMessages(sessionId: String): Flow<List<PaintMessage>>
    fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>>
    suspend fun addMessage(message: PaintMessage)
    suspend fun updateMessage(message: PaintMessage)
    suspend fun deleteMessage(messageId: String)
    suspend fun getMessageCount(sessionId: String): Int
    suspend fun getMessage(messageId: String): PaintMessage?
    
    // 版本管理
    suspend fun getMessagesByVersionGroup(sessionId: String, versionGroup: String): List<PaintMessage>
    suspend fun getVersionCount(sessionId: String, versionGroup: String): Int
    
    // API配置管理
    fun getApiProfiles(): Flow<List<ApiProfile>>
    fun getActiveProfile(): Flow<ApiProfile?>
    fun getApiProfilesSync(): List<ApiProfile>
    fun getActiveProfileSync(): ApiProfile?
    suspend fun saveApiProfile(profile: ApiProfile)
    suspend fun deleteApiProfile(profileId: String)
    suspend fun setActiveProfile(profileId: String)

    /**
     * 导出全部绘画 API 配置及当前启用项。
     *
     * @return 包含访问令牌的 JSON 备份内容。
     */
    fun exportApiProfilesJson(): String

    /**
     * 从 JSON 备份合并绘画 API 配置；相同 ID 的配置会被覆盖，其余现有配置保留。
     *
     * @param content JSON 备份内容。
     * @return 导入数量、合并后总数，或不修改现有配置的校验错误。
     */
    suspend fun importApiProfilesJson(content: String): ApiProfileImportResult
    
    // AI 绘画功能
    suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<ImageRequestPayload>,
        aspectRatio: AspectRatio,
        resolution: Resolution,
        sessionId: String,
        messageId: String
    ): AppResult<List<GeneratedImageFile>>

    /**
     * GPT 图片生成/编辑
     * 无参考图时调用生成接口，有参考图时调用编辑接口
     */
    suspend fun generateGptImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<ImageRequestPayload>,
        size: GptImageSize,
        quality: GptImageQuality,
        outputFormat: GptOutputFormat,
        sessionId: String,
        messageId: String
    ): AppResult<List<GeneratedImageFile>>
    
}
