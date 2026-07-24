package com.example.livewallpaper.feature.aipaint.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabase
import com.example.livewallpaper.feature.aipaint.data.local.database.Paint_draft
import com.example.livewallpaper.feature.aipaint.data.local.database.Paint_draft_image
import com.example.livewallpaper.feature.aipaint.data.local.database.Paint_message
import com.example.livewallpaper.feature.aipaint.data.local.database.Paint_message_image
import com.example.livewallpaper.feature.aipaint.data.local.database.Paint_session
import com.example.livewallpaper.feature.aipaint.data.local.database.Storage_migration
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** SQLDelight implementation for conversations, images, drafts, and atomic backup data. */
class SqlDelightPaintStore(
    private val database: PaintDatabase,
    private val dispatchers: CoroutineDispatcherProvider,
    private val referenceImageStore: PaintReferenceImageStore,
) : PaintConversationDataSource, PaintDataRepository, PaintDraftRepository {
    private val queries = database.paintStorageQueries
    private val _draftsRevision = MutableStateFlow(0L)

    override val draftsRevision: StateFlow<Long> = _draftsRevision.asStateFlow()

    override fun getSessions(): Flow<List<PaintSession>> = queries.selectSessions()
        .asFlow()
        .mapToList(dispatchers.io)
        .map { rows -> rows.map { it.toDomain() } }

    override fun getSession(sessionId: String): Flow<PaintSession?> = queries.selectSessionById(sessionId)
        .asFlow()
        .mapToOneOrNull(dispatchers.io)
        .map { row -> row?.toDomain() }

    override suspend fun createSession(session: PaintSession): String = withContext(dispatchers.io) {
        upsertSession(session)
        session.id
    }

    override suspend fun updateSession(session: PaintSession) = withContext(dispatchers.io) {
        upsertSession(session.copy(updatedAt = TimeProvider.currentTimeMillis()))
    }

    override suspend fun deleteSession(sessionId: String) = withContext(dispatchers.io) {
        val candidates = queries.selectImagePathsBySession(sessionId).executeAsList().filterNotNull().toSet() +
            queries.selectDraftImages(sessionId).executeAsList().map(Paint_draft_image::local_path)
        database.transaction {
            queries.deleteDraft(sessionId)
            queries.deleteSession(sessionId)
        }
        discardUnreferenced(candidates)
    }

    override fun getMessages(sessionId: String): Flow<List<PaintMessage>> = queries
        .selectMessagesBySession(sessionId)
        .asFlow()
        .mapToList(dispatchers.io)
        .map { rows -> withContext(dispatchers.io) { rows.map(::messageWithImages) } }

    override fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): Flow<List<PaintMessage>> = queries
        .selectMessagesPaged(sessionId, limit.toLong(), offset.toLong())
        .asFlow()
        .mapToList(dispatchers.io)
        .map { rows -> withContext(dispatchers.io) { rows.map(::messageWithImages) } }

    override suspend fun addMessage(message: PaintMessage) = withContext(dispatchers.io) {
        database.transaction {
            val order = queries.nextMessageSortOrder(message.sessionId).executeAsOne()
            upsertMessage(message, order)
            normalizeMessageOrder(message.sessionId)
        }
    }

    override suspend fun updateMessage(message: PaintMessage) = withContext(dispatchers.io) {
        val candidates = queries.selectImagesByMessage(message.id).executeAsList().mapNotNull { it.local_path }.toSet()
        database.transaction {
            val existing = queries.selectMessageById(message.id).executeAsOneOrNull()
            val existingOrder = if (existing?.session_id == message.sessionId) {
                existing.sort_order
            } else {
                queries.nextMessageSortOrder(message.sessionId).executeAsOne()
            }
            upsertMessage(message.copy(updatedAt = TimeProvider.currentTimeMillis()), existingOrder)
            normalizeMessageOrder(message.sessionId)
            existing?.session_id?.takeIf { it != message.sessionId }?.let(::normalizeMessageOrder)
        }
        discardUnreferenced(candidates)
    }

    override suspend fun deleteMessage(messageId: String): Unit = withContext(dispatchers.io) {
        val candidates = queries.selectImagesByMessage(messageId).executeAsList().mapNotNull { it.local_path }.toSet()
        queries.deleteMessage(messageId)
        discardUnreferenced(candidates)
    }

    override suspend fun getMessageCount(sessionId: String): Int = withContext(dispatchers.io) {
        queries.countMessagesBySession(sessionId).executeAsOne().toInt()
    }

    override suspend fun getMessage(messageId: String): PaintMessage? = withContext(dispatchers.io) {
        queries.selectMessageById(messageId).executeAsOneOrNull()?.let(::messageWithImages)
    }

    override suspend fun getMessagesByVersionGroup(
        sessionId: String,
        versionGroup: String,
    ): List<PaintMessage> = withContext(dispatchers.io) {
        queries.selectMessagesByVersionGroup(sessionId, versionGroup).executeAsList().map(::messageWithImages)
    }

    override suspend fun getVersionCount(sessionId: String, versionGroup: String): Int =
        withContext(dispatchers.io) {
            queries.countMessagesByVersionGroup(sessionId, versionGroup).executeAsOne().toInt()
        }

    override suspend fun getAllDrafts(): PaintDraftReadResult = withContext(dispatchers.io) {
        runCatching {
            queries.selectAllDrafts().executeAsList().associate { row -> row.draft_key to draftWithImages(row) }
        }.fold(
            onSuccess = PaintDraftReadResult::Success,
            onFailure = { PaintDraftReadResult.Corrupted },
        )
    }

    override suspend fun getDraft(key: String): PaintSessionDraft? = withContext(dispatchers.io) {
        queries.selectDraft(key).executeAsOneOrNull()?.let(::draftWithImages)
    }

    override suspend fun saveDraft(
        key: String,
        draft: PaintSessionDraft,
        expectedRevision: Long,
    ): Boolean = withContext(dispatchers.io) {
        if (_draftsRevision.value != expectedRevision) return@withContext false
        val candidates = queries.selectDraftImages(key).executeAsList().map(Paint_draft_image::local_path).toSet()
        database.transaction { upsertDraft(key, draft) }
        discardUnreferenced(candidates)
        true
    }

    override suspend fun removeDraft(key: String, expectedRevision: Long): Boolean = withContext(dispatchers.io) {
        if (_draftsRevision.value != expectedRevision) return@withContext false
        val candidates = queries.selectDraftImages(key).executeAsList().map(Paint_draft_image::local_path).toSet()
        queries.deleteDraft(key)
        discardUnreferenced(candidates)
        true
    }

    override suspend fun mergeDrafts(drafts: Map<String, PaintSessionDraft>) = withContext(dispatchers.io) {
        val candidates = drafts.keys.flatMap { key ->
            queries.selectDraftImages(key).executeAsList().map(Paint_draft_image::local_path)
        }.toSet()
        database.transaction { drafts.forEach { (key, draft) -> upsertDraft(key, draft) } }
        if (drafts.isNotEmpty()) _draftsRevision.value += 1
        discardUnreferenced(candidates)
    }

    override suspend fun replaceDrafts(drafts: Map<String, PaintSessionDraft>): Boolean = withContext(dispatchers.io) {
        val candidates = allDraftImagePaths()
        try {
            database.transaction {
                queries.deleteAllDrafts()
                drafts.forEach { (key, draft) -> upsertDraft(key, draft) }
            }
            _draftsRevision.value += 1
            discardUnreferenced(candidates)
            true
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getStoredData(): PaintStoredDataReadResult = withContext(dispatchers.io) {
        runCatching { readStoredData() }.fold(
            onSuccess = PaintStoredDataReadResult::Success,
            onFailure = { PaintStoredDataReadResult.Corrupted },
        )
    }

    override suspend fun mergeStoredData(data: PaintStoredData): PaintDataMergeResult = withContext(dispatchers.io) {
        val candidates = allStoredImagePaths()
        database.transaction {
            data.snapshot.sessions.forEach(::upsertSession)
            upsertOrderedMessages(data.snapshot.messages)
            data.drafts.forEach { (key, draft) -> upsertDraft(key, draft) }
        }
        _draftsRevision.value += 1
        discardUnreferenced(candidates)
        PaintDataMergeResult.Success(
            PaintDataImportSummary(
                importedSessionCount = data.snapshot.sessions.size,
                importedMessageCount = data.snapshot.messages.size,
                totalSessionCount = queries.countSessions().executeAsOne().toInt(),
            ),
        )
    }

    override suspend fun replaceStoredData(data: PaintStoredData): Boolean = withContext(dispatchers.io) {
        val candidates = allStoredImagePaths()
        try {
            database.transaction {
                queries.deleteAllDrafts()
                queries.deleteAllSessions()
                data.snapshot.sessions.forEach(::upsertSession)
                upsertOrderedMessages(data.snapshot.messages)
                data.drafts.forEach { (key, draft) -> upsertDraft(key, draft) }
            }
            _draftsRevision.value += 1
            discardUnreferenced(candidates)
            true
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun importStoredData(data: PaintStoredData): PaintDataImportCommitResult =
        withContext(dispatchers.io) {
            try {
                when (val result = mergeStoredData(data)) {
                    PaintDataMergeResult.CorruptedExistingData -> PaintDataImportCommitResult.CorruptedExistingData
                    is PaintDataMergeResult.Success -> PaintDataImportCommitResult.Success(result.summary)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // mergeStoredData writes rows in one SQLDelight transaction, which has already rolled back.
                PaintDataImportCommitResult.Failed
            }
        }

    override suspend fun getPaintDataSnapshot(): PaintDataSnapshotReadResult = when (val result = getStoredData()) {
        PaintStoredDataReadResult.Corrupted -> PaintDataSnapshotReadResult.Corrupted
        is PaintStoredDataReadResult.Success -> PaintDataSnapshotReadResult.Success(result.data.snapshot)
    }

    override suspend fun mergePaintDataSnapshot(snapshot: PaintDataSnapshot): PaintDataMergeResult {
        return mergeStoredData(PaintStoredData(snapshot, emptyMap()))
    }

    override suspend fun replacePaintDataSnapshot(snapshot: PaintDataSnapshot): Boolean {
        val drafts = when (val result = getAllDrafts()) {
            PaintDraftReadResult.Corrupted -> return false
            is PaintDraftReadResult.Success -> result.drafts
        }
        return replaceStoredData(PaintStoredData(snapshot, drafts))
    }

    /** Returns whether the legacy-settings migration marker has already been committed. */
    suspend fun isLegacyMigrationComplete(): Boolean = withContext(dispatchers.io) {
        queries.selectMigration(LEGACY_MIGRATION_KEY).executeAsOneOrNull() != null
    }

    /** Returns whether any SQL-era conversation, image, or draft row already exists. */
    suspend fun hasStoredData(): Boolean = withContext(dispatchers.io) {
        queries.countSessions().executeAsOne() > 0L ||
            queries.countMessages().executeAsOne() > 0L ||
            queries.countImages().executeAsOne() > 0L ||
            queries.countDrafts().executeAsOne() > 0L
    }

    /** Strictly maps every stored row so a missing marker is never repaired over corrupted data. */
    suspend fun verifyStoredData(): Boolean = withContext(dispatchers.io) {
        runCatching {
            val stored = readStoredData()
            stored.snapshot.sessions.size.toLong() == queries.countSessions().executeAsOne() &&
                stored.snapshot.messages.size.toLong() == queries.countMessages().executeAsOne() &&
                stored.snapshot.messages.sumOf { it.images.size }.toLong() == queries.countImages().executeAsOne() &&
                stored.drafts.size.toLong() == queries.countDrafts().executeAsOne() &&
                stored.drafts.values.sumOf { it.selectedImages.size }.toLong() ==
                queries.countDraftImages().executeAsOne()
        }.getOrDefault(false)
    }

    /** Repairs a missing in-database marker without replacing existing SQL-era records. */
    suspend fun recordLegacyMigrationForExistingData() = withContext(dispatchers.io) {
        database.transaction {
            val stored = readStoredData()
            stored.snapshot.sessions.forEach { session -> normalizeMessageOrder(session.id) }
            insertMigrationMarker(stored)
            check(readStoredData().hasSameRecords(stored)) { "Painting database verification failed" }
        }
    }

    /** Runs startup maintenance only against platform-declared app-managed directories. */
    suspend fun cleanupOrphanedImages() = withContext(dispatchers.io) {
        val retained = queries.selectAllMessageImagePaths().executeAsList().filterNotNull().toSet() +
            queries.selectAllDraftImagePaths().executeAsList().toSet()
        referenceImageStore.cleanupOrphans(retained)
    }

    /** Atomically replaces the database with migrated data and records completion counts. */
    suspend fun commitLegacyMigration(data: PaintStoredData) = withContext(dispatchers.io) {
        val candidates = allStoredImagePaths()
        database.transaction {
            queries.deleteAllDrafts()
            queries.deleteAllSessions()
            data.snapshot.sessions.forEach(::upsertSession)
            upsertOrderedMessages(data.snapshot.messages)
            data.drafts.forEach { (key, draft) -> upsertDraft(key, draft) }
            insertMigrationMarker(data)
            val persisted = readStoredData()
            check(persisted.hasSameRecords(data)) { "Painting database verification failed" }
        }
        _draftsRevision.value += 1
        discardUnreferenced(candidates)
    }

    private fun readStoredData(): PaintStoredData {
        val sessions = queries.selectSessions().executeAsList().map { it.toDomain() }
        val messages = sessions.flatMap { session ->
            queries.selectMessagesBySession(session.id).executeAsList().map(::messageWithImages)
        }
        val drafts = queries.selectAllDrafts().executeAsList().associate { row ->
            row.draft_key to draftWithImages(row)
        }
        return PaintStoredData(PaintDataSnapshot(sessions, messages), drafts)
    }

    private suspend fun discardUnreferenced(candidates: Set<String>) {
        if (candidates.isEmpty()) return
        val retained = queries.selectAllMessageImagePaths().executeAsList().filterNotNull().toSet() +
            queries.selectAllDraftImagePaths().executeAsList().toSet()
        referenceImageStore.discardUnreferenced(candidates, retained)
    }

    private fun allStoredImagePaths(): Set<String> =
        queries.selectAllMessageImagePaths().executeAsList().filterNotNull().toSet() + allDraftImagePaths()

    private fun allDraftImagePaths(): Set<String> = queries.selectAllDraftImagePaths().executeAsList().toSet()

    private fun PaintStoredData.hasSameRecords(expected: PaintStoredData): Boolean =
        snapshot.sessions.associateBy(PaintSession::id) == expected.snapshot.sessions.associateBy(PaintSession::id) &&
            snapshot.messages.associateBy(PaintMessage::id) ==
            expected.snapshot.messages.associateBy(PaintMessage::id) &&
            snapshot.messages.messageIdsInCurrentOrder() == expected.snapshot.messages.canonicalMessageIds() &&
            drafts == expected.drafts

    private fun upsertOrderedMessages(messages: List<PaintMessage>) {
        messages.groupBy(PaintMessage::sessionId).forEach { (_, sessionMessages) ->
            sessionMessages.sortedWith(MESSAGE_STORAGE_ORDER).forEachIndexed { index, message ->
                upsertMessage(message, index.toLong())
            }
            normalizeMessageOrder(sessionMessages.first().sessionId)
        }
    }

    private fun normalizeMessageOrder(sessionId: String) {
        queries.selectMessageIdsForStorageOrder(sessionId).executeAsList().forEachIndexed { index, messageId ->
            queries.updateMessageSortOrder(index.toLong(), messageId)
        }
    }

    private fun insertMigrationMarker(data: PaintStoredData) {
        queries.insertMigration(
            Storage_migration(
                migration_key = LEGACY_MIGRATION_KEY,
                completed_at = TimeProvider.currentTimeMillis(),
                session_count = data.snapshot.sessions.size.toLong(),
                message_count = data.snapshot.messages.size.toLong(),
                image_count = data.snapshot.messages.sumOf { it.images.size }.toLong(),
                draft_count = data.drafts.size.toLong(),
            ),
        )
    }

    private fun List<PaintMessage>.canonicalMessageIds(): Map<String, List<String>> =
        groupBy(PaintMessage::sessionId).mapValues { (_, messages) ->
            messages.sortedWith(MESSAGE_STORAGE_ORDER).map(PaintMessage::id)
        }

    private fun List<PaintMessage>.messageIdsInCurrentOrder(): Map<String, List<String>> =
        groupBy(PaintMessage::sessionId).mapValues { (_, messages) -> messages.map(PaintMessage::id) }

    private fun upsertMessage(message: PaintMessage, sortOrder: Long) {
        queries.insertMessage(message.toEntity(sortOrder))
        queries.deleteImagesByMessage(message.id)
        message.images.forEachIndexed { index, image ->
            queries.insertMessageImage(image.toEntity(message.id, index.toLong()))
        }
    }

    private fun upsertSession(session: PaintSession) {
        val entity = session.toEntity()
        queries.insertSession(entity)
        queries.updateSessionRecord(
            title = entity.title,
            model = entity.model,
            aspect_ratio = entity.aspect_ratio,
            resolution = entity.resolution,
            gpt_image_size = entity.gpt_image_size,
            gpt_image_quality = entity.gpt_image_quality,
            gpt_output_format = entity.gpt_output_format,
            created_at = entity.created_at,
            updated_at = entity.updated_at,
            origin_platform = entity.origin_platform,
            is_pinned = entity.is_pinned,
            pinned_at = entity.pinned_at,
            id = entity.id,
        )
    }

    private fun upsertDraft(key: String, draft: PaintSessionDraft) {
        queries.insertDraft(draft.toEntity(key))
        queries.deleteDraftImages(key)
        draft.selectedImages.forEachIndexed { index, image ->
            queries.insertDraftImage(image.toEntity(key, index.toLong()))
        }
    }

    private fun messageWithImages(row: Paint_message): PaintMessage = row.toDomain(
        images = queries.selectImagesByMessage(row.id).executeAsList().map { it.toDomain() },
    )

    private fun draftWithImages(row: Paint_draft): PaintSessionDraft = row.toDomain(
        images = queries.selectDraftImages(row.draft_key).executeAsList().map { it.toDomain() },
    )

    private fun PaintSession.toEntity(): Paint_session = Paint_session(
        id = id,
        title = title,
        model = model.name,
        aspect_ratio = aspectRatio.name,
        resolution = resolution.name,
        gpt_image_size = gptImageSize.name,
        gpt_image_quality = gptImageQuality.name,
        gpt_output_format = gptOutputFormat.name,
        created_at = createdAt,
        updated_at = updatedAt,
        origin_platform = originPlatform.name,
        is_pinned = isPinned,
        pinned_at = pinnedAt,
    )

    private fun Paint_session.toDomain(): PaintSession = PaintSession(
        id = id,
        title = title,
        model = enumValue(model),
        aspectRatio = enumValue(aspect_ratio),
        resolution = enumValue(resolution),
        gptImageSize = enumValue(gpt_image_size),
        gptImageQuality = enumValue(gpt_image_quality),
        gptOutputFormat = enumValue(gpt_output_format),
        createdAt = created_at,
        updatedAt = updated_at,
        originPlatform = enumValue(origin_platform),
        isPinned = is_pinned,
        pinnedAt = pinned_at,
    )

    private fun PaintMessage.toEntity(sortOrder: Long): Paint_message = Paint_message(
        id = id,
        session_id = sessionId,
        sender_identity = senderIdentity.name,
        message_content = messageContent,
        reasoning_content = reasoningContent,
        message_type = messageType.name,
        created_at = createdAt,
        updated_at = updatedAt,
        status = status.name,
        origin_platform = originPlatform.name,
        parent_user_message_id = parentUserMessageId,
        version_group = versionGroup,
        version_index = versionIndex.toLong(),
        generation_model = generationModel?.name,
        generation_aspect_ratio = generationAspectRatio?.name,
        generation_resolution = generationResolution?.name,
        generation_gpt_size = generationGptSize?.name,
        generation_gpt_quality = generationGptQuality?.name,
        generation_gpt_format = generationGptFormat?.name,
        sort_order = sortOrder,
    )

    private fun Paint_message.toDomain(images: List<PaintImage>): PaintMessage = PaintMessage(
        id = id,
        sessionId = session_id,
        senderIdentity = enumValue(sender_identity),
        messageContent = message_content,
        reasoningContent = reasoning_content,
        messageType = enumValue(message_type),
        images = images,
        createdAt = created_at,
        updatedAt = updated_at,
        status = enumValue(status),
        originPlatform = enumValue(origin_platform),
        parentUserMessageId = parent_user_message_id,
        versionGroup = version_group,
        versionIndex = version_index.toInt(),
        generationModel = generation_model?.let(::enumValue),
        generationAspectRatio = generation_aspect_ratio?.let(::enumValue),
        generationResolution = generation_resolution?.let(::enumValue),
        generationGptSize = generation_gpt_size?.let(::enumValue),
        generationGptQuality = generation_gpt_quality?.let(::enumValue),
        generationGptFormat = generation_gpt_format?.let(::enumValue),
    )

    private fun PaintImage.toEntity(messageId: String, sortOrder: Long): Paint_message_image =
        Paint_message_image(id, messageId, localPath, mimeType, width.toLong(), height.toLong(), isReference, sortOrder)

    private fun Paint_message_image.toDomain(): PaintImage =
        PaintImage(id, local_path, mime_type, width.toInt(), height.toInt(), is_reference)

    private fun PaintSessionDraft.toEntity(key: String): Paint_draft = Paint_draft(
        draft_key = key,
        prompt_text = promptText,
        selected_model = selectedModel?.name,
        selected_aspect_ratio = selectedAspectRatio?.name,
        selected_resolution = selectedResolution?.name,
        selected_gpt_size = selectedGptSize?.name,
        selected_gpt_quality = selectedGptQuality?.name,
        selected_gpt_format = selectedGptFormat?.name,
    )

    private fun Paint_draft.toDomain(images: List<PaintDraftImage>): PaintSessionDraft = PaintSessionDraft(
        promptText = prompt_text,
        selectedImages = images,
        selectedModel = selected_model?.let(::enumValue),
        selectedAspectRatio = selected_aspect_ratio?.let(::enumValue),
        selectedResolution = selected_resolution?.let(::enumValue),
        selectedGptSize = selected_gpt_size?.let(::enumValue),
        selectedGptQuality = selected_gpt_quality?.let(::enumValue),
        selectedGptFormat = selected_gpt_format?.let(::enumValue),
    )

    private fun PaintDraftImage.toEntity(key: String, sortOrder: Long): Paint_draft_image = Paint_draft_image(
        draft_key = key,
        id = id,
        local_path = uri,
        mime_type = mimeType,
        width = width.toLong(),
        height = height.toLong(),
        sort_order = sortOrder,
    )

    private fun Paint_draft_image.toDomain(): PaintDraftImage =
        PaintDraftImage(id, local_path, mime_type, width.toInt(), height.toInt())

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown ${T::class.simpleName}: $value")

    private companion object {
        const val LEGACY_MIGRATION_KEY = "legacy_settings_v1"
        val MESSAGE_STORAGE_ORDER = compareBy<PaintMessage>({ it.createdAt }, { it.id })
    }
}
