package com.example.livewallpaper.feature.dynamicwallpaper.domain.model

import kotlinx.serialization.Serializable

/**
 * 图片裁剪参数
 * @param scale 缩放比例，1.0 为原始大小
 * @param offsetX 水平偏移量（相对于图片宽度的比例，-0.5 到 0.5）
 * @param offsetY 垂直偏移量（相对于图片高度的比例，-0.5 到 0.5）
 */
@Serializable
data class ImageCropParams(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

@Serializable
data class WallpaperConfig(
    val interval: Long = 10000L, // Default 10 seconds
    val imageUris: List<String> = emptyList(),
    val scaleMode: ScaleMode = ScaleMode.CENTER_CROP,
    val imageCropParams: Map<String, ImageCropParams> = emptyMap(),
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val languageTag: String? = null
)

/**
 * 缩放模式
 */
@Serializable
enum class ScaleMode {
    /** 填充模式：裁剪图片以填满屏幕 */
    CENTER_CROP,
    /** 适应模式：保持图片完整显示 */
    FIT_CENTER
}

/**
 * 播放模式
 */
@Serializable
enum class PlayMode {
    /** 顺序播放 */
    SEQUENTIAL,
    /** 随机播放 */
    RANDOM
}

