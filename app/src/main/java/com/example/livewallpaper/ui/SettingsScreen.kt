package com.example.livewallpaper.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.example.livewallpaper.LiveWallpaperService
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsEvent
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.gallery.data.MediaStoreRepository
import com.example.livewallpaper.gallery.ui.GalleryScreen
import com.example.livewallpaper.gallery.viewmodel.GalleryViewModel
import com.example.livewallpaper.ui.theme.ButtonPrimary
import com.example.livewallpaper.ui.theme.MintGreen100
import com.example.livewallpaper.ui.theme.MintGreen200
import com.example.livewallpaper.ui.theme.MintGreen300
import com.example.livewallpaper.ui.theme.Teal200
import com.example.livewallpaper.ui.theme.Teal300
import com.example.livewallpaper.ui.theme.Teal400
import com.example.livewallpaper.ui.theme.TextPrimary
import com.example.livewallpaper.ui.theme.TextSecondary
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.ui.LanguageOption
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

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

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 对话框状态
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showCropUri by remember { mutableStateOf<String?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    
    // 多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }
    
    // 多选模式下的返回键处理
    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedUris = emptySet()
    }
    
    // 图库 ViewModel - 使用 remember 配合 DisposableEffect 管理生命周期
    // 仅在需要显示图库时才创建和加载数据
    val mediaStoreRepository: MediaStoreRepository = koinInject()
    val galleryViewModel = remember { GalleryViewModel(mediaStoreRepository) }
    
    // 确保 ViewModel 在界面销毁时正确清理
    DisposableEffect(galleryViewModel) {
        onDispose {
            // ViewModel 会自动清理，这里可以添加额外的清理逻辑
        }
    }
    
    // 权限状态
    var permissionStatus by remember { mutableStateOf(checkPhotoPermissionStatus(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 监听生命周期，返回界面时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = checkPhotoPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 处理图片选择结果
    val handleSelectedUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            // Photo Picker 返回的 URI 有临时读取权限，需要持久化以便壁纸服务使用
            val persistedUris = uris.mapNotNull { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    uri.toString()
                } catch (e: Exception) {
                    // 如果无法持久化，仍然可以使用临时权限
                    uri.toString()
                }
            }
            viewModel.onEvent(SettingsEvent.AddImages(persistedUris))
        }
    }

    // 使用 Photo Picker API
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> handleSelectedUris(uris) }
    
    // 使用 OpenMultipleDocuments 作为备选方案，可以访问所有文件
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleSelectedUris(uris) }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 更新权限状态
        permissionStatus = checkPhotoPermissionStatus(context)
        val (newFullAccess, newPartialAccess) = permissionStatus
        
        // 检查是否有任何权限被授予
        val anyGranted = permissions.values.any { it }
        
        if (newFullAccess || (anyGranted && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            // 获得完整权限（或在 Android 13 及以下获得权限），打开自定义图库浏览器
            galleryViewModel.loadAlbums()
            showGallery = true
        } else if (newPartialAccess) {
            // Android 14+ 只有部分权限，显示提示对话框
            showPermissionDialog = true
        }
    }
    
    // 打开图片选择器的函数
    val openImagePicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: 检查权限状态
            permissionStatus = checkPhotoPermissionStatus(context)
            val (fullAccess, partialAccess) = permissionStatus
            
            when {
                fullAccess -> {
                    // 有完整权限，打开自定义图库浏览器
                    galleryViewModel.loadAlbums()
                    showGallery = true
                }
                partialAccess -> {
                    // 部分权限，显示选择对话框
                    showPermissionDialog = true
                }
                else -> {
                    // 没有权限，请求权限
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13: 需要 READ_MEDIA_IMAGES 权限
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
            // Android 12 及以下: 需要 READ_EXTERNAL_STORAGE 权限
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

    LaunchedEffect(state.config.languageTag) {
        applyLanguage(state.config.languageTag)
    }

    // 最外层 Box，用于叠加图库界面
    Box(modifier = Modifier.fillMaxSize()) {
        // 主界面内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MintGreen100,
                            MintGreen200.copy(alpha = 0.5f)
                        )
                    )
                )
        ) {
            // 背景装饰 - 爪印图案
            PawPrintDecorations()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                TopBar(
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCount = selectedUris.size,
                    totalCount = state.config.imageUris.size,
                    isReorderEnabled = state.config.imageUris.size > 1,
                    onSettingsClick = { showSettingsDialog = true },
                    onReorderClick = { showReorderSheet = true },
                    onExitMultiSelect = {
                        isMultiSelectMode = false
                        selectedUris = emptySet()
                    },
                    onSelectAll = {
                        selectedUris = state.config.imageUris.toSet()
                    },
                    onDeleteSelected = {
                        showDeleteSelectedDialog = true
                    },
                    modifier = Modifier.padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                )

                // 图片瀑布流
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.config.imageUris.isEmpty()) {
                        // 空状态
                        EmptyState(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        // 瀑布流图片墙
                        StaggeredPhotoGrid(
                            imageUris = state.config.imageUris,
                            imageCropParams = state.config.imageCropParams,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedUris = selectedUris,
                            onImageClick = { index -> 
                                val uri = state.config.imageUris[index]
                                if (isMultiSelectMode) {
                                    // 多选模式下切换选中状态
                                    selectedUris = if (selectedUris.contains(uri)) {
                                        selectedUris - uri
                                    } else {
                                        selectedUris + uri
                                    }
                                } else {
                                    // 普通模式下进入裁剪调整界面
                                    showCropUri = uri
                                }
                            },
                            onImageLongPress = { uri -> 
                                if (!isMultiSelectMode) {
                                    // 长按进入多选模式
                                    isMultiSelectMode = true
                                    selectedUris = setOf(uri)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 添加图片按钮
                    AddImageButton(
                        onClick = openImagePicker,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }

                // 底部设置壁纸按钮
                val noImagesHint = stringResource(R.string.no_images_hint)
                BottomActionBar(
                    onSetWallpaperClick = {
                        if (state.config.imageUris.isEmpty()) {
                            // 没有图片时显示提示
                            Toast.makeText(context, noImagesHint, Toast.LENGTH_SHORT).show()
                        } else {
                            // 有图片时正常设置壁纸
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            intent.putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, LiveWallpaperService::class.java)
                            )
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
        
        // 自定义图库浏览器（覆盖在最上层，从底部弹出）
        if (showGallery) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onImagesSelected = { selectedUris ->
                    // 处理选中的图片
                    val uriStrings = selectedUris.map { it.toString() }
                    viewModel.onEvent(SettingsEvent.AddImages(uriStrings))
                    showGallery = false
                },
                onDismiss = {
                    showGallery = false
                }
            )
        }

        if (showReorderSheet) {
            ReorderImagesSheet(
                imageUris = state.config.imageUris,
                onConfirm = { newOrder ->
                    viewModel.onEvent(SettingsEvent.UpdateImageOrder(newOrder))
                    showReorderSheet = false
                },
                onDismiss = { showReorderSheet = false }
            )
        }
    }

    // 设置对话框
    if (showSettingsDialog) {
        SettingsDialog(
            currentInterval = state.config.interval,
            currentScaleMode = state.config.scaleMode,
            currentPlayMode = state.config.playMode,
            currentLanguageTag = state.config.languageTag,
            onConfirm = { interval, scaleMode, playMode ->
                viewModel.onEvent(SettingsEvent.UpdateInterval(interval))
                viewModel.onEvent(SettingsEvent.UpdateScaleMode(scaleMode))
                viewModel.onEvent(SettingsEvent.UpdatePlayMode(playMode))
            },
            onLanguageChange = { option ->
                viewModel.onEvent(SettingsEvent.UpdateLanguage(option.localeTag))
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 删除确认对话框（单张图片）
    showDeleteDialog?.let { uri ->
        DeleteConfirmDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = stringResource(R.string.delete_confirm_message),
            onConfirm = {
                viewModel.onEvent(SettingsEvent.RemoveImage(uri))
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
    
    // 删除选中图片确认对话框
    if (showDeleteSelectedDialog) {
        DeleteConfirmDialog(
            title = stringResource(R.string.delete_selected_title),
            message = stringResource(R.string.delete_selected_message, selectedUris.size),
            onConfirm = {
                viewModel.onEvent(SettingsEvent.RemoveImages(selectedUris.toList()))
                showDeleteSelectedDialog = false
                isMultiSelectMode = false
                selectedUris = emptySet()
            },
            onDismiss = { showDeleteSelectedDialog = false }
        )
    }

    // 图片预览对话框（保留作为备用）
    showPreviewIndex?.let { index ->
        ImagePreviewDialog(
            imageUris = state.config.imageUris,
            initialIndex = index,
            onDismiss = { showPreviewIndex = null }
        )
    }
    
    // 图片裁剪调整界面
    showCropUri?.let { uri ->
        val initialParams = state.config.imageCropParams[uri] ?: ImageCropParams()
        ImageCropScreen(
            imageUri = uri,
            initialParams = initialParams,
            onConfirm = { params ->
                viewModel.onEvent(SettingsEvent.UpdateImageCropParams(uri, params))
                showCropUri = null
            },
            onDismiss = { showCropUri = null }
        )
    }
    
    // 部分权限访问提示对话框
    if (showPermissionDialog) {
        PartialAccessPermissionDialog(
            onSelectMorePhotos = {
                showPermissionDialog = false
                // 使用 Photo Picker 让用户选择更多照片
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAllowFullAccess = {
                showPermissionDialog = false
                // 打开应用设置让用户手动授予完整权限
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onUseDocumentPicker = {
                showPermissionDialog = false
                // 使用文档选择器，可以访问所有文件
                documentPicker.launch(arrayOf("image/*"))
            },
            onDismiss = { showPermissionDialog = false }
        )
    }
}

/**
 * 顶部栏
 */
@Composable
private fun TopBar(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    isReorderEnabled: Boolean,
    onSettingsClick: () -> Unit,
    onReorderClick: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            // 多选模式顶部栏
            // 关闭按钮（简洁样式，无背景）
            IconButton(
                onClick = onExitMultiSelect,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 选中数量
            Text(
                text = stringResource(R.string.multi_select_count, selectedCount),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 全选按钮
            TextButton(
                onClick = onSelectAll,
                enabled = selectedCount < totalCount
            ) {
                Text(
                    text = stringResource(R.string.select_all),
                    color = if (selectedCount < totalCount) Teal300 else TextSecondary
                )
            }
            
            // 删除选中按钮
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (selectedCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = if (selectedCount > 0) MaterialTheme.colorScheme.error else TextSecondary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_selected),
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // 普通模式顶部栏
            // Logo 和标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 应用图标
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.app_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onReorderClick,
                enabled = isReorderEnabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = if (isReorderEnabled) Teal300 else TextSecondary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Reorder,
                    contentDescription = stringResource(R.string.reorder),
                    modifier = Modifier.size(24.dp)
                )
            }

            // 设置按钮
            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Teal300
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 瀑布流图片网格
 */
@Composable
private fun StaggeredPhotoGrid(
    imageUris: List<String>,
    imageCropParams: Map<String, ImageCropParams>,
    isMultiSelectMode: Boolean,
    selectedUris: Set<String>,
    onImageClick: (Int) -> Unit,
    onImageLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 100.dp // 为添加按钮留空间
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = modifier
    ) {
        itemsIndexed(
            items = imageUris,
            key = { _, uri -> uri }
        ) { index, uri ->
            PhotoCard(
                uri = uri,
                hasCropParams = imageCropParams.containsKey(uri),
                isMultiSelectMode = isMultiSelectMode,
                isSelected = selectedUris.contains(uri),
                onClick = { onImageClick(index) },
                onLongPress = { onImageLongPress(uri) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderImagesSheet(
    imageUris: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (imageUris.size < 2) {
        onDismiss()
        return
    }

    var reorderedUris by remember(imageUris) { mutableStateOf(imageUris) }
    var hasChanges by remember(imageUris) { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            reorderedUris = reorderedUris.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            hasChanges = true
        }
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasModifiedOrder = hasChanges && reorderedUris != imageUris

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.reorder_images),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reorder_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = reorderState.listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .reorderable(reorderState)
            ) {
                itemsIndexed(reorderedUris, key = { _, uri -> uri }) { index, uri ->
                    ReorderableItem(state = reorderState, key = uri) { isDragging ->
                        ReorderRow(
                            index = index,
                            uri = uri,
                            isDragging = isDragging,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .detectReorderAfterLongPress(reorderState)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (hasModifiedOrder) {
                            onConfirm(reorderedUris)
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal300)
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            }

            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@Composable
private fun ReorderRow(
    index: Int,
    uri: String,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(400, 400))
            .crossfade(true)
            .build()
    )
    val backgroundColor = if (isDragging) MintGreen100 else Color.White

    Row(
        modifier = modifier
            .shadow(if (isDragging) 8.dp else 2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.reorder_item_label, index + 1),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = Uri.parse(uri).lastPathSegment ?: uri,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.Reorder,
            contentDescription = null,
            tint = Teal300,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun applyLanguage(languageTag: String?) {
    val tag = when {
        languageTag.isNullOrBlank() -> Locale.getDefault().language
        else -> languageTag
    }
    val localeList = LocaleListCompat.forLanguageTags(tag)
    AppCompatDelegate.setApplicationLocales(localeList)
}

/**
 * 单个图片卡片 - 按原比例显示
 * @param uri 图片 URI
 * @param hasCropParams 是否设置了自定义裁剪参数
 * @param isMultiSelectMode 是否处于多选模式
 * @param isSelected 是否被选中
 * @param onClick 点击回调
 * @param onLongPress 长按回调
 */
@Composable
private fun PhotoCard(
    uri: String,
    hasCropParams: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    
    // 使用 ImageRequest 限制图片尺寸，防止超大图片导致 Canvas 崩溃
    // 瀑布流是2列，每列宽度约为屏幕宽度的一半，这里使用 800x1600 作为最大尺寸
    val imageRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(800, 1600)) // 限制最大尺寸，Coil 会自动下采样
            .crossfade(true)
            .placeholder(android.R.drawable.ic_menu_gallery) // 占位符
            .error(android.R.drawable.ic_menu_report_image) // 错误占位符
            .build()
    }
    
    val painter = rememberAsyncImagePainter(imageRequest)
    val painterState = painter.state
    
    // 根据图片实际尺寸计算宽高比 - 使用 derivedStateOf 优化重组性能
    val aspectRatio = remember {
        derivedStateOf {
            when (val state = painterState) {
                is AsyncImagePainter.State.Success -> {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) {
                        size.width / size.height
                    } else {
                        0.75f // 默认 3:4
                    }
                }
                else -> 0.75f // 加载中或失败时使用默认比例
            }
        }
    }.value
    
    // 边框颜色：多选模式且选中时显示选中边框，否则显示裁剪参数边框
    val borderModifier = when {
        isMultiSelectMode && isSelected -> Modifier.border(3.dp, Teal300, RoundedCornerShape(16.dp))
        hasCropParams && !isMultiSelectMode -> Modifier.border(3.dp, Teal300, RoundedCornerShape(16.dp))
        else -> Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f)) // 限制比例范围
            .then(borderModifier)
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 多选模式下的选中遮罩和指示器
            if (isMultiSelectMode) {
                // 半透明遮罩（选中时）
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }
                
                // 选择指示器（右上角）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(
                            color = if (isSelected) Teal300 else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                        .then(
                            if (!isSelected) {
                                Modifier.border(2.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 添加图片按钮 - 圆形，图标在上文本在下
 */
@Composable
private fun AddImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Teal300,
        shadowElevation = 8.dp,
        modifier = modifier.size(80.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = stringResource(R.string.add_image),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

/**
 * 底部操作栏
 */
@Composable
private fun BottomActionBar(
    onSetWallpaperClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onSetWallpaperClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp
            )
        ) {
            Text(
                text = stringResource(R.string.set_live_wallpaper),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📷",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.no_images),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.tap_to_add),
            fontSize = 14.sp,
            color = TextPrimary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 删除确认对话框
 * @param title 对话框标题
 * @param message 对话框消息
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 */
@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_image),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * 部分权限访问提示对话框
 * Android 14+ 用户可能只授予部分照片访问权限
 * 美化版本：从底部弹出的 ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartialAccessPermissionDialog(
    onSelectMorePhotos: () -> Unit,
    onAllowFullAccess: () -> Unit,
    onUseDocumentPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            // 自定义拖拽手柄
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部装饰圆形
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Teal200, Teal400)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📷",
                    fontSize = 32.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 标题
            Text(
                text = stringResource(R.string.permission_limited_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 描述文字
            Text(
                text = stringResource(R.string.permission_limited_message),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // 选项按钮列表
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 选择更多照片 - 主要操作
                Button(
                    onClick = onSelectMorePhotos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal300
                    )
                ) {
                    Text(
                        text = stringResource(R.string.permission_select_more),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // 允许完整访问 - 次要操作
                OutlinedButton(
                    onClick = onAllowFullAccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Teal400
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal300.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stringResource(R.string.permission_full_access),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // 使用文件选择器 - 替代方案
                OutlinedButton(
                    onClick = onUseDocumentPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Teal400
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal300.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stringResource(R.string.permission_use_file_picker),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 继续使用按钮 - 文字按钮
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.permission_continue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            // 底部安全边距
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

/**
 * 背景爪印装饰
 */
@Composable
private fun PawPrintDecorations() {
    val decorations = remember {
        listOf(
            Triple(-20.dp, 100.dp, 15f),
            Triple(300.dp, 80.dp, -20f),
            Triple(50.dp, 250.dp, 30f),
            Triple(280.dp, 400.dp, -10f),
            Triple(-10.dp, 500.dp, 25f),
            Triple(320.dp, 600.dp, -15f),
        )
    }

    decorations.forEach { (x, y, rotation) ->
        Text(
            text = "🐾",
            fontSize = 32.sp,
            modifier = Modifier
                .offset(x = x, y = y)
                .rotate(rotation),
            color = MintGreen300.copy(alpha = 0.3f)
        )
    }
}
