package com.example.livewallpaper.di

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ObservableSettings> {
        val context = get<Context>()
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        SharedPreferencesSettings(preferences)
    }
}

