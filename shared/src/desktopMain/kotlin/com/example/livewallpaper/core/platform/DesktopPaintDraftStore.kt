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
import java.util.Base64
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
        if (draft.isEmpty()) {
            deleteDraft(sessionId)
            return
        }
        runCatching {
            val file = draftFile(sessionId)
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(PersistedDraft.serializer(), draft.toPersisted()), Charsets.UTF_8)
            clearLegacyDraft(sessionId)
        }
    }

    fun deleteDraft(sessionId: String) {
        runCatching { draftFile(sessionId).delete() }
        clearLegacyDraft(sessionId)
    }

    private fun readFileDraft(sessionId: String): DesktopPaintDraft? = runCatching {
        val file = draftFile(sessionId)
        if (!file.isFile) return@runCatching null
        json.decodeFromString(PersistedDraft.serializer(), file.readText(Charsets.UTF_8)).toDraft()
    }.getOrNull()

    private fun readLegacyDraft(sessionId: String): DesktopPaintDraft? {
        val raw = runCatching {
            val chunkCount = preferences.getInt(draftChunkCountKey(sessionId), 0)
            if (chunkCount in 1..MAX_DRAFT_CHUNKS) {
                buildString {
                    repeat(chunkCount) { index ->
                        append(preferences.get(draftChunkKey(sessionId, index), ""))
                    }
                }
            } else {
                preferences.get(draftKey(sessionId), "")
            }
        }.getOrDefault("")
        if (raw.isBlank()) return null
        return decodeLegacyDraft(raw)
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
        sessionId.map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_' }.joinToString("")

    private fun draftKey(sessionId: String): String = "paint_draft_$sessionId"

    private fun draftChunkCountKey(sessionId: String): String = "${draftKey(sessionId)}_parts"

    private fun draftChunkKey(sessionId: String, index: Int): String = "${draftKey(sessionId)}_$index"

    private fun DesktopPaintDraft.toPersisted(): PersistedDraft = PersistedDraft(
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
