package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageRecoveryController
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Selects SQLDelight after migration, or the retained legacy stores after a failed migration. */
class PaintStorageCoordinator(
    private val migrator: PaintStorageMigrator,
    private val sqlStore: SqlDelightPaintStore,
    private val legacyConversations: LegacyPaintConversationStore,
    private val legacyDrafts: LegacyPaintDraftSource,
) : PaintConversationDataSource, PaintDataRepository, PaintDraftRepository, PaintStorageStateProvider,
    PaintStorageRecoveryController {
    private val initializationLock = Mutex()
    private val storageOperationLock = Mutex()
    private val _storageState = MutableStateFlow<PaintStorageState>(PaintStorageState.Initializing)
    private val _draftsRevision = MutableStateFlow(0L)
    private val activeGenerationMessages = mutableMapOf<String, String>()

    override val storageState: StateFlow<PaintStorageState> = _storageState.asStateFlow()
    override val draftsRevision: StateFlow<Long> = _draftsRevision.asStateFlow()

    /** Initializes the backend once; callers may launch this asynchronously during app startup. */
    suspend fun initialize(): Unit = initializationLock.withLock {
        if (_storageState.value !is PaintStorageState.Initializing) return
        _storageState.value = PaintStorageState.Migrating
        val migrationResult = try {
            migrator.migrate()
        } catch (error: CancellationException) {
            _storageState.value = PaintStorageState.Initializing
            throw error
        } catch (_: Exception) {
            PaintStorageMigrationResult.RecoveryRequired(PaintStorageFailure.UNKNOWN)
        }
        _storageState.value = when (val result = migrationResult) {
            PaintStorageMigrationResult.Ready -> PaintStorageState.Ready
            is PaintStorageMigrationResult.ReadOnly -> PaintStorageState.ReadOnly(result.reason)
            is PaintStorageMigrationResult.LegacyFallback -> PaintStorageState.LegacyFallback(result.reason)
            is PaintStorageMigrationResult.RecoveryRequired -> PaintStorageState.RecoveryRequired(result.reason)
        }
        if (_storageState.value !is PaintStorageState.RecoveryRequired) {
            _draftsRevision.value = readableDrafts().draftsRevision.value
        }
    }

    override fun requireRecovery(reason: PaintStorageFailure) {
        _storageState.value = PaintStorageState.RecoveryRequired(reason)
    }

    override fun getSessions(): Flow<List<PaintSession>> = backendFlow { it.getSessions() }
    override fun getSession(sessionId: String): Flow<PaintSession?> = backendFlow { it.getSession(sessionId) }
    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = backendFlow { it.getMessages(sessionId) }
    override fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>> =
        backendFlow { it.getMessagesPaged(sessionId, limit, offset) }

    override suspend fun createSession(session: PaintSession): String = storageOperationLock.withLock {
        writableConversations().createSession(session)
    }

    override suspend fun updateSession(session: PaintSession) = storageOperationLock.withLock {
        writableConversations().updateSession(session)
    }

    override suspend fun deleteSession(sessionId: String) = storageOperationLock.withLock {
        writableConversations().deleteSession(sessionId)
        activeGenerationMessages.entries.removeAll { it.value == sessionId }
        Unit
    }

    override suspend fun addMessage(message: PaintMessage) = storageOperationLock.withLock {
        writableConversations().addMessage(message)
        trackGeneration(message)
    }

    override suspend fun updateMessage(message: PaintMessage) = storageOperationLock.withLock {
        writableConversations().updateMessage(message)
        trackGeneration(message)
    }

    override suspend fun deleteMessage(messageId: String) = storageOperationLock.withLock {
        writableConversations().deleteMessage(messageId)
        activeGenerationMessages.remove(messageId)
        Unit
    }

    override suspend fun getMessageCount(sessionId: String): Int = readableConversations().getMessageCount(sessionId)
    override suspend fun getMessage(messageId: String): PaintMessage? = readableConversations().getMessage(messageId)
    override suspend fun getMessagesByVersionGroup(sessionId: String, versionGroup: String): List<PaintMessage> =
        readableConversations().getMessagesByVersionGroup(sessionId, versionGroup)
    override suspend fun getVersionCount(sessionId: String, versionGroup: String): Int =
        readableConversations().getVersionCount(sessionId, versionGroup)

    override suspend fun getAllDrafts(): PaintDraftReadResult = readableDrafts().getAllDrafts()
    override suspend fun getDraft(key: String): PaintSessionDraft? = readableDrafts().getDraft(key)

    override suspend fun saveDraft(key: String, draft: PaintSessionDraft, expectedRevision: Long): Boolean =
        storageOperationLock.withLock {
            if (_draftsRevision.value != expectedRevision) return@withLock false
            writableDrafts().saveDraft(key, draft, expectedRevision)
        }

    override suspend fun removeDraft(key: String, expectedRevision: Long): Boolean =
        storageOperationLock.withLock {
            if (_draftsRevision.value != expectedRevision) return@withLock false
            writableDrafts().removeDraft(key, expectedRevision)
        }

    override suspend fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) = storageOperationLock.withLock {
        writableDrafts().mergeDrafts(drafts)
        if (drafts.isNotEmpty()) _draftsRevision.value += 1
    }

    override suspend fun replaceDrafts(
        drafts: Map<String, PaintSessionDraft>,
    ): Boolean = storageOperationLock.withLock {
        val replaced = writableDrafts().replaceDrafts(drafts)
        if (replaced) _draftsRevision.value += 1
        replaced
    }

    override suspend fun getStoredData(): PaintStoredDataReadResult = when (val state = awaitTerminalState()) {
        PaintStorageState.Ready -> sqlStore.getStoredData()
        is PaintStorageState.ReadOnly -> sqlStore.getStoredData()
        is PaintStorageState.LegacyFallback -> legacyStoredData()
        is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
        else -> error("Unreachable storage state")
    }

    override suspend fun mergeStoredData(data: PaintStoredData): PaintDataMergeResult = storageOperationLock.withLock {
        when (val state = awaitTerminalState()) {
            PaintStorageState.Ready -> sqlStore.mergeStoredData(data).also { _draftsRevision.value += 1 }
            is PaintStorageState.LegacyFallback -> mergeLegacyStoredData(data)
            is PaintStorageState.ReadOnly -> throw PaintStorageRecoveryRequiredException(state.reason)
            is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
            else -> error("Unreachable storage state")
        }
    }

    override suspend fun replaceStoredData(data: PaintStoredData): Boolean = storageOperationLock.withLock {
        if (activeGenerationMessages.isNotEmpty()) return@withLock false
        when (val state = awaitTerminalState()) {
            PaintStorageState.Ready -> sqlStore.replaceStoredData(data).also { replaced ->
                if (replaced) _draftsRevision.value += 1
            }
            is PaintStorageState.LegacyFallback -> replaceLegacyStoredData(data)
            is PaintStorageState.ReadOnly -> throw PaintStorageRecoveryRequiredException(state.reason)
            is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
            else -> error("Unreachable storage state")
        }
    }

    override suspend fun importStoredData(data: PaintStoredData): PaintDataImportCommitResult =
        storageOperationLock.withLock {
            when (val state = awaitTerminalState()) {
                PaintStorageState.Ready -> {
                    if (activeGenerationMessages.isNotEmpty()) return@withLock PaintDataImportCommitResult.Busy
                    commitImport(
                        data = data,
                        read = sqlStore::getStoredData,
                        merge = sqlStore::mergeStoredData,
                        replace = sqlStore::replaceStoredData,
                    ).also {
                        _draftsRevision.value = sqlStore.draftsRevision.value
                    }
                }
                is PaintStorageState.LegacyFallback -> {
                    if (activeGenerationMessages.isNotEmpty()) return@withLock PaintDataImportCommitResult.Busy
                    commitImport(
                        data = data,
                        read = ::legacyStoredData,
                        merge = ::mergeLegacyStoredData,
                        replace = ::replaceLegacyStoredData,
                    )
                }
                is PaintStorageState.ReadOnly -> throw PaintStorageRecoveryRequiredException(state.reason)
                is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
                else -> error("Unreachable storage state")
            }
        }

    override suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult = when (val result = getStoredData()) {
        PaintStoredDataReadResult.Corrupted -> PaintDataSnapshotReadResult.Corrupted
        is PaintStoredDataReadResult.Success -> PaintDataSnapshotReadResult.Success(result.data.snapshot)
    }

    override suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult {
        val drafts = when (val result = getAllDrafts()) {
            PaintDraftReadResult.Corrupted -> return PaintDataMergeResult.CorruptedExistingData
            is PaintDraftReadResult.Success -> result.drafts
        }
        return mergeStoredData(PaintStoredData(snapshot, drafts))
    }

    override suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean {
        val drafts = when (val result = getAllDrafts()) {
            PaintDraftReadResult.Corrupted -> return false
            is PaintDraftReadResult.Success -> result.drafts
        }
        return replaceStoredData(PaintStoredData(snapshot, drafts))
    }

    private suspend fun legacyStoredData(): PaintStoredDataReadResult = when (val drafts = legacyDrafts.getAllDrafts(
        legacyConversations.readSnapshot().sessions.mapTo(mutableSetOf(), PaintSession::id),
    )) {
        PaintDraftReadResult.Corrupted -> PaintStoredDataReadResult.Corrupted
        is PaintDraftReadResult.Success -> PaintStoredDataReadResult.Success(
            PaintStoredData(legacyConversations.readSnapshot(), drafts.drafts),
        )
    }

    private suspend fun mergeLegacyStoredData(data: PaintStoredData): PaintDataMergeResult {
        val before = legacyStoredData() as? PaintStoredDataReadResult.Success
            ?: return PaintDataMergeResult.CorruptedExistingData
        return runCatching {
            val mergedSnapshot = legacyConversations.mergeSnapshot(data.snapshot)
            legacyDrafts.mergeDrafts(data.drafts)
            if (data.drafts.isNotEmpty()) _draftsRevision.value += 1
            PaintDataMergeResult.Success(
                PaintDataImportSummary(
                    data.snapshot.sessions.size,
                    data.snapshot.messages.size,
                    mergedSnapshot.sessions.size,
                ),
            )
        }.getOrElse {
            legacyConversations.replaceSnapshot(before.data.snapshot)
            legacyDrafts.replaceDrafts(before.data.drafts)
            PaintDataMergeResult.CorruptedExistingData
        }
    }

    private suspend fun replaceLegacyStoredData(data: PaintStoredData): Boolean {
        val before = legacyStoredData() as? PaintStoredDataReadResult.Success ?: return false
        val conversationsReplaced = legacyConversations.replaceSnapshot(data.snapshot)
        val draftsReplaced = conversationsReplaced && legacyDrafts.replaceDrafts(data.drafts)
        if (!draftsReplaced) {
            legacyConversations.replaceSnapshot(before.data.snapshot)
            legacyDrafts.replaceDrafts(before.data.drafts)
            return false
        }
        _draftsRevision.value += 1
        return true
    }

    private suspend fun commitImport(
        data: PaintStoredData,
        read: suspend () -> PaintStoredDataReadResult,
        merge: suspend (PaintStoredData) -> PaintDataMergeResult,
        replace: suspend (PaintStoredData) -> Boolean,
    ): PaintDataImportCommitResult {
        val previous = when (val result = read()) {
            PaintStoredDataReadResult.Corrupted -> return PaintDataImportCommitResult.CorruptedExistingData
            is PaintStoredDataReadResult.Success -> result.data
        }
        return try {
            when (val result = merge(data)) {
                PaintDataMergeResult.CorruptedExistingData -> PaintDataImportCommitResult.CorruptedExistingData
                is PaintDataMergeResult.Success -> PaintDataImportCommitResult.Success(result.summary)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (runCatching { replace(previous) }.getOrDefault(false)) {
                PaintDataImportCommitResult.Failed
            } else {
                requireRecovery(PaintStorageFailure.IMPORT_ROLLBACK_FAILED)
                PaintDataImportCommitResult.RollbackFailed
            }
        }
    }

    private fun trackGeneration(message: PaintMessage) {
        if (message.status == MessageStatus.GENERATING) {
            activeGenerationMessages[message.id] = message.sessionId
        } else {
            activeGenerationMessages.remove(message.id)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun <T> backendFlow(block: (PaintConversationDataSource) -> Flow<T>): Flow<T> =
        storageState.onStart { initialize() }.flatMapLatest { state ->
            when (state) {
                PaintStorageState.Initializing, PaintStorageState.Migrating -> flowOf()
                PaintStorageState.Ready -> block(sqlStore)
                is PaintStorageState.ReadOnly -> block(sqlStore)
                is PaintStorageState.LegacyFallback -> block(legacyConversations)
                is PaintStorageState.RecoveryRequired -> flowOf()
            }
        }

    private suspend fun readableConversations(): PaintConversationDataSource = when (val state = awaitTerminalState()) {
        PaintStorageState.Ready -> sqlStore
        is PaintStorageState.ReadOnly -> sqlStore
        is PaintStorageState.LegacyFallback -> legacyConversations
        is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
        else -> error("Unreachable storage state")
    }

    private suspend fun writableConversations(): PaintConversationDataSource = when (val state = awaitTerminalState()) {
        PaintStorageState.Ready -> sqlStore
        is PaintStorageState.LegacyFallback -> legacyConversations
        is PaintStorageState.ReadOnly -> throw PaintStorageRecoveryRequiredException(state.reason)
        is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
        else -> error("Unreachable storage state")
    }

    private suspend fun readableDrafts(): PaintDraftRepository = when (val state = awaitTerminalState()) {
        PaintStorageState.Ready -> sqlStore
        is PaintStorageState.ReadOnly -> sqlStore
        is PaintStorageState.LegacyFallback -> legacyDrafts
        is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
        else -> error("Unreachable storage state")
    }

    private suspend fun writableDrafts(): PaintDraftRepository = when (val state = awaitTerminalState()) {
        PaintStorageState.Ready -> sqlStore
        is PaintStorageState.LegacyFallback -> legacyDrafts
        is PaintStorageState.ReadOnly -> throw PaintStorageRecoveryRequiredException(state.reason)
        is PaintStorageState.RecoveryRequired -> throw PaintStorageRecoveryRequiredException(state.reason)
        else -> error("Unreachable storage state")
    }

    private suspend fun awaitTerminalState(): PaintStorageState {
        if (storageState.value is PaintStorageState.Initializing) initialize()
        return storageState
            .filter {
                it is PaintStorageState.Ready ||
                    it is PaintStorageState.ReadOnly ||
                    it is PaintStorageState.LegacyFallback ||
                    it is PaintStorageState.RecoveryRequired
            }
            .first()
    }
}
