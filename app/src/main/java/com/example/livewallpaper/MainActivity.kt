package com.example.livewallpaper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.ui.SettingsScreen
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮，防止息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        enableEdgeToEdge()
        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsState()
            
            // 监听主题变化，重新创建 Activity
            LaunchedEffect(uiState.config.themeMode) {
                // 主题变化时不需要重新创建，LiveWallpaperTheme 会自动处理
            }
            
            LiveWallpaperTheme(themeMode = uiState.config.themeMode) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
