@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.livewallpaper.desktop.paint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberDialogState
import com.example.livewallpaper.desktop.DesktopImageFilePicker
import com.example.livewallpaper.desktop.DesktopStrings
import com.example.livewallpaper.desktop.LocalDesktopStrings
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.AuthMode
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

@Composable
fun AiPaintWorkspace(
    viewModel: DesktopPaintViewModel,
    isSidebarCollapsed: Boolean,
    onToggleSidebar: () -> Unit,
    onAddImagesToWallpapers: (List<String>) -> Unit,
    onSetWallpaperPath: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalDesktopStrings.current
    val listState = rememberLazyListState()
    var showApiSettings by remember { mutableStateOf(false) }
    var optionDialog by remember { mutableStateOf<PaintOptionDialog?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.scrollToBottomEvent.collect {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    if (showApiSettings) {
        ApiSettingsDialog(
            uiState = uiState,
            onDismiss = { showApiSettings = false },
            onEvent = viewModel::onEvent,
        )
    }
    optionDialog?.let { dialog ->
        PaintOptionDialogView(
            dialog = dialog,
            onDismiss = { optionDialog = null },
        )
    }
    previewImagePath?.let { path ->
        PaintImagePreviewDialog(
            path = path,
            onDismiss = { previewImagePath = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PaintTopBar(
            uiState = uiState,
            isSidebarCollapsed = isSidebarCollapsed,
            onToggleSidebar = onToggleSidebar,
            onShowApiSettings = { showApiSettings = true },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val messages = remember(uiState.messages, uiState.activeVersions) {
                uiState.messages.visibleWithActiveVersions(uiState.activeVersions)
            }
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.paintEmptyConversationTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.paintEmptyConversationSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        PaintMessageRow(
                            message = message,
                            uiState = uiState,
                            onPreviewImage = { previewImagePath = it },
                            onAddToWallpapers = { path -> onAddImagesToWallpapers(listOf(path)) },
                            onSetWallpaper = onSetWallpaperPath,
                            onRegenerate = { viewModel.onEvent(PaintEvent.RegenerateMessage(message.id)) },
                            onDelete = { viewModel.onEvent(PaintEvent.DeleteMessage(message.id)) },
                            onSwitchVersion = { group, index ->
                                viewModel.onEvent(PaintEvent.SwitchMessageVersion(group, index))
                            },
                        )
                    }
                }
            }
        }
        PaintInputBar(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onShowApiSettings = { showApiSettings = true },
            onShowOptions = { optionDialog = it },
            onPreviewImage = { previewImagePath = it },
        )
        uiState.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = error.localizedPaintText(strings),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { viewModel.onEvent(PaintEvent.ClearError) }) {
                        Text(strings.close)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.DesktopPaintSidebarSection(
    uiState: PaintUiState,
    onEvent: (PaintEvent) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = strings.paintSessions,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            onClick = { onEvent(PaintEvent.CreateSession()) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = strings.paintNewSession,
                modifier = Modifier.padding(6.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (uiState.sessions.isEmpty()) {
            Text(
                text = strings.paintNoSessions,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(uiState.sessions, key = { it.id }) { session ->
                    PaintSessionListItem(
                        session = session,
                        selected = uiState.currentSession?.id == session.id,
                        generatingCount = uiState.generatingSessionCounts[session.id] ?: 0,
                        onSelect = { onEvent(PaintEvent.SelectSession(session.id)) },
                        onPinToggle = {
                            onEvent(if (session.isPinned) PaintEvent.UnpinSession(session.id) else PaintEvent.PinSession(session.id))
                        },
                        onRename = { name -> onEvent(PaintEvent.RenameSession(session.id, name)) },
                        onDelete = { onEvent(PaintEvent.DeleteSession(session.id)) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintSessionListItem(
    session: PaintSession,
    selected: Boolean,
    generatingCount: Int,
    onSelect: () -> Unit,
    onPinToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var hovered by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    if (showRename) {
        RenameSessionDialog(
            initialValue = session.title,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                onRename(it)
            },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = strings.paintDeleteSessionTitle,
            message = strings.paintDeleteSessionMessage,
            confirmText = strings.paintDeleteSession,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                onDelete()
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(background)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (session.isPinned) {
            Text(text = "⌃", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = session.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (generatingCount > 0) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        Box {
            if (hovered || menuExpanded) {
                Surface(
                    onClick = { menuExpanded = true },
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                ) {
                    Text(
                        text = "...",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                DropdownMenuItem(
                    text = { Text(if (session.isPinned) strings.paintUnpinSession else strings.paintPinSession) },
                    onClick = {
                        menuExpanded = false
                        onPinToggle()
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.paintRenameSession) },
                    onClick = {
                        menuExpanded = false
                        showRename = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.paintDeleteSession, color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        showDelete = true
                    },
                )
            }
        }
    }
}

@Composable
private fun PaintTopBar(
    uiState: PaintUiState,
    isSidebarCollapsed: Boolean,
    onToggleSidebar: () -> Unit,
    onShowApiSettings: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    Box(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 24.dp),
    ) {
        Surface(
            onClick = onToggleSidebar,
            modifier = Modifier.align(Alignment.CenterStart).size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isSidebarCollapsed) Icons.Default.Menu else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = uiState.currentSession?.title ?: strings.aiPaintTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${uiState.selectedModel.displayName} · ${uiState.messages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            DesktopPaintChip(
                label = uiState.activeProfile?.name ?: strings.paintNoApi,
                highlighted = uiState.activeProfile == null,
                onClick = onShowApiSettings,
            )
        }
    }
}

@Composable
private fun PaintMessageRow(
    message: PaintMessage,
    uiState: PaintUiState,
    onPreviewImage: (String) -> Unit,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
) {
    val isUser = message.senderIdentity == SenderIdentity.USER
    val strings = LocalDesktopStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (message.messageContent.isNotBlank()) {
                    Text(
                        text = message.messageContent.localizedPaintText(strings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (message.status == MessageStatus.GENERATING) {
                    GeneratingBlock()
                }
                if (message.status == MessageStatus.ERROR) {
                    StatusText(strings.paintFailed, MaterialTheme.colorScheme.error)
                }
                if (message.status == MessageStatus.CANCELLED) {
                    StatusText(strings.paintCancelled, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.images.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        message.images.forEach { image ->
                            PaintImageThumb(
                                image = image,
                                onPreviewImage = onPreviewImage,
                                onAddToWallpapers = onAddToWallpapers,
                                onSetWallpaper = onSetWallpaper,
                            )
                        }
                    }
                }
                if (!isUser) {
                    MessageActions(
                        message = message,
                        uiState = uiState,
                        onRegenerate = onRegenerate,
                        onDelete = onDelete,
                        onSwitchVersion = onSwitchVersion,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActions(
    message: PaintMessage,
    uiState: PaintUiState,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val versionGroup = message.versionGroup
    val versions = if (versionGroup != null) {
        uiState.messages.filter { it.versionGroup == versionGroup }.sortedBy { it.versionIndex }
    } else {
        emptyList()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (versions.size > 1) {
            val currentIndex = (uiState.activeVersions[versionGroup] ?: message.versionIndex).coerceIn(0, versions.lastIndex)
            TextButton(
                onClick = { onSwitchVersion(versionGroup!!, (currentIndex - 1).coerceAtLeast(0)) },
                enabled = currentIndex > 0,
            ) { Text("<") }
            Text(
                text = "${currentIndex + 1}/${versions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onSwitchVersion(versionGroup!!, (currentIndex + 1).coerceAtMost(versions.lastIndex)) },
                enabled = currentIndex < versions.lastIndex,
            ) { Text(">") }
        }
        TextButton(onClick = onRegenerate, enabled = message.status != MessageStatus.GENERATING) {
            Text(strings.paintRegenerate)
        }
        TextButton(onClick = onDelete) {
            Text(strings.paintDeleteMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PaintImageThumb(
    image: PaintImage,
    onPreviewImage: (String) -> Unit,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
) {
    val path = image.localPath
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = path != null) { path?.let(onPreviewImage) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ) {
            if (path != null) {
                FileImage(path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (path != null) {
            Surface(
                onClick = { menuExpanded = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Text("...", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            ImageActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                path = path,
                onAddToWallpapers = onAddToWallpapers,
                onSetWallpaper = onSetWallpaper,
            )
        }
    }
}

@Composable
private fun ImageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    path: String,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        DropdownMenuItem(
            text = { Text(strings.paintCopyPath) },
            onClick = {
                onDismiss()
                copyToClipboard(path)
            },
        )
        DropdownMenuItem(
            text = { Text(strings.paintSaveAs) },
            onClick = {
                onDismiss()
                saveImageAs(path, strings.paintSaveAs)
            },
        )
        DropdownMenuItem(
            text = { Text(strings.paintAddToWallpaper) },
            onClick = {
                onDismiss()
                onAddToWallpapers(path)
            },
        )
        DropdownMenuItem(
            text = { Text(strings.paintSetWallpaper) },
            onClick = {
                onDismiss()
                onSetWallpaper(path)
            },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintInputBar(
    uiState: PaintUiState,
    onEvent: (PaintEvent) -> Unit,
    onShowApiSettings: () -> Unit,
    onShowOptions: (PaintOptionDialog) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var showAttachMenu by remember { mutableStateOf(false) }
    val canSend = uiState.isGenerating || uiState.promptText.isNotBlank() || uiState.selectedImages.isNotEmpty()
    fun addReferenceImages() {
        pickImagePaths(strings.paintAddReferenceImage)
            .take(uiState.selectedModel.maxImages - uiState.selectedImages.size)
            .forEach { onEvent(PaintEvent.AddImage(selectedImageFromPath(it))) }
    }
    val dropTarget = remember(uiState.selectedImages, uiState.selectedModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val paths = event.dragData().imagePathsFromDrop()
                paths.take(uiState.selectedModel.maxImages - uiState.selectedImages.size).forEach { path ->
                    onEvent(PaintEvent.AddImage(selectedImageFromPath(path)))
                }
                return paths.isNotEmpty()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
                target = dropTarget,
            )
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uiState.selectedImages.isNotEmpty()) {
            Row(
                modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.selectedImages.forEach { selected ->
                    Box {
                        Surface(
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                onPreviewImage(selected.uri)
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            FileImage(selected.uri, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Surface(
                            onClick = { onEvent(PaintEvent.RemoveImage(selected.id)) },
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.64f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
        FlowRow(
            modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DesktopPaintChip(
                label = uiState.activeProfile?.name ?: strings.paintNoApi,
                highlighted = uiState.activeProfile == null,
                onClick = onShowApiSettings,
            )
            DesktopPaintChip(
                label = "${strings.paintSelectModel}: ${uiState.selectedModel.displayName}",
                onClick = {
                    onShowOptions(
                        PaintOptionDialog(
                            title = strings.paintSelectModel,
                            selectedLabel = uiState.selectedModel.displayName,
                            options = PaintModel.entries.map { option ->
                                PaintOption(option.displayName) { onEvent(PaintEvent.SelectModel(option)) }
                            },
                        )
                    )
                },
            )
            if (!uiState.selectedModel.isGpt) {
                DesktopPaintChip(
                    label = "${strings.paintSelectRatio}: ${uiState.selectedAspectRatio.displayName}",
                    onClick = {
                        onShowOptions(
                            PaintOptionDialog(
                                title = strings.paintSelectRatio,
                                selectedLabel = uiState.selectedAspectRatio.displayName,
                                options = AspectRatio.availableFor(uiState.selectedModel).map { option ->
                                    PaintOption(option.displayName) { onEvent(PaintEvent.SelectAspectRatio(option)) }
                                },
                            )
                        )
                    },
                )
            }
            if (uiState.selectedModel.supportsResolution) {
                DesktopPaintChip(
                    label = "${strings.paintSelectResolution}: ${uiState.selectedResolution.displayName}",
                    onClick = {
                        onShowOptions(
                            PaintOptionDialog(
                                title = strings.paintSelectResolution,
                                selectedLabel = uiState.selectedResolution.displayName,
                                options = Resolution.availableFor(uiState.selectedModel).map { option ->
                                    PaintOption(option.displayName) { onEvent(PaintEvent.SelectResolution(option)) }
                                },
                            )
                        )
                    },
                )
            }
            if (uiState.selectedModel.supportsGptSize) {
                DesktopPaintChip(
                    label = gptSizeLabel(uiState.selectedGptSize, strings),
                    onClick = {
                        onShowOptions(
                            PaintOptionDialog(
                                title = strings.paintSelectResolution,
                                selectedLabel = gptSizeLabel(uiState.selectedGptSize, strings),
                                options = GptImageSize.entries.map { option ->
                                    PaintOption(gptSizeLabel(option, strings)) { onEvent(PaintEvent.SelectGptSize(option)) }
                                },
                            )
                        )
                    },
                )
            }
            if (uiState.selectedModel.supportsGptQuality) {
                DesktopPaintChip(
                    label = gptQualityLabel(uiState.selectedGptQuality, strings),
                    onClick = {
                        onShowOptions(
                            PaintOptionDialog(
                                title = strings.paintSelectResolution,
                                selectedLabel = gptQualityLabel(uiState.selectedGptQuality, strings),
                                options = GptImageQuality.entries.map { option ->
                                    PaintOption(gptQualityLabel(option, strings)) { onEvent(PaintEvent.SelectGptQuality(option)) }
                                },
                            )
                        )
                    },
                )
            }
            if (uiState.selectedModel.isGpt) {
                DesktopPaintChip(
                    label = gptFormatLabel(uiState.selectedGptFormat, strings),
                    onClick = {
                        onShowOptions(
                            PaintOptionDialog(
                                title = strings.paintSelectResolution,
                                selectedLabel = gptFormatLabel(uiState.selectedGptFormat, strings),
                                options = GptOutputFormat.entries.map { option ->
                                    PaintOption(gptFormatLabel(option, strings)) { onEvent(PaintEvent.SelectGptFormat(option)) }
                                },
                            )
                        )
                    },
                )
            }
        }
        Row(
            modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp).padding(start = 18.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicTextField(
                        value = uiState.promptText,
                        onValueChange = { onEvent(PaintEvent.UpdatePrompt(it)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 170.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (uiState.promptText.isEmpty()) {
                                    Text(
                                        text = strings.paintPromptHint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box {
                            Surface(
                                onClick = { showAttachMenu = true },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = strings.paintAddReferenceImage,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (showAttachMenu) {
                                Popup(
                                    alignment = Alignment.TopStart,
                                    offset = IntOffset(0, -72),
                                    onDismissRequest = { showAttachMenu = false },
                                    properties = PopupProperties(focusable = true),
                                ) {
                                    Surface(
                                        modifier = Modifier.width(220.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 6.dp,
                                        shadowElevation = 12.dp,
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                            text = { Text(strings.paintAddReferenceImage) },
                                            onClick = {
                                                showAttachMenu = false
                                                addReferenceImages()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.promptText.isNotBlank()) {
                            TextButton(onClick = { onEvent(PaintEvent.UpdatePrompt("")) }) {
                                Text(strings.paintClear)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            onClick = {
                                if (uiState.isGenerating) {
                                    onEvent(PaintEvent.StopGeneration)
                                } else if (canSend) {
                                    onEvent(PaintEvent.SendMessage)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = when {
                                uiState.isGenerating -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (uiState.isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                    contentDescription = if (uiState.isGenerating) strings.paintStop else strings.paintSend,
                                    modifier = Modifier.size(if (uiState.isGenerating) 19.dp else 22.dp),
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopPaintChip(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ApiSettingsDialog(
    uiState: PaintUiState,
    onDismiss: () -> Unit,
    onEvent: (PaintEvent) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://yunwu.ai") }
    var token by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.BEARER) }

    fun edit(profile: ApiProfile) {
        editingProfileId = profile.id
        name = profile.name
        baseUrl = profile.baseUrl
        token = profile.token
        authMode = profile.authMode
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(760.dp).heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.paintApiSettings,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(strings.close)
                    }
                }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    modifier = Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.apiProfiles.isEmpty()) {
                        Text(strings.paintNoConfig, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    uiState.apiProfiles.forEach { profile ->
                        Surface(
                            onClick = {
                                onEvent(PaintEvent.SetActiveProfile(profile.id))
                                edit(profile)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.activeProfile?.id == profile.id) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = profile.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { edit(profile) }) { Text(strings.paintUpdate) }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.paintConfigName) })
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(strings.paintApiBaseUrl) })
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(strings.paintAccessToken) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopPaintChip(
                            label = strings.paintAuthBearer,
                            highlighted = authMode == AuthMode.BEARER,
                            onClick = { authMode = AuthMode.BEARER },
                        )
                        DesktopPaintChip(
                            label = strings.paintAuthOfficial,
                            highlighted = authMode == AuthMode.OFFICIAL,
                            onClick = { authMode = AuthMode.OFFICIAL },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val profile = ApiProfile(
                                    id = editingProfileId ?: UUID.randomUUID().toString(),
                                    name = name.ifBlank { strings.paintConfigName },
                                    baseUrl = baseUrl.trim().ifBlank { "https://yunwu.ai" }.trimEnd('/'),
                                    token = token.trim(),
                                    authMode = authMode,
                                )
                                onEvent(PaintEvent.SaveApiProfile(profile))
                                editingProfileId = profile.id
                            },
                            enabled = token.isNotBlank(),
                        ) {
                            Text(if (editingProfileId == null) strings.paintAddConfig else strings.paintSave)
                        }
                        if (editingProfileId != null) {
                            TextButton(
                                onClick = {
                                    editingProfileId?.let { onEvent(PaintEvent.DeleteApiProfile(it)) }
                                    editingProfileId = null
                                    name = ""
                                    token = ""
                                },
                            ) {
                                Text(strings.paintDeleteConfig, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private data class PaintOptionDialog(
    val title: String,
    val selectedLabel: String? = null,
    val options: List<PaintOption>,
)

private data class PaintOption(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun PaintOptionDialogView(
    dialog: PaintOptionDialog,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            LazyColumn(modifier = Modifier.padding(vertical = 8.dp).heightIn(max = 460.dp)) {
                items(dialog.options) { option ->
                    val selected = option.label == dialog.selectedLabel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                option.onClick()
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameSessionDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.paintRenameSessionTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(strings.paintSessionNameLabel) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(strings.paintSave)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalDesktopStrings.current.cancel) } },
    )
}

@Composable
private fun PaintImagePreviewDialog(
    path: String,
    onDismiss: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        title = LocalDesktopStrings.current.paintImagePreview,
        state = rememberDialogState(width = 980.dp, height = 720.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                FileImage(path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun GeneratingBlock() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = LocalDesktopStrings.current.paintGenerating,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun FileImage(
    path: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadImageBitmap(path, 1400) }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Text(LocalDesktopStrings.current.missingFile, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<PaintMessage>.visibleWithActiveVersions(activeVersions: Map<String, Int>): List<PaintMessage> {
    val versionGroups = filter { it.versionGroup != null }.groupBy { it.versionGroup!! }
    return filter { message ->
        val group = message.versionGroup ?: return@filter true
        val versions = versionGroups[group].orEmpty().sortedBy { it.versionIndex }
        val activePosition = (activeVersions[group] ?: versions.lastIndex).coerceIn(0, versions.lastIndex)
        versions.getOrNull(activePosition)?.id == message.id
    }
}

private fun selectedImageFromPath(path: String): SelectedImage {
    val (width, height) = imageDimensions(path)
    return SelectedImage(
        id = UUID.randomUUID().toString(),
        uri = path,
        mimeType = mimeTypeFromPath(path),
        width = width,
        height = height,
    )
}

private fun pickImagePaths(title: String): List<String> {
    return DesktopImageFilePicker.pickImagePaths(title, ::isSupportedImageName)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragData.imagePathsFromDrop(): List<String> {
    if (this !is DragData.FilesList) return emptyList()
    return readFiles().mapNotNull { uriString ->
        runCatching {
            val uri = URI(uriString)
            val file = if (uri.scheme.equals("file", ignoreCase = true)) File(uri) else File(uriString)
            file
        }.getOrNull()
    }.flatMap { file -> collectImageFiles(file).map { it.absolutePath } }
}

private fun collectImageFiles(file: File): Sequence<File> {
    return when {
        file.isFile && isSupportedImageName(file.name) -> sequenceOf(file)
        file.isDirectory -> file.walkTopDown().filter { it.isFile && isSupportedImageName(it.name) }
        else -> emptySequence()
    }
}

private fun isSupportedImageName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".bmp")
}

private fun loadImageBitmap(path: String, maxDimension: Int): ImageBitmap? {
    return runCatching {
        val source = ImageIO.read(File(path.removePrefix("file://"))) ?: return@runCatching null
        source.scaledToMaxDimension(maxDimension).toComposeImageBitmap()
    }.getOrNull()
}

private fun BufferedImage.scaledToMaxDimension(maxDimension: Int): BufferedImage {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxDimension) return this
    val scale = maxDimension.toDouble() / largestSide.toDouble()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = target.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return target
}

private fun imageDimensions(path: String): Pair<Int, Int> {
    return runCatching {
        val image = ImageIO.read(File(path.removePrefix("file://"))) ?: return@runCatching 0 to 0
        image.width to image.height
    }.getOrDefault(0 to 0)
}

private fun mimeTypeFromPath(path: String): String {
    return when {
        path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/png"
    }
}

private fun gptSizeLabel(size: GptImageSize, strings: DesktopStrings): String =
    if (size == GptImageSize.AUTO) strings.paintGptSizeAuto else size.displayName

private fun gptQualityLabel(quality: GptImageQuality, strings: DesktopStrings): String {
    return when (quality) {
        GptImageQuality.AUTO -> strings.paintGptQualityAuto
        GptImageQuality.LOW -> strings.paintGptQualityLow
        GptImageQuality.MEDIUM -> strings.paintGptQualityMedium
        GptImageQuality.HIGH -> strings.paintGptQualityHigh
    }
}

private fun gptFormatLabel(format: GptOutputFormat, strings: DesktopStrings): String {
    return when (format) {
        GptOutputFormat.PNG -> strings.paintGptFormatPng
        GptOutputFormat.JPEG -> strings.paintGptFormatJpeg
        GptOutputFormat.WEBP -> strings.paintGptFormatWebp
    }
}

private fun String.localizedPaintText(strings: DesktopStrings): String {
    return when (this) {
        DesktopPaintErrorText.MISSING_API -> strings.paintMissingApi
        DesktopPaintErrorText.GENERATION_FAILED -> strings.paintGenerationFailed
        else -> this
    }
}

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private fun saveImageAs(sourcePath: String, title: String) {
    val source = File(sourcePath)
    if (!source.isFile) return
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = source.name
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return
    val file = dialog.file ?: return
    source.copyTo(File(directory, file), overwrite = true)
}
