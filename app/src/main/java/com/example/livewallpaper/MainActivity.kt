package com.example.livewallpaper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.ui.WallpaperHomeScreen
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮，防止息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Edge-to-Edge 已在 themes.xml 中配置，无需在代码中调用 enableEdgeToEdge()
        
        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsState()
            
            LiveWallpaperTheme(themeMode = uiState.config.themeMode) {
                WallpaperHomeScreen(viewModel = viewModel)
            }
        }
    }
}
