package com.example.livewallpaper.feature.aipaint.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataImportCommitResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataSnapshot
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageRecoveryRequiredException
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredData
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStoredDataReadResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageState
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintImageV1
import com.example.livewallpaper.feature.aipaint.domain.model.legacy.LegacyPaintMessageV1
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** End-to-end coverage for retained Preferences data and transactional SQLite migration. */
class PaintStorageMigratorTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var settings: Settings
    private lateinit var imageFiles: RecordingLegacyImageFileStore
    private lateinit var sqlStore: SqlDelightPaintStore
    private lateinit var migrator: PaintStorageMigrator
    private lateinit var fallbackStore: LegacyPaintConversationStore
    private val json = Json { encodeDefaults = true }
    private val dispatchers = DefaultCoroutineDispatcherProvider(Dispatchers.Default, Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        settings = MemorySettings()
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PaintDatabase.Schema.create(driver)
        imageFiles = RecordingLegacyImageFileStore()
        sqlStore = SqlDelightPaintStore(PaintDatabase(driver), dispatchers, NoOpReferenceImageStore())
        fallbackStore = LegacyPaintConversationStore(settings, dispatchers)
        migrator = PaintStorageMigrator(
            legacyReader = LegacyPaintStorageReader(settings, imageFiles, dispatchers),
            legacyDrafts = EmptyLegacyPaintDraftSource(),
            legacyConversationStore = fallbackStore,
            imageFileStore = imageFiles,
            referenceImageStore = NoOpReferenceImageStore(),
            sqlStore = sqlStore,
            epochStore = PaintMigrationEpochStore(settings, dispatchers),
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun migratesBase64OnlyImageOnceAndKeepsLegacyKeys() = runBlocking {
        writeLegacyData(base64Data = "aGVsbG8=")

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals(1, stored.snapshot.sessions.size)
        assertEquals(
            "/private/session-1/message-1-image-1.png",
            stored.snapshot.messages.single().images.single().localPath,
        )
        assertTrue(sqlStore.isLegacyMigrationComplete())
        assertTrue(settings.hasKey("PAINT_SESSIONS"))
        assertEquals(1, imageFiles.writeCount)

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())
        assertEquals(1, imageFiles.writeCount)
    }

    @Test
    fun emptyInstallationCreatesAnEmptyReadyDatabase() = runBlocking {
        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertTrue(stored.snapshot.sessions.isEmpty())
        assertTrue(stored.snapshot.messages.isEmpty())
        assertTrue(sqlStore.isLegacyMigrationComplete())
    }

    @Test
    fun missingMarkerNeverReplacesExistingSqlDataWithStaleLegacySnapshot() = runBlocking {
        val sqlSession = PaintSession(id = "sql-session", title = "Current SQL data")
        sqlStore.createSession(sqlSession)
        writeLegacyData(base64Data = "aGVsbG8=")

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals(listOf(sqlSession.id), stored.snapshot.sessions.map(PaintSession::id))
        assertTrue(sqlStore.isLegacyMigrationComplete())
        assertEquals(1, settings.getInt("PAINT_SQL_STORAGE_EPOCH", 0))
        assertEquals(0, imageFiles.writeCount)
    }

    @Test
    fun activatedSqlEpochWithEmptyDatabaseRequiresRecoveryInsteadOfReplayingLegacy() = runBlocking {
        settings.putInt("PAINT_SQL_STORAGE_EPOCH", 1)
        writeLegacyData(base64Data = "aGVsbG8=")

        val result = assertIs<PaintStorageMigrationResult.RecoveryRequired>(migrator.migrate())

        assertEquals(
            com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure.DATABASE_UNAVAILABLE,
            result.reason,
        )
        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertTrue(stored.snapshot.sessions.isEmpty())
        assertFalse(sqlStore.isLegacyMigrationComplete())
        assertEquals(0, imageFiles.writeCount)
    }

    @Test
    fun databaseMarkerRepairsMissingExternalEpochWithoutRerunningLegacyMigration() = runBlocking {
        writeLegacyData(base64Data = "aGVsbG8=")
        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())
        settings.remove("PAINT_SQL_STORAGE_EPOCH")

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        assertEquals(1, settings.getInt("PAINT_SQL_STORAGE_EPOCH", 0))
        assertEquals(1, imageFiles.writeCount)
    }

    @Test
    fun completedSqlEpochWithUnreadableTablesRequiresRecovery() = runBlocking {
        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())
        driver.execute(null, "DROP TABLE paint_draft_image", 0)

        val result = assertIs<PaintStorageMigrationResult.RecoveryRequired>(migrator.migrate())

        assertEquals(
            com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure.DATABASE_UNAVAILABLE,
            result.reason,
        )
    }

    @Test
    fun unavailableEpochWithVerifiedSqlEntersReadableWriteBlockedState() = runBlocking {
        val session = PaintSession(id = "sql-session", title = "Verified SQL")
        sqlStore.createSession(session)
        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())
        val unavailableSettings = UnreadableEpochSettings(settings)
        val readOnlyMigrator = migrator(unavailableSettings)

        val result = assertIs<PaintStorageMigrationResult.ReadOnly>(readOnlyMigrator.migrate())

        assertEquals(PaintStorageFailure.MIGRATION_STATE_READ_FAILED, result.reason)
        val coordinator = PaintStorageCoordinator(
            migrator = readOnlyMigrator,
            sqlStore = sqlStore,
            legacyConversations = LegacyPaintConversationStore(unavailableSettings, dispatchers),
            legacyDrafts = EmptyLegacyPaintDraftSource(),
        )
        coordinator.initialize()
        assertIs<PaintStorageState.ReadOnly>(coordinator.storageState.value)
        val stored = assertIs<PaintStoredDataReadResult.Success>(coordinator.getStoredData()).data
        assertEquals(listOf(session.id), stored.snapshot.sessions.map(PaintSession::id))
        assertFailsWith<PaintStorageRecoveryRequiredException> {
            coordinator.createSession(PaintSession(id = "blocked"))
        }
        Unit
    }

    @Test
    fun unavailableEpochWithEmptySqlNeverReplaysLegacy() = runBlocking {
        writeLegacyData(base64Data = "aGVsbG8=")

        val result = assertIs<PaintStorageMigrationResult.RecoveryRequired>(
            migrator(UnreadableEpochSettings(settings)).migrate(),
        )

        assertEquals(PaintStorageFailure.MIGRATION_STATE_READ_FAILED, result.reason)
        assertFalse(sqlStore.isLegacyMigrationComplete())
        assertEquals(0, imageFiles.writeCount)
    }

    @Test
    fun liveMessagesUseCanonicalCreatedAtAndIdOrder() = runBlocking {
        val session = PaintSession(id = "ordered-session")
        sqlStore.createSession(session)
        val timestamp = 123L
        sqlStore.addMessage(paintTextMessage("z-message", session.id, timestamp))
        sqlStore.addMessage(paintTextMessage("a-message", session.id, timestamp))

        val messages = sqlStore.getMessages(session.id).first()

        assertEquals(listOf("a-message", "z-message"), messages.map(PaintMessage::id))
    }

    @Test
    fun coordinatorDoesNotReplaceCallerRevisionWithNewerBackendRevision() = runBlocking {
        val coordinator = PaintStorageCoordinator(
            migrator = migrator,
            sqlStore = sqlStore,
            legacyConversations = fallbackStore,
            legacyDrafts = EmptyLegacyPaintDraftSource(),
        )
        coordinator.initialize()
        assertEquals(PaintStorageState.Ready, coordinator.storageState.value)
        val editorRevision = coordinator.draftsRevision.value

        sqlStore.mergeDrafts(mapOf("session-1" to PaintSessionDraft(promptText = "imported")))

        assertFalse(
            coordinator.saveDraft(
                key = "session-1",
                draft = PaintSessionDraft(promptText = "stale"),
                expectedRevision = editorRevision,
            ),
        )
        val drafts = assertIs<PaintDraftReadResult.Success>(sqlStore.getAllDrafts()).drafts
        assertEquals("imported", drafts.getValue("session-1").promptText)
    }

    @Test
    fun importGateDoesNotRollBackAConcurrentMessageWrite() = runBlocking {
        val coordinator = PaintStorageCoordinator(
            migrator = migrator,
            sqlStore = sqlStore,
            legacyConversations = fallbackStore,
            legacyDrafts = EmptyLegacyPaintDraftSource(),
        )
        coordinator.initialize()
        val session = PaintSession(id = "session-import-lock")
        coordinator.createSession(session)
        driver.execute(
            null,
            """
                CREATE TRIGGER fail_import_message
                BEFORE INSERT ON paint_message
                WHEN NEW.id = 'import-message'
                BEGIN
                    SELECT RAISE(FAIL, 'forced import failure');
                END
            """.trimIndent(),
            0,
        )
        val importedData = PaintStoredData(
            snapshot = PaintDataSnapshot(
                sessions = listOf(session),
                messages = listOf(paintTextMessage("import-message", session.id, 1L)),
            ),
            drafts = emptyMap(),
        )

        val import = async(start = CoroutineStart.UNDISPATCHED) { coordinator.importStoredData(importedData) }
        val write = async { coordinator.addMessage(paintTextMessage("concurrent-message", session.id, 2L)) }

        assertIs<PaintDataImportCommitResult.Failed>(import.await())
        write.await()
        assertEquals(
            listOf("concurrent-message"),
            coordinator.getMessages(session.id).first().map(PaintMessage::id),
        )
    }

    @Test
    fun importGateRejectsCommitUntilLiveGenerationFinishes() = runBlocking {
        val coordinator = PaintStorageCoordinator(
            migrator = migrator,
            sqlStore = sqlStore,
            legacyConversations = fallbackStore,
            legacyDrafts = EmptyLegacyPaintDraftSource(),
        )
        coordinator.initialize()
        val session = PaintSession(id = "session-live-generation")
        coordinator.createSession(session)
        val generating = paintTextMessage("generating-message", session.id, 1L).copy(
            status = MessageStatus.GENERATING,
        )
        coordinator.addMessage(generating)
        val importedData = PaintStoredData(
            snapshot = PaintDataSnapshot(listOf(session), emptyList()),
            drafts = emptyMap(),
        )

        assertIs<PaintDataImportCommitResult.Busy>(coordinator.importStoredData(importedData))

        coordinator.updateMessage(generating.copy(status = MessageStatus.SUCCESS))
        assertIs<PaintDataImportCommitResult.Success>(coordinator.importStoredData(importedData))
        Unit
    }

    @Test
    fun invalidSoleBase64SourceFallsBackWithoutDatabaseRows() = runBlocking {
        writeLegacyData(base64Data = "invalid")

        assertIs<PaintStorageMigrationResult.LegacyFallback>(migrator.migrate())

        assertFalse(sqlStore.isLegacyMigrationComplete())
        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertTrue(stored.snapshot.sessions.isEmpty())
        assertEquals(1, fallbackStore.readSnapshot().sessions.size)
        assertEquals(null, fallbackStore.readSnapshot().messages.single().images.single().localPath)
    }

    @Test
    fun readablePathWinsAndEmbeddedBase64IsDiscarded() = runBlocking {
        imageFiles.readableIdentifiers += "/existing/reference.png"
        writeLegacyData(base64Data = "invalid", localPath = "/existing/reference.png")

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals("/existing/reference.png", stored.snapshot.messages.single().images.single().localPath)
        assertEquals(0, imageFiles.writeCount)
    }

    @Test
    fun imageWriteFailureRollsBackAndUsesLegacyFallback() = runBlocking {
        imageFiles.failWrites = true
        writeLegacyData(base64Data = "aGVsbG8=")

        assertIs<PaintStorageMigrationResult.LegacyFallback>(migrator.migrate())

        assertFalse(sqlStore.isLegacyMigrationComplete())
        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertTrue(stored.snapshot.sessions.isEmpty())
    }

    @Test
    fun databaseTransactionFailureRollsBackAndDiscardsMaterializedImage() = runBlocking {
        writeLegacyData(base64Data = "aGVsbG8=")
        driver.execute(
            null,
            """
                CREATE TRIGGER fail_message_insert
                BEFORE INSERT ON paint_message
                BEGIN
                    SELECT RAISE(FAIL, 'forced insert failure');
                END
            """.trimIndent(),
            0,
        )

        assertIs<PaintStorageMigrationResult.LegacyFallback>(migrator.migrate())

        assertTrue(PaintDatabase(driver).paintStorageQueries.selectSessions().executeAsList().isEmpty())
        assertFalse(sqlStore.isLegacyMigrationComplete())
        assertTrue(imageFiles.discardedIdentifiers.isNotEmpty())
        assertEquals(1, fallbackStore.readSnapshot().messages.size)
    }

    @Test
    fun currentMessageIdsWinWhileOtherSessionsUseLegacyArrays() = runBlocking {
        val first = PaintSession(id = "session-1", title = "Current")
        val second = PaintSession(id = "session-2", title = "Old array")
        val currentMessage = legacyTextMessage("current-message", first.id)
        val staleArrayMessage = legacyTextMessage("stale-message", first.id)
        val oldArrayMessage = legacyTextMessage("old-array-message", second.id)
        settings.putString("PAINT_SESSIONS", json.encodeToString(listOf(first, second)))
        settings.putString("PAINT_MESSAGE_IDS_${first.id}", json.encodeToString(listOf(currentMessage.id)))
        settings.putString("PAINT_MESSAGE_${currentMessage.id}", json.encodeToString(currentMessage))
        settings.putString("PAINT_MESSAGES_${first.id}", json.encodeToString(listOf(staleArrayMessage)))
        settings.putString("PAINT_MESSAGES_${second.id}", json.encodeToString(listOf(oldArrayMessage)))

        assertIs<PaintStorageMigrationResult.Ready>(migrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals(setOf("current-message", "old-array-message"), stored.snapshot.messages.map { it.id }.toSet())
    }

    @Test
    fun legacyDraftReferenceIsCopiedBeforeDatabaseCommit() = runBlocking {
        val session = PaintSession(id = "session-1", title = "Draft")
        settings.putString("PAINT_SESSIONS", json.encodeToString(listOf(session)))
        val draftSource = EmptyLegacyPaintDraftSource()
        draftSource.saveDraft(
            key = session.id,
            draft = PaintSessionDraft(
                selectedImages = listOf(PaintDraftImage("draft-image", "/external/reference.png", "image/png")),
            ),
            expectedRevision = 0,
        )
        val references = RecordingReferenceImageStore()
        val draftMigrator = PaintStorageMigrator(
            legacyReader = LegacyPaintStorageReader(settings, imageFiles, dispatchers),
            legacyDrafts = draftSource,
            legacyConversationStore = fallbackStore,
            imageFileStore = imageFiles,
            referenceImageStore = references,
            sqlStore = sqlStore,
            epochStore = PaintMigrationEpochStore(settings, dispatchers),
        )

        assertIs<PaintStorageMigrationResult.Ready>(draftMigrator.migrate())

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals(
            "/private/references/session-1/draft-image.png",
            stored.drafts.getValue(session.id).selectedImages.single().uri,
        )
    }

    @Test
    fun legacyDraftReferenceCopyFailureUsesFallbackWithoutMarker() = runBlocking {
        val session = PaintSession(id = "session-1", title = "Draft")
        settings.putString("PAINT_SESSIONS", json.encodeToString(listOf(session)))
        val draftSource = EmptyLegacyPaintDraftSource()
        draftSource.saveDraft(
            key = session.id,
            draft = PaintSessionDraft(
                selectedImages = listOf(PaintDraftImage("draft-image", "/missing/reference.png", "image/png")),
            ),
            expectedRevision = 0,
        )
        val draftMigrator = PaintStorageMigrator(
            legacyReader = LegacyPaintStorageReader(settings, imageFiles, dispatchers),
            legacyDrafts = draftSource,
            legacyConversationStore = fallbackStore,
            imageFileStore = imageFiles,
            referenceImageStore = RecordingReferenceImageStore(failWrites = true),
            sqlStore = sqlStore,
            epochStore = PaintMigrationEpochStore(settings, dispatchers),
        )

        val result = assertIs<PaintStorageMigrationResult.LegacyFallback>(draftMigrator.migrate())

        assertEquals(
            com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageFailure.IMAGE_MATERIALIZATION_FAILED,
            result.reason,
        )
        assertFalse(sqlStore.isLegacyMigrationComplete())
    }

    @Test
    fun migratesTenThousandMessagesOffTheCallingThread() = runBlocking {
        val recordingSettings = ThreadRecordingSettings(settings)
        val sessions = List(20) { index -> PaintSession(id = "session-$index", title = "Session $index") }
        recordingSettings.putString("PAINT_SESSIONS", json.encodeToString(sessions))
        sessions.forEachIndexed { sessionIndex, session ->
            val messages = List(500) { messageIndex ->
                legacyTextMessage("message-$sessionIndex-$messageIndex", session.id)
            }
            recordingSettings.putString(
                "PAINT_MESSAGE_IDS_${session.id}",
                json.encodeToString(messages.map(LegacyPaintMessageV1::id)),
            )
            messages.forEach { message ->
                recordingSettings.putString("PAINT_MESSAGE_${message.id}", json.encodeToString(message))
            }
        }
        val bulkMigrator = PaintStorageMigrator(
            legacyReader = LegacyPaintStorageReader(recordingSettings, imageFiles, dispatchers),
            legacyDrafts = EmptyLegacyPaintDraftSource(),
            legacyConversationStore = LegacyPaintConversationStore(recordingSettings, dispatchers),
            imageFileStore = imageFiles,
            referenceImageStore = NoOpReferenceImageStore(),
            sqlStore = sqlStore,
            epochStore = PaintMigrationEpochStore(recordingSettings, dispatchers),
        )

        withTimeout(60_000) {
            assertIs<PaintStorageMigrationResult.Ready>(bulkMigrator.migrate())
        }

        val stored = assertIs<PaintStoredDataReadResult.Success>(sqlStore.getStoredData()).data
        assertEquals(10_000, stored.snapshot.messages.size)
        assertTrue(recordingSettings.readThreads.isNotEmpty())
        assertTrue(recordingSettings.readThreads.none { it == Thread.currentThread().name })
    }

    private fun writeLegacyData(base64Data: String, localPath: String? = null) {
        val session = PaintSession(id = "session-1", title = "Legacy")
        val message = LegacyPaintMessageV1(
            id = "message-1",
            sessionId = session.id,
            senderIdentity = SenderIdentity.USER,
            messageContent = "reference",
            messageType = MessageType.IMAGE,
            images = listOf(
                LegacyPaintImageV1(
                    id = "image-1",
                    localPath = localPath,
                    base64Data = base64Data,
                    mimeType = "image/png",
                ),
            ),
        )
        settings.putString("PAINT_SESSIONS", json.encodeToString(listOf(session)))
        settings.putString("PAINT_MESSAGE_IDS_${session.id}", json.encodeToString(listOf(message.id)))
        settings.putString("PAINT_MESSAGE_${message.id}", json.encodeToString(message))
    }

    private fun migrator(epochSettings: Settings): PaintStorageMigrator = PaintStorageMigrator(
        legacyReader = LegacyPaintStorageReader(epochSettings, imageFiles, dispatchers),
        legacyDrafts = EmptyLegacyPaintDraftSource(),
        legacyConversationStore = LegacyPaintConversationStore(epochSettings, dispatchers),
        imageFileStore = imageFiles,
        referenceImageStore = NoOpReferenceImageStore(),
        sqlStore = sqlStore,
        epochStore = PaintMigrationEpochStore(epochSettings, dispatchers),
    )

    private fun legacyTextMessage(id: String, sessionId: String): LegacyPaintMessageV1 = LegacyPaintMessageV1(
        id = id,
        sessionId = sessionId,
        senderIdentity = SenderIdentity.USER,
        messageContent = id,
        messageType = MessageType.TEXT,
    )

    private fun paintTextMessage(id: String, sessionId: String, createdAt: Long): PaintMessage = PaintMessage(
        id = id,
        sessionId = sessionId,
        senderIdentity = SenderIdentity.USER,
        messageContent = id,
        messageType = MessageType.TEXT,
        createdAt = createdAt,
    )

    private class RecordingLegacyImageFileStore : LegacyPaintImageFileStore {
        var writeCount = 0
        var failWrites = false
        val readableIdentifiers = mutableSetOf<String>()
        val discardedIdentifiers = mutableListOf<String>()

        override suspend fun isReadable(identifier: String): Boolean = identifier in readableIdentifiers

        override suspend fun writeLegacyImage(
            sessionId: String,
            messageId: String,
            imageId: String,
            mimeType: String,
            bytes: ByteArray,
        ): String? {
            writeCount += 1
            if (failWrites) return null
            return "/private/$sessionId/$messageId-$imageId.png"
        }

        override suspend fun discardCreatedFiles(identifiers: List<String>) {
            discardedIdentifiers += identifiers
        }
    }

    private class NoOpReferenceImageStore : PaintReferenceImageStore {
        override suspend fun persistReference(
            sessionId: String,
            imageId: String,
            sourceIdentifier: String,
            mimeType: String,
        ): String = sourceIdentifier

        override suspend fun discardUnreferenced(
            candidateIdentifiers: Set<String>,
            retainedIdentifiers: Set<String>,
        ) = Unit
    }

    private class RecordingReferenceImageStore(
        private val failWrites: Boolean = false,
    ) : PaintReferenceImageStore {
        override suspend fun persistReference(
            sessionId: String,
            imageId: String,
            sourceIdentifier: String,
            mimeType: String,
        ): String? = if (failWrites) null else "/private/references/$sessionId/$imageId.png"

        override suspend fun discardUnreferenced(
            candidateIdentifiers: Set<String>,
            retainedIdentifiers: Set<String>,
        ) = Unit
    }

    private class ThreadRecordingSettings(
        private val delegate: Settings,
    ) : Settings by delegate {
        val readThreads = mutableSetOf<String>()

        override fun getString(key: String, defaultValue: String): String {
            readThreads += Thread.currentThread().name
            return delegate.getString(key, defaultValue)
        }

        override fun getStringOrNull(key: String): String? {
            readThreads += Thread.currentThread().name
            return delegate.getStringOrNull(key)
        }
    }

    private class UnreadableEpochSettings(
        private val delegate: Settings,
    ) : Settings by delegate {
        override fun getInt(key: String, defaultValue: Int): Int {
            if (key == "PAINT_SQL_STORAGE_EPOCH") error("Settings unavailable")
            return delegate.getInt(key, defaultValue)
        }
    }

    private class MemorySettings : Settings {
        private val values = mutableMapOf<String, Any>()

        override val keys: Set<String> get() = values.keys
        override val size: Int get() = values.size
        override fun clear() = values.clear()
        override fun remove(key: String) { values.remove(key) }
        override fun hasKey(key: String): Boolean = key in values
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = getIntOrNull(key) ?: defaultValue
        override fun getIntOrNull(key: String): Int? = values[key] as? Int
        override fun putLong(key: String, value: Long) { values[key] = value }
        override fun getLong(key: String, defaultValue: Long): Long = getLongOrNull(key) ?: defaultValue
        override fun getLongOrNull(key: String): Long? = values[key] as? Long
        override fun putString(key: String, value: String) { values[key] = value }
        override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue
        override fun getStringOrNull(key: String): String? = values[key] as? String
        override fun putFloat(key: String, value: Float) { values[key] = value }
        override fun getFloat(key: String, defaultValue: Float): Float = getFloatOrNull(key) ?: defaultValue
        override fun getFloatOrNull(key: String): Float? = values[key] as? Float
        override fun putDouble(key: String, value: Double) { values[key] = value }
        override fun getDouble(key: String, defaultValue: Double): Double = getDoubleOrNull(key) ?: defaultValue
        override fun getDoubleOrNull(key: String): Double? = values[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = getBooleanOrNull(key) ?: defaultValue
        override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
    }
}
