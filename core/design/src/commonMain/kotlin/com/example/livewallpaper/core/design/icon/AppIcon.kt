package com.example.livewallpaper.core.design.icon

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Standard icon sizes used by the application design system. */
object AppIconSize {
    val small: Dp = 16.dp
    val compact: Dp = 20.dp
    val standard: Dp = 24.dp
    val large: Dp = 32.dp
}

/**
 * Renders an icon from the application icon catalog.
 *
 * @param imageVector vector supplied by [AppIcons].
 * @param contentDescription localized accessibility description, or `null` for decoration.
 * @param modifier layout and drawing modifiers applied to the icon.
 * @param tint color applied to the monochrome Eva outline vector.
 */
@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
