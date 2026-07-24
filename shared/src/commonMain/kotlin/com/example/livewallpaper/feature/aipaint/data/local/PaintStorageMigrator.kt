package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import kotlinx.coroutines.CancellationException

/** Performs the retained Preferences/JSON to SQLDelight migration exactly once. */
class PaintStorageMigrator(
    private val legacyReader: LegacyPaintStorageReader,
    private val legacyDrafts: LegacyPaintDraftSource,
    private val legacyConversationStore: LegacyPaintConversationStore,
    private val imageFileStore: LegacyPaintImageFileStore,
    private val referenceImageStore: PaintReferenceImageStore,
    private val sqlStore: SqlDelightPaintStore,
    private val epochStore: PaintMigrationEpochStore,
) {
    /** Reads, materializes, verifies and atomically commits legacy painting data. */
    suspend fun migrate(): PaintStorageMigrationResult {
        val epoch = epochStore.readSqlStorageActivation()
        return try {
            migrateInternal(epoch)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (epoch != PaintMigrationEpochReadResult.NotActivated) {
                PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.DATABASE_UNAVAILABLE)
            } else {
                fallback(PaintStorageFailure.DATABASE_UNAVAILABLE)
            }
        }
    }

    private suspend fun migrateInternal(epoch: PaintMigrationEpochReadResult): PaintStorageMigrationResult {
        if (sqlStore.isLegacyMigrationComplete()) {
            if (!sqlStore.verifyStoredData()) {
                return PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.DATABASE_UNAVAILABLE)
            }
            val activationResult = activateVerifiedSql(epoch)
            if (activationResult != null) return activationResult
            cleanupOrphansBestEffort()
            return PaintStorageMigrationResult.Ready
        }

        if (sqlStore.hasStoredData()) {
            return try {
                if (!sqlStore.verifyStoredData()) {
                    return PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.DATABASE_UNAVAILABLE)
                }
                sqlStore.recordLegacyMigrationForExistingData()
                val activationResult = activateVerifiedSql(epoch)
                if (activationResult != null) return activationResult
                cleanupOrphansBestEffort()
                PaintStorageMigrationResult.Ready
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.DATABASE_UNAVAILABLE)
            }
        }

        when (epoch) {
            PaintMigrationEpochReadResult.Activated -> {
                return PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.DATABASE_UNAVAILABLE)
            }
            PaintMigrationEpochReadResult.Unavailable -> {
                return PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.MIGRATION_STATE_READ_FAILED)
            }
            PaintMigrationEpochReadResult.NotActivated -> Unit
        }

        return when (val conversations = legacyReader.read()) {
            is LegacyPaintStorageReadResult.Corrupted -> {
                imageFileStore.discardCreatedFiles(conversations.createdFiles)
                fallback(PaintStorageFailure.CORRUPTED_LEGACY_DATA)
            }
            is LegacyPaintStorageReadResult.Success -> migrate(conversations)
        }
    }

    private suspend fun migrate(
        conversations: LegacyPaintStorageReadResult.Success,
    ): PaintStorageMigrationResult {
        val sessionIds = conversations.snapshot.sessions.mapTo(mutableSetOf()) { it.id }
        val legacyDraftData = when (val result = legacyDrafts.getAllDrafts(sessionIds)) {
            PaintDraftReadResult.Corrupted -> {
                imageFileStore.discardCreatedFiles(conversations.createdFiles)
                return fallback(PaintStorageFailure.CORRUPTED_LEGACY_DATA)
            }
            is PaintDraftReadResult.Success -> result.drafts
        }
        val createdReferences = mutableSetOf<String>()
        val drafts = mutableMapOf<String, PaintSessionDraft>()
        legacyDraftData.forEach { (key, draft) ->
            val images = mutableListOf<PaintDraftImage>()
            draft.selectedImages.forEach { image ->
                val path = try {
                    referenceImageStore.persistReference(
                        sessionId = key,
                        imageId = image.id,
                        sourceIdentifier = image.uri,
                        mimeType = image.mimeType,
                    )
                } catch (error: CancellationException) {
                    imageFileStore.discardCreatedFiles(conversations.createdFiles)
                    referenceImageStore.discardUnreferenced(createdReferences, emptySet())
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (path == null) {
                    imageFileStore.discardCreatedFiles(conversations.createdFiles)
                    referenceImageStore.discardUnreferenced(createdReferences, emptySet())
                    return fallback(PaintStorageFailure.IMAGE_MATERIALIZATION_FAILED)
                }
                createdReferences += path
                images += image.copy(uri = path)
            }
            drafts[key] = draft.copy(selectedImages = images)
        }
        val committed = try {
            sqlStore.commitLegacyMigration(PaintStoredData(conversations.snapshot, drafts))
            true
        } catch (error: CancellationException) {
            imageFileStore.discardCreatedFiles(conversations.createdFiles)
            referenceImageStore.discardUnreferenced(createdReferences, emptySet())
            throw error
        } catch (_: Exception) {
            imageFileStore.discardCreatedFiles(conversations.createdFiles)
            referenceImageStore.discardUnreferenced(createdReferences, emptySet())
            false
        }
        if (!committed) return fallback(PaintStorageFailure.DATABASE_WRITE_FAILED)
        if (!epochStore.markSqlStorageActivated()) {
            return PaintStorageMigrationResult.ReadOnly(PaintStorageFailure.MIGRATION_STATE_WRITE_FAILED)
        }
        cleanupOrphansBestEffort()
        return PaintStorageMigrationResult.Ready
    }

    private suspend fun activateVerifiedSql(
        epoch: PaintMigrationEpochReadResult,
    ): PaintStorageMigrationResult? {
        if (epoch == PaintMigrationEpochReadResult.Activated) return null
        if (epochStore.markSqlStorageActivated()) return null
        val reason = if (epoch == PaintMigrationEpochReadResult.Unavailable) {
            PaintStorageFailure.MIGRATION_STATE_READ_FAILED
        } else {
            PaintStorageFailure.MIGRATION_STATE_WRITE_FAILED
        }
        return PaintStorageMigrationResult.ReadOnly(reason)
    }

    private suspend fun cleanupOrphansBestEffort() {
        try {
            sqlStore.cleanupOrphanedImages()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Maintenance failure must not make an otherwise verified database unavailable.
        }
    }

    private suspend fun fallback(
        reason: PaintStorageFailure,
    ): PaintStorageMigrationResult {
        val fallbackRead = try {
            legacyReader.readForFallback()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val fallbackSnapshot = when (fallbackRead) {
            is LegacyPaintStorageReadResult.Success -> fallbackRead.snapshot
            is LegacyPaintStorageReadResult.Corrupted -> PaintDataSnapshot(emptyList(), emptyList())
            null -> PaintDataSnapshot(emptyList(), emptyList())
        }
        legacyConversationStore.activate(fallbackSnapshot)
        return PaintStorageMigrationResult.LegacyFallback(reason)
    }
}

/** Internal backend decision returned to [PaintStorageCoordinator]. */
sealed interface PaintStorageMigrationResult {
    data object Ready : PaintStorageMigrationResult
    data class ReadOnly(val reason: PaintStorageFailure) : PaintStorageMigrationResult
    data class LegacyFallback(val reason: PaintStorageFailure) : PaintStorageMigrationResult
    data class RecoveryRequired(val reason: PaintStorageFailure) : PaintStorageMigrationResult
}
