package com.example.livewallpaper.feature.aipaint.presentation.state

/**
 * 请求会话列表滚动到指定消息并使其居中，用于搜索结果跳转。
 *
 * @param messageId 目标消息 id；UI 需等该消息完成布局后再滚动。
 * @param animate 目标已在附近时是否使用动画对齐。
 */
data class ScrollToMessageRequest(
    val messageId: String,
    val animate: Boolean = true,
)
