package com.example.livewallpaper.di

import com.example.livewallpaper.core.platform.DesktopGptImageResponseProcessor
import com.example.livewallpaper.core.platform.DesktopImageResponseProcessor
import com.example.livewallpaper.core.platform.GptImageResponseProcessor
import com.example.livewallpaper.core.platform.ImageResponseProcessor
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
}
