package com.example.livewallpaper.feature.aipaint.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SQLite integration coverage for relational painting storage and draft revisions. */
class SqlDelightPaintStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var referenceImages: RecordingReferenceImageStore
    private lateinit var store: SqlDelightPaintStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PaintDatabase.Schema.create(driver)
        referenceImages = RecordingReferenceImageStore()
        store = SqlDelightPaintStore(
            database = PaintDatabase(driver),
            dispatchers = DefaultCoroutineDispatcherProvider(Dispatchers.Default, Dispatchers.Default),
            referenceImageStore = referenceImages,
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun storesOrderedMessagesImagesAndCascadesSessionDeletion() = runBlocking {
        val session = PaintSession(id = "session-1", title = "Stored")
        val imagePath = "/private/references/session-1/image-1.png"
        val message = PaintMessage(
            id = "message-1",
            sessionId = session.id,
            senderIdentity = SenderIdentity.USER,
            messageContent = "reference",
            messageType = MessageType.IMAGE,
            images = listOf(PaintImage("image-1", imagePath, "image/png", 100, 80, true)),
        )

        store.createSession(session)
        store.addMessage(message)

        assertEquals(message, store.getMessages(session.id).first().single())
        assertEquals(1, store.getMessageCount(session.id))

        store.deleteSession(session.id)

        assertNull(store.getMessage(message.id))
        assertTrue(store.getMessages(session.id).first().isEmpty())
        assertEquals(setOf(imagePath), referenceImages.lastCandidates)
    }

    @Test
    fun importedDraftRevisionRejectsStaleEditorWrite() = runBlocking {
        val initialRevision = store.draftsRevision.value
        assertTrue(
            store.saveDraft(
                key = "session-1",
                draft = PaintSessionDraft(promptText = "local"),
                expectedRevision = initialRevision,
            ),
        )

        store.mergeDrafts(
            mapOf(
                "session-1" to PaintSessionDraft(
                    promptText = "imported",
                    selectedImages = listOf(PaintDraftImage("draft-image", "/private/reference.png", "image/png")),
                ),
            ),
        )

        assertFalse(
            store.saveDraft(
                key = "session-1",
                draft = PaintSessionDraft(promptText = "stale"),
                expectedRevision = initialRevision,
            ),
        )
        val drafts = store.getAllDrafts() as PaintDraftReadResult.Success
        assertEquals("imported", drafts.drafts.getValue("session-1").promptText)
    }

    @Test
    fun replacementAssignsDeterministicOrderWhenMessagesShareCreatedAt() = runBlocking {
        val session = PaintSession(id = "session-1", title = "Ordered")
        val messages = listOf("message-c", "message-a", "message-b").map { id ->
            PaintMessage(
                id = id,
                sessionId = session.id,
                senderIdentity = SenderIdentity.USER,
                messageContent = id,
                messageType = MessageType.TEXT,
                createdAt = 123L,
                updatedAt = 123L,
            )
        }

        assertTrue(store.replaceStoredData(PaintStoredData(PaintDataSnapshot(listOf(session), messages), emptyMap())))

        assertEquals(
            listOf("message-a", "message-b", "message-c"),
            store.getMessages(session.id).first().map(PaintMessage::id),
        )
    }

    @Test
    fun mergeReordersExistingAndImportedMessagesWithEqualTimestamps() = runBlocking {
        val session = PaintSession(id = "session-1", title = "Merged order")
        store.createSession(session)
        store.addMessage(
            PaintMessage(
                id = "message-z",
                sessionId = session.id,
                senderIdentity = SenderIdentity.USER,
                messageContent = "existing",
                messageType = MessageType.TEXT,
                createdAt = 123L,
                updatedAt = 123L,
            ),
        )
        val imported = PaintMessage(
            id = "message-a",
            sessionId = session.id,
            senderIdentity = SenderIdentity.USER,
            messageContent = "imported",
            messageType = MessageType.TEXT,
            createdAt = 123L,
            updatedAt = 123L,
        )

        store.mergeStoredData(PaintStoredData(PaintDataSnapshot(listOf(session), listOf(imported)), emptyMap()))

        assertEquals(
            listOf("message-a", "message-z"),
            store.getMessages(session.id).first().map(PaintMessage::id),
        )
    }

    private class RecordingReferenceImageStore : PaintReferenceImageStore {
        var lastCandidates: Set<String> = emptySet()

        override suspend fun persistReference(
            sessionId: String,
            imageId: String,
            sourceIdentifier: String,
            mimeType: String,
        ): String = sourceIdentifier

        override suspend fun discardUnreferenced(
            candidateIdentifiers: Set<String>,
            retainedIdentifiers: Set<String>,
        ) {
            lastCandidates = candidateIdentifiers - retainedIdentifiers
        }
    }
}
