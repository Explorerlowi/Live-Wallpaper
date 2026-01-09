package com.example.livewallpaper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode

// 薄荷绿浅色主题
private val LightColorScheme = lightColorScheme(
    primary = Teal300,
    onPrimary = Color.White,
    primaryContainer = MintGreen200,
    onPrimaryContainer = TextPrimary,
    secondary = Teal400,
    onSecondary = Color.White,
    secondaryContainer = MintGreen100,
    onSecondaryContainer = TextPrimary,
    tertiary = MintGreen400,
    onTertiary = Color.White,
    tertiaryContainer = MintGreen100,
    onTertiaryContainer = TextPrimary,
    background = BackgroundMint,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = MintGreen50,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = DeleteRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// 深色主题
private val DarkColorScheme = darkColorScheme(
    primary = Teal300,  // 使用更亮的青色作为主色
    onPrimary = Color.White,  // 白色文字
    primaryContainer = Teal500,
    onPrimaryContainer = MintGreen100,
    secondary = Teal200,
    onSecondary = Color.White,
    secondaryContainer = Teal500,
    onSecondaryContainer = MintGreen100,
    tertiary = MintGreen300,
    onTertiary = Color.White,
    tertiaryContainer = MintGreen500,
    onTertiaryContainer = MintGreen100,
    background = DarkBackground,
    onBackground = MintGreen100,
    surface = DarkSurface,
    onSurface = MintGreen100,
    surfaceVariant = Color(0xFF2D4A3E),
    onSurfaceVariant = MintGreen200,
    outline = Teal400,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 星尘之海主题 (Sea of Stardust)
private val StardustColorScheme = darkColorScheme(
    primary = MilkyGlow,                      // 银河辉作为主色
    onPrimary = CosmicInk,                    // 深色文字
    primaryContainer = DeepSky,               // 深空蓝容器
    onPrimaryContainer = StarMist,            // 星雾色文字
    secondary = TwilightPlum,                 // 暮光紫作为次要色
    onSecondary = StarMist,                   // 浅色文字
    secondaryContainer = Color(0xFF3D3566),   // 暮光紫深色变体
    onSecondaryContainer = StarMist,
    tertiary = DeepSky,                       // 深空蓝作为第三色
    onTertiary = StarMist,
    tertiaryContainer = Color(0xFF2A4A80),
    onTertiaryContainer = StarMist,
    background = CosmicInk,                   // 宇宙墨背景
    onBackground = StarMist,                  // 星雾色文字
    surface = StardustSurface,                // 表面色
    onSurface = StarMist,                     // 星雾色文字
    surfaceVariant = StardustSurfaceVariant,  // 表面变体
    onSurfaceVariant = MilkyGlow,             // 银河辉文字
    outline = DeepSky,                        // 深空蓝边框
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 根据 ThemeMode 判断是否使用深色主题
 */
@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.STARDUST -> true  // 星尘主题是深色系
    }
}

@Composable
fun LiveWallpaperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val colorScheme = when (themeMode) {
        ThemeMode.STARDUST -> StardustColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 沉浸式：设置状态栏和导航栏透明
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            // 让内容延伸到系统栏区域
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
