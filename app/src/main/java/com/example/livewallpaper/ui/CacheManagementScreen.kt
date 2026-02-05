package com.example.livewallpaper.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.livewallpaper.R
import java.io.File
import java.text.DecimalFormat

/**
 * 缓存图片数据模型
 */
data class CachedImage(
    val file: File,
    val size: Long,
    val lastModified: Long
)

/**
 * 排序方式
 */
enum class CacheSortMode {
    BY_SIZE,
    BY_TIME
}

/**
 * 缓存管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // 状态
    var isLoading by remember { mutableStateOf(true) }
    var cachedImages by remember { mutableStateOf<List<CachedImage>>(emptyList()) }
    var selectedImages by remember { mutableStateOf<Set<File>>(emptySet()) }
    var sortMode by remember { mutableStateOf(CacheSortMode.BY_SIZE) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // 计算选中的总大小
    val selectedSize = remember(selectedImages) {
        selectedImages.sumOf { file -> cachedImages.find { it.file == file }?.size ?: 0L }
    }
    
    // 计算总缓存大小
    val totalSize = remember(cachedImages) {
        cachedImages.sumOf { it.size }
    }
    
    // 排序后的图片列表
    val sortedImages = remember(cachedImages, sortMode) {
        when (sortMode) {
            CacheSortMode.BY_SIZE -> cachedImages.sortedByDescending { it.size }
            CacheSortMode.BY_TIME -> cachedImages.sortedByDescending { it.lastModified }
        }
    }
    
    // 加载缓存图片
    LaunchedEffect(Unit) {
        isLoading = true
        val images = mutableListOf<CachedImage>()
        
        // 扫描 Coil 图片缓存目录
        val coilCacheDir = context.cacheDir.resolve("image_cache")
        if (coilCacheDir.exists() && coilCacheDir.isDirectory) {
            coilCacheDir.walkTopDown().forEach { file ->
                if (file.isFile && isImageFile(file)) {
                    images.add(
                        CachedImage(
                            file = file,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        }
        
        // 扫描 AI 绘画生成的图片目录
        val aiPaintDir = File(context.filesDir, "aipaint")
        if (aiPaintDir.exists() && aiPaintDir.isDirectory) {
            aiPaintDir.walkTopDown().forEach { file ->
                if (file.isFile && isAiPaintImage(file)) {
                    images.add(
                        CachedImage(
                            file = file,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        }
        
        cachedImages = images
        isLoading = false
    }
    
    // 删除选中的缓存
    fun deleteSelected() {
        val toDelete = selectedImages.toList()
        var deletedCount = 0
        
        toDelete.forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }
        
        // 更新列表
        cachedImages = cachedImages.filter { it.file !in selectedImages }
        selectedImages = emptySet()
        
        Toast.makeText(
            context,
            context.getString(R.string.cache_deleted_success, deletedCount),
            Toast.LENGTH_SHORT
        ).show()
    }
    
    BackHandler { onBack() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.cache_management), 
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    // 加载中
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.cache_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                cachedImages.isEmpty() -> {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cache_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    // 统计信息
                    Text(
                        text = stringResource(R.string.cache_image_count, cachedImages.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // 图片网格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(sortedImages, key = { it.file.absolutePath }) { cachedImage ->
                            CacheImageItem(
                                cachedImage = cachedImage,
                                isSelected = cachedImage.file in selectedImages,
                                onClick = {
                                    selectedImages = if (cachedImage.file in selectedImages) {
                                        selectedImages - cachedImage.file
                                    } else {
                                        selectedImages + cachedImage.file
                                    }
                                }
                            )
                        }
                    }
                    
                    // 底部操作栏
                    CacheBottomBar(
                        selectedCount = selectedImages.size,
                        selectedSize = selectedSize,
                        totalCount = cachedImages.size,
                        isAllSelected = selectedImages.size == cachedImages.size && cachedImages.isNotEmpty(),
                        sortMode = sortMode,
                        onSelectAll = {
                            selectedImages = if (selectedImages.size == cachedImages.size) {
                                emptySet()
                            } else {
                                cachedImages.map { it.file }.toSet()
                            }
                        },
                        onSortClick = { showSortSheet = true },
                        onDeleteClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }
    
    // 排序选择底部弹窗
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                SortOption(
                    text = stringResource(R.string.cache_sort_by_size),
                    isSelected = sortMode == CacheSortMode.BY_SIZE,
                    onClick = {
                        sortMode = CacheSortMode.BY_SIZE
                        showSortSheet = false
                    }
                )
                SortOption(
                    text = stringResource(R.string.cache_sort_by_time),
                    isSelected = sortMode == CacheSortMode.BY_TIME,
                    onClick = {
                        sortMode = CacheSortMode.BY_TIME
                        showSortSheet = false
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { showSortSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirm && selectedImages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.cache_delete_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.cache_delete_confirm_message,
                        selectedImages.size,
                        formatFileSize(selectedSize)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteSelected()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun CacheImageItem(
    cachedImage: CachedImage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        // 图片
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(cachedImage.file)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // 选中遮罩
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }
        
        // 选择圆圈
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(24.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // 文件大小标签
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatFileSize(cachedImage.size),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CacheBottomBar(
    selectedCount: Int,
    selectedSize: Long,
    totalCount: Int,
    isAllSelected: Boolean,
    sortMode: CacheSortMode,
    onSelectAll: () -> Unit,
    onSortClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 选中信息
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.cache_selected_size, formatFileSize(selectedSize)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 全选按钮
            TextButton(onClick = onSelectAll) {
                Text(
                    text = if (isAllSelected) {
                        stringResource(R.string.cancel_select_all)
                    } else {
                        stringResource(R.string.select_all)
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // 排序按钮
            TextButton(onClick = onSortClick) {
                Text(
                    text = when (sortMode) {
                        CacheSortMode.BY_SIZE -> stringResource(R.string.cache_sort_by_size)
                        CacheSortMode.BY_TIME -> stringResource(R.string.cache_sort_by_time)
                    } + " ▼",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 删除按钮
            TextButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0
            ) {
                Text(
                    text = stringResource(R.string.cache_delete_selected),
                    color = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }
    }
}

@Composable
private fun SortOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 判断文件是否为 Coil 缓存的图片
 */
private fun isImageFile(file: File): Boolean {
    val name = file.name.lowercase()
    // Coil 缓存文件通常没有扩展名，所以我们检查文件大小和是否可读
    // 排除明显的非图片文件（如 journal 文件）
    if (name.contains("journal") || name.endsWith(".tmp")) {
        return false
    }
    // 检查文件大小（图片通常大于 1KB）
    return file.length() > 1024
}

/**
 * 判断文件是否为 AI 绘画生成的图片
 */
private fun isAiPaintImage(file: File): Boolean {
    val name = file.name.lowercase()
    // AI 绘画图片是 PNG 格式
    return name.endsWith(".png") && file.length() > 1024
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        size >= 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        size >= 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))} MB"
        size >= 1024 -> "${df.format(size / 1024.0)} KB"
        else -> "$size B"
    }
}
