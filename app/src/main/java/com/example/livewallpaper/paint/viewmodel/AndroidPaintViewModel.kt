package com.example.livewallpaper.paint.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    /**
     * 会话草稿数据类（可序列化）
     */
    @Serializable
    private data class SessionDraft(
        val promptText: String = "",
        val selectedImages: List<SerializableSelectedImage> = emptyList()
    )
    
    /**
     * 可序列化的选中图片数据
     */
    @Serializable
    private data class SerializableSelectedImage(
        val id: String,
        val uri: String,
        val mimeType: String,
        val width: Int = 0,
        val height: Int = 0
    )
    
    private fun SelectedImage.toSerializable() = SerializableSelectedImage(id, uri, mimeType, width, height)
    private fun SerializableSelectedImage.toSelectedImage() = SelectedImage(id, uri, mimeType, width, height)

    // 草稿存储的 SharedPreferences
    private val draftPrefs = appContext.getSharedPreferences("paint_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    // 内存中的草稿缓存
    private val sessionDrafts = mutableMapOf<String, SessionDraft>()
    private val tempDraftKey = "__temp_draft__"  // 临时缓存区的 key

    // 使用独立的协程作用域，确保请求不会因为切换会话而中断
    private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generationJob: Job? = null
    private var currentSessionId: String? = null
    
    // 消息监听的协程，切换会话时需要取消旧的监听
    private var messagesCollectJob: Job? = null
    private var promptDraftSaveJob: Job? = null

    private val _scrollToBottomEvent = MutableSharedFlow<Boolean>()
    val scrollToBottomEvent: SharedFlow<Boolean> = _scrollToBottomEvent.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<PaintToastMessage>()
    val toastEvent: SharedFlow<PaintToastMessage> = _toastEvent.asSharedFlow()

    init {
        // 同步获取 API 配置初始值，避免界面闪烁
        val initialProfiles = repository.getApiProfilesSync()
        val initialActiveProfile = repository.getActiveProfileSync()
        
        // 恢复临时缓存区的草稿（初始进入未选择会话时）
        val tempDraft = loadDraft(tempDraftKey)
        
        _uiState.update { 
            it.copy(
                apiProfiles = initialProfiles,
                activeProfile = initialActiveProfile,
                isApiProfileLoaded = true,
                promptText = tempDraft?.promptText ?: "",
                selectedImages = tempDraft?.selectedImages?.map { img -> img.toSelectedImage() } ?: emptyList()
            )
        }
        
        // 继续监听后续变化
        loadApiProfiles()
        loadSessions()
    }
    
    /**
     * 从 SharedPreferences 加载草稿
     */
    private fun loadDraft(key: String): SessionDraft? {
        return try {
            val jsonStr = draftPrefs.getString(key, null) ?: return null
            json.decodeFromString<SessionDraft>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 保存草稿到 SharedPreferences
     */
    private fun saveDraft(key: String, draft: SessionDraft) {
        try {
            val jsonStr = json.encodeToString(draft)
            draftPrefs.edit().putString(key, jsonStr).apply()
            sessionDrafts[key] = draft
        } catch (e: Exception) {
            // 忽略序列化错误
        }
    }
    
    /**
     * 删除草稿
     */
    private fun removeDraft(key: String) {
        draftPrefs.edit().remove(key).apply()
        sessionDrafts.remove(key)
    }

    private fun loadApiProfiles() {
        viewModelScope.launch {
            combine(
                repository.getApiProfiles(),
                repository.getActiveProfile()
            ) { profiles, active ->
                Pair(profiles, active)
            }.collect { (profiles, active) ->
                _uiState.update { 
                    it.copy(
                        apiProfiles = profiles, 
                        activeProfile = active
                    ) 
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
        // 取消旧的消息监听，避免切换会话后旧会话的消息更新影响当前UI
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            repository.getMessages(sessionId).collect { messages ->
                // 再次检查是否仍是当前会话，防止竞态条件
                if (currentSessionId != sessionId) return@collect
                
                val sortedMessages = messages.sortedBy { it.createdAt }
                val currentState = _uiState.value
                
                val newCount = if (!currentState.isAtBottom && sortedMessages.size > currentState.messages.size) {
                    currentState.newMessageCount + (sortedMessages.size - currentState.messages.size)
                } else {
                    0
                }
                
                // 计算每个版本组的最新版本位置（列表长度 - 1），默认显示最新版本
                val latestVersionPositions = sortedMessages
                    .filter { it.versionGroup != null }
                    .groupBy { it.versionGroup!! }
                    .mapValues { (_, msgs) -> msgs.size - 1 }
                
                // 合并现有的 activeVersions，保留用户手动切换的版本
                // 同时确保索引不超出范围
                val mergedVersions = latestVersionPositions.mapValues { (group, latestPos) ->
                    val existingPos = currentState.activeVersions[group]
                    if (existingPos != null && existingPos <= latestPos) {
                        existingPos
                    } else {
                        latestPos
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        messages = sortedMessages,
                        newMessageCount = newCount,
                        activeVersions = mergedVersions
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
            is PaintEvent.RenameSession -> renameSession(event.sessionId, event.newTitle)
            is PaintEvent.SendMessage -> sendMessage()
            is PaintEvent.StopGeneration -> stopGeneration()
            is PaintEvent.LoadMoreMessages -> loadMoreMessages()
            is PaintEvent.DeleteMessage -> deleteMessage(event.messageId)
            is PaintEvent.DeleteMessageVersion -> deleteMessageVersion(event.versionGroup)
            is PaintEvent.EditUserMessage -> editUserMessage(event.messageId)
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
            is PaintEvent.RegenerateMessage -> regenerateMessage(event.messageId)
            is PaintEvent.SwitchMessageVersion -> switchMessageVersion(event.versionGroup, event.targetIndex)
            is PaintEvent.UpdateImageDimensions -> updateImageDimensions(
                event.messageId, event.imageId, event.width, event.height
            )
        }
    }

    private fun currentDraftKey(): String =
        _uiState.value.currentSession?.id ?: tempDraftKey

    private fun snapshotCurrentDraft(): SessionDraft =
        SessionDraft(
            promptText = _uiState.value.promptText,
            selectedImages = _uiState.value.selectedImages.map { it.toSerializable() }
        )

    private fun restoreDraft(key: String): SessionDraft {
        val cached = sessionDrafts[key]
        if (cached != null) return cached
        val loaded = loadDraft(key)
        return if (loaded != null) {
            sessionDrafts[key] = loaded
            loaded
        } else {
            SessionDraft()
        }
    }

    private fun schedulePromptDraftSave(key: String, draft: SessionDraft) {
        sessionDrafts[key] = draft
        promptDraftSaveJob?.cancel()
        promptDraftSaveJob = viewModelScope.launch(Dispatchers.Default) {
            delay(350)
            val jsonStr = try {
                json.encodeToString(draft)
            } catch (_: Exception) {
                null
            }
            if (jsonStr != null) {
                withContext(Dispatchers.IO) {
                    draftPrefs.edit().putString(key, jsonStr).apply()
                }
            }
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
            if (_uiState.value.currentSession == null) {
                // 将临时缓存区的内容转移到新会话
                val tempDraft = restoreDraft(tempDraftKey)
                saveDraft(session.id, tempDraft)
                removeDraft(tempDraftKey)
            }
            selectSession(session.id)
        }
    }

    private fun selectSession(sessionId: String) {
        // 如果选择的是当前会话，不需要重新加载
        if (_uiState.value.currentSession?.id == sessionId) return
        
        viewModelScope.launch {
            // 保存当前草稿
            saveDraft(currentDraftKey(), snapshotCurrentDraft())
            
            repository.getSession(sessionId).first()?.let { session ->
                val draft = restoreDraft(session.id)
                // 先清空消息并显示加载状态
                _uiState.update { 
                    it.copy(
                        currentSession = session,
                        selectedModel = session.model,
                        selectedAspectRatio = session.aspectRatio,
                        selectedResolution = session.resolution,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages.map { img -> img.toSelectedImage() },
                        currentPage = 0,
                        hasMoreMessages = true,
                        newMessageCount = 0,
                        isAtBottom = true,
                        isLoading = true
                    ) 
                }
                // 短暂延迟，让空屏过渡更自然
                delay(200)
                loadMessages(sessionId)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            removeDraft(sessionId)
            if (_uiState.value.currentSession?.id == sessionId) {
                val draft = restoreDraft(tempDraftKey)
                _uiState.update { 
                    it.copy(
                        currentSession = null,
                        messages = emptyList(),
                        promptText = draft.promptText,
                        selectedImages = draft.selectedImages.map { img -> img.toSelectedImage() }
                    ) 
                }
                currentSessionId = null
            }
        }
    }

    private fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.getSession(sessionId).first()?.let { session ->
                val updatedSession = session.copy(title = newTitle)
                repository.updateSession(updatedSession)
                // 如果是当前会话，同步更新 UI 状态
                if (_uiState.value.currentSession?.id == sessionId) {
                    _uiState.update { it.copy(currentSession = updatedSession) }
                }
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
                // 获取图片尺寸
                val (width, height) = getImageDimensions(img.uri)
                PaintImage(
                    id = generateId(),
                    mimeType = img.mimeType,
                    localPath = img.uri,
                    width = width,
                    height = height,
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
            
            // 为AI消息生成版本组ID
            val versionGroupId = generateId()
            val assistantMessage = PaintMessage(
                id = generateId(),
                sessionId = session.id,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroupId,
                versionIndex = 0
            )
            
            val startTime = System.currentTimeMillis()
            // 发送消息后清空该会话的草稿
            saveDraft(session.id, SessionDraft())
            removeDraft(tempDraftKey)
            
            // 先只显示用户消息
            _uiState.update { current ->
                val nextMessages = (current.messages + userMessage).sortedBy { it.createdAt }
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
            
            _scrollToBottomEvent.emit(true) // 发送新消息时使用动画
            repository.addMessage(userMessage)
            
            // 延迟后添加 AI 消息，提升体验
            delay(500)
            
            _uiState.update { current ->
                val nextMessages = (current.messages + assistantMessage).sortedBy { it.createdAt }
                current.copy(messages = nextMessages)
            }
            
            _scrollToBottomEvent.emit(true) // 发送新消息时使用动画
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
                        val imageInfo = saveGeneratedImage(
                            sessionId = generatingSessionId,
                            messageId = generatingMessageId,
                            base64Data = base64Data
                        )
                        val updatedMessage = assistantMessage.copy(
                            messageContent = "",
                            images = listOf(
                                PaintImage(
                                    id = generateId(),
                                    localPath = imageInfo?.first,
                                    mimeType = "image/png",
                                    width = imageInfo?.second ?: 0,
                                    height = imageInfo?.third ?: 0
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

    /**
     * 保存生成的图片并返回路径和尺寸信息
     * @return Triple<路径, 宽度, 高度>，失败返回 null
     */
    private fun saveGeneratedImage(
        sessionId: String,
        messageId: String,
        base64Data: String
    ): Triple<String, Int, Int>? {
        val bytes = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return null
        }

        // 解码获取图片尺寸
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight

        val dir = File(appContext.filesDir, "aipaint/$sessionId").apply { mkdirs() }
        val file = File(dir, "$messageId.png")
        return try {
            file.writeBytes(bytes)
            Triple(file.absolutePath, width, height)
        } catch (_: IOException) {
            null
        }
    }

    private fun loadReferenceImagesForApi(images: List<SelectedImage>): List<PaintImage> {
        if (images.isEmpty()) return emptyList()
        return images.mapNotNull { selected ->
            val bytes = try {
                val uriString = selected.uri
                if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                    // 本地文件路径
                    val filePath = if (uriString.startsWith("file://")) {
                        uriString.removePrefix("file://")
                    } else {
                        uriString
                    }
                    File(filePath).readBytes()
                } else {
                    // content:// URI
                    val uri = Uri.parse(uriString)
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
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
            val state = _uiState.value
            val message = state.messages.find { it.id == messageId }
            val versionGroup = message?.versionGroup
            
            // 如果删除的是正在生成的消息，先取消生成任务
            if (message?.status == MessageStatus.GENERATING) {
                generationJob?.cancel()
                _uiState.update { 
                    it.copy(
                        isGenerating = false,
                        generatingSessionId = null,
                        generationStartTime = 0L
                    ) 
                }
            }
            
            // 如果是有版本组的消息，删除后需要切换到其他版本
            if (versionGroup != null) {
                val versionsInGroup = state.messages
                    .filter { it.versionGroup == versionGroup }
                    .sortedBy { it.versionIndex }
                
                if (versionsInGroup.size > 1) {
                    // 找到当前消息在版本列表中的位置
                    val currentPosition = versionsInGroup.indexOfFirst { it.id == messageId }
                    val activeIndex = state.activeVersions[versionGroup] ?: currentPosition
                    
                    // 计算删除后应该显示的版本位置
                    val newPosition = when {
                        currentPosition < activeIndex -> activeIndex - 1  // 删除的在当前显示之前，索引减1
                        currentPosition == activeIndex && currentPosition > 0 -> currentPosition - 1  // 删除当前显示的，切换到上一个
                        currentPosition == activeIndex -> 0  // 删除的是第一个且是当前显示的
                        else -> activeIndex  // 删除的在当前显示之后，索引不变
                    }
                    _uiState.update { it.copy(activeVersions = it.activeVersions + (versionGroup to newPosition)) }
                } else {
                    // 只剩一个版本，删除后清理 activeVersions
                    _uiState.update { it.copy(activeVersions = it.activeVersions - versionGroup) }
                }
            }
            
            repository.deleteMessage(messageId)
            _toastEvent.emit(PaintToastMessage.Deleted)
        }
    }
    
    /**
     * 删除整个版本组的所有消息
     */
    private fun deleteMessageVersion(versionGroup: String) {
        viewModelScope.launch {
            val messages = _uiState.value.messages.filter { it.versionGroup == versionGroup }
            messages.forEach { message ->
                repository.deleteMessage(message.id)
            }
            // 清理 activeVersions 中的记录
            _uiState.update { state ->
                state.copy(activeVersions = state.activeVersions - versionGroup)
            }
            _toastEvent.emit(PaintToastMessage.Deleted)
        }
    }

    private fun updatePrompt(text: String) {
        _uiState.update { it.copy(promptText = text) }
        val key = currentDraftKey()
        val current = restoreDraft(key)
        schedulePromptDraftSave(key, current.copy(promptText = text))
    }

    private fun addImage(image: SelectedImage) {
        _uiState.update { it.copy(selectedImages = it.selectedImages + image) }
        // 保存草稿
        val key = currentDraftKey()
        val current = sessionDrafts[key] ?: loadDraft(key) ?: SessionDraft()
        saveDraft(key, current.copy(selectedImages = current.selectedImages + image.toSerializable()))
    }

    private fun removeImage(imageId: String) {
        _uiState.update { 
            it.copy(selectedImages = it.selectedImages.filter { img -> img.id != imageId }) 
        }
        // 保存草稿
        val key = currentDraftKey()
        val current = sessionDrafts[key] ?: loadDraft(key) ?: SessionDraft()
        saveDraft(key, current.copy(selectedImages = current.selectedImages.filter { img -> img.id != imageId }))
    }

    private fun clearImages() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
        // 保存草稿
        val key = currentDraftKey()
        val current = sessionDrafts[key] ?: loadDraft(key) ?: SessionDraft()
        saveDraft(key, current.copy(selectedImages = emptyList()))
    }

    /**
     * 编辑用户消息
     * 将消息内容和图片回填到输入框，用户可以修改后作为新消息发送
     */
    private fun editUserMessage(messageId: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId } ?: return@launch
            
            // 只能编辑用户消息
            if (message.senderIdentity != SenderIdentity.USER) return@launch
            
            // 将消息内容回填到输入框
            val promptText = message.messageContent
            
            // 将消息中的参考图片转换为 SelectedImage
            val selectedImages = message.images
                .filter { it.isReference && it.localPath != null }
                .map { img ->
                    SelectedImage(
                        id = generateId(),
                        uri = img.localPath!!,
                        mimeType = img.mimeType
                    )
                }
            
            // 更新草稿和 UI 状态
            val key = currentDraftKey()
            saveDraft(key, SessionDraft(
                promptText = promptText, 
                selectedImages = selectedImages.map { it.toSerializable() }
            ))
            _uiState.update { 
                it.copy(
                    promptText = promptText,
                    selectedImages = selectedImages
                ) 
            }
        }
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
            _scrollToBottomEvent.emit(false) // 用户点击按钮时瞬间到达
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
     * 重新生成消息
     * 基于关联的用户消息重新生成AI回复，创建新版本
     */
    private fun regenerateMessage(messageId: String) {
        val state = _uiState.value
        val profile = state.activeProfile
        if (profile == null) {
            viewModelScope.launch { _toastEvent.emit(PaintToastMessage.PleaseConfigApi) }
            return
        }
        
        // 如果正在生成中，不允许重新生成
        if (state.isGenerating) {
            viewModelScope.launch { _toastEvent.emit(PaintToastMessage.GeneratingInProgress) }
            return
        }

        viewModelScope.launch {
            // 获取当前消息
            val currentMessage = repository.getMessage(messageId) ?: return@launch
            
            // 只有AI消息才能重新生成
            if (currentMessage.senderIdentity != SenderIdentity.ASSISTANT) return@launch
            
            val sessionId = currentMessage.sessionId
            val session = repository.getSession(sessionId).first() ?: return@launch
            
            // 找到关联的用户消息
            val userMessage = findParentUserMessage(currentMessage)
            if (userMessage == null) {
                _toastEvent.emit(PaintToastMessage.CannotRegenerate)
                return@launch
            }
            
            // 计算新版本索引
            val versionGroup = currentMessage.versionGroup ?: generateId()
            val existingVersions = if (currentMessage.versionGroup != null) {
                repository.getVersionCount(sessionId, versionGroup)
            } else {
                // 旧消息没有版本组，需要先为其创建版本组
                val updatedOldMessage = currentMessage.copy(
                    versionGroup = versionGroup,
                    versionIndex = 0,
                    parentUserMessageId = userMessage.id
                )
                repository.updateMessage(updatedOldMessage)
                1
            }
            val newVersionIndex = existingVersions
            
            // 创建新的AI消息（占位符）
            val newAssistantMessage = PaintMessage(
                id = generateId(),
                sessionId = sessionId,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroup,
                versionIndex = newVersionIndex
            )
            
            repository.addMessage(newAssistantMessage)
            
            // 更新当前显示版本为新版本（使用列表位置，即版本数量 - 1 + 1 = 版本数量，因为新消息还没加入列表）
            // 新消息添加后，它在列表中的位置就是 existingVersions（0-based）
            _uiState.update { 
                it.copy(
                    activeVersions = it.activeVersions + (versionGroup to existingVersions),
                    isGenerating = true,
                    generatingSessionId = sessionId,
                    generationStartTime = System.currentTimeMillis()
                )
            }
            
            // 加载用户消息中的参考图片
            val userImagesForApi = userMessage.images
                .filter { it.isReference && it.localPath != null }
                .mapNotNull { img ->
                    val path = img.localPath ?: return@mapNotNull null
                    val bytes = try {
                        if (path.startsWith("/") || path.startsWith("file://")) {
                            // 本地文件路径
                            val filePath = if (path.startsWith("file://")) {
                                path.removePrefix("file://")
                            } else {
                                path
                            }
                            File(filePath).readBytes()
                        } else {
                            // content:// URI
                            val uri = Uri.parse(path)
                            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    PaintImage(
                        id = generateId(),
                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        mimeType = img.mimeType,
                        isReference = true
                    )
                }
            
            val generatingMessageId = newAssistantMessage.id
            
            // 使用独立的协程作用域进行生成
            generationJob = generationScope.launch {
                try {
                    val result = repository.generateImage(
                        profile = profile,
                        model = session.model,
                        prompt = userMessage.messageContent,
                        images = userImagesForApi,
                        aspectRatio = session.aspectRatio,
                        resolution = session.resolution
                    )
                    
                    result.onSuccess { base64Data ->
                        val imageInfo = saveGeneratedImage(
                            sessionId = sessionId,
                            messageId = generatingMessageId,
                            base64Data = base64Data
                        )
                        val updatedMessage = newAssistantMessage.copy(
                            messageContent = "",
                            images = listOf(
                                PaintImage(
                                    id = generateId(),
                                    localPath = imageInfo?.first,
                                    mimeType = "image/png",
                                    width = imageInfo?.second ?: 0,
                                    height = imageInfo?.third ?: 0
                                )
                            ),
                            status = MessageStatus.SUCCESS,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(updatedMessage)
                        repository.getSession(sessionId).first()?.let { sess ->
                            repository.updateSession(sess)
                        }
                        withContext(Dispatchers.Main) {
                            _toastEvent.emit(PaintToastMessage.GenerateSuccess)
                        }
                    }.onError { error ->
                        val errorMessage = newAssistantMessage.copy(
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
                    throw e
                } catch (e: Exception) {
                    val errorMessage = newAssistantMessage.copy(
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

    /**
     * 查找AI消息关联的用户消息
     */
    private suspend fun findParentUserMessage(aiMessage: PaintMessage): PaintMessage? {
        // 如果有明确的父消息ID，直接查找
        aiMessage.parentUserMessageId?.let { parentId ->
            return repository.getMessage(parentId)
        }
        
        // 否则，查找该AI消息之前最近的用户消息
        val allMessages = _uiState.value.messages.sortedBy { it.createdAt }
        val aiIndex = allMessages.indexOfFirst { it.id == aiMessage.id }
        if (aiIndex <= 0) return null
        
        // 向前查找最近的用户消息
        for (i in (aiIndex - 1) downTo 0) {
            val msg = allMessages[i]
            if (msg.senderIdentity == SenderIdentity.USER) {
                return msg
            }
        }
        return null
    }

    /**
     * 切换消息版本
     */
    private fun switchMessageVersion(versionGroup: String, targetIndex: Int) {
        _uiState.update { 
            it.copy(activeVersions = it.activeVersions + (versionGroup to targetIndex))
        }
    }

    private fun generateId(): String = 
        "${System.currentTimeMillis()}-${Random.nextInt(10000, 99999)}"
    
    /**
     * 获取图片尺寸
     * 使用 BitmapFactory.Options 仅解码边界信息，不加载完整图片
     */
    private fun getImageDimensions(uriString: String): Pair<Int, Int> {
        return try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    /**
     * 更新消息中图片的尺寸信息
     * 用于回填旧数据中缺失的宽高信息
     */
    private fun updateImageDimensions(messageId: String, imageId: String, width: Int, height: Int) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId } ?: return@launch
            val updatedImages = message.images.map { img ->
                if (img.id == imageId && img.width == 0 && img.height == 0) {
                    img.copy(width = width, height = height)
                } else {
                    img
                }
            }
            if (updatedImages != message.images) {
                val updatedMessage = message.copy(images = updatedImages)
                repository.updateMessage(updatedMessage)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 注意：这里不取消 generationScope，让请求继续完成
        // 如果需要在 ViewModel 销毁时取消请求，可以取消注释下面的代码
        // generationScope.cancel()
    }
}
