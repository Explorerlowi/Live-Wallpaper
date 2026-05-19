package com.example.livewallpaper.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Defines semantic color tokens used by the app design system.
 *
 * These colors describe product intent instead of Material role names, so feature UI can depend on
 * stable app meanings such as page background, surface, primary text, and brand action.
 */
@Immutable
data class AppColorTokens(
    val pageBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val border: Color,
    val brand: Color,
    val brandPressed: Color,
    val brandSubtle: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val onBrand: Color,
)

/**
 * Selects one of the visual palettes supported by the design system.
 *
 * Feature screens should not branch on these values directly. Theme selection belongs at the app
 * shell level, while components consume [AppDesign.colors].
 */
enum class AppDesignThemeStyle {
    Fresh,
    Dark,
    Stardust,
    Clear,
}

internal val FreshLightColors = AppColorTokens(
    pageBackground = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceElevated = Color.White,
    surfaceMuted = Color(0xFFF2F4F7),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5F6673),
    textTertiary = Color(0xFF9AA1AD),
    divider = Color(0xFFEDEFF3),
    border = Color(0xFFE1E5EB),
    brand = Color(0xFF14B8A6),
    brandPressed = Color(0xFF0F9486),
    brandSubtle = Color(0xFFE7F8F5),
    success = Color(0xFF18A058),
    warning = Color(0xFFFFA940),
    danger = Color(0xFFFF4D4F),
    onBrand = Color.White,
)

internal val DarkColors = AppColorTokens(
    pageBackground = Color(0xFF111418),
    surface = Color(0xFF191D23),
    surfaceElevated = Color(0xFF20252C),
    surfaceMuted = Color(0xFF252B33),
    textPrimary = Color(0xFFF4F6F8),
    textSecondary = Color(0xFFB8C0CC),
    textTertiary = Color(0xFF7F8896),
    divider = Color(0xFF2D333D),
    border = Color(0xFF363D48),
    brand = Color(0xFF4DD6C8),
    brandPressed = Color(0xFF24B9AA),
    brandSubtle = Color(0xFF163D3A),
    success = Color(0xFF52C41A),
    warning = Color(0xFFFFC069),
    danger = Color(0xFFFF7875),
    onBrand = Color(0xFF071B19),
)

internal val StardustColors = AppColorTokens(
    pageBackground = Color(0xFF0B1838),
    surface = Color(0xFF101F42),
    surfaceElevated = Color(0xFF182A55),
    surfaceMuted = Color(0xFF223665),
    textPrimary = Color(0xFFE8F0FF),
    textSecondary = Color(0xFFC8DDF5),
    textTertiary = Color(0xFF88A5E0),
    divider = Color(0xFF263B6B),
    border = Color(0xFF3A60A0),
    brand = Color(0xFF88A5E0),
    brandPressed = Color(0xFF6F8ED0),
    brandSubtle = Color(0xFF223665),
    success = Color(0xFF67D6A3),
    warning = Color(0xFFFFC875),
    danger = Color(0xFFFF8C8C),
    onBrand = Color(0xFF0B1838),
)

internal val ClearColors = AppColorTokens(
    pageBackground = Color(0xFFFAFAFF),
    surface = Color.White,
    surfaceElevated = Color.White,
    surfaceMuted = Color(0xFFF2F0F8),
    textPrimary = Color(0xFF343044),
    textSecondary = Color(0xFF6F687E),
    textTertiary = Color(0xFF9E97AE),
    divider = Color(0xFFEDE9F5),
    border = Color(0xFFE2DCEF),
    brand = Color(0xFF8177F6),
    brandPressed = Color(0xFF6B60DC),
    brandSubtle = Color(0xFFF0EEFF),
    success = Color(0xFF18A058),
    warning = Color(0xFFFFA940),
    danger = Color(0xFFFF4D4F),
    onBrand = Color.White,
)
