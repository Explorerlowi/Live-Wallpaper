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
import com.example.livewallpaper.paint.service.ImageGenerationService
import com.example.livewallpaper.paint.service.GenerationTaskManager
import com.example.livewallpaper.paint.service.GenerationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
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

    // 使用全局任务管理器，生命周期独立于 ViewModel
    private val taskManager = GenerationTaskManager
    private var currentSessionId: String? = null
    
    // 消息监听的协程，切换会话时需要取消旧的监听
    private var messagesCollectJob: Job? = null
    private var promptDraftSaveJob: Job? = null

    /**
     * 将消息标记为正在生成，更新 UI 状态
     */
    private fun markGenerating(messageId: String, sessionId: String, startTime: Long) {
        // UI 状态从 taskManager 同步
        syncGeneratingState()
    }

    /**
     * 将消息标记为生成完成，更新 UI 状态
     */
    private fun unmarkGenerating(messageId: String, sessionId: String) {
        taskManager.unregister(messageId)
        syncGeneratingState()
    }

    /**
     * 从 GenerationTaskManager 同步生成状态到 UI
     */
    private fun syncGeneratingState() {
        val tasks = taskManager.activeTasks.value
        val sessionCounts = taskManager.getSessionCounts()
        val earliestTime = taskManager.getEarliestStartTime()
        val currentSid = _uiState.value.currentSession?.id
        _uiState.update { current ->
            current.copy(
                isGenerating = tasks.isNotEmpty(),
                generatingMessageIds = tasks.keys,
                generatingSessionCounts = sessionCounts,
                generatingSessionId = if (currentSid != null && sessionCounts.containsKey(currentSid)) currentSid else null,
                generationStartTime = earliestTime
            )
        }
    }

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
        
        // 从全局任务管理器恢复生成状态，并持续监听变化
        syncGeneratingState()
        viewModelScope.launch {
            taskManager.activeTasks.collect {
                syncGeneratingState()
            }
        }
        // 监听生成结果事件，转发为 toast
        viewModelScope.launch {
            taskManager.resultEvents.collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        _toastEvent.emit(
                            if (result.imageCount > 1) PaintToastMessage.GenerateMultipleSuccess(result.imageCount)
                            else PaintToastMessage.GenerateSuccess
                        )
                    }
                    is GenerationResult.Failed -> {
                        _toastEvent.emit(PaintToastMessage.GenerateFailed(result.error))
                    }
                    is GenerationResult.Cancelled -> {
                        // 用户主动取消，不需要额外 toast（stopGeneration 已发送 Stopped）
                    }
                }
            }
        }
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
            is PaintEvent.PinSession -> updateSessionPinned(event.sessionId, true)
            is PaintEvent.UnpinSession -> updateSessionPinned(event.sessionId, false)
            is PaintEvent.SendMessage -> sendMessage()
            is PaintEvent.StopGeneration -> stopGeneration()
            is PaintEvent.CancelGeneration -> Unit
            is PaintEvent.DismissGenerationTask -> Unit
            PaintEvent.ClearGenerationTaskHistory -> Unit
            is PaintEvent.LoadMoreMessages -> loadMoreMessages()
            is PaintEvent.DeleteMessage -> deleteMessage(event.messageId)
            is PaintEvent.DeleteMessageVersion -> deleteMessageVersion(event.versionGroup)
            is PaintEvent.EditUserMessage -> editUserMessage(event.messageId)
            is PaintEvent.UpdatePrompt -> updatePrompt(event.text)
            is PaintEvent.AddImage -> addImage(event.image)
            is PaintEvent.RemoveImage -> removeImage(event.imageId)
            is PaintEvent.ReorderImages -> reorderImages(event.images)
            is PaintEvent.ClearImages -> clearImages()
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
            is PaintEvent.ScrollToBottom -> scrollToBottom()
            is PaintEvent.ClearNewMessageCount -> clearNewMessageCount()
            is PaintEvent.ClearError -> clearError()
            is PaintEvent.RegenerateMessage -> regenerateMessage(event.messageId)
            is PaintEvent.SwitchMessageVersion -> switchMessageVersion(event.versionGroup, event.targetIndex)
            is PaintEvent.UpdateImageDimensions -> updateImageDimensions(
                event.messageId, event.imageId, event.width, event.height
            )
            is PaintEvent.ReplaceImagePath -> replaceImagePath(event.oldPath, event.newPath)
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
                resolution = _uiState.value.selectedResolution,
                gptImageSize = _uiState.value.selectedGptSize,
                gptImageQuality = _uiState.value.selectedGptQuality,
                gptOutputFormat = _uiState.value.selectedGptFormat
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
                        selectedGptSize = session.gptImageSize,
                        selectedGptQuality = session.gptImageQuality,
                        selectedGptFormat = session.gptOutputFormat,
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

    private fun updateSessionPinned(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.getSession(sessionId).first()?.let { session ->
                val updatedSession = session.copy(
                    isPinned = pinned,
                    pinnedAt = if (pinned) System.currentTimeMillis() else null
                )
                repository.updateSession(updatedSession)
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

            // 在 IO 线程获取图片尺寸，避免阻塞主线程
            val userImagesForMessage = withContext(Dispatchers.IO) {
                selectedImagesSnapshot.map { img ->
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
                versionIndex = 0,
                generationModel = state.selectedModel,
                generationAspectRatio = state.selectedAspectRatio,
                generationResolution = if (state.selectedModel.supportsResolution) state.selectedResolution else null,
                generationGptSize = if (state.selectedModel.isGpt) state.selectedGptSize else null,
                generationGptQuality = if (state.selectedModel.isGpt) state.selectedGptQuality else null,
                generationGptFormat = if (state.selectedModel.isGpt) state.selectedGptFormat else null
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
                    newMessageCount = 0
                )
            }
            
            markGenerating(assistantMessage.id, session.id, startTime)
            
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

            ImageGenerationService.start(appContext, generatingSessionId)

            val job = taskManager.generationScope.launch {
                var generationSuccess = false
                var failureMessage: String? = null
                try {
                    val userImagesForApi = loadReferenceImagesForApi(selectedImagesSnapshot)

                    val result = if (state.selectedModel.isGpt) {
                        repository.generateGptImage(
                            profile = profile,
                            prompt = prompt,
                            images = userImagesForApi,
                            size = state.selectedGptSize,
                            quality = state.selectedGptQuality,
                            outputFormat = state.selectedGptFormat,
                            sessionId = generatingSessionId,
                            messageId = generatingMessageId
                        )
                    } else {
                        repository.generateImage(
                            profile = profile,
                            model = state.selectedModel,
                            prompt = prompt,
                            images = userImagesForApi,
                            aspectRatio = state.selectedAspectRatio,
                            resolution = state.selectedResolution,
                            sessionId = generatingSessionId,
                            messageId = generatingMessageId
                        )
                    }
                    
                    result.onSuccess { imageFiles ->
                        val savedImages = imageFiles.map { file ->
                            PaintImage(
                                id = generateId(),
                                localPath = file.filePath,
                                mimeType = mimeTypeFromPath(file.filePath),
                                width = file.width,
                                height = file.height
                            )
                        }
                        
                        generationSuccess = true
                        val updatedMessage = assistantMessage.copy(
                            messageContent = "",
                            images = savedImages,
                            status = MessageStatus.SUCCESS,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(updatedMessage)
                        repository.getSession(generatingSessionId).first()?.let { sess ->
                            repository.updateSession(sess)
                        }
                        taskManager.emitResult(GenerationResult.Success(savedImages.size))
                    }.onError { error ->
                        failureMessage = error.message
                        val errorMessage = assistantMessage.copy(
                            messageContent = error.message ?: "生成失败",
                            status = MessageStatus.ERROR,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(errorMessage)
                        taskManager.emitResult(GenerationResult.Failed(error.message))
                    }
                    
                } catch (e: CancellationException) {
                    // 用户主动取消生成，更新消息状态为 CANCELLED
                    withContext(NonCancellable) {
                        val cancelledMessage = assistantMessage.copy(
                            messageContent = "",
                            status = MessageStatus.CANCELLED,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(cancelledMessage)
                        taskManager.emitResult(GenerationResult.Cancelled)
                    }
                } catch (e: Exception) {
                    failureMessage = e.message
                    val errorMessage = assistantMessage.copy(
                        messageContent = e.message ?: "生成失败",
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMessage(errorMessage)
                    taskManager.emitResult(GenerationResult.Failed(e.message))
                } finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main) {
                            unmarkGenerating(generatingMessageId, generatingSessionId)
                        }
                        // 所有生成任务都完成后才停止前台服务
                        if (taskManager.activeTasks.value.isEmpty()) {
                            if (failureMessage != null || generationSuccess) {
                                ImageGenerationService.stopWithResult(
                                    appContext, generationSuccess, failureMessage, generatingSessionId
                                )
                            } else {
                                ImageGenerationService.stop(appContext)
                            }
                        }
                    }
                }
            }
            taskManager.register(generatingMessageId, generatingSessionId, startTime, job)
            markGenerating(generatingMessageId, generatingSessionId, startTime)
        }
    }

    private fun loadReferenceImagesForApi(images: List<SelectedImage>): List<PaintImage> {
        if (images.isEmpty()) return emptyList()
        return images.mapNotNull { selected ->
            val bytes = try {
                val uriString = selected.uri
                val rawBytes = if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                    val filePath = if (uriString.startsWith("file://")) {
                        uriString.removePrefix("file://")
                    } else {
                        uriString
                    }
                    File(filePath).readBytes()
                } else {
                    val uri = Uri.parse(uriString)
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                // 如果原始数据超过 3MB，进行压缩
                compressImageIfNeeded(rawBytes, selected.mimeType)
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

    /**
     * 如果图片字节数超过 3MB，使用 Bitmap 解码后逐步降低质量压缩
     * 优先降低 JPEG 质量，若仍超限则缩小分辨率
     * @param rawBytes 原始图片字节数据
     * @param mimeType 图片 MIME 类型
     * @return 压缩后的字节数据（不超过 3MB），压缩失败则返回原始数据
     */
    private fun compressImageIfNeeded(rawBytes: ByteArray?, mimeType: String): ByteArray? {
        if (rawBytes == null) return null
        val maxSize = 3 * 1024 * 1024 // 3MB
        if (rawBytes.size <= maxSize) return rawBytes

        return try {
            // 先按原始尺寸解码
            var bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
            var scaleFactor = 1.0f

            // 最多尝试 5 轮缩放
            repeat(5) {
                // 从质量 90 开始逐步降低
                var quality = 90
                while (quality >= 30) {
                    val output = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
                    val compressed = output.toByteArray()
                    if (compressed.size <= maxSize) {
                        if (scaleFactor < 1.0f) bitmap.recycle()
                        return compressed
                    }
                    quality -= 10
                }
                // 质量压缩不够，缩小分辨率
                scaleFactor *= 0.7f
                val newWidth = (bitmap.width * 0.7f).toInt().coerceAtLeast(100)
                val newHeight = (bitmap.height * 0.7f).toInt().coerceAtLeast(100)
                val oldBitmap = bitmap
                bitmap = android.graphics.Bitmap.createScaledBitmap(oldBitmap, newWidth, newHeight, true)
                if (oldBitmap !== bitmap) oldBitmap.recycle()
            }

            // 兜底：最低质量 + 当前缩放后的尺寸
            val finalOutput = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 30, finalOutput)
            bitmap.recycle()
            finalOutput.toByteArray()
        } catch (_: Exception) {
            rawBytes
        }
    }

    private fun stopGeneration() {
        taskManager.cancelAll()
        ImageGenerationService.stop(appContext)
        syncGeneratingState()
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
                taskManager.cancel(messageId)
                syncGeneratingState()
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
        val state = _uiState.value
        val maxImages = state.selectedModel.maxImages
        if (state.selectedImages.size >= maxImages) {
            viewModelScope.launch { _toastEvent.emit(PaintToastMessage.ImageLimitExceeded(maxImages)) }
            return
        }
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

    private fun reorderImages(images: List<SelectedImage>) {
        val selectedIds = _uiState.value.selectedImages.map { it.id }.toSet()
        if (images.map { it.id }.toSet() != selectedIds) return
        _uiState.update { it.copy(selectedImages = images) }
        val key = currentDraftKey()
        val current = sessionDrafts[key] ?: loadDraft(key) ?: SessionDraft()
        saveDraft(key, current.copy(selectedImages = images.map { it.toSerializable() }))
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
                        mimeType = img.mimeType,
                        width = img.width,
                        height = img.height
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
        _uiState.update { state ->
            // 如果已选图片超过新模型限制，截断
            val trimmedImages = if (state.selectedImages.size > model.maxImages) {
                state.selectedImages.take(model.maxImages)
            } else {
                state.selectedImages
            }
            // 如果当前分辨率不在新模型支持范围内，重置为 1K
            val availableRes = Resolution.availableFor(model)
            val newResolution = if (state.selectedResolution in availableRes) {
                state.selectedResolution
            } else {
                Resolution.RES_1K
            }
            // 如果当前宽高比不在新模型支持范围内，重置为 1:1
            val availableRatios = AspectRatio.availableFor(model)
            val newRatio = if (state.selectedAspectRatio in availableRatios) {
                state.selectedAspectRatio
            } else {
                AspectRatio.RATIO_1_1
            }
            state.copy(
                selectedModel = model,
                selectedImages = trimmedImages,
                selectedResolution = newResolution,
                selectedAspectRatio = newRatio
            )
        }
        // 读取 update 之后的最新状态，确保 aspectRatio/resolution 与 UI 一致
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        model = updatedState.selectedModel,
                        aspectRatio = updatedState.selectedAspectRatio,
                        resolution = updatedState.selectedResolution
                    )
                )
            }
        }
    }

    private fun selectAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        model = updatedState.selectedModel,
                        aspectRatio = updatedState.selectedAspectRatio,
                        resolution = updatedState.selectedResolution
                    )
                )
            }
        }
    }

    private fun selectResolution(resolution: Resolution) {
        _uiState.update { it.copy(selectedResolution = resolution) }
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        model = updatedState.selectedModel,
                        aspectRatio = updatedState.selectedAspectRatio,
                        resolution = updatedState.selectedResolution
                    )
                )
            }
        }
    }

    private fun selectGptSize(size: GptImageSize) {
        _uiState.update { it.copy(selectedGptSize = size) }
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        gptImageSize = updatedState.selectedGptSize,
                        gptImageQuality = updatedState.selectedGptQuality,
                        gptOutputFormat = updatedState.selectedGptFormat
                    )
                )
            }
        }
    }

    private fun selectGptQuality(quality: GptImageQuality) {
        _uiState.update { it.copy(selectedGptQuality = quality) }
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        gptImageSize = updatedState.selectedGptSize,
                        gptImageQuality = updatedState.selectedGptQuality,
                        gptOutputFormat = updatedState.selectedGptFormat
                    )
                )
            }
        }
    }

    private fun selectGptFormat(format: GptOutputFormat) {
        _uiState.update { it.copy(selectedGptFormat = format) }
        val updatedState = _uiState.value
        updatedState.currentSession?.let { session ->
            viewModelScope.launch {
                repository.updateSession(
                    session.copy(
                        gptImageSize = updatedState.selectedGptSize,
                        gptImageQuality = updatedState.selectedGptQuality,
                        gptOutputFormat = updatedState.selectedGptFormat
                    )
                )
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

        // 直接从 uiState 获取当前选择的参数（同步、即时），避免数据库异步更新的竞态问题
        val currentModel = state.selectedModel
        val currentAspectRatio = state.selectedAspectRatio
        val currentResolution = state.selectedResolution
        val currentGptSize = state.selectedGptSize
        val currentGptQuality = state.selectedGptQuality
        val currentGptFormat = state.selectedGptFormat

        viewModelScope.launch {
            // 获取当前消息
            val currentMessage = repository.getMessage(messageId) ?: return@launch
            
            // 只有AI消息才能重新生成
            if (currentMessage.senderIdentity != SenderIdentity.ASSISTANT) return@launch
            
            val sessionId = currentMessage.sessionId
            
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
            
            // 创建新的AI消息（占位符）- 使用当前 UI 选择的参数
            val newAssistantMessage = PaintMessage(
                id = generateId(),
                sessionId = sessionId,
                senderIdentity = SenderIdentity.ASSISTANT,
                messageContent = "",
                messageType = MessageType.IMAGE,
                status = MessageStatus.GENERATING,
                parentUserMessageId = userMessage.id,
                versionGroup = versionGroup,
                versionIndex = newVersionIndex,
                generationModel = currentModel,
                generationAspectRatio = currentAspectRatio,
                generationResolution = if (currentModel.supportsResolution) currentResolution else null,
                generationGptSize = if (currentModel.isGpt) currentGptSize else null,
                generationGptQuality = if (currentModel.isGpt) currentGptQuality else null,
                generationGptFormat = if (currentModel.isGpt) currentGptFormat else null
            )
            
            repository.addMessage(newAssistantMessage)
            
            // 更新当前显示版本为新版本（使用列表位置，即版本数量 - 1 + 1 = 版本数量，因为新消息还没加入列表）
            // 新消息添加后，它在列表中的位置就是 existingVersions（0-based）
            _uiState.update { 
                it.copy(
                    activeVersions = it.activeVersions + (versionGroup to existingVersions)
                )
            }
            
            val regenStartTime = System.currentTimeMillis()
            markGenerating(newAssistantMessage.id, sessionId, regenStartTime)
            
            // 加载用户消息中的参考图片
            val userImagesForApi = userMessage.images
                .filter { it.isReference && it.localPath != null }
                .mapNotNull { img ->
                    val path = img.localPath ?: return@mapNotNull null
                    val bytes = try {
                        val rawBytes = if (path.startsWith("/") || path.startsWith("file://")) {
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
                        // 与首次生成保持一致：原始数据超过 3MB 时压缩，避免重新生成时 OOM
                        compressImageIfNeeded(rawBytes, img.mimeType)
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

            ImageGenerationService.start(appContext, sessionId)
            
            val regenJob = taskManager.generationScope.launch {
                var generationSuccess = false
                var failureMessage: String? = null
                try {
                    val result = if (currentModel.isGpt) {
                        repository.generateGptImage(
                            profile = profile,
                            prompt = userMessage.messageContent,
                            images = userImagesForApi,
                            size = currentGptSize,
                            quality = currentGptQuality,
                            outputFormat = currentGptFormat,
                            sessionId = sessionId,
                            messageId = generatingMessageId
                        )
                    } else {
                        repository.generateImage(
                            profile = profile,
                            model = currentModel,
                            prompt = userMessage.messageContent,
                            images = userImagesForApi,
                            aspectRatio = currentAspectRatio,
                            resolution = currentResolution,
                            sessionId = sessionId,
                            messageId = generatingMessageId
                        )
                    }
                    
                    result.onSuccess { imageFiles ->
                        val savedImages = imageFiles.map { file ->
                            PaintImage(
                                id = generateId(),
                                localPath = file.filePath,
                                mimeType = mimeTypeFromPath(file.filePath),
                                width = file.width,
                                height = file.height
                            )
                        }
                        
                        generationSuccess = true
                        val updatedMessage = newAssistantMessage.copy(
                            messageContent = "",
                            images = savedImages,
                            status = MessageStatus.SUCCESS,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(updatedMessage)
                        repository.getSession(sessionId).first()?.let { sess ->
                            repository.updateSession(sess)
                        }
                        taskManager.emitResult(GenerationResult.Success(savedImages.size))
                    }.onError { error ->
                        failureMessage = error.message
                        val errorMessage = newAssistantMessage.copy(
                            messageContent = error.message ?: "生成失败",
                            status = MessageStatus.ERROR,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(errorMessage)
                        taskManager.emitResult(GenerationResult.Failed(error.message))
                    }
                } catch (e: CancellationException) {
                    // 用户主动取消生成，更新消息状态为 CANCELLED
                    withContext(NonCancellable) {
                        val cancelledMessage = newAssistantMessage.copy(
                            messageContent = "",
                            status = MessageStatus.CANCELLED,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateMessage(cancelledMessage)
                        taskManager.emitResult(GenerationResult.Cancelled)
                    }
                } catch (e: Exception) {
                    failureMessage = e.message
                    val errorMessage = newAssistantMessage.copy(
                        messageContent = e.message ?: "生成失败",
                        status = MessageStatus.ERROR,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMessage(errorMessage)
                    taskManager.emitResult(GenerationResult.Failed(e.message))
                } finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main) {
                            unmarkGenerating(generatingMessageId, sessionId)
                        }
                        if (taskManager.activeTasks.value.isEmpty()) {
                            if (failureMessage != null || generationSuccess) {
                                ImageGenerationService.stopWithResult(
                                    appContext, generationSuccess, failureMessage, sessionId
                                )
                            } else {
                                ImageGenerationService.stop(appContext)
                            }
                        }
                    }
                }
            }
            taskManager.register(generatingMessageId, sessionId, regenStartTime, regenJob)
            markGenerating(generatingMessageId, sessionId, regenStartTime)
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
     * 根据文件路径推断 MIME 类型
     */
    private fun mimeTypeFromPath(path: String): String {
        return when {
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/png"
        }
    }
    
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
    
    /**
     * 替换图片的路径
     * 用于图片编辑后用新缓存文件替换原引用，同时支持消息图片和输入框图片
     */
    private fun replaceImagePath(oldPath: String, newPath: String) {
        val selectedImg = _uiState.value.selectedImages.find { it.uri == oldPath }
        if (selectedImg != null) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(newPath, options)
            val updated = _uiState.value.selectedImages.map { img ->
                if (img.uri == oldPath) img.copy(
                    uri = newPath,
                    width = if (options.outWidth > 0) options.outWidth else img.width,
                    height = if (options.outHeight > 0) options.outHeight else img.height
                ) else img
            }
            _uiState.update { it.copy(selectedImages = updated) }
            return
        }

        viewModelScope.launch {
            val message = _uiState.value.messages.find { msg ->
                msg.images.any { it.localPath == oldPath }
            } ?: return@launch
            
            val updatedImages = message.images.map { img ->
                if (img.localPath == oldPath) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(newPath, options)
                    img.copy(
                        localPath = newPath,
                        width = if (options.outWidth > 0) options.outWidth else img.width,
                        height = if (options.outHeight > 0) options.outHeight else img.height
                    )
                } else {
                    img
                }
            }
            val updatedMessage = message.copy(images = updatedImages)
            repository.updateMessage(updatedMessage)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 不取消 taskManager 中的任务，让生成请求继续完成
        // ViewModel 重建时会从 taskManager 恢复状态
    }
}
