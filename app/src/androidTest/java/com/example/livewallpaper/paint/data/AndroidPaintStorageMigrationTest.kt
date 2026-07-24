package com.example.livewallpaper.paint.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.core.platform.AndroidLegacyPaintImageFileStore
import com.example.livewallpaper.core.platform.AndroidPaintDatabaseDriverFactory
import com.example.livewallpaper.core.platform.AndroidPaintReferenceImageStore
import com.example.livewallpaper.feature.aipaint.data.local.EmptyLegacyPaintDraftSource
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintConversationStore
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintStorageReader
import com.example.livewallpaper.feature.aipaint.data.local.PaintStorageMigrationResult
import com.example.livewallpaper.feature.aipaint.data.local.PaintStorageMigrator
import com.example.livewallpaper.feature.aipaint.data.local.PaintMigrationEpochStore
import com.example.livewallpaper.feature.aipaint.data.local.SqlDelightPaintStore
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseFactory
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredDataReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the retained Android SharedPreferences conversation migrates into private SQLite. */
@RunWith(AndroidJUnit4::class)
class AndroidPaintStorageMigrationTest {
    @Test
    fun sharedPreferencesConversationMigratesToSqlite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val preferencesName = "paint-migration-test-$suffix"
        val databaseName = "$preferencesName.db"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val settings = SharedPreferencesSettings(preferences)
        val session = PaintSession(id = "android-session", title = "Android migration")
        val message = PaintMessage(
            id = "android-message",
            sessionId = session.id,
            senderIdentity = SenderIdentity.USER,
            messageContent = "hello",
            messageType = MessageType.TEXT,
        )
        val json = Json { encodeDefaults = true }
        settings.putString("PAINT_SESSIONS", json.encodeToString(listOf(session)))
        settings.putString("PAINT_MESSAGE_IDS_${session.id}", json.encodeToString(listOf(message.id)))
        settings.putString("PAINT_MESSAGE_${message.id}", json.encodeToString(message))

        val dispatchers = DefaultCoroutineDispatcherProvider(Dispatchers.IO, Dispatchers.Default)
        val database = PaintDatabaseFactory.create(AndroidPaintDatabaseDriverFactory(context, databaseName))
        val referenceStore = AndroidPaintReferenceImageStore(context, dispatchers)
        val legacyImageStore = AndroidLegacyPaintImageFileStore(context, dispatchers)
        val sqlStore = SqlDelightPaintStore(database, dispatchers, referenceStore)
        val migrator = PaintStorageMigrator(
            legacyReader = LegacyPaintStorageReader(settings, legacyImageStore, dispatchers),
            legacyDrafts = EmptyLegacyPaintDraftSource(),
            legacyConversationStore = LegacyPaintConversationStore(settings, dispatchers),
            imageFileStore = legacyImageStore,
            referenceImageStore = referenceStore,
            sqlStore = sqlStore,
            epochStore = PaintMigrationEpochStore(settings, dispatchers),
        )

        try {
            assertTrue(migrator.migrate() is PaintStorageMigrationResult.Ready)
            val stored = sqlStore.getStoredData() as PaintStoredDataReadResult.Success
            assertEquals(listOf(session.id), stored.data.snapshot.sessions.map { it.id })
            assertEquals(listOf(message.id), stored.data.snapshot.messages.map { it.id })
            assertTrue(sqlStore.isLegacyMigrationComplete())
        } finally {
            preferences.edit().clear().commit()
            context.deleteDatabase(databaseName)
        }
    }
}
