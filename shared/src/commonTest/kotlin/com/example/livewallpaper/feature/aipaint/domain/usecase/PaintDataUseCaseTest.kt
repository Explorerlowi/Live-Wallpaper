package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.model.ExtractedPaintDataArchive
import com.example.livewallpaper.feature.aipaint.domain.model.ExportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.ImportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportSummary
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportCommitResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataMergeResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshotReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredDataReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageRecoveryRequiredException
import com.example.livewallpaper.feature.aipaint.domain.model.PreviewPaintDataImportResult
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageRecoveryController
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies use-case coordination around archive writing, validation, merging, and cleanup. */
class PaintDataUseCaseTest {
    @Test
    fun previewValidatesBackupReturnsSummaryAndDiscardsExtraction() = runBlocking {
        val prepared = preparedBackup()
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-preview",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = PreviewPaintDataImportUseCase(archiveGateway)("source")

        val success = assertIs<PreviewPaintDataImportResult.Success>(result)
        assertEquals(1, success.sessionCount)
        assertEquals(1, success.messageCount)
        assertEquals(1, success.draftCount)
        assertEquals(1, success.imageCount)
        assertEquals(PaintClientPlatform.UNKNOWN, success.exportedFromPlatform)
        assertEquals(listOf("extraction-preview"), archiveGateway.discardedExtractions)
    }

    @Test
    fun previewRejectsInvalidManifestAndDiscardsExtraction() = runBlocking {
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-invalid-preview",
                manifestJson = "{}",
                imagePathsByArchivePath = emptyMap(),
            ),
        )

        val result = PreviewPaintDataImportUseCase(archiveGateway)("source")

        val failure = assertIs<PreviewPaintDataImportResult.Failure>(result)
        assertEquals(PaintDataTransferError.INVALID_ARCHIVE, failure.error)
        assertEquals(listOf("extraction-invalid-preview"), archiveGateway.discardedExtractions)
    }

    @Test
    fun exportWritesPreparedArchiveAndReturnsStatistics() = runBlocking {
        val dataRepository = FakePaintDataRepository(
            sampleSnapshot(),
            drafts = mapOf("session-1" to PaintSessionDraft("Draft")),
        )
        val archiveGateway = FakePaintDataArchiveGateway(clientPlatform = PaintClientPlatform.ANDROID)

        val result = ExportPaintDataUseCase(dataRepository, archiveGateway)("destination")

        val success = assertIs<ExportPaintDataResult.Success>(result)
        assertEquals(1, success.sessionCount)
        assertEquals(1, success.messageCount)
        assertEquals(0, success.imageCount)
        assertNotNull(archiveGateway.writtenRequest)
        assertTrue(archiveGateway.writtenRequest?.manifestJson?.contains("session-1") == true)
        val decoded = assertIs<PaintDataCodecResult.Success<DecodedPaintDataBackup>>(
            PaintDataBackupCodec.decode(archiveGateway.writtenRequest?.manifestJson.orEmpty(), emptyMap()),
        ).value
        assertEquals(PaintClientPlatform.ANDROID, decoded.exportedFromPlatform)
    }

    @Test
    fun exportSilentlyOmitsImagesRejectedByPlatformPolicy() = runBlocking {
        val archiveGateway = FakePaintDataArchiveGateway(
            exportableImageIdentifiers = emptySet(),
        )

        val result = ExportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshotWithImage()),
            archiveGateway,
        )("destination")

        val success = assertIs<ExportPaintDataResult.Success>(result)
        assertEquals(1, success.sessionCount)
        assertEquals(1, success.messageCount)
        assertEquals(0, success.imageCount)
        assertEquals(setOf("/local/reference.png"), archiveGateway.requestedExportIdentifiers)
        assertTrue(archiveGateway.writtenRequest?.imageSources?.isEmpty() == true)
        val decoded = assertIs<PaintDataCodecResult.Success<DecodedPaintDataBackup>>(
            PaintDataBackupCodec.decode(
                archiveGateway.writtenRequest?.manifestJson.orEmpty(),
                emptyMap(),
            ),
        ).value
        assertEquals(null, decoded.snapshot.messages.single().images.single().localPath)
        assertFalse(archiveGateway.writtenRequest?.manifestJson.orEmpty().contains("base64Data"))
    }

    @Test
    fun exportFailsInsteadOfSilentlyDroppingCorruptedDrafts() = runBlocking {
        val archiveGateway = FakePaintDataArchiveGateway()
        val result = ExportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshot(), corruptedOnRead = true),
            archiveGateway,
        )("destination")

        val failure = assertIs<ExportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertEquals(null, archiveGateway.writtenRequest)
    }

    @Test
    fun exportFailsInsteadOfSilentlyDroppingCorruptedConversationData() = runBlocking {
        val archiveGateway = FakePaintDataArchiveGateway()
        val dataRepository = FakePaintDataRepository(
            snapshot = sampleSnapshot(),
            corruptedOnRead = true,
        )

        val result = ExportPaintDataUseCase(
            dataRepository,
            archiveGateway,
        )("destination")

        val failure = assertIs<ExportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertEquals(null, archiveGateway.writtenRequest)
    }

    @Test
    fun exportMapsStorageRecoveryToDedicatedError() = runBlocking {
        val result = ExportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshot(), recoveryRequiredOnRead = true),
            FakePaintDataArchiveGateway(),
        )("destination")

        val failure = assertIs<ExportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.STORAGE_RECOVERY_REQUIRED, failure.error)
    }

    @Test
    fun importRejectsInvalidManifestBeforeMutationAndDiscardsExtraction() = runBlocking {
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-1",
                manifestJson = "{}",
                imagePathsByArchivePath = emptyMap(),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, FakeLegacyImageFileStore())("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.INVALID_ARCHIVE, failure.error)
        assertFalse(dataRepository.wasMerged)
        assertEquals(listOf("extraction-1"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importKeepsExtractionAfterSuccessfulMerge() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-ok",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, FakeLegacyImageFileStore())("source")

        assertIs<ImportPaintDataResult.Success>(result)
        assertTrue(dataRepository.wasMerged)
        assertTrue(archiveGateway.discardedExtractions.isEmpty())
        assertEquals(setOf("/restored/image_000001.png"), archiveGateway.lastRetainedIdentifiers)
        assertEquals("Draft", dataRepository.currentData.drafts["session-1"]?.promptText)
    }

    @Test
    fun importMaterializesHistoricalEmbeddedBase64BeforeMerge() = runBlocking {
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val imageFileStore = FakeLegacyImageFileStore()
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "legacy-extraction",
                manifestJson = legacyEmbeddedManifest("aGVsbG8="),
                imagePathsByArchivePath = emptyMap(),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, imageFileStore)("source")

        assertIs<ImportPaintDataResult.Success>(result)
        assertEquals(
            "/legacy/session-1/legacy-extraction_message-1-image-1.png",
            dataRepository.currentSnapshot.messages.single().images.single().localPath,
        )
        assertTrue(imageFileStore.discarded.isEmpty())
    }

    @Test
    fun importRejectsInvalidHistoricalBase64WithoutMutation() = runBlocking {
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "legacy-invalid",
                manifestJson = legacyEmbeddedManifest("not-base64"),
                imagePathsByArchivePath = emptyMap(),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, FakeLegacyImageFileStore())("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertFalse(dataRepository.wasMerged)
        assertEquals(listOf("legacy-invalid"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importRollsBackConversationAndDiscardsExtractionWhenDraftMergeFails() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(
            PaintDataSnapshot(emptyList(), emptyList()),
            failOnMerge = true,
        )
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-partial",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, FakeLegacyImageFileStore())("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.UNKNOWN, failure.error)
        assertTrue(dataRepository.wasMerged)
        assertTrue(dataRepository.wasReplaced)
        assertEquals(PaintDataSnapshot(emptyList(), emptyList()), dataRepository.currentSnapshot)
        assertEquals(listOf("extraction-partial"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importRollbackFailureRequiresRecoveryAndPreservesMaterializedFiles() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(
            PaintDataSnapshot(emptyList(), emptyList()),
            failOnMerge = true,
            failOnReplace = true,
        )
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-recovery",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )
        val recoveryController = RecordingRecoveryController()

        val result = ImportPaintDataUseCase(
            dataRepository,
            archiveGateway,
            FakeLegacyImageFileStore(),
            recoveryController,
        )("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.STORAGE_RECOVERY_REQUIRED, failure.error)
        assertEquals(PaintStorageFailure.IMPORT_ROLLBACK_FAILED, recoveryController.reason)
        assertTrue(archiveGateway.discardedExtractions.isEmpty())
        assertEquals("session-1", dataRepository.currentSnapshot.sessions.single().id)
    }

    @Test
    fun importDoesNotMutateWhenExistingConversationDataIsCorrupted() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(
            snapshot = PaintDataSnapshot(emptyList(), emptyList()),
            corruptedOnMerge = true,
        )
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-corrupted-local",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, archiveGateway, FakeLegacyImageFileStore())("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertFalse(dataRepository.wasMerged)
        assertEquals(listOf("extraction-corrupted-local"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importMapsStorageRecoveryToDedicatedErrorAndDiscardsExtraction() = runBlocking {
        val prepared = preparedBackup()
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-storage-recovery",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )
        val result = ImportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshot(), recoveryRequiredOnRead = true),
            archiveGateway,
            FakeLegacyImageFileStore(),
        )("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.STORAGE_RECOVERY_REQUIRED, failure.error)
        assertEquals(listOf("extraction-storage-recovery"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importReportsStorageBusyWhileGenerationIsActive() = runBlocking {
        val prepared = preparedBackup()
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-storage-busy",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )
        val result = ImportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshot(), busyOnImport = true),
            archiveGateway,
            FakeLegacyImageFileStore(),
        )("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.STORAGE_BUSY, failure.error)
        assertEquals(listOf("extraction-storage-busy"), archiveGateway.discardedExtractions)
    }

    private fun preparedBackup(): PreparedPaintDataBackup =
        assertIs<PaintDataCodecResult.Success<PreparedPaintDataBackup>>(
            PaintDataBackupCodec.prepare(
                snapshot = sampleSnapshotWithImage(),
                drafts = mapOf(
                    "session-1" to PaintSessionDraft(
                        promptText = "Draft",
                        selectedImages = listOf(
                            PaintDraftImage(
                                id = "draft-image-1",
                                uri = "/local/reference.png",
                                mimeType = "image/png",
                            ),
                        ),
                    ),
                ),
                exportedAt = 123L,
            ),
        ).value

    private fun extractedPaths(prepared: PreparedPaintDataBackup): Map<String, String> =
        prepared.request.imageSources.associate { source ->
            source.archivePath to "/restored/${source.archivePath.substringAfterLast('/')}"
        }

    private fun legacyEmbeddedManifest(base64: String): String = """
        {
          "format": "live-wallpaper-paint-data",
          "version": 1,
          "exportedAt": 123,
          "sessions": [{"id": "session-1"}],
          "messages": [{
            "id": "message-1",
            "sessionId": "session-1",
            "senderIdentity": "USER",
            "messageContent": "legacy",
            "messageType": "IMAGE",
            "images": [{
              "id": "image-1",
              "localPath": "/unavailable/legacy-image.png",
              "base64Data": "$base64",
              "mimeType": "image/png"
            }]
          }],
          "drafts": {},
          "images": []
        }
    """.trimIndent()

    private fun sampleSnapshot(): PaintDataSnapshot = PaintDataSnapshot(
        sessions = listOf(PaintSession(id = "session-1", title = "Sample")),
        messages = listOf(
            PaintMessage(
                id = "message-1",
                sessionId = "session-1",
                senderIdentity = SenderIdentity.USER,
                messageContent = "Hello",
                messageType = MessageType.TEXT,
            ),
        ),
    )

    private fun sampleSnapshotWithImage(): PaintDataSnapshot = PaintDataSnapshot(
        sessions = listOf(PaintSession(id = "session-1", title = "Sample")),
        messages = listOf(
            PaintMessage(
                id = "message-1",
                sessionId = "session-1",
                senderIdentity = SenderIdentity.USER,
                messageContent = "Reference",
                messageType = MessageType.IMAGE,
                images = listOf(
                    PaintImage(
                        id = "image-1",
                        localPath = "/local/reference.png",
                        mimeType = "image/png",
                    ),
                ),
            ),
        ),
    )

    private class FakePaintDataRepository(
        private var snapshot: PaintDataSnapshot,
        drafts: Map<String, PaintSessionDraft> = emptyMap(),
        private val corruptedOnRead: Boolean = false,
        private val corruptedOnMerge: Boolean = false,
        private val failOnMerge: Boolean = false,
        private val failOnReplace: Boolean = false,
        private val recoveryRequiredOnRead: Boolean = false,
        private val busyOnImport: Boolean = false,
    ) : PaintDataRepository {
        private var drafts = drafts.toMap()
        var wasMerged: Boolean = false
        var wasReplaced: Boolean = false
        val currentSnapshot: PaintDataSnapshot
            get() = snapshot
        val currentData: PaintStoredData
            get() = PaintStoredData(snapshot, drafts)

        override suspend fun getStoredData(): PaintStoredDataReadResult {
            if (recoveryRequiredOnRead) {
                throw PaintStorageRecoveryRequiredException(PaintStorageFailure.DATABASE_UNAVAILABLE)
            }
            return if (corruptedOnRead) {
                PaintStoredDataReadResult.Corrupted
            } else {
                PaintStoredDataReadResult.Success(currentData)
            }
        }

        override suspend fun mergeStoredData(data: PaintStoredData): PaintDataMergeResult {
            if (corruptedOnMerge) return PaintDataMergeResult.CorruptedExistingData
            wasMerged = true
            snapshot = data.snapshot
            drafts = drafts + data.drafts
            if (failOnMerge) error("transaction failed")
            return PaintDataMergeResult.Success(
                PaintDataImportSummary(
                    importedSessionCount = data.snapshot.sessions.size,
                    importedMessageCount = data.snapshot.messages.size,
                    totalSessionCount = snapshot.sessions.size,
                ),
            )
        }

        override suspend fun replaceStoredData(data: PaintStoredData): Boolean {
            wasReplaced = true
            if (failOnReplace) return false
            snapshot = data.snapshot
            drafts = data.drafts
            return true
        }

        override suspend fun importStoredData(data: PaintStoredData): PaintDataImportCommitResult {
            if (busyOnImport) return PaintDataImportCommitResult.Busy
            val previous = when (val result = getStoredData()) {
                PaintStoredDataReadResult.Corrupted -> return PaintDataImportCommitResult.CorruptedExistingData
                is PaintStoredDataReadResult.Success -> result.data
            }
            return try {
                when (val result = mergeStoredData(data)) {
                    PaintDataMergeResult.CorruptedExistingData -> PaintDataImportCommitResult.CorruptedExistingData
                    is PaintDataMergeResult.Success -> PaintDataImportCommitResult.Success(result.summary)
                }
            } catch (_: Exception) {
                if (replaceStoredData(previous)) {
                    PaintDataImportCommitResult.Failed
                } else {
                    PaintDataImportCommitResult.RollbackFailed
                }
            }
        }

        override suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult = if (corruptedOnRead) {
            PaintDataSnapshotReadResult.Corrupted
        } else {
            PaintDataSnapshotReadResult.Success(snapshot)
        }

        override suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult {
            return mergeStoredData(PaintStoredData(snapshot, drafts))
        }

        override suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean {
            return replaceStoredData(PaintStoredData(snapshot, drafts))
        }
    }

    private class RecordingRecoveryController : PaintStorageRecoveryController {
        var reason: PaintStorageFailure? = null

        override fun requireRecovery(reason: PaintStorageFailure) {
            this.reason = reason
        }
    }

    private class FakePaintDataArchiveGateway(
        private val extractedArchive: ExtractedPaintDataArchive? = null,
        private val exportableImageIdentifiers: Set<String>? = null,
        override val clientPlatform: PaintClientPlatform = PaintClientPlatform.UNKNOWN,
    ) : PaintDataArchiveGateway {
        var writtenRequest: PaintDataArchiveWriteRequest? = null
        val discardedExtractions = mutableListOf<String>()
        var lastRetainedIdentifiers: Set<String>? = null
        var requestedExportIdentifiers: Set<String>? = null

        override suspend fun retainExportableImageIdentifiers(sourceIdentifiers: Set<String>): Set<String> {
            requestedExportIdentifiers = sourceIdentifiers
            return exportableImageIdentifiers ?: sourceIdentifiers
        }

        override suspend fun writeArchive(
            destinationIdentifier: String,
            request: PaintDataArchiveWriteRequest,
        ): PaintDataArchiveResult<Unit> {
            writtenRequest = request
            return PaintDataArchiveResult.Success(Unit)
        }

        override suspend fun extractArchive(
            sourceIdentifier: String,
        ): PaintDataArchiveResult<ExtractedPaintDataArchive> = extractedArchive?.let {
            PaintDataArchiveResult.Success(it)
        } ?: PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)

        override suspend fun discardExtraction(extractionId: String) {
            discardedExtractions += extractionId
        }

        override suspend fun pruneImportedImages(retainedSourceIdentifiers: Set<String>) {
            lastRetainedIdentifiers = retainedSourceIdentifiers
        }
    }

    private class FakeLegacyImageFileStore : LegacyPaintImageFileStore {
        val discarded = mutableListOf<String>()

        override suspend fun isReadable(identifier: String): Boolean = true

        override suspend fun writeLegacyImage(
            sessionId: String,
            messageId: String,
            imageId: String,
            mimeType: String,
            bytes: ByteArray,
        ): String = "/legacy/$sessionId/$messageId-$imageId.png"

        override suspend fun discardCreatedFiles(identifiers: List<String>) {
            discarded += identifiers
        }
    }
}
