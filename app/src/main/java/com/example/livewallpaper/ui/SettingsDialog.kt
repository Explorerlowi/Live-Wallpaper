package com.example.livewallpaper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.ui.theme.Teal300
import java.util.Locale

/**
 * 设置对话框
 * 用于调整壁纸切换间隔、缩放模式和播放模式
 * 只有点击确认按钮时才会保存设置
 */
@Composable
fun SettingsDialog(
    currentInterval: Long,
    currentScaleMode: ScaleMode,
    currentPlayMode: PlayMode,
    currentLanguageTag: String?,
    onConfirm: (interval: Long, scaleMode: ScaleMode, playMode: PlayMode) -> Unit,
    onLanguageChange: (LanguageOption) -> Unit,
    onDismiss: () -> Unit
) {
    // 本地状态，用于临时保存用户的修改
    var intervalValue by remember { mutableFloatStateOf(currentInterval.toFloat()) }
    var selectedScaleMode by remember { mutableStateOf(currentScaleMode) }
    var selectedPlayMode by remember { mutableStateOf(currentPlayMode) }
    var showIntervalInputDialog by remember { mutableStateOf(false) }
    val initialLanguage = remember { LanguageOption.fromTag(currentLanguageTag) }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 间隔设置
                Text(
                    text = stringResource(R.string.interval_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = intervalValue,
                        onValueChange = { intervalValue = it },
                        valueRange = 1000f..60000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Teal300,
                            activeTrackColor = Teal300
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 可点击的间隔数值，点击后弹出输入框
                    Surface(
                        modifier = Modifier
                            .clickable { showIntervalInputDialog = true }
                            .clip(RoundedCornerShape(8.dp)),
                        color = Teal300.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = stringResource(R.string.interval_seconds, intervalValue / 1000f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Teal300,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .width(72.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 缩放模式设置
                Text(
                    text = stringResource(R.string.scale_mode_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScaleMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ScaleMode.CENTER_CROP -> stringResource(R.string.scale_mode_fill)
                            ScaleMode.FIT_CENTER -> stringResource(R.string.scale_mode_fit)
                        }
                        
                        FilterChip(
                            selected = selectedScaleMode == mode,
                            onClick = { selectedScaleMode = mode },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Teal300,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 播放模式设置
                Text(
                    text = stringResource(R.string.play_mode_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayMode.entries.forEach { mode ->
                        val label = when (mode) {
                            PlayMode.SEQUENTIAL -> stringResource(R.string.play_mode_sequential)
                            PlayMode.RANDOM -> stringResource(R.string.play_mode_random)
                        }
                        
                        FilterChip(
                            selected = selectedPlayMode == mode,
                            onClick = { selectedPlayMode = mode },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Teal300,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 语言设置
                Text(
                    text = stringResource(R.string.language_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageOption.entries.forEach { option ->
                        FilterChip(
                            selected = selectedLanguage == option,
                            onClick = { selectedLanguage = option },
                            label = { Text(stringResource(option.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Teal300,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 取消按钮
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 确认按钮
                    TextButton(
                        onClick = {
                            onConfirm(intervalValue.toLong(), selectedScaleMode, selectedPlayMode)
                            if (selectedLanguage != initialLanguage) {
                                onLanguageChange(selectedLanguage)
                            }
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.confirm),
                            color = Teal300,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // 间隔输入对话框
    if (showIntervalInputDialog) {
        IntervalInputDialog(
            currentValue = intervalValue / 1000f,
            onConfirm = { seconds ->
                val millis = (seconds * 1000).toLong().coerceIn(1000L, 60000L)
                intervalValue = millis.toFloat()
                showIntervalInputDialog = false
            },
            onDismiss = { showIntervalInputDialog = false }
        )
    }
}

/**
 * 间隔输入对话框
 */
@Composable
private fun IntervalInputDialog(
    currentValue: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.interval_input_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.interval_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.interval_seconds_label)) },
                    suffix = { Text("s") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val value = inputText.toFloatOrNull()
                            if (value != null && value >= 1f && value <= 60f) {
                                onConfirm(value)
                            } else {
                                isError = true
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal300,
                        cursorColor = Teal300
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.interval_input_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = inputText.toFloatOrNull()
                    if (value != null && value >= 1f && value <= 60f) {
                        onConfirm(value)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                    color = Teal300
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

enum class LanguageOption(val localeTag: String, val labelRes: Int) {
    EN("en", R.string.language_en),
    ZH("zh", R.string.language_zh);

    companion object {
        fun fromTag(tag: String?): LanguageOption {
            if (tag.isNullOrBlank()) {
                val defaultLang = Locale.getDefault().language
                return if (defaultLang.startsWith("zh")) ZH else EN
            }
            return if (tag.startsWith("zh")) ZH else EN
        }
    }
}
