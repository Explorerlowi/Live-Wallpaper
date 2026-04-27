package com.example.livewallpaper.ui.components

import android.util.Base64
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.livewallpaper.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 图片对比预览对话框
 *
 * 两张图片重叠显示，长按隐藏上层图片以查看下层图片。
 * 支持双指缩放、拖动和双击缩放，风格与 [ImagePreviewDialog] 保持一致。
 *
 * @param topImage 上层图片（默认显示）
 * @param bottomImage 下层图片（长按时显示）
 * @param onDismiss 关闭回调
 * @param onSwap 交换上下层回调
 */
@Composable
fun ImageComparePreviewDialog(
    topImage: ImageSource,
    bottomImage: ImageSource,
    onDismiss: () -> Unit
) {
    // 控制 UI 元素的显示/隐藏
    var showControls by remember { mutableStateOf(true) }
    // 长按时隐藏上层图片
    var hideTopImage by remember { mutableStateOf(false) }

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
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // 图片对比区域
            CompareImageContent(
                topImage = topImage,
                bottomImage = bottomImage,
                hideTopImage = hideTopImage,
                onTap = { showControls = !showControls },
                onLongPressStart = { hideTopImage = true },
                onLongPressEnd = { hideTopImage = false }
            )

            // 顶部控制栏
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    // 中间标题
                    Text(
                        text = stringResource(R.string.compare_preview_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // 右侧关闭按钮
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 底部区域
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 长按提示标签
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Text(
                        text = if (hideTopImage) {
                            stringResource(R.string.compare_image_bottom)
                        } else {
                            stringResource(R.string.compare_hint_long_press)
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}


/**
 * 对比图片内容区域
 *
 * 两张图片重叠显示，支持双指缩放、拖动、双击缩放。
 * 长按时隐藏上层图片以查看下层，松手恢复。
 */
@Composable
private fun CompareImageContent(
    topImage: ImageSource,
    bottomImage: ImageSource,
    hideTopImage: Boolean,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val animatedScale = remember { Animatable(1f) }

    // 上层图片透明度动画
    val topAlpha by animateFloatAsState(
        targetValue = if (hideTopImage) 0f else 1f,
        animationSpec = tween(200),
        label = "topAlpha"
    )

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val maxScale = 5f
    val minScale = 0.5f
    val doubleTapScale = 2.5f

    fun calculateBounds(currentScale: Float, size: Size): Offset {
        if (currentScale <= 1f || size == Size.Zero) return Offset.Zero
        val scaledWidth = size.width * currentScale
        val scaledHeight = size.height * currentScale
        val maxX = ((scaledWidth - size.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - size.height) / 2f).coerceAtLeast(0f)
        return Offset(maxX, maxY)
    }

    fun constrainOffset(off: Offset, currentScale: Float): Offset {
        val bounds = calculateBounds(currentScale, containerSize)
        return Offset(
            x = off.x.coerceIn(-bounds.x, bounds.x),
            y = off.y.coerceIn(-bounds.y, bounds.y)
        )
    }

    // 构建 painter
    val topPainter = rememberImagePainter(topImage)
    val bottomPainter = rememberImagePainter(bottomImage)
    val topBitmap = rememberBase64Bitmap(topImage)
    val bottomBitmap = rememberBase64Bitmap(bottomImage)

    // 双击检测状态
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() },
        contentAlignment = Alignment.Center
    ) {
        val imageModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val doubleTapTimeout = 300L // 双击间隔阈值（毫秒）
                val tapSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    val downPos = down.position

                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    var isLongPress = false
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                    // 启动长按检测
                    val longPressJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(longPressTimeout)
                        isLongPress = true
                        onLongPressStart()
                    }

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }

                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize =
                                    event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()
                                if (zoomMotion > tapSlop || panMotion > tapSlop) {
                                    pastTouchSlop = true
                                    longPressJob.cancel()
                                }
                            }

                            if (pastTouchSlop) {
                                if (zoomChange != 1f) {
                                    val newScale =
                                        (scale * zoomChange).coerceIn(minScale, maxScale)
                                    val centroid =
                                        event.calculateCentroid(useCurrent = false)
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
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }

                                if (panChange != Offset.Zero && scale > 1f) {
                                    val newOffset = offset + panChange
                                    offset = constrainOffset(newOffset, scale)
                                    coroutineScope.launch {
                                        animatedOffset.snapTo(offset)
                                    }
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    longPressJob.cancel()

                    // 手指抬起后的处理
                    if (isLongPress) {
                        // 长按结束 → 恢复上层图片
                        onLongPressEnd()
                    } else if (!pastTouchSlop) {
                        // 没有移动过 → 判断是单击还是双击
                        val upTime = System.currentTimeMillis()
                        val timeSinceLastTap = upTime - lastTapTime
                        val distFromLastTap = (downPos - lastTapOffset).getDistance()

                        if (timeSinceLastTap < doubleTapTimeout && distFromLastTap < tapSlop * 3) {
                            // 双击
                            lastTapTime = 0L
                            coroutineScope.launch {
                                if (scale > 1f) {
                                    animatedScale.animateTo(
                                        1f,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
                                    animatedOffset.animateTo(
                                        Offset.Zero,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val centerX = containerSize.width / 2f
                                    val centerY = containerSize.height / 2f
                                    val offsetX =
                                        (centerX - downPos.x) * (doubleTapScale - 1f)
                                    val offsetY =
                                        (centerY - downPos.y) * (doubleTapScale - 1f)
                                    val targetOffset = constrainOffset(
                                        Offset(offsetX, offsetY),
                                        doubleTapScale
                                    )
                                    animatedScale.animateTo(
                                        doubleTapScale,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
                                    animatedOffset.animateTo(
                                        targetOffset,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
                                    scale = doubleTapScale
                                    offset = targetOffset
                                }
                            }
                        } else {
                            // 记录本次点击，等待可能的第二次点击
                            lastTapTime = upTime
                            lastTapOffset = downPos
                            // 延迟后判断是否为单击（没有后续双击）
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(doubleTapTimeout)
                                if (lastTapTime == upTime) {
                                    // 没有被双击覆盖 → 单击
                                    onTap()
                                }
                            }
                        }
                    }

                    // 缩放回弹
                    if (scale <= 1f && pastTouchSlop) {
                        coroutineScope.launch {
                            animatedScale.animateTo(
                                1f,
                                spring(stiffness = Spring.StiffnessMedium)
                            )
                            animatedOffset.animateTo(
                                Offset.Zero,
                                spring(stiffness = Spring.StiffnessMedium)
                            )
                            scale = 1f
                            offset = Offset.Zero
                        }
                    } else if (pastTouchSlop) {
                        val constrainedOffset = constrainOffset(offset, scale)
                        if (constrainedOffset != offset) {
                            coroutineScope.launch {
                                animatedOffset.animateTo(
                                    constrainedOffset,
                                    spring(stiffness = Spring.StiffnessMedium)
                                )
                                offset = constrainedOffset
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                translationX = animatedOffset.value.x
                translationY = animatedOffset.value.y
            }

        // 下层图片（始终显示）
        Box(modifier = imageModifier) {
            if (bottomBitmap != null) {
                Image(
                    bitmap = bottomBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.compare_image_bottom),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bottomPainter != null) {
                Image(
                    painter = bottomPainter,
                    contentDescription = stringResource(R.string.compare_image_bottom),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 上层图片（长按时淡出）
            if (topBitmap != null) {
                Image(
                    bitmap = topBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.compare_image_top),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = topAlpha }
                )
            } else if (topPainter != null) {
                Image(
                    painter = topPainter,
                    contentDescription = stringResource(R.string.compare_image_top),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = topAlpha }
                )
            }
        }
    }
}

/**
 * 根据 [ImageSource] 创建 Coil Painter（非 Base64 类型）
 */
@Composable
private fun rememberImagePainter(source: ImageSource): androidx.compose.ui.graphics.painter.Painter? {
    val context = LocalContext.current
    return when (source) {
        is ImageSource.UriSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(source.uri)
                .size(coil.size.Size(2560, 2560))
                .crossfade(true)
                .build()
        )
        is ImageSource.StringSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(source.path)
                .size(coil.size.Size(2560, 2560))
                .crossfade(true)
                .build()
        )
        is ImageSource.ResourceSource -> rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(source.resId)
                .crossfade(true)
                .build()
        )
        is ImageSource.PainterSource -> source.painter
        is ImageSource.Base64Source -> null
    }
}

/**
 * 如果 [ImageSource] 是 Base64 类型，解码为 Bitmap
 */
@Composable
private fun rememberBase64Bitmap(source: ImageSource): android.graphics.Bitmap? {
    return remember(source) {
        when (source) {
            is ImageSource.Base64Source -> {
                try {
                    val bytes = Base64.decode(source.base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
