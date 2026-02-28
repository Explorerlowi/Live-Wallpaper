package com.example.livewallpaper.paint

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.paint.ui.PaintScreen
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import org.koin.androidx.compose.koinViewModel

class PaintActivity : ComponentActivity() {

    private var pendingSessionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pendingSessionId.value = intent.getStringExtra(EXTRA_SESSION_ID)
        
        // 从 Intent 获取初始主题模式，避免首帧闪白
        val initialThemeMode = intent.getStringExtra("themeMode")?.let {
            try { ThemeMode.valueOf(it) } catch (e: Exception) { ThemeMode.SYSTEM }
        } ?: ThemeMode.SYSTEM
        
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            
            var themeMode by mutableStateOf(initialThemeMode)
            
            LaunchedEffect(settingsState.config.themeMode) {
                themeMode = settingsState.config.themeMode
            }
            
            LiveWallpaperTheme(themeMode = themeMode) {
                val sessionId by pendingSessionId
                PaintScreen(
                    initialSessionId = sessionId,
                    onSessionRestored = { pendingSessionId.value = null },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSessionId.value = intent.getStringExtra(EXTRA_SESSION_ID)
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
