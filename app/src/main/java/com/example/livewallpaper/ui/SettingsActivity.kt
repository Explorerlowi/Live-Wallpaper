package com.example.livewallpaper.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.ui.theme.LiveWallpaperTheme
import com.example.livewallpaper.ui.theme.MintGreen100
import com.example.livewallpaper.ui.theme.MintGreen200
import com.example.livewallpaper.ui.LanguageOption
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮，防止息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Edge-to-Edge 已在 themes.xml 中配置
        
        // 获取传入的参数
        val currentInterval = intent.getLongExtra("interval", 5000L)
        val currentScaleMode = ScaleMode.valueOf(
            intent.getStringExtra("scaleMode") ?: ScaleMode.CENTER_CROP.name
        )
        val currentPlayMode = PlayMode.valueOf(
            intent.getStringExtra("playMode") ?: PlayMode.SEQUENTIAL.name
        )
        val currentLanguageTag = intent.getStringExtra("languageTag")
        val currentThemeMode = ThemeMode.valueOf(
            intent.getStringExtra("themeMode") ?: ThemeMode.SYSTEM.name
        )
        
        // 立即设置内容，减少启动延迟
        setContent {
            // 使用 remember 和 mutableStateOf 来响应主题变化
            var themeMode by remember { mutableStateOf(currentThemeMode) }
            
            LiveWallpaperTheme(themeMode = themeMode) {
                SettingsScreen(
                    currentInterval = currentInterval,
                    currentScaleMode = currentScaleMode,
                    currentPlayMode = currentPlayMode,
                    currentLanguageTag = currentLanguageTag,
                    currentThemeMode = currentThemeMode,
                    onConfirm = { interval, scaleMode, playMode ->
                        intent.putExtra("interval", interval)
                        intent.putExtra("scaleMode", scaleMode.name)
                        intent.putExtra("playMode", playMode.name)
                        setResult(RESULT_OK, intent)
                    },
                    onLanguageChange = { language ->
                        intent.putExtra("languageTag", language.localeTag)
                        setResult(RESULT_OK, intent)
                        // 保存时应用语言变化
                        val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(language.localeTag)
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                        // 语言切换会重建 Activity，直接 finish 避免闪黑
                        finish()
                    },
                    onThemeModeChange = { newThemeMode ->
                        themeMode = newThemeMode  // 立即更新主题
                        intent.putExtra("themeMode", newThemeMode.name)
                        setResult(RESULT_OK, intent)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentInterval: Long,
    currentScaleMode: ScaleMode,
    currentPlayMode: PlayMode,
    currentLanguageTag: String?,
    currentThemeMode: ThemeMode,
    onConfirm: (interval: Long, scaleMode: ScaleMode, playMode: PlayMode) -> Unit,
    onLanguageChange: (LanguageOption) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    var intervalValue by remember { mutableFloatStateOf(currentInterval.toFloat()) }
    var selectedScaleMode by remember { mutableStateOf(currentScaleMode) }
    var selectedPlayMode by remember { mutableStateOf(currentPlayMode) }
    var showIntervalInputDialog by remember { mutableStateOf(false) }
    val initialLanguage = remember { LanguageOption.fromTag(currentLanguageTag) }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var selectedThemeMode by remember { mutableStateOf(currentThemeMode) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    
    // 检查是否有修改
    val hasChanges = remember(intervalValue, selectedScaleMode, selectedPlayMode, selectedLanguage, selectedThemeMode) {
        intervalValue.toLong() != currentInterval ||
        selectedScaleMode != currentScaleMode ||
        selectedPlayMode != currentPlayMode ||
        selectedLanguage != initialLanguage ||
        selectedThemeMode != currentThemeMode
    }
    
    // 处理返回按钮
    val handleBack: () -> Unit = {
        if (hasChanges) {
            showExitConfirmDialog = true
        } else {
            onBack()
        }
    }
    
    // 拦截系统返回键
    BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Surface(
                    modifier = Modifier
                        .clickable { showIntervalInputDialog = true }
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = stringResource(R.string.interval_seconds, intervalValue / 1000f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
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
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 主题设置
            Text(
                text = stringResource(R.string.theme_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        ThemeMode.STARDUST -> stringResource(R.string.theme_stardust)
                    }

                    FilterChip(
                        selected = selectedThemeMode == mode,
                        onClick = { 
                            selectedThemeMode = mode
                            // 立即应用主题变化
                            onThemeModeChange(mode)
                        },
                        label = { Text(label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
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
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    
    // 退出确认对话框
    if (showExitConfirmDialog) {
        ExitConfirmDialog(
            onSaveAndExit = {
                onConfirm(intervalValue.toLong(), selectedScaleMode, selectedPlayMode)
                showExitConfirmDialog = false
                // 语言变化时通知并应用（onLanguageChange 内部会 finish）
                if (selectedLanguage != initialLanguage) {
                    onLanguageChange(selectedLanguage)
                } else {
                    // 语言没变化，正常退出
                    onBack()
                }
            },
            onDiscardAndExit = {
                // 如果主题被修改了，需要恢复原来的主题
                if (selectedThemeMode != currentThemeMode) {
                    onThemeModeChange(currentThemeMode)
                }
                // 语言没有立即应用，所以不需要恢复
                showExitConfirmDialog = false
                onBack()
            },
            onDismiss = { showExitConfirmDialog = false }
        )
    }
}

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
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
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
                    color = MaterialTheme.colorScheme.primary
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

/**
 * 退出确认对话框
 */
@Composable
private fun ExitConfirmDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.exit_confirm_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.exit_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text(
                    text = stringResource(R.string.save_and_exit),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscardAndExit) {
                    Text(
                        text = stringResource(R.string.discard_and_exit),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
