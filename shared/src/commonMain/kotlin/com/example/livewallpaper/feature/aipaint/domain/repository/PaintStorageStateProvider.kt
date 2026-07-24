package com.example.livewallpaper.feature.aipaint.domain.repository

import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageState
import kotlinx.coroutines.flow.StateFlow

/** Observable state of the painting storage initialization and migration. */
interface PaintStorageStateProvider {
    /** Current backend readiness state. */
    val storageState: StateFlow<PaintStorageState>
}
