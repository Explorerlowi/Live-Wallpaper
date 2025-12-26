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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
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
import com.example.livewallpaper.ui.theme.Teal300
import com.example.livewallpaper.ui.components.ConfirmDialog
import com.example.livewallpaper.ui.components.ImagePreviewConfig
import com.example.livewallpaper.ui.components.ImageSource
import com.example.livewallpaper.ui.components.SingleImagePreviewDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * 图片缓存 - 避免重复解码 Base64
 */
private object ImageCache {
    // 缓存最多 20 张图片，约 100MB（假设每张 5MB）
    private val cache = LruCache<String, Bitmap>(20)
    
    fun get(imageId: String): Bitmap? = cache.get(imageId)
    
    fun put(imageId: String, bitmap: Bitmap) {
        cache.put(imageId, bitmap)
    }
}

/**
 * 异步解码 Base64 图片
 */
@Composable
private fun rememberDecodedBitmap(imageId: String, base64: String): Bitmap? {
    var bitmap by remember(imageId) { mutableStateOf(ImageCache.get(imageId)) }
    
    LaunchedEffect(imageId) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 使用 DrawerState 控制抽屉
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
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
    
    // 图片预览状态
    var previewImageSource by remember { mutableStateOf<ImageSource?>(null) }
    
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
                viewModel.onEvent(
                    PaintEvent.AddImage(
                        SelectedImage(
                            id = "${System.currentTimeMillis()}-${Random.nextInt(1000)}",
                            uri = uri.toString(),
                            mimeType = mimeType
                        )
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.paint_image_load_failed), Toast.LENGTH_SHORT).show()
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
        viewModel.scrollToBottomEvent.collectLatest {
            withFrameNanos { }
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.scrollToItem(0)
            }
        }
    }

    // 监听Toast事件
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            val text = when (message) {
                is PaintToastMessage.PleaseConfigApi -> context.getString(R.string.paint_please_config_api)
                is PaintToastMessage.GenerateSuccess -> context.getString(R.string.paint_generate_success)
                is PaintToastMessage.GenerateFailed -> context.getString(R.string.paint_generate_failed) + (message.error?.let { ": $it" } ?: "")
                is PaintToastMessage.Stopped -> context.getString(R.string.paint_stopped)
                is PaintToastMessage.SaveSuccess -> context.getString(R.string.paint_save_success)
                is PaintToastMessage.Deleted -> context.getString(R.string.paint_deleted)
                is PaintToastMessage.EnhanceSuccess -> context.getString(R.string.paint_enhance_success)
                is PaintToastMessage.EnhanceFailed -> context.getString(R.string.paint_enhance_failed) + (message.error?.let { ": $it" } ?: "")
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
                onDeleteSession = { viewModel.onEvent(PaintEvent.DeleteSession(it)) }
            )
        },
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Scaffold(
            topBar = {
                PaintTopBar(
                    currentSession = uiState.currentSession,
                    selectedModel = uiState.selectedModel,
                    onSessionClick = { scope.launch { drawerState.open() } },
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
                    onImagePreview = { source ->
                        previewImageSource = source
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
                if (uiState.messages.isEmpty() && uiState.currentSession == null) {
                    // 空状态
                    EmptyState()
                } else {
                    // 预先计算反转列表，避免每次重组都创建新列表
                    val reversedMessages = remember(uiState.messages) {
                        uiState.messages.asReversed()
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
                            items = reversedMessages,
                            key = { it.id },
                            contentType = { it.messageType }
                        ) { message ->
                            MessageItem(
                                message = message,
                                onImageClick = { source ->
                                    previewImageSource = source
                                },
                                onCopyText = { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
                                },
                                onDeleteMessage = { messageId ->
                                    viewModel.onEvent(PaintEvent.DeleteMessage(messageId))
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
    
    // 图片预览对话框
    previewImageSource?.let { source ->
        SingleImagePreviewDialog(
            image = source,
            config = ImagePreviewConfig(
                showRotateButton = true,
                showFlipButton = true,
                showDownloadButton = true
            ),
            onDismiss = { previewImageSource = null }
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
    onImageClick: (ImageSource) -> Unit = {},
    onCopyText: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {}
) {
    val isUser = message.senderIdentity == SenderIdentity.USER
    val isAssistant = !isUser
    
    // 长按菜单状态
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 记录长按位置
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    // 计算生成时长
    // 对于已完成的消息，直接使用 updatedAt - createdAt
    // 对于正在生成的消息，实时更新当前时间
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
        // 已完成的消息，使用固定的时长
        (message.updatedAt - message.createdAt).coerceAtLeast(0L)
    }
    val durationText = formatDuration(durationMillis)
    
    // 预先计算不变的值，避免重组时重复计算
    val bubbleShape = remember(isUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        )
    }
    
    // 删除确认对话框
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
    
    Box {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = bubbleShape,
                color = if (isUser) Teal300.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                with(density) {
                                    pressOffset = DpOffset(offset.x.toDp(), offset.y.toDp())
                                }
                                showMenu = true
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 文本内容
                    if (message.messageContent.isNotEmpty()) {
                        Text(
                            text = message.messageContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.status == MessageStatus.ERROR) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // 图片 - 使用 image.id 作为 key 而不是整个 base64
                    message.images.forEach { image ->
                        Spacer(modifier = Modifier.height(8.dp))
                        image.localPath?.let { path ->
                            MessageLocalImage(
                                path = path,
                                onClick = { onImageClick(ImageSource.StringSource(path)) },
                                onLongClick = { showMenu = true }
                            )
                        }
                        image.base64Data?.let { base64 ->
                            MessageImage(
                                imageId = image.id,
                                base64 = base64,
                                onClick = { onImageClick(ImageSource.Base64Source(base64, image.mimeType)) },
                                onLongClick = { showMenu = true }
                            )
                        }
                    }
                    
                    // 生成中状态
                    if (isAssistant && message.status == MessageStatus.GENERATING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Teal300
                            )
                            Text(
                                text = stringResource(R.string.paint_generating_time, durationText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

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
        }
        
        // 长按菜单 - 在长按位置显示，使用主题样式
        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(
                extraSmall = RoundedCornerShape(12.dp)
            )
        ) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = pressOffset,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 复制选项（仅当有文本内容时显示）
                if (message.messageContent.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { 
                            Text(
                                stringResource(R.string.message_copy),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        onClick = {
                            onCopyText(message.messageContent)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentCopy, 
                                contentDescription = null,
                                tint = Teal300,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                // 删除选项
                DropdownMenuItem(
                    text = { 
                        Text(
                            stringResource(R.string.message_delete),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        ) 
                    },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val bitmap = rememberDecodedBitmap(imageId, base64)
    val shape = remember { RoundedCornerShape(8.dp) }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentScale = ContentScale.FillWidth
        )
    } else {
        // 加载占位符
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Teal300
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageLocalImage(
    path: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val shape = remember { RoundedCornerShape(8.dp) }
    AsyncImage(
        model = path,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentScale = ContentScale.FillWidth
    )
}

@Composable
private fun ScrollToBottomButton(
    newMessageCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (newMessageCount > 0) Teal300 else MaterialTheme.colorScheme.surface,
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
