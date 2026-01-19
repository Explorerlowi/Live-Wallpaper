package com.example.livewallpaper.paint.ui.split

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livewallpaper.R
import com.example.livewallpaper.ui.components.AppDropdownMenu
import com.example.livewallpaper.ui.components.AppMenuItem
import com.example.livewallpaper.ui.components.ImageEditScreen
import com.example.livewallpaper.ui.components.ImageEditResult
import com.example.livewallpaper.ui.components.applyImageEditResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// SharedPreferences 键名
private const val PREFS_NAME = "image_split_naming"
private const val KEY_PREFIX_VALUES = "prefix_values"
private const val KEY_SUFFIX_VALUES = "suffix_values"

/**
 * 从 SharedPreferences 加载保存的前缀列表
 */
private fun loadSavedPrefixes(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedString = prefs.getString(KEY_PREFIX_VALUES, null)
    return if (savedString.isNullOrBlank()) {
        listOf("sticker", "character", "emoji", "avatar")
    } else {
        savedString.split(",").filter { it.isNotBlank() }
    }
}

/**
 * 从 SharedPreferences 加载保存的后缀列表
 */
private fun loadSavedSuffixes(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedString = prefs.getString(KEY_SUFFIX_VALUES, null)
    return if (savedString.isNullOrBlank()) {
        listOf("happy", "sad", "angry", "surprised", "confused", "love")
    } else {
        savedString.split(",").filter { it.isNotBlank() }
    }
}

/**
 * 保存前缀列表到 SharedPreferences
 */
private fun savePrefixes(context: Context, values: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_PREFIX_VALUES, values.joinToString(",")).apply()
}

/**
 * 保存后缀列表到 SharedPreferences
 */
private fun saveSuffixes(context: Context, values: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SUFFIX_VALUES, values.joinToString(",")).apply()
}

/**
 * 微调与导出屏幕
 * 
 * @param tiles 切分后的图块列表
 * @param onBack 返回上一步
 * @param onDismiss 关闭整个流程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSplitExportScreen(
    tiles: List<SplitTile>,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态
    var mutableTiles by remember { mutableStateOf(tiles) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var exportFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var prefixValue by remember { mutableStateOf("sticker") }
    var suffixValue by remember { mutableStateOf("happy") }
    var compressAsZip by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    
    // 从持久化存储加载已保存的值
    var savedPrefixValues by remember { mutableStateOf(loadSavedPrefixes(context)) }
    var savedSuffixValues by remember { mutableStateOf(loadSavedSuffixes(context)) }
    
    // 命名弹窗状态
    var showPrefixDialog by remember { mutableStateOf(false) }
    var showSuffixDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    
    // 图片编辑器状态
    var showImageEditor by remember { mutableStateOf(false) }
    var editingTileIndex by remember { mutableIntStateOf(-1) }
    
    // 更新单个图块的前缀
    fun updatePrefixForTile(index: Int, prefix: String) {
        val newTiles = mutableTiles.toMutableList()
        val currentTile = newTiles[index]
        // 从当前文件名中提取后缀部分，如果没有则使用默认后缀
        val currentSuffix = if (currentTile.fileName.contains("_")) {
            currentTile.fileName.substringAfter("_").substringBefore("_")
        } else {
            suffixValue
        }
        newTiles[index] = currentTile.copy(
            fileName = "${prefix}_${currentSuffix}_${String.format("%02d", index + 1)}"
        )
        mutableTiles = newTiles
    }
    
    // 更新单个图块的后缀
    fun updateSuffixForTile(index: Int, suffix: String) {
        val newTiles = mutableTiles.toMutableList()
        val currentTile = newTiles[index]
        // 从当前文件名中提取前缀部分，如果没有则使用默认前缀
        val currentPrefix = if (currentTile.fileName.contains("_")) {
            currentTile.fileName.substringBefore("_")
        } else {
            prefixValue
        }
        newTiles[index] = currentTile.copy(
            fileName = "${currentPrefix}_${suffix}_${String.format("%02d", index + 1)}"
        )
        mutableTiles = newTiles
    }
    
    // 应用前缀到所有
    fun applyPrefixToAll(prefix: String) {
        mutableTiles = mutableTiles.mapIndexed { index, tile ->
            // 从当前文件名中提取后缀部分，如果没有则使用默认后缀
            val currentSuffix = if (tile.fileName.contains("_")) {
                tile.fileName.substringAfter("_").substringBefore("_")
            } else {
                suffixValue
            }
            tile.copy(fileName = "${prefix}_${currentSuffix}_${String.format("%02d", index + 1)}")
        }
    }
    
    // 应用后缀到所有
    fun applySuffixToAll(suffix: String) {
        mutableTiles = mutableTiles.mapIndexed { index, tile ->
            // 从当前文件名中提取前缀部分，如果没有则使用默认前缀
            val currentPrefix = if (tile.fileName.contains("_")) {
                tile.fileName.substringBefore("_")
            } else {
                prefixValue
            }
            tile.copy(fileName = "${currentPrefix}_${suffix}_${String.format("%02d", index + 1)}")
        }
    }
    
    // 导出图片
    fun exportTiles() {
        scope.launch {
            isExporting = true
            try {
                val result = withContext(Dispatchers.IO) {
                    if (compressAsZip) {
                        exportAsZip(context, mutableTiles, exportFormat)
                    } else {
                        exportIndividually(context, mutableTiles, exportFormat)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.split_export_success, mutableTiles.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.split_export_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                isExporting = false
            }
        }
    }
    
    // 滑动进入/退出动画
    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible && !isClosing) 0f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        finishedListener = { 
            // 退出动画完成后调用 onBack
            if (isClosing) {
                onBack()
            }
        },
        label = "slideOffset"
    )
    
    // 启动时触发进入动画
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // 带动画的返回处理
    val handleBack: () -> Unit = {
        if (!isClosing) {
            isClosing = true
        }
    }
    
    Dialog(
        onDismissRequest = { }, // 不允许点击外部关闭
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 拦截返回键，播放退出动画后返回
        BackHandler {
            handleBack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * slideOffset
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部栏
                    ExportTopBar(
                        onBack = handleBack,
                        onReset = {
                            showResetConfirm = true
                        }
                    )

                    // 中间可滚动区域
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 标题
                        Text(
                            text = stringResource(R.string.split_adjust_tiles),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(R.string.split_tap_to_edit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 图块轮播区
                        TileCarousel(
                            tiles = mutableTiles,
                            selectedIndex = selectedIndex,
                            onSelectTile = { selectedIndex = it },
                            onEditTile = { index ->
                                editingTileIndex = index
                                showImageEditor = true
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 导出格式
                        ExportFormatSection(
                            selectedFormat = exportFormat,
                            onFormatSelect = { exportFormat = it }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 命名规则
                        NamingRuleSection(
                            prefixValue = prefixValue,
                            suffixValue = suffixValue,
                            onPrefixClick = { showPrefixDialog = true },
                            onSuffixClick = { showSuffixDialog = true }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 压缩选项
                        CompressOption(
                            enabled = compressAsZip,
                            onToggle = { compressAsZip = it }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 底部按钮
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 3.dp
                    ) {
                        Button(
                            onClick = { exportTiles() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.split_download_tiles, mutableTiles.size),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // 关键：不要再弹出系统 Dialog/AlertDialog（会触发窗口 dim/重绘导致闪屏）
            // 前后缀输入改成同一 Dialog 内的覆盖层
            if (showPrefixDialog) {
                NamingInputDialog(
                    title = stringResource(R.string.split_select_prefix),
                    currentValue = prefixValue,
                    savedValues = savedPrefixValues,
                    onValueSelect = { value, applyToAll ->
                        prefixValue = value
                        if (applyToAll) {
                            applyPrefixToAll(value)
                        } else {
                            updatePrefixForTile(selectedIndex, value)
                        }
                    },
                    onSaveValue = { value ->
                        if (value.isNotBlank() && value !in savedPrefixValues) {
                            val newList = savedPrefixValues + value
                            savedPrefixValues = newList
                            savePrefixes(context, newList)
                        }
                    },
                    onDismiss = { showPrefixDialog = false }
                )
            }

            if (showSuffixDialog) {
                NamingInputDialog(
                    title = stringResource(R.string.split_select_suffix),
                    currentValue = suffixValue,
                    savedValues = savedSuffixValues,
                    onValueSelect = { value, applyToAll ->
                        suffixValue = value
                        if (applyToAll) {
                            applySuffixToAll(value)
                        } else {
                            updateSuffixForTile(selectedIndex, value)
                        }
                    },
                    onSaveValue = { value ->
                        if (value.isNotBlank() && value !in savedSuffixValues) {
                            val newList = savedSuffixValues + value
                            savedSuffixValues = newList
                            saveSuffixes(context, newList)
                        }
                    },
                    onDismiss = { showSuffixDialog = false }
                )
            }

            if (showResetConfirm) {
                ResetConfirmOverlay(
                    title = stringResource(R.string.split_reset_confirm_title),
                    message = stringResource(R.string.split_reset_confirm_message),
                    onConfirm = {
                        // 重置所有微调（不重置前后缀）
                        mutableTiles = tiles
                        showResetConfirm = false
                    },
                    onDismiss = { showResetConfirm = false }
                )
            }
            
            // 图片编辑器
            if (showImageEditor && editingTileIndex >= 0 && editingTileIndex < mutableTiles.size) {
                val editingTile = mutableTiles[editingTileIndex]
                // 使用原始 bitmap 进行编辑，并恢复之前的编辑参数
                val initialResult = ImageEditResult(
                    cropRect = androidx.compose.ui.geometry.Rect(
                        left = editingTile.editParams.cropLeft,
                        top = editingTile.editParams.cropTop,
                        right = editingTile.editParams.cropRight,
                        bottom = editingTile.editParams.cropBottom
                    ),
                    rotation = editingTile.editParams.rotation,
                    flipHorizontal = editingTile.editParams.flipHorizontal,
                    flipVertical = editingTile.editParams.flipVertical
                )
                ImageEditScreen(
                    bitmap = editingTile.originalBitmap,  // 使用原始 bitmap
                    initialResult = initialResult,         // 恢复之前的编辑参数
                    onConfirm = { result ->
                        // 应用编辑结果到原始 bitmap
                        val newBitmap = applyImageEditResult(editingTile.originalBitmap, result)
                        // 保存编辑参数
                        val newEditParams = TileEditParams(
                            cropLeft = result.cropRect.left,
                            cropTop = result.cropRect.top,
                            cropRight = result.cropRect.right,
                            cropBottom = result.cropRect.bottom,
                            rotation = result.rotation,
                            flipHorizontal = result.flipHorizontal,
                            flipVertical = result.flipVertical
                        )
                        val newTiles = mutableTiles.toMutableList()
                        newTiles[editingTileIndex] = editingTile.copy(
                            bitmap = newBitmap,
                            editParams = newEditParams,
                            // 重置微调参数
                            offsetX = 0f,
                            offsetY = 0f,
                            scale = 1f
                        )
                        mutableTiles = newTiles
                        showImageEditor = false
                        editingTileIndex = -1
                    },
                    onDismiss = {
                        showImageEditor = false
                        editingTileIndex = -1
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportTopBar(
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.split_back))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.split_finetune_export),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        },
        actions = {
            TextButton(onClick = onReset) {
                Text(
                    stringResource(R.string.split_reset),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

/**
 * 图块轮播区
 */
@Composable
private fun TileCarousel(
    tiles: List<SplitTile>,
    selectedIndex: Int,
    onSelectTile: (Int) -> Unit,
    onEditTile: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    
    // 获取容器宽度，用于计算动态 contentPadding
    var containerWidth by remember { mutableIntStateOf(0) }
    
    // 动态计算 contentPadding，确保第一张和最后一张图片能居中
    // 使用容器宽度的一半减去最小卡片宽度的一半
    val horizontalPadding = remember(containerWidth) {
        if (containerWidth > 0) {
            // 确保 padding 足够大，让任意宽度的卡片都能居中
            (containerWidth / 2).coerceAtLeast(with(density) { 140.dp.roundToPx() })
        } else {
            with(density) { 140.dp.roundToPx() }
        }
    }
    
    // 监听滚动位置，实时更新选中项
    val centerItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2
            
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                kotlin.math.abs(itemCenter - viewportCenter)
            }?.index ?: 0
        }
    }
    
    // 当滚动停止时更新选中项
    LaunchedEffect(centerItemIndex) {
        if (!listState.isScrollInProgress && centerItemIndex != selectedIndex) {
            onSelectTile(centerItemIndex)
        }
    }
    
    // 滚动停止后吸附到最近的卡片
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centerItemIndex != selectedIndex) {
            onSelectTile(centerItemIndex)
        }
    }
    
    // 点击卡片或选中项变化时滚动到该卡片居中
    LaunchedEffect(selectedIndex) {
        // 等待一帧让布局完成
        kotlinx.coroutines.delay(16)
        
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        
        val viewportWidth = layoutInfo.viewportSize.width
        val viewportCenter = layoutInfo.viewportStartOffset + viewportWidth / 2
        
        // 查找选中项的信息
        val targetItem = layoutInfo.visibleItemsInfo.find { it.index == selectedIndex }
        
        if (targetItem != null) {
            // 选中项在可见范围内，计算需要滚动的距离让它居中
            val itemCenter = targetItem.offset + targetItem.size / 2
            val scrollDistance = itemCenter - viewportCenter
            
            if (kotlin.math.abs(scrollDistance) > 10) {
                listState.animateScrollBy(scrollDistance.toFloat())
            }
        } else {
            // 选中项不在可见范围内，先滚动到该项，再调整居中
            // 计算 scrollOffset：让 item 的中心对齐 viewport 的中心
            // 由于不知道 item 的实际宽度，使用平均值估算
            val estimatedItemWidth = with(density) { 180.dp.roundToPx() }
            val scrollOffset = -(viewportWidth / 2) + horizontalPadding + (estimatedItemWidth / 2)
            
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = scrollOffset
            )
            
            // 滚动后再次微调居中
            kotlinx.coroutines.delay(100)
            val newLayoutInfo = listState.layoutInfo
            val newTargetItem = newLayoutInfo.visibleItemsInfo.find { it.index == selectedIndex }
            if (newTargetItem != null) {
                val newViewportCenter = newLayoutInfo.viewportStartOffset + newLayoutInfo.viewportSize.width / 2
                val newItemCenter = newTargetItem.offset + newTargetItem.size / 2
                val adjustDistance = newItemCenter - newViewportCenter
                if (kotlin.math.abs(adjustDistance) > 5) {
                    listState.animateScrollBy(adjustDistance.toFloat())
                }
            }
        }
    }
    
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            // 限制预览区高度，避免图片卡片撑破布局
            .height(240.dp)
            .onSizeChanged { containerWidth = it.width },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = with(density) { horizontalPadding.toDp() }),
        verticalAlignment = Alignment.CenterVertically,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    ) {
        itemsIndexed(tiles) { index, tile ->
            TileCard(
                tile = tile,
                isSelected = index == centerItemIndex, // 使用实时计算的中心项
                onClick = { onSelectTile(index) },
                onDoubleClick = { onEditTile(index) }
            )
        }
    }
}

/**
 * 单个图块卡片
 */
@Composable
private fun TileCard(
    tile: SplitTile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 4.dp,
        label = "elevation"
    )

    // 高度固定，宽度按原始比例动态变化（并限制范围，避免极端长图撑爆轮播）
    val cardHeight = 200.dp
    val aspect = remember(tile.bitmap) {
        tile.bitmap.width.toFloat() / tile.bitmap.height.coerceAtLeast(1).toFloat()
    }
    val cardWidth = (cardHeight * aspect).coerceIn(120.dp, 280.dp)
    
    Column(
        modifier = Modifier
            .scale(scale)
            .width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图片卡片
        Surface(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clickable {
                    if (isSelected) {
                        // 已选中的卡片，点击进入编辑
                        onDoubleClick()
                    } else {
                        // 未选中的卡片，点击选中
                        onClick()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            shadowElevation = elevation,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = tile.bitmap.asImageBitmap(),
                    contentDescription = "Tile ${tile.index + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = tile.offsetX
                            translationY = tile.offsetY
                            scaleX = tile.scale
                            scaleY = tile.scale
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 外部底部标签
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ) {
            Text(
                text = if (tile.fileName.isNotBlank()) tile.fileName else "tile ${String.format("%02d", tile.index + 1)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 导出格式选择
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatSection(
    selectedFormat: ExportFormat,
    onFormatSelect: (ExportFormat) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.split_export_format),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Box {
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { expanded = true },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFormat.extension.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            val items = ExportFormat.entries.map { format ->
                val subtitle = when (format) {
                    ExportFormat.PNG -> stringResource(R.string.split_export_png_desc)
                    ExportFormat.JPG -> stringResource(R.string.split_export_jpg_desc)
                }
                AppMenuItem(
                    title = format.extension.uppercase(),
                    subtitle = subtitle,
                    icon = if (format == ExportFormat.PNG) Icons.Default.Image else Icons.Default.Photo,
                    trailingIcon = if (selectedFormat == format) Icons.Default.Check else null,
                    selected = selectedFormat == format,
                    onClick = { onFormatSelect(format) }
                )
            }

            AppDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                items = items,
                // 限制弹出菜单宽度，避免铺满全屏
                modifier = Modifier.widthIn(min = 180.dp, max = 260.dp),
                showDividers = true
            )
        }
    }
}

/**
 * 命名规则区域
 */
@Composable
private fun NamingRuleSection(
    prefixValue: String,
    suffixValue: String,
    onPrefixClick: () -> Unit,
    onSuffixClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.split_naming_rules),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 前缀选择
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.split_prefix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        NamingFieldButton(
                            value = prefixValue,
                            onClick = onPrefixClick
                        )
                    }
                    
                    // 分隔符
                    Text(
                        text = "_",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.Bottom).padding(bottom = 10.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    // 后缀选择
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.split_suffix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        NamingFieldButton(
                            value = suffixValue,
                            onClick = onSuffixClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NamingFieldButton(
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 压缩选项
 */
@Composable
private fun CompressOption(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.split_compress_zip),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.split_compress_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * 命名输入弹窗
 */
@Composable
private fun NamingInputDialog(
    title: String,
    currentValue: String,
    savedValues: List<String>,
    onValueSelect: (String, Boolean) -> Unit,
    onSaveValue: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf(currentValue) }

    // 覆盖层模式：避免弹出系统 Dialog/AlertDialog（会触发窗口 dim/重绘导致闪屏）
    BackHandler(onBack = onDismiss)

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 520.dp)
                // 防止点击穿透到背景
                .clickable(interactionSource = cardInteraction, indication = null) { },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.split_input_new),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.split_input_new)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (savedValues.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.split_saved_options),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                savedValues.forEach { value ->
                                    val selected = inputValue == value
                                    FilterChip(
                                        selected = selected,
                                        onClick = { inputValue = value },
                                        label = { Text(value) },
                                        leadingIcon = if (selected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (inputValue.isNotBlank() && inputValue !in savedValues) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onSaveValue(inputValue) }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.split_save_new),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            onValueSelect(inputValue, false)
                            onDismiss()
                        },
                        enabled = inputValue.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(R.string.split_apply),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    TextButton(
                        onClick = {
                            onValueSelect(inputValue, true)
                            onDismiss()
                        },
                        enabled = inputValue.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(R.string.split_apply_all),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetConfirmOverlay(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 520.dp)
                .clickable(interactionSource = cardInteraction, indication = null) { },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.confirm),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * FlowRow 布局（简单实现）
 */
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

// ==================== 导出工具函数 ====================

private suspend fun exportAsZip(
    context: Context,
    tiles: List<SplitTile>,
    format: ExportFormat
): Boolean {
    val timestamp = System.currentTimeMillis()
    val zipFileName = "Tiles_$timestamp.zip"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, zipFileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ImageSplit")
        }
        
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw Exception("Failed to create file")
        
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                tiles.forEach { tile ->
                    val entryName = "${tile.fileName}.${format.extension}"
                    zipOut.putNextEntry(ZipEntry(entryName))
                    tile.bitmap.compress(
                        if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        90,
                        zipOut
                    )
                    zipOut.closeEntry()
                }
            }
        }
    } else {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val splitDir = File(downloadsDir, "ImageSplit")
        splitDir.mkdirs()
        
        val zipFile = File(splitDir, zipFileName)
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zipOut ->
                tiles.forEach { tile ->
                    val entryName = "${tile.fileName}.${format.extension}"
                    zipOut.putNextEntry(ZipEntry(entryName))
                    tile.bitmap.compress(
                        if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        90,
                        zipOut
                    )
                    zipOut.closeEntry()
                }
            }
        }
    }
    
    return true
}

private suspend fun exportIndividually(
    context: Context,
    tiles: List<SplitTile>,
    format: ExportFormat
): Boolean {
    tiles.forEach { tile ->
        val fileName = "${tile.fileName}.${format.extension}"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageSplit")
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw Exception("Failed to create file")
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                tile.bitmap.compress(
                    if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    90,
                    outputStream
                )
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val splitDir = File(picturesDir, "ImageSplit")
            splitDir.mkdirs()
            
            val file = File(splitDir, fileName)
            FileOutputStream(file).use { fos ->
                tile.bitmap.compress(
                    if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    90,
                    fos
                )
            }
        }
    }
    
    return true
}
