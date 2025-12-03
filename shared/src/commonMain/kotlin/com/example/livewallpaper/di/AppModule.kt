package com.example.livewallpaper.di

import com.example.livewallpaper.feature.dynamicwallpaper.data.repository.WallpaperRepositoryImpl
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule = module {
    single<WallpaperRepository> { WallpaperRepositoryImpl(get()) }
    factory { SettingsViewModel(get()) }
}

expect val platformModule: Module

