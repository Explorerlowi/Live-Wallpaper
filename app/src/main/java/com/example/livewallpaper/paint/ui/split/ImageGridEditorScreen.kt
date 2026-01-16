package com.example.livewallpaper.paint.ui.split

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.livewallpaper.R
import com.example.livewallpaper.gallery.data.MediaStoreRepository
import com.example.livewallpaper.gallery.ui.GalleryScreen
import com.example.livewallpaper.gallery.viewmodel.GalleryViewModel
import com.example.livewallpaper.ui.components.AppDropdownMenu
import com.example.livewallpaper.ui.components.AppMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

/**
 * 图片网格编辑器屏幕
 * 
 * @param imagePath 图片路径
 * @param onDismiss 关闭回调
 * @param onSplitComplete 切分完成回调，传递切分后的图块列表和网格配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridEditorScreen(
    imagePath: String,
    onDismiss: () -> Unit,
    onSplitComplete: (List<SplitTile>, Bitmap, GridConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 当前正在编辑的图片路径（支持“替换图片”）
    var currentImagePath by remember(imagePath) { mutableStateOf(imagePath) }
    
    // 加载源图片
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentImagePath) {
        sourceBitmap = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(currentImagePath)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // 网格配置状态
    var gridConfig by remember { mutableStateOf(GridConfig().withEvenDividers()) }
    var undoStack by remember { mutableStateOf<List<GridConfig>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<GridConfig>>(emptyList()) }
    
    // 保存当前状态到 undo 栈
    fun saveToUndo() {
        undoStack = undoStack + gridConfig
        redoStack = emptyList()
    }
    
    // Undo
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + gridConfig
            gridConfig = undoStack.last()
            undoStack = undoStack.dropLast(1)
        }
    }
    
    // Redo
    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + gridConfig
            gridConfig = redoStack.last()
            redoStack = redoStack.dropLast(1)
        }
    }
    
    // Reset - 只重置分割线位置，保留行列数
    fun resetGrid() {
        saveToUndo()
        gridConfig = gridConfig.withEvenDividers()
    }

    // 复用绘画输入框同款的自定义图库（GalleryScreen）
    val mediaStoreRepository: MediaStoreRepository = koinInject()
    val galleryViewModel = remember { GalleryViewModel(mediaStoreRepository) }
    var showGallery by remember { mutableStateOf(false) }

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

    // 打开自定义图库（与 PaintScreen 行为保持一致）
    val openImagePicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionStatus = checkPhotoPermissionStatus(context)
            val (fullAccess, _) = permissionStatus
            if (fullAccess) {
                galleryViewModel.loadAlbums()
                showGallery = true
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
    
    // 切分图片
    fun splitImage() {
        val bitmap = sourceBitmap ?: return
        val tiles = mutableListOf<SplitTile>()
        
        val cropBounds = gridConfig.cropBounds
        val cropX = (cropBounds.left * bitmap.width).toInt()
        val cropY = (cropBounds.top * bitmap.height).toInt()
        val cropWidth = ((cropBounds.right - cropBounds.left) * bitmap.width).toInt()
        val cropHeight = ((cropBounds.bottom - cropBounds.top) * bitmap.height).toInt()
        
        // 计算每个格子的边界
        // 注意：交互编辑时允许分割线“越过/重叠”其它线（顺序可能临时乱序，甚至重叠）
        // 切分计算阶段再统一排序 + 最小间距修正，避免出现 0 宽/0 高切片导致 createBitmap 失败被跳过。
        val rowPositions = listOf(0f) + sanitizeSortedDividers(
            sortedDividers = gridConfig.rowDividers.sorted(),
            minGapFraction = 2f / cropHeight.coerceAtLeast(1).toFloat() // 约 2px
        ) + listOf(1f)
        val colPositions = listOf(0f) + sanitizeSortedDividers(
            sortedDividers = gridConfig.colDividers.sorted(),
            minGapFraction = 2f / cropWidth.coerceAtLeast(1).toFloat() // 约 2px
        ) + listOf(1f)
        
        var index = 0
        for (row in 0 until gridConfig.rows) {
            for (col in 0 until gridConfig.columns) {
                val left = (colPositions[col] * cropWidth).toInt()
                val top = (rowPositions[row] * cropHeight).toInt()
                val right = (colPositions[col + 1] * cropWidth).toInt()
                val bottom = (rowPositions[row + 1] * cropHeight).toInt()
                
                val tileWidth = (right - left).coerceAtLeast(1)
                val tileHeight = (bottom - top).coerceAtLeast(1)
                
                try {
                    val tileBitmap = Bitmap.createBitmap(
                        bitmap,
                        (cropX + left).coerceIn(0, bitmap.width - 1),
                        (cropY + top).coerceIn(0, bitmap.height - 1),
                        tileWidth.coerceAtMost(bitmap.width - cropX - left),
                        tileHeight.coerceAtMost(bitmap.height - cropY - top)
                    )
                    
                    tiles.add(
                        SplitTile(
                            index = index,
                            row = row,
                            column = col,
                            bitmap = tileBitmap,
                            fileName = "Tile_${String.format("%02d", index + 1)}"
                        )
                    )
                } catch (e: Exception) {
                    // 跳过无效的切片
                }
                index++
            }
        }
        
        onSplitComplete(tiles, bitmap, gridConfig)
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            GridEditorTopBar(
                onBack = onDismiss,
                onPickImage = { openImagePicker() }
            )

            // 中间可滚动区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 图片预览区（只读展示，点击后全屏编辑）
                var showGridEditDialog by remember { mutableStateOf(false) }
                if (sourceBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGridEditDialog = true }
                    ) {
                        GridPreviewArea(
                            bitmap = sourceBitmap!!,
                            gridConfig = gridConfig,
                            interactive = false,
                            onConfigChange = { /* read-only */ }
                        )
                    }

                    if (showGridEditDialog) {
                        GridEditFullScreenDialog(
                            bitmap = sourceBitmap!!,
                            initialConfig = gridConfig,
                            onDismiss = { showGridEditDialog = false },
                            onSave = { newConfig ->
                                saveToUndo()
                                gridConfig = newConfig
                                showGridEditDialog = false
                            }
                        )
                    }
                } else {
                    // 加载中占位
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作栏（Undo/Redo/Reset）
                OperationBar(
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onUndo = { undo() },
                    onRedo = { redo() },
                    onReset = { resetGrid() }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 网格配置区
                GridConfigSection(
                    gridConfig = gridConfig,
                    onPresetSelect = { preset ->
                        saveToUndo()
                        gridConfig = gridConfig.withPreset(preset)
                    },
                    onRowsChange = { rows ->
                        saveToUndo()
                        gridConfig = gridConfig.withRows(rows)
                    },
                    onColumnsChange = { columns ->
                        saveToUndo()
                        gridConfig = gridConfig.withColumns(columns)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 底部按钮
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Button(
                    onClick = { splitImage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    enabled = sourceBitmap != null
                ) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.split_and_save),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    // 自定义图库浏览器（与绘画输入框一致）
    if (showGallery) {
        GalleryScreen(
            viewModel = galleryViewModel,
            onImagesSelected = { selectedUris ->
                val uri = selectedUris.firstOrNull()
                if (uri == null) {
                    showGallery = false
                    return@GalleryScreen
                }

                scope.launch {
                    val newPath = copyUriToCacheFile(context, uri)
                    if (newPath.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.paint_image_load_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        showGallery = false
                        return@launch
                    }

                    // 重置编辑状态并加载新图片
                    sourceBitmap = null
                    currentImagePath = newPath
                    gridConfig = GridConfig().withEvenDividers()
                    undoStack = emptyList()
                    redoStack = emptyList()
                    showGallery = false
                }
            },
            onDismiss = { showGallery = false }
        )
    }
}

/**
 * 检查照片访问权限状态
 * @return Pair<是否有完整权限, 是否有部分权限>
 */
private fun checkPhotoPermissionStatus(context: Context): Pair<Boolean, Boolean> {
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
 * 将 content:// Uri 拷贝到应用缓存目录，返回可解码的本地路径。
 * - 选择了 file:// 时尽量直接返回原始路径
 * - 选择了 content:// 时复制到 cacheDir，避免后续权限失效/无法解码
 */
private suspend fun copyUriToCacheFile(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        if (uri.scheme == "file") {
            return@withContext uri.path
        }

        val mimeType = context.contentResolver.getType(uri) ?: "image/png"
        val lower = mimeType.lowercase(Locale.US)
        val ext = when {
            lower.contains("png") -> "png"
            lower.contains("webp") -> "webp"
            lower.contains("gif") -> "gif"
            else -> "jpg"
        }

        val outFile = File(context.cacheDir, "split_replace_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null

        outFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

/**
 * 对已排序的分割线做最小间距修正，避免切分时出现 0 宽/0 高的格子（导致 createBitmap 失败被跳过）。
 *
 * @param sortedDividers 已经从小到大排序的分割线（范围 0..1）
 * @param minGapFraction 最小间距（比例），通常按像素换算成比例传入
 */
private fun sanitizeSortedDividers(sortedDividers: List<Float>, minGapFraction: Float): List<Float> {
    if (sortedDividers.isEmpty()) return emptyList()
    val minGap = minGapFraction.coerceIn(0.0001f, 0.05f)
    val out = ArrayList<Float>(sortedDividers.size)
    var last = 0f
    sortedDividers.forEach { pos ->
        val clamped = pos.coerceIn(minGap, 1f - minGap)
        val adjusted = if (out.isEmpty()) clamped else clamped.coerceAtLeast(last + minGap)
        if (adjusted >= 1f - minGap) return@forEach
        out.add(adjusted)
        last = adjusted
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridEditorTopBar(
    onBack: () -> Unit,
    onPickImage: () -> Unit
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.split_back)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.split_grid_editor),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        },
        actions = {
            IconButton(onClick = onPickImage) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.split_replace_image)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

/**
 * 网格预览区域
 */
@Composable
private fun GridPreviewArea(
    bitmap: Bitmap,
    gridConfig: GridConfig,
    onConfigChange: (GridConfig) -> Unit,
    interactive: Boolean = true,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    clipShape: Shape? = null,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 当前正在拖动的分割线索引
    var draggingRowIndex by remember { mutableStateOf<Int?>(null) }
    var draggingColIndex by remember { mutableStateOf<Int?>(null) }
    
    // 图片显示区域大小
    val imageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    
    // 虚线效果
    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    val gridLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val gridLineActiveColor = MaterialTheme.colorScheme.primary
    val cropLineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)

    // 避免 pointerInput 因 gridConfig 更新而中断拖拽
    val latestGridConfig by rememberUpdatedState(gridConfig)

    val dragModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures

                    val cfg = latestGridConfig
                    val cropBounds = cfg.cropBounds
                    val cropLeft = cropBounds.left * canvasSize.width
                    val cropTop = cropBounds.top * canvasSize.height
                    val cropWidth = (cropBounds.right - cropBounds.left) * canvasSize.width
                    val cropHeight = (cropBounds.bottom - cropBounds.top) * canvasSize.height

                    // 检测触摸点附近的分割线（扩大检测范围到 40px）
                    val touchThreshold = 40f

                    // 检测水平分割线
                    cfg.rowDividers.forEachIndexed { index, position ->
                        val y = cropTop + position * cropHeight
                        if (kotlin.math.abs(startOffset.y - y) < touchThreshold &&
                            startOffset.x >= cropLeft && startOffset.x <= cropLeft + cropWidth
                        ) {
                            onDragStart?.invoke()
                            draggingRowIndex = index
                            return@detectDragGestures
                        }
                    }

                    // 检测垂直分割线
                    cfg.colDividers.forEachIndexed { index, position ->
                        val x = cropLeft + position * cropWidth
                        if (kotlin.math.abs(startOffset.x - x) < touchThreshold &&
                            startOffset.y >= cropTop && startOffset.y <= cropTop + cropHeight
                        ) {
                            onDragStart?.invoke()
                            draggingColIndex = index
                            return@detectDragGestures
                        }
                    }
                },
                onDragEnd = {
                    draggingRowIndex = null
                    draggingColIndex = null
                    onDragEnd?.invoke()
                },
                onDragCancel = {
                    draggingRowIndex = null
                    draggingColIndex = null
                    onDragEnd?.invoke()
                },
                onDrag = { change, dragAmount ->
                    change.consume()

                    val cfg = latestGridConfig
                    val cropBounds = cfg.cropBounds
                    val cropHeight = (cropBounds.bottom - cropBounds.top) * canvasSize.height
                    val cropWidth = (cropBounds.right - cropBounds.left) * canvasSize.width

                    draggingRowIndex?.let { index ->
                        val currentPosition = cfg.rowDividers[index]
                        val raw = (currentPosition + dragAmount.y / cropHeight)
                            .coerceIn(0.05f, 0.95f)
                        val newPosition = raw
                        val newDividers = cfg.rowDividers.toMutableList()
                        newDividers[index] = newPosition
                        // 关键：不排序 -> 不会触发“另一根线跟着一起移动/互换”的错觉
                        onConfigChange(cfg.copy(rowDividers = newDividers))
                    }

                    draggingColIndex?.let { index ->
                        val currentPosition = cfg.colDividers[index]
                        val raw = (currentPosition + dragAmount.x / cropWidth)
                            .coerceIn(0.05f, 0.95f)
                        val newPosition = raw
                        val newDividers = cfg.colDividers.toMutableList()
                        newDividers[index] = newPosition
                        onConfigChange(cfg.copy(colDividers = newDividers))
                    }
                }
            )
        }
    } else {
        Modifier
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(
            imageAspectRatio.coerceIn(0.5f, 2f),
            matchHeightConstraintsFirst = true
        )
        .then(modifier)
        .onSizeChanged { canvasSize = it }
        .then(dragModifier)
        .drawWithContent {
                drawContent()
                
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    val cropBounds = gridConfig.cropBounds
                    val cropLeft = cropBounds.left * size.width
                    val cropTop = cropBounds.top * size.height
                    val cropWidth = (cropBounds.right - cropBounds.left) * size.width
                    val cropHeight = (cropBounds.bottom - cropBounds.top) * size.height
                    
                    // 绘制裁剪边框
                    drawRect(
                        color = cropLineColor,
                        topLeft = Offset(cropLeft, cropTop),
                        size = Size(cropWidth, cropHeight),
                        style = Stroke(width = 3f, pathEffect = dashPathEffect)
                    )
                    
                    // 绘制水平分割线
                    gridConfig.rowDividers.forEachIndexed { index, position ->
                        val y = cropTop + position * cropHeight
                        val isActive = draggingRowIndex == index
                        drawLine(
                            color = if (isActive) gridLineActiveColor else gridLineColor,
                            start = Offset(cropLeft, y),
                            end = Offset(cropLeft + cropWidth, y),
                            strokeWidth = if (isActive) 4f else 2f,
                            pathEffect = if (isActive) null else dashPathEffect
                        )
                    }
                    
                    // 绘制垂直分割线
                    gridConfig.colDividers.forEachIndexed { index, position ->
                        val x = cropLeft + position * cropWidth
                        val isActive = draggingColIndex == index
                        drawLine(
                            color = if (isActive) gridLineActiveColor else gridLineColor,
                            start = Offset(x, cropTop),
                            end = Offset(x, cropTop + cropHeight),
                            strokeWidth = if (isActive) 4f else 2f,
                            pathEffect = if (isActive) null else dashPathEffect
                        )
                    }
                }
            }

    // 某些 Compose 版本没有 RectangleShape；这里用“可选 clip”实现全屏无圆角
    val finalModifier = if (clipShape != null) {
        baseModifier.clip(clipShape)
    } else {
        baseModifier
    }

    Box(modifier = finalModifier) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 全屏网格编辑弹窗（可拖动分割线）
 *
 * - 外部页面只展示只读预览，避免与 verticalScroll 手势冲突
 * - 在弹窗内再进行精细拖动编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridEditFullScreenDialog(
    bitmap: Bitmap,
    initialConfig: GridConfig,
    onDismiss: () -> Unit,
    onSave: (GridConfig) -> Unit
) {
    var gridConfig by remember(initialConfig) { mutableStateOf(initialConfig) }
    var undoStack by remember(initialConfig) { mutableStateOf<List<GridConfig>>(emptyList()) }
    var redoStack by remember(initialConfig) { mutableStateOf<List<GridConfig>>(emptyList()) }

    fun saveToUndo() {
        undoStack = undoStack + gridConfig
        redoStack = emptyList()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + gridConfig
            gridConfig = undoStack.last()
            undoStack = undoStack.dropLast(1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + gridConfig
            gridConfig = redoStack.last()
            redoStack = redoStack.dropLast(1)
        }
    }

    fun resetGrid() {
        saveToUndo()
        gridConfig = gridConfig.withEvenDividers()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // 中间图片区域
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    // 全屏编辑：左右留出安全边距，避免与系统返回手势冲突
                    // 上下对称预留空间给按钮和工具栏
                    .padding(horizontal = 12.dp, vertical = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                // 使用“真实可用高度”作为图片最大高度，彻底避免顶到顶部按钮
                val previewMaxHeight = this.maxHeight

                GridPreviewArea(
                    bitmap = bitmap,
                    gridConfig = gridConfig,
                    interactive = true,
                    // 全屏编辑：不裁剪圆角，铺满宽度
                    clipShape = null,
                    modifier = Modifier.heightIn(max = previewMaxHeight),
                    onDragStart = { saveToUndo() },
                    onDragEnd = { /* no-op */ },
                    onConfigChange = { newConfig ->
                        // 拖动时只更新状态，不再每帧写 undo 栈
                        gridConfig = newConfig
                    }
                )
            }

            // 顶部：左上角 X / 右上角 √
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { onSave(gridConfig) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.confirm),
                    tint = Color.White
                )
            }

            // 底部：撤销 / 重做 / 重置
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GridEditToolButton(
                        icon = Icons.Default.Undo,
                        label = stringResource(R.string.split_undo),
                        enabled = undoStack.isNotEmpty(),
                        onClick = { undo() }
                    )
                    GridEditToolButton(
                        icon = Icons.Default.Redo,
                        label = stringResource(R.string.split_redo),
                        enabled = redoStack.isNotEmpty(),
                        onClick = { redo() }
                    )
                    GridEditToolButton(
                        icon = Icons.Default.Refresh,
                        label = stringResource(R.string.split_reset_grid),
                        enabled = true,
                        onClick = { resetGrid() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridEditToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
        )
    }
}

/**
 * 操作栏
 */
@Composable
private fun OperationBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OperationButton(
                icon = Icons.Default.Undo,
                label = stringResource(R.string.split_undo),
                enabled = canUndo,
                onClick = onUndo
            )
            OperationButton(
                icon = Icons.Default.Redo,
                label = stringResource(R.string.split_redo),
                enabled = canRedo,
                onClick = onRedo
            )
            OperationButton(
                icon = Icons.Default.Refresh,
                label = stringResource(R.string.split_reset_grid),
                enabled = true,
                onClick = onReset
            )
        }
    }
}

@Composable
private fun OperationButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )
    }
}

/**
 * 网格配置区
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridConfigSection(
    gridConfig: GridConfig,
    onPresetSelect: (GridPreset) -> Unit,
    onRowsChange: (Int) -> Unit,
    onColumnsChange: (Int) -> Unit
) {
    var presetExpanded by remember { mutableStateOf(false) }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.split_grid_config),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 预设选择
            Text(
                text = stringResource(R.string.split_ratio_preset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 预设菜单（主题化）
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { presetExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = getPresetLabel(gridConfig.preset, gridConfig.rows, gridConfig.columns),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        // 把菜单锚点放到右侧箭头上，让下拉从右侧弹出
                        Box {
                            Icon(
                                imageVector = if (presetExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AppDropdownMenu(
                                expanded = presetExpanded,
                                onDismissRequest = { presetExpanded = false },
                                items = GridPreset.entries
                                    .filter { it != GridPreset.CUSTOM }
                                    .map { preset ->
                                        AppMenuItem(
                                            title = getPresetLabel(preset, preset.rows, preset.columns),
                                            icon = Icons.Default.GridView,
                                            trailingIcon = if (gridConfig.preset == preset) Icons.Default.Check else null,
                                            selected = gridConfig.preset == preset,
                                            onClick = { onPresetSelect(preset) }
                                        )
                                    },
                                // 限制弹出菜单宽度，避免铺满全屏
                                modifier = Modifier.widthIn(min = 180.dp, max = 260.dp),
                                showDividers = true
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 行列数调节
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 行数
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.split_rows),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NumberStepper(
                        value = gridConfig.rows,
                        onValueChange = onRowsChange,
                        minValue = 1,
                        maxValue = 10
                    )
                }
                
                // 列数
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.split_columns),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NumberStepper(
                        value = gridConfig.columns,
                        onValueChange = onColumnsChange,
                        minValue = 1,
                        maxValue = 10
                    )
                }
            }
        }
    }
}

@Composable
private fun getPresetLabel(preset: GridPreset, rows: Int, columns: Int): String {
    return when (preset) {
        GridPreset.GRID_3x3 -> stringResource(R.string.split_preset_grid_3x3)
        GridPreset.GRID_4x4 -> stringResource(R.string.split_preset_grid_4x4)
        GridPreset.GRID_4x5 -> stringResource(R.string.split_preset_grid_4x5)
        GridPreset.GRID_4x6 -> stringResource(R.string.split_preset_grid_4x6)
        GridPreset.GRID_5x5 -> stringResource(R.string.split_preset_grid_5x5)
        GridPreset.CUSTOM -> stringResource(R.string.split_preset_custom, rows, columns)
    }
}

/**
 * 数字步进器
 */
@Composable
private fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 减少按钮
            IconButton(
                onClick = { if (value > minValue) onValueChange(value - 1) },
                enabled = value > minValue
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (value > minValue) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
            
            // 当前值
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            // 增加按钮
            IconButton(
                onClick = { if (value < maxValue) onValueChange(value + 1) },
                enabled = value < maxValue
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = if (value < maxValue) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
        }
    }
}
