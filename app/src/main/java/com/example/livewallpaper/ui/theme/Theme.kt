package com.example.livewallpaper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.livewallpaper.core.design.theme.AppDesignTheme
import com.example.livewallpaper.core.design.theme.AppDesignThemeStyle
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode

/**
 * Returns whether the selected theme should use dark system bar icons and dark app colors.
 *
 * @param themeMode User-selected theme mode.
 * @return True when dark colors should be used.
 */
@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.STARDUST -> true
        ThemeMode.CLEAR -> false
    }
}

/**
 * App theme bridge used by Android screens.
 *
 * This function keeps platform system-bar handling in the app module and delegates visual tokens,
 * typography, shape, and Material3 mapping to `core:design`.
 *
 * @param themeMode User-selected theme mode.
 * @param content Composable content rendered inside the design system.
 */
@Composable
fun LiveWallpaperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val designStyle = when (themeMode) {
        ThemeMode.STARDUST -> AppDesignThemeStyle.Stardust
        ThemeMode.CLEAR -> AppDesignThemeStyle.Clear
        ThemeMode.DARK -> AppDesignThemeStyle.Dark
        ThemeMode.SYSTEM -> if (darkTheme) AppDesignThemeStyle.Dark else AppDesignThemeStyle.Fresh
        ThemeMode.LIGHT -> AppDesignThemeStyle.Fresh
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    AppDesignTheme(
        style = designStyle,
        content = content,
    )
}
