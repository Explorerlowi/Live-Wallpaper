package com.example.livewallpaper

import android.app.Application
import com.example.livewallpaper.di.appModule
import com.example.livewallpaper.di.galleryModule
import com.example.livewallpaper.di.imageModule
import com.example.livewallpaper.di.paintModule
import com.example.livewallpaper.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LiveWallpaperApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@LiveWallpaperApplication)
            modules(appModule, platformModule, galleryModule, imageModule, paintModule)
        }
    }
}

