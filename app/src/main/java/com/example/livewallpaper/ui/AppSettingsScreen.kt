package com.example.livewallpaper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.BuildConfig
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode

import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.UpdateStatus
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    currentInterval: Long,
    currentScaleMode: ScaleMode,
    currentPlayMode: PlayMode,
    currentLanguageTag: String?,
    currentThemeMode: ThemeMode,
    updateStatus: UpdateStatus,
    onConfirm: (interval: Long, scaleMode: ScaleMode, playMode: PlayMode) -> Unit,
    onLanguageChange: (LanguageOption) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onCheckUpdate: () -> Unit,
    onClearUpdateStatus: () -> Unit,
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
    val context = LocalContext.current

    // Bottom Sheet States
    var showScaleModeSheet by remember { mutableStateOf(false) }
    var showPlayModeSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current

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
    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (hasChanges) {
                        TextButton(
                            onClick = {
                                onConfirm(intervalValue.toLong(), selectedScaleMode, selectedPlayMode)
                                // 语言变化时通知并应用
                                if (selectedLanguage != initialLanguage) {
                                    onLanguageChange(selectedLanguage)
                                } else {
                                    onBack()
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.save_and_exit),
                                fontWeight = FontWeight.Bold
                            )
                        }
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
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 壁纸设置组
                item {
                    SettingsGroupCard {
                        // 切换间隔
                        SettingsItem(
                            icon = Icons.Default.Timer,
                            title = stringResource(R.string.interval_label),
                            value = stringResource(R.string.interval_seconds, intervalValue / 1000f),
                            onClick = { showIntervalInputDialog = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 缩放模式
                        val scaleModeLabel = when (selectedScaleMode) {
                            ScaleMode.CENTER_CROP -> stringResource(R.string.scale_mode_fill)
                            ScaleMode.FIT_CENTER -> stringResource(R.string.scale_mode_fit)
                        }
                        SettingsItem(
                            icon = Icons.Default.AspectRatio,
                            title = stringResource(R.string.scale_mode_label),
                            value = scaleModeLabel,
                            onClick = { showScaleModeSheet = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 播放模式
                        val playModeLabel = when (selectedPlayMode) {
                            PlayMode.SEQUENTIAL -> stringResource(R.string.play_mode_sequential)
                            PlayMode.RANDOM -> stringResource(R.string.play_mode_random)
                        }
                        SettingsItem(
                            icon = Icons.Default.PlayCircle,
                            title = stringResource(R.string.play_mode_label),
                            value = playModeLabel,
                            onClick = { showPlayModeSheet = true }
                        )
                    }
                }

                // 外观设置组
                item {
                    SettingsGroupCard {
                        // 主题
                        val themeLabel = when (selectedThemeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            ThemeMode.STARDUST -> stringResource(R.string.theme_stardust)
                            ThemeMode.CLEAR -> stringResource(R.string.theme_clear)
                        }
                        SettingsItem(
                            icon = Icons.Default.ColorLens,
                            title = stringResource(R.string.theme_label),
                            value = themeLabel,
                            onClick = { showThemeSheet = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 语言
                        SettingsItem(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.language_label),
                            value = stringResource(selectedLanguage.labelRes),
                            onClick = { showLanguageSheet = true }
                        )
                    }
                }

                // 关于
                item {
                    SettingsGroupCard {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.check_update),
                            value = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                            onClick = onCheckUpdate
                        )
                    }
                }
            }
        }
    }

    // 检查更新状态处理
    when (updateStatus) {
        is UpdateStatus.Checking -> {
            // 不显示加载弹窗
        }
        is UpdateStatus.Success -> {
            if (updateStatus.hasNewVersion) {
                val targetVersion = updateStatus.version.orEmpty()
                Dialog(onDismissRequest = onClearUpdateStatus) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_new_version_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.update_version_change,
                                    BuildConfig.VERSION_NAME,
                                    targetVersion
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (!updateStatus.desc.isNullOrBlank()) {
                                Text(
                                    text = updateStatus.desc!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                            Button(
                                onClick = {
                                    onClearUpdateStatus()
                                    updateStatus.downloadUrl?.let { uriHandler.openUri(it) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.update_upgrade_now))
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            TextButton(
                                onClick = onClearUpdateStatus,
                                modifier = Modifier.height(32.dp) // 减小按钮高度
                            ) {
                                Text(
                                    text = stringResource(R.string.update_later),
                                    style = MaterialTheme.typography.bodySmall, // 减小字号
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            } else {
                val latestToast = stringResource(R.string.update_already_latest)
                LaunchedEffect(Unit) {
                    Toast.makeText(context, latestToast, Toast.LENGTH_SHORT).show()
                    onClearUpdateStatus()
                }
            }
        }
        is UpdateStatus.Error -> {
            val failedToast = stringResource(R.string.update_check_failed, updateStatus.message)
            LaunchedEffect(Unit) {
                Toast.makeText(context, failedToast, Toast.LENGTH_SHORT).show()
                onClearUpdateStatus()
            }
        }
        else -> {}
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

    // 缩放模式选择
    if (showScaleModeSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.scale_mode_label),
            options = ScaleMode.entries,
            selectedOption = selectedScaleMode,
            onOptionSelected = {
                selectedScaleMode = it
                showScaleModeSheet = false
            },
            onDismissRequest = { showScaleModeSheet = false },
            labelProvider = { mode ->
                when (mode) {
                    ScaleMode.CENTER_CROP -> stringResource(R.string.scale_mode_fill)
                    ScaleMode.FIT_CENTER -> stringResource(R.string.scale_mode_fit)
                }
            }
        )
    }

    // 播放模式选择
    if (showPlayModeSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.play_mode_label),
            options = PlayMode.entries,
            selectedOption = selectedPlayMode,
            onOptionSelected = {
                selectedPlayMode = it
                showPlayModeSheet = false
            },
            onDismissRequest = { showPlayModeSheet = false },
            labelProvider = { mode ->
                when (mode) {
                    PlayMode.SEQUENTIAL -> stringResource(R.string.play_mode_sequential)
                    PlayMode.RANDOM -> stringResource(R.string.play_mode_random)
                }
            }
        )
    }

    // 主题选择
    if (showThemeSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.theme_label),
            options = ThemeMode.entries,
            selectedOption = selectedThemeMode,
            onOptionSelected = {
                selectedThemeMode = it
                onThemeModeChange(it) // 立即应用主题预览
                showThemeSheet = false
            },
            onDismissRequest = { showThemeSheet = false },
            labelProvider = { mode ->
                when (mode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    ThemeMode.STARDUST -> stringResource(R.string.theme_stardust)
                    ThemeMode.CLEAR -> stringResource(R.string.theme_clear)
                }
            }
        )
    }

    // 语言选择
    if (showLanguageSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.language_label),
            options = LanguageOption.entries,
            selectedOption = selectedLanguage,
            onOptionSelected = {
                selectedLanguage = it
                showLanguageSheet = false
            },
            onDismissRequest = { showLanguageSheet = false },
            labelProvider = { stringResource(it.labelRes) }
        )
    }

    // 退出确认对话框
    if (showExitConfirmDialog) {
        ExitConfirmDialog(
            onSaveAndExit = {
                onConfirm(intervalValue.toLong(), selectedScaleMode, selectedPlayMode)
                if (selectedLanguage != initialLanguage) {
                    onLanguageChange(selectedLanguage)
                } else {
                    onBack()
                }
                showExitConfirmDialog = false
            },
            onDiscardAndExit = {
                // 如果主题被修改了，恢复原来的主题
                if (selectedThemeMode != currentThemeMode) {
                    onThemeModeChange(currentThemeMode)
                }
                showExitConfirmDialog = false
                onBack()
            },
            onDismiss = { showExitConfirmDialog = false }
        )
    }
}

@Composable
fun SettingsGroupCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = null,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectionBottomSheet(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    labelProvider: @Composable (T) -> String
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            options.forEach { option ->
                val isSelected = option == selectedOption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = labelProvider(option),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
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

