package com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibraryOperations
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsEvent
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsUiState
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.UpdateStatus
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
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getConfig(),
        repository.getLibraryDocument(),
        _isLoading,
        _updateStatus
    ) { config, document, isLoading, updateStatus ->
        SettingsUiState(
            config = config,
            libraries = WallpaperLibraryOperations.toSummaries(document),
            activeLibraryId = document.activeLibraryId,
            libraryImages = WallpaperLibraryOperations.projectImageUrisByLibrary(document),
            allImageCropParams = WallpaperLibraryOperations.projectAllCropParams(document),
            isLoading = isLoading,
            updateStatus = updateStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initialUiState()
    )

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.AddImages -> repository.addImages(event.uris)
                is SettingsEvent.AddImagesToLibrary -> {
                    repository.addImagesToLibrary(event.libraryId, event.uris)
                }
                is SettingsEvent.RemoveImage -> repository.removeImage(event.uri)
                is SettingsEvent.RemoveImages -> repository.removeImages(event.uris)
                is SettingsEvent.RemoveImagesFromLibrary -> {
                    repository.removeImagesFromLibrary(event.libraryId, event.uris)
                }
                is SettingsEvent.RemoveAllImages -> repository.removeAllImages()
                is SettingsEvent.UpdateImageOrder -> repository.updateImageOrder(event.uris)
                is SettingsEvent.UpdateLibraryImageOrder -> {
                    repository.updateLibraryImageOrder(event.libraryId, event.uris)
                }
                is SettingsEvent.UpdateInterval -> repository.setInterval(event.interval)
                is SettingsEvent.UpdateScaleMode -> repository.setScaleMode(event.mode)
                is SettingsEvent.UpdatePlayMode -> repository.setPlayMode(event.mode)
                is SettingsEvent.UpdateImageCropParams -> {
                    repository.setImageCropParams(event.uri, event.params)
                }
                is SettingsEvent.CreateLibrary -> repository.createLibrary(event.name)
                is SettingsEvent.RenameLibrary -> repository.renameLibrary(event.libraryId, event.name)
                is SettingsEvent.DeleteLibrary -> repository.deleteLibrary(event.libraryId)
                is SettingsEvent.SwitchLibrary -> repository.setActiveLibrary(event.libraryId)
                is SettingsEvent.UpdateLanguage -> repository.setLanguage(event.languageTag)
                is SettingsEvent.UpdateThemeMode -> repository.setThemeMode(event.mode)
                is SettingsEvent.UpdateLaunchAtStartup -> repository.setLaunchAtStartup(event.enabled)
                is SettingsEvent.UpdateRestoreSlideshowOnLaunch -> {
                    repository.setRestoreSlideshowOnLaunch(event.enabled)
                }
                is SettingsEvent.UpdatePaintGenerationSuccessNotification -> {
                    repository.setPaintGenerationSuccessNotification(event.enabled)
                }
                is SettingsEvent.CheckUpdate -> checkUpdate(event)
                is SettingsEvent.ClearUpdateStatus -> _updateStatus.value = UpdateStatus.Idle
            }
        }
    }

    private fun initialUiState(): SettingsUiState {
        val document = repository.getLibraryDocumentSync()
        return SettingsUiState(
            config = repository.getConfigSync(),
            libraries = WallpaperLibraryOperations.toSummaries(document),
            activeLibraryId = document.activeLibraryId,
            libraryImages = WallpaperLibraryOperations.projectImageUrisByLibrary(document),
            allImageCropParams = WallpaperLibraryOperations.projectAllCropParams(document)
        )
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
