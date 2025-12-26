package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.*

data class PaintUiState(
    val currentSession: PaintSession? = null,
    val sessions: List<PaintSession> = emptyList(),
    val messages: List<PaintMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val generatingSessionId: String? = null, // 正在生成的会话ID
    val generationStartTime: Long = 0L, // 生成开始时间
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
    
    // 分页状态
    val hasMoreMessages: Boolean = true,
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    
    // 新消息提示
    val newMessageCount: Int = 0,
    val isAtBottom: Boolean = true
)

/**
 * 用户选择的图片（用于上传）
 */
data class SelectedImage(
    val id: String,
    val uri: String,
    val mimeType: String = "image/png"
)
