package com.example.livewallpaper.feature.aipaint.domain.usecase

import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyBase64
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintImageV1
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintMessageV1
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.toDomain
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.toLegacyV1
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveSource
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveLimits
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Builds and validates the platform-independent manifest stored in painting ZIP archives. */
internal object PaintDataBackupCodec {
    private const val BACKUP_FORMAT = "live-wallpaper-paint-data"
    private const val BACKUP_VERSION = 1
    private val safeImagePath = Regex("^images/[A-Za-z0-9][A-Za-z0-9_.-]*$")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }

    /**
     * Rewrites platform image identifiers to safe archive paths and serializes a manifest.
     *
     * @param snapshot Complete local conversation data.
     * @param drafts Unsent prompts and reference images.
     * @param exportedAt Backup creation timestamp.
     * @param exportableImageIdentifiers Image identifiers approved by the platform for packaging.
     * Images outside this set remain as message metadata without a local path and are removed from drafts.
     * @param exportedFromPlatform Platform that created this backup archive.
     * @return Prepared manifest and source image list, or a validation error.
     */
    fun prepare(
        snapshot: PaintDataSnapshot,
        drafts: Map<String, PaintSessionDraft>,
        exportedAt: Long,
        exportableImageIdentifiers: Set<String>? = null,
        exportedFromPlatform: PaintClientPlatform = PaintClientPlatform.UNKNOWN,
    ): PaintDataCodecResult<PreparedPaintDataBackup> = try {
        validateConversationData(snapshot.sessions, snapshot.messages)
        validateDrafts(drafts)

        val sourcesByIdentifier = linkedMapOf<String, PaintDataArchiveSource>()

        fun archivePath(sourceIdentifier: String, mimeType: String): String {
            return sourcesByIdentifier.getOrPut(sourceIdentifier) {
                val index = sourcesByIdentifier.size + 1
                PaintDataArchiveSource(
                    sourceIdentifier = sourceIdentifier,
                    archivePath = "images/image_${index.toString().padStart(6, '0')}.${imageExtension(mimeType)}",
                )
            }.archivePath
        }

        val archivedMessages = snapshot.messages.map { message ->
            message.toLegacyV1(
                images = message.images.map { image ->
                    archivePaintImage(
                        image = image,
                        exportableImageIdentifiers = exportableImageIdentifiers,
                        archivePath = ::archivePath,
                    )
                },
            )
        }
        val archivedDrafts = drafts.mapValues { (_, draft) ->
            draft.copy(
                selectedImages = draft.selectedImages
                    .filter { image -> exportableImageIdentifiers == null || image.uri in exportableImageIdentifiers }
                    .map { image -> image.copy(uri = archivePath(image.uri, image.mimeType)) },
            )
        }
        val imageEntries = sourcesByIdentifier.values.map { source ->
            PaintDataBackupImageEntry(path = source.archivePath)
        }
        if (imageEntries.size + 1 > PaintDataArchiveLimits.MAX_ENTRY_COUNT) {
            invalid(PaintDataTransferError.ARCHIVE_TOO_LARGE)
        }
        val backup = PaintDataBackupManifest(
            exportedAt = exportedAt,
            exportedFromPlatform = exportedFromPlatform,
            sessions = snapshot.sessions,
            messages = archivedMessages,
            drafts = archivedDrafts,
            images = imageEntries,
        )
        PaintDataCodecResult.Success(
            PreparedPaintDataBackup(
                request = PaintDataArchiveWriteRequest(
                    manifestJson = json.encodeToString(backup),
                    imageSources = sourcesByIdentifier.values.toList(),
                ),
                sessionCount = snapshot.sessions.size,
                messageCount = snapshot.messages.size,
                imageCount = imageEntries.size,
            ),
        )
    } catch (error: PaintDataValidationException) {
        PaintDataCodecResult.Failure(error.error)
    } catch (_: SerializationException) {
        PaintDataCodecResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
    } catch (_: IllegalArgumentException) {
        PaintDataCodecResult.Failure(PaintDataTransferError.CORRUPTED_DATA)
    }

    /**
     * Validates a manifest and replaces archive paths with extracted app-owned file paths.
     *
     * @param manifestJson Extracted manifest content.
     * @param imagePathsByArchivePath Extracted image path mapping.
     * @return Restorable conversation data and drafts, or a validation error.
     */
    fun decode(
        manifestJson: String,
        imagePathsByArchivePath: Map<String, String>,
    ): PaintDataCodecResult<DecodedPaintDataBackup> {
        val backup = try {
            json.decodeFromString<PaintDataBackupManifest>(manifestJson)
        } catch (_: SerializationException) {
            return PaintDataCodecResult.Failure(PaintDataTransferError.INVALID_ARCHIVE)
        } catch (_: IllegalArgumentException) {
            return PaintDataCodecResult.Failure(PaintDataTransferError.INVALID_ARCHIVE)
        }

        return try {
            if (backup.format != BACKUP_FORMAT) {
                return PaintDataCodecResult.Failure(PaintDataTransferError.INVALID_ARCHIVE)
            }
            if (backup.version != BACKUP_VERSION) {
                return PaintDataCodecResult.Failure(PaintDataTransferError.UNSUPPORTED_VERSION)
            }

            val messagesForValidation = backup.messages.map { message ->
                message.toDomain(message.images.map { image -> image.toDomain() })
            }
            validateConversationData(backup.sessions, messagesForValidation)
            validateDrafts(backup.drafts)
            val declaredImagePaths = backup.images.map { it.path }
            if (declaredImagePaths.size != declaredImagePaths.distinct().size) {
                invalid(PaintDataTransferError.CORRUPTED_DATA)
            }
            if (declaredImagePaths.any { !safeImagePath.matches(it) }) {
                invalid(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY)
            }
            val declaredImagePathSet = declaredImagePaths.toSet()
            val referencedPaths = buildSet {
                backup.messages.forEach { message ->
                    message.images.forEach { image ->
                        val localPath = image.localPath ?: return@forEach
                        if (localPath in declaredImagePathSet || image.base64Data.isNullOrBlank()) add(localPath)
                    }
                }
                backup.drafts.values.forEach { draft ->
                    draft.selectedImages.mapTo(this) { it.uri }
                }
            }
            if (!declaredImagePathSet.containsAll(referencedPaths)) {
                invalid(PaintDataTransferError.MISSING_IMAGE)
            }
            if (!referencedPaths.containsAll(declaredImagePathSet)) {
                invalid(PaintDataTransferError.CORRUPTED_DATA)
            }
            if (imagePathsByArchivePath.keys != declaredImagePathSet) {
                invalid(PaintDataTransferError.MISSING_IMAGE)
            }

            val restoredMessages = backup.messages.map { message ->
                message.toDomain(
                    images = message.images.map { image ->
                        val archivedPath = image.localPath
                        if (archivedPath == null || archivedPath !in declaredImagePathSet) {
                            image.toDomain(localPath = null)
                        } else {
                            image.toDomain(localPath = imagePathsByArchivePath.getValue(archivedPath))
                        }
                    },
                )
            }
            val embeddedImages = backup.messages.flatMap { message ->
                message.images.mapNotNull { image ->
                    if (image.localPath in declaredImagePathSet || image.base64Data.isNullOrBlank()) {
                        return@mapNotNull null
                    }
                    val bytes = LegacyBase64.decode(image.base64Data)
                        ?: invalid(PaintDataTransferError.CORRUPTED_DATA)
                    LegacyEmbeddedBackupImage(
                        sessionId = message.sessionId,
                        messageId = message.id,
                        imageId = image.id,
                        mimeType = image.mimeType,
                        bytes = bytes,
                    )
                }
            }
            val restoredDrafts = backup.drafts.mapValues { (_, draft) ->
                draft.copy(
                    selectedImages = draft.selectedImages.map { image ->
                        image.copy(uri = imagePathsByArchivePath.getValue(image.uri))
                    },
                )
            }
            PaintDataCodecResult.Success(
                DecodedPaintDataBackup(
                    snapshot = PaintDataSnapshot(
                        sessions = backup.sessions,
                        messages = restoredMessages,
                    ),
                    drafts = restoredDrafts,
                    imageCount = declaredImagePaths.size + embeddedImages.size,
                    exportedFromPlatform = backup.exportedFromPlatform,
                    embeddedImages = embeddedImages,
                ),
            )
        } catch (error: PaintDataValidationException) {
            PaintDataCodecResult.Failure(error.error)
        } catch (_: NoSuchElementException) {
            PaintDataCodecResult.Failure(PaintDataTransferError.MISSING_IMAGE)
        }
    }

    private fun archivePaintImage(
        image: PaintImage,
        exportableImageIdentifiers: Set<String>?,
        archivePath: (String, String) -> String,
    ): LegacyPaintImageV1 {
        val localPath = image.localPath ?: return image.toLegacyV1(localPath = null)
        if (exportableImageIdentifiers != null && localPath !in exportableImageIdentifiers) {
            return image.toLegacyV1(localPath = null)
        }
        return image.toLegacyV1(localPath = archivePath(localPath, image.mimeType))
    }

    private fun validateConversationData(
        sessions: List<PaintSession>,
        messages: List<PaintMessage>,
    ) {
        val sessionIds = sessions.map { it.id }
        if (sessionIds.any { it.isBlank() } || sessionIds.size != sessionIds.distinct().size) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        val sessionIdSet = sessionIds.toSet()
        val messageIds = messages.map { it.id }
        if (messageIds.any { it.isBlank() } || messageIds.size != messageIds.distinct().size) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        if (messages.any { it.sessionId !in sessionIdSet }) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        if (messages.any { message -> message.images.any { it.localPath?.isBlank() == true } }) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        val imageIds = messages.flatMap { message -> message.images.map(PaintImage::id) }
        if (imageIds.any(String::isBlank) || imageIds.size != imageIds.distinct().size) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
    }

    private fun validateDrafts(drafts: Map<String, PaintSessionDraft>) {
        if (drafts.keys.any { it.isBlank() }) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        if (drafts.values.any { draft -> draft.selectedImages.any { it.uri.isBlank() } }) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
        if (drafts.values.any { draft ->
                val imageIds = draft.selectedImages.map(PaintDraftImage::id)
                imageIds.any(String::isBlank) || imageIds.size != imageIds.distinct().size
            }
        ) {
            invalid(PaintDataTransferError.CORRUPTED_DATA)
        }
    }

    private fun imageExtension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        else -> "png"
    }

    private fun invalid(error: PaintDataTransferError): Nothing {
        throw PaintDataValidationException(error)
    }

    @Serializable
    private data class PaintDataBackupManifest(
        val format: String = BACKUP_FORMAT,
        val version: Int = BACKUP_VERSION,
        val exportedAt: Long,
        val exportedFromPlatform: PaintClientPlatform = PaintClientPlatform.UNKNOWN,
        val sessions: List<PaintSession>,
        val messages: List<LegacyPaintMessageV1>,
        val drafts: Map<String, PaintSessionDraft> = emptyMap(),
        val images: List<PaintDataBackupImageEntry> = emptyList(),
    )

    @Serializable
    private data class PaintDataBackupImageEntry(
        val path: String,
    )
}

/** Internal result used while preparing and decoding painting manifests. */
internal sealed interface PaintDataCodecResult<out T> {
    /** Successful codec operation. */
    data class Success<T>(val value: T) : PaintDataCodecResult<T>

    /** Failed codec operation. */
    data class Failure(val error: PaintDataTransferError) : PaintDataCodecResult<Nothing>
}

/**
 * Prepared archive payload and export statistics.
 *
 * @property request Manifest and image sources for the platform writer.
 * @property sessionCount Exported session count.
 * @property messageCount Exported message count.
 * @property imageCount Exported unique image count.
 */
internal data class PreparedPaintDataBackup(
    val request: PaintDataArchiveWriteRequest,
    val sessionCount: Int,
    val messageCount: Int,
    val imageCount: Int,
)

/**
 * Validated data reconstructed from an extracted archive.
 *
 * @property snapshot Restorable conversations.
 * @property drafts Restorable unsent drafts.
 * @property imageCount Restored unique image count.
 * @property exportedFromPlatform Platform that created the decoded archive.
 */
internal data class DecodedPaintDataBackup(
    val snapshot: PaintDataSnapshot,
    val drafts: Map<String, PaintSessionDraft>,
    val imageCount: Int,
    val exportedFromPlatform: PaintClientPlatform,
    val embeddedImages: List<LegacyEmbeddedBackupImage> = emptyList(),
)

/** Decoded historical v1 payload waiting to be materialized before the database transaction. */
internal data class LegacyEmbeddedBackupImage(
    val sessionId: String,
    val messageId: String,
    val imageId: String,
    val mimeType: String,
    val bytes: ByteArray,
)

private class PaintDataValidationException(
    val error: PaintDataTransferError,
) : IllegalArgumentException()
