package com.example.livewallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return WallpaperEngine()
    }

    inner class WallpaperEngine : Engine(), KoinComponent {
        private val repository: WallpaperRepository by inject()
        private val imageLoader: ImageLoader by inject()
        private val scope = CoroutineScope(Dispatchers.Main + Job())
        private var wallpaperJob: Job? = null
        
        private var config: WallpaperConfig = WallpaperConfig()
        private var currentImageIndex = 0
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            
            repository.getConfig()
                .onEach { newConfig ->
                    config = newConfig
                    restartWallpaperLoop()
                }
                .launchIn(scope)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            restartWallpaperLoop()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                restartWallpaperLoop()
            } else {
                stopWallpaperLoop()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            scope.coroutineContext[Job]?.cancel()
        }

        private fun restartWallpaperLoop() {
            stopWallpaperLoop()
            if (isVisible) {
                wallpaperJob = scope.launch {
                    runWallpaperLoop()
                }
            }
        }

        private fun stopWallpaperLoop() {
            wallpaperJob?.cancel()
            wallpaperJob = null
        }

        private suspend fun runWallpaperLoop() {
            // 用于随机模式的已播放索引记录
            val playedIndices = mutableSetOf<Int>()
            
            while (currentCoroutineContext().isActive) {
                val uris = config.imageUris
                if (uris.isEmpty()) {
                    drawPlaceholder()
                    delay(1000) // Wait and retry
                    continue
                }

                // 根据播放模式选择下一张图片
                val nextIndex = when (config.playMode) {
                    PlayMode.SEQUENTIAL -> {
                        // 顺序播放
                        if (currentImageIndex >= uris.size) {
                            currentImageIndex = 0
                        }
                        currentImageIndex
                    }
                    PlayMode.RANDOM -> {
                        // 随机播放：确保不重复直到所有图片都播放过
                        if (playedIndices.size >= uris.size) {
                            playedIndices.clear()
                        }
                        var randomIndex: Int
                        do {
                            randomIndex = Random.nextInt(uris.size)
                        } while (playedIndices.contains(randomIndex) && playedIndices.size < uris.size)
                        playedIndices.add(randomIndex)
                        randomIndex
                    }
                }
                
                val uri = uris[nextIndex]
                val cropParams = config.imageCropParams[uri] ?: ImageCropParams()
                loadImageAndDraw(uri, cropParams)

                // 更新索引（仅对顺序播放有意义）
                currentImageIndex = (nextIndex + 1) % uris.size
                delay(config.interval)
            }
        }

        private suspend fun loadImageAndDraw(uri: String, cropParams: ImageCropParams) {
            // 根据屏幕尺寸计算合理的最大加载尺寸
            // 使用屏幕尺寸的 2 倍作为最大值，确保缩放后依然清晰
            // 同时设置上限 4096 防止超大图片导致 Canvas 崩溃
            val maxSize = maxOf(surfaceWidth, surfaceHeight) * 2
            val targetSize = maxSize.coerceIn(1080, 4096)
            
            val request = ImageRequest.Builder(this@LiveWallpaperService)
                .data(uri)
                .size(targetSize, targetSize)
                .allowHardware(false) // Canvas drawing needs software bitmap
                .memoryCachePolicy(coil.request.CachePolicy.DISABLED) // 禁用内存缓存，避免共享 Bitmap 被回收
                .build()
            
            val result = imageLoader.execute(request)
            val drawable = result.drawable
            
            if (drawable != null) {
                // 检查是否有自定义裁剪参数（非默认值）
                val hasCustomCrop = cropParams.scale != 1f || 
                                   cropParams.offsetX != 0f || 
                                   cropParams.offsetY != 0f
                
                // 转换为 Bitmap
                // 由于禁用了内存缓存，这里得到的是独立的 Bitmap，可以安全回收
                val bitmap = drawable.toBitmap()
                try {
                    // 再次检查 Bitmap 是否已被回收（防御性编程）
                    if (!bitmap.isRecycled) {
                        drawBitmap(bitmap, cropParams, hasCustomCrop)
                    }
                } finally {
                    // 使用完毕后回收 Bitmap，避免内存泄漏
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }

        private fun drawPlaceholder() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    val paint = Paint().apply {
                        color = Color.WHITE
                        textSize = 50f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("No images selected", canvas.width / 2f, canvas.height / 2f, paint)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawBitmap(bitmap: Bitmap, cropParams: ImageCropParams, hasCustomCrop: Boolean) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val canvasWidth = canvas.width
                    val canvasHeight = canvas.height
                    
                    canvas.drawColor(Color.BLACK) // Clear background
                    
                    val destRect = if (hasCustomCrop) {
                        // 有自定义裁剪参数，使用自定义参数
                        calculateDestRectWithCrop(
                            srcW = bitmap.width,
                            srcH = bitmap.height,
                            dstW = canvasWidth,
                            dstH = canvasHeight,
                            cropParams = cropParams
                        )
                    } else {
                        // 没有自定义裁剪参数，使用全局缩放模式
                        calculateDestRectWithScaleMode(
                            srcW = bitmap.width,
                            srcH = bitmap.height,
                            dstW = canvasWidth,
                            dstH = canvasHeight,
                            scaleMode = config.scaleMode
                        )
                    }
                    
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    canvas.drawBitmap(bitmap, srcRect, destRect, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
        
        /**
         * 根据全局缩放模式计算目标绘制区域
         */
        private fun calculateDestRectWithScaleMode(
            srcW: Int, srcH: Int,
            dstW: Int, dstH: Int,
            scaleMode: ScaleMode
        ): Rect {
            val srcAspect = srcW.toFloat() / srcH
            val dstAspect = dstW.toFloat() / dstH
            
            val scale = when (scaleMode) {
                ScaleMode.CENTER_CROP -> {
                    // 填充模式：图片填满屏幕，可能裁剪
                    if (srcAspect > dstAspect) {
                        dstH.toFloat() / srcH
                    } else {
                        dstW.toFloat() / srcW
                    }
                }
                ScaleMode.FIT_CENTER -> {
                    // 适应模式：图片完整显示，可能有黑边
                    if (srcAspect > dstAspect) {
                        dstW.toFloat() / srcW
                    } else {
                        dstH.toFloat() / srcH
                    }
                }
            }
            
            val scaledW = (srcW * scale).toInt()
            val scaledH = (srcH * scale).toInt()
            
            // 居中显示
            val left = (dstW - scaledW) / 2
            val top = (dstH - scaledH) / 2
            
            return Rect(left, top, left + scaledW, top + scaledH)
        }
        
        /**
         * 根据裁剪参数计算目标绘制区域
         * 
         * 此方法的计算逻辑必须与 ImageCropScreen 中的预览逻辑保持一致：
         * 1. 首先使用 Fit 模式计算基础尺寸
         * 2. 然后应用用户设置的 scale 参数（该参数已包含填充屏幕所需的缩放比例）
         * 3. 最后应用用户设置的偏移量
         * 
         * @param srcW 原图宽度
         * @param srcH 原图高度
         * @param dstW 目标画布宽度
         * @param dstH 目标画布高度
         * @param cropParams 用户设置的裁剪参数（scale 已包含 minScaleToFill）
         */
        private fun calculateDestRectWithCrop(
            srcW: Int, srcH: Int, 
            dstW: Int, dstH: Int, 
            cropParams: ImageCropParams
        ): Rect {
            val srcAspect = srcW.toFloat() / srcH
            val dstAspect = dstW.toFloat() / dstH
            
            // 使用 Fit 模式计算基础尺寸（与 ImageCropScreen 中 ContentScale.Fit 一致）
            val (baseW, baseH) = if (srcAspect > dstAspect) {
                // 图片更宽，以宽度为准
                Pair(dstW.toFloat(), dstW.toFloat() / srcAspect)
            } else {
                // 图片更高，以高度为准
                Pair(dstH.toFloat() * srcAspect, dstH.toFloat())
            }
            
            // 应用用户缩放（scale 已包含填充屏幕所需的 minScaleToFill）
            val scaledW = (baseW * cropParams.scale).toInt()
            val scaledH = (baseH * cropParams.scale).toInt()
            
            // 计算基础居中位置
            val baseCenterX = dstW / 2f
            val baseCenterY = dstH / 2f
            
            // 应用用户偏移（偏移量是相对于缩放后图片尺寸的比例）
            val offsetX = cropParams.offsetX * scaledW
            val offsetY = cropParams.offsetY * scaledH
            
            val left = (baseCenterX - scaledW / 2f + offsetX).toInt()
            val top = (baseCenterY - scaledH / 2f + offsetY).toInt()
            
            return Rect(left, top, left + scaledW, top + scaledH)
        }
    }
}

