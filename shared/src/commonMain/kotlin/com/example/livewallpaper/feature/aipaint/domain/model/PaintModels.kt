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
    val versionIndex: Int = 0,                // 当前版本索引（从0开始）
    
    // 生成参数记录（仅AI消息有）
    val generationModel: PaintModel? = null,        // 生成时使用的模型
    val generationAspectRatio: AspectRatio? = null,  // 生成时使用的比例
    val generationResolution: Resolution? = null,    // 生成时使用的分辨率（Gemini）
    val generationGptSize: GptImageSize? = null,     // 生成时使用的尺寸（GPT）
    val generationGptQuality: GptImageQuality? = null, // 生成时使用的质量（GPT）
    val generationGptFormat: GptOutputFormat? = null  // 生成时使用的输出格式（GPT）
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
    PENDING, GENERATING, SUCCESS, ERROR, CANCELLED
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
    val gptImageSize: GptImageSize = GptImageSize.AUTO,
    val gptImageQuality: GptImageQuality = GptImageQuality.AUTO,
    val gptOutputFormat: GptOutputFormat = GptOutputFormat.PNG,
    val createdAt: Long = TimeProvider.currentTimeMillis(),
    val updatedAt: Long = TimeProvider.currentTimeMillis()
)

/**
 * 模型提供商
 */
@Serializable
enum class ModelProvider {
    GEMINI, GPT
}

/**
 * GPT 图片尺寸
 *
 * gpt-image-2 支持任意分辨率，但需满足：
 * - 最大边长 ≤ 3840px
 * - 两边均为 16 的倍数
 * - 长短边比例 ≤ 3:1
 * - 总像素 655,360 ~ 8,294,400
 *
 * 此处列出常用预设尺寸。
 */
@Serializable
enum class GptImageSize(val displayName: String, val value: String, val description: String) {
    AUTO("auto", "auto", "auto"),
    SIZE_1024x1024("1024×1024（1:1）", "1024x1024", "1K square"),
    SIZE_1536x1024("1536×1024（3:2）", "1536x1024", "1K landscape"),
    SIZE_1024x1536("1024×1536（2:3）", "1024x1536", "1K portrait"),
    SIZE_2048x2048("2048×2048（1:1）", "2048x2048", "2K square"),
    SIZE_2048x1152("2048×1152（16:9）", "2048x1152", "2K landscape"),
    SIZE_1152x2048("1152×2048（9:16）", "1152x2048", "2K portrait"),
    SIZE_3840x2160("3840×2160（16:9）", "3840x2160", "4K landscape"),
    SIZE_2160x3840("2160×3840（9:16）", "2160x3840", "4K portrait");

    /**
     * 获取数值比例
     */
    fun toFloat(): Float = when (this) {
        AUTO -> 1f
        SIZE_1024x1024 -> 1f
        SIZE_1536x1024 -> 1536f / 1024f
        SIZE_1024x1536 -> 1024f / 1536f
        SIZE_2048x2048 -> 1f
        SIZE_2048x1152 -> 2048f / 1152f
        SIZE_1152x2048 -> 1152f / 2048f
        SIZE_3840x2160 -> 3840f / 2160f
        SIZE_2160x3840 -> 2160f / 3840f
    }

    companion object {
        /**
         * 根据 AspectRatio 推荐最接近的 GPT 尺寸
         */
        fun fromAspectRatio(ratio: AspectRatio): GptImageSize {
            val r = ratio.toFloat()
            return when {
                r > 1.2f -> SIZE_1536x1024  // 横版
                r < 0.8f -> SIZE_1024x1536  // 竖版
                else -> SIZE_1024x1024       // 正方形
            }
        }
    }
}

/**
 * GPT 图片质量
 *
 * - low: 快速草稿、缩略图、快速迭代
 * - medium: 常规用途
 * - high: 最终成品
 * - auto: 模型根据 prompt 自动选择
 */
@Serializable
enum class GptImageQuality(val displayName: String, val value: String) {
    AUTO("auto", "auto"),
    LOW("low", "low"),
    MEDIUM("medium", "medium"),
    HIGH("high", "high")
}

/**
 * GPT 输出格式
 *
 * - png: 默认，无损，支持透明（但 gpt-image-2 不支持透明背景）
 * - jpeg: 比 png 更快，适合对延迟敏感的场景
 * - webp: 体积小，支持压缩
 */
@Serializable
enum class GptOutputFormat(val displayName: String, val value: String) {
    PNG("PNG", "png"),
    JPEG("JPEG", "jpeg"),
    WEBP("WebP", "webp")
}

/**
 * 绘画模型
 */
@Serializable
enum class PaintModel(val displayName: String, val endpoint: String, val maxImages: Int) {
    GEMINI_2_5_FLASH("nano banana", "gemini-2.5-flash-image", 3),
    GEMINI_3_PRO("nano banana pro", "gemini-3-pro-image-preview", 14),
    GEMINI_3_1_FLASH("nano banana 2", "gemini-3.1-flash-image-preview", 14),
    GPT_IMAGE_2("gpt image 2", "gpt-image-2", 16);

    /** 模型提供商 */
    val provider: ModelProvider
        get() = when (this) {
            GPT_IMAGE_2 -> ModelProvider.GPT
            else -> ModelProvider.GEMINI
        }

    /** 是否为 GPT 模型 */
    val isGpt: Boolean get() = provider == ModelProvider.GPT

    /** 是否支持自定义分辨率（Gemini 专属） */
    val supportsResolution: Boolean
        get() = this == GEMINI_3_PRO || this == GEMINI_3_1_FLASH

    /** 是否支持 GPT 尺寸选择 */
    val supportsGptSize: Boolean get() = isGpt

    /** 是否支持 GPT 质量选择 */
    val supportsGptQuality: Boolean get() = isGpt
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
    RATIO_4_5("4:5", "4:5", "社交竖版"),
    RATIO_5_4("5:4", "5:4", "社交横版"),
    RATIO_16_9("16:9", "16:9", "宽屏"),
    RATIO_9_16("9:16", "9:16", "手机竖屏"),
    RATIO_1_4("1:4", "1:4", "超长竖版"),
    RATIO_4_1("4:1", "4:1", "超长横版"),
    RATIO_1_8("1:8", "1:8", "极长竖版"),
    RATIO_8_1("8:1", "8:1", "极长横版");

    /** 仅 Gemini 3.1 Flash 支持的极端宽高比 */
    val isExtreme: Boolean
        get() = this in listOf(RATIO_1_4, RATIO_4_1, RATIO_1_8, RATIO_8_1)
    
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
         * 获取指定模型可用的宽高比列表
         * - 极端比例（1:4、4:1、1:8、8:1）仅 Gemini 3.1 Flash 支持
         * - GPT 模型使用 GptImageSize，此处返回可映射的比例子集
         */
        fun availableFor(model: PaintModel): List<AspectRatio> = when (model) {
            PaintModel.GEMINI_3_1_FLASH -> entries.toList()
            PaintModel.GPT_IMAGE_2 -> listOf(RATIO_1_1, RATIO_3_2, RATIO_2_3)
            else -> entries.filter { !it.isExtreme }
        }

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
 * 分辨率（Pro 和 3.1 Flash 模型支持）
 */
@Serializable
enum class Resolution(val displayName: String, val value: String) {
    RES_05K("0.5K", "0.5K"),
    RES_1K("1K", "1K"),
    RES_2K("2K", "2K"),
    RES_4K("4K", "4K");

    companion object {
        /** 获取指定模型可用的分辨率列表 */
        fun availableFor(model: PaintModel): List<Resolution> = when (model) {
            PaintModel.GEMINI_3_1_FLASH -> entries.toList()          // 0.5K ~ 4K
            PaintModel.GEMINI_3_PRO -> listOf(RES_1K, RES_2K, RES_4K) // 1K ~ 4K
            else -> emptyList()
        }
    }
}

/**
 * 生成图片的文件信息
 * 由 ImageResponseProcessor 流式处理后返回
 */
data class GeneratedImageFile(
    val filePath: String,
    val width: Int,
    val height: Int
)

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
