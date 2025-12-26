package com.example.livewallpaper.paint.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * AI 绘画 ViewModel
 * 负责管理 UI 状态和处理用户事件
 */
class AndroidPaintViewModel(
    private val appContext: Context,
    private val repository: PaintRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaintUiState())
    val uiState: StateFlow<PaintUiState> = _uiState.asStateFlow()

    private data class SessionDraft(
        val promptText: String = "",
        val selectedImages: List<SelectedImage> = emptyList()
    )

    private val sessionDrafts = mutableMapOf<String, SessionDraft>()
    private val newSessionDraftKey = "__new_session__"

    // 使用独立的协程作用域，确保请求不会因为切换会话而中断
    private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generationJob: Job? = null
    private var currentSessionId: String? = null

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>()
    val scrollToBottomEvent: SharedFlow<Unit> = _scrollToBottomEvent.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<PaintToastMessage>()
    val toastEvent: SharedFlow<PaintToastMessage> = _toastEvent.asSharedFlow()

    init {
        loadApiProfiles()
        loadSessions()
    }

    private fun loadApiProfiles() {
        viewModelScope.launch {
            combine(
                repository.getApiProfiles(),
                repository.getActiveProfile()
            ) { profiles, active ->
                Pair(profiles, active)
            }.collect { (profiles, active) ->
                _uiState.update { it.copy(apiProfiles = profiles, activeProfile = active) }
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
        viewModelScope.launch {
            repository.getMessages(sessionId).collect { messages ->
                val sortedMessages = messages.sortedBy { it.createdAt }
                val currentState = _uiState.value
                
                val newCount = if (!currentState.isAtBottom && sortedMessages.size > currentState.messages.size) {
                    currentState.newMessageCount + (sortedMessages.size - currentState.messages.size)
                } else {
                    0
                }
                
                _uiState.update { 
                    it.copy(
                        messages = sortedMessages,
                        newMessageCount = newCount
                    ) 
                }
            }
        }
    }


    fun onEvent(event: PaintEvent) {
        when (event) {
            is PaintEvent.CreateSession -> createSession(event.model)
            is PaintEvent.SelectSession -> selectSession(event.sessionId)
            is PaintEvent.DeleteSession -> deleteSession(event.sessionId)
            is PaintEvent.SendMessage -> sendMessage()
            is PaintEvent.StopGeneration -> stopGeneration()
            is PaintEvent.LoadMoreMessages -> loadMoreMessages()
            is PaintEvent.DeleteMessage -> deleteMessage(event.messageId)
            is PaintEvent.UpdatePrompt -> updatePrompt(event.text)
            is PaintEvent.AddImage -> addImage(event.image)
            is PaintEvent.RemoveImage -> removeImage(event.imageId)
            is PaintEvent.ClearImages -> clearImages()
            is PaintEvent.SelectModel -> selectModel(event.model)
            is PaintEvent.SelectAspectRatio -> selectAspectRatio(event.ratio)
            is PaintEvent.SelectResolution -> selectResolution(event.resolution)
            is PaintEvent.SaveApiProfile -> saveApiProfile(event.profile)
            is PaintEvent.DeleteApiProfile -> deleteApiProfile(event.profileId)
            is PaintEvent.SetActiveProfile -> setActiveProfile(event.profileId)
            is PaintEvent.EnhancePrompt -> enhancePrompt()
            is PaintEvent.UpdateScrollState -> updateScrollState(event.isAtBottom)
            is PaintEvent.ScrollToBottom -> scrollToBottom()
            is PaintEvent.ClearNewMessageCount -> clearNewMessageCount()
            is PaintEvent.ClearError -> clearError()
        }
    }

    private fun currentDraftKey(): String =
        _uiState.value.currentSession?.id ?: newSessionDraftKey

    private fun snapshotCurrentDraft(): SessionDraft =
        SessionDraft(
            promptText = _uiState.value.promptText,
            selectedImages = _uiState.value.selectedImages
        )

    private fun restoreDraft(key: String): SessionDraft =
        sessionDrafts[key] ?: SessionDraft()

    private fun createSession(model: PaintModel) {
        viewModelScope.launch {
            val session = PaintSession(
                id = generateId(),
                model = model,
                aspectRatio = _uiState.value.selectedAspectRatio,
                resolution = _uiState.value.selectedResolution
            )
            repository.createSession(session)
            if (_uiState.value.currentSession == null) {
                sessionDrafts[session.id] = sessionDrafts[newSessionDraftKey] ?: snapshotCurrentDraft()
                sessionDrafts.remove(newSessionDraftKey)
            }
            selectSession(session.id)
        }
    }

    private fun selectSession(sessionId: String) {
        viewModelScope.launch {
            sessionDrafts[currentDraftKey()] = snapshotCurrentDraft()
            repository.getSession(sessionId).first()?.let { session ->
                val draft = restoreDraft(session.id)
                _uiState.update { 
                    it.copy(
                        currentSession = session,
                        selectedModel = session.model,
                        selectedAspectRatio = session.aspectRatio,
                        selectedResolution = session.resolution,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages,
                        currentPage = 0,
                        hasMoreMessages = true,
                        newMessageCount = 0,
                        isAtBottom = true
                    ) 
                }
                loadMessages(sessionId)
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            sessionDrafts.remove(sessionId)
            if (_uiState.value.currentSession?.id == sessionId) {
                val draft = restoreDraft(newSessionDraftKey)
                _uiState.update { 
                    it.copy(
                        currentSession = null,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages
                    ) 
                }
                currentSessionId = null
            }
        }
    }


    private fun sendMessage() {
        val state = _uiState.value
        val prompt = state.promptText.trim()
        
        if (prompt.isEmpty() && state.selectedImages.isEmpty()) return
        val profile = state.activeProfile
        if (profile == null) {
            viewModelScope.launch { _toastEvent.emit(PaintToastMessage.PleaseConfigApi) }
            return
        }

        viewModelScope.launch {
            val session = state.currentSession ?: run {
                val newSession = PaintSession(
                    id = generateId(),
                    model = state.selectedModel,
                    aspectRatio = state.selectedAspectRatio,
                    resolution = state.selectedResolution
                )
                repository.createSession(newSession)
                _uiState.update { it.copy(currentSession = newSession) }
                loadMessages(newSession.id)
                newSession
            }

            val selectedImagesSnapshot = state.selectedImages

            val userImagesForMessage = selectedImagesSnapshot.map { img ->
                PaintImage(
                    id = generateId(),
                    mimeType = img.mimeType,
                    localPath = img.uri,
                    isReference = true
                )
            }
            
            val userMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                senderIdentity = SenderIdentity.USER,
                messageContent = prompt,
                messageType = if (userImagesForMessage.isNotEmpty()) MessageType.IMAGE else MessageType.TEXT,
                images = userImagesForMessage
            )
            val assistantMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING
            )
            
            val startTime = System.currentTimeMillis()
            sessionDrafts[session.id] = SessionDraft()
            sessionDrafts.remove(newSessionDraftKey)
            _uiState.update { current ->
                val nextMessages = (current.messages + userMessage + assistantMessage).sortedBy { it.createdAt }
                current.copy(
                    messages = nextMessages,
                    promptText = "",
                    selectedImages = emptyList(),
                    isGenerating = true,
                    generatingSessionId = session.id,
                    generationStartTime = startTime,
                    newMessageCount = 0
                )
            }
            
            _scrollToBottomEvent.emit(Unit)
            
            repository.addMessage(userMessage)
            repository.addMessage(assistantMessage)
            
            // 保存当前生成的会话ID和消息ID，用于后续更新
            val generatingSessionId = session.id
            val generatingMessageId = assistantMessage.id

            // 使用独立的协程作用域，确保请求不会因为切换会话或应用进入后台而中断
            generationJob = generationScope.launch {
                try {
                    val userImagesForApi = loadReferenceImagesForApi(selectedImagesSnapshot)

                    // 调用 Repository 的网络请求方法
                    val result = repository.generateImage(
                        profile = profile,
                        model = state.selectedModel,
                        prompt = prompt,
                        images = userImagesForApi,
                        aspectRatio = state.selectedAspectRatio,
                        resolution = state.selectedResolution
                    )
                    
                    result.onSuccess { base64Data ->
                        val imagePath = saveGeneratedImage(
                            sessionId = generatingSessionId,
                            messageId = generatingMessageId,
                            base64Data = base64Data
                        )
                        val updatedMessage = assistantMessage.copy(
                            messageContent = "",
                            images = listOf(
                                PaintImage(
                                    id = generateId(),
                                    localPath = imagePath,
                                    mimeType = "image/png"
                                )
                            ),
                            status = MessageStatus.SUCCESS,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(updatedMessage)
                        // 更新会话的 updatedAt
                        repository.getSession(generatingSessionId).first()?.let { sess ->
                            repository.updateSession(sess)
                        }
                        withContext(Dispatchers.Main) {
                            _toastEvent.emit(PaintToastMessage.GenerateSuccess)
                        }
                    }.onError { error ->
                        val errorMessage = assistantMessage.copy(
                            messageContent = error.message ?: "生成失败",
                            status = MessageStatus.ERROR,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(errorMessage)
                        withContext(Dispatchers.Main) {
                            _toastEvent.emit(PaintToastMessage.GenerateFailed(error.message))
                        }
                    }
                    
                } catch (e: CancellationException) {
                    // 用户主动取消，不更新消息状态
                    throw e
                } catch (e: Exception) {
                    val errorMessage = assistantMessage.copy(
                        messageContent = e.message ?: "生成失败",
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMessage(errorMessage)
                    withContext(Dispatchers.Main) {
                        _toastEvent.emit(PaintToastMessage.GenerateFailed(e.message))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _uiState.update { 
                            it.copy(
                                isGenerating = false,
                                generatingSessionId = null,
                                generationStartTime = 0L
                            ) 
                        }
                    }
                }
            }
        }
    }

    private fun saveGeneratedImage(
        sessionId: String,
        messageId: String,
        base64Data: String
    ): String? {
        val bytes = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val dir = File(appContext.filesDir, "aipaint/$sessionId").apply { mkdirs() }
        val file = File(dir, "$messageId.png")
        return try {
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: IOException) {
            null
        }
    }

    private fun loadReferenceImagesForApi(images: List<SelectedImage>): List<PaintImage> {
        if (images.isEmpty()) return emptyList()
        return images.mapNotNull { selected ->
            val uri = runCatching { Uri.parse(selected.uri) }.getOrNull() ?: return@mapNotNull null
            val bytes = try {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            PaintImage(
                id = generateId(),
                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mimeType = selected.mimeType,
                isReference = true
            )
        }
    }

    private fun stopGeneration() {
        generationJob?.cancel()
        _uiState.update { 
            it.copy(
                isGenerating = false,
                generatingSessionId = null,
                generationStartTime = 0L
            ) 
        }
        viewModelScope.launch { _toastEvent.emit(PaintToastMessage.Stopped) }
    }

    private fun loadMoreMessages() {
        // 分页加载实现
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            _toastEvent.emit(PaintToastMessage.Deleted)
        }
    }

    private fun updatePrompt(text: String) {
        val key = currentDraftKey()
        val current = sessionDrafts[key]
        sessionDrafts[key] = (current ?: snapshotCurrentDraft()).copy(promptText = text)
        _uiState.update { it.copy(promptText = text) }
    }

    private fun addImage(image: SelectedImage) {
        val key = currentDraftKey()
        val base = sessionDrafts[key] ?: snapshotCurrentDraft()
        val next = base.selectedImages + image
        sessionDrafts[key] = base.copy(selectedImages = next)
        _uiState.update { it.copy(selectedImages = it.selectedImages + image) }
    }

    private fun removeImage(imageId: String) {
        val key = currentDraftKey()
        val base = sessionDrafts[key] ?: snapshotCurrentDraft()
        val next = base.selectedImages.filter { img -> img.id != imageId }
        sessionDrafts[key] = base.copy(selectedImages = next)
        _uiState.update { 
            it.copy(selectedImages = it.selectedImages.filter { img -> img.id != imageId }) 
        }
    }

    private fun clearImages() {
        val key = currentDraftKey()
        val base = sessionDrafts[key] ?: snapshotCurrentDraft()
        sessionDrafts[key] = base.copy(selectedImages = emptyList())
        _uiState.update { it.copy(selectedImages = emptyList()) }
    }


    private fun selectModel(model: PaintModel) {
        _uiState.update { it.copy(selectedModel = model) }
        _uiState.value.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(session.copy(model = model))
            }
        }
    }

    private fun selectAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
        _uiState.value.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(session.copy(aspectRatio = ratio))
            }
        }
    }

    private fun selectResolution(resolution: Resolution) {
        _uiState.update { it.copy(selectedResolution = resolution) }
        _uiState.value.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(session.copy(resolution = resolution))
            }
        }
    }

    private fun saveApiProfile(profile: ApiProfile) {
        viewModelScope.launch {
            repository.saveApiProfile(profile)
            _toastEvent.emit(PaintToastMessage.SaveSuccess)
        }
    }

    private fun deleteApiProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteApiProfile(profileId)
            _toastEvent.emit(PaintToastMessage.Deleted)
        }
    }

    private fun setActiveProfile(profileId: String) {
        viewModelScope.launch {
            repository.setActiveProfile(profileId)
        }
    }

    private fun enhancePrompt() {
        val state = _uiState.value
        val prompt = state.promptText.trim()
        if (prompt.isEmpty()) return
        val profile = state.activeProfile
        if (profile == null) {
            viewModelScope.launch { _toastEvent.emit(PaintToastMessage.PleaseConfigApi) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 调用 Repository 的网络请求方法
                val result = repository.enhancePrompt(profile, prompt)
                
                result.onSuccess { enhanced ->
                    _uiState.update { it.copy(promptText = enhanced, isLoading = false) }
                    _toastEvent.emit(PaintToastMessage.EnhanceSuccess)
                }.onError { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _toastEvent.emit(PaintToastMessage.EnhanceFailed(error.message))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _toastEvent.emit(PaintToastMessage.EnhanceFailed(e.message))
            }
        }
    }

    private fun updateScrollState(isAtBottom: Boolean) {
        _uiState.update { 
            it.copy(
                isAtBottom = isAtBottom,
                newMessageCount = if (isAtBottom) 0 else it.newMessageCount
            ) 
        }
    }

    private fun scrollToBottom() {
        viewModelScope.launch {
            _scrollToBottomEvent.emit(Unit)
            _uiState.update { it.copy(newMessageCount = 0, isAtBottom = true) }
        }
    }

    private fun clearNewMessageCount() {
        _uiState.update { it.copy(newMessageCount = 0) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun generateId(): String = 
        "${System.currentTimeMillis()}-${Random.nextInt(10000, 99999)}"
    
    override fun onCleared() {
        super.onCleared()
        // 注意：这里不取消 generationScope，让请求继续完成
        // 如果需要在 ViewModel 销毁时取消请求，可以取消注释下面的代码
        // generationScope.cancel()
    }
}
