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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.foundation.clickable
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
import com.example.livewallpaper.ui.components.LiquidGlassButton
import com.example.livewallpaper.ui.theme.MintGreen100
import com.example.livewallpaper.ui.theme.MintGreen200
import com.example.livewallpaper.ui.theme.MintGreen300
import com.example.livewallpaper.ui.theme.TextPrimary
import com.example.livewallpaper.ui.theme.TextSecondary
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.ui.LanguageOption
import org.koin.androidx.compose.koinViewModel
import com.example.livewallpaper.ui.components.ImagePreviewDialog
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.TextButton

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

    // Activity Result Launcher for Settings
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                data.getLongExtra("interval", -1L).takeIf { it != -1L }?.let { interval ->
                    viewModel.onEvent(SettingsEvent.UpdateInterval(interval))
                }
                data.getStringExtra("scaleMode")?.let { scaleMode ->
                    viewModel.onEvent(SettingsEvent.UpdateScaleMode(
                        com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode.valueOf(scaleMode)
                    ))
                }
                data.getStringExtra("playMode")?.let { playMode ->
                    viewModel.onEvent(SettingsEvent.UpdatePlayMode(
                        com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode.valueOf(playMode)
                    ))
                }
                data.getStringExtra("languageTag")?.let { languageTag ->
                    viewModel.onEvent(SettingsEvent.UpdateLanguage(languageTag))
                }
                data.getStringExtra("themeMode")?.let { themeMode ->
                    viewModel.onEvent(SettingsEvent.UpdateThemeMode(
                        com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode.valueOf(themeMode)
                    ))
                }
            }
        }
    }

    // 对话框状态
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

    // 监听语言变化并应用，applyLanguage 内部会检查是否需要真正切换
    LaunchedEffect(state.config.languageTag) {
        applyLanguage(state.config.languageTag)
    }

    // 最外层 Box，用于叠加图库界面
    Box(modifier = Modifier.fillMaxSize()) {
        // 主界面内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 顶部栏 - 增加顶部 StatusBars 的 padding
                TopBar(
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCount = selectedUris.size,
                    totalCount = state.config.imageUris.size,
                    isReorderEnabled = state.config.imageUris.size > 1,
                    onSettingsClick = {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            putExtra("interval", state.config.interval)
                            putExtra("scaleMode", state.config.scaleMode.name)
                            putExtra("playMode", state.config.playMode.name)
                            putExtra("languageTag", state.config.languageTag)
                            putExtra("themeMode", state.config.themeMode.name)
                        }
                        settingsLauncher.launch(intent)
                    },
                    onReorderClick = { showReorderSheet = true },
                    onDrawClick = {
                        val intent = Intent(context, com.example.livewallpaper.paint.PaintActivity::class.java).apply {
                            putExtra("themeMode", state.config.themeMode.name)
                        }
                        context.startActivity(intent)
                    },
                    onExitMultiSelect = {
                        isMultiSelectMode = false
                        selectedUris = emptySet()
                    },
                    onSelectAll = {
                        selectedUris = state.config.imageUris.toSet()
                    },
                    onDeselectAll = {
                        selectedUris = emptySet()
                    },
                    onDeleteSelected = {
                        showDeleteSelectedDialog = true
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
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
                }
            }

            // 悬浮在底部的操作区域 (添加按钮 + 设置壁纸按钮)
            // 使用 Box 组合，并放置在底部中间
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                 if (state.config.imageUris.isNotEmpty()) {
                     FloatingBottomBar(
                         onAddClick = openImagePicker,
                         onSetWallpaperClick = {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            intent.putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, LiveWallpaperService::class.java)
                            )
                            context.startActivity(intent)
                         }
                     )
                 } else {
                     // 空状态下只显示添加按钮，大一点
                     AddImageButton(
                        onClick = openImagePicker,
                        modifier = Modifier.align(Alignment.BottomCenter)
                     )
                 }
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
            imagePaths = state.config.imageUris,
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
    onDrawClick: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            // 多选模式顶部栏 - 保持功能，优化样式
            IconButton(
                onClick = onExitMultiSelect,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "$selectedCount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            val isAllSelected = selectedCount == totalCount && totalCount > 0
            
            TextButton(
                onClick = {
                    if (isAllSelected) {
                        onDeselectAll()
                    } else {
                        onSelectAll()
                    }
                },
                enabled = true
            ) {
                Text(
                    text = if (isAllSelected) stringResource(R.string.cancel_select_all) else stringResource(R.string.select_all),
                    fontWeight = FontWeight.Medium
                )
            }
            
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_selected)
                )
            }
        } else {
            // 普通模式顶部栏 - 极简风格
            // 左侧：App 标题
            Text(
                text = stringResource(R.string.app_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            // 右侧：功能按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 绘图
                SmallIconBtn(
                    icon = Icons.Default.Brush,
                    contentDescription = stringResource(R.string.draw),
                    onClick = onDrawClick
                )

                // 排序
                if (isReorderEnabled) {
                    SmallIconBtn(
                        icon = Icons.Default.Reorder,
                        contentDescription = stringResource(R.string.reorder),
                        onClick = onReorderClick
                    )
                }

                // 设置
                SmallIconBtn(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun SmallIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp), // 图标稍微放大一点
            tint = MaterialTheme.colorScheme.onSurface
        )
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
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderedUris = reorderedUris.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        hasChanges = true
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasModifiedOrder = hasChanges && reorderedUris != imageUris
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.reorder_images),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reorder_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
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
                                .longPressDraggableHandle()
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            .size(Size(200, 200))  // 缩小尺寸，排序列表不需要太大
            .allowRgb565(true)
            .crossfade(true)
            .build()
    )
    val backgroundColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Uri.parse(uri).lastPathSegment ?: uri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.Reorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun applyLanguage(languageTag: String?) {
    val tag = when {
        languageTag.isNullOrBlank() -> Locale.getDefault().language
        else -> languageTag
    }
    
    // 检查当前应用语言是否已经是目标语言，避免重复设置导致 Activity 重建
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) {
        Locale.getDefault().language
    } else {
        currentLocales[0]?.language ?: Locale.getDefault().language
    }
    
    // 只有当语言不同时才应用，避免不必要的 Activity 重建
    if (currentTag != tag) {
        val localeList = LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

/**
 * 单个图片卡片 - 纯净样式
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
    
    // 使用 ImageRequest 限制图片尺寸，启用 RGB_565 减少内存占用
    val imageRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(600, 1200))  // 缩略图尺寸，足够瀑布流显示
            .allowRgb565(true)      // 使用 RGB_565 格式，内存减半
            .crossfade(true)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .build()
    }
    
    val painter = rememberAsyncImagePainter(imageRequest)
    val painterState = painter.state
    
    // 比例计算
    val aspectRatio = remember {
        derivedStateOf {
            when (val state = painterState) {
                is AsyncImagePainter.State.Success -> {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) size.width / size.height else 0.75f
                }
                else -> 0.75f
            }
        }
    }.value
    
    // 选中状态边框
    val borderColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (isMultiSelectMode && isSelected) 4.dp else 0.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(16.dp), // 加大圆角
        border = if (borderWidth > 0.dp) androidx.compose.foundation.BorderStroke(borderWidth, borderColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // 使用变体色作为底色
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 选中遮罩
            if (isMultiSelectMode && isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
                
                // 选中图标
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                )
            }
            
            // 如果有裁剪参数，显示一个小标记
            if (hasCropParams && !isMultiSelectMode) {
                 Icon(
                    imageVector = Icons.Default.CheckCircle, // 或者换成 Crop 图标
                    contentDescription = "Cropped",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .padding(2.dp)
                )
            }
        }
    }
}

/**
 * 悬浮底部操作栏
 */
@Composable
private fun FloatingBottomBar(
    onAddClick: () -> Unit,
    onSetWallpaperClick: () -> Unit
) {
    // 玻璃质感容器
    Box(
        modifier = Modifier
            .height(64.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color.Black.copy(alpha = 0.2f),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(32.dp))
            // 1. 半透明背景 (模拟磨砂)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            )
            // 2. 玻璃高光边框
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f), // 上部亮
                        Color.White.copy(alpha = 0.1f)  // 下部暗
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
    ) {
        // 3. 额外的玻璃反射层
        Box(
            modifier = Modifier
                .matchParentSize() // 使用 matchParentSize 避免撑大父容器
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(200f, 200f)
                    )
                )
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 添加按钮
            LiquidGlassButton(
                onClick = onAddClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }

            // 设置壁纸按钮
            LiquidGlassButton(
                onClick = onSetWallpaperClick,
                modifier = Modifier
                    .height(48.dp)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.set_live_wallpaper),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 添加图片大按钮 (空状态用)
 */
@Composable
private fun AddImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier
            .padding(bottom = 32.dp)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.add_image),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 移除旧的 BottomActionBar 定义，保留 EmptyState 和 Dialogs


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
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.tap_to_add),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.primary
                            )
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
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 描述文字
            Text(
                text = stringResource(R.string.permission_limited_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        containerColor = MaterialTheme.colorScheme.primary
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
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 底部安全边距
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

