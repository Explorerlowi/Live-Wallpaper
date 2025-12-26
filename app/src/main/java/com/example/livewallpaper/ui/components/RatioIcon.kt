package com.example.livewallpaper.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 比例图标组件
 * 根据宽高比绘制不同形状的矩形图标
 * 
 * @param widthRatio 宽度比例
 * @param heightRatio 高度比例
 * @param size 图标整体大小
 */
@Composable
fun RatioIcon(
    widthRatio: Int,
    heightRatio: Int,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val maxDimension = size
    val ratio = widthRatio.toFloat() / heightRatio.toFloat()
    
    val (width, height) = if (ratio >= 1f) {
        // 横向或正方形
        val w = maxDimension
        val h = maxDimension / ratio
        w to h
    } else {
        // 纵向
        val h = maxDimension
        val w = maxDimension * ratio
        w to h
    }
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}
