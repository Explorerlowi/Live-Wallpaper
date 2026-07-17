package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.prefs.Preferences

/**
 * Desktop-only AI paint draft snapshot persisted outside Preferences to avoid key-value length limits.
 */
data class DesktopPaintDraft(
    val promptText: String = "",
    val selectedImages: List<SelectedImage> = emptyList(),
    val selectedModel: PaintModel? = null,
    val selectedAspectRatio: AspectRatio? = null,
    val selectedResolution: Resolution? = null,
    val selectedGptSize: GptImageSize? = null,
    val selectedGptQuality: GptImageQuality? = null,
    val selectedGptFormat: GptOutputFormat? = null,
)

/** Strict result used when every desktop draft must be preserved in a backup. */
sealed interface DesktopPaintDraftBackupReadResult {
    /** All persisted desktop drafts were decoded without dropping fields or missing images. */
    data class Success(val drafts: Map<String, DesktopPaintDraft>) : DesktopPaintDraftBackupReadResult

    /** At least one persisted draft could not be decoded safely. */
    data object Corrupted : DesktopPaintDraftBackupReadResult
}

/**
 * Stores desktop AI paint drafts as per-session JSON files and migrates the legacy Preferences chunks on first read.
 */
object DesktopPaintDraftStore {
    private val preferences = Preferences.userRoot().node(DRAFT_PREFERENCES_NODE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun readDraft(sessionId: String): DesktopPaintDraft {
        val fileDraft = readFileDraft(sessionId)
        if (fileDraft != null) return fileDraft.validImagesOnly()

        val legacyDraft = readLegacyDraft(sessionId)
        if (legacyDraft != null) {
            writeDraft(sessionId, legacyDraft)
            clearLegacyDraft(sessionId)
            return legacyDraft.validImagesOnly()
        }
        return DesktopPaintDraft()
    }

    fun writeDraft(sessionId: String, draft: DesktopPaintDraft) {
        writeDraftStrict(sessionId, draft)
    }

    fun deleteDraft(sessionId: String) {
        deleteDraftStrict(sessionId)
    }

    /**
     * Reads every file-backed draft plus legacy drafts belonging to current sessions.
     *
     * Unlike [readDraft], this method keeps missing image references so archive creation fails
     * visibly instead of silently omitting them.
     */
    fun readDraftsForBackup(sessionIds: Set<String>): DesktopPaintDraftBackupReadResult {
        val drafts = runCatching {
            val result = linkedMapOf<String, DesktopPaintDraft>()
            val directory = DesktopAiPaintStoragePaths.draftDirectory()
            check(directory.isDirectory) { "Desktop draft path is not a directory" }
            val files = directory
                .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                .orEmpty()
            files.forEach { file ->
                val persisted = json.decodeFromString(
                    PersistedDraft.serializer(),
                    file.readText(Charsets.UTF_8),
                )
                val key = persisted.draftKey?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                check(key !in result) { "Duplicate desktop draft key" }
                result[key] = persisted.toDraft()
            }
            result
        }.getOrElse {
            return DesktopPaintDraftBackupReadResult.Corrupted
        }

        sessionIds.forEach { sessionId ->
            if (sessionId in drafts) return@forEach
            val rawResult = runCatching { readLegacyDraftContent(sessionId) }
            if (rawResult.isFailure) return DesktopPaintDraftBackupReadResult.Corrupted
            val raw = rawResult.getOrNull().orEmpty()
            if (raw.isBlank()) return@forEach
            val draft = decodeLegacyDraftStrict(raw)
                ?: return DesktopPaintDraftBackupReadResult.Corrupted
            drafts[sessionId] = draft
        }
        return DesktopPaintDraftBackupReadResult.Success(
            drafts.filterValues { draft -> !draft.isEmpty() },
        )
    }

    /** Merges a set of drafts and restores the previous file-backed state if any write fails. */
    fun mergeDraftsStrict(drafts: Map<String, DesktopPaintDraft>): Boolean {
        if (drafts.isEmpty()) return true
        val existing = when (val result = readDraftsForBackup(emptySet())) {
            DesktopPaintDraftBackupReadResult.Corrupted -> return false
            is DesktopPaintDraftBackupReadResult.Success -> result.drafts
        }
        return replaceFileBackedDraftsWithRollback(existing + drafts, existing)
    }

    /** Replaces every file-backed draft and restores the previous state if replacement fails. */
    fun replaceDraftsStrict(drafts: Map<String, DesktopPaintDraft>): Boolean {
        val existing = when (val result = readDraftsForBackup(emptySet())) {
            DesktopPaintDraftBackupReadResult.Corrupted -> return false
            is DesktopPaintDraftBackupReadResult.Success -> result.drafts
        }
        return replaceFileBackedDraftsWithRollback(drafts, existing)
    }

    /** Writes one draft atomically and reports storage failures. */
    fun writeDraftStrict(sessionId: String, draft: DesktopPaintDraft): Boolean {
        if (draft.isEmpty()) return deleteDraftStrict(sessionId)
        return runCatching {
            val file = draftFile(sessionId)
            check(file.parentFile?.mkdirs() != false || file.parentFile?.isDirectory == true)
            val temporary = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
            try {
                temporary.writeText(
                    json.encodeToString(PersistedDraft.serializer(), draft.toPersisted(sessionId)),
                    Charsets.UTF_8,
                )
                moveReplacing(temporary, file)
            } finally {
                temporary.delete()
            }
            clearLegacyDraft(sessionId)
        }.isSuccess
    }

    /** Deletes one file-backed and legacy draft and reports storage failures. */
    fun deleteDraftStrict(sessionId: String): Boolean = runCatching {
        val file = draftFile(sessionId)
        check(!file.exists() || file.delete())
        clearLegacyDraft(sessionId)
    }.isSuccess

    private fun replaceFileBackedDraftsWithRollback(
        replacement: Map<String, DesktopPaintDraft>,
        previous: Map<String, DesktopPaintDraft>,
    ): Boolean {
        if (writeFileBackedDraftSet(replacement)) return true
        writeFileBackedDraftSet(previous)
        return false
    }

    private fun writeFileBackedDraftSet(drafts: Map<String, DesktopPaintDraft>): Boolean = runCatching {
        val directory = DesktopAiPaintStoragePaths.draftDirectory()
        check(directory.isDirectory)
        directory
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .forEach { file -> check(file.delete()) }
        drafts.forEach { (key, draft) ->
            check(writeDraftStrict(key, draft))
        }
    }.isSuccess

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun readFileDraft(sessionId: String): DesktopPaintDraft? = runCatching {
        val file = draftFile(sessionId)
        if (!file.isFile) return@runCatching null
        json.decodeFromString(PersistedDraft.serializer(), file.readText(Charsets.UTF_8)).toDraft()
    }.getOrNull()

    private fun readLegacyDraft(sessionId: String): DesktopPaintDraft? {
        val raw = runCatching { readLegacyDraftContent(sessionId) }.getOrDefault("")
        if (raw.isBlank()) return null
        return decodeLegacyDraft(raw)
    }

    private fun readLegacyDraftContent(sessionId: String): String {
        val chunkCount = preferences.getInt(draftChunkCountKey(sessionId), 0)
        check(chunkCount in 0..MAX_DRAFT_CHUNKS) { "Invalid legacy desktop draft chunk count" }
        return if (chunkCount in 1..MAX_DRAFT_CHUNKS) {
            buildString {
                repeat(chunkCount) { index ->
                    append(
                        checkNotNull(preferences.get(draftChunkKey(sessionId, index), null)) {
                            "Missing legacy desktop draft chunk"
                        },
                    )
                }
            }
        } else {
            preferences.get(draftKey(sessionId), "")
        }
    }

    private fun decodeLegacyDraft(raw: String): DesktopPaintDraft? = runCatching {
        val lines = raw.lineSequence().toList()
        if (lines.size < 2 || lines[0] != LEGACY_DRAFT_FORMAT_VERSION) return@runCatching null
        val decoder = Base64.getUrlDecoder()
        val prompt = String(decoder.decode(lines[1]), Charsets.UTF_8)
        val images = lines.drop(2).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 5) return@mapNotNull null
            SelectedImage(
                id = parts[0],
                uri = String(decoder.decode(parts[1]), Charsets.UTF_8),
                mimeType = String(decoder.decode(parts[2]), Charsets.UTF_8),
                width = parts[3].toIntOrNull() ?: 0,
                height = parts[4].toIntOrNull() ?: 0
            )
        }
        DesktopPaintDraft(promptText = prompt, selectedImages = images)
    }.getOrNull()

    private fun decodeLegacyDraftStrict(raw: String): DesktopPaintDraft? = runCatching {
        val lines = raw.lineSequence().toList()
        if (lines.size < 2 || lines[0] != LEGACY_DRAFT_FORMAT_VERSION) return@runCatching null
        val decoder = Base64.getUrlDecoder()
        val images = lines.drop(2).map { line ->
            val parts = line.split('\t')
            if (parts.size != 5) return@runCatching null
            val width = parts[3].toIntOrNull() ?: return@runCatching null
            val height = parts[4].toIntOrNull() ?: return@runCatching null
            SelectedImage(
                id = parts[0],
                uri = String(decoder.decode(parts[1]), Charsets.UTF_8),
                mimeType = String(decoder.decode(parts[2]), Charsets.UTF_8),
                width = width,
                height = height,
            )
        }
        DesktopPaintDraft(
            promptText = String(decoder.decode(lines[1]), Charsets.UTF_8),
            selectedImages = images,
        )
    }.getOrNull()

    private fun clearLegacyDraft(sessionId: String) {
        runCatching {
            val chunkCount = preferences.getInt(draftChunkCountKey(sessionId), 0)
            preferences.remove(draftKey(sessionId))
            preferences.remove(draftChunkCountKey(sessionId))
            repeat(chunkCount.coerceIn(0, MAX_DRAFT_CHUNKS)) { index ->
                preferences.remove(draftChunkKey(sessionId, index))
            }
        }
    }

    private fun DesktopPaintDraft.validImagesOnly(): DesktopPaintDraft = copy(
        selectedImages = selectedImages.filter { File(it.uri.removePrefix("file://")).isFile }
    )

    private fun DesktopPaintDraft.isEmpty(): Boolean =
        promptText.isBlank() && selectedImages.isEmpty()

    private fun draftFile(sessionId: String): File =
        File(DesktopAiPaintStoragePaths.draftDirectory(), "${safeFileName(sessionId)}.json")

    private fun safeFileName(sessionId: String): String =
        sessionId.map { char ->
            if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_'
        }.joinToString("")

    private fun draftKey(sessionId: String): String = "paint_draft_$sessionId"

    private fun draftChunkCountKey(sessionId: String): String = "${draftKey(sessionId)}_parts"

    private fun draftChunkKey(sessionId: String, index: Int): String = "${draftKey(sessionId)}_$index"

    private fun DesktopPaintDraft.toPersisted(draftKey: String): PersistedDraft = PersistedDraft(
        draftKey = draftKey,
        promptText = promptText,
        selectedImages = selectedImages.map { it.toPersisted() },
        selectedModel = selectedModel?.name,
        selectedAspectRatio = selectedAspectRatio?.name,
        selectedResolution = selectedResolution?.name,
        selectedGptSize = selectedGptSize?.name,
        selectedGptQuality = selectedGptQuality?.name,
        selectedGptFormat = selectedGptFormat?.name,
    )

    private fun PersistedDraft.toDraft(): DesktopPaintDraft = DesktopPaintDraft(
        promptText = promptText,
        selectedImages = selectedImages.map { it.toSelectedImage() },
        selectedModel = selectedModel?.let { value -> enumValueOrNull<PaintModel>(value) },
        selectedAspectRatio = selectedAspectRatio?.let { value -> enumValueOrNull<AspectRatio>(value) },
        selectedResolution = selectedResolution?.let { value -> enumValueOrNull<Resolution>(value) },
        selectedGptSize = selectedGptSize?.let { value -> enumValueOrNull<GptImageSize>(value) },
        selectedGptQuality = selectedGptQuality?.let { value -> enumValueOrNull<GptImageQuality>(value) },
        selectedGptFormat = selectedGptFormat?.let { value -> enumValueOrNull<GptOutputFormat>(value) },
    )

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private fun SelectedImage.toPersisted(): PersistedSelectedImage = PersistedSelectedImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )

    private fun PersistedSelectedImage.toSelectedImage(): SelectedImage = SelectedImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )

    private const val DRAFT_PREFERENCES_NODE = "com.example.livewallpaper.desktop"
    private const val LEGACY_DRAFT_FORMAT_VERSION = "v1"
    private const val MAX_DRAFT_CHUNKS = 4096
}

@Serializable
private data class PersistedDraft(
    val draftKey: String? = null,
    val promptText: String = "",
    val selectedImages: List<PersistedSelectedImage> = emptyList(),
    val selectedModel: String? = null,
    val selectedAspectRatio: String? = null,
    val selectedResolution: String? = null,
    val selectedGptSize: String? = null,
    val selectedGptQuality: String? = null,
    val selectedGptFormat: String? = null,
)

@Serializable
private data class PersistedSelectedImage(
    val id: String,
    val uri: String,
    val mimeType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0,
)
