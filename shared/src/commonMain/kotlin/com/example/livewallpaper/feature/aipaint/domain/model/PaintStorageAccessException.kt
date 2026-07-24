package com.example.livewallpaper.feature.aipaint.domain.model

/**
 * Signals that painting storage is intentionally blocking an operation until recovery completes.
 *
 * The normalized [reason] lets import and export map this condition without treating it as an
 * unrelated unknown exception.
 */
class PaintStorageRecoveryRequiredException(
    val reason: PaintStorageFailure,
) : IllegalStateException("Painting storage requires recovery: $reason")
