package com.example.livewallpaper.di

import com.example.livewallpaper.core.network.HttpClientFactory
import com.example.livewallpaper.feature.aipaint.data.remote.GeminiApiService
import com.example.livewallpaper.feature.aipaint.data.repository.PaintRepositoryImpl
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.dynamicwallpaper.data.repository.WallpaperRepositoryImpl
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule = module {
    // 网络
    single { HttpClientFactory.create(enableLogging = false) }
    
    // AI 绘画
    single { GeminiApiService(get()) }
    single<PaintRepository> { PaintRepositoryImpl(get(), get()) }
    
    // 壁纸
    single<WallpaperRepository> { WallpaperRepositoryImpl(get()) }
    factory { SettingsViewModel(get()) }
}

expect val platformModule: Module

