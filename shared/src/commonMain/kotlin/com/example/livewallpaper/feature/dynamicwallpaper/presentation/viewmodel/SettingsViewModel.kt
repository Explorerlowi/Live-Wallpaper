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

import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.UpdateStatus

class SettingsViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getConfig(),
        _isLoading,
        _updateStatus
    ) { config, isLoading, updateStatus ->
        SettingsUiState(config, isLoading, updateStatus)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(config = repository.getConfigSync())
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
                is SettingsEvent.CheckUpdate -> checkUpdate(event)
                is SettingsEvent.ClearUpdateStatus -> _updateStatus.value = UpdateStatus.Idle
            }
        }
    }

    private suspend fun checkUpdate(event: SettingsEvent.CheckUpdate) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val response = repository.checkAppUpdate(event.apiKey, event.appKey, event.version, event.build)
            if (response.code == 0 && response.data != null) {
                _updateStatus.value = UpdateStatus.Success(
                    hasNewVersion = response.data.buildHaveNewVersion,
                    version = response.data.buildVersion,
                    desc = response.data.buildUpdateDescription,
                    downloadUrl = response.data.downloadURL
                )
            } else {
                _updateStatus.value = UpdateStatus.Error(response.message)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }
}

