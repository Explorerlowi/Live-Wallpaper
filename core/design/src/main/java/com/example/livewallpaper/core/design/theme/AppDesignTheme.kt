package com.example.livewallpaper.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalAppColors = staticCompositionLocalOf { FreshLightColors }
private val LocalAppSpacing = staticCompositionLocalOf { AppSpacingTokens() }
private val LocalAppRadius = staticCompositionLocalOf { AppRadiusTokens() }
private val LocalAppComponentSizes = staticCompositionLocalOf { AppComponentSizeTokens() }
private val LocalAppTypography = staticCompositionLocalOf { AppTypographyTokens() }

/**
 * Entry point for the app design system.
 *
 * It maps app-specific tokens into Material3 so existing Compose APIs keep working, while new
 * screens can consume [AppDesign] tokens and components directly.
 *
 * @param style Visual palette selected by the app shell.
 * @param content Content rendered with the design system applied.
 */
@Composable
fun AppDesignTheme(
    style: AppDesignThemeStyle = AppDesignThemeStyle.Fresh,
    content: @Composable () -> Unit,
) {
    val colors = colorsForStyle(style)
    val radius = AppRadiusTokens()
    val typography = AppTypographyTokens()

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppSpacing provides AppSpacingTokens(),
        LocalAppRadius provides radius,
        LocalAppComponentSizes provides AppComponentSizeTokens(),
        LocalAppTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme(colors, style == AppDesignThemeStyle.Dark || style == AppDesignThemeStyle.Stardust),
            typography = materialTypography(typography),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(radius.xs),
                small = RoundedCornerShape(radius.sm),
                medium = RoundedCornerShape(radius.md),
                large = RoundedCornerShape(radius.lg),
                extraLarge = RoundedCornerShape(radius.xl),
            ),
            content = content,
        )
    }
}

/**
 * Provides access to current design tokens inside composables.
 */
object AppDesign {
    /**
     * Current semantic color tokens.
     */
    val colors: AppColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    /**
     * Current spacing tokens.
     */
    val spacing: AppSpacingTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    /**
     * Current radius tokens.
     */
    val radius: AppRadiusTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppRadius.current

    /**
     * Current component dimension tokens.
     */
    val sizes: AppComponentSizeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppComponentSizes.current

    /**
     * Current app typography tokens.
     */
    val typography: AppTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current
}

internal fun colorsForStyle(style: AppDesignThemeStyle): AppColorTokens {
    return when (style) {
        AppDesignThemeStyle.Fresh -> FreshLightColors
        AppDesignThemeStyle.Dark -> DarkColors
        AppDesignThemeStyle.Stardust -> StardustColors
        AppDesignThemeStyle.Clear -> ClearColors
    }
}

private fun materialColorScheme(colors: AppColorTokens, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandSubtle,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.brandPressed,
            onSecondary = colors.onBrand,
            secondaryContainer = colors.surfaceMuted,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.success,
            onTertiary = Color.White,
            tertiaryContainer = colors.surfaceMuted,
            onTertiaryContainer = colors.textPrimary,
            background = colors.pageBackground,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            outlineVariant = colors.divider,
            error = colors.danger,
            onError = Color.White,
            errorContainer = colors.surfaceMuted,
            onErrorContainer = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandSubtle,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.brandPressed,
            onSecondary = colors.onBrand,
            secondaryContainer = colors.surfaceMuted,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.success,
            onTertiary = Color.White,
            tertiaryContainer = colors.surfaceMuted,
            onTertiaryContainer = colors.textPrimary,
            background = colors.pageBackground,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            outlineVariant = colors.divider,
            error = colors.danger,
            onError = Color.White,
            errorContainer = colors.surfaceMuted,
            onErrorContainer = colors.danger,
        )
    }
}

private fun materialTypography(appTypography: AppTypographyTokens): Typography {
    return Typography(
        displayLarge = appTypography.pageTitle,
        displayMedium = appTypography.pageTitle,
        displaySmall = appTypography.sectionTitle,
        headlineLarge = appTypography.pageTitle,
        headlineMedium = appTypography.pageTitle,
        headlineSmall = appTypography.sectionTitle,
        titleLarge = appTypography.pageTitle,
        titleMedium = appTypography.sectionTitle,
        titleSmall = appTypography.itemTitle,
        bodyLarge = appTypography.body,
        bodyMedium = appTypography.body,
        bodySmall = appTypography.caption,
        labelLarge = appTypography.button,
        labelMedium = appTypography.bodyStrong,
        labelSmall = appTypography.caption,
    )
}
