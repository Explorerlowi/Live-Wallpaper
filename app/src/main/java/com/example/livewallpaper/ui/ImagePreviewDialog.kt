package com.example.livewallpaper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size

/**
 * 图片全屏预览对话框
 * 支持左右滑动切换、缩放和拖动操作
 */
@Composable
fun ImagePreviewDialog(
    imageUris: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageUris.size }
    )

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
            // 图片翻页器
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { imageUris[it] }
            ) { page ->
                ZoomableImage(
                    imageUri = imageUris[page],
                    onTap = onDismiss
                )
            }
        }
    }
}

/**
 * 可缩放的图片组件
 * 当图片未缩放时，允许水平滑动事件穿透给父级 HorizontalPager
 */
@Composable
private fun ZoomableImage(
    imageUri: String,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    // 限制最大加载尺寸，防止超大图片导致 Canvas 崩溃
    // 2560 像素对于手机屏幕显示和缩放查看已经足够
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUri)
            .size(Size(2560, 2560))
            .crossfade(true)
            .build()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // 双击切换缩放
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = {
                        // 单击退出
                        onTap()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // 等待第一个手指按下
                        awaitFirstDown(requireUnconsumed = false)
                        
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            
                            // 处理缩放
                            if (zoomChange != 1f) {
                                scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                // 缩放时消费事件
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            
                            // 处理平移
                            if (panChange != Offset.Zero) {
                                if (scale > 1f) {
                                    // 图片已缩放，处理拖动
                                    offset = Offset(
                                        x = offset.x + panChange.x,
                                        y = offset.y + panChange.y
                                    )
                                    // 消费事件，阻止 Pager 滑动
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    // 图片未缩放，检查是否为双指手势
                                    val fingerCount = event.changes.count { it.pressed }
                                    if (fingerCount >= 2) {
                                        // 双指操作，准备缩放，消费事件
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                    // 单指水平滑动时不消费，让 Pager 处理
                                    offset = Offset.Zero
                                }
                            }
                            
                            // 如果缩放到 1 以下，重置
                            if (scale <= 1f) {
                                offset = Offset.Zero
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        )
    }
}
