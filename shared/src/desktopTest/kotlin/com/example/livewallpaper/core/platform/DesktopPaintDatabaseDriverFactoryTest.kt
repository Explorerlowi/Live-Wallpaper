package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** Desktop driver coverage for schema version stamping and the unversioned v1 compatibility path. */
class DesktopPaintDatabaseDriverFactoryTest {
    @Test
    fun createsAndReopensUnversionedBaselineWithoutRecreatingTables() {
        val directory = Files.createTempDirectory("paint-database-driver").toFile()
        val databaseFile = File(directory, "paint.db")
        try {
            DesktopPaintDatabaseDriverFactory(databaseFile).createDriver().close()
            assertEquals(PaintDatabase.Schema.version, readUserVersion(databaseFile))

            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA user_version = 0") }
            }

            val reopened = DesktopPaintDatabaseDriverFactory(databaseFile).createDriver()
            try {
                assertEquals(0L, PaintDatabase(reopened).paintStorageQueries.countSessions().executeAsOne())
            } finally {
                reopened.close()
            }
            assertEquals(PaintDatabase.Schema.version, readUserVersion(databaseFile))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun readUserVersion(databaseFile: File): Long =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    check(result.next())
                    result.getLong(1)
                }
            }
        }
}
