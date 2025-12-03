package com.example.livewallpaper.di

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Coil ImageLoader 配置模块
 * 提供全局单例 ImageLoader，配置内存缓存和磁盘缓存
 */
val imageModule = module {
    single<ImageLoader> {
        val context = androidContext()
        
        // 计算内存缓存大小（可用内存的 25%）
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val maxMemory = activityManager.memoryClass * 1024 * 1024L // 转换为字节
        val memoryCacheSize = (maxMemory * 0.25).toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCacheSize)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .apply {
                // 仅在 Debug 模式下启用日志
                val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebuggable) {
                    logger(DebugLogger())
                }
            }
            .respectCacheHeaders(false) // 壁纸场景下忽略服务器缓存头
            .build()
    }
}

