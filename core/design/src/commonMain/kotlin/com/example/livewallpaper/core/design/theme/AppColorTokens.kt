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

// 浅色主题：与品牌青色同色相的薄荷色系，表面与文字均带青灰调，
// 让强调色与中性层属于同一家族（参照星辰主题的单色相分层逻辑）。
internal val FreshLightColors = AppColorTokens(
    pageBackground = Color(0xFFF2F7F6),
    surface = Color.White,
    surfaceElevated = Color.White,
    surfaceMuted = Color(0xFFE3EEEB),
    textPrimary = Color(0xFF16211F),
    textSecondary = Color(0xFF5C6E6A),
    textTertiary = Color(0xFF92A5A0),
    divider = Color(0xFFE0EBE8),
    border = Color(0xFFCFE0DC),
    brand = Color(0xFF14B8A6),
    brandPressed = Color(0xFF0F9486),
    brandSubtle = Color(0xFFDDF3EF),
    success = Color(0xFF18A058),
    warning = Color(0xFFFFA940),
    danger = Color(0xFFFF4D4F),
    onBrand = Color.White,
)

// 深色主题：青黑色系深色，表面与文字统一偏向品牌青色相，
// 并拉宽 pageBackground → surface → surfaceMuted 的明度阶梯，
// 保证卡片以半透明叠加后仍能与背景分层（参照星辰主题的分层逻辑）。
internal val DarkColors = AppColorTokens(
    pageBackground = Color(0xFF0D1414),
    surface = Color(0xFF141D1C),
    surfaceElevated = Color(0xFF1B2724),
    surfaceMuted = Color(0xFF273834),
    textPrimary = Color(0xFFEAF4F2),
    textSecondary = Color(0xFFB4CBC7),
    textTertiary = Color(0xFF7C9C96),
    divider = Color(0xFF243230),
    border = Color(0xFF35494A),
    brand = Color(0xFF66CFC1),
    brandPressed = Color(0xFF3FB3A4),
    brandSubtle = Color(0xFF1C3733),
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

// 清透主题：紫色系整体保持不变，仅加深中间层（surfaceMuted / divider / border），
// 拉宽与纯白表面之间的明度阶梯，让卡片与分割线在半透明叠加后仍清晰可读。
internal val ClearColors = AppColorTokens(
    pageBackground = Color(0xFFF7F5FD),
    surface = Color.White,
    surfaceElevated = Color.White,
    surfaceMuted = Color(0xFFE9E4F6),
    textPrimary = Color(0xFF343044),
    textSecondary = Color(0xFF6F687E),
    textTertiary = Color(0xFF9E97AE),
    divider = Color(0xFFE5DFF2),
    border = Color(0xFFD8CFEC),
    brand = Color(0xFF8177F6),
    brandPressed = Color(0xFF6B60DC),
    brandSubtle = Color(0xFFF0EEFF),
    success = Color(0xFF18A058),
    warning = Color(0xFFFFA940),
    danger = Color(0xFFFF4D4F),
    onBrand = Color.White,
)
