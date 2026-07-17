package com.example.livewallpaper.di

import com.example.livewallpaper.core.platform.DesktopPaintDataArchiveGateway
import com.example.livewallpaper.core.platform.DesktopPaintDraftRepository
import com.example.livewallpaper.core.platform.DesktopGptImageResponseProcessor
import com.example.livewallpaper.core.platform.DesktopImageResponseProcessor
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.platform.ImageResponseProcessor
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.usecase.ExportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.ImportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.PreviewPaintDataImportUseCase
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.PreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val platformModule: Module = module {
    single<ObservableSettings> {
        PreferencesSettings(
            Preferences.userRoot().node("com.example.livewallpaper")
        )
    }
    single<ImageResponseProcessor> { DesktopImageResponseProcessor() }
    single<GptImageResponseProcessor> { DesktopGptImageResponseProcessor() }
    single<PaintDataArchiveGateway> { DesktopPaintDataArchiveGateway() }
    single<PaintDraftRepository> { DesktopPaintDraftRepository(get()) }
    factory { ExportPaintDataUseCase(get(), get(), get()) }
    factory { ImportPaintDataUseCase(get(), get(), get()) }
    factory { PreviewPaintDataImportUseCase(get()) }
}
