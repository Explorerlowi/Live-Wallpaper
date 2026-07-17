package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import kotlinx.coroutines.flow.StateFlow

/** Platform-neutral access to unsent painting drafts. */
interface PaintDraftRepository {
    /**
     * Monotonic revision bumped whenever drafts change outside the paint editor
     * (for example after a backup import). Observers should drop in-memory caches.
     */
    val draftsRevision: StateFlow<Long>

    /** @return Every saved draft, or a corruption result rather than silently omitting data. */
    suspend fun getAllDrafts(): PaintDraftReadResult

    /**
     * Reads one saved draft.
     *
     * @param key Session ID or temporary draft key.
     * @return Saved draft, or `null` when absent or unreadable.
     */
    fun getDraft(key: String): PaintSessionDraft?

    /**
     * Saves one draft without marking it as an external change.
     *
     * @param key Session ID or temporary draft key.
     * @param draft Draft to persist.
     * @param expectedRevision Revision observed when the editor created this draft state.
     * @return `false` when an external merge happened first and the stale write was rejected.
     */
    fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean

    /**
     * Removes one draft without marking it as an external change.
     *
     * @param key Session ID or temporary draft key.
     * @param expectedRevision Revision observed when the editor requested deletion.
     * @return `false` when an external merge happened first and the stale deletion was rejected.
     */
    fun removeDraft(key: String, expectedRevision: Long): Boolean

    /**
     * Merges imported drafts by key without deleting unrelated local drafts.
     *
     * @param drafts Validated drafts from a backup archive.
     */
    fun mergeDrafts(drafts: Map<String, PaintSessionDraft>)

    /**
     * Replaces every saved draft with [drafts].
     *
     * This is used to roll back an import if another storage commit fails.
     *
     * @param drafts Exact validated draft state to restore.
     * @return `true` when all drafts were replaced.
     */
    fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean
}
