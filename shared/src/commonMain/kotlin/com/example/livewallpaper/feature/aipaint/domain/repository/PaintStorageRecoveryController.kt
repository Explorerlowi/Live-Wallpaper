package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure

/** Moves painting storage into a fail-closed recovery state after an unrecoverable write failure. */
interface PaintStorageRecoveryController {
    /** Disables further painting writes and exposes [reason] through the storage state flow. */
    fun requireRecovery(reason: PaintStorageFailure)
}

/** Recovery callback used by isolated use-case tests and non-coordinated repository implementations. */
object NoOpPaintStorageRecoveryController : PaintStorageRecoveryController {
    override fun requireRecovery(reason: PaintStorageFailure) = Unit
}
