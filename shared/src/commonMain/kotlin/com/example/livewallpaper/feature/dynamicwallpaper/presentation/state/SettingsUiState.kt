package com.example.livewallpaper.feature.dynamicwallpaper.presentation.state

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig

data class SettingsUiState(
    val config: WallpaperConfig = WallpaperConfig(),
    val isLoading: Boolean = false
)

