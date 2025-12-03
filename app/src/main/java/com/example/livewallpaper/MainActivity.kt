package com.example.livewallpaper

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.livewallpaper.ui.SettingsScreen
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveWallpaperTheme {
                SettingsScreen()
            }
        }
    }
}