package com.example.livewallpaper.feature.dynamicwallpaper.data.repository

import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.AppUpdateService
import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model.PgyerResponse
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WallpaperRepositoryImpl(
    private val settings: ObservableSettings,
    private val appUpdateService: AppUpdateService
) : WallpaperRepository {

    private val key = "WALLPAPER_CONFIG"
    private val json = Json { ignoreUnknownKeys = true }

    override fun getConfig(): Flow<WallpaperConfig> = callbackFlow {
        val listener = settings.addStringListener(key, "") { jsonString ->
            trySend(parseConfig(jsonString))
        }
        // Send initial value
        trySend(parseConfig(settings.getString(key, "")))
        
        awaitClose {
            listener.deactivate()
        }
    }

    private fun parseConfig(jsonString: String): WallpaperConfig {
        if (jsonString.isBlank()) return WallpaperConfig()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            WallpaperConfig()
        }
    }

    override suspend fun updateConfig(config: WallpaperConfig) {
        val jsonString = json.encodeToString(config)
        settings[key] = jsonString
    }

    override suspend fun addImages(uris: List<String>) {
        val current = getCurrentConfig()
        val newImages = current.imageUris + uris
        updateConfig(current.copy(imageUris = newImages.distinct()))
    }

    override suspend fun removeImage(uri: String) {
        val current = getCurrentConfig()
        val newImages = current.imageUris - uri
        // 同时移除对应的裁剪参数
        val newCropParams = current.imageCropParams - uri
        updateConfig(current.copy(imageUris = newImages, imageCropParams = newCropParams))
    }

    override suspend fun removeImages(uris: List<String>) {
        val current = getCurrentConfig()
        val uriSet = uris.toSet()
        val newImages = current.imageUris.filterNot { it in uriSet }
        // 同时移除对应的裁剪参数
        val newCropParams = current.imageCropParams.filterKeys { it !in uriSet }
        updateConfig(current.copy(imageUris = newImages, imageCropParams = newCropParams))
    }

    override suspend fun removeAllImages() {
        val current = getCurrentConfig()
        updateConfig(current.copy(imageUris = emptyList(), imageCropParams = emptyMap()))
    }

    override suspend fun updateImageOrder(uris: List<String>) {
        val current = getCurrentConfig()
        if (current.imageUris.isEmpty()) return

        val desiredOrder = uris.distinct().filter { it in current.imageUris.toSet() }
        if (desiredOrder.isEmpty()) return

        val desiredSet = desiredOrder.toSet()
        val remaining = current.imageUris.filter { it !in desiredSet }
        val newOrder = desiredOrder + remaining

        if (newOrder != current.imageUris) {
            updateConfig(current.copy(imageUris = newOrder))
        }
    }

    override suspend fun setInterval(interval: Long) {
        val current = getCurrentConfig()
        updateConfig(current.copy(interval = interval))
    }

    override suspend fun setScaleMode(mode: ScaleMode) {
        val current = getCurrentConfig()
        updateConfig(current.copy(scaleMode = mode))
    }

    override suspend fun setPlayMode(mode: PlayMode) {
        val current = getCurrentConfig()
        updateConfig(current.copy(playMode = mode))
    }

    override suspend fun setImageCropParams(uri: String, params: ImageCropParams) {
        val current = getCurrentConfig()
        val newCropParams = current.imageCropParams + (uri to params)
        updateConfig(current.copy(imageCropParams = newCropParams))
    }

    override suspend fun setLanguage(languageTag: String?) {
        val current = getCurrentConfig()
        updateConfig(current.copy(languageTag = languageTag))
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        val current = getCurrentConfig()
        updateConfig(current.copy(themeMode = mode))
    }

    override suspend fun checkAppUpdate(
        apiKey: String,
        appKey: String,
        buildVersion: String?,
        buildBuildVersion: Int?
    ): PgyerResponse {
        return appUpdateService.checkUpdate(apiKey, appKey, buildVersion, buildBuildVersion)
    }

    private fun getCurrentConfig(): WallpaperConfig {
        return parseConfig(settings.getString(key, ""))
    }

    override fun getConfigSync(): WallpaperConfig {
        return getCurrentConfig()
    }
}

