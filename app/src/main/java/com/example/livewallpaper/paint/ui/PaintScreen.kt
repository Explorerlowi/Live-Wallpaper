package com.example.livewallpaper.paint.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import com.example.livewallpaper.gallery.data.MediaStoreRepository
import com.example.livewallpaper.gallery.ui.GalleryScreen
import com.example.livewallpaper.gallery.viewmodel.GalleryViewModel
import com.example.livewallpaper.paint.viewmodel.AndroidPaintViewModel
import com.example.livewallpaper.paint.viewmodel.PaintToastMessage
import com.example.livewallpaper.paint.ui.split.ImageSplitActivity
import com.example.livewallpaper.ui.components.AppDropdownMenu
import com.example.livewallpaper.ui.components.AppMenuItem
import com.example.livewallpaper.ui.components.ConfirmDialog
import com.example.livewallpaper.ui.components.ImagePreviewConfig
import com.example.livewallpaper.ui.components.ImagePreviewDialog
import com.example.livewallpaper.ui.components.ImageSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * 图片缓存 - 避免重复解码 Base64
 * 使用基于内存大小的 LruCache，限制为 64MB
 */
private object ImageCache {
    // 最大缓存 64MB
    private const val MAX_CACHE_SIZE = 64 * 1024 * 1024
    
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }
    }
    
    fun get(imageId: String): Bitmap? = cache.get(imageId)
    
    fun put(imageId: String, bitmap: Bitmap) {
        cache.put(imageId, bitmap)
    }
    
    fun clear() {
        cache.evictAll()
    }
}

/**
 * 异步解码 Base64 图片
 * 使用采样率降低内存占用，避免 OOM
 */
@Composable
private fun rememberDecodedBitmap(imageId: String, base64: String): Bitmap? {
    var bitmap by remember(imageId) { mutableStateOf(ImageCache.get(imageId)) }
    
    LaunchedEffect(imageId) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                try {
                    // 使用流式解码避免一次性加载整个 byte 数组
                    val inputStream = java.io.ByteArrayInputStream(base64.toByteArray(Charsets.US_ASCII))
                    val base64Stream = android.util.Base64InputStream(inputStream, Base64.DEFAULT)
                    
                    // 先获取图片尺寸
                    val tempBytes = base64Stream.readBytes()
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, options)
                    
                    // 计算采样率，目标最大边 2048px
                    val maxDimension = maxOf(options.outWidth, options.outHeight)
                    val targetSize = 2048
                    var sampleSize = 1
                    while (maxDimension / sampleSize > targetSize) {
                        sampleSize *= 2
                    }
                    
                    // 使用采样率解码
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565 // 使用 RGB_565 减少内存
                    }
                    BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, decodeOptions)?.also {
                        ImageCache.put(imageId, it)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintScreen(
    viewModel: AndroidPaintViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    
    // 使用 DrawerState 控制抽屉
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // 监听抽屉状态变化，开始打开时立即清除焦点（收起键盘）
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open || drawerState.currentValue == DrawerValue.Open) {
            focusManager.clearFocus()
        }
    }
    
    // 处理返回逻辑：如果抽屉打开则关闭抽屉，否则关闭界面
    val handleBack: () -> Unit = {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            onBack()
        }
    }
    
    // 拦截系统返回键
    BackHandler {
        handleBack()
    }
    
    var showApiSettings by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showRatioSelector by remember { mutableStateOf(false) }
    var showResolutionSelector by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showFullScreenInput by remember { mutableStateOf(false) }
    val collapsedInputFocusRequester = remember { FocusRequester() }
    
    // 图片预览状态（支持多图预览）
    var previewImages by remember { mutableStateOf<List<ImageSource>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    
    // 图库 ViewModel
    val mediaStoreRepository: MediaStoreRepository = koinInject()
    val galleryViewModel = remember { GalleryViewModel(mediaStoreRepository) }
    
    // 权限状态
    var permissionStatus by remember { mutableStateOf(checkPhotoPermissionStatus(context)) }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionStatus = checkPhotoPermissionStatus(context)
        val (newFullAccess, _) = permissionStatus
        val anyGranted = permissions.values.any { it }
        
        if (newFullAccess || (anyGranted && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            galleryViewModel.loadAlbums()
            showGallery = true
        }
    }
    
    // 处理选中的图片
    val handleSelectedImages: (List<android.net.Uri>) -> Unit = { uris ->
        uris.forEach { uri ->
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/png"
                
                // 获取图片尺寸
                val (width, height) = try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        Pair(options.outWidth, options.outHeight)
                    } ?: Pair(0, 0)
                } catch (e: Exception) {
                    Pair(0, 0)
                }
                
                viewModel.onEvent(
                    PaintEvent.AddImage(
                        SelectedImage(
                            id = "${System.currentTimeMillis()}-${Random.nextInt(1000)}",
                            uri = uri.toString(),
                            mimeType = mimeType,
                            width = width,
                            height = height
                        )
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(context, resources.getString(R.string.paint_image_load_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 打开图片选择器的函数
    val openImagePicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            permissionStatus = checkPhotoPermissionStatus(context)
            val (fullAccess, _) = permissionStatus
            
            when {
                fullAccess -> {
                    galleryViewModel.loadAlbums()
                    showGallery = true
                }
                else -> {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                galleryViewModel.loadAlbums()
                showGallery = true
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            }
        } else {
            // Android 12 及以下
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                galleryViewModel.loadAlbums()
                showGallery = true
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    // 监听滚动到底部事件（reverseLayout=true 时，index 0 是底部）
    LaunchedEffect(Unit) {
        viewModel.scrollToBottomEvent.collectLatest { shouldAnimate ->
            // 等待 Compose 完成重组和布局
            delay(50)
            if (listState.layoutInfo.totalItemsCount > 0) {
                if (shouldAnimate) {
                    // 发送新消息时使用动画
                    listState.animateScrollToItem(0)
                } else {
                    // 点击按钮时瞬间到达
                    listState.scrollToItem(0)
                }
            }
        }
    }

    // 监听Toast事件
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            val text = when (message) {
                is PaintToastMessage.PleaseConfigApi -> resources.getString(R.string.paint_please_config_api)
                is PaintToastMessage.GenerateSuccess -> resources.getString(R.string.paint_generate_success)
                is PaintToastMessage.GenerateMultipleSuccess -> resources.getString(R.string.paint_generate_multiple_success, message.count)
                is PaintToastMessage.GenerateFailed -> resources.getString(R.string.paint_generate_failed) + (message.error?.let { ": $it" } ?: "")
                is PaintToastMessage.Stopped -> resources.getString(R.string.paint_stopped)
                is PaintToastMessage.SaveSuccess -> resources.getString(R.string.paint_save_success)
                is PaintToastMessage.Deleted -> resources.getString(R.string.paint_deleted)
                is PaintToastMessage.EnhanceSuccess -> resources.getString(R.string.paint_enhance_success)
                is PaintToastMessage.EnhanceFailed -> resources.getString(R.string.paint_enhance_failed) + (message.error?.let { ": $it" } ?: "")
                is PaintToastMessage.GeneratingInProgress -> resources.getString(R.string.paint_generating_in_progress)
                is PaintToastMessage.DownloadSuccess -> resources.getString(R.string.paint_download_success)
                is PaintToastMessage.DownloadFailed -> resources.getString(R.string.paint_download_failed) + (message.error?.let { ": $it" } ?: "")
                is PaintToastMessage.CannotRegenerate -> resources.getString(R.string.paint_cannot_regenerate)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    // 监听滚动状态（reverseLayout=true 时，firstVisibleItemIndex 接近 0 表示在底部）
    LaunchedEffect(listState) {
        snapshotFlow { 
            listState.firstVisibleItemIndex <= 1
        }.collect { isAtBottom ->
            viewModel.onEvent(PaintEvent.UpdateScrollState(isAtBottom))
        }
    }

    // 使用 ModalNavigationDrawer 实现侧边抽屉
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawerContent(
                sessions = uiState.sessions,
                currentSessionId = uiState.currentSession?.id,
                onSelectSession = { 
                    viewModel.onEvent(PaintEvent.SelectSession(it))
                    scope.launch { drawerState.close() }
                },
                onCreateSession = {
                    viewModel.onEvent(PaintEvent.CreateSession())
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { viewModel.onEvent(PaintEvent.DeleteSession(it)) },
                onRenameSession = { sessionId, newTitle ->
                    viewModel.onEvent(PaintEvent.RenameSession(sessionId, newTitle))
                }
            )
        },
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            topBar = {
                PaintTopBar(
                    currentSession = uiState.currentSession,
                    selectedModel = uiState.selectedModel,
                    onSessionClick = { 
                        scope.launch { drawerState.open() } 
                    },
                    onModelClick = { showModelSelector = true },
                    onClose = handleBack
                )
            },
            bottomBar = {
                PaintBottomBar(
                    promptText = uiState.promptText,
                    selectedImages = uiState.selectedImages,
                    isGenerating = uiState.isGenerating,
                    isLoading = uiState.isLoading,
                    generationStartTime = uiState.generationStartTime,
                    selectedModel = uiState.selectedModel,
                    selectedRatio = uiState.selectedAspectRatio,
                    selectedResolution = uiState.selectedResolution,
                    activeProfile = uiState.activeProfile,
                    isApiProfileLoaded = uiState.isApiProfileLoaded,
                    collapsedInputFocusRequester = collapsedInputFocusRequester,
                    onPromptChange = { viewModel.onEvent(PaintEvent.UpdatePrompt(it)) },
                    onSend = { viewModel.onEvent(PaintEvent.SendMessage) },
                    onStop = { viewModel.onEvent(PaintEvent.StopGeneration) },
                    onEnhance = { viewModel.onEvent(PaintEvent.EnhancePrompt) },
                    onPickImage = openImagePicker,
                    onRemoveImage = { viewModel.onEvent(PaintEvent.RemoveImage(it)) },
                    onSettingsClick = { showApiSettings = true },
                    onModelClick = { showModelSelector = true },
                    onRatioClick = { showRatioSelector = true },
                    onResolutionClick = { showResolutionSelector = true },
                    onExpandInput = { showFullScreenInput = true },
                    onImagePreview = { images, index ->
                        previewImages = images
                        previewInitialIndex = index
                    },
                    onApplyRatio = { ratio ->
                        viewModel.onEvent(PaintEvent.SelectAspectRatio(ratio))
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
            ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    // 切换会话时的空屏过渡
                    Box(modifier = Modifier.fillMaxSize())
                } else if (uiState.messages.isEmpty() && uiState.currentSession == null) {
                    // 空状态
                    EmptyState()
                } else {
                    // 预先过滤消息列表：只保留应该显示的消息
                    // 对于版本组，使用关联的用户消息的 createdAt 来保持位置稳定
                    val filteredMessages = remember(uiState.messages, uiState.activeVersions) {
                        // 构建用户消息 ID -> createdAt 的映射
                        val userMessageTimes = uiState.messages
                            .filter { it.senderIdentity == SenderIdentity.USER }
                            .associate { it.id to it.createdAt }
                        
                        uiState.messages.filter { message ->
                            if (message.senderIdentity == SenderIdentity.USER) {
                                true
                            } else {
                                val versionGroup = message.versionGroup
                                if (versionGroup == null) {
                                    true // 旧消息没有版本组，始终显示
                                } else {
                                    // 获取该版本组的所有消息，按 versionIndex 排序
                                    val versionsInGroup = uiState.messages
                                        .filter { it.versionGroup == versionGroup }
                                        .sortedBy { it.versionIndex }
                                    // activeVersions 存储的是列表位置
                                    val activePosition = uiState.activeVersions[versionGroup] 
                                        ?: (versionsInGroup.size - 1)
                                    val safePosition = activePosition.coerceIn(0, (versionsInGroup.size - 1).coerceAtLeast(0))
                                    // 检查当前消息是否是应该显示的那个
                                    versionsInGroup.getOrNull(safePosition)?.id == message.id
                                }
                            }
                        }.sortedBy { msg ->
                            // 对于 AI 消息，使用关联的用户消息时间排序，保持位置稳定
                            if (msg.senderIdentity == SenderIdentity.ASSISTANT && msg.parentUserMessageId != null) {
                                userMessageTimes[msg.parentUserMessageId] ?: msg.createdAt
                            } else {
                                msg.createdAt
                            }
                        }.asReversed()
                    }
                    
                    // 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                        reverseLayout = true
                    ) {
                        items(
                            items = filteredMessages,
                            key = { it.id },
                            contentType = { it.messageType }
                        ) { message ->
                            MessageItem(
                                message = message,
                                allMessages = uiState.messages,
                                activeVersions = uiState.activeVersions,
                                selectedAspectRatio = uiState.selectedAspectRatio,
                                onImageClick = { images, index ->
                                    previewImages = images
                                    previewInitialIndex = index
                                },
                                onCopyText = { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, resources.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
                                },
                                onEditMessage = { messageId ->
                                    viewModel.onEvent(PaintEvent.EditUserMessage(messageId))
                                },
                                onDeleteMessage = { messageId ->
                                    viewModel.onEvent(PaintEvent.DeleteMessage(messageId))
                                },
                                onDeleteVersionGroup = { versionGroup ->
                                    viewModel.onEvent(PaintEvent.DeleteMessageVersion(versionGroup))
                                },
                                onRegenerate = { messageId ->
                                    viewModel.onEvent(PaintEvent.RegenerateMessage(messageId))
                                },
                                onSwitchVersion = { versionGroup, targetIndex ->
                                    viewModel.onEvent(PaintEvent.SwitchMessageVersion(versionGroup, targetIndex))
                                },
                                onDownloadImage = { image ->
                                    // 下载图片逻辑
                                    scope.launch {
                                        try {
                                            val imagePath = image.localPath
                                            if (imagePath != null) {
                                                saveImageToGallery(context, imagePath)
                                                Toast.makeText(context, resources.getString(R.string.paint_download_success), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, resources.getString(R.string.paint_download_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onAddImages = { images ->
                                    // 将图片添加到选中列表
                                    images.forEach { image ->
                                        val path = image.localPath ?: return@forEach
                                        viewModel.onEvent(
                                            PaintEvent.AddImage(
                                                SelectedImage(
                                                    id = "${System.currentTimeMillis()}-${Random.nextInt(1000)}",
                                                    uri = path,
                                                    mimeType = image.mimeType,
                                                    width = image.width,
                                                    height = image.height
                                                )
                                            )
                                        )
                                    }
                                },
                                onUpdateImageDimensions = { messageId, imageId, width, height ->
                                    viewModel.onEvent(
                                        PaintEvent.UpdateImageDimensions(messageId, imageId, width, height)
                                    )
                                },
                                onSplitImage = { imagePath ->
                                    ImageSplitActivity.launch(context, imagePath)
                                }
                            )
                        }
                    }
                }

                // 滚动到底部按钮
                AnimatedVisibility(
                    visible = !uiState.isAtBottom && uiState.messages.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    ScrollToBottomButton(
                        newMessageCount = uiState.newMessageCount,
                        onClick = { viewModel.onEvent(PaintEvent.ScrollToBottom) }
                    )
                }
            }
            }

            if (showFullScreenInput) {
                FullScreenPromptOverlay(
                    promptText = uiState.promptText,
                    selectedImages = uiState.selectedImages,
                    isGenerating = uiState.isGenerating,
                    isLoading = uiState.isLoading,
                    onPromptChange = { viewModel.onEvent(PaintEvent.UpdatePrompt(it)) },
                    onSend = {
                        viewModel.onEvent(PaintEvent.SendMessage)
                        showFullScreenInput = false
                        collapsedInputFocusRequester.requestFocus()
                    },
                    onStop = { viewModel.onEvent(PaintEvent.StopGeneration) },
                    onEnhance = { viewModel.onEvent(PaintEvent.EnhancePrompt) },
                    onPickImage = openImagePicker,
                    onRemoveImage = { viewModel.onEvent(PaintEvent.RemoveImage(it)) },
                    onImagePreview = { images, index ->
                        previewImages = images
                        previewInitialIndex = index
                    },
                    onDismiss = {
                        showFullScreenInput = false
                        collapsedInputFocusRequester.requestFocus()
                    }
                )
            }
        }
    }

    // API设置对话框
    if (showApiSettings) {
        ApiSettingsDialog(
            profiles = uiState.apiProfiles,
            activeProfileId = uiState.activeProfile?.id,
            onSaveProfile = { viewModel.onEvent(PaintEvent.SaveApiProfile(it)) },
            onDeleteProfile = { viewModel.onEvent(PaintEvent.DeleteApiProfile(it)) },
            onSetActive = { viewModel.onEvent(PaintEvent.SetActiveProfile(it)) },
            onDismiss = { showApiSettings = false }
        )
    }

    // 模型选择器
    if (showModelSelector) {
        ModelSelectorDialog(
            selectedModel = uiState.selectedModel,
            onSelect = { 
                viewModel.onEvent(PaintEvent.SelectModel(it))
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false }
        )
    }

    // 比例选择器
    if (showRatioSelector) {
        RatioSelectorDialog(
            selectedRatio = uiState.selectedAspectRatio,
            onSelect = {
                viewModel.onEvent(PaintEvent.SelectAspectRatio(it))
                showRatioSelector = false
            },
            onDismiss = { showRatioSelector = false }
        )
    }

    // 分辨率选择器
    if (showResolutionSelector) {
        ResolutionSelectorDialog(
            selectedResolution = uiState.selectedResolution,
            onSelect = {
                viewModel.onEvent(PaintEvent.SelectResolution(it))
                showResolutionSelector = false
            },
            onDismiss = { showResolutionSelector = false }
        )
    }
    
    // 自定义图库浏览器
    if (showGallery) {
        GalleryScreen(
            viewModel = galleryViewModel,
            onImagesSelected = { selectedUris ->
                handleSelectedImages(selectedUris)
                showGallery = false
            },
            onDismiss = {
                showGallery = false
            }
        )
    }
    
    // 图片预览对话框（支持多图左右滑动）
    if (previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            config = ImagePreviewConfig(
                showRotateButton = true,
                showFlipButton = true,
                showDownloadButton = true
            ),
            onDismiss = { 
                previewImages = emptyList()
                previewInitialIndex = 0
            }
        )
    }
    
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaintTopBar(
    currentSession: PaintSession?,
    selectedModel: PaintModel,
    onSessionClick: () -> Unit,
    onModelClick: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onSessionClick) {
                Icon(Icons.Default.Menu, contentDescription = "会话")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.draw),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Brush,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.paint_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.paint_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageItem(
    message: PaintMessage,
    allMessages: List<PaintMessage> = emptyList(),
    activeVersions: Map<String, Int> = emptyMap(),
    selectedAspectRatio: AspectRatio = AspectRatio.RATIO_1_1,
    onImageClick: (List<ImageSource>, Int) -> Unit = { _, _ -> },
    onCopyText: (String) -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onDeleteVersionGroup: (String) -> Unit = {},
    onRegenerate: (String) -> Unit = {},
    onSwitchVersion: (String, Int) -> Unit = { _, _ -> },
    onDownloadImage: (PaintImage) -> Unit = {},
    onAddImages: (List<PaintImage>) -> Unit = {},
    onUpdateImageDimensions: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    onSplitImage: (String) -> Unit = {}
) {
    val isUser = message.senderIdentity == SenderIdentity.USER
    val isAssistant = !isUser
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteOptions by remember { mutableStateOf(false) }

    // 计算版本信息 - 基于列表位置而非 versionIndex
    val versionGroup = message.versionGroup
    val versionInfo = remember(versionGroup, allMessages, activeVersions) {
        if (versionGroup != null && isAssistant) {
            val versions = allMessages.filter { it.versionGroup == versionGroup }
                .sortedBy { it.versionIndex }
            val totalVersions = versions.size
            // activeVersions 存储的是列表位置（0-based）
            val currentPosition = (activeVersions[versionGroup] ?: (totalVersions - 1))
                .coerceIn(0, (totalVersions - 1).coerceAtLeast(0))
            Triple(currentPosition, totalVersions, versions)
        } else {
            Triple(0, 1, emptyList())
        }
    }
    val (currentVersionIndex, totalVersions, _) = versionInfo

    // 计算生成时长
    val durationMillis = if (message.status == MessageStatus.GENERATING) {
        val nowMillis by produceState(
            initialValue = System.currentTimeMillis(),
            key1 = message.id
        ) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000)
            }
        }
        (nowMillis - message.createdAt).coerceAtLeast(0L)
    } else {
        (message.updatedAt - message.createdAt).coerceAtLeast(0L)
    }
    val durationText = formatDuration(durationMillis)
    
    val bubbleShape = remember(isUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        )
    }
    
    // 删除确认对话框（用户消息或单版本AI消息）
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.message_delete_title),
            message = stringResource(R.string.message_delete_confirm),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            isDangerous = true,
            onConfirm = { onDeleteMessage(message.id) },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    
    // AI消息多版本删除选项对话框
    if (showDeleteOptions) {
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            title = { Text(stringResource(R.string.message_delete_title)) },
            text = { Text(stringResource(R.string.message_delete_version_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteOptions = false
                        versionGroup?.let { onDeleteVersionGroup(it) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.message_delete_all_versions))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteOptions = false
                        onDeleteMessage(message.id)
                    }
                ) {
                    Text(stringResource(R.string.message_delete_current_version))
                }
            }
        )
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 消息气泡
        Surface(
            shape = bubbleShape,
            color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 文本内容
                if (message.messageContent.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            text = message.messageContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.status == MessageStatus.ERROR)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // 图片 - 支持多图预览
                val imageSources = remember(message.images) {
                    message.images.mapNotNull { image ->
                        image.localPath?.let { ImageSource.StringSource(it) }
                            ?: image.base64Data?.let { ImageSource.Base64Source(it, image.mimeType) }
                    }
                }
                
                message.images.forEachIndexed { index, image ->
                    Spacer(modifier = Modifier.height(8.dp))
                    image.localPath?.let { path ->
                        MessageLocalImage(
                            path = path,
                            width = image.width,
                            height = image.height,
                            onClick = { onImageClick(imageSources, index) },
                            onLongClick = { },
                            onDimensionsLoaded = { w, h ->
                                // 如果原始图片没有宽高信息，回填更新
                                if (image.width == 0 || image.height == 0) {
                                    onUpdateImageDimensions(message.id, image.id, w, h)
                                }
                            }
                        )
                    }
                    image.base64Data?.let { base64 ->
                        MessageImage(
                            imageId = image.id,
                            base64 = base64,
                            width = image.width,
                            height = image.height,
                            onClick = { onImageClick(imageSources, index) },
                            onLongClick = { },
                            onDimensionsLoaded = { w, h ->
                                if (image.width == 0 || image.height == 0) {
                                    onUpdateImageDimensions(message.id, image.id, w, h)
                                }
                            }
                        )
                    }
                }
                
                // 生成中状态 - 显示呼吸动画占位图
                if (isAssistant && message.status == MessageStatus.GENERATING) {
                    GeneratingPlaceholder(
                        aspectRatio = selectedAspectRatio
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.paint_generating_time, durationText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 完成状态
                if (isAssistant && message.status != MessageStatus.GENERATING) {
                    val statusText = when (message.status) {
                        MessageStatus.SUCCESS -> stringResource(R.string.paint_status_done)
                        MessageStatus.ERROR -> stringResource(R.string.paint_status_failed)
                        MessageStatus.PENDING -> stringResource(R.string.paint_status_pending)
                        MessageStatus.GENERATING -> ""
                    }
                    val metaText = stringResource(R.string.paint_status_time, statusText, durationText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.status == MessageStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
        
        // AI消息的固定操作栏（包括正在生成状态，以便用户可以处理卡住的消息）
        if (isAssistant) {
            Spacer(modifier = Modifier.height(4.dp))
            MessageActionBar(
                message = message,
                totalVersions = totalVersions,
                currentVersionIndex = currentVersionIndex,
                hasImages = message.images.isNotEmpty(),
                onAddImages = { onAddImages(message.images) },
                onCopy = { onCopyText(message.messageContent) },
                onRegenerate = { onRegenerate(message.id) },
                onPreviousVersion = {
                    if (versionGroup != null && currentVersionIndex > 0) {
                        onSwitchVersion(versionGroup, currentVersionIndex - 1)
                    }
                },
                onNextVersion = {
                    if (versionGroup != null && currentVersionIndex < totalVersions - 1) {
                        onSwitchVersion(versionGroup, currentVersionIndex + 1)
                    }
                },
                onDelete = {
                    // 多版本时显示选项，单版本直接确认删除
                    if (totalVersions > 1) {
                        showDeleteOptions = true
                    } else {
                        showDeleteConfirm = true
                    }
                },
                onDownload = {
                    message.images.firstOrNull()?.let { onDownloadImage(it) }
                },
                onSplitImage = {
                    message.images.firstOrNull()?.localPath?.let { onSplitImage(it) }
                }
            )
        }
        
        // 用户消息的简化操作栏（仅复制和删除）
        if (isUser) {
            Spacer(modifier = Modifier.height(4.dp))
            UserMessageActionBar(
                message = message,
                onCopy = { onCopyText(message.messageContent) },
                onEdit = { onEditMessage(message.id) },
                onDelete = { showDeleteConfirm = true }
            )
        }
    }
}

/**
 * AI消息操作栏
 */
@Composable
private fun MessageActionBar(
    message: PaintMessage,
    totalVersions: Int,
    currentVersionIndex: Int,
    hasImages: Boolean,
    onAddImages: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onSplitImage: () -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val moreMenuItems = listOf(
        AppMenuItem(
            title = stringResource(R.string.split_image),
            icon = Icons.Default.ContentCut,
            enabled = hasImages,
            onClick = {
                showMoreMenu = false
                onSplitImage()
            }
        )
    )

    // 菜单固定出现在锚点上方：
    // - menuHeight ≈ items * 48dp
    // - anchorHeight ≈ 36dp（ActionIconButton）
    // - gap ≈ 16dp（留白，避免挡住按钮）
    val moreMenuOffsetY = remember(moreMenuItems.size) { -((moreMenuItems.size * 48) + 36 + 16).dp }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 版本切换器（仅多版本时显示）
        if (totalVersions > 1) {
            VersionSwitcher(
                current = currentVersionIndex + 1,
                total = totalVersions,
                onPrevious = onPreviousVersion,
                onNext = onNextVersion
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        // 添加图片按钮（仅有图片时显示，放在版本切换器右边）
        if (hasImages) {
            ActionIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.message_add_to_selected),
                onClick = onAddImages
            )
        }
        
        // 复制按钮（仅有文本时显示）
        if (message.messageContent.isNotEmpty()) {
            ActionIconButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.message_copy),
                onClick = onCopy
            )
        }
        
        // 重新生成按钮
        ActionIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.message_regenerate),
            onClick = onRegenerate
        )
        
        // 下载按钮（仅有图片时显示）
        if (hasImages) {
            ActionIconButton(
                icon = Icons.Default.Download,
                contentDescription = stringResource(R.string.message_download),
                onClick = onDownload
            )
        }
        
        // 删除按钮
        ActionIconButton(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.message_delete),
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )

        Box {
            ActionIconButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "更多",
                onClick = { showMoreMenu = true }
            )
            AppDropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                items = moreMenuItems,
                offset = DpOffset(0.dp, moreMenuOffsetY)
            )
        }
    }
}

/**
 * 用户消息操作栏（简化版）
 */
@Composable
private fun UserMessageActionBar(
    message: PaintMessage,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 复制按钮（仅有文本时显示）
        if (message.messageContent.isNotEmpty()) {
            ActionIconButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.message_copy),
                onClick = onCopy
            )
        }
        
        // 编辑按钮
        ActionIconButton(
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.message_edit),
            onClick = onEdit
        )
        
        // 删除按钮
        ActionIconButton(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.message_delete),
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )
    }
}

/**
 * 版本切换器
 */
@Composable
private fun VersionSwitcher(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 2.dp)
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = current > 1,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.message_version_previous),
                modifier = Modifier.size(16.dp),
                tint = if (current > 1) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
        
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        IconButton(
            onClick = onNext,
            enabled = current < total,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.message_version_next),
                modifier = Modifier.size(16.dp),
                tint = if (current < total) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * 操作栏图标按钮
 */
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            // 去掉默认 ripple（会出现一闪而过的灰色无圆角动效）
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
    }
}

@Composable
private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).toInt().coerceAtLeast(0)
    return if (totalSeconds < 60) {
        stringResource(R.string.paint_time_format_seconds, totalSeconds)
    } else {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        stringResource(R.string.paint_time_format_minutes, minutes, seconds)
    }
}

/**
 * 消息图片组件 - 异步解码，带缓存
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageImage(
    imageId: String,
    base64: String,
    width: Int = 0,
    height: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDimensionsLoaded: ((Int, Int) -> Unit)? = null
) {
    val bitmap = rememberDecodedBitmap(imageId, base64)
    val shape = remember { RoundedCornerShape(8.dp) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxWidth = screenWidth / 2
    
    // 优先使用预设宽高，否则从解码后的 bitmap 获取
    val aspectRatio = when {
        width > 0 && height > 0 -> width.toFloat() / height.toFloat()
        bitmap != null -> bitmap.width.toFloat() / bitmap.height.toFloat()
        else -> 1f // 默认 1:1 占位
    }
    
    // 如果没有预设宽高且 bitmap 已加载，回调通知
    LaunchedEffect(bitmap, width, height) {
        if (bitmap != null && (width == 0 || height == 0)) {
            onDimensionsLoaded?.invoke(bitmap.width, bitmap.height)
        }
    }
    
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            // 加载占位符
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageLocalImage(
    path: String,
    width: Int = 0,
    height: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDimensionsLoaded: ((Int, Int) -> Unit)? = null
) {
    val shape = remember { RoundedCornerShape(8.dp) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxWidth = screenWidth / 2
    
    // 如果有预设宽高，直接使用；否则等待图片加载后获取
    val presetAspectRatio = if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        null
    }
    var loadedAspectRatio by remember { mutableStateOf<Float?>(null) }
    val aspectRatio = presetAspectRatio ?: loadedAspectRatio
    
    // 计算占位图尺寸
    val placeholderModifier = if (aspectRatio != null) {
        Modifier
            .widthIn(max = maxWidth)
            .aspectRatio(aspectRatio)
    } else {
        // 没有宽高信息时使用默认 1:1 占位
        Modifier
            .width(maxWidth)
            .aspectRatio(1f)
    }
    
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    
    Box(
        modifier = placeholderModifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isError) {
            // 图片已删除，显示占位图，点击无反应
            Image(
                painter = painterResource(id = R.drawable.ic_image_deleted),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    ),
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    isLoading = false
                    isError = false
                    // 如果没有预设宽高，从加载结果获取并回调
                    if (presetAspectRatio == null) {
                        val painter = state.painter
                        val intrinsicSize = painter.intrinsicSize
                        if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                            loadedAspectRatio = intrinsicSize.width / intrinsicSize.height
                            onDimensionsLoaded?.invoke(
                                intrinsicSize.width.toInt(),
                                intrinsicSize.height.toInt()
                            )
                        }
                    }
                },
                onLoading = { isLoading = true },
                onError = { 
                    isLoading = false
                    isError = true
                }
            )
            
            // 加载中显示图片图标
            if (isLoading) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ScrollToBottomButton(
    newMessageCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (newMessageCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (newMessageCount > 0) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            if (newMessageCount > 0) {
                Text(
                    text = "$newMessageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 检查照片访问权限状态
 * @return Pair<是否有完整权限, 是否有部分权限>
 */
private fun checkPhotoPermissionStatus(context: android.content.Context): Pair<Boolean, Boolean> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 14+ (API 34+)
        val hasFullAccess = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasPartialAccess = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        
        Pair(hasFullAccess, hasPartialAccess && !hasFullAccess)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13 (API 33)
        val hasFullAccess = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        Pair(hasFullAccess, false)
    } else {
        // Android 12 及以下
        val hasFullAccess = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        Pair(hasFullAccess, false)
    }
}

/**
 * 保存图片到相册
 */
private suspend fun saveImageToGallery(context: Context, imagePath: String) {
    withContext(Dispatchers.IO) {
        val sourceFile = java.io.File(imagePath)
        if (!sourceFile.exists()) {
            throw java.io.IOException("源文件不存在")
        }
        
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "AI_Paint_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AIPaint")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw java.io.IOException("无法创建媒体文件")
        
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}

/**
 * 生成中占位图 - 骨架屏光效动画
 * 灰底 + 图片图标 + 从左到右循环的淡白色光效
 */
@Composable
private fun GeneratingPlaceholder(
    aspectRatio: AspectRatio,
    modifier: Modifier = Modifier
) {
    // 计算宽高比
    val ratio = when (aspectRatio) {
        AspectRatio.RATIO_1_1 -> 1f
        AspectRatio.RATIO_2_3 -> 2f / 3f
        AspectRatio.RATIO_3_2 -> 3f / 2f
        AspectRatio.RATIO_3_4 -> 3f / 4f
        AspectRatio.RATIO_4_3 -> 4f / 3f
        AspectRatio.RATIO_16_9 -> 16f / 9f
        AspectRatio.RATIO_9_16 -> 9f / 16f
    }
    
    // 与真实图片一致的宽度
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxWidth = screenWidth / 2
    
    // 灰色背景
    val backgroundColor = Color(0xFFE0E0E0)
    
    // 从左到右循环的光效动画
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    // 固定外框容器
    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .drawBehind {
                // 绘制从左到右的光效
                val shimmerWidth = size.width * 0.4f
                val startX = size.width * shimmerOffset
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        startX = startX,
                        endX = startX + shimmerWidth
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 中心图片图标
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFFBDBDBD)
        )
    }
}
