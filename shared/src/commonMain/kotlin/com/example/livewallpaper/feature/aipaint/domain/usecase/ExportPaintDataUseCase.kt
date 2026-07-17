package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.domain.model.ExportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshotReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import kotlinx.coroutines.CancellationException

/**
 * Exports every painting session, message, and draft plus platform-approved images to one ZIP archive.
 *
 * @param paintDataRepository Source of complete conversation snapshots.
 * @param draftRepository Source of unsent painting drafts.
 * @param archiveGateway Platform ZIP writer.
 */
class ExportPaintDataUseCase(
    private val paintDataRepository: PaintDataRepository,
    private val draftRepository: PaintDraftRepository,
    private val archiveGateway: PaintDataArchiveGateway,
) {
    /**
     * Creates a painting data backup at a platform destination selected by the user.
     *
     * @param destinationIdentifier Platform destination identifier.
     * @return Export statistics or a normalized error.
     */
    suspend operator fun invoke(destinationIdentifier: String): ExportPaintDataResult {
        return try {
            val snapshot = when (val result = paintDataRepository.getPaintDataSnapshot()) {
                PaintDataSnapshotReadResult.Corrupted -> {
                    return ExportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                }
                is PaintDataSnapshotReadResult.Success -> result.snapshot
            }
            val drafts = when (val result = draftRepository.getAllDrafts()) {
                PaintDraftReadResult.Corrupted -> {
                    return ExportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                }
                is PaintDraftReadResult.Success -> result.drafts
            }
            val referencedImageIdentifiers = buildSet {
                snapshot.messages.forEach { message ->
                    message.images.mapNotNullTo(this) { image -> image.localPath }
                }
                drafts.values.forEach { draft ->
                    draft.selectedImages.mapTo(this) { image -> image.uri }
                }
            }
            val exportableImageIdentifiers = archiveGateway.retainExportableImageIdentifiers(
                referencedImageIdentifiers,
            )
            val prepared = when (
                val result = PaintDataBackupCodec.prepare(
                    snapshot = snapshot,
                    drafts = drafts,
                    exportedAt = TimeProvider.currentTimeMillis(),
                    exportableImageIdentifiers = exportableImageIdentifiers,
                    exportedFromPlatform = archiveGateway.clientPlatform,
                    includeEmbeddedImageData = archiveGateway.allowEmbeddedImageData,
                )
            ) {
                is PaintDataCodecResult.Failure -> return ExportPaintDataResult.Failure(result.error)
                is PaintDataCodecResult.Success -> result.value
            }
            when (val result = archiveGateway.writeArchive(destinationIdentifier, prepared.request)) {
                is PaintDataArchiveResult.Failure -> ExportPaintDataResult.Failure(result.error)
                is PaintDataArchiveResult.Success -> ExportPaintDataResult.Success(
                    sessionCount = prepared.sessionCount,
                    messageCount = prepared.messageCount,
                    imageCount = prepared.imageCount,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ExportPaintDataResult.Failure(PaintDataTransferError.UNKNOWN)
        }
    }
}
