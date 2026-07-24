package com.example.livewallpaper.core.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory

/** iOS SQLDelight driver stored in the application's private SQLite location. */
class IOSPaintDatabaseDriverFactory : PaintDatabaseDriverFactory {
    override fun createDriver(): SqlDriver = NativeSqliteDriver(PaintDatabase.Schema, "paint.db")
}
