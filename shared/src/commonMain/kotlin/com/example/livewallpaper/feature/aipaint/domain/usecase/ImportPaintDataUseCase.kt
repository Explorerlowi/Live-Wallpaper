package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.model.ImportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportCommitResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageRecoveryRequiredException
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredDataReadResult
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.NoOpPaintStorageRecoveryController
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageRecoveryController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Imports a validated painting ZIP archive and merges it into current app data.
 *
 * @param paintDataRepository Destination for validated conversation data.
 * @param archiveGateway Platform ZIP reader and extraction owner.
 */
class ImportPaintDataUseCase(
    private val paintDataRepository: PaintDataRepository,
    private val archiveGateway: PaintDataArchiveGateway,
    private val legacyImageFileStore: LegacyPaintImageFileStore,
    private val recoveryController: PaintStorageRecoveryController = NoOpPaintStorageRecoveryController,
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
        val materializedLegacyImages = mutableListOf<String>()
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

            val restoredSnapshot = materializeLegacyImages(
                decoded = decoded,
                extractionId = extracted.extractionId,
                createdFiles = materializedLegacyImages,
            ) ?: return ImportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)

            val importedData = PaintStoredData(restoredSnapshot, decoded.drafts)

            // Snapshot, merge and any rollback run behind one repository-owned storage gate.
            withContext(NonCancellable) {
                when (val result = paintDataRepository.importStoredData(importedData)) {
                    PaintDataImportCommitResult.CorruptedExistingData -> {
                        ImportPaintDataResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
                    }
                    PaintDataImportCommitResult.Failed -> {
                        ImportPaintDataResult.Failure(PaintDataTransferError.UNKNOWN)
                    }
                    PaintDataImportCommitResult.RollbackFailed -> {
                        keepExtractedImages = true
                        recoveryController.requireRecovery(PaintStorageFailure.IMPORT_ROLLBACK_FAILED)
                        ImportPaintDataResult.Failure(PaintDataTransferError.STORAGE_RECOVERY_REQUIRED)
                    }
                    PaintDataImportCommitResult.Busy -> {
                        ImportPaintDataResult.Failure(PaintDataTransferError.STORAGE_BUSY)
                    }
                    is PaintDataImportCommitResult.Success -> {
                        keepExtractedImages = true
                        pruneUnreferencedImportedImages()
                        ImportPaintDataResult.Success(
                            importedSessionCount = result.summary.importedSessionCount,
                            importedMessageCount = result.summary.importedMessageCount,
                            imageCount = decoded.imageCount,
                            totalSessionCount = result.summary.totalSessionCount,
                        )
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: PaintStorageRecoveryRequiredException) {
            ImportPaintDataResult.Failure(PaintDataTransferError.STORAGE_RECOVERY_REQUIRED)
        } catch (_: Exception) {
            ImportPaintDataResult.Failure(PaintDataTransferError.UNKNOWN)
        } finally {
            if (!keepExtractedImages) {
                withContext(NonCancellable) {
                    legacyImageFileStore.discardCreatedFiles(materializedLegacyImages)
                    archiveGateway.discardExtraction(extracted.extractionId)
                }
            }
        }
    }

    private suspend fun pruneUnreferencedImportedImages() {
        try {
            val storedData = when (val result = paintDataRepository.getStoredData()) {
                PaintStoredDataReadResult.Corrupted -> return
                is PaintStoredDataReadResult.Success -> result.data
            }
            val retainedIdentifiers = storedData.snapshot.messages
                .flatMap { message -> message.images.mapNotNull { it.localPath } }
                .toMutableSet()
            storedData.drafts.values.forEach { draft ->
                draft.selectedImages.mapTo(retainedIdentifiers) { it.uri }
            }
            archiveGateway.pruneImportedImages(retainedIdentifiers)
        } catch (_: Exception) {
            // Pruning is best-effort; imported data is already committed at this point.
        }
    }

    private suspend fun materializeLegacyImages(
        decoded: DecodedPaintDataBackup,
        extractionId: String,
        createdFiles: MutableList<String>,
    ): com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot? {
        if (decoded.embeddedImages.isEmpty()) return decoded.snapshot
        val pathsByImageId = mutableMapOf<String, String>()
        for (image in decoded.embeddedImages) {
            val path = legacyImageFileStore.writeLegacyImage(
                sessionId = image.sessionId,
                messageId = "${extractionId}_${image.messageId}",
                imageId = image.imageId,
                mimeType = image.mimeType,
                bytes = image.bytes,
            ) ?: return null
            createdFiles += path
            pathsByImageId[image.imageId] = path
        }
        return decoded.snapshot.copy(
            messages = decoded.snapshot.messages.map { message ->
                message.copy(
                    images = message.images.map { image ->
                        pathsByImageId[image.id]?.let { path -> image.copy(localPath = path) } ?: image
                    },
                )
            },
        )
    }
}
