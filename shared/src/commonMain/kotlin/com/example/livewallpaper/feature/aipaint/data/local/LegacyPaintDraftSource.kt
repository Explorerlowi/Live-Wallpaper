package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository

/** Retained platform draft backend used only by migration and same-process fallback. */
interface LegacyPaintDraftSource : PaintDraftRepository {
    /** Reads legacy drafts, including platform formats that need the known session IDs. */
    suspend fun getAllDrafts(sessionIds: Set<String>): PaintDraftReadResult
}
