package com.example.livewallpaper.desktop

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 与当前 Material 主题配色一致的桌面端滚动条样式。
 *
 * 默认态使用 onSurfaceVariant 的低透明度，悬停 / 拖动时切换为主题主色，
 * 保证在浅色、深色以及自定义主题下都能与内容区形成合适的对比。
 *
 * @return 可直接传给 `VerticalScrollbar` / `HorizontalScrollbar` 的样式。
 */
@Composable
fun desktopThemedScrollbarStyle(): ScrollbarStyle {
    val colorScheme = MaterialTheme.colorScheme
    return ScrollbarStyle(
        minimalHeight = 36.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(3.dp),
        hoverDurationMillis = 240,
        unhoverColor = colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
        hoverColor = colorScheme.primary.copy(alpha = 0.72f),
    )
}
