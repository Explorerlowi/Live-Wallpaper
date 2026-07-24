package com.example.livewallpaper.di

import android.content.Context
import com.example.livewallpaper.core.platform.AndroidGptImageResponseProcessor
import com.example.livewallpaper.core.platform.AndroidImageResponseProcessor
import com.example.livewallpaper.core.platform.AndroidImageRequestPayloadReader
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.platform.ImageResponseProcessor
import com.example.livewallpaper.core.platform.AndroidPaintDatabaseDriverFactory
import com.example.livewallpaper.core.platform.AndroidLegacyPaintImageFileStore
import com.example.livewallpaper.core.platform.AndroidPaintReferenceImageStore
import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlinx.coroutines.Dispatchers

actual val platformModule: Module = module {
    single<ObservableSettings> {
        val context = get<Context>()
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        SharedPreferencesSettings(preferences)
    }
    single<ImageResponseProcessor> { AndroidImageResponseProcessor(get()) }
    single<GptImageResponseProcessor> { AndroidGptImageResponseProcessor(get()) }
    single<PaintDatabaseDriverFactory> { AndroidPaintDatabaseDriverFactory(get()) }
    single<CoroutineDispatcherProvider> {
        DefaultCoroutineDispatcherProvider(Dispatchers.IO, Dispatchers.Default)
    }
    single<LegacyPaintImageFileStore> { AndroidLegacyPaintImageFileStore(get(), get()) }
    single<ImageRequestPayloadReader> { AndroidImageRequestPayloadReader(get(), get()) }
    single<PaintReferenceImageStore> { AndroidPaintReferenceImageStore(get(), get()) }
}

