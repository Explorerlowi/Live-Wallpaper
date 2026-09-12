package com.example.livewallpaper.feature.dynamicwallpaper.domain.repository

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibraryDocument
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun getConfig(): Flow<WallpaperConfig>
    /** 同步获取当前配置，用于初始化 UI 状态 */
    fun getConfigSync(): WallpaperConfig
    /** 观察壁纸库文档，供库切换与管理界面使用 */
    fun getLibraryDocument(): Flow<WallpaperLibraryDocument>
    /** 同步获取壁纸库文档 */
    fun getLibraryDocumentSync(): WallpaperLibraryDocument
    suspend fun updateConfig(config: WallpaperConfig)
    /** 将图片加入当前激活库；已存在的 URI 只增加引用 */
    suspend fun addImages(uris: List<String>)
    /** 将图片加入指定库，用于跨库共享 */
    suspend fun addImagesToLibrary(libraryId: String, uris: List<String>)
    suspend fun removeImage(uri: String)
    suspend fun removeImages(uris: List<String>)
    /** 从指定库移除图片；不再被任何库引用的图片会被删除 */
    suspend fun removeImagesFromLibrary(libraryId: String, uris: List<String>)
    suspend fun removeAllImages()
    suspend fun updateImageOrder(uris: List<String>)
    /** 调整指定库内的图片顺序 */
    suspend fun updateLibraryImageOrder(libraryId: String, uris: List<String>)
    suspend fun setInterval(interval: Long)
    suspend fun setScaleMode(mode: ScaleMode)
    suspend fun setPlayMode(mode: PlayMode)
    suspend fun setImageCropParams(uri: String, params: ImageCropParams)
    /** 新建壁纸库并切换为激活库 */
    suspend fun createLibrary(name: String)
    suspend fun renameLibrary(libraryId: String, name: String)
    suspend fun deleteLibrary(libraryId: String)
    /** 切换当前轮播使用的壁纸库 */
    suspend fun setActiveLibrary(libraryId: String)
    suspend fun setLanguage(languageTag: String?)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLaunchAtStartup(enabled: Boolean)
    suspend fun setRestoreSlideshowOnLaunch(enabled: Boolean)
    suspend fun setPaintGenerationSuccessNotification(enabled: Boolean)
    suspend fun checkAppUpdate(apiKey: String, appKey: String, buildVersion: String?, buildBuildVersion: Int?): com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model.PgyerResponse
}
