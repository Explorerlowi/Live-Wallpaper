package com.example.livewallpaper.gallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.example.livewallpaper.R
import com.example.livewallpaper.ui.components.ImagePreviewDialogFromUris
import com.example.livewallpaper.gallery.model.Album
import com.example.livewallpaper.gallery.model.MediaItem
import com.example.livewallpaper.gallery.viewmodel.GalleryPage
import com.example.livewallpaper.gallery.viewmodel.GalleryViewModel
import com.example.livewallpaper.ui.theme.TextPrimary

// 图库背景色（纯白色，不透明）
private val GalleryBackgroundColor = Color(0xFFF5F5F5)

/**
 * 图库浏览器主界面（使用 Dialog 确保正确的层级）
 * @param viewModel 图库 ViewModel
 * @param onImagesSelected 选择完成回调，返回选中的图片 URI 列表
 * @param onDismiss 关闭图库回调
 */
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onImagesSelected: (List<Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    
    // 预览对话框状态
    var showPreview by remember { mutableStateOf(false) }
    
    // 单张图片预览索引（点击放大图标时使用）
    var previewImageIndex by remember { mutableStateOf<Int?>(null) }
    
    // 相册列表滚动状态 - 使用 ViewModel 中保存的位置初始化
    val albumListState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.albumListScrollIndex,
        initialFirstVisibleItemScrollOffset = uiState.albumListScrollOffset
    )

    // 处理返回键
    BackHandler(enabled = true) {
        when {
            previewImageIndex != null -> previewImageIndex = null
            showPreview -> showPreview = false
            currentPage is GalleryPage.AlbumList -> onDismiss()
            currentPage is GalleryPage.ImageGrid -> viewModel.backToAlbums()
        }
    }

    // 使用 Dialog 确保层级高于其他内容
    Dialog(
        onDismissRequest = {
            when (currentPage) {
                is GalleryPage.AlbumList -> onDismiss()
                is GalleryPage.ImageGrid -> viewModel.backToAlbums()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            color = GalleryBackgroundColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                GalleryTopBar(
                    currentPage = currentPage,
                    selectedCount = uiState.selectedImages.size,
                    onBack = {
                        when (currentPage) {
                            is GalleryPage.AlbumList -> onDismiss()
                            is GalleryPage.ImageGrid -> viewModel.backToAlbums()
                        }
                    },
                    onSelectAll = { viewModel.selectAll() },
                    onClearSelection = { viewModel.clearSelection() }
                )

                // 内容区域
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState is GalleryPage.ImageGrid) {
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        } else {
                            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "page_transition"
                ) { page ->
                    when (page) {
                        is GalleryPage.AlbumList -> {
                            AlbumListContent(
                                allImagesAlbum = uiState.allImagesAlbum,
                                albums = uiState.albums,
                                error = uiState.error,
                                listState = albumListState,
                                onAlbumClick = { album ->
                                    // 保存当前滚动位置后再打开相册
                                    viewModel.saveAlbumListScrollPosition(
                                        albumListState.firstVisibleItemIndex,
                                        albumListState.firstVisibleItemScrollOffset
                                    )
                                    viewModel.openAlbum(album)
                                },
                                onRetry = { viewModel.loadAlbums() }
                            )
                        }
                        is GalleryPage.ImageGrid -> {
                            ImageGridContent(
                                images = uiState.images,
                                selectedImages = uiState.selectedImages,
                                onImageClick = { viewModel.toggleImageSelection(it) },
                                onPreviewImage = { index -> previewImageIndex = index }
                            )
                        }
                    }
                }

                // 底部确认按钮（仅在有选中图片时显示）
                if (uiState.selectedImages.isNotEmpty()) {
                    ConfirmSelectionBar(
                        selectedCount = uiState.selectedImages.size,
                        onPreview = { showPreview = true },
                        onConfirm = {
                            val selected = viewModel.confirmSelection()
                            onImagesSelected(selected)
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }
        }
    }
    
    // 预览对话框（预览所有选中图片）
    if (showPreview && uiState.selectedImages.isNotEmpty()) {
        ImagePreviewDialogFromUris(
            imageUris = uiState.selectedImages.toList(),
            initialIndex = 0,
            onDismiss = { showPreview = false }
        )
    }
    
    // 单张图片预览对话框（点击放大图标触发）
    previewImageIndex?.let { index ->
        val selectedList = uiState.selectedImages.toList()
        if (index in selectedList.indices) {
            ImagePreviewDialogFromUris(
                imageUris = selectedList,
                initialIndex = index,
                onDismiss = { previewImageIndex = null }
            )
        }
    }
}

/**
 * 图库顶部栏
 */
@Composable
private fun GalleryTopBar(
    currentPage: GalleryPage,
    selectedCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回/关闭按钮
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = when (currentPage) {
                        is GalleryPage.AlbumList -> Icons.Default.Close
                        is GalleryPage.ImageGrid -> Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = stringResource(R.string.close),
                    tint = TextPrimary
                )
            }

            // 标题
            Text(
                text = when (currentPage) {
                    is GalleryPage.AlbumList -> stringResource(R.string.gallery_select_album)
                    is GalleryPage.ImageGrid -> currentPage.album.name
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            // 选中数量
            if (selectedCount > 0) {
                Text(
                    text = stringResource(R.string.gallery_selected_count, selectedCount),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 全选/取消全选按钮（仅在图片网格页面显示）
            if (currentPage is GalleryPage.ImageGrid) {
                TextButton(
                    onClick = if (selectedCount > 0) onClearSelection else onSelectAll
                ) {
                    Text(
                        text = if (selectedCount > 0) {
                            stringResource(R.string.gallery_clear_selection)
                        } else {
                            stringResource(R.string.gallery_select_all)
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 相册列表内容
 * @param allImagesAlbum "所有图片"虚拟相册
 * @param albums 相册列表
 * @param error 错误信息
 * @param listState 列表滚动状态，用于保持滚动位置
 * @param onAlbumClick 相册点击回调
 * @param onRetry 重试回调
 */
@Composable
private fun AlbumListContent(
    allImagesAlbum: Album?,
    albums: List<Album>,
    error: String?,
    listState: LazyListState,
    onAlbumClick: (Album) -> Unit,
    onRetry: () -> Unit
) {
    when {
        error != null -> {
            ErrorContent(error = error, onRetry = onRetry)
        }
        albums.isEmpty() && allImagesAlbum == null -> {
            EmptyContent(message = stringResource(R.string.gallery_no_albums))
        }
        else -> {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "所有图片"相册
                allImagesAlbum?.let { album ->
                    item(key = "all_images") {
                        AlbumItem(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }

                // 其他相册
                items(
                    items = albums,
                    key = { it.id }
                ) { album ->
                    AlbumItem(
                        album = album,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }
    }
}

/**
 * 相册项
 */
@Composable
private fun AlbumItem(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图 - 限制尺寸防止超大图片崩溃
            val context = LocalContext.current
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(album.coverUri)
                        .size(Size(200, 200))
                        .crossfade(true)
                        .build()
                ),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 相册信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.gallery_photo_count, album.count),
                    fontSize = 14.sp,
                    color = TextPrimary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 图片网格内容
 */
@Composable
private fun ImageGridContent(
    images: List<MediaItem>,
    selectedImages: Set<Uri>,
    onImageClick: (Uri) -> Unit,
    onPreviewImage: (Int) -> Unit
) {
    // 预计算选中图片的索引映射
    val selectedList = selectedImages.toList()
    
    if (images.isEmpty()) {
        EmptyContent(message = stringResource(R.string.gallery_no_images))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = images,
                key = { it.id }
            ) { image ->
                val isSelected = selectedImages.contains(image.uri)
                // 计算当前图片在选中列表中的索引
                val selectedIndex = if (isSelected) selectedList.indexOf(image.uri) else -1
                
                ImageGridItem(
                    image = image,
                    isSelected = isSelected,
                    selectedIndex = if (isSelected && selectedIndex >= 0) selectedIndex + 1 else null,
                    onClick = { onImageClick(image.uri) },
                    onPreviewClick = if (isSelected && selectedIndex >= 0) {
                        { onPreviewImage(selectedIndex) }
                    } else null
                )
            }
        }
    }
}

/**
 * 图片网格项
 * @param image 图片信息
 * @param isSelected 是否被选中
 * @param selectedIndex 选中序号（从1开始，未选中时为null）
 * @param onClick 点击回调（切换选中状态）
 * @param onPreviewClick 预览点击回调（仅选中时有效）
 */
@Composable
private fun ImageGridItem(
    image: MediaItem,
    isSelected: Boolean,
    selectedIndex: Int?,
    onClick: () -> Unit,
    onPreviewClick: (() -> Unit)?
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, primaryColor, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
    ) {
        // 缩略图 - 限制尺寸防止超大图片崩溃
        val context = LocalContext.current
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .size(Size(400, 400))
                    .crossfade(true)
                    .build()
            ),
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 选中指示器和放大预览图标
        if (isSelected) {
            // 半透明遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
            
            // 左上角放大预览图标
            if (onPreviewClick != null) {
                Surface(
                    onClick = onPreviewClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = stringResource(R.string.gallery_preview_image),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // 右上角选中序号
            if (selectedIndex != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedIndex.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 确认选择栏
 */
@Composable
private fun ConfirmSelectionBar(
    selectedCount: Int,
    onPreview: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.gallery_selected_count, selectedCount),
                fontSize = 16.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            // 预览按钮
            TextButton(onClick = onPreview) {
                Text(
                    text = stringResource(R.string.gallery_preview),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // 确认按钮
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.gallery_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "😢",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            fontSize = 16.sp,
            color = TextPrimary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.gallery_retry))
        }
    }
}

/**
 * 空内容提示
 */
@Composable
private fun EmptyContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 16.sp,
            color = TextPrimary.copy(alpha = 0.7f)
        )
    }
}
