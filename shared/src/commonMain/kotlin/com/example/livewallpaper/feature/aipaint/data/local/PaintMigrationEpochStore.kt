package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Database-independent marker proving that this installation has entered SQL-backed storage. */
class PaintMigrationEpochStore(
    private val settings: Settings,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    /** Reads the external epoch without collapsing an unavailable Settings backend into `false`. */
    suspend fun readSqlStorageActivation(): PaintMigrationEpochReadResult = withContext(dispatchers.io) {
        try {
            if (settings.getInt(KEY_SQL_STORAGE_EPOCH, 0) >= CURRENT_SQL_STORAGE_EPOCH) {
                PaintMigrationEpochReadResult.Activated
            } else {
                PaintMigrationEpochReadResult.NotActivated
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PaintMigrationEpochReadResult.Unavailable
        }
    }

    /** Persists and verifies the SQL storage epoch after the database transaction commits. */
    suspend fun markSqlStorageActivated(): Boolean = withContext(dispatchers.io) {
        try {
            settings.putInt(KEY_SQL_STORAGE_EPOCH, CURRENT_SQL_STORAGE_EPOCH)
            settings.getInt(KEY_SQL_STORAGE_EPOCH, 0) >= CURRENT_SQL_STORAGE_EPOCH
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val KEY_SQL_STORAGE_EPOCH = "PAINT_SQL_STORAGE_EPOCH"
        const val CURRENT_SQL_STORAGE_EPOCH = 1
    }
}

/** Result of reading the database-independent SQL activation epoch. */
sealed interface PaintMigrationEpochReadResult {
    /** This installation previously committed SQL-backed storage. */
    data object Activated : PaintMigrationEpochReadResult

    /** No SQL activation epoch has been committed yet. */
    data object NotActivated : PaintMigrationEpochReadResult

    /** The Settings backend could not be read safely. */
    data object Unavailable : PaintMigrationEpochReadResult
}
