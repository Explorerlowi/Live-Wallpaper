package com.example.livewallpaper.feature.aipaint.domain.model

import kotlinx.serialization.Serializable

/**
 * A complete, platform-independent snapshot of painting conversations.
 *
 * @property sessions All saved painting sessions.
 * @property messages All messages belonging to [sessions].
 */
data class PaintDataSnapshot(
    val sessions: List<PaintSession>,
    val messages: List<PaintMessage>,
)

/** Complete transactional state persisted by the painting database. */
data class PaintStoredData(
    val snapshot: PaintDataSnapshot,
    val drafts: Map<String, PaintSessionDraft>,
)

/** Result of reading all transactional painting data. */
sealed interface PaintStoredDataReadResult {
    /** Every conversation and draft was decoded successfully. */
    data class Success(val data: PaintStoredData) : PaintStoredDataReadResult

    /** Stored rows could not be mapped without losing data. */
    data object Corrupted : PaintStoredDataReadResult
}

/** Observable readiness of the local painting storage backend. */
sealed interface PaintStorageState {
    /** The database driver and migration marker are being inspected. */
    data object Initializing : PaintStorageState

    /** Legacy conversations and drafts are being copied transactionally. */
    data object Migrating : PaintStorageState

    /** SQLDelight is the active storage backend. */
    data object Ready : PaintStorageState

    /** Verified SQL data remains readable, but writes are blocked until the epoch marker is repaired. */
    data class ReadOnly(val reason: PaintStorageFailure) : PaintStorageState

    /** Migration failed and the retained legacy backend is active for this process. */
    data class LegacyFallback(val reason: PaintStorageFailure) : PaintStorageState

    /** SQL-era storage cannot be trusted; all painting writes remain disabled until recovery. */
    data class RecoveryRequired(val reason: PaintStorageFailure) : PaintStorageState
}

/** Normalized non-user-facing reasons for falling back to legacy storage. */
enum class PaintStorageFailure {
    CORRUPTED_LEGACY_DATA,
    IMAGE_MATERIALIZATION_FAILED,
    DATABASE_WRITE_FAILED,
    DATABASE_UNAVAILABLE,
    MIGRATION_STATE_READ_FAILED,
    MIGRATION_STATE_WRITE_FAILED,
    IMPORT_ROLLBACK_FAILED,
    UNKNOWN,
}

/** Result of reading a complete painting conversation snapshot from local storage. */
sealed interface PaintDataSnapshotReadResult {
    /** Successfully decoded every saved session and referenced message. */
    data class Success(val snapshot: PaintDataSnapshot) : PaintDataSnapshotReadResult

    /** At least one saved session, message ID list, or message could not be decoded safely. */
    data object Corrupted : PaintDataSnapshotReadResult
}

/**
 * An unsent reference image stored in a painting draft.
 *
 * @property id Stable image identifier.
 * @property uri Platform resource identifier or restored local file path.
 * @property mimeType Image MIME type.
 * @property width Pixel width when known.
 * @property height Pixel height when known.
 */
@Serializable
data class PaintDraftImage(
    val id: String,
    val uri: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * Unsent input associated with a painting session or the temporary session.
 *
 * @property promptText Unsent prompt text.
 * @property selectedImages Unsent reference images.
 * @property selectedModel Draft-specific model selection when it differs from the saved session.
 * @property selectedAspectRatio Draft-specific aspect ratio selection.
 * @property selectedResolution Draft-specific Gemini resolution selection.
 * @property selectedGptSize Draft-specific GPT image size selection.
 * @property selectedGptQuality Draft-specific GPT quality selection.
 * @property selectedGptFormat Draft-specific GPT output format selection.
 */
@Serializable
data class PaintSessionDraft(
    val promptText: String = "",
    val selectedImages: List<PaintDraftImage> = emptyList(),
    val selectedModel: PaintModel? = null,
    val selectedAspectRatio: AspectRatio? = null,
    val selectedResolution: Resolution? = null,
    val selectedGptSize: GptImageSize? = null,
    val selectedGptQuality: GptImageQuality? = null,
    val selectedGptFormat: GptOutputFormat? = null,
)

/** Result of reading every persisted painting draft for export. */
sealed interface PaintDraftReadResult {
    /** Successfully decoded all persisted drafts. */
    data class Success(val drafts: Map<String, PaintSessionDraft>) : PaintDraftReadResult

    /** At least one persisted draft could not be decoded without data loss. */
    data object Corrupted : PaintDraftReadResult
}

/**
 * Summary returned after painting data has been merged into local storage.
 *
 * @property importedSessionCount Number of sessions supplied by the archive.
 * @property importedMessageCount Number of messages supplied by the archive.
 * @property totalSessionCount Total number of sessions after merging.
 */
data class PaintDataImportSummary(
    val importedSessionCount: Int,
    val importedMessageCount: Int,
    val totalSessionCount: Int,
)

/** Result of merging validated painting data into local conversation storage. */
sealed interface PaintDataMergeResult {
    /** Merge completed without dropping existing local data. */
    data class Success(val summary: PaintDataImportSummary) : PaintDataMergeResult

    /** Existing local data was corrupted, so no imported data was written. */
    data object CorruptedExistingData : PaintDataMergeResult
}

/** Result of an import commit whose snapshot, merge, and rollback share one storage gate. */
sealed interface PaintDataImportCommitResult {
    /** The imported bundle was committed successfully. */
    data class Success(val summary: PaintDataImportSummary) : PaintDataImportCommitResult

    /** Existing rows were corrupted, so no import mutation was attempted. */
    data object CorruptedExistingData : PaintDataImportCommitResult

    /** The merge failed and the exact pre-import state was restored. */
    data object Failed : PaintDataImportCommitResult

    /** Neither the merge nor restoration could establish a trustworthy state. */
    data object RollbackFailed : PaintDataImportCommitResult

    /** A live image generation is still updating conversation rows. */
    data object Busy : PaintDataImportCommitResult
}

/**
 * A file that must be written into a painting data archive.
 *
 * @property sourceIdentifier Platform resource identifier for the source image.
 * @property archivePath Safe relative path used inside the ZIP archive.
 */
data class PaintDataArchiveSource(
    val sourceIdentifier: String,
    val archivePath: String,
)

/**
 * Input passed to the platform archive writer.
 *
 * @property manifestJson Versioned painting data manifest.
 * @property imageSources Image resources referenced by the manifest.
 */
data class PaintDataArchiveWriteRequest(
    val manifestJson: String,
    val imageSources: List<PaintDataArchiveSource>,
)

/**
 * Files extracted from a painting data archive.
 *
 * @property extractionId Opaque identifier used to discard an invalid extraction.
 * @property manifestJson Extracted versioned manifest.
 * @property imagePathsByArchivePath Mapping from ZIP paths to restored local files.
 */
data class ExtractedPaintDataArchive(
    val extractionId: String,
    val manifestJson: String,
    val imagePathsByArchivePath: Map<String, String>,
)

/** Shared safety limits used by archive preparation and platform ZIP implementations. */
object PaintDataArchiveLimits {
    const val MAX_ENTRY_COUNT = 10_000
    const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
    const val MAX_IMAGE_BYTES = 512L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
}

/** Errors that can be shown for painting data import or export. */
enum class PaintDataTransferError {
    FILE_ACCESS,
    INVALID_ARCHIVE,
    UNSUPPORTED_VERSION,
    ARCHIVE_TOO_LARGE,
    UNSAFE_ARCHIVE_ENTRY,
    MISSING_IMAGE,
    CORRUPTED_DATA,
    STORAGE_BUSY,
    STORAGE_RECOVERY_REQUIRED,
    UNKNOWN,
}

/** Result of a platform ZIP archive operation. */
sealed interface PaintDataArchiveResult<out T> {
    /** Successful archive operation. */
    data class Success<T>(val value: T) : PaintDataArchiveResult<T>

    /** Failed archive operation. */
    data class Failure(val error: PaintDataTransferError) : PaintDataArchiveResult<Nothing>
}

/** Result returned by the export painting data use case. */
sealed interface ExportPaintDataResult {
    /**
     * Successful export statistics.
     *
     * @property sessionCount Exported session count.
     * @property messageCount Exported message count.
     * @property imageCount Exported unique image count.
     */
    data class Success(
        val sessionCount: Int,
        val messageCount: Int,
        val imageCount: Int,
    ) : ExportPaintDataResult

    /** Failed export. */
    data class Failure(val error: PaintDataTransferError) : ExportPaintDataResult
}

/** Result returned by the import painting data use case. */
sealed interface ImportPaintDataResult {
    /**
     * Successful import statistics.
     *
     * @property importedSessionCount Imported session count.
     * @property importedMessageCount Imported message count.
     * @property imageCount Restored unique image count.
     * @property totalSessionCount Total session count after merging.
     */
    data class Success(
        val importedSessionCount: Int,
        val importedMessageCount: Int,
        val imageCount: Int,
        val totalSessionCount: Int,
    ) : ImportPaintDataResult

    /** Failed import. */
    data class Failure(val error: PaintDataTransferError) : ImportPaintDataResult
}

/** Result of validating and inspecting a painting backup without mutating local data. */
sealed interface PreviewPaintDataImportResult {
    /**
     * Valid backup summary shown before the user confirms an import.
     *
     * @property sessionCount Session count declared by the validated backup.
     * @property messageCount Message count declared by the validated backup.
     * @property draftCount Saved unsent draft count.
     * @property imageCount Validated image file count.
     * @property exportedFromPlatform Platform that created the backup.
     */
    data class Success(
        val sessionCount: Int,
        val messageCount: Int,
        val draftCount: Int,
        val imageCount: Int,
        val exportedFromPlatform: PaintClientPlatform,
    ) : PreviewPaintDataImportResult

    /** Backup inspection failed before any local data was changed. */
    data class Failure(val error: PaintDataTransferError) : PreviewPaintDataImportResult
}
