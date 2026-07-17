package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.model.ImportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataMergeResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshotReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Imports a validated painting ZIP archive and merges it into current app data.
 *
 * @param paintDataRepository Destination for validated conversation data.
 * @param draftRepository Destination for validated unsent drafts.
 * @param archiveGateway Platform ZIP reader and extraction owner.
 */
class ImportPaintDataUseCase(
    private val paintDataRepository: PaintDataRepository,
    private val draftRepository: PaintDraftRepository,
    private val archiveGateway: PaintDataArchiveGateway,
) {
    /**
     * Restores painting data from a platform source selected by the user.
     *
     * Imported values overwrite matching IDs; unrelated local values remain intact.
     *
     * Conversation and draft stores are restored to their pre-import snapshots if either commit fails.
     *
     * @param sourceIdentifier Platform source identifier.
     * @return Import statistics or a normalized error.
     */
    suspend operator fun invoke(sourceIdentifier: String): ImportPaintDataResult {
        val extracted = when (val result = archiveGateway.extractArchive(sourceIdentifier)) {
            is PaintDataArchiveResult.Failure -> return ImportPaintDataResult.Failure(result.error)
            is PaintDataArchiveResult.Success -> result.value
        }

        var keepExtractedImages = false
        return try {
            val decoded = when (
                val result = PaintDataBackupCodec.decode(
                    manifestJson = extracted.manifestJson,
                    imagePathsByArchivePath = extracted.imagePathsByArchivePath,
                )
            ) {
                is PaintDataCodecResult.Failure -> return ImportPaintDataResult.Failure(result.error)
                is PaintDataCodecResult.Success -> result.value
            }

            val previousSnapshot = when (val result = paintDataRepository.getPaintDataSnapshot()) {
                PaintDataSnapshotReadResult.Corrupted -> {
                    return ImportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                }
                is PaintDataSnapshotReadResult.Success -> result.snapshot
            }
            val previousDrafts = when (val result = draftRepository.getAllDrafts()) {
                PaintDraftReadResult.Corrupted -> {
                    return ImportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                }
                is PaintDraftReadResult.Success -> result.drafts
            }

            // Once merge starts, finish both writes or roll both stores back before returning.
            withContext(NonCancellable) {
                try {
                    val summary = when (val result = paintDataRepository.mergePaintDataSnapshot(decoded.snapshot)) {
                        PaintDataMergeResult.CorruptedExistingData -> {
                            return@withContext ImportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                        }
                        is PaintDataMergeResult.Success -> result.summary
                    }
                    draftRepository.mergeDrafts(decoded.drafts)
                    keepExtractedImages = true
                    pruneUnreferencedImportedImages()
                    ImportPaintDataResult.Success(
                        importedSessionCount = summary.importedSessionCount,
                        importedMessageCount = summary.importedMessageCount,
                        imageCount = decoded.imageCount,
                        totalSessionCount = summary.totalSessionCount,
                    )
                } catch (_: Exception) {
                    val conversationsRestored = runCatching {
                        paintDataRepository.replacePaintDataSnapshot(previousSnapshot)
                    }.getOrDefault(false)
                    val draftsRestored = runCatching {
                        draftRepository.replaceDrafts(previousDrafts)
                    }.getOrDefault(false)
                    keepExtractedImages = !conversationsRestored || !draftsRestored
                    ImportPaintDataResult.Failure(PaintDataTransferError.UNKNOWN)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ImportPaintDataResult.Failure(PaintDataTransferError.UNKNOWN)
        } finally {
            if (!keepExtractedImages) {
                withContext(NonCancellable) {
                    archiveGateway.discardExtraction(extracted.extractionId)
                }
            }
        }
    }

    private suspend fun pruneUnreferencedImportedImages() {
        try {
            val snapshot = when (val result = paintDataRepository.getPaintDataSnapshot()) {
                PaintDataSnapshotReadResult.Corrupted -> return
                is PaintDataSnapshotReadResult.Success -> result.snapshot
            }
            val retainedIdentifiers = snapshot.messages
                .flatMap { message -> message.images.mapNotNull { it.localPath } }
                .toMutableSet()
            when (val drafts = draftRepository.getAllDrafts()) {
                PaintDraftReadResult.Corrupted -> return
                is PaintDraftReadResult.Success -> drafts.drafts.values.forEach { draft ->
                    draft.selectedImages.mapTo(retainedIdentifiers) { it.uri }
                }
            }
            archiveGateway.pruneImportedImages(retainedIdentifiers)
        } catch (_: Exception) {
            // Pruning is best-effort; imported data is already committed at this point.
        }
    }
}
