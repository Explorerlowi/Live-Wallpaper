package com.example.livewallpaper.paint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import com.example.livewallpaper.ui.components.ImageSource
import com.example.livewallpaper.ui.theme.Teal300

@Composable
fun PaintBottomBar(
    promptText: String,
    selectedImages: List<SelectedImage>,
    isGenerating: Boolean,
    isLoading: Boolean,
    generationStartTime: Long,
    selectedModel: PaintModel,
    selectedRatio: AspectRatio,
    selectedResolution: Resolution,
    activeProfile: ApiProfile?,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onEnhance: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onModelClick: () -> Unit,
    onRatioClick: () -> Unit,
    onResolutionClick: () -> Unit,
    onImagePreview: ((ImageSource) -> Unit)? = null
) {
    // 实时计时器
    var elapsedSeconds by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    
    LaunchedEffect(isGenerating, generationStartTime) {
        if (isGenerating && generationStartTime > 0) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - generationStartTime) / 1000
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            // 选中的图片预览
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedImages.forEach { image ->
                        SelectedImagePreview(
                            image = image,
                            onRemove = { onRemoveImage(image.id) },
                            onClick = { 
                                onImagePreview?.invoke(ImageSource.StringSource(image.uri))
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 生成中状态显示
            if (isGenerating && elapsedSeconds > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Teal300.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Teal300
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.paint_generating_time, formatElapsedTime(context, elapsedSeconds)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal300
                    )
                }
            }

            // 快捷按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // API设置
                QuickActionChip(
                    icon = Icons.Default.Settings,
                    label = activeProfile?.name ?: stringResource(R.string.paint_no_api),
                    isHighlighted = activeProfile == null,
                    onClick = onSettingsClick
                )
                
                // 模型选择
                QuickActionChip(
                    icon = Icons.Default.AutoAwesome,
                    label = selectedModel.displayName,
                    onClick = onModelClick
                )
                
                // 比例选择
                QuickActionChip(
                    icon = Icons.Default.AspectRatio,
                    label = selectedRatio.displayName,
                    onClick = onRatioClick
                )
                
                // 分辨率选择 (仅Pro模型)
                if (selectedModel == PaintModel.GEMINI_3_PRO) {
                    QuickActionChip(
                        icon = Icons.Default.HighQuality,
                        label = selectedResolution.displayName,
                        onClick = onResolutionClick
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 优化按钮
                IconButton(
                    onClick = onEnhance,
                    enabled = promptText.isNotEmpty() && !isLoading && !isGenerating
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = stringResource(R.string.paint_enhance),
                            tint = if (promptText.isNotEmpty()) Teal300 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // 输入框
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = promptText,
                            onValueChange = onPromptChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 24.dp, max = 120.dp),
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(Teal300),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (promptText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.paint_prompt_hint),
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                // 图片按钮
                IconButton(onClick = onPickImage) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = stringResource(R.string.add_image),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 发送/停止按钮
                IconButton(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || promptText.isNotEmpty() || selectedImages.isNotEmpty()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isGenerating) MaterialTheme.colorScheme.error else Teal300
                    ) {
                        Icon(
                            if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isGenerating) stringResource(R.string.paint_stop) else stringResource(R.string.paint_send),
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlighted) 
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f) 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isHighlighted) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isHighlighted) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SelectedImagePreview(
    image: SelectedImage,
    onRemove: () -> Unit,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        // 删除按钮
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.delete_image),
                tint = Color.White,
                modifier = Modifier
                    .padding(2.dp)
                    .size(16.dp)
            )
        }
    }
}

/**
 * 格式化已用时间
 */
private fun formatElapsedTime(context: android.content.Context, seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) {
        context.getString(R.string.paint_time_format_minutes, minutes, secs)
    } else {
        context.getString(R.string.paint_time_format_seconds, secs)
    }
}
