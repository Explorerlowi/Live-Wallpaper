package com.example.livewallpaper.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.feature.aipaint.domain.model.ExportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.ImportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PreviewPaintDataImportResult
import com.example.livewallpaper.feature.aipaint.domain.usecase.ExportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.ImportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.PreviewPaintDataImportUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State shown by the desktop painting data backup controls. */
sealed interface DesktopPaintDataTransferState {
    /** No transfer is running and no result is pending. */
    data object Idle : DesktopPaintDataTransferState

    /** A ZIP backup is being written. */
    data object Exporting : DesktopPaintDataTransferState

    /** A ZIP backup is being validated and merged. */
    data object Importing : DesktopPaintDataTransferState

    /** Export completed with item counts. */
    data class Exported(
        val sessionCount: Int,
        val messageCount: Int,
        val imageCount: Int,
    ) : DesktopPaintDataTransferState

    /** Import completed with item counts. */
    data class Imported(
        val sessionCount: Int,
        val messageCount: Int,
        val imageCount: Int,
        val totalSessionCount: Int,
    ) : DesktopPaintDataTransferState

    /** Transfer failed with a localized, platform-independent reason. */
    data class Failed(val error: PaintDataTransferError) : DesktopPaintDataTransferState
}

/** Desktop import dialog selection and preliminary validation state. */
sealed interface DesktopPaintDataImportPreviewState {
    /** No file is selected. */
    data object Empty : DesktopPaintDataImportPreviewState

    /** The selected file is being validated. */
    data class Validating(val sourcePath: String, val displayName: String) : DesktopPaintDataImportPreviewState

    /** The selected file is a valid painting backup. */
    data class Ready(
        val sourcePath: String,
        val displayName: String,
        val sessionCount: Int,
        val messageCount: Int,
        val draftCount: Int,
        val imageCount: Int,
        val exportedFromPlatform: PaintClientPlatform,
    ) : DesktopPaintDataImportPreviewState

    /** The selected file failed preliminary validation. */
    data class Invalid(
        val sourcePath: String,
        val displayName: String,
        val error: PaintDataTransferError,
    ) : DesktopPaintDataImportPreviewState
}

/** Coordinates the desktop settings dialog with shared painting backup use cases. */
class DesktopPaintDataTransferViewModel(
    private val exportPaintData: ExportPaintDataUseCase,
    private val importPaintData: ImportPaintDataUseCase,
    private val previewPaintDataImport: PreviewPaintDataImportUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<DesktopPaintDataTransferState>(DesktopPaintDataTransferState.Idle)
    private val _importPreviewState = MutableStateFlow<DesktopPaintDataImportPreviewState>(
        DesktopPaintDataImportPreviewState.Empty,
    )
    private var previewJob: Job? = null

    /** Current desktop backup operation or result. */
    val state: StateFlow<DesktopPaintDataTransferState> = _state.asStateFlow()

    /** Selected desktop backup and preliminary validation state. */
    val importPreviewState: StateFlow<DesktopPaintDataImportPreviewState> = _importPreviewState.asStateFlow()

    /** Starts exporting all painting data to [destinationPath]. */
    fun exportData(destinationPath: String) {
        val current = _state.value
        if (current.isBusy() || !_state.compareAndSet(current, DesktopPaintDataTransferState.Exporting)) return
        viewModelScope.launch {
            _state.value = when (val result = exportPaintData(destinationPath)) {
                is ExportPaintDataResult.Failure -> DesktopPaintDataTransferState.Failed(result.error)
                is ExportPaintDataResult.Success -> DesktopPaintDataTransferState.Exported(
                    sessionCount = result.sessionCount,
                    messageCount = result.messageCount,
                    imageCount = result.imageCount,
                )
            }
        }
    }

    /** Starts importing and merging the validated ZIP at [sourcePath]. */
    fun importData(sourcePath: String) {
        val current = _state.value
        if (current.isBusy() || !_state.compareAndSet(current, DesktopPaintDataTransferState.Importing)) return
        viewModelScope.launch {
            _state.value = when (val result = importPaintData(sourcePath)) {
                is ImportPaintDataResult.Failure -> DesktopPaintDataTransferState.Failed(result.error)
                is ImportPaintDataResult.Success -> DesktopPaintDataTransferState.Imported(
                    sessionCount = result.importedSessionCount,
                    messageCount = result.importedMessageCount,
                    imageCount = result.imageCount,
                    totalSessionCount = result.totalSessionCount,
                )
            }
        }
    }

    /** Validates [sourcePath] without changing local conversations or drafts. */
    fun previewImport(sourcePath: String) {
        if (_state.value.isBusy()) return
        previewJob?.cancel()
        val displayName = java.io.File(sourcePath).name.ifBlank { sourcePath }
        _importPreviewState.value = DesktopPaintDataImportPreviewState.Validating(sourcePath, displayName)
        previewJob = viewModelScope.launch {
            _importPreviewState.value = when (val result = previewPaintDataImport(sourcePath)) {
                is PreviewPaintDataImportResult.Failure -> DesktopPaintDataImportPreviewState.Invalid(
                    sourcePath = sourcePath,
                    displayName = displayName,
                    error = result.error,
                )
                is PreviewPaintDataImportResult.Success -> DesktopPaintDataImportPreviewState.Ready(
                    sourcePath = sourcePath,
                    displayName = displayName,
                    sessionCount = result.sessionCount,
                    messageCount = result.messageCount,
                    draftCount = result.draftCount,
                    imageCount = result.imageCount,
                    exportedFromPlatform = result.exportedFromPlatform,
                )
            }
        }
    }

    /** Imports the backup that passed preliminary validation. */
    fun confirmPreviewedImport() {
        val preview = _importPreviewState.value as? DesktopPaintDataImportPreviewState.Ready ?: return
        _importPreviewState.value = DesktopPaintDataImportPreviewState.Empty
        importData(preview.sourcePath)
    }

    /** Cancels preview validation and clears the selected backup. */
    fun clearImportPreview() {
        previewJob?.cancel()
        previewJob = null
        _importPreviewState.value = DesktopPaintDataImportPreviewState.Empty
    }

    /** Clears the displayed result while preserving an active operation. */
    fun clearResult() {
        if (!_state.value.isBusy()) {
            _state.value = DesktopPaintDataTransferState.Idle
        }
    }

    private fun DesktopPaintDataTransferState.isBusy(): Boolean =
        this is DesktopPaintDataTransferState.Exporting || this is DesktopPaintDataTransferState.Importing
}
