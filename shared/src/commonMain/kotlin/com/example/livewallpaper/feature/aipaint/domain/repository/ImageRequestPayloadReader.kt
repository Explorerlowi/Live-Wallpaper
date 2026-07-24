package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload

/** Reads one platform file or resource into the short-lived payload used by an image request. */
interface ImageRequestPayloadReader {
    /** Returns request bytes for [sourceIdentifier], or `null` when the resource is unavailable. */
    suspend fun read(sourceIdentifier: String, mimeType: String): ImageRequestPayload?
}
