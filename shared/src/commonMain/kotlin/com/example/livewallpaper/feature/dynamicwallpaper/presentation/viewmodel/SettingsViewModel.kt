package com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsEvent
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getConfig(),
        _isLoading
    ) { config, isLoading ->
        SettingsUiState(config, isLoading)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.AddImages -> repository.addImages(event.uris)
                is SettingsEvent.RemoveImage -> repository.removeImage(event.uri)
                is SettingsEvent.RemoveImages -> repository.removeImages(event.uris)
                is SettingsEvent.RemoveAllImages -> repository.removeAllImages()
                is SettingsEvent.UpdateImageOrder -> repository.updateImageOrder(event.uris)
                is SettingsEvent.UpdateInterval -> repository.setInterval(event.interval)
                is SettingsEvent.UpdateScaleMode -> repository.setScaleMode(event.mode)
                is SettingsEvent.UpdatePlayMode -> repository.setPlayMode(event.mode)
                is SettingsEvent.UpdateImageCropParams -> repository.setImageCropParams(event.uri, event.params)
                is SettingsEvent.UpdateLanguage -> repository.setLanguage(event.languageTag)
                is SettingsEvent.UpdateThemeMode -> repository.setThemeMode(event.mode)
            }
        }
    }
}

