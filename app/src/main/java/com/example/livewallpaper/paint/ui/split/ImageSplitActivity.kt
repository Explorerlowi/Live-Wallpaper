package com.example.livewallpaper.paint.ui.split

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 图片切分 Activity（承载“切分/网格编辑”页面）
 *
 * “微调与导出”仍以 Dialog 的形式从此 Activity 内弹出。
 */
class ImageSplitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Edge-to-Edge 已在 themes.xml 中配置

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath.isNullOrBlank()) {
            finish()
            return
        }

        // 从 Intent 获取初始主题模式，避免首帧闪白（可选）
        val initialThemeMode = intent.getStringExtra(EXTRA_THEME_MODE)?.let {
            try {
                ThemeMode.valueOf(it)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        } ?: ThemeMode.SYSTEM

        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()

            var themeMode by remember { mutableStateOf(initialThemeMode) }
            LaunchedEffect(settingsState.config.themeMode) {
                themeMode = settingsState.config.themeMode
            }

            LiveWallpaperTheme(themeMode = themeMode) {
                ImageSplitFlow(
                    imagePath = imagePath,
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_IMAGE_PATH = "imagePath"
        private const val EXTRA_THEME_MODE = "themeMode"

        /**
         * 启动图片切分 Activity
         */
        fun launch(context: Context, imagePath: String) {
            val intent = Intent(context, ImageSplitActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, imagePath)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

