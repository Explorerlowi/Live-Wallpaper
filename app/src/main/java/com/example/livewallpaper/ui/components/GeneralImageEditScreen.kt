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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
 * 拖拽交互类型
 */
private enum class DragAction {
    /** 无操作 */
    NONE,
    /** 移动整个图形 */
    MOVE,
    /** 缩放（右下角白点） */
    SCALE,
    /** 旋转（左上角旋转按钮） */
    ROTATE,
    /** 拖动箭头起点 */
    ARROW_START,
    /** 拖动箭头终点 */
    ARROW_END
}

/**
 * 绘制操作记录（用于撤销）
 *
 * 矩形/圆形/箭头支持变换属性（偏移、缩放、旋转），用于选中后的交互编辑。
 */
sealed class DrawOperation {
    /** 自由画笔路径 */
    data class PenStroke(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float
    ) : DrawOperation()

    /** 矩形（支持变换） */
    data class RectStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val translateOffset: Offset = Offset.Zero,
        val scaleFactor: Float = 1f,
        val rotationDeg: Float = 0f
    ) : DrawOperation()

    /** 圆形（支持变换） */
    data class CircleStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val translateOffset: Offset = Offset.Zero,
        val scaleFactor: Float = 1f,
        val rotationDeg: Float = 0f
    ) : DrawOperation()

    /** 箭头（两端可独立拖动，支持平移） */
    data class ArrowStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val translateOffset: Offset = Offset.Zero
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

/** 控制手柄半径（Canvas 坐标，绘制用） */
private const val HANDLE_RADIUS = 10f
/** 旋转按钮半径（比普通手柄大） */
private const val ROTATE_HANDLE_RADIUS = 32f
/** 控制手柄命中检测半径 */
private const val HANDLE_HIT_RADIUS = 22f
/** 旋转按钮命中检测半径 */
private const val ROTATE_HIT_RADIUS = 40f
/** 命中检测容差 */
private const val HIT_TOLERANCE = 30f


// ==================== 命中检测工具 ====================

/**
 * 计算点到线段的最短距离
 */
private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val t = ((ap.x * ab.x + ap.y * ab.y) / (ab.x * ab.x + ab.y * ab.y)).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * ab.x, a.y + t * ab.y)
    val dx = p.x - proj.x
    val dy = p.y - proj.y
    return sqrt(dx * dx + dy * dy)
}

/**
 * 获取矩形/圆形操作经过变换后的中心点
 */
private fun getShapeCenter(start: Offset, end: Offset, translate: Offset): Offset {
    val cx = (start.x + end.x) / 2f + translate.x
    val cy = (start.y + end.y) / 2f + translate.y
    return Offset(cx, cy)
}

/**
 * 获取矩形/圆形操作经过变换后的四个角（考虑旋转和缩放）
 * 返回顺序：左上、右上、右下、左下
 */
private fun getTransformedCorners(
    start: Offset, end: Offset,
    translate: Offset, scaleFactor: Float, rotationDeg: Float
): List<Offset> {
    val cx = (start.x + end.x) / 2f
    val cy = (start.y + end.y) / 2f
    val hw = kotlin.math.abs(end.x - start.x) / 2f * scaleFactor
    val hh = kotlin.math.abs(end.y - start.y) / 2f * scaleFactor
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    fun rotate(lx: Float, ly: Float): Offset {
        return Offset(
            cx + translate.x + lx * cosR - ly * sinR,
            cy + translate.y + lx * sinR + ly * cosR
        )
    }
    return listOf(
        rotate(-hw, -hh), // 左上
        rotate(hw, -hh),  // 右上
        rotate(hw, hh),   // 右下
        rotate(-hw, hh)   // 左下
    )
}

/**
 * 判断点是否在凸四边形内（使用叉积法）
 */
private fun isPointInQuad(p: Offset, corners: List<Offset>): Boolean {
    var sign = 0
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        if (cross > 0) {
            if (sign < 0) return false
            sign = 1
        } else if (cross < 0) {
            if (sign > 0) return false
            sign = -1
        }
    }
    return true
}

/**
 * 判断点是否在椭圆边框附近或内部（用于圆形选中检测）
 */
private fun isPointNearEllipse(
    p: Offset, start: Offset, end: Offset,
    translate: Offset, scaleFactor: Float, rotationDeg: Float,
    tolerance: Float
): Boolean {
    val cx = (start.x + end.x) / 2f + translate.x
    val cy = (start.y + end.y) / 2f + translate.y
    val a = kotlin.math.abs(end.x - start.x) / 2f * scaleFactor + tolerance
    val b = kotlin.math.abs(end.y - start.y) / 2f * scaleFactor + tolerance
    if (a < 1f || b < 1f) return false

    // 反旋转点到椭圆局部坐标
    val rad = Math.toRadians(-rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val dx = p.x - cx
    val dy = p.y - cy
    val lx = dx * cosR - dy * sinR
    val ly = dx * sinR + dy * cosR

    return (lx * lx) / (a * a) + (ly * ly) / (b * b) <= 1f
}

/**
 * 检测点击位置命中了哪个操作（从后往前检测，后绘制的优先）
 * 仅检测矩形、圆形、箭头（PEN 不可选中）
 *
 * @return 命中的操作在 drawHistory 中的索引，-1 表示未命中
 */
private fun hitTestOperation(
    canvasPos: Offset,
    drawHistory: List<DrawOperation>,
    selectedBrushShape: BrushShape
): Int {
    // 只有在画圆/框/箭头模式下才可选中
    if (selectedBrushShape == BrushShape.PEN) return -1

    for (i in drawHistory.indices.reversed()) {
        val op = drawHistory[i]
        val hit = when (op) {
            is DrawOperation.RectStroke -> {
                val corners = getTransformedCorners(
                    op.start, op.end, op.translateOffset, op.scaleFactor, op.rotationDeg
                )
                isPointInQuad(canvasPos, corners)
            }
            is DrawOperation.CircleStroke -> {
                isPointNearEllipse(
                    canvasPos, op.start, op.end,
                    op.translateOffset, op.scaleFactor, op.rotationDeg,
                    HIT_TOLERANCE
                )
            }
            is DrawOperation.ArrowStroke -> {
                val s = op.start + op.translateOffset
                val e = op.end + op.translateOffset
                pointToSegmentDistance(canvasPos, s, e) < HIT_TOLERANCE
            }
            is DrawOperation.PenStroke -> false
        }
        if (hit) return i
    }
    return -1
}

/**
 * 检测点击位置命中了选中图形的哪个控制手柄
 */
private fun hitTestHandle(
    canvasPos: Offset,
    op: DrawOperation,
    handleHitRadius: Float
): DragAction {
    when (op) {
        is DrawOperation.RectStroke -> {
            val corners = getTransformedCorners(
                op.start, op.end, op.translateOffset, op.scaleFactor, op.rotationDeg
            )
            // 右下角缩放白点（优先级最高）
            if (distanceBetween(canvasPos, corners[2]) < handleHitRadius) {
                return DragAction.SCALE
            }
            // 左上角旋转按钮（更大的命中区域）
            val rotatePos = getRotateHandlePosition(corners[0], corners)
            if (distanceBetween(canvasPos, rotatePos) < ROTATE_HIT_RADIUS) {
                return DragAction.ROTATE
            }
            // 在图形内部 → 移动
            if (isPointInQuad(canvasPos, corners)) {
                return DragAction.MOVE
            }
        }
        is DrawOperation.CircleStroke -> {
            val corners = getTransformedCorners(
                op.start, op.end, op.translateOffset, op.scaleFactor, op.rotationDeg
            )
            // 右下角缩放白点
            if (distanceBetween(canvasPos, corners[2]) < handleHitRadius) {
                return DragAction.SCALE
            }
            // 左上角旋转按钮（更大的命中区域）
            val rotatePos = getRotateHandlePosition(corners[0], corners)
            if (distanceBetween(canvasPos, rotatePos) < ROTATE_HIT_RADIUS) {
                return DragAction.ROTATE
            }
            // 在椭圆内部 → 移动（用边界框检测，更宽松）
            if (isPointNearEllipse(
                    canvasPos, op.start, op.end,
                    op.translateOffset, op.scaleFactor, op.rotationDeg, HIT_TOLERANCE
                )
            ) {
                return DragAction.MOVE
            }
        }
        is DrawOperation.ArrowStroke -> {
            val s = op.start + op.translateOffset
            val e = op.end + op.translateOffset
            // 起点白点
            if (distanceBetween(canvasPos, s) < handleHitRadius) {
                return DragAction.ARROW_START
            }
            // 终点白点
            if (distanceBetween(canvasPos, e) < handleHitRadius) {
                return DragAction.ARROW_END
            }
            // 线段上 → 移动
            if (pointToSegmentDistance(canvasPos, s, e) < HIT_TOLERANCE) {
                return DragAction.MOVE
            }
        }
        else -> {}
    }
    return DragAction.NONE
}

private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

/**
 * 旋转手柄位置：直接在左上角
 */
private fun getRotateHandlePosition(topLeft: Offset, @Suppress("UNUSED_PARAMETER") corners: List<Offset>): Offset {
    return topLeft
}


// ==================== 公开入口 ====================

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
 * - 矩形/圆形/箭头支持选中后移动、缩放、旋转
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

    val onDownload: () -> Unit = {
        if (saveState != SaveState.SAVING) {
            scope.launch {
                saveState = SaveState.SAVING
                saveProgress = 0f
                val success = saveEditedImageToGallery(
                    context, imageSource, drawHistory, containerSize
                ) { progress ->
                    saveProgress = progress
                }
                saveState = if (success) SaveState.SUCCESS else SaveState.FAILED
            }
        }
    }

    // 当前正在绘制的临时操作
    var currentDrawStart by remember { mutableStateOf<Offset?>(null) }
    var currentDrawEnd by remember { mutableStateOf<Offset?>(null) }
    var currentPenPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // ── 选中状态 ──
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // 切换画笔形状或退出画笔模式时取消选中
    LaunchedEffect(selectedBrushShape, isBrushMode) {
        selectedIndex = -1
    }

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

        // ── 绘制层：始终渲染绘制历史 + 选中手柄 ──
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
                drawHistory.forEachIndexed { index, op ->
                    drawOperationWithTransform(op)
                    // 绘制选中手柄
                    if (index == selectedIndex) {
                        drawSelectionHandles(op)
                    }
                }
                // 绘制当前正在创建的临时图形
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

                                val canvasDown = screenToCanvas(down.position)

                                // ── 优先检测：是否点击了选中图形的控制手柄 ──
                                val selOp = if (selectedIndex in drawHistory.indices) drawHistory[selectedIndex] else null
                                var dragAction = DragAction.NONE
                                if (selOp != null) {
                                    dragAction = hitTestHandle(canvasDown, selOp, HANDLE_HIT_RADIUS)
                                }

                                if (dragAction != DragAction.NONE && selOp != null) {
                                    // ── 拖拽控制手柄 ──
                                    val dragIdx = selectedIndex
                                    var prevCanvas = canvasDown

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        val change = pressed.first()
                                        val curCanvas = screenToCanvas(change.position)
                                        val delta = curCanvas - prevCanvas
                                        prevCanvas = curCanvas

                                        val currentOp = drawHistory[dragIdx]
                                        drawHistory[dragIdx] = applyDragAction(
                                            currentOp, dragAction, delta, curCanvas
                                        )
                                        change.consume()
                                    }
                                } else {
                                    // ── 检测是否点击了某个可选中的图形 ──
                                    val hitIdx = hitTestOperation(canvasDown, drawHistory, selectedBrushShape)

                                    if (hitIdx >= 0 && hitIdx != selectedIndex) {
                                        // 选中新图形，消费本次手势
                                        selectedIndex = hitIdx
                                        // 等待抬起
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            event.changes.forEach { it.consume() }
                                            if (pressed.isEmpty()) break
                                        }
                                    } else if (hitIdx >= 0 && hitIdx == selectedIndex) {
                                        // 已选中的图形上再次按下 → 移动
                                        val dragIdx = selectedIndex
                                        var prevCanvas = canvasDown

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.isEmpty()) break

                                            val change = pressed.first()
                                            val curCanvas = screenToCanvas(change.position)
                                            val delta = curCanvas - prevCanvas
                                            prevCanvas = curCanvas

                                            val currentOp = drawHistory[dragIdx]
                                            drawHistory[dragIdx] = applyDragAction(
                                                currentOp, DragAction.MOVE, delta, curCanvas
                                            )
                                            change.consume()
                                        }
                                    } else if (selectedIndex >= 0) {
                                        // ── 未命中任何图形但有选中 → 仅取消选中，不绘制 ──
                                        selectedIndex = -1
                                        // 等待抬起
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            event.changes.forEach { it.consume() }
                                            if (pressed.isEmpty()) break
                                        }
                                    } else {

                                        currentDrawStart = canvasDown
                                        currentDrawEnd = canvasDown
                                        if (selectedBrushShape == BrushShape.PEN) {
                                            currentPenPoints = listOf(canvasDown)
                                        }

                                        // 是否已切换到缩放模式
                                        var isZooming = false
                                        var prevCentroid = Offset.Unspecified
                                        var prevSpread = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }

                                            if (pressed.isEmpty()) {
                                                if (!isZooming) {
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
                                                        // 过滤掉尺寸过小的图形（点击未拖动产生的）
                                                        val minSize = 10f
                                                        val tooSmall = when (op) {
                                                            is DrawOperation.PenStroke -> op.points.size < 2
                                                            is DrawOperation.RectStroke ->
                                                                kotlin.math.abs(op.end.x - op.start.x) < minSize &&
                                                                kotlin.math.abs(op.end.y - op.start.y) < minSize
                                                            is DrawOperation.CircleStroke ->
                                                                kotlin.math.abs(op.end.x - op.start.x) < minSize &&
                                                                kotlin.math.abs(op.end.y - op.start.y) < minSize
                                                            is DrawOperation.ArrowStroke ->
                                                                distanceBetween(op.start, op.end) < minSize
                                                        }
                                                        if (!tooSmall) {
                                                            drawHistory.add(op)
                                                        }
                                                    }
                                                }
                                                currentDrawStart = null
                                                currentDrawEnd = null
                                                currentPenPoints = emptyList()
                                                break
                                            }

                                            if (pressed.size >= 2) {
                                                if (!isZooming) {
                                                    isZooming = true
                                                    currentDrawStart = null
                                                    currentDrawEnd = null
                                                    currentPenPoints = emptyList()
                                                }
                                                val p1 = pressed[0].position
                                                val p2 = pressed[1].position
                                                val centroid = Offset(
                                                    (p1.x + p2.x) / 2f,
                                                    (p1.y + p2.y) / 2f
                                                )
                                                val dx = p2.x - p1.x
                                                val dy = p2.y - p1.y
                                                val spread = sqrt(dx * dx + dy * dy)

                                                if (prevCentroid != Offset.Unspecified && prevSpread > 0f) {
                                                    val zoom = spread / prevSpread
                                                    val pan = centroid - prevCentroid
                                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                    val newOffset = if (newScale > 1f) {
                                                        clampOffset(offset + pan, newScale)
                                                    } else Offset.Zero
                                                    scale = newScale
                                                    offset = newOffset
                                                }
                                                prevCentroid = centroid
                                                prevSpread = spread
                                                event.changes.forEach { it.consume() }
                                            } else if (!isZooming) {
                                                val change = pressed.first()
                                                val pos = screenToCanvas(change.position)
                                                currentDrawEnd = pos
                                                if (selectedBrushShape == BrushShape.PEN) {
                                                    currentPenPoints = currentPenPoints + pos
                                                }
                                                change.consume()
                                            } else {
                                                prevCentroid = Offset.Unspecified
                                                prevSpread = 0f
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
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
                                    } else Offset.Zero
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
                                                    animatableOffset.animateTo(Offset.Zero, tween(300))
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
                // 形状选择面板
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
                                if (selectedIndex == drawHistory.lastIndex) {
                                    selectedIndex = -1
                                }
                                drawHistory.removeAt(drawHistory.lastIndex)
                                if (selectedIndex >= drawHistory.size) {
                                    selectedIndex = -1
                                }
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


// ==================== 拖拽变换应用 ====================

/**
 * 根据拖拽类型更新操作的变换属性
 */
private fun applyDragAction(
    op: DrawOperation,
    action: DragAction,
    delta: Offset,
    currentPos: Offset
): DrawOperation {
    return when (op) {
        is DrawOperation.RectStroke -> {
            when (action) {
                DragAction.MOVE -> op.copy(
                    translateOffset = op.translateOffset + delta
                )
                DragAction.SCALE -> {
                    val center = getShapeCenter(op.start, op.end, op.translateOffset)
                    val prevDist = distanceBetween(currentPos - delta, center)
                    val curDist = distanceBetween(currentPos, center)
                    if (prevDist > 1f) {
                        val ratio = curDist / prevDist
                        op.copy(scaleFactor = (op.scaleFactor * ratio).coerceIn(0.3f, 5f))
                    } else op
                }
                DragAction.ROTATE -> {
                    val center = getShapeCenter(op.start, op.end, op.translateOffset)
                    val prevAngle = atan2(
                        (currentPos.y - delta.y) - center.y,
                        (currentPos.x - delta.x) - center.x
                    )
                    val curAngle = atan2(
                        currentPos.y - center.y,
                        currentPos.x - center.x
                    )
                    val angleDelta = Math.toDegrees((curAngle - prevAngle).toDouble()).toFloat()
                    op.copy(rotationDeg = op.rotationDeg + angleDelta)
                }
                else -> op
            }
        }
        is DrawOperation.CircleStroke -> {
            when (action) {
                DragAction.MOVE -> op.copy(
                    translateOffset = op.translateOffset + delta
                )
                DragAction.SCALE -> {
                    val center = getShapeCenter(op.start, op.end, op.translateOffset)
                    val prevDist = distanceBetween(currentPos - delta, center)
                    val curDist = distanceBetween(currentPos, center)
                    if (prevDist > 1f) {
                        val ratio = curDist / prevDist
                        op.copy(scaleFactor = (op.scaleFactor * ratio).coerceIn(0.3f, 5f))
                    } else op
                }
                DragAction.ROTATE -> {
                    val center = getShapeCenter(op.start, op.end, op.translateOffset)
                    val prevAngle = atan2(
                        (currentPos.y - delta.y) - center.y,
                        (currentPos.x - delta.x) - center.x
                    )
                    val curAngle = atan2(
                        currentPos.y - center.y,
                        currentPos.x - center.x
                    )
                    val angleDelta = Math.toDegrees((curAngle - prevAngle).toDouble()).toFloat()
                    op.copy(rotationDeg = op.rotationDeg + angleDelta)
                }
                else -> op
            }
        }
        is DrawOperation.ArrowStroke -> {
            when (action) {
                DragAction.MOVE -> op.copy(
                    translateOffset = op.translateOffset + delta
                )
                DragAction.ARROW_START -> op.copy(
                    start = op.start + delta
                )
                DragAction.ARROW_END -> op.copy(
                    end = op.end + delta
                )
                else -> op
            }
        }
        else -> op
    }
}

// ==================== 绘制辅助函数 ====================

/**
 * 绘制操作（带变换支持）
 */
private fun DrawScope.drawOperationWithTransform(op: DrawOperation) {
    when (op) {
        is DrawOperation.PenStroke -> drawPenPath(op.points, op.color, op.strokeWidth)
        is DrawOperation.RectStroke -> {
            val cx = (op.start.x + op.end.x) / 2f + op.translateOffset.x
            val cy = (op.start.y + op.end.y) / 2f + op.translateOffset.y
            rotate(degrees = op.rotationDeg, pivot = Offset(cx, cy)) {
                val hw = kotlin.math.abs(op.end.x - op.start.x) / 2f * op.scaleFactor
                val hh = kotlin.math.abs(op.end.y - op.start.y) / 2f * op.scaleFactor
                val topLeft = Offset(cx - hw, cy - hh)
                val rectSize = Size(hw * 2f, hh * 2f)
                drawRect(
                    color = op.color,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(
                        width = op.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        is DrawOperation.CircleStroke -> {
            val cx = (op.start.x + op.end.x) / 2f + op.translateOffset.x
            val cy = (op.start.y + op.end.y) / 2f + op.translateOffset.y
            rotate(degrees = op.rotationDeg, pivot = Offset(cx, cy)) {
                val hw = kotlin.math.abs(op.end.x - op.start.x) / 2f * op.scaleFactor
                val hh = kotlin.math.abs(op.end.y - op.start.y) / 2f * op.scaleFactor
                val topLeft = Offset(cx - hw, cy - hh)
                val ovalSize = Size(hw * 2f, hh * 2f)
                drawOval(
                    color = op.color,
                    topLeft = topLeft,
                    size = ovalSize,
                    style = Stroke(
                        width = op.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        is DrawOperation.ArrowStroke -> {
            val s = op.start + op.translateOffset
            val e = op.end + op.translateOffset
            drawArrowShape(s, e, op.color, op.strokeWidth)
        }
    }
}

/**
 * 绘制选中图形的控制手柄
 */
private fun DrawScope.drawSelectionHandles(op: DrawOperation) {
    when (op) {
        is DrawOperation.RectStroke -> {
            val corners = getTransformedCorners(
                op.start, op.end, op.translateOffset, op.scaleFactor, op.rotationDeg
            )
            drawShapeSelectionHandles(corners)
        }
        is DrawOperation.CircleStroke -> {
            val corners = getTransformedCorners(
                op.start, op.end, op.translateOffset, op.scaleFactor, op.rotationDeg
            )
            drawShapeSelectionHandles(corners)
        }
        is DrawOperation.ArrowStroke -> {
            val s = op.start + op.translateOffset
            val e = op.end + op.translateOffset

            // 两端之间的白色连线
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = s,
                end = e,
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )

            // 起点白点
            drawCircle(
                color = Color.White,
                radius = HANDLE_RADIUS,
                center = s
            )
            drawCircle(
                color = Color.Gray,
                radius = HANDLE_RADIUS,
                center = s,
                style = Stroke(width = 1.5f)
            )

            // 终点白点
            drawCircle(
                color = Color.White,
                radius = HANDLE_RADIUS,
                center = e
            )
            drawCircle(
                color = Color.Gray,
                radius = HANDLE_RADIUS,
                center = e,
                style = Stroke(width = 1.5f)
            )
        }
        else -> {}
    }
}

/**
 * 绘制矩形/圆形选中时的边框、缩放手柄和旋转按钮
 */
private fun DrawScope.drawShapeSelectionHandles(corners: List<Offset>) {
    // 绘制边框
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = a,
            end = b,
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }

    // 右下角缩放白点
    drawCircle(
        color = Color.White,
        radius = HANDLE_RADIUS,
        center = corners[2]
    )
    drawCircle(
        color = Color.Gray,
        radius = HANDLE_RADIUS,
        center = corners[2],
        style = Stroke(width = 1.5f)
    )

    // 左上角旋转按钮（直接在角上，更大）
    val rotatePos = getRotateHandlePosition(corners[0], corners)
    drawCircle(
        color = Color.White,
        radius = ROTATE_HANDLE_RADIUS,
        center = rotatePos
    )
    drawCircle(
        color = Color.Gray.copy(alpha = 0.4f),
        radius = ROTATE_HANDLE_RADIUS,
        center = rotatePos,
        style = Stroke(width = 1.5f)
    )
    drawRotateIcon(rotatePos, ROTATE_HANDLE_RADIUS)
}

/**
 * 在指定位置绘制旋转图标（Material Refresh 图标 path）
 */
private fun DrawScope.drawRotateIcon(center: Offset, radius: Float) {
    val iconColor = Color(0xFF444444)
    val s = radius / 12f  // 24x24 viewBox，中心 12,12

    // Material Icons "Refresh" path，坐标已相对于 center 做偏移和缩放
    fun px(x: Float) = center.x + (x - 12f) * s
    fun py(y: Float) = center.y + (y - 12f) * s

    val refreshPath = Path().apply {
        moveTo(px(17.65f), py(6.35f))
        cubicTo(px(16.2f), py(4.9f), px(14.21f), py(4f), px(12f), py(4f))
        cubicTo(px(7.58f), py(4f), px(4.01f), py(7.58f), px(4.01f), py(12f))
        cubicTo(px(4.01f), py(16.42f), px(7.58f), py(20f), px(12f), py(20f))
        cubicTo(px(15.73f), py(20f), px(18.84f), py(17.45f), px(19.73f), py(14f))
        lineTo(px(17.65f), py(14f))
        cubicTo(px(16.83f), py(16.33f), px(14.61f), py(18f), px(12f), py(18f))
        cubicTo(px(8.69f), py(18f), px(6f), py(15.31f), px(6f), py(12f))
        cubicTo(px(6f), py(8.69f), px(8.69f), py(6f), px(12f), py(6f))
        cubicTo(px(13.66f), py(6f), px(15.14f), py(6.69f), px(16.22f), py(7.78f))
        lineTo(px(13f), py(11f))
        lineTo(px(20f), py(11f))
        lineTo(px(20f), py(4f))
        close()
    }
    drawPath(refreshPath, iconColor)
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
 * 绘制矩形（无变换，用于临时绘制预览）
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
 * 绘制圆形（无变换，用于临时绘制预览）
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
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
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
                            } else Modifier
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .then(
                            if (color == Color.White) {
                                Modifier.border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
                            } else Modifier
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
 * 将编辑后的图片（原图 + 绘制内容）保存到系统相册
 */
private suspend fun saveEditedImageToGallery(
    context: Context,
    source: String,
    drawHistory: List<DrawOperation>,
    containerSize: IntSize,
    onProgress: (Float) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f)
            val originalBitmap = loadBitmapFromSource(context, source, onProgress)
                ?: return@withContext false
            onProgress(0.5f)

            // 如果有绘制内容，合成到图片上
            val finalBitmap = if (drawHistory.isNotEmpty()) {
                renderDrawingsOntoBitmap(originalBitmap, drawHistory, containerSize)
            } else {
                originalBitmap
            }
            onProgress(0.7f)

            val result = saveBitmapToMediaStore(context, finalBitmap, onProgress)
            // 如果合成了新 Bitmap，回收它（但不回收原图，因为可能还在用）
            if (finalBitmap !== originalBitmap) {
                finalBitmap.recycle()
            }
            onProgress(1f)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 将 drawHistory 中的所有绘制操作渲染到原图 Bitmap 上
 *
 * 坐标转换逻辑：
 * 绘制时的坐标系是 Compose Canvas（containerSize 大小），图片以 ContentScale.Fit 居中显示。
 * 需要计算图片在 Canvas 中的实际显示区域，然后将绘制坐标映射到图片像素坐标。
 */
private fun renderDrawingsOntoBitmap(
    original: Bitmap,
    drawHistory: List<DrawOperation>,
    containerSize: IntSize
): Bitmap {
    val imgW = original.width.toFloat()
    val imgH = original.height.toFloat()
    val cW = containerSize.width.toFloat()
    val cH = containerSize.height.toFloat()

    if (cW <= 0f || cH <= 0f) return original

    // ContentScale.Fit: 图片等比缩放后居中
    val fitScale = min(cW / imgW, cH / imgH)
    val displayW = imgW * fitScale
    val displayH = imgH * fitScale
    val offsetX = (cW - displayW) / 2f
    val offsetY = (cH - displayH) / 2f

    // 屏幕 Canvas 坐标 → 图片像素坐标
    fun toImgX(sx: Float) = (sx - offsetX) / fitScale
    fun toImgY(sy: Float) = (sy - offsetY) / fitScale
    fun toImgOffset(o: Offset) = Offset(toImgX(o.x), toImgY(o.y))
    fun toImgLen(len: Float) = len / fitScale

    val result = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)

    for (op in drawHistory) {
        when (op) {
            is DrawOperation.PenStroke -> {
                if (op.points.size < 2) continue
                val paint = android.graphics.Paint().apply {
                    color = op.color.toArgb()
                    strokeWidth = toImgLen(op.strokeWidth)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val path = android.graphics.Path().apply {
                    val first = toImgOffset(op.points[0])
                    moveTo(first.x, first.y)
                    for (i in 1 until op.points.size) {
                        val pt = toImgOffset(op.points[i])
                        lineTo(pt.x, pt.y)
                    }
                }
                canvas.drawPath(path, paint)
            }

            is DrawOperation.RectStroke -> {
                val paint = android.graphics.Paint().apply {
                    color = op.color.toArgb()
                    strokeWidth = toImgLen(op.strokeWidth)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val cx = toImgX((op.start.x + op.end.x) / 2f + op.translateOffset.x)
                val cy = toImgY((op.start.y + op.end.y) / 2f + op.translateOffset.y)
                val hw = kotlin.math.abs(op.end.x - op.start.x) / 2f * op.scaleFactor
                val hh = kotlin.math.abs(op.end.y - op.start.y) / 2f * op.scaleFactor
                val hwImg = toImgLen(hw)
                val hhImg = toImgLen(hh)

                canvas.save()
                canvas.rotate(op.rotationDeg, cx, cy)
                canvas.drawRect(
                    cx - hwImg, cy - hhImg,
                    cx + hwImg, cy + hhImg,
                    paint
                )
                canvas.restore()
            }

            is DrawOperation.CircleStroke -> {
                val paint = android.graphics.Paint().apply {
                    color = op.color.toArgb()
                    strokeWidth = toImgLen(op.strokeWidth)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val cx = toImgX((op.start.x + op.end.x) / 2f + op.translateOffset.x)
                val cy = toImgY((op.start.y + op.end.y) / 2f + op.translateOffset.y)
                val hw = kotlin.math.abs(op.end.x - op.start.x) / 2f * op.scaleFactor
                val hh = kotlin.math.abs(op.end.y - op.start.y) / 2f * op.scaleFactor
                val hwImg = toImgLen(hw)
                val hhImg = toImgLen(hh)

                canvas.save()
                canvas.rotate(op.rotationDeg, cx, cy)
                val rect = android.graphics.RectF(
                    cx - hwImg, cy - hhImg,
                    cx + hwImg, cy + hhImg
                )
                canvas.drawOval(rect, paint)
                canvas.restore()
            }

            is DrawOperation.ArrowStroke -> {
                val paint = android.graphics.Paint().apply {
                    color = op.color.toArgb()
                    strokeWidth = toImgLen(op.strokeWidth)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val s = toImgOffset(op.start + op.translateOffset)
                val e = toImgOffset(op.end + op.translateOffset)

                // 线段
                canvas.drawLine(s.x, s.y, e.x, e.y, paint)

                // 箭头尖端
                val dx = e.x - s.x
                val dy = e.y - s.y
                val length = sqrt(dx * dx + dy * dy)
                if (length >= 1f) {
                    val arrowHeadLength = min(length * 0.3f, toImgLen(40f))
                    val arrowAngle = Math.toRadians(25.0)
                    val angle = atan2(dy.toDouble(), dx.toDouble())

                    val x1 = e.x - arrowHeadLength * cos(angle - arrowAngle).toFloat()
                    val y1 = e.y - arrowHeadLength * sin(angle - arrowAngle).toFloat()
                    val x2 = e.x - arrowHeadLength * cos(angle + arrowAngle).toFloat()
                    val y2 = e.y - arrowHeadLength * sin(angle + arrowAngle).toFloat()

                    canvas.drawLine(e.x, e.y, x1, y1, paint)
                    canvas.drawLine(e.x, e.y, x2, y2, paint)
                }
            }
        }
    }

    return result
}

/**
 * 将 Compose Color 转换为 Android ARGB int
 */
private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
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
