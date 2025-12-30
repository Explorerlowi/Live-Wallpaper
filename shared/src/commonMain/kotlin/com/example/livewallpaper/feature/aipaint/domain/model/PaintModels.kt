package com.example.livewallpaper.feature.aipaint.domain.model

import com.example.livewallpaper.core.util.TimeProvider
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
    val createdAt: Long = TimeProvider.currentTimeMillis(),
    val updatedAt: Long = TimeProvider.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SUCCESS,
    
    // 版本管理字段（用于重新生成功能）
    val parentUserMessageId: String? = null,  // 关联的用户消息ID（仅AI消息有）
    val versionGroup: String? = null,         // 版本组ID（同一组的消息共享此ID）
    val versionIndex: Int = 0                 // 当前版本索引（从0开始）
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
    val createdAt: Long = TimeProvider.currentTimeMillis(),
    val updatedAt: Long = TimeProvider.currentTimeMillis()
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
    RATIO_9_16("9:16", "9:16", "手机竖屏");
    
    /**
     * 获取数值比例
     */
    fun toFloat(): Float {
        val parts = value.split(":")
        val w = parts[0].toFloatOrNull() ?: 1f
        val h = parts[1].toFloatOrNull() ?: 1f
        return w / h
    }
    
    companion object {
        /**
         * 根据图片宽高计算最接近的比例
         * @param width 图片宽度
         * @param height 图片高度
         * @return 最接近的 AspectRatio，如果无法计算则返回 null
         */
        fun findClosest(width: Int, height: Int): AspectRatio? {
            if (width <= 0 || height <= 0) return null
            
            val imageRatio = width.toFloat() / height.toFloat()
            
            return entries.minByOrNull { ratio ->
                kotlin.math.abs(ratio.toFloat() - imageRatio)
            }
        }
    }
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
