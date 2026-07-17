package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveLimits
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Verifies the versioned painting backup manifest and image path restoration rules. */
class PaintDataBackupCodecTest {
    @Test
    fun prepareAndDecodeDeduplicatesAndRestoresImagePaths() {
        val prepared = assertIs<PaintDataCodecResult.Success<PreparedPaintDataBackup>>(
            PaintDataBackupCodec.prepare(
                snapshot = sampleSnapshot(),
                drafts = sampleDrafts(),
                exportedAt = 123L,
                exportedFromPlatform = PaintClientPlatform.DESKTOP,
            ),
        ).value

        assertEquals(2, prepared.imageCount)
        assertEquals(2, prepared.request.imageSources.size)
        assertFalse(prepared.request.manifestJson.contains("/local/reference.png"))
        assertTrue(prepared.request.manifestJson.contains("images/image_000001.png"))

        val restoredPaths = prepared.request.imageSources.associate { source ->
            source.archivePath to "/restored/${source.archivePath.substringAfterLast('/')}"
        }
        val decoded = assertIs<PaintDataCodecResult.Success<DecodedPaintDataBackup>>(
            PaintDataBackupCodec.decode(prepared.request.manifestJson, restoredPaths),
        ).value

        assertEquals(2, decoded.imageCount)
        assertEquals(PaintClientPlatform.DESKTOP, decoded.exportedFromPlatform)
        assertEquals(PaintClientPlatform.ANDROID, decoded.snapshot.sessions.single().originPlatform)
        assertEquals(PaintClientPlatform.DESKTOP, decoded.snapshot.messages.single().originPlatform)
        assertEquals(
            restoredPaths.getValue("images/image_000001.png"),
            decoded.snapshot.messages.first().images.first().localPath,
        )
        assertEquals(
            restoredPaths.getValue("images/image_000001.png"),
            decoded.drafts.getValue("session-1").selectedImages.first().uri,
        )
    }

    @Test
    fun decodeTreatsMissingPlatformFieldsInLegacyBackupAsUnknown() {
        val prepared = preparedBackup()
        val root = Json.parseToJsonElement(prepared.request.manifestJson).jsonObject.toMutableMap()
        root.remove("exportedFromPlatform")
        root["sessions"] = JsonArray(
            root.getValue("sessions").jsonArray.map { session ->
                JsonObject(session.jsonObject - "originPlatform")
            },
        )
        root["messages"] = JsonArray(
            root.getValue("messages").jsonArray.map { message ->
                JsonObject(message.jsonObject - "originPlatform")
            },
        )

        val decoded = assertIs<PaintDataCodecResult.Success<DecodedPaintDataBackup>>(
            PaintDataBackupCodec.decode(JsonObject(root).toString(), extractedPaths(prepared)),
        ).value

        assertEquals(PaintClientPlatform.UNKNOWN, decoded.exportedFromPlatform)
        assertEquals(PaintClientPlatform.UNKNOWN, decoded.snapshot.sessions.single().originPlatform)
        assertEquals(PaintClientPlatform.UNKNOWN, decoded.snapshot.messages.single().originPlatform)
    }

    @Test
    fun prepareOmitsImagesRejectedByPlatformWithoutDroppingConversationData() {
        val prepared = assertIs<PaintDataCodecResult.Success<PreparedPaintDataBackup>>(
            PaintDataBackupCodec.prepare(
                snapshot = sampleSnapshot(),
                drafts = sampleDrafts(),
                exportedAt = 123L,
                exportableImageIdentifiers = setOf("/local/result.webp"),
            ),
        ).value

        assertEquals(1, prepared.messageCount)
        assertEquals(1, prepared.imageCount)
        assertEquals(
            listOf("/local/result.webp"),
            prepared.request.imageSources.map { source -> source.sourceIdentifier },
        )

        val restoredPaths = prepared.request.imageSources.associate { source ->
            source.archivePath to "/restored/${source.archivePath.substringAfterLast('/')}"
        }
        val decoded = assertIs<PaintDataCodecResult.Success<DecodedPaintDataBackup>>(
            PaintDataBackupCodec.decode(prepared.request.manifestJson, restoredPaths),
        ).value

        assertEquals(2, decoded.snapshot.messages.single().images.size)
        assertEquals(null, decoded.snapshot.messages.single().images.first().localPath)
        assertEquals(
            "/restored/image_000001.webp",
            decoded.snapshot.messages.single().images.last().localPath,
        )
        assertTrue(decoded.drafts.getValue("session-1").selectedImages.isEmpty())
    }

    @Test
    fun decodeRejectsUnsupportedManifestVersion() {
        val prepared = preparedBackup()
        val unsupportedManifest = prepared.request.manifestJson.replaceFirst(
            oldValue = "\"version\": 1",
            newValue = "\"version\": 99",
        )

        val result = assertIs<PaintDataCodecResult.Failure>(
            PaintDataBackupCodec.decode(unsupportedManifest, extractedPaths(prepared)),
        )

        assertEquals(PaintDataTransferError.UNSUPPORTED_VERSION, result.error)
    }

    @Test
    fun decodeRejectsArchivePathTraversal() {
        val prepared = preparedBackup()
        val unsafeManifest = prepared.request.manifestJson.replace(
            oldValue = "images/image_000001.png",
            newValue = "../escape.png",
        )
        val extractedPaths = extractedPaths(prepared).toMutableMap().apply {
            val restored = remove("images/image_000001.png")
            put("../escape.png", restored.orEmpty())
        }

        val result = assertIs<PaintDataCodecResult.Failure>(
            PaintDataBackupCodec.decode(unsafeManifest, extractedPaths),
        )

        assertEquals(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY, result.error)
    }

    @Test
    fun decodeRejectsMissingImageFile() {
        val prepared = preparedBackup()
        val incompletePaths = extractedPaths(prepared) - "images/image_000001.png"

        val result = assertIs<PaintDataCodecResult.Failure>(
            PaintDataBackupCodec.decode(prepared.request.manifestJson, incompletePaths),
        )

        assertEquals(PaintDataTransferError.MISSING_IMAGE, result.error)
    }

    @Test
    fun decodeRejectsUnreferencedArchiveImage() {
        val prepared = preparedBackup()
        val root = Json.parseToJsonElement(prepared.request.manifestJson).jsonObject.toMutableMap()
        root["images"] = JsonArray(
            root.getValue("images").jsonArray + buildJsonObject {
                put("path", "images/unreferenced.png")
            },
        )
        val extractedPaths = extractedPaths(prepared) +
            ("images/unreferenced.png" to "/restored/unreferenced.png")

        val result = assertIs<PaintDataCodecResult.Failure>(
            PaintDataBackupCodec.decode(JsonObject(root).toString(), extractedPaths),
        )

        assertEquals(PaintDataTransferError.CORRUPTED_DATA, result.error)
    }

    @Test
    fun prepareRejectsMoreImagesThanTheImporterCanRead() {
        val images = List(PaintDataArchiveLimits.MAX_ENTRY_COUNT) { index ->
            PaintImage(
                id = "image-$index",
                localPath = "/local/image-$index.png",
                mimeType = "image/png",
            )
        }
        val snapshot = PaintDataSnapshot(
            sessions = listOf(PaintSession(id = "session-1")),
            messages = listOf(
                PaintMessage(
                    id = "message-1",
                    sessionId = "session-1",
                    senderIdentity = SenderIdentity.ASSISTANT,
                    messageContent = "",
                    messageType = MessageType.IMAGE,
                    images = images,
                ),
            ),
        )

        val result = assertIs<PaintDataCodecResult.Failure>(
            PaintDataBackupCodec.prepare(snapshot, emptyMap(), exportedAt = 123L),
        )

        assertEquals(PaintDataTransferError.ARCHIVE_TOO_LARGE, result.error)
    }

    private fun preparedBackup(): PreparedPaintDataBackup =
        assertIs<PaintDataCodecResult.Success<PreparedPaintDataBackup>>(
            PaintDataBackupCodec.prepare(sampleSnapshot(), sampleDrafts(), exportedAt = 123L),
        ).value

    private fun extractedPaths(prepared: PreparedPaintDataBackup): Map<String, String> =
        prepared.request.imageSources.associate { source ->
            source.archivePath to "/restored/${source.archivePath.substringAfterLast('/')}"
        }

    private fun sampleSnapshot(): PaintDataSnapshot = PaintDataSnapshot(
        sessions = listOf(
            PaintSession(
                id = "session-1",
                title = "Sample",
                originPlatform = PaintClientPlatform.ANDROID,
            ),
        ),
        messages = listOf(
            PaintMessage(
                id = "message-1",
                sessionId = "session-1",
                originPlatform = PaintClientPlatform.DESKTOP,
                senderIdentity = SenderIdentity.USER,
                messageContent = "Reference",
                messageType = MessageType.IMAGE,
                images = listOf(
                    PaintImage(
                        id = "image-1",
                        localPath = "/local/reference.png",
                        mimeType = "image/png",
                        isReference = true,
                    ),
                    PaintImage(
                        id = "image-2",
                        localPath = "/local/result.webp",
                        mimeType = "image/webp",
                    ),
                ),
            ),
        ),
    )

    private fun sampleDrafts(): Map<String, PaintSessionDraft> = mapOf(
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
    )
}
