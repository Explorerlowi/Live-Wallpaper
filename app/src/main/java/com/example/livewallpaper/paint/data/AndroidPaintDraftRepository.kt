package com.example.livewallpaper.paint.data

import android.content.Context
import androidx.core.content.edit
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android storage adapter for unsent painting drafts.
 *
 * @param context Application context used to access the existing draft preferences.
 */
class AndroidPaintDraftRepository(context: Context) : PaintDraftRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val _draftsRevision = MutableStateFlow(0L)
    private val storageLock = Any()

    override val draftsRevision: StateFlow<Long> = _draftsRevision.asStateFlow()

    override suspend fun getAllDrafts(): PaintDraftReadResult = synchronized(storageLock) {
        val drafts = mutableMapOf<String, PaintSessionDraft>()
        preferences.all.forEach { (key, value) ->
            val content = value as? String ?: return PaintDraftReadResult.Corrupted
            val draft = decodeDraft(content) ?: return PaintDraftReadResult.Corrupted
            drafts[key] = draft
        }
        return PaintDraftReadResult.Success(drafts)
    }

    override fun getDraft(key: String): PaintSessionDraft? = synchronized(storageLock) {
        val content = preferences.getString(key, null) ?: return null
        return decodeDraft(content)
    }

    private fun decodeDraft(content: String): PaintSessionDraft? {
        return try {
            json.decodeFromString(content)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean =
        synchronized(storageLock) {
            if (_draftsRevision.value != expectedRevision) return@synchronized false
            preferences.edit {
                putString(key, json.encodeToString(draft))
            }
            true
        }

    override fun removeDraft(key: String, expectedRevision: Long): Boolean = synchronized(storageLock) {
        if (_draftsRevision.value != expectedRevision) return@synchronized false
        preferences.edit {
            remove(key)
        }
        true
    }

    override fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) = synchronized(storageLock) {
        if (drafts.isEmpty()) return@synchronized
        val encodedDrafts = drafts.mapValues { (_, draft) -> json.encodeToString(draft) }
        val editor = preferences.edit()
        encodedDrafts.forEach { (key, encodedDraft) ->
            editor.putString(key, encodedDraft)
        }
        check(editor.commit()) { "Unable to persist imported Android painting drafts" }
        _draftsRevision.value = _draftsRevision.value + 1
    }

    override fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean = synchronized(storageLock) {
        runCatching {
            val encodedDrafts = drafts.mapValues { (_, draft) -> json.encodeToString(draft) }
            val editor = preferences.edit().clear()
            encodedDrafts.forEach { (key, encodedDraft) ->
                editor.putString(key, encodedDraft)
            }
            check(editor.commit()) { "Unable to restore Android painting drafts" }
            _draftsRevision.value = _draftsRevision.value + 1
        }.isSuccess
    }

    private companion object {
        const val PREFERENCES_NAME = "paint_drafts"
    }
}
