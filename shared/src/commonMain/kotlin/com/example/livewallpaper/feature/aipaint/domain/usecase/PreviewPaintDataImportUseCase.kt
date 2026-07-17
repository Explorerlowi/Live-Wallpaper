package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PreviewPaintDataImportResult
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Validates and summarizes a painting backup without changing conversations or drafts. */
class PreviewPaintDataImportUseCase(
    private val archiveGateway: PaintDataArchiveGateway,
) {
    /**
     * Extracts, validates, and summarizes the selected backup, then removes preview files.
     *
     * @param sourceIdentifier Platform source selected by the user.
     * @return Validated backup statistics or a normalized validation error.
     */
    suspend operator fun invoke(sourceIdentifier: String): PreviewPaintDataImportResult {
        val extracted = try {
            when (val result = archiveGateway.extractArchive(sourceIdentifier)) {
                is PaintDataArchiveResult.Failure -> return PreviewPaintDataImportResult.Failure(result.error)
                is PaintDataArchiveResult.Success -> result.value
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return PreviewPaintDataImportResult.Failure(PaintDataTransferError.UNKNOWN)
        }

        return try {
            when (
                val result = PaintDataBackupCodec.decode(
                    manifestJson = extracted.manifestJson,
                    imagePathsByArchivePath = extracted.imagePathsByArchivePath,
                )
            ) {
                is PaintDataCodecResult.Failure -> PreviewPaintDataImportResult.Failure(result.error)
                is PaintDataCodecResult.Success -> PreviewPaintDataImportResult.Success(
                    sessionCount = result.value.snapshot.sessions.size,
                    messageCount = result.value.snapshot.messages.size,
                    draftCount = result.value.drafts.size,
                    imageCount = result.value.imageCount,
                    exportedFromPlatform = result.value.exportedFromPlatform,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PreviewPaintDataImportResult.Failure(PaintDataTransferError.UNKNOWN)
        } finally {
            withContext(NonCancellable) {
                try {
                    archiveGateway.discardExtraction(extracted.extractionId)
                } catch (_: Exception) {
                    // Preview cleanup is best-effort and must not hide the validation result.
                }
            }
        }
    }
}
