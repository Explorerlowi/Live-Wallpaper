package com.example.livewallpaper.feature.aipaint.data.local.database

/** Builds the generated painting database with shared primitive adapters. */
object PaintDatabaseFactory {
    /** @return A database backed by the supplied platform driver factory. */
    fun create(driverFactory: PaintDatabaseDriverFactory): PaintDatabase =
        PaintDatabase(driverFactory.createDriver())
}
