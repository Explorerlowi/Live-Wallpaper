package com.example.livewallpaper.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * 图片数据源类型
 * 支持多种图片来源
 */
sealed class ImageSource {
    /** 本地文件 URI */
    data class UriSource(val uri: Uri) : ImageSource()
    
    /** 字符串路径（可以是 URI 字符串、文件路径或网络 URL） */
    data class StringSource(val path: String) : ImageSource()
    
    /** 资源 ID */
    data class ResourceSource(val resId: Int) : ImageSource()
    
    /** 自定义 Painter */
    data class PainterSource(val painter: Painter) : ImageSource()
    
    /** Base64 编码的图片数据 */
    data class Base64Source(val base64: String, val mimeType: String = "image/png") : ImageSource()
}

/**
 * 图片预览配置
 */
data class ImagePreviewConfig(
    /** 背景颜色 */
    val backgroundColor: Color = Color.Black.copy(alpha = 0.95f),
    /** 最大缩放倍数 */
    val maxScale: Float = 5f,
    /** 最小缩放倍数 */
    val minScale: Float = 0.5f,
    /** 双击缩放倍数 */
    val doubleTapScale: Float = 2.5f,
    /** 是否显示页码指示器 */
    val showIndicator: Boolean = true,
    /** 是否显示关闭按钮 */
    val showCloseButton: Boolean = true,
    /** 是否显示工具栏 */
    val showToolbar: Boolean = false,
    /** 图片最大加载尺寸（防止超大图片导致崩溃） */
    val maxImageSize: Int = 2560,
    /** 单击是否关闭预览（false 时切换控件显示） */
    val tapToClose: Boolean = false,
    /** 是否显示旋转按钮 */
    val showRotateButton: Boolean = false,
    /** 是否显示镜像按钮 */
    val showFlipButton: Boolean = false,
    /** 是否显示下载按钮 */
    val showDownloadButton: Boolean = false
)

/**
 * 图片变换状态
 */
data class ImageTransformState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)


/**
 * 通用图片预览对话框
 * 
 * 功能特性：
 * - 支持多种图片来源（URI、路径、资源、Painter、Base64）
 * - 支持多图左右滑动切换
 * - 支持双指缩放和拖动
 * - 支持双击快速缩放
 * - 支持旋转和镜像
 * - 支持下载保存
 * - 可选的页码指示器
 * - 可选的工具栏
 * 
 * @param images 图片列表
 * @param initialIndex 初始显示的图片索引
 * @param config 预览配置
 * @param onDismiss 关闭回调
 * @param onShare 分享回调（可选）
 * @param onDelete 删除回调（可选）
 * @param onDownload 下载回调（可选，返回是否成功）
 */
@Composable
fun ImagePreviewDialog(
    images: List<ImageSource>,
    initialIndex: Int = 0,
    config: ImagePreviewConfig = ImagePreviewConfig(),
    onDismiss: () -> Unit,
    onShare: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    onDownload: ((Int) -> Unit)? = null
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val safeInitialIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { images.size }
    )
    
    // 控制 UI 元素的显示/隐藏
    var showControls by remember { mutableStateOf(true) }
    
    // 每张图片的变换状态 - 使用 mutableStateOf 包装以触发重组
    var transformStates by remember { 
        mutableStateOf(
            images.indices.associateWith { ImageTransformState() }
        )
    }
    
    // 当前页面
    val currentPage = pagerState.currentPage
    
    // 当前图片的变换状态
    val currentTransform = transformStates[currentPage] ?: ImageTransformState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(config.backgroundColor)
        ) {
            // 图片翻页器
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it }
            ) { page ->
                val pageTransform = transformStates[page] ?: ImageTransformState()
                ZoomableImageContent(
                    imageSource = images[page],
                    config = config,
                    transform = pageTransform,
                    onTap = {
                        if (config.tapToClose) {
                            onDismiss()
                        } else {
                            showControls = !showControls
                        }
                    }
                )
            }
            
            // 顶部控制栏
            AnimatedVisibility(
                visible = showControls && config.showCloseButton,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // 底部区域
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 工具栏
                val hasToolbarButtons = config.showToolbar || 
                    config.showRotateButton || 
                    config.showFlipButton || 
                    config.showDownloadButton ||
                    onShare != null || 
                    onDelete != null
                    
                AnimatedVisibility(
                    visible = showControls && hasToolbarButtons,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 左旋按钮
                        if (config.showRotateButton) {
                            ToolbarButton(
                                icon = Icons.Default.Rotate90DegreesCcw,
                                contentDescription = "左旋",
                                onClick = {
                                    val current = transformStates[currentPage] ?: ImageTransformState()
                                    val newRotation = current.rotation - 90f
                                    transformStates = transformStates.toMutableMap().apply {
                                        put(currentPage, current.copy(rotation = newRotation))
                                    }
                                }
                            )
                        }
                        
                        // 右旋按钮
                        if (config.showRotateButton) {
                            ToolbarButton(
                                icon = Icons.Default.Rotate90DegreesCw,
                                contentDescription = "右旋",
                                onClick = {
                                    val current = transformStates[currentPage] ?: ImageTransformState()
                                    val newRotation = current.rotation + 90f
                                    transformStates = transformStates.toMutableMap().apply {
                                        put(currentPage, current.copy(rotation = newRotation))
                                    }
                                }
                            )
                        }
                        
                        // 左右镜像按钮
                        if (config.showFlipButton) {
                            ToolbarButton(
                                icon = Icons.Default.Flip,
                                contentDescription = "左右镜像",
                                onClick = {
                                    val current = transformStates[currentPage] ?: ImageTransformState()
                                    transformStates = transformStates.toMutableMap().apply {
                                        put(currentPage, current.copy(flipHorizontal = !current.flipHorizontal))
                                    }
                                }
                            )
                        }
                        
                        // 上下镜像按钮
                        if (config.showFlipButton) {
                            ToolbarButton(
                                icon = Icons.Default.Flip,
                                contentDescription = "上下镜像",
                                rotateIcon = 90f,
                                onClick = {
                                    val current = transformStates[currentPage] ?: ImageTransformState()
                                    transformStates = transformStates.toMutableMap().apply {
                                        put(currentPage, current.copy(flipVertical = !current.flipVertical))
                                    }
                                }
                            )
                        }
                        
                        // 下载按钮
                        if (config.showDownloadButton) {
                            ToolbarButton(
                                icon = Icons.Default.Download,
                                contentDescription = "下载",
                                onClick = {
                                    if (onDownload != null) {
                                        onDownload(currentPage)
                                    } else {
                                        // 默认下载逻辑
                                        coroutineScope.launch {
                                            val success = saveImageToGallery(
                                                context = context,
                                                imageSource = images[currentPage],
                                                transform = currentTransform
                                            )
                                            val message = if (success) "图片已保存到相册" else "保存失败"
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        
                        // 分享按钮
                        onShare?.let { share ->
                            ToolbarButton(
                                icon = Icons.Default.Share,
                                contentDescription = "分享",
                                onClick = { share(currentPage) }
                            )
                        }
                        
                        // 删除按钮
                        onDelete?.let { delete ->
                            ToolbarButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "删除",
                                onClick = { delete(currentPage) }
                            )
                        }
                    }
                }
                
                // 页码指示器
                AnimatedVisibility(
                    visible = showControls && config.showIndicator && images.size > 1,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 工具栏按钮
 */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    rotateIcon: Float = 0f,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = if (rotateIcon != 0f) {
                Modifier.graphicsLayer { rotationZ = rotateIcon }
            } else {
                Modifier
            }
        )
    }
}


/**
 * 可缩放的图片内容组件
 * 支持旋转和镜像变换
 */
@Composable
private fun ZoomableImageContent(
    imageSource: ImageSource,
    config: ImagePreviewConfig,
    transform: ImageTransformState,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 用于平滑动画的 Animatable
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val animatedScale = remember { Animatable(1f) }
    
    // 旋转和镜像动画
    val animatedRotation by animateFloatAsState(
        targetValue = transform.rotation,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )
    val animatedScaleX by animateFloatAsState(
        targetValue = if (transform.flipHorizontal) -1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scaleX"
    )
    val animatedScaleY by animateFloatAsState(
        targetValue = if (transform.flipVertical) -1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scaleY"
    )
    
    // 容器尺寸
    var containerSize by remember { mutableStateOf(Size.Zero) }

    // 根据图片来源创建内容
    val bitmap = remember(imageSource) {
        when (imageSource) {
            is ImageSource.Base64Source -> {
                try {
                    val bytes = Base64.decode(imageSource.base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    val painter = when (imageSource) {
        is ImageSource.UriSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageSource.uri)
                .size(coil.size.Size(config.maxImageSize, config.maxImageSize))
                .crossfade(true)
                .build()
        )
        is ImageSource.StringSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageSource.path)
                .size(coil.size.Size(config.maxImageSize, config.maxImageSize))
                .crossfade(true)
                .build()
        )
        is ImageSource.ResourceSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageSource.resId)
                .crossfade(true)
                .build()
        )
        is ImageSource.PainterSource -> imageSource.painter
        is ImageSource.Base64Source -> null
    }
    
    // 加载状态
    val isLoading = painter is AsyncImagePainter && 
        painter.state is AsyncImagePainter.State.Loading

    fun calculateBounds(currentScale: Float, containerSize: Size): Offset {
        if (currentScale <= 1f || containerSize == Size.Zero) {
            return Offset.Zero
        }
        val scaledWidth = containerSize.width * currentScale
        val scaledHeight = containerSize.height * currentScale
        val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)
        return Offset(maxX, maxY)
    }
    
    fun constrainOffset(offset: Offset, currentScale: Float): Offset {
        val bounds = calculateBounds(currentScale, containerSize)
        return Offset(
            x = offset.x.coerceIn(-bounds.x, bounds.x),
            y = offset.y.coerceIn(-bounds.y, bounds.y)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scale > 1f) {
                                animatedScale.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                                animatedOffset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessLow))
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = config.doubleTapScale
                                val centerX = containerSize.width / 2f
                                val centerY = containerSize.height / 2f
                                val offsetX = (centerX - tapOffset.x) * (targetScale - 1f)
                                val offsetY = (centerY - tapOffset.y) * (targetScale - 1f)
                                val targetOffset = constrainOffset(Offset(offsetX, offsetY), targetScale)
                                
                                animatedScale.animateTo(targetScale, spring(stiffness = Spring.StiffnessLow))
                                animatedOffset.animateTo(targetOffset, spring(stiffness = Spring.StiffnessLow))
                                scale = targetScale
                                offset = targetOffset
                            }
                        }
                    },
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        
        val imageModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            
                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()
                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }
                            
                            if (pastTouchSlop) {
                                if (zoomChange != 1f) {
                                    val newScale = (scale * zoomChange).coerceIn(config.minScale, config.maxScale)
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val newOffset = if (newScale > 1f) {
                                        val centerX = containerSize.width / 2f
                                        val centerY = containerSize.height / 2f
                                        val focusX = centroid.x - centerX
                                        val focusY = centroid.y - centerY
                                        Offset(
                                            x = offset.x - focusX * (zoomChange - 1f),
                                            y = offset.y - focusY * (zoomChange - 1f)
                                        )
                                    } else offset
                                    
                                    scale = newScale
                                    offset = constrainOffset(newOffset, newScale)
                                    coroutineScope.launch {
                                        animatedScale.snapTo(scale)
                                        animatedOffset.snapTo(offset)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                
                                if (panChange != Offset.Zero) {
                                    if (scale > 1f) {
                                        val newOffset = offset + panChange
                                        offset = constrainOffset(newOffset, scale)
                                        coroutineScope.launch { animatedOffset.snapTo(offset) }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else {
                                        val fingerCount = event.changes.count { it.pressed }
                                        if (fingerCount >= 2) {
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    
                    if (scale <= 1f) {
                        coroutineScope.launch {
                            animatedScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                            animatedOffset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMedium))
                            scale = 1f
                            offset = Offset.Zero
                        }
                    } else {
                        val constrainedOffset = constrainOffset(offset, scale)
                        if (constrainedOffset != offset) {
                            coroutineScope.launch {
                                animatedOffset.animateTo(constrainedOffset, spring(stiffness = Spring.StiffnessMedium))
                                offset = constrainedOffset
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = animatedScale.value * animatedScaleX
                scaleY = animatedScale.value * animatedScaleY
                translationX = animatedOffset.value.x
                translationY = animatedOffset.value.y
                rotationZ = animatedRotation
            }
        
        // 根据图片来源显示不同的内容
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        } else if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        }
    }
}


/**
 * 保存图片到相册
 */
private suspend fun saveImageToGallery(
    context: Context,
    imageSource: ImageSource,
    transform: ImageTransformState
): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = when (imageSource) {
            is ImageSource.Base64Source -> {
                val bytes = Base64.decode(imageSource.base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            is ImageSource.UriSource -> {
                context.contentResolver.openInputStream(imageSource.uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
            is ImageSource.StringSource -> {
                val path = imageSource.path
                when {
                    // 本地文件路径
                    path.startsWith("/") -> {
                        java.io.File(path).inputStream().use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                    // file:// URI
                    path.startsWith("file://") -> {
                        java.io.File(path.removePrefix("file://")).inputStream().use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                    // content:// URI
                    else -> {
                        val uri = Uri.parse(path)
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                }
            }
            is ImageSource.ResourceSource -> {
                BitmapFactory.decodeResource(context.resources, imageSource.resId)
            }
            is ImageSource.PainterSource -> null
        } ?: return@withContext false
        
        // 应用变换
        val transformedBitmap = applyTransform(bitmap, transform)
        
        // 保存到相册
        val filename = "IMG_${System.currentTimeMillis()}.png"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext false
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                transformedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!picturesDir.exists()) picturesDir.mkdirs()
            val file = File(picturesDir, filename)
            FileOutputStream(file).use { outputStream ->
                transformedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }
        
        if (transformedBitmap != bitmap) {
            transformedBitmap.recycle()
        }
        bitmap.recycle()
        
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * 应用旋转和镜像变换
 */
private fun applyTransform(bitmap: Bitmap, transform: ImageTransformState): Bitmap {
    // 将旋转角度归一化到 0-360 范围
    val normalizedRotation = ((transform.rotation % 360f) + 360f) % 360f
    
    if (normalizedRotation == 0f && !transform.flipHorizontal && !transform.flipVertical) {
        return bitmap
    }
    
    val matrix = Matrix().apply {
        if (normalizedRotation != 0f) {
            postRotate(normalizedRotation, bitmap.width / 2f, bitmap.height / 2f)
        }
        if (transform.flipHorizontal) {
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        if (transform.flipVertical) {
            postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
        }
    }
    
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// ==================== 便捷扩展函数 ====================

/**
 * 从字符串列表创建图片预览对话框
 */
@JvmName("ImagePreviewDialogFromPaths")
@Composable
fun ImagePreviewDialog(
    imagePaths: List<String>,
    initialIndex: Int = 0,
    config: ImagePreviewConfig = ImagePreviewConfig(),
    onDismiss: () -> Unit,
    onShare: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    onDownload: ((Int) -> Unit)? = null
) {
    ImagePreviewDialog(
        images = imagePaths.map { ImageSource.StringSource(it) },
        initialIndex = initialIndex,
        config = config,
        onDismiss = onDismiss,
        onShare = onShare,
        onDelete = onDelete,
        onDownload = onDownload
    )
}

/**
 * 从 URI 列表创建图片预览对话框
 */
@Composable
fun ImagePreviewDialogFromUris(
    imageUris: List<Uri>,
    initialIndex: Int = 0,
    config: ImagePreviewConfig = ImagePreviewConfig(),
    onDismiss: () -> Unit,
    onShare: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    onDownload: ((Int) -> Unit)? = null
) {
    ImagePreviewDialog(
        images = imageUris.map { ImageSource.UriSource(it) },
        initialIndex = initialIndex,
        config = config,
        onDismiss = onDismiss,
        onShare = onShare,
        onDelete = onDelete,
        onDownload = onDownload
    )
}

/**
 * 单图预览对话框
 */
@Composable
fun SingleImagePreviewDialog(
    image: ImageSource,
    config: ImagePreviewConfig = ImagePreviewConfig().copy(showIndicator = false),
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null
) {
    ImagePreviewDialog(
        images = listOf(image),
        initialIndex = 0,
        config = config,
        onDismiss = onDismiss,
        onShare = onShare?.let { { _ -> it() } },
        onDelete = onDelete?.let { { _ -> it() } },
        onDownload = onDownload?.let { { _ -> it() } }
    )
}

/**
 * 单图预览（字符串路径）
 */
@Composable
fun SingleImagePreviewDialog(
    imagePath: String,
    config: ImagePreviewConfig = ImagePreviewConfig().copy(showIndicator = false),
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null
) {
    SingleImagePreviewDialog(
        image = ImageSource.StringSource(imagePath),
        config = config,
        onDismiss = onDismiss,
        onShare = onShare,
        onDelete = onDelete,
        onDownload = onDownload
    )
}
