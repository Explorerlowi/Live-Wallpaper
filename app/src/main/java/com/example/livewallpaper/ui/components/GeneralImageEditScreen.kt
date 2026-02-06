package com.example.livewallpaper.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.example.livewallpaper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * 图片编辑工具类型
 */
enum class EditTool {
    BRUSH,
    MOSAIC,
    TEXT,
    CROP
}

/**
 * 图片保存状态
 */
private enum class SaveState {
    /** 空闲，无保存操作 */
    IDLE,
    /** 正在保存中 */
    SAVING,
    /** 保存成功 */
    SUCCESS,
    /** 保存失败 */
    FAILED
}

/**
 * 通用图片编辑界面（全屏 Dialog）
 *
 * 三层结构：
 * - 底层：纯黑背景
 * - 中层：图片展示（居中 Fit，支持双指缩放/平移）
 * - 顶层：可隐藏的按钮层（顶部返回/下载 + 底部工具栏）
 *
 * 下载功能内置，点击下载按钮直接保存图片到系统相册。
 * 点击任何非按钮区域可隐藏按钮层，再次点击恢复。
 *
 * @param imageSource 图片来源（URI 字符串、文件路径或网络 URL）
 * @param onNavigateBack 返回回调
 * @param onToolSelected 工具按钮点击回调（具体逻辑暂不实现）
 * @param onDone 完成按钮回调
 */
@Composable
fun GeneralImageEditScreen(
    imageSource: String,
    onNavigateBack: () -> Unit,
    onToolSelected: (EditTool) -> Unit = {},
    onDone: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 沉浸式全屏设置
        val view = LocalView.current
        DisposableEffect(Unit) {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            dialogWindow?.let { w ->
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowCompat.getInsetsController(w, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                dialogWindow?.let { w ->
                    WindowCompat.getInsetsController(w, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        BackHandler { onNavigateBack() }

        ImageEditBody(
            imageSource = imageSource,
            onNavigateBack = onNavigateBack,
            onToolSelected = onToolSelected,
            onDone = onDone
        )
    }
}

// ==================== 内部实现 ====================

/**
 * 编辑界面主体（三层叠加）
 */
@Composable
private fun ImageEditBody(
    imageSource: String,
    onNavigateBack: () -> Unit,
    onToolSelected: (EditTool) -> Unit,
    onDone: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 保存状态与进度
    var saveState by remember { mutableStateOf(SaveState.IDLE) }
    var saveProgress by remember { mutableFloatStateOf(0f) }

    // 保存成功/失败后自动消失
    LaunchedEffect(saveState) {
        if (saveState == SaveState.SUCCESS || saveState == SaveState.FAILED) {
            delay(1500L)
            saveState = SaveState.IDLE
        }
    }

    // 内置下载回调
    val onDownload: () -> Unit = {
        if (saveState != SaveState.SAVING) {
            scope.launch {
                saveState = SaveState.SAVING
                saveProgress = 0f
                val success = saveImageToGallery(context, imageSource) { progress ->
                    saveProgress = progress
                }
                saveState = if (success) SaveState.SUCCESS else SaveState.FAILED
            }
        }
    }

    val density = LocalDensity.current
    // 捕获初始 inset 值并固定，避免切后台时系统栏恢复导致布局跳动
    val currentStatusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val currentNavBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var statusInset by remember { mutableStateOf(currentStatusTop) }
    var navInset by remember { mutableStateOf(currentNavBottom) }
    // 只在首次有非零值时锁定
    LaunchedEffect(currentStatusTop) {
        if (currentStatusTop > 0.dp && statusInset == 0.dp) {
            statusInset = currentStatusTop
        }
    }
    LaunchedEffect(currentNavBottom) {
        if (currentNavBottom > 0.dp && navInset == 0.dp) {
            navInset = currentNavBottom
        }
    }

    // 缩放与平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 用于双击动画
    val animatableScale = remember { Animatable(1f) }
    val animatableOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    /** 限制平移范围，确保图片不会移出可视区域 */
    fun clampOffset(targetOffset: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f) return Offset.Zero
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = targetOffset.x.coerceIn(-maxX, maxX),
            y = targetOffset.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // ── 第二层：可缩放/平移的图片 ──
        Image(
            painter = rememberAsyncImagePainter(model = imageSource),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // ── 手势层：双指缩放/平移 + 单击切换控件 + 双击还原 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 双指缩放，限制范围 1x ~ 5x
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        // 平移量需要根据缩放比例调整
                        val newOffset = if (newScale > 1f) {
                            clampOffset(offset + pan, newScale)
                        } else {
                            Offset.Zero
                        }
                        scale = newScale
                        offset = newOffset
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 单击切换控件显隐
                            showControls = !showControls
                        },
                        onDoubleTap = {
                            // 双击：如果已缩放则还原，否则放大到 2x
                            scope.launch {
                                if (scale > 1.1f) {
                                    // 还原
                                    launch {
                                        animatableScale.snapTo(scale)
                                        animatableScale.animateTo(1f, tween(300))
                                    }
                                    launch {
                                        animatableOffset.snapTo(offset)
                                        animatableOffset.animateTo(Offset.Zero, tween(300))
                                    }
                                    // 同步最终值
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    // 放大到 2x，以点击位置为中心
                                    val targetScale = 2.5f
                                    val focusX =
                                        (containerSize.width / 2f - it.x) * (targetScale - 1f)
                                    val focusY =
                                        (containerSize.height / 2f - it.y) * (targetScale - 1f)
                                    val targetOffset =
                                        clampOffset(Offset(focusX, focusY), targetScale)
                                    launch {
                                        animatableScale.snapTo(scale)
                                        animatableScale.animateTo(targetScale, tween(300))
                                    }
                                    launch {
                                        animatableOffset.snapTo(offset)
                                        animatableOffset.animateTo(targetOffset, tween(300))
                                    }
                                    scale = targetScale
                                    offset = targetOffset
                                }
                            }
                        }
                    )
                }
        )

        // 双击动画驱动：将 Animatable 值同步到 graphicsLayer
        LaunchedEffect(animatableScale.value, animatableOffset.value) {
            if (animatableScale.isRunning || animatableOffset.isRunning) {
                scale = animatableScale.value
                offset = animatableOffset.value
            }
        }

        // ── 第三层：顶部栏 ──
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            EditTopBar(
                onBack = onNavigateBack,
                onDownload = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = (statusInset - 12.dp).coerceAtLeast(0.dp))
            )
        }

        // ── 第三层：底部工具栏 ──
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            EditBottomBar(
                onToolSelected = onToolSelected,
                onDone = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navInset + 12.dp)
            )
        }

        // ── 保存进度/结果浮层 ──
        SaveOverlay(
            saveState = saveState,
            progress = saveProgress,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 保存进度/结果浮层
 *
 * 参考截图设计：
 * - 保存中：半透明黑色圆角矩形，内含环形进度条 + 百分比 + "正在保存..."
 * - 保存成功：半透明黑色胶囊，内含 ✓ 图标 + "保存成功"
 * - 保存失败：半透明黑色胶囊，内含 "保存失败"
 */
@Composable
private fun SaveOverlay(
    saveState: SaveState,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = saveState != SaveState.IDLE,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        when (saveState) {
            SaveState.SAVING -> {
                // 进度动画平滑过渡
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(200),
                    label = "saveProgress"
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(56.dp)
                        ) {
                            // 底层灰色轨道
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(56.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round
                            )
                            // 进度弧
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(56.dp),
                                color = Color(0xFF7EC8E3),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round
                            )
                            // 百分比文字
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.image_edit_saving),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            SaveState.SUCCESS -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.paint_download_success),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            SaveState.FAILED -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.paint_download_failed),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> { /* IDLE: 不显示 */ }
        }
    }
}

/**
 * 顶部栏：左侧返回 ＜，右侧下载 ↓
 */
@Composable
private fun EditTopBar(
    onBack: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.image_edit_back),
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.image_edit_download),
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * 底部工具栏：画笔 · 马赛克 · 文字 · 裁剪 ＋ 完成按钮
 *
 * 布局参考截图：图标均匀分布在左侧，完成按钮靠右。
 */
@Composable
private fun EditBottomBar(
    onToolSelected: (EditTool) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标区
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ToolIcon(
                icon = Icons.Default.Brush,
                label = stringResource(R.string.image_edit_brush),
                onClick = { onToolSelected(EditTool.BRUSH) }
            )
            ToolIcon(
                icon = Icons.Default.BlurOn,
                label = stringResource(R.string.image_edit_mosaic),
                onClick = { onToolSelected(EditTool.MOSAIC) }
            )
            ToolIcon(
                icon = Icons.Default.TextFields,
                label = stringResource(R.string.image_edit_text),
                onClick = { onToolSelected(EditTool.TEXT) }
            )
            ToolIcon(
                icon = Icons.Default.Crop,
                label = stringResource(R.string.image_edit_crop),
                onClick = { onToolSelected(EditTool.CROP) }
            )
        }

        // 完成按钮
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A90D9)
            ),
            modifier = Modifier.height(38.dp)
        ) {
            Text(
                text = stringResource(R.string.image_edit_done),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

/**
 * 单个工具图标按钮
 */
@Composable
private fun ToolIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ==================== 图片保存工具 ====================

/**
 * 将图片保存到系统相册（带进度回调）
 *
 * 进度分三阶段：
 * - 0% ~ 60%：加载 Bitmap（解码图片数据）
 * - 60% ~ 90%：写入 MediaStore（压缩并保存）
 * - 90% ~ 100%：完成收尾
 *
 * @param context 上下文
 * @param source 图片来源字符串
 * @param onProgress 进度回调，范围 0f ~ 1f
 * @return 是否保存成功
 */
private suspend fun saveImageToGallery(
    context: Context,
    source: String,
    onProgress: (Float) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f)
            val bitmap = loadBitmapFromSource(context, source, onProgress)
                ?: return@withContext false
            onProgress(0.6f)
            val result = saveBitmapToMediaStore(context, bitmap, onProgress)
            onProgress(1f)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 根据来源类型加载 Bitmap，通过回调报告进度
 */
private fun loadBitmapFromSource(
    context: Context,
    source: String,
    onProgress: (Float) -> Unit
): Bitmap? {
    onProgress(0.1f)
    return when {
        // content:// URI
        source.startsWith("content://") -> {
            onProgress(0.2f)
            context.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                onProgress(0.3f)
                BitmapFactory.decodeStream(stream)
            }
        }
        // 网络 URL
        source.startsWith("http://") || source.startsWith("https://") -> {
            onProgress(0.15f)
            URL(source).openStream()?.use { stream ->
                onProgress(0.3f)
                BitmapFactory.decodeStream(stream)
            }
        }
        // 本地文件路径
        else -> {
            val file = File(source)
            if (file.exists()) {
                onProgress(0.3f)
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                try {
                    onProgress(0.2f)
                    context.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                        onProgress(0.3f)
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

/**
 * 通过 MediaStore 将 Bitmap 保存到系统相册
 */
private fun saveBitmapToMediaStore(
    context: Context,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
): Boolean {
    val filename = "IMG_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    onProgress(0.65f)
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: return false

    return try {
        onProgress(0.7f)
        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } ?: false

        onProgress(0.9f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        onProgress(0.95f)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        e.printStackTrace()
        false
    }
}
