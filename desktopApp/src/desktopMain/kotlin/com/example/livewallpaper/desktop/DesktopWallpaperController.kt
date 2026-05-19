package com.example.livewallpaper.desktop

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import kotlinx.coroutines.flow.StateFlow

sealed interface DesktopWallpaperStatus {
    data object Idle : DesktopWallpaperStatus
    data object Unsupported : DesktopWallpaperStatus
    data class Current(val currentPath: String) : DesktopWallpaperStatus
    data class Running(val currentPath: String) : DesktopWallpaperStatus
    data class Error(val message: String) : DesktopWallpaperStatus
}

interface DesktopWallpaperController {
    val status: StateFlow<DesktopWallpaperStatus>

    fun start(config: WallpaperConfig)

    fun stop()

    fun setWallpaper(path: String, scaleMode: ScaleMode)
}
