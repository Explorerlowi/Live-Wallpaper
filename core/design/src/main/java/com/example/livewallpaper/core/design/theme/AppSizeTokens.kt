package com.example.livewallpaper.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Defines spacing steps used by screens and reusable components.
 *
 * The scale is intentionally compact because the app is a utility-oriented consumer product where
 * users scan photo lists and settings repeatedly.
 */
@Immutable
data class AppSpacingTokens(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val screenHorizontal: Dp = 16.dp,
)

/**
 * Defines corner radius values for app surfaces and controls.
 *
 * Radii are capped to avoid the heavy rounded Material3 look. Pill shapes remain opt-in for
 * floating controls or circular icon affordances only.
 */
@Immutable
data class AppRadiusTokens(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 10.dp,
    val lg: Dp = 14.dp,
    val xl: Dp = 18.dp,
)

/**
 * Defines fixed heights for common controls.
 *
 * Keeping dimensions stable prevents layout shifts when labels, icons, or loading states change.
 */
@Immutable
data class AppComponentSizeTokens(
    val topBarHeight: Dp = 52.dp,
    val buttonMediumHeight: Dp = 44.dp,
    val buttonLargeHeight: Dp = 48.dp,
    val inputHeight: Dp = 46.dp,
    val listItemMinHeight: Dp = 52.dp,
    val iconButtonSize: Dp = 40.dp,
)
