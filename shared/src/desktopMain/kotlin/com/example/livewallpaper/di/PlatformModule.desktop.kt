package com.example.livewallpaper.di

import com.example.livewallpaper.core.platform.DesktopPaintDataArchiveGateway
import com.example.livewallpaper.core.platform.DesktopPaintDatabaseDriverFactory
import com.example.livewallpaper.core.platform.DesktopLegacyPaintImageFileStore
import com.example.livewallpaper.core.platform.DesktopPaintDraftRepository
import com.example.livewallpaper.core.platform.DesktopPaintReferenceImageStore
import com.example.livewallpaper.core.platform.DesktopGptImageResponseProcessor
import com.example.livewallpaper.core.platform.DesktopImageResponseProcessor
import com.example.livewallpaper.core.platform.DesktopImageRequestPayloadReader
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.platform.ImageResponseProcessor
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.data.local.database.PaintDatabaseDriverFactory
import com.example.livewallpaper.feature.aipaint.data.local.LegacyPaintDraftSource
import com.example.livewallpaper.feature.aipaint.domain.repository.LegacyPaintImageFileStore
import com.example.livewallpaper.feature.aipaint.domain.repository.ImageRequestPayloadReader
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import com.example.livewallpaper.feature.aipaint.domain.usecase.ExportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.ImportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.PreviewPaintDataImportUseCase
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.PreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers

actual val platformModule: Module = module {
    single<ObservableSettings> {
        PreferencesSettings(
            Preferences.userRoot().node("com.example.livewallpaper")
        )
    }
    single<ImageResponseProcessor> { DesktopImageResponseProcessor() }
    single<GptImageResponseProcessor> { DesktopGptImageResponseProcessor() }
    single<PaintDataArchiveGateway> { DesktopPaintDataArchiveGateway() }
    single<PaintDatabaseDriverFactory> { DesktopPaintDatabaseDriverFactory() }
    single<CoroutineDispatcherProvider> {
        DefaultCoroutineDispatcherProvider(Dispatchers.IO, Dispatchers.Default)
    }
    single<LegacyPaintImageFileStore> { DesktopLegacyPaintImageFileStore(get()) }
    single<ImageRequestPayloadReader> { DesktopImageRequestPayloadReader(get()) }
    single<PaintReferenceImageStore> { DesktopPaintReferenceImageStore(get()) }
    single<LegacyPaintDraftSource> { DesktopPaintDraftRepository(get()) }
    factory { ExportPaintDataUseCase(get(), get()) }
    factory { ImportPaintDataUseCase(get(), get(), get(), get()) }
    factory { PreviewPaintDataImportUseCase(get()) }
}
