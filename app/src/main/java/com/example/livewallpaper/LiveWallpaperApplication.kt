package com.example.livewallpaper

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.livewallpaper.di.appModule
import com.example.livewallpaper.di.galleryModule
import com.example.livewallpaper.di.imageModule
import com.example.livewallpaper.di.paintModule
import com.example.livewallpaper.di.platformModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LiveWallpaperApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@LiveWallpaperApplication)
            modules(appModule, platformModule, galleryModule, imageModule, paintModule)
        }
    }
    
    /**
     * 提供全局 ImageLoader，让 Coil 的 AsyncImage 自动使用配置好的缓存
     */
    override fun newImageLoader(): ImageLoader = get()
}

