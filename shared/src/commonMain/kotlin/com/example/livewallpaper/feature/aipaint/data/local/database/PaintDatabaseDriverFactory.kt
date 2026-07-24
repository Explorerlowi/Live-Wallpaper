package com.example.livewallpaper.feature.aipaint.data.local.database

import app.cash.sqldelight.db.SqlDriver

/** Creates the platform SQLDelight driver for the painting database. */
interface PaintDatabaseDriverFactory {
    /** @return An opened driver with foreign-key enforcement enabled. */
    fun createDriver(): SqlDriver
}
