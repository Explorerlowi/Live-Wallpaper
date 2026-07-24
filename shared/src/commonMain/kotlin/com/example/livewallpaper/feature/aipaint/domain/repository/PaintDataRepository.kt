package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataMergeResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportCommitResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshotReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredDataReadResult

/** Repository operations used by full painting data backup and restore. */
interface PaintDataRepository {
    /** Reads conversations and drafts from one consistent storage snapshot. */
    suspend fun getStoredData(): PaintStoredDataReadResult

    /** Merges conversations and drafts in one transaction. */
    suspend fun mergeStoredData(data: PaintStoredData): PaintDataMergeResult

    /** Replaces conversations and drafts in one transaction. */
    suspend fun replaceStoredData(data: PaintStoredData): Boolean

    /**
     * Commits one imported bundle while excluding ordinary conversation and draft writes.
     *
     * Implementations must use one atomic database transaction or capture the rollback snapshot
     * under the same operation gate used by the merge. A concurrent message write must never be
     * replaced by an older snapshot.
     */
    suspend fun importStoredData(data: PaintStoredData): PaintDataImportCommitResult

    /**
     * Reads every saved session and message as one consistent snapshot.
     *
     * @return Complete painting conversation data.
     */
    suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult

    /**
     * Merges imported data by stable ID; imported duplicates overwrite local values.
     *
     * Existing sessions and messages absent from the import remain unchanged.
     *
     * @param snapshot Validated imported painting data.
     * @return Completed merge counts, or a corruption result without mutating storage.
     */
    suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult

    /**
     * Replaces all saved painting conversations with an exact snapshot.
     *
     * This is reserved for rolling back a failed multi-store import. API profiles and other
     * application settings must remain untouched.
     *
     * @param snapshot Previously validated conversation state to restore.
     * @return `true` when the complete snapshot was restored.
     */
    suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean
}
