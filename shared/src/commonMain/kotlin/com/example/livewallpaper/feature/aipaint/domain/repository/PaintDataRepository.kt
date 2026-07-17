package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataMergeResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshotReadResult

/** Repository operations used by full painting data backup and restore. */
interface PaintDataRepository {
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
