package com.example.livewallpaper.desktop.paint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.core.error.AppResult
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.random.Random

internal object DesktopPaintErrorText {
    const val MISSING_API = "__desktop_paint_missing_api__"
    const val GENERATION_FAILED = "__desktop_paint_generation_failed__"
}

class DesktopPaintViewModel(
    private val repository: PaintRepository
) : ViewModel() {

    private data class SessionDraft(
        val promptText: String = "",
        val selectedImages: List<SelectedImage> = emptyList()
    )

    private data class GenerationTask(
        val sessionId: String,
        val startedAt: Long,
        val job: Job
    )

    private val _uiState = MutableStateFlow(PaintUiState())
    val uiState: StateFlow<PaintUiState> = _uiState.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Boolean>()
    val scrollToBottomEvent: SharedFlow<Boolean> = _scrollToBottomEvent.asSharedFlow()

    private val sessionDrafts = mutableMapOf<String, SessionDraft>()
    private val generationTasks = mutableMapOf<String, GenerationTask>()
    private val draftPreferences = Preferences.userRoot().node(DRAFT_PREFERENCES_NODE)
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
    }

    fun onEvent(event: PaintEvent) {
        when (event) {
            is PaintEvent.CreateSession -> createSession(event.model)
            is PaintEvent.SelectSession -> selectSession(event.sessionId)
            is PaintEvent.DeleteSession -> deleteSession(event.sessionId)
            is PaintEvent.RenameSession -> renameSession(event.sessionId, event.newTitle)
            is PaintEvent.PinSession -> updateSessionPinned(event.sessionId, true)
            is PaintEvent.UnpinSession -> updateSessionPinned(event.sessionId, false)
            PaintEvent.SendMessage -> sendMessage()
            PaintEvent.StopGeneration -> stopGeneration()
            PaintEvent.LoadMoreMessages -> Unit
            is PaintEvent.DeleteMessage -> deleteMessage(event.messageId)
            is PaintEvent.DeleteMessageVersion -> deleteMessageVersion(event.versionGroup)
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
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

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
                    )
                }
            }
        }
    }

    private fun createSession(model: PaintModel) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = PaintSession(
                id = generateId(),
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
                        selectedModel = session.model,
                        selectedAspectRatio = session.aspectRatio,
                        selectedResolution = session.resolution,
                        selectedGptSize = session.gptImageSize,
                        selectedGptQuality = session.gptImageQuality,
                        selectedGptFormat = session.gptOutputFormat,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages,
                        currentPage = 0,
                        hasMoreMessages = true,
                        newMessageCount = 0,
                        isAtBottom = true,
                        isLoading = true
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
            clearPersistedDraft(sessionId)
            if (_uiState.value.currentSession?.id == sessionId) {
                currentSessionId = null
                messagesCollectJob?.cancel()
                _uiState.update {
                    it.copy(currentSession = null, messages = emptyList(), promptText = "", selectedImages = emptyList())
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
            val selectedImagesSnapshot = state.selectedImages
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
                senderIdentity = SenderIdentity.USER,
                messageContent = prompt,
                messageType = if (userImagesForMessage.isNotEmpty()) MessageType.IMAGE else MessageType.TEXT,
                images = userImagesForMessage
            )
            val versionGroup = generateId()
            val assistantMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
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
                it.copy(promptText = "", selectedImages = emptyList(), error = null)
            }
            _scrollToBottomEvent.emit(true)
            delay(650)
            repository.addMessage(assistantMessage)
            _scrollToBottomEvent.emit(true)
            launchGeneration(profile, assistantMessage, prompt, userImagesForApi)
        }
    }

    private suspend fun createSessionForSend(state: PaintUiState): PaintSession {
        val session = PaintSession(
            id = generateId(),
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
        images: List<PaintImage>
    ) {
        val startedAt = System.currentTimeMillis()
        val job = viewModelScope.launch {
            try {
                val result = if (assistantMessage.generationModel?.isGpt == true) {
                    repository.generateGptImage(
                        profile = profile,
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
                    is AppResult.Success -> saveGeneratedImages(assistantMessage, result.data)
                    is AppResult.Error -> repository.updateMessage(
                        assistantMessage.copy(
                            messageContent = result.error.message ?: DesktopPaintErrorText.GENERATION_FAILED,
                            status = MessageStatus.ERROR,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    repository.updateMessage(
                        assistantMessage.copy(status = MessageStatus.CANCELLED, updatedAt = System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                repository.updateMessage(
                    assistantMessage.copy(
                        messageContent = e.message ?: DesktopPaintErrorText.GENERATION_FAILED,
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                generationTasks.remove(assistantMessage.id)
                syncGeneratingState()
            }
        }
        generationTasks[assistantMessage.id] = GenerationTask(assistantMessage.sessionId, startedAt, job)
        syncGeneratingState()
    }

    private suspend fun saveGeneratedImages(
        assistantMessage: PaintMessage,
        files: List<GeneratedImageFile>
    ) {
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
    }

    private fun stopGeneration() {
        generationTasks.values.forEach { it.job.cancel() }
        generationTasks.clear()
        syncGeneratingState()
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
            val existingVersions = if (currentMessage.versionGroup != null) {
                repository.getVersionCount(currentMessage.sessionId, versionGroup)
            } else {
                repository.updateMessage(currentMessage.copy(versionGroup = versionGroup, versionIndex = 0))
                1
            }
            val model = state.selectedModel
            val newAssistantMessage = PaintMessage(
                id = generateId(),
                sessionId = currentMessage.sessionId,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroup,
                versionIndex = existingVersions,
                generationModel = model,
                generationAspectRatio = state.selectedAspectRatio,
                generationResolution = if (model.supportsResolution) state.selectedResolution else null,
                generationGptSize = if (model.isGpt) state.selectedGptSize else null,
                generationGptQuality = if (model.isGpt) state.selectedGptQuality else null,
                generationGptFormat = if (model.isGpt) state.selectedGptFormat else null
            )
            repository.addMessage(newAssistantMessage)
            _uiState.update { it.copy(activeVersions = it.activeVersions + (versionGroup to existingVersions)) }
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
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    private fun deleteMessageVersion(versionGroup: String) {
        viewModelScope.launch {
            _uiState.value.messages
                .filter { it.versionGroup == versionGroup }
                .forEach { repository.deleteMessage(it.id) }
            _uiState.update { it.copy(activeVersions = it.activeVersions - versionGroup) }
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
        val maxImages = _uiState.value.selectedModel.maxImages
        if (_uiState.value.selectedImages.size >= maxImages) return
        _uiState.update { it.copy(selectedImages = it.selectedImages + image) }
        saveCurrentDraft()
    }

    private fun removeImage(imageId: String) {
        _uiState.update { state -> state.copy(selectedImages = state.selectedImages.filter { it.id != imageId }) }
        saveCurrentDraft()
    }

    private fun clearImages() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
        saveCurrentDraft()
    }

    private fun selectModel(model: PaintModel) {
        _uiState.update { state ->
            val ratio = state.selectedAspectRatio.takeIf { it in AspectRatio.availableFor(model) } ?: AspectRatio.RATIO_1_1
            val resolution = state.selectedResolution.takeIf { it in Resolution.availableFor(model) } ?: Resolution.RES_1K
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
        updateCurrentSessionSettings()
    }

    private fun selectResolution(resolution: Resolution) {
        _uiState.update { it.copy(selectedResolution = resolution) }
        updateCurrentSessionSettings()
    }

    private fun selectGptSize(size: GptImageSize) {
        _uiState.update { it.copy(selectedGptSize = size) }
        updateCurrentSessionSettings()
    }

    private fun selectGptQuality(quality: GptImageQuality) {
        _uiState.update { it.copy(selectedGptQuality = quality) }
        updateCurrentSessionSettings()
    }

    private fun selectGptFormat(format: GptOutputFormat) {
        _uiState.update { it.copy(selectedGptFormat = format) }
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

    private fun deleteApiProfile(profileId: String) {
        viewModelScope.launch { repository.deleteApiProfile(profileId) }
    }

    private fun setActiveProfile(profileId: String) {
        viewModelScope.launch { repository.setActiveProfile(profileId) }
    }

    private fun updateScrollState(isAtBottom: Boolean) {
        _uiState.update { it.copy(isAtBottom = isAtBottom, newMessageCount = if (isAtBottom) 0 else it.newMessageCount) }
    }

    private fun scrollToBottom() {
        viewModelScope.launch {
            _scrollToBottomEvent.emit(false)
            _uiState.update { it.copy(newMessageCount = 0, isAtBottom = true) }
        }
    }

    private fun clearNewMessageCount() {
        _uiState.update { it.copy(newMessageCount = 0) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
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
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.map { image ->
                    if (image.uri == oldPath) {
                        val (width, height) = imageDimensions(newPath)
                        image.copy(uri = newPath, width = width, height = height)
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
        _uiState.update {
            it.copy(
                isGenerating = generationTasks.isNotEmpty(),
                generatingMessageIds = generationTasks.keys,
                generatingSessionCounts = sessionCounts,
                generatingSessionId = if (currentSession != null && currentSession in sessionCounts) currentSession else null,
                generationStartTime = generationTasks.values.minOfOrNull { task -> task.startedAt } ?: 0L
            )
        }
    }

    private fun saveCurrentDraft() {
        val sessionId = _uiState.value.currentSession?.id ?: return
        val draft = SessionDraft(
            promptText = _uiState.value.promptText,
            selectedImages = _uiState.value.selectedImages
        )
        sessionDrafts[sessionId] = draft
        savePersistedDraft(sessionId, draft)
    }

    private fun clearDraft(sessionId: String) {
        sessionDrafts[sessionId] = SessionDraft()
        clearPersistedDraft(sessionId)
    }

    private fun loadPersistedDraft(sessionId: String): SessionDraft {
        val raw = draftPreferences.get(draftKey(sessionId), "")
        if (raw.isBlank()) return SessionDraft()
        return decodeDraft(raw).let { draft ->
            SessionDraft(
                promptText = draft.promptText,
                selectedImages = draft.selectedImages.filter { File(it.uri.removePrefix("file://")).isFile }
            )
        }
    }

    private fun savePersistedDraft(sessionId: String, draft: SessionDraft) {
        if (draft.promptText.isBlank() && draft.selectedImages.isEmpty()) {
            clearPersistedDraft(sessionId)
            return
        }
        draftPreferences.put(draftKey(sessionId), encodeDraft(draft))
    }

    private fun clearPersistedDraft(sessionId: String) {
        draftPreferences.remove(draftKey(sessionId))
    }

    private fun PaintImage.asApiReferenceImage(): PaintImage? {
        val path = localPath ?: return null
        val file = File(path.removePrefix("file://"))
        if (!file.isFile) return null
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())
        return copy(base64Data = base64)
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

    private fun encodeDraft(draft: SessionDraft): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return buildString {
            appendLine(DRAFT_FORMAT_VERSION)
            appendLine(encoder.encodeToString(draft.promptText.toByteArray(Charsets.UTF_8)))
            draft.selectedImages.forEach { image ->
                append(image.id)
                append('\t')
                append(encoder.encodeToString(image.uri.toByteArray(Charsets.UTF_8)))
                append('\t')
                append(encoder.encodeToString(image.mimeType.toByteArray(Charsets.UTF_8)))
                append('\t')
                append(image.width)
                append('\t')
                append(image.height)
                appendLine()
            }
        }
    }

    private fun decodeDraft(raw: String): SessionDraft {
        return runCatching {
            val lines = raw.lineSequence().toList()
            if (lines.size < 2 || lines[0] != DRAFT_FORMAT_VERSION) return@runCatching SessionDraft()
            val decoder = Base64.getUrlDecoder()
            val prompt = String(decoder.decode(lines[1]), Charsets.UTF_8)
            val images = lines.drop(2).mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 5) return@mapNotNull null
                SelectedImage(
                    id = parts[0],
                    uri = String(decoder.decode(parts[1]), Charsets.UTF_8),
                    mimeType = String(decoder.decode(parts[2]), Charsets.UTF_8),
                    width = parts[3].toIntOrNull() ?: 0,
                    height = parts[4].toIntOrNull() ?: 0
                )
            }
            SessionDraft(promptText = prompt, selectedImages = images)
        }.getOrDefault(SessionDraft())
    }

    private fun draftKey(sessionId: String): String = "paint_draft_$sessionId"

    private companion object {
        private const val DRAFT_PREFERENCES_NODE = "com.example.livewallpaper.desktop"
        private const val DRAFT_FORMAT_VERSION = "v1"
    }
}
