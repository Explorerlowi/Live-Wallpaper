package com.example.livewallpaper.feature.dynamicwallpaper.presentation.state

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig

data class SettingsUiState(
    val config: WallpaperConfig = WallpaperConfig(),
    val isLoading: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.Idle
)

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
