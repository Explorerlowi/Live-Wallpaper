package com.example.livewallpaper.core.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory

/** Android SQLDelight driver stored in the application's private database directory. */
class AndroidPaintDatabaseDriverFactory(
    private val context: Context,
    private val databaseName: String = DATABASE_NAME,
) : PaintDatabaseDriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = PaintDatabase.Schema,
        context = context,
        name = databaseName,
        callback = object : AndroidSqliteDriver.Callback(PaintDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    private companion object {
        const val DATABASE_NAME = "paint.db"
    }
}
