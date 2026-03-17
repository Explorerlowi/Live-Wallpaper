package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.*

data class PaintUiState(
    val currentSession: PaintSession? = null,
    val sessions: List<PaintSession> = emptyList(),
    val messages: List<PaintMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false, // 是否有任何任务在生成中（便捷属性）
    val generatingSessionId: String? = null, // 当前会话是否在生成中（便捷属性，仅当前会话）
    val generationStartTime: Long = 0L, // 最早的生成开始时间
    val generatingMessageIds: Set<String> = emptySet(), // 所有正在生成的消息ID
    val generatingSessionCounts: Map<String, Int> = emptyMap(), // 每个会话正在生成的任务数
    val error: String? = null,
    
    // 输入状态
    val promptText: String = "",
    val selectedImages: List<SelectedImage> = emptyList(),
    
    // 设置状态
    val selectedModel: PaintModel = PaintModel.GEMINI_2_5_FLASH,
    val selectedAspectRatio: AspectRatio = AspectRatio.RATIO_1_1,
    val selectedResolution: Resolution = Resolution.RES_1K,
    
    // API配置
    val apiProfiles: List<ApiProfile> = emptyList(),
    val activeProfile: ApiProfile? = null,
    val isApiProfileLoaded: Boolean = true, // 默认为 true，因为同步获取
    
    // 分页状态
    val hasMoreMessages: Boolean = true,
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    
    // 新消息提示
    val newMessageCount: Int = 0,
    val isAtBottom: Boolean = true,
    
    // 版本管理：当前显示的版本映射 (versionGroup -> 当前显示的versionIndex)
    val activeVersions: Map<String, Int> = emptyMap()
)

/**
 * 用户选择的图片（用于上传）
 */
data class SelectedImage(
    val id: String,
    val uri: String,
    val mimeType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0
)
