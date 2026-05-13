package com.example.livewallpaper.core.design.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Adds a clickable modifier without ripple indication.
 *
 * Use this for selectable chips and lightweight tag controls where selection is communicated by
 * color and border changes. Regular command buttons should keep their ripple feedback.
 *
 * @param enabled Whether clicks are accepted.
 * @param onClick Action invoked when the element is clicked.
 * @return A modifier with ripple-free click handling.
 */
@Composable
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    return clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )
}
