package com.example.livewallpaper.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 按下时的缩放效果
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scale"
    )

    // 基于主色调生成渐变色
    val gradientColors = remember(containerColor) {
        listOf(
            containerColor,
            containerColor.adjustBrightness(-0.1f) // 稍微暗一点点，增加立体感
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 8.dp,
                shape = shape,
                spotColor = containerColor.copy(alpha = 0.5f),
                ambientColor = containerColor.copy(alpha = 0.3f)
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // 自定义效果，禁用默认水波纹
                onClick = onClick
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = gradientColors
                )
            )
    ) {
        // 玻璃质感的高光层 (上亮下暗)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 边框光泽 (模拟玻璃边缘反光)
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.3f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
        )

        // 按钮内容
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * 调整颜色的亮度
 * @param fraction > 0 变亮 (混合白色), < 0 变暗 (混合黑色)
 */
private fun Color.adjustBrightness(fraction: Float): Color {
    return if (fraction > 0) {
        // Lighten: mix with white
        val r = this.red + (1f - this.red) * fraction
        val g = this.green + (1f - this.green) * fraction
        val b = this.blue + (1f - this.blue) * fraction
        Color(r, g, b, this.alpha)
    } else {
        // Darken: mix with black
        val f = 1f + fraction // fraction is negative
        val r = this.red * f
        val g = this.green * f
        val b = this.blue * f
        Color(r, g, b, this.alpha)
    }
}
