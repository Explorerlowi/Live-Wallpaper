package com.example.livewallpaper.feature.aipaint.domain.repository

/** Platform boundary for copying selected references into app-managed persistent storage. */
interface PaintReferenceImageStore {
    /** Copies [sourceIdentifier] into the private reference directory for [sessionId]. */
    suspend fun persistReference(
        sessionId: String,
        imageId: String,
        sourceIdentifier: String,
        mimeType: String,
    ): String?

    /** Deletes candidate files only when they are app-managed and absent from [retainedIdentifiers]. */
    suspend fun discardUnreferenced(
        candidateIdentifiers: Set<String>,
        retainedIdentifiers: Set<String>,
    )

    /** Removes unreferenced files only from the dedicated persistent reference-image directory. */
    suspend fun cleanupOrphans(retainedIdentifiers: Set<String>) = Unit
}
