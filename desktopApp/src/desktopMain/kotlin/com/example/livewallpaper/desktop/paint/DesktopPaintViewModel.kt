package com.example.livewallpaper.desktop.paint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.core.platform.DesktopPaintDraft
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileImportResult
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.ImageRequestPayload
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.activeVersionsAfterRemoving
import com.example.livewallpaper.feature.aipaint.domain.model.nextPaintVersion
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDraftImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSessionDraft
import com.example.livewallpaper.feature.aipaint.domain.model.PaintStorageState
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageStateProvider
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationBatchNotice
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationBatchState
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationCompletion
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintGenerationTaskUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintMessageSearchUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.ScrollToLatestRequest
import com.example.livewallpaper.feature.aipaint.presentation.state.ScrollToMessageRequest
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import com.example.livewallpaper.feature.aipaint.presentation.state.isAllowedWhenStorageReadOnly
import com.example.livewallpaper.feature.aipaint.presentation.state.recomputeMessageSearch
import com.example.livewallpaper.feature.aipaint.presentation.viewmodel.handleMessageSearchEvent
import com.example.livewallpaper.feature.aipaint.presentation.viewmodel.copySelectedMessages
import com.example.livewallpaper.feature.aipaint.presentation.viewmodel.deleteFailedVersions
import com.example.livewallpaper.feature.aipaint.presentation.viewmodel.deleteSelectedMessages
import com.example.livewallpaper.feature.aipaint.presentation.viewmodel.moveSelectedMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

internal object DesktopPaintErrorText {
    const val MISSING_API = "__desktop_paint_missing_api__"
    const val GENERATION_FAILED = "__desktop_paint_generation_failed__"
}

/** 桌面端在一批生成请求结束后展示的系统通知。 */
sealed interface DesktopPaintGenerationNotice {
    /** 只有一个请求成功。 */
    data class SingleSuccess(
        val sessionId: String,
        val messageId: String,
        val imageCount: Int,
    ) : DesktopPaintGenerationNotice

    /** 多个重叠请求全部结束。 */
    data class Summary(
        val successCount: Int,
        val failureCount: Int,
    ) : DesktopPaintGenerationNotice
}

class DesktopPaintViewModel(
    private val repository: PaintRepository,
    private val draftRepository: PaintDraftRepository,
    private val storageStateProvider: PaintStorageStateProvider,
    private val referenceImageStore: PaintReferenceImageStore,
) : ViewModel() {

    private data class GenerationTask(
        val sessionId: String,
        val modelName: String,
        val startedAt: Long,
        val waveId: Int,
        val job: Job
    )

    private val _uiState = MutableStateFlow(PaintUiState())
    val uiState: StateFlow<PaintUiState> = _uiState.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<ScrollToLatestRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scrollToBottomEvent: SharedFlow<ScrollToLatestRequest> = _scrollToBottomEvent.asSharedFlow()

    // 搜索结果跳转：通知 UI 滚动到指定消息并居中
    private val _scrollToMessageEvent = MutableSharedFlow<ScrollToMessageRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scrollToMessageEvent: SharedFlow<ScrollToMessageRequest> = _scrollToMessageEvent.asSharedFlow()

    private val _generationNoticeEvent = MutableSharedFlow<DesktopPaintGenerationNotice>()
    val generationNoticeEvent: SharedFlow<DesktopPaintGenerationNotice> = _generationNoticeEvent.asSharedFlow()

    private var generationBatch = GenerationBatchState()

    private val sessionDrafts = mutableMapOf<String, DesktopPaintDraft>()
    private val generationTasks = mutableMapOf<String, GenerationTask>()
    private val generationTaskHistory = mutableListOf<PaintGenerationTaskUiState>()
    private var currentSessionId: String? = null
    private var messagesCollectJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                apiProfiles = repository.getApiProfilesSync(),
                activeProfile = repository.getActiveProfileSync(),
                isApiProfileLoaded = true
            )
        }
        loadApiProfiles()
        loadSessions()
        viewModelScope.launch {
            storageStateProvider.storageState.collect { storageState ->
                _uiState.update { it.copy(storageState = storageState) }
            }
        }
        viewModelScope.launch {
            draftRepository.draftsRevision
                .drop(1)
                .collect { reloadDraftsAfterExternalChange() }
        }
    }

    fun onEvent(event: PaintEvent) {
        val storageState = _uiState.value.storageState
        val writable = storageState == PaintStorageState.Ready || storageState is PaintStorageState.LegacyFallback
        if (!writable && (storageState !is PaintStorageState.ReadOnly || !event.isAllowedWhenStorageReadOnly())) return
        when (event) {
            is PaintEvent.CreateSession -> createSession(event.model)
            is PaintEvent.SelectSession -> selectSession(event.sessionId)
            is PaintEvent.DeleteSession -> deleteSession(event.sessionId)
            is PaintEvent.RenameSession -> renameSession(event.sessionId, event.newTitle)
            is PaintEvent.PinSession -> updateSessionPinned(event.sessionId, true)
            is PaintEvent.UnpinSession -> updateSessionPinned(event.sessionId, false)
            PaintEvent.SendMessage -> sendMessage()
            PaintEvent.StopGeneration -> stopGeneration()
            is PaintEvent.CancelGeneration -> cancelGeneration(event.messageId)
            is PaintEvent.DismissGenerationTask -> dismissGenerationTask(event.messageId)
            PaintEvent.ClearGenerationTaskHistory -> clearGenerationTaskHistory()
            PaintEvent.LoadMoreMessages -> Unit
            is PaintEvent.DeleteMessage -> deleteMessage(event.messageId)
            is PaintEvent.DeleteMessageVersion -> deleteMessageVersion(event.versionGroup)
            is PaintEvent.DeleteFailedMessageVersions -> viewModelScope.launch {
                _uiState.deleteFailedVersions(repository, event.versionGroup)
            }
            is PaintEvent.DeleteMessages -> viewModelScope.launch {
                _uiState.deleteSelectedMessages(repository, event.messageIds)
            }
            is PaintEvent.MoveMessages -> viewModelScope.launch {
                _uiState.moveSelectedMessages(repository, event.messageIds, event.targetSessionId)
            }
            is PaintEvent.CopyMessages -> viewModelScope.launch {
                _uiState.copySelectedMessages(repository, event.messageIds, event.targetSessionId)
            }
            is PaintEvent.EditUserMessage -> editUserMessage(event.messageId)
            is PaintEvent.UpdateImageDimensions -> updateImageDimensions(
                event.messageId,
                event.imageId,
                event.width,
                event.height
            )
            is PaintEvent.ReplaceImagePath -> replaceImagePath(event.oldPath, event.newPath)
            is PaintEvent.RegenerateMessage -> regenerateMessage(event.messageId)
            is PaintEvent.SwitchMessageVersion -> switchMessageVersion(event.versionGroup, event.targetIndex)
            is PaintEvent.UpdatePrompt -> updatePrompt(event.text)
            is PaintEvent.AddImage -> addImage(event.image)
            is PaintEvent.RemoveImage -> removeImage(event.imageId)
            is PaintEvent.ReorderImages -> reorderImages(event.images)
            PaintEvent.ClearImages -> clearImages()
            is PaintEvent.SelectModel -> selectModel(event.model)
            is PaintEvent.SelectAspectRatio -> selectAspectRatio(event.ratio)
            is PaintEvent.SelectResolution -> selectResolution(event.resolution)
            is PaintEvent.SelectGptSize -> selectGptSize(event.size)
            is PaintEvent.SelectGptQuality -> selectGptQuality(event.quality)
            is PaintEvent.SelectGptFormat -> selectGptFormat(event.format)
            is PaintEvent.SaveApiProfile -> saveApiProfile(event.profile)
            is PaintEvent.DeleteApiProfile -> deleteApiProfile(event.profileId)
            is PaintEvent.SetActiveProfile -> setActiveProfile(event.profileId)
            is PaintEvent.UpdateScrollState -> updateScrollState(event.isAtBottom)
            PaintEvent.ScrollToBottom -> scrollToBottom()
            PaintEvent.ClearNewMessageCount -> clearNewMessageCount()
            PaintEvent.OpenMessageSearch,
            PaintEvent.CloseMessageSearch,
            is PaintEvent.UpdateMessageSearchQuery,
            is PaintEvent.StepMessageSearch,
            is PaintEvent.JumpToMessage,
            -> handleMessageSearch(event)
            PaintEvent.ClearError -> clearError()
        }
    }

    private fun loadApiProfiles() {
        viewModelScope.launch {
            combine(repository.getApiProfiles(), repository.getActiveProfile()) { profiles, active ->
                profiles to active
            }.collect { (profiles, active) ->
                _uiState.update {
                    it.copy(apiProfiles = profiles, activeProfile = active, isApiProfileLoaded = true)
                }
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            repository.getSessions().collect { sessions ->
                val previousSession = _uiState.value.currentSession
                val refreshedSession = previousSession?.let { current ->
                    sessions.firstOrNull { it.id == current.id }
                }
                val generationSettingsChanged = previousSession != null && refreshedSession != null &&
                    previousSession.hasDifferentGenerationSettings(refreshedSession)
                val refreshedDraft = refreshedSession
                    ?.takeIf { generationSettingsChanged }
                    ?.let { session -> sessionDrafts[session.id] ?: loadPersistedDraft(session.id) }

                if (previousSession != null && refreshedSession == null) {
                    currentSessionId = null
                    messagesCollectJob?.cancel()
                }
                _uiState.update { state ->
                    when {
                        previousSession == null -> state.copy(sessions = sessions)
                        refreshedSession == null -> state.copy(
                            sessions = sessions,
                            currentSession = null,
                            messages = emptyList(),
                        )
                        generationSettingsChanged -> state.copy(
                            sessions = sessions,
                            currentSession = refreshedSession,
                            selectedModel = refreshedDraft?.selectedModel ?: refreshedSession.model,
                            selectedAspectRatio = refreshedDraft?.selectedAspectRatio ?: refreshedSession.aspectRatio,
                            selectedResolution = refreshedDraft?.selectedResolution ?: refreshedSession.resolution,
                            selectedGptSize = refreshedDraft?.selectedGptSize ?: refreshedSession.gptImageSize,
                            selectedGptQuality = refreshedDraft?.selectedGptQuality ?: refreshedSession.gptImageQuality,
                            selectedGptFormat = refreshedDraft?.selectedGptFormat ?: refreshedSession.gptOutputFormat,
                        )
                        else -> state.copy(sessions = sessions, currentSession = refreshedSession)
                    }
                }
                syncGeneratingState()
            }
        }
    }

    private fun PaintSession.hasDifferentGenerationSettings(other: PaintSession): Boolean =
        model != other.model ||
            aspectRatio != other.aspectRatio ||
            resolution != other.resolution ||
            gptImageSize != other.gptImageSize ||
            gptImageQuality != other.gptImageQuality ||
            gptOutputFormat != other.gptOutputFormat

    private fun loadMessages(sessionId: String) {
        currentSessionId = sessionId
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            repository.getMessages(sessionId).collect { messages ->
                if (currentSessionId != sessionId) return@collect
                val sortedMessages = messages.sortedBy { it.createdAt }
                val currentState = _uiState.value
                val latestVersionPositions = sortedMessages
                    .filter { it.versionGroup != null }
                    .groupBy { it.versionGroup!! }
                    .mapValues { (_, versionMessages) -> versionMessages.size - 1 }
                val mergedVersions = latestVersionPositions.mapValues { (group, latestPosition) ->
                    currentState.activeVersions[group]?.takeIf { it <= latestPosition } ?: latestPosition
                }
                val newCount = if (!currentState.isAtBottom && sortedMessages.size > currentState.messages.size) {
                    currentState.newMessageCount + sortedMessages.size - currentState.messages.size
                } else {
                    0
                }
                _uiState.update {
                    it.copy(
                        messages = sortedMessages,
                        activeVersions = mergedVersions,
                        newMessageCount = newCount
                    ).recomputeMessageSearch()
                }
            }
        }
    }

    private fun createSession(model: PaintModel) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = PaintSession(
                id = generateId(),
                originPlatform = PaintClientPlatform.DESKTOP,
                model = model,
                aspectRatio = state.selectedAspectRatio,
                resolution = state.selectedResolution,
                gptImageSize = state.selectedGptSize,
                gptImageQuality = state.selectedGptQuality,
                gptOutputFormat = state.selectedGptFormat
            )
            repository.createSession(session)
            selectSession(session.id)
        }
    }

    private fun selectSession(sessionId: String) {
        if (_uiState.value.currentSession?.id == sessionId) return
        viewModelScope.launch {
            saveCurrentDraft()
            repository.getSession(sessionId).first()?.let { session ->
                val draft = sessionDrafts[session.id] ?: loadPersistedDraft(session.id)
                _uiState.update {
                    it.copy(
                        currentSession = session,
                        selectedModel = draft.selectedModel ?: session.model,
                        selectedAspectRatio = draft.selectedAspectRatio ?: session.aspectRatio,
                        selectedResolution = draft.selectedResolution ?: session.resolution,
                        selectedGptSize = draft.selectedGptSize ?: session.gptImageSize,
                        selectedGptQuality = draft.selectedGptQuality ?: session.gptImageQuality,
                        selectedGptFormat = draft.selectedGptFormat ?: session.gptOutputFormat,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages,
                        currentPage = 0,
                        hasMoreMessages = true,
                        newMessageCount = 0,
                        isAtBottom = true,
                        isLoading = true,
                        messageSearch = PaintMessageSearchUiState(),
                    )
                }
                delay(80)
                loadMessages(sessionId)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            sessionDrafts.remove(sessionId)
            generationTaskHistory.removeAll { it.sessionId == sessionId }
            clearPersistedDraft(sessionId)
            generationTasks
                .filterValues { it.sessionId == sessionId }
                .keys
                .toList()
                .forEach { cancelGeneration(it) }
            if (_uiState.value.currentSession?.id == sessionId) {
                currentSessionId = null
                messagesCollectJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentSession = null,
                        messages = emptyList(),
                        promptText = "",
                        selectedImages = emptyList(),
                        messageSearch = PaintMessageSearchUiState(),
                    )
                }
            }
        }
    }

    private fun renameSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.getSession(sessionId).first()?.let { session ->
                val updated = session.copy(title = trimmed)
                repository.updateSession(updated)
                if (_uiState.value.currentSession?.id == sessionId) {
                    _uiState.update { it.copy(currentSession = updated) }
                }
            }
        }
    }

    private fun updateSessionPinned(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.getSession(sessionId).first()?.let { session ->
                val updated = session.copy(
                    isPinned = pinned,
                    pinnedAt = if (pinned) System.currentTimeMillis() else null
                )
                repository.updateSession(updated)
                if (_uiState.value.currentSession?.id == sessionId) {
                    _uiState.update { it.copy(currentSession = updated) }
                }
            }
        }
    }

    private fun sendMessage() {
        val state = _uiState.value
        val prompt = state.promptText.trim()
        val profile = state.activeProfile
        if (prompt.isEmpty() && state.selectedImages.isEmpty()) return
        if (profile == null) {
            _uiState.update { it.copy(error = DesktopPaintErrorText.MISSING_API) }
            return
        }

        viewModelScope.launch {
            val session = state.currentSession ?: createSessionForSend(state)
            val selectedImagesSnapshot = state.selectedImages.map { image ->
                val storedPath = referenceImageStore.persistReference(
                    sessionId = session.id,
                    imageId = image.id,
                    sourceIdentifier = image.uri,
                    mimeType = image.mimeType,
                ) ?: return@launch
                image.copy(uri = storedPath)
            }
            val userImagesForMessage = withContext(Dispatchers.IO) {
                selectedImagesSnapshot.map { selected ->
                    val (width, height) = imageDimensions(selected.uri)
                    PaintImage(
                        id = generateId(),
                        localPath = selected.uri,
                        mimeType = selected.mimeType,
                        width = width,
                        height = height,
                        isReference = true
                    )
                }
            }
            val userImagesForApi = withContext(Dispatchers.IO) {
                userImagesForMessage.mapNotNull { image -> image.asApiReferenceImage() }
            }
            val userMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                originPlatform = PaintClientPlatform.DESKTOP,
                senderIdentity = SenderIdentity.USER,
                messageContent = prompt,
                messageType = if (userImagesForMessage.isNotEmpty()) MessageType.IMAGE else MessageType.TEXT,
                images = userImagesForMessage
            )
            val versionGroup = generateId()
            val assistantMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                originPlatform = PaintClientPlatform.DESKTOP,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroup,
                versionIndex = 0,
                generationModel = state.selectedModel,
                generationAspectRatio = state.selectedAspectRatio,
                generationResolution = if (state.selectedModel.supportsResolution) state.selectedResolution else null,
                generationGptSize = if (state.selectedModel.isGpt) state.selectedGptSize else null,
                generationGptQuality = if (state.selectedModel.isGpt) state.selectedGptQuality else null,
                generationGptFormat = if (state.selectedModel.isGpt) state.selectedGptFormat else null
            )

            repository.addMessage(userMessage)
            if (session.title == "新会话" && prompt.isNotBlank()) {
                repository.updateSession(session.copy(title = prompt.take(28)))
            }
            clearDraft(session.id)
            _uiState.update {
                val nextMessages = (it.messages + userMessage)
                    .distinctBy { message -> message.id }
                    .sortedBy { message -> message.createdAt }
                it.copy(
                    messages = nextMessages,
                    promptText = "",
                    selectedImages = emptyList(),
                    error = null,
                    newMessageCount = 0,
                    isAtBottom = true,
                )
            }
            _scrollToBottomEvent.emit(
                ScrollToLatestRequest(animate = true, messageId = userMessage.id)
            )
            delay(650)
            repository.addMessage(assistantMessage)
            _uiState.update {
                val nextMessages = (it.messages + assistantMessage)
                    .distinctBy { message -> message.id }
                    .sortedBy { message -> message.createdAt }
                it.copy(messages = nextMessages)
            }
            _scrollToBottomEvent.emit(
                ScrollToLatestRequest(animate = true, messageId = assistantMessage.id)
            )
            launchGeneration(profile, assistantMessage, prompt, userImagesForApi)
        }
    }

    private suspend fun createSessionForSend(state: PaintUiState): PaintSession {
        val session = PaintSession(
            id = generateId(),
            originPlatform = PaintClientPlatform.DESKTOP,
            model = state.selectedModel,
            aspectRatio = state.selectedAspectRatio,
            resolution = state.selectedResolution,
            gptImageSize = state.selectedGptSize,
            gptImageQuality = state.selectedGptQuality,
            gptOutputFormat = state.selectedGptFormat
        )
        repository.createSession(session)
        _uiState.update { it.copy(currentSession = session) }
        loadMessages(session.id)
        return session
    }

    private fun launchGeneration(
        profile: ApiProfile,
        assistantMessage: PaintMessage,
        prompt: String,
        images: List<ImageRequestPayload>
    ) {
        val startedAt = System.currentTimeMillis()
        val waveStart = generationBatch.onTaskStarted()
        generationBatch = waveStart.state
        val waveId = waveStart.waveId
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var finalStatus = MessageStatus.ERROR
            var imageCount = 0
            try {
                val result = if (assistantMessage.generationModel?.isGpt == true) {
                    repository.generateGptImage(
                        profile = profile,
                        model = assistantMessage.generationModel ?: PaintModel.GPT_IMAGE_2,
                        prompt = prompt,
                        images = images,
                        size = assistantMessage.generationGptSize ?: GptImageSize.AUTO,
                        quality = assistantMessage.generationGptQuality ?: GptImageQuality.AUTO,
                        outputFormat = assistantMessage.generationGptFormat ?: GptOutputFormat.PNG,
                        sessionId = assistantMessage.sessionId,
                        messageId = assistantMessage.id
                    )
                } else {
                    repository.generateImage(
                        profile = profile,
                        model = assistantMessage.generationModel ?: PaintModel.GEMINI_2_5_FLASH,
                        prompt = prompt,
                        images = images,
                        aspectRatio = assistantMessage.generationAspectRatio ?: AspectRatio.RATIO_1_1,
                        resolution = assistantMessage.generationResolution ?: Resolution.RES_1K,
                        sessionId = assistantMessage.sessionId,
                        messageId = assistantMessage.id
                    )
                }
                when (result) {
                    is AppResult.Success -> {
                        finalStatus = MessageStatus.SUCCESS
                        imageCount = saveGeneratedImages(assistantMessage, result.data)
                    }
                    is AppResult.Error -> {
                        finalStatus = MessageStatus.ERROR
                        repository.updateMessage(
                            assistantMessage.copy(
                                messageContent = result.error.message ?: DesktopPaintErrorText.GENERATION_FAILED,
                                status = MessageStatus.ERROR,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                finalStatus = MessageStatus.CANCELLED
                withContext(NonCancellable) {
                    repository.updateMessage(
                        assistantMessage.copy(status = MessageStatus.CANCELLED, updatedAt = System.currentTimeMillis())
                    )
                }
            } catch (e: Throwable) {
                repository.updateMessage(
                    assistantMessage.copy(
                        messageContent = e.message ?: DesktopPaintErrorText.GENERATION_FAILED,
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    completeGenerationTask(assistantMessage.id, finalStatus, imageCount)
                    syncGeneratingState()
                }
            }
        }
        generationTasks[assistantMessage.id] = GenerationTask(
            sessionId = assistantMessage.sessionId,
            modelName = assistantMessage.generationModel?.displayName ?: "",
            startedAt = startedAt,
            waveId = waveId,
            job = job
        )
        job.start()
        syncGeneratingState()
    }

    private suspend fun saveGeneratedImages(
        assistantMessage: PaintMessage,
        files: List<GeneratedImageFile>
    ): Int {
        val images = files.map { file ->
            PaintImage(
                id = generateId(),
                localPath = file.filePath,
                mimeType = mimeTypeFromPath(file.filePath),
                width = file.width,
                height = file.height
            )
        }
        repository.updateMessage(
            assistantMessage.copy(
                images = images,
                status = MessageStatus.SUCCESS,
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.getSession(assistantMessage.sessionId).first()?.let { repository.updateSession(it) }
        return images.size
    }

    private fun stopGeneration() {
        generationBatch = generationBatch.suppress()
        generationTasks.values.forEach { it.job.cancel() }
        syncGeneratingState()
    }

    private fun cancelGeneration(messageId: String) {
        generationTasks[messageId]?.job?.cancel()
        syncGeneratingState()
    }

    private fun dismissGenerationTask(messageId: String) {
        generationTaskHistory.removeAll { it.messageId == messageId }
        syncGeneratingState()
    }

    private fun clearGenerationTaskHistory() {
        generationTaskHistory.clear()
        syncGeneratingState()
    }

    private suspend fun completeGenerationTask(messageId: String, status: MessageStatus, imageCount: Int) {
        val task = generationTasks.remove(messageId) ?: return
        val sessionTitle = _uiState.value.sessions.firstOrNull { it.id == task.sessionId }?.title.orEmpty()
        generationTaskHistory.removeAll { it.messageId == messageId }
        generationTaskHistory.add(
            0,
            PaintGenerationTaskUiState(
                messageId = messageId,
                sessionId = task.sessionId,
                sessionTitle = sessionTitle,
                modelName = task.modelName,
                startedAt = task.startedAt,
                status = status,
                completedAt = System.currentTimeMillis()
            )
        )
        if (generationTaskHistory.size > MAX_GENERATION_TASK_HISTORY) {
            generationTaskHistory.subList(MAX_GENERATION_TASK_HISTORY, generationTaskHistory.size).clear()
        }
        publishGenerationNotice(task, messageId, status, imageCount)
    }

    /**
     * 单个成功仍提示图片数量。多个重叠请求要等全部结束后再提示成功和失败数量。
     */
    private suspend fun publishGenerationNotice(
        task: GenerationTask,
        messageId: String,
        status: MessageStatus,
        imageCount: Int,
    ) {
        val completion = when (status) {
            MessageStatus.SUCCESS -> GenerationCompletion.Success(imageCount)
            MessageStatus.CANCELLED -> GenerationCompletion.Cancelled
            else -> GenerationCompletion.Failed(null)
        }
        val finished = generationBatch.onTaskFinished(task.waveId, completion)
        generationBatch = finished.state
        when (val notice = finished.notice) {
            is GenerationBatchNotice.SingleSuccess -> {
                if (notice.imageCount <= 0) return
                _generationNoticeEvent.emit(
                    DesktopPaintGenerationNotice.SingleSuccess(
                        sessionId = task.sessionId,
                        messageId = messageId,
                        imageCount = notice.imageCount,
                    )
                )
            }
            is GenerationBatchNotice.Summary -> _generationNoticeEvent.emit(
                DesktopPaintGenerationNotice.Summary(
                    successCount = notice.successCount,
                    failureCount = notice.failureCount,
                )
            )
            is GenerationBatchNotice.SingleFailure, null -> Unit
        }
    }

    private fun regenerateMessage(messageId: String) {
        val state = _uiState.value
        val profile = state.activeProfile ?: run {
            _uiState.update { it.copy(error = DesktopPaintErrorText.MISSING_API) }
            return
        }
        viewModelScope.launch {
            val currentMessage = repository.getMessage(messageId) ?: return@launch
            if (currentMessage.senderIdentity != SenderIdentity.ASSISTANT) return@launch
            val userMessage = findParentUserMessage(currentMessage) ?: return@launch
            val versionGroup = currentMessage.versionGroup ?: generateId()
            val existingVersionIndexes = if (currentMessage.versionGroup != null) {
                repository.getMessagesByVersionGroup(currentMessage.sessionId, versionGroup).map { it.versionIndex }
            } else {
                repository.updateMessage(currentMessage.copy(versionGroup = versionGroup, versionIndex = 0))
                listOf(0)
            }
            val (newVersionIndex, activePosition) = nextPaintVersion(existingVersionIndexes)
            val model = state.selectedModel
            val newAssistantMessage = PaintMessage(
                id = generateId(),
                sessionId = currentMessage.sessionId,
                originPlatform = PaintClientPlatform.DESKTOP,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroup,
                versionIndex = newVersionIndex,
                generationModel = model,
                generationAspectRatio = state.selectedAspectRatio,
                generationResolution = if (model.supportsResolution) state.selectedResolution else null,
                generationGptSize = if (model.isGpt) state.selectedGptSize else null,
                generationGptQuality = if (model.isGpt) state.selectedGptQuality else null,
                generationGptFormat = if (model.isGpt) state.selectedGptFormat else null
            )
            repository.addMessage(newAssistantMessage)
            _uiState.update { it.copy(activeVersions = it.activeVersions + (versionGroup to activePosition)) }
            val apiImages = withContext(Dispatchers.IO) {
                userMessage.images.filter { it.isReference }.mapNotNull { it.asApiReferenceImage() }
            }
            launchGeneration(profile, newAssistantMessage, userMessage.messageContent, apiImages)
        }
    }

    private suspend fun findParentUserMessage(aiMessage: PaintMessage): PaintMessage? {
        aiMessage.parentUserMessageId?.let { return repository.getMessage(it) }
        val messages = _uiState.value.messages.sortedBy { it.createdAt }
        val index = messages.indexOfFirst { it.id == aiMessage.id }
        if (index <= 0) return null
        return (index - 1 downTo 0)
            .map { messages[it] }
            .firstOrNull { it.senderIdentity == SenderIdentity.USER }
    }

    private fun deleteMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (message.status == MessageStatus.GENERATING) return
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    private fun deleteMessageVersion(versionGroup: String) {
        viewModelScope.launch {
            val messages = _uiState.value.messages.filter { it.versionGroup == versionGroup }
            val removing = messages.filter { it.status != MessageStatus.GENERATING }
            if (removing.isEmpty()) return@launch
            val removedIds = removing.map { it.id }.toSet()
            _uiState.update { state ->
                state.copy(
                    activeVersions = activeVersionsAfterRemoving(
                        messages = state.messages,
                        activeVersions = state.activeVersions,
                        removedIds = removedIds,
                    ),
                )
            }
            removing.forEach { repository.deleteMessage(it.id) }
        }
    }

    private fun editUserMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (message.senderIdentity != SenderIdentity.USER) return
        _uiState.update {
            it.copy(
                promptText = message.messageContent,
                selectedImages = message.images.mapNotNull { image ->
                    val path = image.localPath ?: return@mapNotNull null
                    SelectedImage(image.id, path, image.mimeType, image.width, image.height)
                }
            )
        }
        saveCurrentDraft()
    }

    private fun updatePrompt(text: String) {
        _uiState.update { it.copy(promptText = text) }
        saveCurrentDraft()
    }

    private fun addImage(image: SelectedImage) {
        viewModelScope.launch {
            val maxImages = _uiState.value.selectedModel.maxImages
            if (_uiState.value.selectedImages.size >= maxImages) return@launch
            val sessionKey = _uiState.value.currentSession?.id ?: "__temp_draft__"
            val storedPath = referenceImageStore.persistReference(
                sessionId = sessionKey,
                imageId = image.id,
                sourceIdentifier = image.uri,
                mimeType = image.mimeType,
            ) ?: return@launch
            _uiState.update { it.copy(selectedImages = it.selectedImages + image.copy(uri = storedPath)) }
            saveCurrentDraft()
        }
    }

    private fun removeImage(imageId: String) {
        _uiState.update { state -> state.copy(selectedImages = state.selectedImages.filter { it.id != imageId }) }
        saveCurrentDraft()
    }

    private fun reorderImages(images: List<SelectedImage>) {
        val selectedIds = _uiState.value.selectedImages.map { it.id }.toSet()
        if (images.map { it.id }.toSet() != selectedIds) return
        _uiState.update { it.copy(selectedImages = images) }
        saveCurrentDraft()
    }

    private fun clearImages() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
        saveCurrentDraft()
    }

    private fun selectModel(model: PaintModel) {
        _uiState.update { state ->
            val ratio = state.selectedAspectRatio
                .takeIf { it in AspectRatio.availableFor(model) }
                ?: AspectRatio.RATIO_1_1
            val resolution = state.selectedResolution
                .takeIf { it in Resolution.availableFor(model) }
                ?: Resolution.RES_1K
            state.copy(
                selectedModel = model,
                selectedAspectRatio = ratio,
                selectedResolution = resolution,
                selectedImages = state.selectedImages.take(model.maxImages)
            )
        }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun selectAspectRatio(ratio: AspectRatio) {
        _uiState.update { state ->
            val gptSize = if (state.selectedModel.isGpt) GptImageSize.fromAspectRatio(ratio) else state.selectedGptSize
            state.copy(selectedAspectRatio = ratio, selectedGptSize = gptSize)
        }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun selectResolution(resolution: Resolution) {
        _uiState.update { it.copy(selectedResolution = resolution) }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun selectGptSize(size: GptImageSize) {
        _uiState.update { it.copy(selectedGptSize = size) }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun selectGptQuality(quality: GptImageQuality) {
        _uiState.update { it.copy(selectedGptQuality = quality) }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun selectGptFormat(format: GptOutputFormat) {
        _uiState.update { it.copy(selectedGptFormat = format) }
        saveCurrentDraft()
        updateCurrentSessionSettings()
    }

    private fun updateCurrentSessionSettings() {
        val state = _uiState.value
        val session = state.currentSession ?: return
        viewModelScope.launch {
            repository.updateSession(
                session.copy(
                    model = state.selectedModel,
                    aspectRatio = state.selectedAspectRatio,
                    resolution = state.selectedResolution,
                    gptImageSize = state.selectedGptSize,
                    gptImageQuality = state.selectedGptQuality,
                    gptOutputFormat = state.selectedGptFormat
                )
            )
        }
    }

    private fun saveApiProfile(profile: ApiProfile) {
        viewModelScope.launch { repository.saveApiProfile(profile) }
    }

    /**
     * 生成包含全部绘画 API 配置及当前启用项的 JSON 备份。
     *
     * @return 包含访问令牌的 JSON 内容，由桌面端写入用户指定文件。
     */
    fun exportApiProfilesJson(): String = repository.exportApiProfilesJson()

    /**
     * 从 JSON 内容合并绘画 API 配置。
     *
     * @param content 从用户选择文件读取的 JSON 内容。
     * @return 导入结果；校验失败时不会修改任何已有配置。
     */
    suspend fun importApiProfilesJson(content: String): ApiProfileImportResult =
        repository.importApiProfilesJson(content)

    private fun deleteApiProfile(profileId: String) {
        viewModelScope.launch { repository.deleteApiProfile(profileId) }
    }

    private fun setActiveProfile(profileId: String) {
        viewModelScope.launch { repository.setActiveProfile(profileId) }
    }

    private fun updateScrollState(isAtBottom: Boolean) {
        _uiState.update {
            it.copy(
                isAtBottom = isAtBottom,
                newMessageCount = if (isAtBottom) 0 else it.newMessageCount,
            )
        }
    }

    private fun scrollToBottom() {
        viewModelScope.launch {
            _scrollToBottomEvent.emit(ScrollToLatestRequest(animate = false))
            _uiState.update { it.copy(newMessageCount = 0, isAtBottom = true) }
        }
    }

    private fun clearNewMessageCount() {
        _uiState.update { it.copy(newMessageCount = 0) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 处理会话内搜索事件：状态更新由 shared 复用逻辑完成，需要定位时通知 UI 滚动到目标消息。
     */
    private fun handleMessageSearch(event: PaintEvent) {
        val target = _uiState.handleMessageSearchEvent(event) ?: return
        _scrollToMessageEvent.tryEmit(ScrollToMessageRequest(target.messageId))
    }

    private fun switchMessageVersion(versionGroup: String, targetIndex: Int) {
        _uiState.update { it.copy(activeVersions = it.activeVersions + (versionGroup to targetIndex)) }
    }

    private fun updateImageDimensions(messageId: String, imageId: String, width: Int, height: Int) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId } ?: return@launch
            val updatedImages = message.images.map { image ->
                if (image.id == imageId && (image.width == 0 || image.height == 0)) {
                    image.copy(width = width, height = height)
                } else {
                    image
                }
            }
            if (updatedImages != message.images) {
                repository.updateMessage(message.copy(images = updatedImages))
            }
        }
    }

    private fun replaceImagePath(oldPath: String, newPath: String) {
        val (newWidth, newHeight) = imageDimensions(newPath)
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.map { image ->
                    if (image.uri == oldPath) {
                        image.copy(uri = newPath, width = newWidth, height = newHeight)
                    } else {
                        image
                    }
                }
            )
        }
        saveCurrentDraft()
    }

    private fun syncGeneratingState() {
        val sessionCounts = generationTasks.values.groupingBy { it.sessionId }.eachCount()
        val currentSession = _uiState.value.currentSession?.id
        val sessionTitles = _uiState.value.sessions.associate { it.id to it.title }
        val taskItems = generationTasks.map { (messageId, task) ->
            PaintGenerationTaskUiState(
                messageId = messageId,
                sessionId = task.sessionId,
                sessionTitle = sessionTitles[task.sessionId] ?: "",
                modelName = task.modelName,
                startedAt = task.startedAt,
                status = MessageStatus.GENERATING
            )
        }.sortedBy { it.startedAt } + generationTaskHistory.map { task ->
            task.copy(sessionTitle = sessionTitles[task.sessionId] ?: task.sessionTitle)
        }
        _uiState.update {
            it.copy(
                isGenerating = generationTasks.isNotEmpty(),
                generatingMessageIds = generationTasks.keys,
                generatingSessionCounts = sessionCounts,
                generatingSessionId = if (currentSession != null && currentSession in sessionCounts) {
                    currentSession
                } else {
                    null
                },
                generationStartTime = generationTasks.values.minOfOrNull { task -> task.startedAt } ?: 0L,
                generationTasks = taskItems
            )
        }
    }

    private fun saveCurrentDraft() {
        val sessionId = _uiState.value.currentSession?.id ?: return
        val draft = DesktopPaintDraft(
            promptText = _uiState.value.promptText,
            selectedImages = _uiState.value.selectedImages,
            selectedModel = _uiState.value.selectedModel,
            selectedAspectRatio = _uiState.value.selectedAspectRatio,
            selectedResolution = _uiState.value.selectedResolution,
            selectedGptSize = _uiState.value.selectedGptSize,
            selectedGptQuality = _uiState.value.selectedGptQuality,
            selectedGptFormat = _uiState.value.selectedGptFormat
        )
        val expectedRevision = draftRepository.draftsRevision.value
        viewModelScope.launch {
            if (draftRepository.saveDraft(sessionId, draft.toDomainDraft(), expectedRevision)) {
                sessionDrafts[sessionId] = draft
            }
        }
    }

    private suspend fun clearDraft(sessionId: String) {
        val expectedRevision = draftRepository.draftsRevision.value
        if (draftRepository.removeDraft(sessionId, expectedRevision)) {
            sessionDrafts[sessionId] = DesktopPaintDraft()
        }
    }

    private suspend fun loadPersistedDraft(sessionId: String): DesktopPaintDraft =
        draftRepository.getDraft(sessionId)?.toDesktopDraft() ?: DesktopPaintDraft()

    private suspend fun clearPersistedDraft(sessionId: String) {
        val expectedRevision = draftRepository.draftsRevision.value
        draftRepository.removeDraft(sessionId, expectedRevision)
    }

    private suspend fun reloadDraftsAfterExternalChange() {
        sessionDrafts.clear()
        val session = _uiState.value.currentSession ?: return
        val draft = loadPersistedDraft(session.id)
        sessionDrafts[session.id] = draft
        _uiState.update { state ->
            state.copy(
                selectedModel = draft.selectedModel ?: session.model,
                selectedAspectRatio = draft.selectedAspectRatio ?: session.aspectRatio,
                selectedResolution = draft.selectedResolution ?: session.resolution,
                selectedGptSize = draft.selectedGptSize ?: session.gptImageSize,
                selectedGptQuality = draft.selectedGptQuality ?: session.gptImageQuality,
                selectedGptFormat = draft.selectedGptFormat ?: session.gptOutputFormat,
                promptText = draft.promptText,
                selectedImages = draft.selectedImages,
            )
        }
    }

    private fun DesktopPaintDraft.toDomainDraft(): PaintSessionDraft = PaintSessionDraft(
        promptText = promptText,
        selectedImages = selectedImages.map { image -> image.toDomainDraftImage() },
        selectedModel = selectedModel,
        selectedAspectRatio = selectedAspectRatio,
        selectedResolution = selectedResolution,
        selectedGptSize = selectedGptSize,
        selectedGptQuality = selectedGptQuality,
        selectedGptFormat = selectedGptFormat,
    )

    private fun PaintSessionDraft.toDesktopDraft(): DesktopPaintDraft = DesktopPaintDraft(
        promptText = promptText,
        selectedImages = selectedImages.map { image -> image.toSelectedImage() },
        selectedModel = selectedModel,
        selectedAspectRatio = selectedAspectRatio,
        selectedResolution = selectedResolution,
        selectedGptSize = selectedGptSize,
        selectedGptQuality = selectedGptQuality,
        selectedGptFormat = selectedGptFormat,
    )

    private fun SelectedImage.toDomainDraftImage(): PaintDraftImage = PaintDraftImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )

    private fun PaintDraftImage.toSelectedImage(): SelectedImage = SelectedImage(
        id = id,
        uri = uri,
        mimeType = mimeType,
        width = width,
        height = height,
    )

    private fun PaintImage.asApiReferenceImage(): ImageRequestPayload? {
        val path = localPath ?: return null
        val file = File(path.removePrefix("file://"))
        if (!file.isFile) return null
        return ImageRequestPayload(bytes = file.readBytes(), mimeType = mimeType)
    }

    private fun imageDimensions(path: String): Pair<Int, Int> {
        return runCatching {
            val image = ImageIO.read(File(path.removePrefix("file://"))) ?: return@runCatching 0 to 0
            image.width to image.height
        }.getOrDefault(0 to 0)
    }

    private fun mimeTypeFromPath(path: String): String {
        return when {
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/png"
        }
    }

    private fun generateId(): String =
        "${System.currentTimeMillis()}-${Random.nextInt(10000, 99999)}"

    private companion object {
        private const val MAX_GENERATION_TASK_HISTORY = 50
    }
}
