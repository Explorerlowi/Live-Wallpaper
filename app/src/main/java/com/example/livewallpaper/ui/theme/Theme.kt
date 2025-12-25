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

/**
 * 根据 ThemeMode 判断是否使用深色主题
 */
@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun LiveWallpaperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
