package com.example.livewallpaper.feature.dynamicwallpaper.presentation.state

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibrarySummary

/**
 * 壁纸设置界面状态。
 *
 * @param config 全局设置与激活库投影（轮播引擎使用）
 * @param libraries 库摘要列表
 * @param activeLibraryId 当前激活库
 * @param libraryImages 各库的图片 URI 列表，供库内浏览使用
 * @param allImageCropParams 全部图片的裁剪参数
 */
data class SettingsUiState(
    val config: WallpaperConfig = WallpaperConfig(),
    val libraries: List<WallpaperLibrarySummary> = emptyList(),
    val activeLibraryId: String = "",
    val libraryImages: Map<String, List<String>> = emptyMap(),
    val allImageCropParams: Map<String, ImageCropParams> = emptyMap(),
    val isLoading: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.Idle
) {
    val activeLibraryName: String
        get() = libraries.firstOrNull { it.id == activeLibraryId }?.name.orEmpty()

    val canDeleteLibrary: Boolean
        get() = libraries.size > 1

    /**
     * 获取指定库的图片列表。
     *
     * @param libraryId 库 ID
     * @return 库内图片 URI，库不存在时为空
     */
    fun imagesOf(libraryId: String): List<String> = libraryImages[libraryId].orEmpty()
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Success(
        val hasNewVersion: Boolean,
        val version: String?,
        val desc: String?,
        val downloadUrl: String?
    ) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
