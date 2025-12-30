package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.*

sealed class PaintEvent {
    // 会话操作
    data class CreateSession(val model: PaintModel = PaintModel.GEMINI_2_5_FLASH) : PaintEvent()
    data class SelectSession(val sessionId: String) : PaintEvent()
    data class DeleteSession(val sessionId: String) : PaintEvent()
    
    // 消息操作
    data object SendMessage : PaintEvent()
    data object StopGeneration : PaintEvent()
    data object LoadMoreMessages : PaintEvent()
    data class DeleteMessage(val messageId: String) : PaintEvent()
    data class DeleteMessageVersion(val versionGroup: String) : PaintEvent()  // 删除整个版本组
    data class UpdateImageDimensions(
        val messageId: String,
        val imageId: String,
        val width: Int,
        val height: Int
    ) : PaintEvent()
    
    // 重新生成与版本切换
    data class RegenerateMessage(val messageId: String) : PaintEvent()
    data class SwitchMessageVersion(val versionGroup: String, val targetIndex: Int) : PaintEvent()
    
    // 输入操作
    data class UpdatePrompt(val text: String) : PaintEvent()
    data class AddImage(val image: SelectedImage) : PaintEvent()
    data class RemoveImage(val imageId: String) : PaintEvent()
    data object ClearImages : PaintEvent()
    
    // 设置操作
    data class SelectModel(val model: PaintModel) : PaintEvent()
    data class SelectAspectRatio(val ratio: AspectRatio) : PaintEvent()
    data class SelectResolution(val resolution: Resolution) : PaintEvent()
    
    // API配置操作
    data class SaveApiProfile(val profile: ApiProfile) : PaintEvent()
    data class DeleteApiProfile(val profileId: String) : PaintEvent()
    data class SetActiveProfile(val profileId: String) : PaintEvent()
    
    // 提示词优化
    data object EnhancePrompt : PaintEvent()
    
    // 滚动状态
    data class UpdateScrollState(val isAtBottom: Boolean) : PaintEvent()
    data object ScrollToBottom : PaintEvent()
    data object ClearNewMessageCount : PaintEvent()
    
    // 错误处理
    data object ClearError : PaintEvent()
}
