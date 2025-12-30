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
    
    // AI 绘画功能
    suspend fun generateImage(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): AppResult<String>
    
    suspend fun enhancePrompt(
        profile: ApiProfile,
        prompt: String
    ): AppResult<String>
}
