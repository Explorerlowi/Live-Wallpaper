package com.example.livewallpaper.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import android.view.WindowManager
import com.example.livewallpaper.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 图片编辑结果
 */
data class ImageEditResult(
    val cropRect: Rect,         // 裁剪区域（相对于原图的比例 0-1）
    val rotation: Int,          // 旋转角度（0, 90, 180, 270）
    val flipHorizontal: Boolean,// 水平翻转
    val flipVertical: Boolean   // 垂直翻转
)

/**
 * 通用图片编辑组件
 * 
 * 支持：
 * - 裁剪：拖动边框调整裁剪区域
 * - 旋转：90度为单位旋转
 * - 镜像：水平/垂直翻转
 * 
 * @param bitmap 要编辑的图片
 * @param initialResult 初始编辑参数
 * @param onConfirm 确认回调，返回编辑结果
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun ImageEditScreen(
    bitmap: Bitmap,
    initialResult: ImageEditResult = ImageEditResult(
        cropRect = Rect(0f, 0f, 1f, 1f),
        rotation = 0,
        flipHorizontal = false,
        flipVertical = false
    ),
    onConfirm: (ImageEditResult) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 设置 Dialog 的 Window 为沉浸式全屏模式
        val view = LocalView.current
        DisposableEffect(Unit) {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            
            dialogWindow?.let { w ->
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                
                WindowCompat.setDecorFitsSystemWindows(w, false)
                
                val insetsController = WindowCompat.getInsetsController(w, view)
                insetsController.apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            
            onDispose {
                dialogWindow?.let { w ->
                    val insetsController = WindowCompat.getInsetsController(w, view)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        
        BackHandler { onDismiss() }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ImageEditContent(
                bitmap = bitmap,
                initialResult = initialResult,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 图片编辑内容区域
 */
@Composable
private fun ImageEditContent(
    bitmap: Bitmap,
    initialResult: ImageEditResult,
    onConfirm: (ImageEditResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 编辑状态
    var rotation by remember { mutableIntStateOf(initialResult.rotation) }
    var flipHorizontal by remember { mutableStateOf(initialResult.flipHorizontal) }
    var flipVertical by remember { mutableStateOf(initialResult.flipVertical) }
    
    // 裁剪框状态（相对于图片显示区域的比例 0-1）
    var cropLeft by remember { mutableFloatStateOf(initialResult.cropRect.left) }
    var cropTop by remember { mutableFloatStateOf(initialResult.cropRect.top) }
    var cropRight by remember { mutableFloatStateOf(initialResult.cropRect.right) }
    var cropBottom by remember { mutableFloatStateOf(initialResult.cropRect.bottom) }
    
    // 容器尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 图片显示区域（考虑旋转后的尺寸）
    val displayedImageRect = remember(containerSize, bitmap, rotation, density) {
        calculateDisplayedImageRect(containerSize, bitmap, rotation, density.density)
    }
    
    // 是否正在拖动
    var isDragging by remember { mutableStateOf(false) }
    
    // 自动应用裁剪的延迟任务
    var autoApplyJob by remember { mutableStateOf<Job?>(null) }
    
    // 动画状态 - 用于在松手后平滑过渡到裁剪预览
    var showCroppedPreview by remember { mutableStateOf(false) }
    val previewAnimationProgress by animateFloatAsState(
        targetValue = if (showCroppedPreview) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "previewAnimation"
    )
    
    // 当拖动结束时，启动自动应用计时
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            autoApplyJob?.cancel()
            autoApplyJob = scope.launch {
                delay(1000L)
                showCroppedPreview = true
            }
        } else {
            autoApplyJob?.cancel()
            showCroppedPreview = false
        }
    }
    
    // 重置函数
    fun reset() {
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
        rotation = 0
        flipHorizontal = false
        flipVertical = false
        showCroppedPreview = false
    }
    
    // 旋转函数
    fun rotate() {
        rotation = (rotation + 90) % 360
        // 旋转后重置裁剪框
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
        showCroppedPreview = false
    }
    
    // 获取当前编辑结果
    fun getCurrentResult(): ImageEditResult {
        return ImageEditResult(
            cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom),
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical
        )
    }
    
    Column(modifier = modifier) {
        // 主要编辑区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            if (containerSize.width > 0 && containerSize.height > 0) {
                // 图片和裁剪框
                CropOverlay(
                    bitmap = bitmap,
                    rotation = rotation,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    imageRect = displayedImageRect,
                    showCroppedPreview = showCroppedPreview,
                    previewProgress = previewAnimationProgress,
                    onCropChange = { left, top, right, bottom ->
                        cropLeft = left
                        cropTop = top
                        cropRight = right
                        cropBottom = bottom
                    },
                    onDragStateChange = { dragging ->
                        isDragging = dragging
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 底部控制区域
        BottomControlBar(
            onReset = { reset() },
            onRotate = { rotate() },
            onCancel = onDismiss,
            onConfirm = { onConfirm(getCurrentResult()) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 裁剪叠加层
 */
@Composable
private fun CropOverlay(
    bitmap: Bitmap,
    rotation: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    imageRect: Rect,
    showCroppedPreview: Boolean,
    previewProgress: Float,
    onCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onDragStateChange: (isDragging: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 边框拖动检测阈值
    val edgeThreshold = with(density) { 40.dp.toPx() }
    val cornerThreshold = with(density) { 60.dp.toPx() }
    
    // 最小裁剪尺寸（相对于图片的比例）
    val minCropSize = 0.1f
    
    // 当前拖动的边/角
    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }
    
    // 将图片转换为 ImageBitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    
    // 使用 rememberUpdatedState 确保 pointerInput lambda 中使用最新的值
    val currentCropLeft by rememberUpdatedState(cropLeft)
    val currentCropTop by rememberUpdatedState(cropTop)
    val currentCropRight by rememberUpdatedState(cropRight)
    val currentCropBottom by rememberUpdatedState(cropBottom)
    
    // 计算裁剪框在屏幕上的实际位置
    val cropRect = remember(imageRect, cropLeft, cropTop, cropRight, cropBottom) {
        Rect(
            left = imageRect.left + imageRect.width * cropLeft,
            top = imageRect.top + imageRect.height * cropTop,
            right = imageRect.left + imageRect.width * cropRight,
            bottom = imageRect.top + imageRect.height * cropBottom
        )
    }
    
    // 使用 rememberUpdatedState 确保 cropRect 也是最新的
    val currentCropRect by rememberUpdatedState(cropRect)
    
    // 记录拖动开始时的位置（用于移动整个裁剪框）
    var dragStartPos by remember { mutableStateOf(Offset.Zero) }
    var dragStartCropLeft by remember { mutableFloatStateOf(0f) }
    var dragStartCropTop by remember { mutableFloatStateOf(0f) }
    var dragStartCropRight by remember { mutableFloatStateOf(0f) }
    var dragStartCropBottom by remember { mutableFloatStateOf(0f) }
    
    Canvas(
        modifier = modifier
            .pointerInput(imageRect) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downPos = down.position
                    
                    // 检测点击位置是否在边框附近（使用最新的 cropRect）
                    dragTarget = detectDragTarget(
                        position = downPos,
                        cropRect = currentCropRect,
                        edgeThreshold = edgeThreshold,
                        cornerThreshold = cornerThreshold
                    )
                    
                    if (dragTarget != null) {
                        onDragStateChange(true)
                        
                        // 记录拖动开始时的状态（用于移动整个裁剪框）
                        dragStartPos = downPos
                        dragStartCropLeft = currentCropLeft
                        dragStartCropTop = currentCropTop
                        dragStartCropRight = currentCropRight
                        dragStartCropBottom = currentCropBottom
                        
                        do {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Move) {
                                val change = event.changes.firstOrNull() ?: continue
                                val currentPos = change.position
                                
                                if (dragTarget == DragTarget.CENTER) {
                                    // 移动整个裁剪框
                                    moveCropRect(
                                        startPos = dragStartPos,
                                        currentPos = currentPos,
                                        imageRect = imageRect,
                                        startLeft = dragStartCropLeft,
                                        startTop = dragStartCropTop,
                                        startRight = dragStartCropRight,
                                        startBottom = dragStartCropBottom,
                                        onUpdate = onCropChange
                                    )
                                } else {
                                    // 调整边框大小（使用最新的 crop 值）
                                    updateCropRect(
                                        target = dragTarget!!,
                                        position = currentPos,
                                        imageRect = imageRect,
                                        currentLeft = currentCropLeft,
                                        currentTop = currentCropTop,
                                        currentRight = currentCropRight,
                                        currentBottom = currentCropBottom,
                                        minSize = minCropSize,
                                        onUpdate = onCropChange
                                    )
                                }
                                
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        
                        onDragStateChange(false)
                        dragTarget = null
                    }
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 绘制图片（带旋转和翻转）
        drawContext.canvas.nativeCanvas.apply {
            save()
            
            // 移动到图片中心
            translate(
                imageRect.left + imageRect.width / 2,
                imageRect.top + imageRect.height / 2
            )
            
            // 应用旋转
            rotate(rotation.toFloat())
            
            // 应用翻转
            scale(
                if (flipHorizontal) -1f else 1f,
                if (flipVertical) -1f else 1f
            )
            
            // 计算旋转后的绘制尺寸
            val drawWidth: Float
            val drawHeight: Float
            if (rotation == 90 || rotation == 270) {
                drawWidth = imageRect.height
                drawHeight = imageRect.width
            } else {
                drawWidth = imageRect.width
                drawHeight = imageRect.height
            }
            
            // 绘制图片
            drawImage(
                image = imageBitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (-drawWidth / 2).toInt(),
                    (-drawHeight / 2).toInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(
                    drawWidth.toInt(),
                    drawHeight.toInt()
                )
            )
            
            restore()
        }
        
        // 绘制裁剪框外的暗色遮罩
        // 当显示预览时，遮罩变得更深
        val baseMaskAlpha = 0.5f
        val previewMaskAlpha = 0.8f
        val maskAlpha = baseMaskAlpha + (previewMaskAlpha - baseMaskAlpha) * previewProgress
        val maskColor = Color.Black.copy(alpha = maskAlpha)
        
        // 上方遮罩
        if (cropRect.top > imageRect.top) {
            drawRect(
                color = maskColor,
                topLeft = Offset(imageRect.left, imageRect.top),
                size = Size(imageRect.width, cropRect.top - imageRect.top)
            )
        }
        
        // 下方遮罩
        if (cropRect.bottom < imageRect.bottom) {
            drawRect(
                color = maskColor,
                topLeft = Offset(imageRect.left, cropRect.bottom),
                size = Size(imageRect.width, imageRect.bottom - cropRect.bottom)
            )
        }
        
        // 左侧遮罩
        if (cropRect.left > imageRect.left) {
            drawRect(
                color = maskColor,
                topLeft = Offset(imageRect.left, cropRect.top),
                size = Size(cropRect.left - imageRect.left, cropRect.height)
            )
        }
        
        // 右侧遮罩
        if (cropRect.right < imageRect.right) {
            drawRect(
                color = maskColor,
                topLeft = Offset(cropRect.right, cropRect.top),
                size = Size(imageRect.right - cropRect.right, cropRect.height)
            )
        }
        
        // 绘制裁剪框边框
        val borderColor = Color.White
        val borderWidth = 2.dp.toPx()
        
        drawRect(
            color = borderColor,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            style = Stroke(width = borderWidth)
        )
        
        // 绘制3x3网格线
        val gridColor = Color.White.copy(alpha = 0.5f)
        val gridWidth = 1.dp.toPx()
        
        // 垂直网格线
        for (i in 1..2) {
            val x = cropRect.left + cropRect.width * i / 3
            drawLine(
                color = gridColor,
                start = Offset(x, cropRect.top),
                end = Offset(x, cropRect.bottom),
                strokeWidth = gridWidth
            )
        }
        
        // 水平网格线
        for (i in 1..2) {
            val y = cropRect.top + cropRect.height * i / 3
            drawLine(
                color = gridColor,
                start = Offset(cropRect.left, y),
                end = Offset(cropRect.right, y),
                strokeWidth = gridWidth
            )
        }
        
        // 绘制四个角的加粗标记
        val cornerLength = 20.dp.toPx()
        val cornerWidth = 4.dp.toPx()
        
        // 左上角
        drawLine(
            color = borderColor,
            start = Offset(cropRect.left, cropRect.top),
            end = Offset(cropRect.left + cornerLength, cropRect.top),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = borderColor,
            start = Offset(cropRect.left, cropRect.top),
            end = Offset(cropRect.left, cropRect.top + cornerLength),
            strokeWidth = cornerWidth
        )
        
        // 右上角
        drawLine(
            color = borderColor,
            start = Offset(cropRect.right - cornerLength, cropRect.top),
            end = Offset(cropRect.right, cropRect.top),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = borderColor,
            start = Offset(cropRect.right, cropRect.top),
            end = Offset(cropRect.right, cropRect.top + cornerLength),
            strokeWidth = cornerWidth
        )
        
        // 左下角
        drawLine(
            color = borderColor,
            start = Offset(cropRect.left, cropRect.bottom),
            end = Offset(cropRect.left + cornerLength, cropRect.bottom),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = borderColor,
            start = Offset(cropRect.left, cropRect.bottom - cornerLength),
            end = Offset(cropRect.left, cropRect.bottom),
            strokeWidth = cornerWidth
        )
        
        // 右下角
        drawLine(
            color = borderColor,
            start = Offset(cropRect.right - cornerLength, cropRect.bottom),
            end = Offset(cropRect.right, cropRect.bottom),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = borderColor,
            start = Offset(cropRect.right, cropRect.bottom - cornerLength),
            end = Offset(cropRect.right, cropRect.bottom),
            strokeWidth = cornerWidth
        )
        
        // 绘制四边中点的拖动手柄
        val handleLength = 16.dp.toPx()
        val handleWidth = 4.dp.toPx()
        
        // 上边中点
        drawLine(
            color = borderColor,
            start = Offset(cropRect.centerX - handleLength / 2, cropRect.top),
            end = Offset(cropRect.centerX + handleLength / 2, cropRect.top),
            strokeWidth = handleWidth
        )
        
        // 下边中点
        drawLine(
            color = borderColor,
            start = Offset(cropRect.centerX - handleLength / 2, cropRect.bottom),
            end = Offset(cropRect.centerX + handleLength / 2, cropRect.bottom),
            strokeWidth = handleWidth
        )
        
        // 左边中点
        drawLine(
            color = borderColor,
            start = Offset(cropRect.left, cropRect.centerY - handleLength / 2),
            end = Offset(cropRect.left, cropRect.centerY + handleLength / 2),
            strokeWidth = handleWidth
        )
        
        // 右边中点
        drawLine(
            color = borderColor,
            start = Offset(cropRect.right, cropRect.centerY - handleLength / 2),
            end = Offset(cropRect.right, cropRect.centerY + handleLength / 2),
            strokeWidth = handleWidth
        )
    }
}

/**
 * 拖动目标枚举
 */
private enum class DragTarget {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    CENTER  // 移动整个裁剪框
}

/**
 * 检测拖动目标
 */
private fun detectDragTarget(
    position: Offset,
    cropRect: Rect,
    edgeThreshold: Float,
    cornerThreshold: Float
): DragTarget? {
    val x = position.x
    val y = position.y
    
    // 检测四个角（优先级最高）
    if (abs(x - cropRect.left) < cornerThreshold && abs(y - cropRect.top) < cornerThreshold) {
        return DragTarget.TOP_LEFT
    }
    if (abs(x - cropRect.right) < cornerThreshold && abs(y - cropRect.top) < cornerThreshold) {
        return DragTarget.TOP_RIGHT
    }
    if (abs(x - cropRect.left) < cornerThreshold && abs(y - cropRect.bottom) < cornerThreshold) {
        return DragTarget.BOTTOM_LEFT
    }
    if (abs(x - cropRect.right) < cornerThreshold && abs(y - cropRect.bottom) < cornerThreshold) {
        return DragTarget.BOTTOM_RIGHT
    }
    
    // 检测四条边
    if (abs(x - cropRect.left) < edgeThreshold && y in cropRect.top..cropRect.bottom) {
        return DragTarget.LEFT
    }
    if (abs(x - cropRect.right) < edgeThreshold && y in cropRect.top..cropRect.bottom) {
        return DragTarget.RIGHT
    }
    if (abs(y - cropRect.top) < edgeThreshold && x in cropRect.left..cropRect.right) {
        return DragTarget.TOP
    }
    if (abs(y - cropRect.bottom) < edgeThreshold && x in cropRect.left..cropRect.right) {
        return DragTarget.BOTTOM
    }
    
    // 检测裁剪框内部（用于移动整个裁剪框）
    if (x in cropRect.left..cropRect.right && y in cropRect.top..cropRect.bottom) {
        return DragTarget.CENTER
    }
    
    return null
}

/**
 * 移动整个裁剪框
 */
private fun moveCropRect(
    startPos: Offset,
    currentPos: Offset,
    imageRect: Rect,
    startLeft: Float,
    startTop: Float,
    startRight: Float,
    startBottom: Float,
    onUpdate: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    // 计算拖动的偏移量（转换为相对于图片的比例）
    val deltaX = (currentPos.x - startPos.x) / imageRect.width
    val deltaY = (currentPos.y - startPos.y) / imageRect.height
    
    // 计算裁剪框的宽度和高度
    val cropWidth = startRight - startLeft
    val cropHeight = startBottom - startTop
    
    // 计算新的位置
    var newLeft = startLeft + deltaX
    var newTop = startTop + deltaY
    var newRight = startRight + deltaX
    var newBottom = startBottom + deltaY
    
    // 限制裁剪框不能超出图片边界
    if (newLeft < 0f) {
        newLeft = 0f
        newRight = cropWidth
    }
    if (newRight > 1f) {
        newRight = 1f
        newLeft = 1f - cropWidth
    }
    if (newTop < 0f) {
        newTop = 0f
        newBottom = cropHeight
    }
    if (newBottom > 1f) {
        newBottom = 1f
        newTop = 1f - cropHeight
    }
    
    onUpdate(newLeft, newTop, newRight, newBottom)
}

/**
 * 更新裁剪框
 */
private fun updateCropRect(
    target: DragTarget,
    position: Offset,
    imageRect: Rect,
    currentLeft: Float,
    currentTop: Float,
    currentRight: Float,
    currentBottom: Float,
    minSize: Float,
    onUpdate: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    // 将屏幕坐标转换为相对于图片的比例
    val relX = ((position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
    val relY = ((position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
    
    var newLeft = currentLeft
    var newTop = currentTop
    var newRight = currentRight
    var newBottom = currentBottom
    
    when (target) {
        DragTarget.LEFT -> {
            newLeft = relX.coerceAtMost(currentRight - minSize)
        }
        DragTarget.RIGHT -> {
            newRight = relX.coerceAtLeast(currentLeft + minSize)
        }
        DragTarget.TOP -> {
            newTop = relY.coerceAtMost(currentBottom - minSize)
        }
        DragTarget.BOTTOM -> {
            newBottom = relY.coerceAtLeast(currentTop + minSize)
        }
        DragTarget.TOP_LEFT -> {
            newLeft = relX.coerceAtMost(currentRight - minSize)
            newTop = relY.coerceAtMost(currentBottom - minSize)
        }
        DragTarget.TOP_RIGHT -> {
            newRight = relX.coerceAtLeast(currentLeft + minSize)
            newTop = relY.coerceAtMost(currentBottom - minSize)
        }
        DragTarget.BOTTOM_LEFT -> {
            newLeft = relX.coerceAtMost(currentRight - minSize)
            newBottom = relY.coerceAtLeast(currentTop + minSize)
        }
        DragTarget.BOTTOM_RIGHT -> {
            newRight = relX.coerceAtLeast(currentLeft + minSize)
            newBottom = relY.coerceAtLeast(currentTop + minSize)
        }

        else -> {}
    }
    
    onUpdate(newLeft, newTop, newRight, newBottom)
}

/**
 * 计算图片在容器中显示的实际区域
 */
private fun calculateDisplayedImageRect(
    containerSize: IntSize,
    bitmap: Bitmap,
    rotation: Int,
    density: Float = 2.5f
): Rect {
    if (containerSize.width <= 0 || containerSize.height <= 0) {
        return Rect.Zero
    }
    
    // 根据旋转角度获取图片的有效尺寸
    val imageWidth: Float
    val imageHeight: Float
    if (rotation == 90 || rotation == 270) {
        imageWidth = bitmap.height.toFloat()
        imageHeight = bitmap.width.toFloat()
    } else {
        imageWidth = bitmap.width.toFloat()
        imageHeight = bitmap.height.toFloat()
    }
    
    // 计算图片显示区域的尺寸
    // 两侧留出固定间距
    val horizontalPadding = 24f // dp
    val paddingPx = horizontalPadding * density
    
    val availableWidth = containerSize.width - paddingPx * 2
    val availableHeight = containerSize.height - paddingPx * 2
    
    val imageAspect = imageWidth / imageHeight
    val containerAspect = availableWidth / availableHeight
    
    val displayWidth: Float
    val displayHeight: Float
    
    if (imageAspect > containerAspect) {
        // 图片更宽，以宽度为准
        displayWidth = availableWidth
        displayHeight = availableWidth / imageAspect
    } else {
        // 图片更高，以高度为准
        displayHeight = availableHeight
        displayWidth = availableHeight * imageAspect
    }
    
    // 居中显示
    val left = (containerSize.width - displayWidth) / 2
    val top = (containerSize.height - displayHeight) / 2
    
    return Rect(
        left = left,
        top = top,
        right = left + displayWidth,
        bottom = top + displayHeight
    )
}

/**
 * 底部控制栏
 */
@Composable
private fun BottomControlBar(
    onReset: () -> Unit,
    onRotate: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    Column(
        modifier = modifier
            .background(Color.Black)
            .padding(bottom = navBarPadding)
    ) {
        // 操作按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 还原按钮
            ActionButton(
                icon = Icons.Default.Refresh,
                text = stringResource(R.string.image_editor_reset),
                onClick = onReset
            )
            
            // 旋转按钮
            ActionButton(
                icon = Icons.Default.RotateRight,
                text = stringResource(R.string.image_editor_rotate),
                onClick = onRotate
            )
        }
        
        // 取消/裁剪按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 取消按钮
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.image_editor_cancel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            
            // 裁剪按钮
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.image_editor_crop),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

/**
 * 工具函数：应用编辑结果到 Bitmap
 */
fun applyImageEditResult(
    source: Bitmap,
    result: ImageEditResult
): Bitmap {
    val matrix = Matrix()
    
    // 应用旋转
    if (result.rotation != 0) {
        matrix.postRotate(result.rotation.toFloat())
    }
    
    // 应用翻转
    if (result.flipHorizontal || result.flipVertical) {
        matrix.postScale(
            if (result.flipHorizontal) -1f else 1f,
            if (result.flipVertical) -1f else 1f
        )
    }
    
    // 先应用旋转和翻转
    val rotatedBitmap = Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, matrix, true
    )
    
    // 然后应用裁剪
    val cropRect = result.cropRect
    val cropX = (cropRect.left * rotatedBitmap.width).toInt().coerceAtLeast(0)
    val cropY = (cropRect.top * rotatedBitmap.height).toInt().coerceAtLeast(0)
    val cropWidth = ((cropRect.right - cropRect.left) * rotatedBitmap.width).toInt()
        .coerceAtMost(rotatedBitmap.width - cropX)
    val cropHeight = ((cropRect.bottom - cropRect.top) * rotatedBitmap.height).toInt()
        .coerceAtMost(rotatedBitmap.height - cropY)
    
    if (cropWidth <= 0 || cropHeight <= 0) {
        return rotatedBitmap
    }
    
    return Bitmap.createBitmap(rotatedBitmap, cropX, cropY, cropWidth, cropHeight)
}

// Rect 扩展属性
private val Rect.centerX: Float get() = (left + right) / 2
private val Rect.centerY: Float get() = (top + bottom) / 2
