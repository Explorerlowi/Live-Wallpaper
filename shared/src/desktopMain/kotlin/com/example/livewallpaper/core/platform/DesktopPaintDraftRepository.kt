package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintDraftSource
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Desktop adapter that exposes file-backed AI painting drafts to shared backup use cases.
 *
 * @param dispatchers Injected dispatcher provider used for every file operation.
 */
class DesktopPaintDraftRepository(
    private val dispatchers: CoroutineDispatcherProvider,
) : LegacyPaintDraftSource {
    private val storageLock = Any()
    private val _draftsRevision = MutableStateFlow(0L)

    override val draftsRevision: StateFlow<Long> = _draftsRevision.asStateFlow()

    override suspend fun getAllDrafts(): PaintDraftReadResult = getAllDrafts(emptySet())

    override suspend fun getAllDrafts(sessionIds: Set<String>): PaintDraftReadResult = withContext(dispatchers.io) {
        synchronized(storageLock) {
            when (val result = DesktopPaintDraftStore.readDraftsForBackup(sessionIds)) {
                DesktopPaintDraftBackupReadResult.Corrupted -> PaintDraftReadResult.Corrupted
                is DesktopPaintDraftBackupReadResult.Success -> PaintDraftReadResult.Success(
                    result.drafts.mapValues { (_, draft) -> draft.toDomainDraft() },
                )
            }
        }
    }

    override suspend fun getDraft(key: String): PaintSessionDraft? = withContext(dispatchers.io) {
        synchronized(storageLock) {
            DesktopPaintDraftStore.readDraft(key)
                .takeUnless { draft -> draft.promptText.isBlank() && draft.selectedImages.isEmpty() }
                ?.toDomainDraft()
        }
    }

    override suspend fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean =
        withContext(dispatchers.io) {
            synchronized(storageLock) {
                if (_draftsRevision.value != expectedRevision) return@synchronized false
                DesktopPaintDraftStore.writeDraftStrict(key, draft.toDesktopDraft())
            }
        }

    override suspend fun removeDraft(key: String, expectedRevision: Long): Boolean = withContext(dispatchers.io) {
        synchronized(storageLock) {
            if (_draftsRevision.value != expectedRevision) return@synchronized false
            DesktopPaintDraftStore.deleteDraftStrict(key)
        }
    }

    override suspend fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) = withContext(dispatchers.io) {
        synchronized(storageLock) {
            if (drafts.isEmpty()) return@synchronized
            check(
                DesktopPaintDraftStore.mergeDraftsStrict(
                    drafts.mapValues { (_, draft) -> draft.toDesktopDraft() },
                ),
            ) {
                "Unable to persist imported desktop painting drafts"
            }
            _draftsRevision.value += 1
        }
    }

    override suspend fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean = withContext(dispatchers.io) {
        synchronized(storageLock) {
            val replaced = DesktopPaintDraftStore.replaceDraftsStrict(
                drafts.mapValues { (_, draft) -> draft.toDesktopDraft() },
            )
            if (replaced) {
                _draftsRevision.value += 1
            }
            replaced
        }
    }

    private fun DesktopPaintDraft.toDomainDraft(): PaintSessionDraft = PaintSessionDraft(
        promptText = promptText,
        selectedImages = selectedImages.map { image -> image.toDomainImage() },
        selectedModel = selectedModel,
        selectedAspectRatio = selectedAspectRatio,
        selectedResolution = selectedResolution,
        selectedGptSize = selectedGptSize,
        selectedGptQuality = selectedGptQuality,
        selectedGptFormat = selectedGptFormat,
    )

    private fun PaintSessionDraft.toDesktopDraft(): DesktopPaintDraft = DesktopPaintDraft(
        promptText = promptText,
        selectedImages = selectedImages.map { image -> image.toSelectedImage() },
        selectedModel = selectedModel,
        selectedAspectRatio = selectedAspectRatio,
        selectedResolution = selectedResolution,
        selectedGptSize = selectedGptSize,
        selectedGptQuality = selectedGptQuality,
        selectedGptFormat = selectedGptFormat,
    )

    private fun SelectedImage.toDomainImage(): PaintDraftImage = PaintDraftImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )

    private fun PaintDraftImage.toSelectedImage(): SelectedImage = SelectedImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )
}
