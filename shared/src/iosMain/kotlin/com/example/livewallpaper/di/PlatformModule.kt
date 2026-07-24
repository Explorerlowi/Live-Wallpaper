package com.example.livewallpaper.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.core.platform.IOSPaintDatabaseDriverFactory
import com.example.livewallpaper.core.platform.IOSLegacyPaintImageFileStore
import com.example.livewallpaper.core.platform.IOSPaintReferenceImageStore
import com.example.livewallpaper.core.platform.IOSImageRequestPayloadReader
import com.example.livewallpaper.feature.aipaint.data.local.EmptyLegacyPaintDraftSource
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintDraftSource
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory
import kotlinx.coroutines.Dispatchers

actual val platformModule: Module = module {
    single<ObservableSettings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
    single<PaintDatabaseDriverFactory> { IOSPaintDatabaseDriverFactory() }
    single<CoroutineDispatcherProvider> {
        DefaultCoroutineDispatcherProvider(Dispatchers.Default, Dispatchers.Default)
    }
    single<LegacyPaintImageFileStore> { IOSLegacyPaintImageFileStore(get()) }
    single<ImageRequestPayloadReader> { IOSImageRequestPayloadReader(get()) }
    single<PaintReferenceImageStore> { IOSPaintReferenceImageStore(get()) }
    single<LegacyPaintDraftSource> { EmptyLegacyPaintDraftSource() }
}

