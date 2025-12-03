package com.example.livewallpaper.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 图片裁剪调整界面
 * 支持手势缩放和拖动，边界回弹效果
 *
 * @param imageUri 图片 URI
 * @param initialParams 初始裁剪参数
 * @param onConfirm 确认回调，返回新的裁剪参数
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun ImageCropScreen(
    imageUri: String,
    initialParams: ImageCropParams,
    onConfirm: (ImageCropParams) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false  // 允许内容延伸到系统栏下方
        )
    ) {
        // 设置 Dialog 的 Window 为沉浸式全屏模式
        val view = LocalView.current
        DisposableEffect(Unit) {
            // 通过 DialogWindowProvider 获取 Dialog 的 window
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            
            dialogWindow?.let { w ->
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                
                // 允许内容延伸到系统栏区域
                WindowCompat.setDecorFitsSystemWindows(w, false)
                
                // 使用 WindowInsetsController 隐藏系统栏
                val insetsController = WindowCompat.getInsetsController(w, view)
                insetsController.apply {
                    // 隐藏状态栏和导航栏
                    hide(WindowInsetsCompat.Type.systemBars())
                    // 设置沉浸式模式：滑动边缘可以暂时显示系统栏
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            
            onDispose {
                // 退出时恢复系统栏显示
                dialogWindow?.let { w ->
                    val insetsController = WindowCompat.getInsetsController(w, view)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CroppableImage(
                imageUri = imageUri,
                initialParams = initialParams,
                onConfirm = onConfirm,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 可裁剪的图片组件
 * 支持双指缩放、单指拖动、边界回弹
 */
@Composable
private fun CroppableImage(
    imageUri: String,
    initialParams: ImageCropParams,
    onConfirm: (ImageCropParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 图片状态
    var scale by remember { mutableFloatStateOf(initialParams.scale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // 容器尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 图片原始尺寸
    var imageSize by remember { mutableStateOf(Size.Zero) }
    
    // 用于边界回弹动画
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val animatedScale = remember { Animatable(1f) }
    
    // 是否正在手势操作中
    var isGestureActive by remember { mutableStateOf(false) }

    // 加载图片
    // 限制最大加载尺寸，防止超大图片导致 Canvas 崩溃
    // 2560 像素对于手机屏幕显示和缩放查看已经足够
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUri)
            .size(coil.size.Size(2560, 2560))
            .crossfade(true)
            .build()
    )
    
    // 获取图片实际尺寸
    val painterState = painter.state
    LaunchedEffect(painterState) {
        if (painterState is AsyncImagePainter.State.Success) {
            val intrinsicSize = painterState.painter.intrinsicSize
            if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                imageSize = intrinsicSize
            }
        }
    }
    
    // 计算填充屏幕所需的最小缩放比例
    val minScaleToFill = remember(containerSize, imageSize) {
        if (containerSize.width <= 0 || containerSize.height <= 0 || 
            imageSize.width <= 0 || imageSize.height <= 0) {
            1f
        } else {
            val imageAspect = imageSize.width / imageSize.height
            val containerAspect = containerSize.width.toFloat() / containerSize.height
            
            // Fit 模式的基础缩放
            val fitScale = if (imageAspect > containerAspect) {
                containerSize.width.toFloat() / imageSize.width
            } else {
                containerSize.height.toFloat() / imageSize.height
            }
            
            // 填充模式需要的缩放
            val fillScale = if (imageAspect > containerAspect) {
                containerSize.height.toFloat() / imageSize.height
            } else {
                containerSize.width.toFloat() / imageSize.width
            }
            
            // 返回从 Fit 到 Fill 需要的额外缩放比例
            fillScale / fitScale
        }
    }
    
    // 初始化缩放和偏移量
    LaunchedEffect(containerSize, imageSize, initialParams) {
        if (containerSize.width > 0 && containerSize.height > 0 && imageSize.width > 0) {
            // 如果是默认参数（scale = 1），则使用填充屏幕的缩放比例
            val effectiveScale = if (initialParams.scale == 1f) {
                minScaleToFill
            } else {
                initialParams.scale
            }
            scale = effectiveScale
            
            // 根据初始参数计算偏移量
            val displayedSize = calculateDisplayedImageSize(imageSize, containerSize, effectiveScale)
            offset = Offset(
                x = initialParams.offsetX * displayedSize.width,
                y = initialParams.offsetY * displayedSize.height
            )
        }
    }

    // 计算边界限制
    fun calculateBounds(): Pair<Offset, Offset> {
        if (containerSize.width <= 0 || containerSize.height <= 0 || imageSize.width <= 0) {
            return Pair(Offset.Zero, Offset.Zero)
        }
        
        val displayedSize = calculateDisplayedImageSize(imageSize, containerSize, scale)
        
        // 计算允许的最大偏移量
        // 图片边缘不能超出屏幕边缘
        val maxOffsetX = ((displayedSize.width - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((displayedSize.height - containerSize.height) / 2f).coerceAtLeast(0f)
        
        return Pair(
            Offset(-maxOffsetX, -maxOffsetY), // 最小偏移
            Offset(maxOffsetX, maxOffsetY)    // 最大偏移
        )
    }
    
    // 限制偏移量到边界内
    fun constrainOffset(currentOffset: Offset): Offset {
        val (minOffset, maxOffset) = calculateBounds()
        return Offset(
            x = currentOffset.x.coerceIn(minOffset.x, maxOffset.x),
            y = currentOffset.y.coerceIn(minOffset.y, maxOffset.y)
        )
    }
    
    // 回弹动画
    fun animateToBounds() {
        val constrainedOffset = constrainOffset(offset)
        val minScale = minScaleToFill.coerceAtLeast(1f)
        val maxScale = 5f
        
        scope.launch {
            // 如果缩放小于最小值，回弹到最小值（填充屏幕）
            if (scale < minScale) {
                animatedScale.snapTo(scale)
                animatedScale.animateTo(
                    targetValue = minScale,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 300f
                    )
                ) {
                    scale = value
                }
            } else if (scale > maxScale) {
                animatedScale.snapTo(scale)
                animatedScale.animateTo(
                    targetValue = maxScale,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 300f
                    )
                ) {
                    scale = value
                }
            }
        }
        
        scope.launch {
            if (offset != constrainedOffset) {
                animatedOffset.snapTo(offset)
                animatedOffset.animateTo(
                    targetValue = constrainedOffset,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 300f
                    )
                ) {
                    offset = value
                }
            }
        }
    }
    
    // 将当前状态转换为裁剪参数
    fun getCurrentCropParams(): ImageCropParams {
        if (containerSize.width <= 0 || containerSize.height <= 0 || imageSize.width <= 0) {
            return ImageCropParams()
        }
        
        val displayedSize = calculateDisplayedImageSize(imageSize, containerSize, scale)
        
        return ImageCropParams(
            scale = scale,
            offsetX = if (displayedSize.width > 0) offset.x / displayedSize.width else 0f,
            offsetY = if (displayedSize.height > 0) offset.y / displayedSize.height else 0f
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isGestureActive = true
                    
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        
                        // 处理缩放
                        if (zoomChange != 1f) {
                            // 允许缩放到最小值的一半（用于回弹效果），最大 8 倍
                            val minAllowed = (minScaleToFill * 0.5f).coerceAtLeast(0.5f)
                            val newScale = (scale * zoomChange).coerceIn(minAllowed, 8f)
                            
                            // 以手势中心点为缩放中心
                            val centroid = event.calculateCentroid()
                            val oldScale = scale
                            scale = newScale
                            
                            // 调整偏移以保持缩放中心
                            val scaleDiff = newScale - oldScale
                            val centerOffset = Offset(
                                x = centroid.x - containerSize.width / 2f,
                                y = centroid.y - containerSize.height / 2f
                            )
                            offset = Offset(
                                x = offset.x - centerOffset.x * scaleDiff / oldScale,
                                y = offset.y - centerOffset.y * scaleDiff / oldScale
                            )
                            
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        
                        // 处理拖动
                        if (panChange != Offset.Zero) {
                            offset = Offset(
                                x = offset.x + panChange.x,
                                y = offset.y + panChange.y
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    
                    isGestureActive = false
                    // 手势结束后触发边界回弹
                    animateToBounds()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 图片
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
        
        // 底部确认按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = 24.dp,
                    vertical = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            Button(
                onClick = {
                    onConfirm(getCurrentCropParams())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.crop_confirm),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * 计算图片在容器中显示的实际尺寸
 */
private fun calculateDisplayedImageSize(
    imageSize: Size,
    containerSize: IntSize,
    scale: Float
): Size {
    if (imageSize.width <= 0 || imageSize.height <= 0 || 
        containerSize.width <= 0 || containerSize.height <= 0) {
        return Size.Zero
    }
    
    val imageAspect = imageSize.width / imageSize.height
    val containerAspect = containerSize.width.toFloat() / containerSize.height
    
    // ContentScale.Fit 的计算逻辑
    val baseSize = if (imageAspect > containerAspect) {
        // 图片更宽，以宽度为准
        Size(
            width = containerSize.width.toFloat(),
            height = containerSize.width / imageAspect
        )
    } else {
        // 图片更高，以高度为准
        Size(
            width = containerSize.height * imageAspect,
            height = containerSize.height.toFloat()
        )
    }
    
    return Size(
        width = baseSize.width * scale,
        height = baseSize.height * scale
    )
}

