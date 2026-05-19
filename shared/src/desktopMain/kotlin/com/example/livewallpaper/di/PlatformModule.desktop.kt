package com.example.livewallpaper.di

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
}
