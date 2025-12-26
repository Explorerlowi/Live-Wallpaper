package com.example.livewallpaper.feature.aipaint.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class PaintViewModel(
    private val repository: PaintRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaintUiState())
    val uiState: StateFlow<PaintUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var currentSessionId: String? = null

    // 用于通知UI滚动到底部
    private val _scrollToBottomEvent = MutableSharedFlow<Unit>()
    val scrollToBottomEvent: SharedFlow<Unit> = _scrollToBottomEvent.asSharedFlow()

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
                
                // 检测新消息
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

    private fun createSession(model: PaintModel) {
        viewModelScope.launch {
            val session = PaintSession(
                id = generateId(),
                model = model,
                aspectRatio = _uiState.value.selectedAspectRatio,
                resolution = _uiState.value.selectedResolution
            )
            repository.createSession(session)
            selectSession(session.id)
        }
    }

    private fun selectSession(sessionId: String) {
        viewModelScope.launch {
            repository.getSession(sessionId).first()?.let { session ->
                _uiState.update { 
                    it.copy(
                        currentSession = session,
                        selectedModel = session.model,
                        selectedAspectRatio = session.aspectRatio,
                        selectedResolution = session.resolution,
                        messages = emptyList(),
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
            if (_uiState.value.currentSession?.id == sessionId) {
                _uiState.update { 
                    it.copy(
                        currentSession = null,
                        messages = emptyList()
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
        if (state.activeProfile == null) {
            _uiState.update { it.copy(error = "请先配置API") }
            return
        }

        viewModelScope.launch {
            // 确保有会话
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

            // 创建用户消息
            val userImages = state.selectedImages.map { img ->
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
                messageType = if (userImages.isNotEmpty()) MessageType.IMAGE else MessageType.TEXT,
                images = userImages
            )
            
            repository.addMessage(userMessage)
            
            // 清空输入
            _uiState.update { 
                it.copy(
                    promptText = "",
                    selectedImages = emptyList(),
                    isGenerating = true
                ) 
            }
            
            // 滚动到底部
            _scrollToBottomEvent.emit(Unit)

            // 创建助手消息占位
            val assistantMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING
            )
            repository.addMessage(assistantMessage)

            // 调用API生成图片
            generationJob = launch {
                try {
                    val result = callGeminiApi(
                        profile = state.activeProfile,
                        model = state.selectedModel,
                        prompt = prompt,
                        images = userImages,
                        aspectRatio = state.selectedAspectRatio,
                        resolution = state.selectedResolution
                    )
                    
                    val updatedMessage = assistantMessage.copy(
                        messageContent = "",
                        images = listOf(
                            PaintImage(
                                id = generateId(),
                                base64Data = result,
                                mimeType = "image/png"
                            )
                        ),
                        status = MessageStatus.SUCCESS,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMessage(updatedMessage)
                    
                } catch (e: Exception) {
                    val errorMessage = assistantMessage.copy(
                        messageContent = e.message ?: "生成失败",
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMessage(errorMessage)
                } finally {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private fun stopGeneration() {
        generationJob?.cancel()
        _uiState.update { it.copy(isGenerating = false) }
    }

    private fun loadMoreMessages() {
        val state = _uiState.value
        val session = state.currentSession ?: return
        if (!state.hasMoreMessages || state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val offset = (state.currentPage + 1) * state.pageSize
            repository.getMessagesPaged(session.id, state.pageSize, offset).first().let { newMessages ->
                _uiState.update { 
                    it.copy(
                        currentPage = state.currentPage + 1,
                        hasMoreMessages = newMessages.size >= state.pageSize,
                        isLoading = false
                    ) 
                }
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    private fun updatePrompt(text: String) {
        _uiState.update { it.copy(promptText = text) }
    }

    private fun addImage(image: SelectedImage) {
        _uiState.update { 
            it.copy(selectedImages = it.selectedImages + image) 
        }
    }

    private fun removeImage(imageId: String) {
        _uiState.update { 
            it.copy(selectedImages = it.selectedImages.filter { img -> img.id != imageId }) 
        }
    }

    private fun clearImages() {
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
        }
    }

    private fun deleteApiProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteApiProfile(profileId)
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
        if (state.activeProfile == null) {
            _uiState.update { it.copy(error = "请先配置API") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val enhanced = callEnhanceApi(state.activeProfile, prompt)
                _uiState.update { it.copy(promptText = enhanced, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "优化失败: ${e.message}", isLoading = false) }
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

    // API调用方法 - 这些将在Android端实现
    private suspend fun callGeminiApi(
        profile: ApiProfile,
        model: PaintModel,
        prompt: String,
        images: List<PaintImage>,
        aspectRatio: AspectRatio,
        resolution: Resolution
    ): String {
        // 实际实现在Android端
        throw NotImplementedError("需要在Android端实现")
    }

    private suspend fun callEnhanceApi(profile: ApiProfile, prompt: String): String {
        // 实际实现在Android端
        throw NotImplementedError("需要在Android端实现")
    }
}
