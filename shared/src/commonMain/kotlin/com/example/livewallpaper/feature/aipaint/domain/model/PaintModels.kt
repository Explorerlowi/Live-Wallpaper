package com.example.livewallpaper.feature.aipaint.domain.model

import kotlinx.serialization.Serializable

/**
 * AI绘画消息
 */
@Serializable
data class PaintMessage(
    val id: String,
    val sessionId: String,
    val senderIdentity: SenderIdentity,
    val messageContent: String,
    val reasoningContent: String? = null,
    val messageType: MessageType,
    val images: List<PaintImage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SUCCESS
)

@Serializable
enum class SenderIdentity {
    USER, ASSISTANT
}

@Serializable
enum class MessageType {
    TEXT, IMAGE
}

@Serializable
enum class MessageStatus {
    PENDING, GENERATING, SUCCESS, ERROR
}

/**
 * 图片信息
 */
@Serializable
data class PaintImage(
    val id: String,
    val localPath: String? = null,
    val base64Data: String? = null,
    val mimeType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0,
    val isReference: Boolean = false // 是否为参考图
)

/**
 * 绘画会话
 */
@Serializable
data class PaintSession(
    val id: String,
    val title: String = "新会话",
    val model: PaintModel = PaintModel.GEMINI_2_5_FLASH,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_1_1,
    val resolution: Resolution = Resolution.RES_1K,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 绘画模型
 */
@Serializable
enum class PaintModel(val displayName: String, val endpoint: String) {
    GEMINI_2_5_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash-image"),
    GEMINI_3_PRO("Gemini 3 Pro", "gemini-3-pro-image-preview")
}

/**
 * 宽高比
 */
@Serializable
enum class AspectRatio(
    val displayName: String, 
    val value: String,
    val description: String
) {
    RATIO_1_1("1:1", "1:1", "正方形"),
    RATIO_2_3("2:3", "2:3", "肖像"),
    RATIO_3_2("3:2", "3:2", "风景"),
    RATIO_3_4("3:4", "3:4", "标准竖版"),
    RATIO_4_3("4:3", "4:3", "标准横版"),
    RATIO_16_9("16:9", "16:9", "宽屏"),
    RATIO_9_16("9:16", "9:16", "手机竖屏")
}

/**
 * 分辨率 (仅Pro模型支持)
 */
@Serializable
enum class Resolution(val displayName: String, val value: String) {
    RES_1K("1K", "1K"),
    RES_2K("2K", "2K"),
    RES_4K("4K", "4K")
}

/**
 * API配置
 */
@Serializable
data class ApiProfile(
    val id: String,
    val name: String,
    val baseUrl: String = "https://yunwu.ai",
    val token: String,
    val authMode: AuthMode = AuthMode.BEARER
)

@Serializable
enum class AuthMode {
    BEARER, OFFICIAL
}
