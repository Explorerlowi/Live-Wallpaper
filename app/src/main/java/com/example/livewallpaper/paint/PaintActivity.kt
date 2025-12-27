package com.example.livewallpaper.paint

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.paint.ui.PaintScreen
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import org.koin.androidx.compose.koinViewModel

class PaintActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Edge-to-Edge 已在 themes.xml 中配置
        
        // 从 Intent 获取初始主题模式，避免首帧闪白
        val initialThemeMode = intent.getStringExtra("themeMode")?.let {
            try { ThemeMode.valueOf(it) } catch (e: Exception) { ThemeMode.SYSTEM }
        } ?: ThemeMode.SYSTEM
        
        // 立即设置内容，减少启动延迟
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            
            // 使用 Intent 传入的初始值，后续跟随 ViewModel 状态
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            
            // 当 ViewModel 加载完成后同步主题
            LaunchedEffect(settingsState.config.themeMode) {
                themeMode = settingsState.config.themeMode
            }
            
            LiveWallpaperTheme(themeMode = themeMode) {
                PaintScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
