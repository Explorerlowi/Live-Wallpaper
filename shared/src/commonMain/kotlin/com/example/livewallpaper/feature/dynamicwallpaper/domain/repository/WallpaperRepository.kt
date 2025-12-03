package com.example.livewallpaper.feature.dynamicwallpaper.domain.repository

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun getConfig(): Flow<WallpaperConfig>
    suspend fun updateConfig(config: WallpaperConfig)
    suspend fun addImages(uris: List<String>)
    suspend fun removeImage(uri: String)
    suspend fun removeImages(uris: List<String>)
    suspend fun removeAllImages()
    suspend fun updateImageOrder(uris: List<String>)
    suspend fun setInterval(interval: Long)
    suspend fun setScaleMode(mode: ScaleMode)
    suspend fun setPlayMode(mode: PlayMode)
    suspend fun setImageCropParams(uri: String, params: ImageCropParams)
    suspend fun setLanguage(languageTag: String?)
}

