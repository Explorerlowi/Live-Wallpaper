package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.*

sealed class PaintEvent {
    // 会话操作
    data class CreateSession(val model: PaintModel = PaintModel.GEMINI_2_5_FLASH) : PaintEvent()
    data class SelectSession(val sessionId: String) : PaintEvent()
    data class DeleteSession(val sessionId: String) : PaintEvent()
    data class RenameSession(val sessionId: String, val newTitle: String) : PaintEvent()
    data class PinSession(val sessionId: String) : PaintEvent()
    data class UnpinSession(val sessionId: String) : PaintEvent()
    
    // 消息操作
    data object SendMessage : PaintEvent()
    data object StopGeneration : PaintEvent()
    data class CancelGeneration(val messageId: String) : PaintEvent()
    data class DismissGenerationTask(val messageId: String) : PaintEvent()
    data object ClearGenerationTaskHistory : PaintEvent()
    data object LoadMoreMessages : PaintEvent()
    data class DeleteMessage(val messageId: String) : PaintEvent()
    data class DeleteMessageVersion(val versionGroup: String) : PaintEvent()  // 删除整个版本组

    /**
     * 删除当前会话中选中的消息。
     *
     * 正在生成的消息不会删除。选中某个已完成版本时，同一版本组里其余已完成的版本会一起删除。
     *
     * @property messageIds 要删除的消息 ID。
     */
    data class DeleteMessages(val messageIds: List<String>) : PaintEvent()

    /**
     * 把选中的消息移动到另一个会话。
     *
     * 正在生成的消息会被忽略。图片文件保留原路径。
     *
     * @property messageIds 要移动的消息 ID。
     * @property targetSessionId 目标会话 ID。
     */
    data class MoveMessages(
        val messageIds: List<String>,
        val targetSessionId: String,
    ) : PaintEvent()

    /**
     * 把选中的消息复制到另一个会话，当前会话中的原消息保留。
     *
     * 正在生成的消息会被忽略。副本使用新的消息 ID，图片仍指向原文件。
     *
     * @property messageIds 要复制的消息 ID。
     * @property targetSessionId 目标会话 ID。
     */
    data class CopyMessages(
        val messageIds: List<String>,
        val targetSessionId: String,
    ) : PaintEvent()
    data class EditUserMessage(val messageId: String) : PaintEvent()  // 编辑用户消息（回填到输入框）
    data class UpdateImageDimensions(
        val messageId: String,
        val imageId: String,
        val width: Int,
        val height: Int
    ) : PaintEvent()
    data class ReplaceImagePath(val oldPath: String, val newPath: String) : PaintEvent()
    
    // 重新生成与版本切换
    data class RegenerateMessage(val messageId: String) : PaintEvent()
    data class SwitchMessageVersion(val versionGroup: String, val targetIndex: Int) : PaintEvent()
    
    // 输入操作
    data class UpdatePrompt(val text: String) : PaintEvent()
    data class AddImage(val image: SelectedImage) : PaintEvent()
    data class RemoveImage(val imageId: String) : PaintEvent()
    data class ReorderImages(val images: List<SelectedImage>) : PaintEvent()
    data object ClearImages : PaintEvent()
    
    // 设置操作
    data class SelectModel(val model: PaintModel) : PaintEvent()
    data class SelectAspectRatio(val ratio: AspectRatio) : PaintEvent()
    data class SelectResolution(val resolution: Resolution) : PaintEvent()
    data class SelectGptSize(val size: GptImageSize) : PaintEvent()
    data class SelectGptQuality(val quality: GptImageQuality) : PaintEvent()
    data class SelectGptFormat(val format: GptOutputFormat) : PaintEvent()
    
    // API配置操作
    data class SaveApiProfile(val profile: ApiProfile) : PaintEvent()
    data class DeleteApiProfile(val profileId: String) : PaintEvent()
    data class SetActiveProfile(val profileId: String) : PaintEvent()
    
    // 滚动状态
    data class UpdateScrollState(val isAtBottom: Boolean) : PaintEvent()
    data object ScrollToBottom : PaintEvent()
    data object ClearNewMessageCount : PaintEvent()

    // 当前会话内关键字搜索
    data object OpenMessageSearch : PaintEvent()
    data object CloseMessageSearch : PaintEvent()
    data class UpdateMessageSearchQuery(val query: String) : PaintEvent()
    data class StepMessageSearch(val forward: Boolean) : PaintEvent()  // forward=true 走向更新的命中
    data class JumpToMessage(val messageId: String) : PaintEvent()
    
    // 错误处理
    data object ClearError : PaintEvent()
}

/** Returns whether this event only changes navigation or in-memory presentation state. */
fun PaintEvent.isAllowedWhenStorageReadOnly(): Boolean = when (this) {
    is PaintEvent.SelectSession,
    PaintEvent.LoadMoreMessages,
    is PaintEvent.SwitchMessageVersion,
    PaintEvent.StopGeneration,
    is PaintEvent.CancelGeneration,
    is PaintEvent.DismissGenerationTask,
    PaintEvent.ClearGenerationTaskHistory,
    is PaintEvent.UpdateScrollState,
    PaintEvent.ScrollToBottom,
    PaintEvent.ClearNewMessageCount,
    PaintEvent.OpenMessageSearch,
    PaintEvent.CloseMessageSearch,
    is PaintEvent.UpdateMessageSearchQuery,
    is PaintEvent.StepMessageSearch,
    is PaintEvent.JumpToMessage,
    PaintEvent.ClearError,
    -> true
    else -> false
}
