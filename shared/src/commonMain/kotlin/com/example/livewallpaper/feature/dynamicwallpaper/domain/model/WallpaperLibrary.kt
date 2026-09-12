package com.example.livewallpaper.feature.dynamicwallpaper.domain.model

import kotlinx.serialization.Serializable

/**
 * 壁纸库中的一张图片。同一 URI 全局只对应一个条目，可被多个库引用。
 *
 * @param id 条目标识
 * @param uri 平台图片地址（Android 为 content URI，Desktop 为本地路径）
 * @param cropParams 裁剪参数，各库共享
 * @param tags 预留标签，供后续 AI 打标与心情选图使用
 */
@Serializable
data class WallpaperItem(
    val id: String,
    val uri: String,
    val cropParams: ImageCropParams = ImageCropParams(),
    val tags: List<String> = emptyList()
)

/**
 * 用户自定义命名的壁纸库。
 *
 * @param id 库标识
 * @param name 展示名称
 * @param itemIds 库内图片顺序，元素为 [WallpaperItem.id]
 */
@Serializable
data class WallpaperLibrary(
    val id: String,
    val name: String,
    val itemIds: List<String> = emptyList()
)

/**
 * 多壁纸库持久化文档。
 *
 * @param version 文档版本，便于后续迁移
 * @param activeLibraryId 当前激活库，轮播只播放该库
 * @param libraries 全部壁纸库
 * @param items 图片条目表，按 URI 去重
 */
@Serializable
data class WallpaperLibraryDocument(
    val version: Int = 1,
    val activeLibraryId: String,
    val libraries: List<WallpaperLibrary>,
    val items: List<WallpaperItem> = emptyList()
)

/**
 * 供 UI 展示的壁纸库摘要。
 *
 * @param id 库标识
 * @param name 展示名称
 * @param imageCount 库内图片数量
 * @param isActive 是否为当前激活库
 */
data class WallpaperLibrarySummary(
    val id: String,
    val name: String,
    val imageCount: Int,
    val isActive: Boolean
)

/**
 * 根据系统/应用语言提供默认库名称。
 * 名称会写入用户数据，之后可被重命名，不属于运行时 UI 文案。
 */
object WallpaperLibraryNames {
    /**
     * 返回迁移或全新安装时使用的默认库名。
     *
     * @param languageTag BCP 47 语言标签，`null` 时按英文处理
     * @return 中文环境为「默认」，其余为 `Default`
     */
    fun defaultName(languageTag: String?): String {
        val tag = languageTag?.lowercase().orEmpty()
        return if (tag.startsWith("zh")) "默认" else "Default"
    }
}
