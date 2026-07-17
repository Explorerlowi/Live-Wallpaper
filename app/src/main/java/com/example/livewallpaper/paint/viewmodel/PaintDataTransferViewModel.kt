package com.example.livewallpaper.paint.viewmodel

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

/** UI state for painting data import and export in general settings. */
sealed interface PaintDataTransferUiState {
    /** No transfer is active and no result is waiting to be shown. */
    data object Idle : PaintDataTransferUiState

    /** A ZIP archive is being created. */
    data object Exporting : PaintDataTransferUiState

    /** A ZIP archive is being validated and imported. */
    data object Importing : PaintDataTransferUiState

    /**
     * Successful export statistics.
     *
     * @property sessionCount Exported session count.
     * @property messageCount Exported message count.
     * @property imageCount Exported unique image count.
     */
    data class Exported(
        val sessionCount: Int,
        val messageCount: Int,
        val imageCount: Int,
    ) : PaintDataTransferUiState

    /**
     * Successful import statistics.
     *
     * @property sessionCount Imported session count.
     * @property messageCount Imported message count.
     * @property imageCount Restored unique image count.
     * @property totalSessionCount Total local session count after merging.
     */
    data class Imported(
        val sessionCount: Int,
        val messageCount: Int,
        val imageCount: Int,
        val totalSessionCount: Int,
    ) : PaintDataTransferUiState

    /**
     * Failed transfer with a user-mappable error.
     *
     * @property error Normalized transfer error.
     */
    data class Failed(val error: PaintDataTransferError) : PaintDataTransferUiState
}

/** Import-file selection and validation state shown inside the confirmation dialog. */
sealed interface PaintDataImportPreviewUiState {
    /** No file has been selected. */
    data object Empty : PaintDataImportPreviewUiState

    /** The selected file is being extracted and validated. */
    data class Validating(
        val sourceIdentifier: String,
        val displayName: String,
    ) : PaintDataImportPreviewUiState

    /** The selected file is a valid painting backup and can be imported. */
    data class Ready(
        val sourceIdentifier: String,
        val displayName: String,
        val sessionCount: Int,
        val messageCount: Int,
        val draftCount: Int,
        val imageCount: Int,
        val exportedFromPlatform: PaintClientPlatform,
    ) : PaintDataImportPreviewUiState

    /** The selected file failed preliminary validation. */
    data class Invalid(
        val sourceIdentifier: String,
        val displayName: String,
        val error: PaintDataTransferError,
    ) : PaintDataImportPreviewUiState
}

/**
 * Coordinates general-settings UI with shared painting data transfer use cases.
 *
 * @param exportPaintData Shared export use case.
 * @param importPaintData Shared import use case.
 * @param previewPaintDataImport Shared non-mutating backup inspection use case.
 */
class PaintDataTransferViewModel(
    private val exportPaintData: ExportPaintDataUseCase,
    private val importPaintData: ImportPaintDataUseCase,
    private val previewPaintDataImport: PreviewPaintDataImportUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PaintDataTransferUiState>(PaintDataTransferUiState.Idle)
    private val _importPreviewState = MutableStateFlow<PaintDataImportPreviewUiState>(
        PaintDataImportPreviewUiState.Empty,
    )
    private var previewJob: Job? = null

    /** Current painting data transfer state. */
    val uiState: StateFlow<PaintDataTransferUiState> = _uiState.asStateFlow()

    /** Selected import file and its preliminary validation state. */
    val importPreviewState: StateFlow<PaintDataImportPreviewUiState> = _importPreviewState.asStateFlow()

    /**
     * Starts a complete painting data export.
     *
     * @param destinationIdentifier Storage Access Framework destination URI.
     */
    fun export(destinationIdentifier: String) {
        val current = _uiState.value
        if (current.isBusy() || !_uiState.compareAndSet(current, PaintDataTransferUiState.Exporting)) return
        viewModelScope.launch {
            _uiState.value = when (val result = exportPaintData(destinationIdentifier)) {
                is ExportPaintDataResult.Failure -> PaintDataTransferUiState.Failed(result.error)
                is ExportPaintDataResult.Success -> PaintDataTransferUiState.Exported(
                    sessionCount = result.sessionCount,
                    messageCount = result.messageCount,
                    imageCount = result.imageCount,
                )
            }
        }
    }

    /**
     * Starts a validated, non-destructive painting data import.
     *
     * @param sourceIdentifier Storage Access Framework source URI.
     */
    fun import(sourceIdentifier: String) {
        val current = _uiState.value
        if (current.isBusy() || !_uiState.compareAndSet(current, PaintDataTransferUiState.Importing)) return
        viewModelScope.launch {
            _uiState.value = when (val result = importPaintData(sourceIdentifier)) {
                is ImportPaintDataResult.Failure -> PaintDataTransferUiState.Failed(result.error)
                is ImportPaintDataResult.Success -> PaintDataTransferUiState.Imported(
                    sessionCount = result.importedSessionCount,
                    messageCount = result.importedMessageCount,
                    imageCount = result.imageCount,
                    totalSessionCount = result.totalSessionCount,
                )
            }
        }
    }

    /** Selects a backup and validates it without changing local painting data. */
    fun previewImport(sourceIdentifier: String, displayName: String) {
        if (_uiState.value.isBusy()) return
        previewJob?.cancel()
        _importPreviewState.value = PaintDataImportPreviewUiState.Validating(sourceIdentifier, displayName)
        previewJob = viewModelScope.launch {
            _importPreviewState.value = when (val result = previewPaintDataImport(sourceIdentifier)) {
                is PreviewPaintDataImportResult.Failure -> PaintDataImportPreviewUiState.Invalid(
                    sourceIdentifier = sourceIdentifier,
                    displayName = displayName,
                    error = result.error,
                )
                is PreviewPaintDataImportResult.Success -> PaintDataImportPreviewUiState.Ready(
                    sourceIdentifier = sourceIdentifier,
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

    /** Imports the currently validated file. */
    fun confirmPreviewedImport() {
        val preview = _importPreviewState.value as? PaintDataImportPreviewUiState.Ready ?: return
        _importPreviewState.value = PaintDataImportPreviewUiState.Empty
        import(preview.sourceIdentifier)
    }

    /** Cancels preview work and forgets the selected import file. */
    fun clearImportPreview() {
        previewJob?.cancel()
        previewJob = null
        _importPreviewState.value = PaintDataImportPreviewUiState.Empty
    }

    /** Clears a displayed result and returns to the idle state. */
    fun clearResult() {
        if (!_uiState.value.isBusy()) {
            _uiState.value = PaintDataTransferUiState.Idle
        }
    }

    private fun PaintDataTransferUiState.isBusy(): Boolean =
        this is PaintDataTransferUiState.Exporting || this is PaintDataTransferUiState.Importing
}
