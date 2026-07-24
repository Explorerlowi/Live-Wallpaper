package com.example.livewallpaper.core.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory
import java.io.File
import java.sql.DriverManager
import java.util.Properties

/** Desktop SQLDelight driver stored under the stable application data directory. */
class DesktopPaintDatabaseDriverFactory(
    private val databaseFile: File = File(System.getProperty("user.home"), ".live-wallpaper/data/paint.db"),
) : PaintDatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        check(databaseFile.parentFile?.mkdirs() != false || databaseFile.parentFile?.isDirectory == true)
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        val databaseState = inspectDatabase(url)
        return JdbcSqliteDriver(
            url = url,
            properties = Properties().apply { put("foreign_keys", "true") },
        ).also { driver ->
            val targetVersion = PaintDatabase.Schema.version
            val sourceVersion = when {
                !databaseState.hasPaintSchema -> 0L
                databaseState.userVersion == 0L -> UNVERSIONED_BASELINE_VERSION
                else -> databaseState.userVersion
            }
            check(sourceVersion <= targetVersion) {
                "Painting database version $sourceVersion is newer than supported version $targetVersion"
            }
            when {
                sourceVersion == 0L -> PaintDatabase.Schema.create(driver)
                sourceVersion < targetVersion -> PaintDatabase.Schema.migrate(driver, sourceVersion, targetVersion)
            }
            driver.execute(null, "PRAGMA user_version = $targetVersion", 0)
        }
    }

    private fun inspectDatabase(url: String): DatabaseState {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection(url).use { connection ->
            val userVersion = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    if (result.next()) result.getLong(1) else 0L
                }
            }
            val hasPaintSchema = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'paint_session' LIMIT 1",
            ).use { statement -> statement.executeQuery().use { it.next() } }
            DatabaseState(userVersion, hasPaintSchema)
        }
    }

    private data class DatabaseState(
        val userVersion: Long,
        val hasPaintSchema: Boolean,
    )

    private companion object {
        const val UNVERSIONED_BASELINE_VERSION = 1L
    }
}
