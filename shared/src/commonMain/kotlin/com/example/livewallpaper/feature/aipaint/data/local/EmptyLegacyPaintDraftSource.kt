package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Legacy draft source for platforms that never shipped a pre-database draft format. */
class EmptyLegacyPaintDraftSource : LegacyPaintDraftSource {
    private val drafts = mutableMapOf<String, PaintSessionDraft>()
    private val revision = MutableStateFlow(0L)

    override val draftsRevision: StateFlow<Long> = revision
    override suspend fun getAllDrafts(): PaintDraftReadResult = PaintDraftReadResult.Success(drafts.toMap())
    override suspend fun getAllDrafts(sessionIds: Set<String>): PaintDraftReadResult = getAllDrafts()
    override suspend fun getDraft(key: String): PaintSessionDraft? = drafts[key]

    override suspend fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean {
        if (revision.value != expectedRevision) return false
        drafts[key] = draft
        return true
    }

    override suspend fun removeDraft(key: String, expectedRevision: Long): Boolean {
        if (revision.value != expectedRevision) return false
        drafts.remove(key)
        return true
    }

    override suspend fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) {
        this.drafts.putAll(drafts)
        if (drafts.isNotEmpty()) revision.value += 1
    }

    override suspend fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean {
        this.drafts.clear()
        this.drafts.putAll(drafts)
        revision.value += 1
        return true
    }
}
