package com.example.livewallpaper.ui.theme

import android.app.Activity
import android.os.Build
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
    primary = DarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = Teal500,
    onPrimaryContainer = MintGreen100,
    secondary = Teal200,
    onSecondary = Color.Black,
    secondaryContainer = Teal500,
    onSecondaryContainer = MintGreen100,
    tertiary = MintGreen300,
    onTertiary = Color.Black,
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

@Composable
fun LiveWallpaperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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
