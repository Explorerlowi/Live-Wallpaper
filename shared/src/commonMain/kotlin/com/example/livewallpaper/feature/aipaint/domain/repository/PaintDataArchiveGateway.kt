package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.ExtractedPaintDataArchive
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform

/**
 * Platform boundary for reading and writing painting ZIP archives.
 *
 * Implementations own platform file APIs and must reject unsafe ZIP entry paths.
 */
interface PaintDataArchiveGateway {
    /** Platform creating archives through this gateway. */
    val clientPlatform: PaintClientPlatform
        get() = PaintClientPlatform.UNKNOWN

    /** Whether raw Base64 image payloads may be embedded directly in the manifest. */
    val allowEmbeddedImageData: Boolean
        get() = true

    /**
     * Keeps only image identifiers that the current platform permits and can read for export.
     *
     * Platforms may restrict backups to app-managed storage. Identifiers omitted from the
     * returned set are removed from the archive manifest without failing the export.
     *
     * @param sourceIdentifiers Image identifiers referenced by conversations and drafts.
     * @return Identifiers that should be packaged into the archive.
     */
    suspend fun retainExportableImageIdentifiers(sourceIdentifiers: Set<String>): Set<String> = sourceIdentifiers

    /**
     * Writes a complete painting data archive.
     *
     * @param destinationIdentifier Platform destination selected by the user.
     * @param request Manifest and image resources to package.
     * @return Success or a normalized file/archive error.
     */
    suspend fun writeArchive(
        destinationIdentifier: String,
        request: PaintDataArchiveWriteRequest,
    ): PaintDataArchiveResult<Unit>

    /**
     * Extracts an archive into app-owned storage without mutating conversation data.
     *
     * @param sourceIdentifier Platform source selected by the user.
     * @return Extracted manifest and image paths, or a validation error.
     */
    suspend fun extractArchive(sourceIdentifier: String): PaintDataArchiveResult<ExtractedPaintDataArchive>

    /**
     * Deletes files from a failed or rejected extraction.
     *
     * @param extractionId Opaque identifier returned by [extractArchive].
     */
    suspend fun discardExtraction(extractionId: String)

    /**
     * Best-effort removal of previously imported image files that are no longer referenced.
     *
     * Implementations must only delete files inside their own import storage root.
     *
     * @param retainedSourceIdentifiers Absolute platform identifiers still referenced by data.
     */
    suspend fun pruneImportedImages(retainedSourceIdentifiers: Set<String>)
}
