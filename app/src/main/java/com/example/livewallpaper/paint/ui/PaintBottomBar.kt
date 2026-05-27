package com.example.livewallpaper.paint.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import com.example.livewallpaper.ui.components.ConfirmDialog
import com.example.livewallpaper.ui.components.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PaintBottomBar(
    promptText: String,
    selectedImages: List<SelectedImage>,
    isGenerating: Boolean,
    generatingCount: Int = 0,
    generationStartTime: Long,
    selectedModel: PaintModel,
    selectedRatio: AspectRatio,
    selectedResolution: Resolution,
    selectedGptSize: GptImageSize = GptImageSize.AUTO,
    selectedGptQuality: GptImageQuality = GptImageQuality.AUTO,
    selectedGptFormat: GptOutputFormat = GptOutputFormat.PNG,
    activeProfile: ApiProfile?,
    isApiProfileLoaded: Boolean = true,
    collapsedInputFocusRequester: FocusRequester,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onReorderImages: (List<SelectedImage>) -> Unit,
    onSettingsClick: () -> Unit,
    onModelClick: () -> Unit,
    onRatioClick: () -> Unit,
    onResolutionClick: () -> Unit,
    onGptSizeClick: () -> Unit = {},
    onGptQualityClick: () -> Unit = {},
    onGptFormatClick: () -> Unit = {},
    onExpandInput: () -> Unit,
    onImagePreview: ((List<ImageSource>, Int) -> Unit)? = null,
    onApplyRatio: ((AspectRatio) -> Unit)? = null
) {
    // 实时计时器
    var elapsedSeconds by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    
    // 描述选择器状态
    var showDescriptionPicker by remember { mutableStateOf(false) }
    
    // 确认弹窗状态
    var showClearConfirm by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    
    // 输入框行数
    var lineCount by remember { mutableIntStateOf(1) }

    var promptFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = promptText,
                selection = TextRange(promptText.length)
            )
        )
    }
    val selectedImageListState = rememberLazyListState()
    val selectedImageReorderState = rememberReorderableLazyListState(selectedImageListState) { from, to ->
        onReorderImages(selectedImages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        })
    }

    LaunchedEffect(promptText) {
        if (promptText != promptFieldValue.text) {
            promptFieldValue = TextFieldValue(
                text = promptText,
                selection = TextRange(promptText.length)
            )
        }
    }
    
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
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                // 选中的图片预览
                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        state = selectedImageListState,
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(selectedImages, key = { _, image -> image.id }) { index, image ->
                            // 计算该图片的推荐比例
                            val imageRatio = remember(image.width, image.height) {
                                if (image.width > 0 && image.height > 0) {
                                    AspectRatio.findClosest(image.width, image.height)
                                } else null
                            }
                            
                            ReorderableItem(state = selectedImageReorderState, key = image.id) {
                                Box(modifier = Modifier.longPressDraggableHandle()) {
                                    SelectedImagePreview(
                                        image = image,
                                        recommendedRatio = imageRatio,
                                        isRatioSelected = imageRatio == selectedRatio,
                                        onRemove = { onRemoveImage(image.id) },
                                        onClick = {
                                            val allImages = selectedImages.map { ImageSource.StringSource(it.uri) }
                                            onImagePreview?.invoke(allImages, index)
                                        },
                                        onRatioClick = {
                                            imageRatio?.let { onApplyRatio?.invoke(it) }
                                        }
                                    )
                                }
                            }
                        }
                        // 添加参考图占位卡片
                        item {
                            AddReferenceImageCard(onClick = onPickImage)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                // 生成中状态显示
                if (isGenerating && elapsedSeconds > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (generatingCount > 1) {
                                stringResource(R.string.paint_generating_multiple_time, generatingCount, formatElapsedTime(context, elapsedSeconds))
                            } else {
                                stringResource(R.string.paint_generating_time, formatElapsedTime(context, elapsedSeconds))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 快捷按钮栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // API设置
                    QuickActionChip(
                        icon = Icons.Default.Settings,
                        label = when {
                            !isApiProfileLoaded -> stringResource(R.string.paint_api_loading)
                            activeProfile != null -> activeProfile.name
                            else -> stringResource(R.string.paint_no_api)
                        },
                        isHighlighted = isApiProfileLoaded && activeProfile == null,
                        onClick = onSettingsClick
                    )
                    
                    // 模型选择
                    QuickActionChip(
                        icon = Icons.Default.AutoAwesome,
                        label = selectedModel.displayName,
                        onClick = onModelClick
                    )
                    
                    // 比例选择（Gemini 模型使用）
                    if (!selectedModel.isGpt) {
                        QuickActionChip(
                            icon = Icons.Default.AspectRatio,
                            label = selectedRatio.displayName,
                            onClick = onRatioClick
                        )
                    }
                    
                    // 分辨率选择（支持分辨率的模型显示）
                    if (selectedModel.supportsResolution) {
                        QuickActionChip(
                            icon = Icons.Default.HighQuality,
                            label = selectedResolution.displayName,
                            onClick = onResolutionClick
                        )
                    }
                    
                    // GPT 尺寸选择
                    if (selectedModel.supportsGptSize) {
                        QuickActionChip(
                            icon = Icons.Default.PhotoSizeSelectLarge,
                            label = if (selectedGptSize == GptImageSize.AUTO) {
                                stringResource(R.string.paint_gpt_size_auto)
                            } else {
                                selectedGptSize.displayName
                            },
                            onClick = onGptSizeClick
                        )
                    }
                    
                    // GPT 质量选择
                    if (selectedModel.supportsGptQuality) {
                        QuickActionChip(
                            icon = Icons.Default.HighQuality,
                            label = when (selectedGptQuality) {
                                GptImageQuality.AUTO -> stringResource(R.string.paint_gpt_quality_auto)
                                GptImageQuality.LOW -> stringResource(R.string.paint_gpt_quality_low)
                                GptImageQuality.MEDIUM -> stringResource(R.string.paint_gpt_quality_medium)
                                GptImageQuality.HIGH -> stringResource(R.string.paint_gpt_quality_high)
                            },
                            onClick = onGptQualityClick
                        )
                    }
                    
                    // GPT 输出格式选择
                    if (selectedModel.isGpt) {
                        QuickActionChip(
                            icon = Icons.Default.Image,
                            label = when (selectedGptFormat) {
                                GptOutputFormat.PNG -> stringResource(R.string.paint_gpt_format_png)
                                GptOutputFormat.JPEG -> stringResource(R.string.paint_gpt_format_jpeg)
                                GptOutputFormat.WEBP -> stringResource(R.string.paint_gpt_format_webp)
                            },
                            onClick = onGptFormatClick
                        )
                    }
                    
                    // 添加描述
                    QuickActionChip(
                        icon = Icons.Default.Add,
                        label = stringResource(R.string.desc_add_description),
                        onClick = { showDescriptionPicker = true }
                    )
                }

                // 输入栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 输入框
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                BasicTextField(
                                    value = promptFieldValue,
                                    onValueChange = { newValue ->
                                        promptFieldValue = newValue
                                        onPromptChange(newValue.text)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(collapsedInputFocusRequester)
                                        .heightIn(min = 24.dp, max = 120.dp)
                                        .padding(vertical = 8.dp),
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    onTextLayout = { textLayoutResult ->
                                        lineCount = textLayoutResult.lineCount
                                    },
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

                                // 相册按钮（已有参考图时隐藏，通过占位卡片添加）
                                if (selectedImages.isEmpty()) {
                                    Surface(
                                        onClick = onPickImage,
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = stringResource(R.string.add_image),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                // 发送/停止按钮
                                if (isGenerating) {
                                    Surface(
                                        onClick = { showStopConfirm = true },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Default.Stop,
                                                contentDescription = stringResource(R.string.paint_stop),
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                if (promptText.isNotEmpty()) {
                                    Surface(
                                        onClick = onSend,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = stringResource(R.string.paint_send),
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 展开和清除按钮 - 固定在输入框内部右上角，竖排
                            androidx.compose.animation.AnimatedVisibility(
                                visible = lineCount >= 4,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    // 展开按钮
                                    IconButton(
                                        onClick = onExpandInput,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.OpenInFull,
                                            contentDescription = stringResource(R.string.paint_expand_input),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 清除按钮
                                    Text(
                                        text = stringResource(R.string.paint_clear),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (promptText.isNotEmpty())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .clickable(
                                                enabled = promptText.isNotEmpty(),
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) { showClearConfirm = true }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 描述选择器底部抽屉
    if (showDescriptionPicker) {
        DescriptionPickerSheet(
            onDismiss = { showDescriptionPicker = false },
            onConfirm = { selectedDescriptions ->
                if (selectedDescriptions.isNotEmpty()) {
                    val descriptionsText = selectedDescriptions.joinToString(", ")
                    val newPrompt = if (promptText.isEmpty()) {
                        descriptionsText
                    } else {
                        "$promptText, $descriptionsText"
                    }
                    onPromptChange(newPrompt)
                }
            }
        )
    }
    
    // 清除确认弹窗
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.paint_clear_confirm_title),
            message = stringResource(R.string.paint_clear_confirm_message),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { onPromptChange("") },
            onDismiss = { showClearConfirm = false }
        )
    }

    // 停止生成确认弹窗
    if (showStopConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.paint_stop_confirm_title),
            message = stringResource(R.string.paint_stop_confirm_message),
            confirmText = stringResource(R.string.paint_stop),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { onStop() },
            onDismiss = { showStopConfirm = false }
        )
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
        shape = RoundedCornerShape(8.dp),
        color = if (isHighlighted) 
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f) 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
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

/**
 * 添加参考图占位卡片
 * 显示在已选图片列表末尾，点击触发图片选择
 */
@Composable
private fun AddReferenceImageCard(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.paint_add_reference_image),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SelectedImagePreview(
    image: SelectedImage,
    recommendedRatio: AspectRatio? = null,
    isRatioSelected: Boolean = false,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    onRatioClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
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
        
        // 推荐比例标签
        if (recommendedRatio != null) {
            Text(
                text = recommendedRatio.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isRatioSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.clickable(onClick = onRatioClick)
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

@Composable
fun FullScreenPromptOverlay(
    promptText: String,
    selectedImages: List<SelectedImage>,
    isGenerating: Boolean,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onReorderImages: (List<SelectedImage>) -> Unit,
    onImagePreview: ((List<ImageSource>, Int) -> Unit)?,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = promptText,
                selection = TextRange(promptText.length)
            )
        )
    }
    
    // 确认弹窗状态
    var showClearConfirm by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    val selectedImageListState = rememberLazyListState()
    val selectedImageReorderState = rememberReorderableLazyListState(selectedImageListState) { from, to ->
        onReorderImages(selectedImages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        })
    }
    
    LaunchedEffect(Unit) {
        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
        focusRequester.requestFocus()
    }

    LaunchedEffect(promptText) {
        if (promptText != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = promptText,
                selection = TextRange(promptText.length)
            )
        }
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.ime),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedImages.isNotEmpty()) {
                        Text(
                            text = "${selectedImages.size} ${stringResource(R.string.paint_image)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 清除按钮（右上角）
                    TextButton(
                        onClick = { if (textFieldValue.text.isNotEmpty()) showClearConfirm = true },
                        enabled = textFieldValue.text.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.paint_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (textFieldValue.text.isNotEmpty()) 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    state = selectedImageListState,
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(selectedImages, key = { _, image -> image.id }) { index, image ->
                        ReorderableItem(state = selectedImageReorderState, key = image.id) {
                            Box(modifier = Modifier.longPressDraggableHandle()) {
                                SelectedImagePreview(
                                    image = image,
                                    onRemove = { onRemoveImage(image.id) },
                                    onClick = {
                                        val allImages = selectedImages.map { ImageSource.StringSource(it.uri) }
                                        onImagePreview?.invoke(allImages, index)
                                    }
                                )
                            }
                        }
                    }
                    // 添加参考图占位卡片
                    item {
                        AddReferenceImageCard(onClick = onPickImage)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onPromptChange(it.text)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 28.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.paint_prompt_hint),
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        lineHeight = 28.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onPickImage,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = stringResource(R.string.paint_image),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isGenerating) {
                    Surface(
                        onClick = { showStopConfirm = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.paint_stop),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                run {
                    val canSend = textFieldValue.text.isNotEmpty() || selectedImages.isNotEmpty()
                    Surface(
                        onClick = { if (canSend) onSend() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = stringResource(R.string.paint_send),
                            color = if (canSend) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
    
    // 清除确认弹窗
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.paint_clear_confirm_title),
            message = stringResource(R.string.paint_clear_confirm_message),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { onPromptChange("") },
            onDismiss = { showClearConfirm = false }
        )
    }

    // 停止生成确认弹窗
    if (showStopConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.paint_stop_confirm_title),
            message = stringResource(R.string.paint_stop_confirm_message),
            confirmText = stringResource(R.string.paint_stop),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { onStop() },
            onDismiss = { showStopConfirm = false }
        )
    }
}
