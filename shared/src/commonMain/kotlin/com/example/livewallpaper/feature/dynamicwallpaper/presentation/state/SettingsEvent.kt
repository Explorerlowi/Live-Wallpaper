package com.example.livewallpaper.feature.dynamicwallpaper.presentation.state

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode

sealed interface SettingsEvent {
    data class AddImages(val uris: List<String>) : SettingsEvent
    data class RemoveImage(val uri: String) : SettingsEvent
    data class RemoveImages(val uris: List<String>) : SettingsEvent
    data object RemoveAllImages : SettingsEvent
    data class UpdateImageOrder(val uris: List<String>) : SettingsEvent
    data class UpdateInterval(val interval: Long) : SettingsEvent
    data class UpdateScaleMode(val mode: ScaleMode) : SettingsEvent
    data class UpdatePlayMode(val mode: PlayMode) : SettingsEvent
    data class UpdateImageCropParams(val uri: String, val params: ImageCropParams) : SettingsEvent
    data class UpdateLanguage(val languageTag: String?) : SettingsEvent
}

