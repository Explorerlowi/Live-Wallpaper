package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.model.ExtractedPaintDataArchive
import com.example.livewallpaper.feature.aipaint.domain.model.ExportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.ImportPaintDataResult
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportSummary
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
import com.example.livewallpaper.feature.aipaint.domain.model.PreviewPaintDataImportResult
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val dataRepository = FakePaintDataRepository(sampleSnapshot())
        val draftRepository = FakePaintDraftRepository(mapOf("session-1" to PaintSessionDraft("Draft")))
        val archiveGateway = FakePaintDataArchiveGateway(clientPlatform = PaintClientPlatform.ANDROID)

        val result = ExportPaintDataUseCase(dataRepository, draftRepository, archiveGateway)("destination")

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
            allowEmbeddedImageData = false,
        )

        val result = ExportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshotWithImage()),
            FakePaintDraftRepository(emptyMap()),
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
        assertEquals(null, decoded.snapshot.messages.single().images.single().base64Data)
    }

    @Test
    fun exportFailsInsteadOfSilentlyDroppingCorruptedDrafts() = runBlocking {
        val archiveGateway = FakePaintDataArchiveGateway()
        val draftRepository = FakePaintDraftRepository(
            drafts = emptyMap(),
            corruptedOnRead = true,
        )

        val result = ExportPaintDataUseCase(
            FakePaintDataRepository(sampleSnapshot()),
            draftRepository,
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
            FakePaintDraftRepository(emptyMap()),
            archiveGateway,
        )("destination")

        val failure = assertIs<ExportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertEquals(null, archiveGateway.writtenRequest)
    }

    @Test
    fun importRejectsInvalidManifestBeforeMutationAndDiscardsExtraction() = runBlocking {
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val draftRepository = FakePaintDraftRepository(emptyMap())
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-1",
                manifestJson = "{}",
                imagePathsByArchivePath = emptyMap(),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, draftRepository, archiveGateway)("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.INVALID_ARCHIVE, failure.error)
        assertFalse(dataRepository.wasMerged)
        assertFalse(draftRepository.wasMerged)
        assertEquals(listOf("extraction-1"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importKeepsExtractionAfterSuccessfulMerge() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val draftRepository = FakePaintDraftRepository(emptyMap())
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-ok",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, draftRepository, archiveGateway)("source")

        assertIs<ImportPaintDataResult.Success>(result)
        assertTrue(dataRepository.wasMerged)
        assertTrue(draftRepository.wasMerged)
        assertTrue(archiveGateway.discardedExtractions.isEmpty())
        assertEquals(setOf("/restored/image_000001.png"), archiveGateway.lastRetainedIdentifiers)
        assertEquals(1L, draftRepository.draftsRevision.value)
        assertFalse(
            draftRepository.saveDraft(
                key = "session-1",
                draft = PaintSessionDraft(promptText = "stale editor value"),
                expectedRevision = 0L,
            ),
        )
        assertEquals("Draft", draftRepository.getDraft("session-1")?.promptText)
    }

    @Test
    fun importRollsBackConversationAndDiscardsExtractionWhenDraftMergeFails() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(PaintDataSnapshot(emptyList(), emptyList()))
        val draftRepository = FakePaintDraftRepository(
            drafts = emptyMap(),
            failOnMerge = true,
        )
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-partial",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, draftRepository, archiveGateway)("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.UNKNOWN, failure.error)
        assertTrue(dataRepository.wasMerged)
        assertTrue(dataRepository.wasReplaced)
        assertTrue(draftRepository.wasReplaced)
        assertEquals(PaintDataSnapshot(emptyList(), emptyList()), dataRepository.currentSnapshot)
        assertEquals(listOf("extraction-partial"), archiveGateway.discardedExtractions)
    }

    @Test
    fun importDoesNotMutateWhenExistingConversationDataIsCorrupted() = runBlocking {
        val prepared = preparedBackup()
        val dataRepository = FakePaintDataRepository(
            snapshot = PaintDataSnapshot(emptyList(), emptyList()),
            corruptedOnMerge = true,
        )
        val draftRepository = FakePaintDraftRepository(emptyMap())
        val archiveGateway = FakePaintDataArchiveGateway(
            extractedArchive = ExtractedPaintDataArchive(
                extractionId = "extraction-corrupted-local",
                manifestJson = prepared.request.manifestJson,
                imagePathsByArchivePath = extractedPaths(prepared),
            ),
        )

        val result = ImportPaintDataUseCase(dataRepository, draftRepository, archiveGateway)("source")

        val failure = assertIs<ImportPaintDataResult.Failure>(result)
        assertEquals(PaintDataTransferError.CORRUPTED_DATA, failure.error)
        assertFalse(dataRepository.wasMerged)
        assertFalse(draftRepository.wasMerged)
        assertEquals(listOf("extraction-corrupted-local"), archiveGateway.discardedExtractions)
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
                        base64Data = "embedded-image-data",
                        mimeType = "image/png",
                    ),
                ),
            ),
        ),
    )

    private class FakePaintDataRepository(
        private var snapshot: PaintDataSnapshot,
        private val corruptedOnRead: Boolean = false,
        private val corruptedOnMerge: Boolean = false,
    ) : PaintDataRepository {
        var wasMerged: Boolean = false
        var wasReplaced: Boolean = false
        val currentSnapshot: PaintDataSnapshot
            get() = snapshot

        override suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult = if (corruptedOnRead) {
            PaintDataSnapshotReadResult.Corrupted
        } else {
            PaintDataSnapshotReadResult.Success(snapshot)
        }

        override suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult {
            if (corruptedOnMerge) return PaintDataMergeResult.CorruptedExistingData
            wasMerged = true
            this.snapshot = snapshot
            return PaintDataMergeResult.Success(
                PaintDataImportSummary(
                    importedSessionCount = snapshot.sessions.size,
                    importedMessageCount = snapshot.messages.size,
                    totalSessionCount = snapshot.sessions.size,
                ),
            )
        }

        override suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean {
            wasReplaced = true
            this.snapshot = snapshot
            return true
        }
    }

    private class FakePaintDraftRepository(
        drafts: Map<String, PaintSessionDraft>,
        private val failOnMerge: Boolean = false,
        private val corruptedOnRead: Boolean = false,
    ) : PaintDraftRepository {
        private val drafts = drafts.toMutableMap()
        private val _draftsRevision = MutableStateFlow(0L)
        override val draftsRevision: StateFlow<Long> = _draftsRevision.asStateFlow()

        var wasMerged: Boolean = false
        var wasReplaced: Boolean = false

        override suspend fun getAllDrafts(): PaintDraftReadResult = if (corruptedOnRead) {
            PaintDraftReadResult.Corrupted
        } else {
            PaintDraftReadResult.Success(drafts)
        }

        override fun getDraft(key: String): PaintSessionDraft? = drafts[key]

        override fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean {
            if (_draftsRevision.value != expectedRevision) return false
            drafts[key] = draft
            return true
        }

        override fun removeDraft(key: String, expectedRevision: Long): Boolean {
            if (_draftsRevision.value != expectedRevision) return false
            drafts.remove(key)
            return true
        }

        override fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) {
            if (failOnMerge) {
                throw IllegalStateException("draft merge failed")
            }
            wasMerged = true
            this.drafts.putAll(drafts)
            _draftsRevision.value = _draftsRevision.value + 1
        }

        override fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean {
            wasReplaced = true
            this.drafts.clear()
            this.drafts.putAll(drafts)
            _draftsRevision.value = _draftsRevision.value + 1
            return true
        }
    }

    private class FakePaintDataArchiveGateway(
        private val extractedArchive: ExtractedPaintDataArchive? = null,
        private val exportableImageIdentifiers: Set<String>? = null,
        override val clientPlatform: PaintClientPlatform = PaintClientPlatform.UNKNOWN,
        override val allowEmbeddedImageData: Boolean = true,
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
}
