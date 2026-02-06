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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
 * 画笔形状类型
 */
enum class BrushShape {
    /** 自由画笔 */
    PEN,
    /** 矩形 */
    RECT,
    /** 圆形 */
    CIRCLE,
    /** 箭头 */
    ARROW
}

/**
 * 绘制操作记录（用于撤销）
 */
sealed class DrawOperation {
    /** 自由画笔路径 */
    data class PenStroke(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float
    ) : DrawOperation()

    /** 矩形 */
    data class RectStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float
    ) : DrawOperation()

    /** 圆形 */
    data class CircleStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float
    ) : DrawOperation()

    /** 箭头 */
    data class ArrowStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float
    ) : DrawOperation()
}

/**
 * 图片保存状态
 */
private enum class SaveState {
    IDLE, SAVING, SUCCESS, FAILED
}

/**
 * 预定义颜色列表
 */
private val BRUSH_COLORS = listOf(
    Color.White,
    Color.Red,
    Color(0xFFFFA500),  // 橙色
    Color.Yellow,
    Color.Green,
    Color(0xFF00BFFF),  // 天蓝
    Color(0xFF8A2BE2),  // 紫色
    Color.Black,
    Color(0xFF808080)   // 灰色
)

/**
 * 通用图片编辑界面（全屏 Dialog）
 *
 * 三层结构：
 * - 底层：纯黑背景
 * - 中层：图片展示（居中 Fit，支持双指缩放/平移）
 * - 顶层：可隐藏的按钮层（顶部返回/下载 + 底部工具栏）
 *
 * 画笔功能：
 * - 支持自由画笔、矩形、圆形、箭头四种形状
 * - 支持多种颜色选择
 * - 支持撤销操作
 *
 * @param imageSource 图片来源（URI 字符串、文件路径或网络 URL）
 * @param onNavigateBack 返回回调
 * @param onToolSelected 工具按钮点击回调
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
 * 编辑界面主体
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

    // 保存状态
    var saveState by remember { mutableStateOf(SaveState.IDLE) }
    var saveProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(saveState) {
        if (saveState == SaveState.SUCCESS || saveState == SaveState.FAILED) {
            delay(1500L)
            saveState = SaveState.IDLE
        }
    }

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
    val currentStatusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val currentNavBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var statusInset by remember { mutableStateOf(currentStatusTop) }
    var navInset by remember { mutableStateOf(currentNavBottom) }
    LaunchedEffect(currentStatusTop) {
        if (currentStatusTop > 0.dp && statusInset == 0.dp) statusInset = currentStatusTop
    }
    LaunchedEffect(currentNavBottom) {
        if (currentNavBottom > 0.dp && navInset == 0.dp) navInset = currentNavBottom
    }

    // 缩放与平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val animatableScale = remember { Animatable(1f) }
    val animatableOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // ── 画笔状态 ──
    var isBrushMode by remember { mutableStateOf(false) }
    var selectedBrushShape by remember { mutableStateOf(BrushShape.PEN) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var showShapePicker by remember { mutableStateOf(false) }
    val drawHistory = remember { mutableStateListOf<DrawOperation>() }

    // 当前正在绘制的临时操作
    var currentDrawStart by remember { mutableStateOf<Offset?>(null) }
    var currentDrawEnd by remember { mutableStateOf<Offset?>(null) }
    var currentPenPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val brushStrokeWidth = 6f

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
        // ── 图片层 ──
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

        // ── 绘制层：始终渲染绘制历史 ──
        if (drawHistory.isNotEmpty() || (isBrushMode && currentDrawStart != null)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                drawHistory.forEach { op -> drawOperation(op) }
                val start = currentDrawStart
                val end = currentDrawEnd
                if (start != null && end != null) {
                    when (selectedBrushShape) {
                        BrushShape.PEN -> {
                            if (currentPenPoints.size >= 2) {
                                drawPenPath(currentPenPoints, selectedColor, brushStrokeWidth)
                            }
                        }
                        BrushShape.RECT -> drawRectShape(start, end, selectedColor, brushStrokeWidth)
                        BrushShape.CIRCLE -> drawCircleShape(start, end, selectedColor, brushStrokeWidth)
                        BrushShape.ARROW -> drawArrowShape(start, end, selectedColor, brushStrokeWidth)
                    }
                }
            }
        }

        // ── 统一手势层 ──
        // 画笔模式：单指绘制 + 双指缩放/平移，全部在同一个 awaitEachGesture 中处理
        // 非画笔模式：双指缩放/平移 + 单击/双击，使用原有的 detect* API
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isBrushMode) {
                        Modifier.pointerInput(selectedBrushShape, selectedColor) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                // 坐标转换：屏幕坐标 → Canvas 坐标
                                fun screenToCanvas(screenPos: Offset): Offset {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    return Offset(
                                        x = (screenPos.x - cx - offset.x) / scale + cx,
                                        y = (screenPos.y - cy - offset.y) / scale + cy
                                    )
                                }

                                val canvasStart = screenToCanvas(down.position)
                                currentDrawStart = canvasStart
                                currentDrawEnd = canvasStart
                                if (selectedBrushShape == BrushShape.PEN) {
                                    currentPenPoints = listOf(canvasStart)
                                }

                                // 是否已切换到缩放模式
                                var isZooming = false
                                var prevCentroid = Offset.Unspecified
                                var prevSpread = 0f

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.isEmpty()) {
                                        // 所有手指抬起
                                        if (!isZooming) {
                                            // 完成绘制
                                            val s = currentDrawStart
                                            val e = currentDrawEnd
                                            if (s != null && e != null) {
                                                val op = when (selectedBrushShape) {
                                                    BrushShape.PEN -> DrawOperation.PenStroke(
                                                        points = currentPenPoints.toList(),
                                                        color = selectedColor,
                                                        strokeWidth = brushStrokeWidth
                                                    )
                                                    BrushShape.RECT -> DrawOperation.RectStroke(
                                                        start = s, end = e,
                                                        color = selectedColor,
                                                        strokeWidth = brushStrokeWidth
                                                    )
                                                    BrushShape.CIRCLE -> DrawOperation.CircleStroke(
                                                        start = s, end = e,
                                                        color = selectedColor,
                                                        strokeWidth = brushStrokeWidth
                                                    )
                                                    BrushShape.ARROW -> DrawOperation.ArrowStroke(
                                                        start = s, end = e,
                                                        color = selectedColor,
                                                        strokeWidth = brushStrokeWidth
                                                    )
                                                }
                                                drawHistory.add(op)
                                            }
                                        }
                                        currentDrawStart = null
                                        currentDrawEnd = null
                                        currentPenPoints = emptyList()
                                        prevCentroid = Offset.Unspecified
                                        break
                                    }

                                    if (pressed.size >= 2) {
                                        // 双指：切换到缩放模式
                                        if (!isZooming) {
                                            // 首次检测到双指，取消当前绘制
                                            isZooming = true
                                            currentDrawStart = null
                                            currentDrawEnd = null
                                            currentPenPoints = emptyList()
                                        }

                                        // 计算双指中心和间距
                                        val p1 = pressed[0].position
                                        val p2 = pressed[1].position
                                        val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                        val dx = p2.x - p1.x
                                        val dy = p2.y - p1.y
                                        val spread = sqrt(dx * dx + dy * dy)

                                        if (prevCentroid != Offset.Unspecified && prevSpread > 0f) {
                                            val zoom = spread / prevSpread
                                            val pan = centroid - prevCentroid
                                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                                            val newOffset = if (newScale > 1f) {
                                                clampOffset(offset + pan, newScale)
                                            } else {
                                                Offset.Zero
                                            }
                                            scale = newScale
                                            offset = newOffset
                                        }
                                        prevCentroid = centroid
                                        prevSpread = spread

                                        // 消费所有事件，防止穿透
                                        event.changes.forEach { it.consume() }
                                    } else if (!isZooming) {
                                        // 单指且未进入缩放模式：绘制
                                        val change = pressed.first()
                                        val pos = screenToCanvas(change.position)
                                        currentDrawEnd = pos
                                        if (selectedBrushShape == BrushShape.PEN) {
                                            currentPenPoints = currentPenPoints + pos
                                        }
                                        change.consume()
                                    } else {
                                        // 缩放模式中只剩一指，重置 prevCentroid 等待下次双指
                                        prevCentroid = Offset.Unspecified
                                        prevSpread = 0f
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    } else {
                        // 非画笔模式：双指缩放 + 单击/双击
                        Modifier
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
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
                                    onTap = { showControls = !showControls },
                                    onDoubleTap = {
                                        scope.launch {
                                            if (scale > 1.1f) {
                                                launch {
                                                    animatableScale.snapTo(scale)
                                                    animatableScale.animateTo(1f, tween(300))
                                                }
                                                launch {
                                                    animatableOffset.snapTo(offset)
                                                    animatableOffset.animateTo(
                                                        Offset.Zero,
                                                        tween(300)
                                                    )
                                                }
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                val targetScale = 2.5f
                                                val focusX =
                                                    (containerSize.width / 2f - it.x) * (targetScale - 1f)
                                                val focusY =
                                                    (containerSize.height / 2f - it.y) * (targetScale - 1f)
                                                val targetOffset =
                                                    clampOffset(
                                                        Offset(focusX, focusY),
                                                        targetScale
                                                    )
                                                launch {
                                                    animatableScale.snapTo(scale)
                                                    animatableScale.animateTo(
                                                        targetScale,
                                                        tween(300)
                                                    )
                                                }
                                                launch {
                                                    animatableOffset.snapTo(offset)
                                                    animatableOffset.animateTo(
                                                        targetOffset,
                                                        tween(300)
                                                    )
                                                }
                                                scale = targetScale
                                                offset = targetOffset
                                            }
                                        }
                                    }
                                )
                            }
                    }
                )
        )

        // 双击动画同步
        LaunchedEffect(animatableScale.value, animatableOffset.value) {
            if (animatableScale.isRunning || animatableOffset.isRunning) {
                scale = animatableScale.value
                offset = animatableOffset.value
            }
        }

        // ── 顶部栏 ──
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

        // ── 底部区域（画笔工具栏 + 主工具栏） ──
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navInset + 12.dp)
            ) {
                // 形状选择面板（点击画笔选择按钮后展开）
                AnimatedVisibility(
                    visible = isBrushMode && showShapePicker,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it }
                ) {
                    ShapePickerBar(
                        selectedShape = selectedBrushShape,
                        onShapeSelected = { shape ->
                            selectedBrushShape = shape
                            showShapePicker = false
                        }
                    )
                }

                // 画笔工具栏（颜色选择 + 撤销）
                AnimatedVisibility(
                    visible = isBrushMode,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it }
                ) {
                    BrushToolBar(
                        selectedColor = selectedColor,
                        canUndo = drawHistory.isNotEmpty(),
                        onToggleShapePicker = { showShapePicker = !showShapePicker },
                        onColorSelected = { selectedColor = it },
                        onUndo = {
                            if (drawHistory.isNotEmpty()) {
                                drawHistory.removeAt(drawHistory.lastIndex)
                            }
                        }
                    )
                }

                // 主工具栏
                EditBottomBar(
                    isBrushMode = isBrushMode,
                    onToolSelected = { tool ->
                        when (tool) {
                            EditTool.BRUSH -> {
                                isBrushMode = !isBrushMode
                                if (!isBrushMode) showShapePicker = false
                            }
                            else -> onToolSelected(tool)
                        }
                    },
                    onDone = onDone,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 保存浮层 ──
        SaveOverlay(
            saveState = saveState,
            progress = saveProgress,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ==================== 绘制辅助函数 ====================

/**
 * 根据操作类型分发绘制
 */
private fun DrawScope.drawOperation(op: DrawOperation) {
    when (op) {
        is DrawOperation.PenStroke -> drawPenPath(op.points, op.color, op.strokeWidth)
        is DrawOperation.RectStroke -> drawRectShape(op.start, op.end, op.color, op.strokeWidth)
        is DrawOperation.CircleStroke -> drawCircleShape(op.start, op.end, op.color, op.strokeWidth)
        is DrawOperation.ArrowStroke -> drawArrowShape(op.start, op.end, op.color, op.strokeWidth)
    }
}

/**
 * 绘制自由画笔路径
 */
private fun DrawScope.drawPenPath(points: List<Offset>, color: Color, strokeWidth: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * 绘制矩形
 */
private fun DrawScope.drawRectShape(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
    val size = Size(
        width = kotlin.math.abs(end.x - start.x),
        height = kotlin.math.abs(end.y - start.y)
    )
    drawRect(
        color = color,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * 绘制圆形（椭圆，由起点和终点确定的矩形内切）
 */
private fun DrawScope.drawCircleShape(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
    val size = Size(
        width = kotlin.math.abs(end.x - start.x),
        height = kotlin.math.abs(end.y - start.y)
    )
    drawOval(
        color = color,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * 绘制箭头（线段 + 箭头尖端）
 */
private fun DrawScope.drawArrowShape(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    // 主线段
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // 箭头尖端
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return

    val arrowHeadLength = min(length * 0.3f, 40f)
    val arrowAngle = Math.toRadians(25.0)
    val angle = atan2(dy.toDouble(), dx.toDouble())

    val x1 = end.x - arrowHeadLength * cos(angle - arrowAngle).toFloat()
    val y1 = end.y - arrowHeadLength * sin(angle - arrowAngle).toFloat()
    val x2 = end.x - arrowHeadLength * cos(angle + arrowAngle).toFloat()
    val y2 = end.y - arrowHeadLength * sin(angle + arrowAngle).toFloat()

    val arrowPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(x1, y1)
        moveTo(end.x, end.y)
        lineTo(x2, y2)
    }
    drawPath(
        path = arrowPath,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// ==================== UI 组件 ====================

/**
 * 形状选择面板
 * 参考第二张截图：画笔、方形、圆形、箭头 四个图标横排
 */
@Composable
private fun ShapePickerBar(
    selectedShape: BrushShape,
    onShapeSelected: (BrushShape) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShapeItem(
            shape = BrushShape.PEN,
            isSelected = selectedShape == BrushShape.PEN,
            label = stringResource(R.string.image_edit_shape_pen),
            onClick = { onShapeSelected(BrushShape.PEN) }
        )
        ShapeItem(
            shape = BrushShape.RECT,
            isSelected = selectedShape == BrushShape.RECT,
            label = stringResource(R.string.image_edit_shape_rect),
            onClick = { onShapeSelected(BrushShape.RECT) }
        )
        ShapeItem(
            shape = BrushShape.CIRCLE,
            isSelected = selectedShape == BrushShape.CIRCLE,
            label = stringResource(R.string.image_edit_shape_circle),
            onClick = { onShapeSelected(BrushShape.CIRCLE) }
        )
        ShapeItem(
            shape = BrushShape.ARROW,
            isSelected = selectedShape == BrushShape.ARROW,
            label = stringResource(R.string.image_edit_shape_arrow),
            onClick = { onShapeSelected(BrushShape.ARROW) }
        )
    }
}

/**
 * 形状选择项：使用 Canvas 绘制对应图标
 */
@Composable
private fun ShapeItem(
    shape: BrushShape,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(Color.White.copy(alpha = 0.2f))
                else Modifier
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (shape) {
                BrushShape.PEN -> {
                    // 画笔图标：一条曲线
                    val path = Path().apply {
                        moveTo(w * 0.15f, h * 0.85f)
                        cubicTo(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, w * 0.85f, h * 0.15f)
                    }
                    drawPath(path, tint, style = stroke)
                }
                BrushShape.RECT -> {
                    drawRect(
                        color = tint,
                        topLeft = Offset(w * 0.15f, h * 0.2f),
                        size = Size(w * 0.7f, h * 0.6f),
                        style = stroke
                    )
                }
                BrushShape.CIRCLE -> {
                    drawOval(
                        color = tint,
                        topLeft = Offset(w * 0.1f, h * 0.1f),
                        size = Size(w * 0.8f, h * 0.8f),
                        style = stroke
                    )
                }
                BrushShape.ARROW -> {
                    // 箭头图标
                    drawLine(tint, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.15f), strokeWidth = 2f, cap = StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(w * 0.85f, h * 0.15f)
                        lineTo(w * 0.55f, h * 0.18f)
                        moveTo(w * 0.85f, h * 0.15f)
                        lineTo(w * 0.82f, h * 0.45f)
                    }
                    drawPath(path, tint, style = stroke)
                }
            }
        }
    }
}

/**
 * 画笔工具栏：画笔选择按钮 + 颜色列表 + 撤销按钮
 * 参考第一张截图的中间行
 */
@Composable
private fun BrushToolBar(
    selectedColor: Color,
    canUndo: Boolean,
    onToggleShapePicker: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 画笔选择按钮（带选中圈）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White, CircleShape)
                .clickable(
                    onClick = onToggleShapePicker,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.image_edit_brush),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 颜色选择列表（可横向滚动）
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BRUSH_COLORS.forEach { color ->
                val isSelected = color == selectedColor
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 28.dp else 24.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .then(
                            if (color == Color.White) {
                                Modifier.border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            onClick = { onColorSelected(color) },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 撤销按钮
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.image_edit_undo),
                tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 保存进度/结果浮层
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
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(56.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(56.dp),
                                color = Color(0xFF7EC8E3),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round
                            )
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
            else -> { /* IDLE */ }
        }
    }
}

/**
 * 顶部栏：左侧返回，右侧下载
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
 * 底部主工具栏：画笔 · 马赛克 · 文字 · 裁剪 ＋ 完成按钮
 */
@Composable
private fun EditBottomBar(
    isBrushMode: Boolean,
    onToolSelected: (EditTool) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ToolIcon(
                icon = Icons.Default.Brush,
                label = stringResource(R.string.image_edit_brush),
                isActive = isBrushMode,
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
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF4A90D9) else Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ==================== 图片保存工具 ====================

/**
 * 将图片保存到系统相册（带进度回调）
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
 * 根据来源类型加载 Bitmap
 */
private fun loadBitmapFromSource(
    context: Context,
    source: String,
    onProgress: (Float) -> Unit
): Bitmap? {
    onProgress(0.1f)
    return when {
        source.startsWith("content://") -> {
            onProgress(0.2f)
            context.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                onProgress(0.3f)
                BitmapFactory.decodeStream(stream)
            }
        }
        source.startsWith("http://") || source.startsWith("https://") -> {
            onProgress(0.15f)
            URL(source).openStream()?.use { stream ->
                onProgress(0.3f)
                BitmapFactory.decodeStream(stream)
            }
        }
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
