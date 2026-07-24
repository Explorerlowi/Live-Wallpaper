package com.example.livewallpaper.di

import com.example.livewallpaper.core.network.HttpClientFactory
import com.example.livewallpaper.feature.aipaint.data.remote.GeminiApiService
import com.example.livewallpaper.feature.aipaint.data.remote.GptApiService
import com.example.livewallpaper.feature.aipaint.data.repository.PaintRepositoryImpl
import com.example.livewallpaper.feature.aipaint.data.local.PaintConversationDataSource
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintConversationStore
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintStorageReader
import com.example.livewallpaper.feature.aipaint.data.local.PaintStorageCoordinator
import com.example.livewallpaper.feature.aipaint.data.local.PaintMigrationEpochStore
import com.example.livewallpaper.feature.aipaint.data.local.PaintStorageMigrator
import com.example.livewallpaper.feature.aipaint.data.local.SqlDelightPaintStore
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseFactory
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageRecoveryController
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintStorageStateProvider
import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.AppUpdateService
import com.example.livewallpaper.feature.dynamicwallpaper.data.repository.WallpaperRepositoryImpl
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.russhwolf.settings.ObservableSettings
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule = module {
    // 网络
    single { HttpClientFactory.create(enableLogging = true) }
    
    // AI 绘画
    single { GeminiApiService(get()) }
    single { GptApiService(get()) }
    single { PaintDatabaseFactory.create(get()) }
    single { SqlDelightPaintStore(get(), get(), get()) }
    single { LegacyPaintStorageReader(get<ObservableSettings>(), get(), get()) }
    single { LegacyPaintConversationStore(get<ObservableSettings>(), get()) }
    single { PaintMigrationEpochStore(get<ObservableSettings>(), get()) }
    single { PaintStorageMigrator(get(), get(), get(), get(), get(), get(), get()) }
    single { PaintStorageCoordinator(get(), get(), get(), get()) }
    single<PaintConversationDataSource> { get<PaintStorageCoordinator>() }
    single<PaintDraftRepository> { get<PaintStorageCoordinator>() }
    single { PaintRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<PaintRepository> { get<PaintRepositoryImpl>() }
    single<PaintDataRepository> { get<PaintStorageCoordinator>() }
    single<PaintStorageStateProvider> { get<PaintStorageCoordinator>() }
    single<PaintStorageRecoveryController> { get<PaintStorageCoordinator>() }
    
    // 更新服务
    single { AppUpdateService(get()) }

    // 壁纸
    single<WallpaperRepository> { WallpaperRepositoryImpl(get(), get()) }
    factory { SettingsViewModel(get()) }
}

expect val platformModule: Module
