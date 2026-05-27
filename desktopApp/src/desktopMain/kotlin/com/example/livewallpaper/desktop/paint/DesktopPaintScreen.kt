@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.livewallpaper.desktop.paint

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import com.example.livewallpaper.core.platform.DesktopAiPaintStoragePaths
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Desktop
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.Image as AwtImage
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    val currentSessionId = uiState.currentSession?.id
    val listState = remember(currentSessionId) { LazyListState() }
    var showApiSettings by remember { mutableStateOf(false) }
    var optionDialog by remember { mutableStateOf<PaintOptionDialog?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var lastRenderedSessionId by remember { mutableStateOf<String?>(null) }
    var lastAutoScrollKey by remember { mutableStateOf<String?>(null) }
    var showCopyFeedback by remember { mutableStateOf(false) }
    var copyFeedbackSerial by remember { mutableStateOf(0) }

    fun copyWithFeedback(text: String) {
        copyToClipboard(text)
        showCopyFeedback = true
        copyFeedbackSerial += 1
    }

    fun copyImageWithFeedback(path: String) {
        if (copyImageToClipboard(path)) {
            showCopyFeedback = true
            copyFeedbackSerial += 1
        }
    }

    LaunchedEffect(listState) {
        viewModel.scrollToBottomEvent.collect { shouldAnimate ->
            delay(50)
            if (listState.layoutInfo.totalItemsCount > 0) {
                if (shouldAnimate) {
                    listState.animateScrollToItem(0)
                } else {
                    listState.scrollToItem(0)
                }
            }
        }
    }
    LaunchedEffect(copyFeedbackSerial) {
        if (copyFeedbackSerial > 0) {
            delay(1400)
            showCopyFeedback = false
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaintTopBar(
                uiState = uiState,
                isSidebarCollapsed = isSidebarCollapsed,
                onToggleSidebar = onToggleSidebar,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val messages = remember(uiState.messages, uiState.activeVersions) {
                    uiState.messages.visibleWithActiveVersions(uiState.activeVersions)
                }
                val latestMessage = messages.firstOrNull()
                LaunchedEffect(
                    currentSessionId,
                    latestMessage?.id,
                    latestMessage?.updatedAt,
                    latestMessage?.status,
                    latestMessage?.images?.size,
                ) {
                    val sessionId = currentSessionId ?: return@LaunchedEffect
                    val message = latestMessage ?: return@LaunchedEffect
                    val autoScrollKey = listOf(
                        sessionId,
                        message.id,
                        message.updatedAt,
                        message.status.name,
                        message.images.size,
                    ).joinToString(":")
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= messages.size }
                    if (lastRenderedSessionId != sessionId) {
                        listState.scrollToItem(0)
                    } else if (lastAutoScrollKey != autoScrollKey) {
                        listState.animateScrollToItem(0)
                    }
                    lastRenderedSessionId = sessionId
                    lastAutoScrollKey = autoScrollKey
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
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex <= 1
                        }.collect { isAtBottom ->
                            viewModel.onEvent(PaintEvent.UpdateScrollState(isAtBottom))
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
                        reverseLayout = true,
                    ) {
                        items(messages, key = { it.id }) { message ->
                            PaintMessageRow(
                                message = message,
                                uiState = uiState,
                                onPreviewImage = { previewImagePath = it },
                                onAddToWallpapers = { path -> onAddImagesToWallpapers(listOf(path)) },
                                onAddImages = { images ->
                                    val remaining = uiState.selectedModel.maxImages - uiState.selectedImages.size
                                    images
                                        .mapNotNull(::selectedImageFromPaintImage)
                                        .take(remaining.coerceAtLeast(0))
                                        .forEach { viewModel.onEvent(PaintEvent.AddImage(it)) }
                                },
                                onSetWallpaper = onSetWallpaperPath,
                                onRegenerate = { viewModel.onEvent(PaintEvent.RegenerateMessage(message.id)) },
                                onDelete = { viewModel.onEvent(PaintEvent.DeleteMessage(message.id)) },
                                onDeleteVersionGroup = { group -> viewModel.onEvent(PaintEvent.DeleteMessageVersion(group)) },
                                onEdit = { viewModel.onEvent(PaintEvent.EditUserMessage(message.id)) },
                                onSwitchVersion = { group, index ->
                                    viewModel.onEvent(PaintEvent.SwitchMessageVersion(group, index))
                                },
                                onCopyText = ::copyWithFeedback,
                                onCopyImage = ::copyImageWithFeedback,
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !uiState.isAtBottom,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it },
                        ) {
                            ScrollToLatestButton(
                                newMessageCount = uiState.newMessageCount,
                                onClick = { viewModel.onEvent(PaintEvent.ScrollToBottom) },
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
        if (showCopyFeedback) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
            ) {
                Text(
                    text = strings.paintCopied,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
private fun ScrollToLatestButton(
    newMessageCount: Int,
    onClick: () -> Unit,
) {
    val active = newMessageCount > 0
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = LocalDesktopStrings.current.paintScrollToLatest,
                modifier = Modifier.size(20.dp),
                tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            if (active) {
                Text(
                    text = newMessageCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ColumnScope.DesktopPaintSidebarSection(
    uiState: PaintUiState,
    onEvent: (PaintEvent) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var addHovered by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.paintSessions,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (addHovered) 0.12f else 0f,
                        ),
                    )
                    .onPointerEvent(PointerEventType.Enter) { addHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { addHovered = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onEvent(PaintEvent.CreateSession()) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = strings.paintNewSession,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (uiState.sessions.isEmpty()) {
            Text(
                text = strings.paintNoSessions,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
            .padding(start = 14.dp, end = 4.dp),
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
            SessionMenuButton(
                onClick = { menuExpanded = true },
                visible = hovered || menuExpanded,
                active = menuExpanded,
            )
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
@OptIn(ExperimentalComposeUiApi::class)
private fun SessionMenuButton(
    visible: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 26.dp)
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (hovered || active) 0.75f else 0f,
                ),
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                enabled = visible,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = null,
            modifier = Modifier.size(18.dp).alpha(if (visible) 1f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaintTopBar(
    uiState: PaintUiState,
    isSidebarCollapsed: Boolean,
    onToggleSidebar: () -> Unit,
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
        Box(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.currentSession?.title ?: strings.aiPaintTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    onAddImages: (List<PaintImage>) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onDeleteVersionGroup: (String) -> Unit,
    onEdit: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyImage: (String) -> Unit,
) {
    val isUser = message.senderIdentity == SenderIdentity.USER
    val strings = LocalDesktopStrings.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteOptions by remember { mutableStateOf(false) }
    val versionGroup = message.versionGroup
    val versions = remember(versionGroup, uiState.messages) {
        if (versionGroup != null && !isUser) {
            uiState.messages.filter { it.versionGroup == versionGroup }.sortedBy { it.versionIndex }
        } else {
            emptyList()
        }
    }
    val currentVersionIndex = if (versions.isNotEmpty() && versionGroup != null) {
        (uiState.activeVersions[versionGroup] ?: (versions.size - 1)).coerceIn(0, versions.lastIndex)
    } else {
        0
    }
    val durationMillis = messageDurationMillis(message)
    val durationText = formatDuration(durationMillis)
    val timeText = remember(message.createdAt) { formatMessageTime(message.createdAt) }
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = strings.paintDeleteMessageTitle,
            message = strings.paintDeleteMessageConfirm,
            confirmText = strings.paintDeleteMessage,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }
    if (showDeleteOptions) {
        DeleteVersionDialog(
            onDismiss = { showDeleteOptions = false },
            onDeleteCurrent = {
                showDeleteOptions = false
                onDelete()
            },
            onDeleteAll = {
                showDeleteOptions = false
                versionGroup?.let(onDeleteVersionGroup)
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = timeText,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        )
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 520.dp else 760.dp),
            shape = bubbleShape,
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
            },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message.messageContent.isNotBlank()) {
                    val displayText = message.messageContent.localizedPaintText(strings)
                    var selectableText by remember(message.id, displayText) {
                        mutableStateOf(TextFieldValue(displayText))
                    }
                    BasicTextField(
                        value = selectableText,
                        onValueChange = { selectableText = it },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (message.status == MessageStatus.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                        cursorBrush = SolidColor(Color.Transparent),
                    )
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
                                onCopyText = onCopyText,
                                onCopyImage = onCopyImage,
                            )
                        }
                    }
                }
                if (!isUser && message.status == MessageStatus.GENERATING) {
                    GeneratingBlock(
                        aspectRatio = message.generationAspectRatio ?: uiState.selectedAspectRatio,
                        text = strings.paintGeneratingTime(durationText),
                    )
                }
                if (!isUser && message.status != MessageStatus.GENERATING) {
                    StatusText(
                        text = strings.paintStatusTime(message.status.localizedStatus(strings), durationText),
                        color = if (message.status == MessageStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        },
                    )
                }
                if (!isUser && message.generationModel != null) {
                    GenerationParamsBadge(message)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (isUser) {
            UserMessageActions(
                message = message,
                onCopy = { onCopyText(message.messageContent) },
                onEdit = onEdit,
                onDelete = { showDeleteConfirm = true },
            )
        } else {
            AssistantMessageActions(
                message = message,
                versions = versions,
                currentVersionIndex = currentVersionIndex,
                imagesAvailable = message.images.any { it.localPath?.let { path -> localImageFile(path)?.isFile } == true || it.base64Data != null },
                onAddImages = { onAddImages(message.images) },
                onCopy = { onCopyText(message.messageContent) },
                onRegenerate = onRegenerate,
                onDownload = { message.images.firstOrNull()?.localPath?.let { saveImageAs(it, strings.paintSaveAs) } },
                onDelete = {
                    if (versions.size > 1) showDeleteOptions = true else showDeleteConfirm = true
                },
                onSwitchVersion = onSwitchVersion,
            )
        }
    }
}

@Composable
private fun AssistantMessageActions(
    message: PaintMessage,
    versions: List<PaintMessage>,
    currentVersionIndex: Int,
    imagesAvailable: Boolean,
    onAddImages: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val versionGroup = message.versionGroup
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (versions.size > 1) {
            VersionSwitcher(
                current = currentVersionIndex + 1,
                total = versions.size,
                onPrevious = { versionGroup?.let { onSwitchVersion(it, currentVersionIndex - 1) } },
                onNext = { versionGroup?.let { onSwitchVersion(it, currentVersionIndex + 1) } },
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (imagesAvailable) {
            MessageIconButton(Icons.Default.Add, strings.paintAddReferenceImage, onClick = onAddImages)
        }
        if (message.messageContent.isNotBlank()) {
            MessageIconButton(Icons.Default.ContentCopy, strings.paintCopyMessage, onClick = onCopy)
        }
        MessageIconButton(
            icon = Icons.Default.Refresh,
            label = strings.paintRegenerate,
            onClick = onRegenerate,
        )
        if (imagesAvailable) {
            MessageIconButton(Icons.Default.Download, strings.paintSaveAs, onClick = onDownload)
        }
        MessageIconButton(
            icon = Icons.Default.Delete,
            label = strings.paintDeleteMessage,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.78f),
            onClick = onDelete,
        )
    }
}

@Composable
private fun UserMessageActions(
    message: PaintMessage,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.messageContent.isNotBlank()) {
            MessageIconButton(Icons.Default.ContentCopy, strings.paintCopyMessage, onClick = onCopy)
        }
        MessageIconButton(Icons.Default.Edit, strings.paintEditMessage, onClick = onEdit)
        MessageIconButton(
            icon = Icons.Default.Delete,
            label = strings.paintDeleteMessage,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.78f),
            onClick = onDelete,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MessageIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (hovered && enabled) 0.50f else 0f,
                ),
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun VersionSwitcher(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MessageVersionButton(
            icon = Icons.Default.ChevronLeft,
            enabled = current > 1,
            onClick = onPrevious,
        )
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
        )
        MessageVersionButton(
            icon = Icons.Default.ChevronRight,
            enabled = current < total,
            onClick = onNext,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MessageVersionButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (hovered && enabled) 0.50f else 0f,
                ),
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
            },
        )
    }
}

@Composable
private fun GenerationParamsBadge(message: PaintMessage) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.generationModel?.let { GenerationParamChip(it.displayName) }
        if (message.generationModel?.isGpt == true) {
            message.generationGptSize?.let { GenerationParamChip(gptSizeLabel(it, LocalDesktopStrings.current)) }
            message.generationGptQuality?.let { GenerationParamChip(gptQualityLabel(it, LocalDesktopStrings.current)) }
        } else {
            message.generationAspectRatio?.let { GenerationParamChip(it.displayName) }
            message.generationResolution?.let { GenerationParamChip(it.displayName) }
        }
    }
}

@Composable
private fun GenerationParamChip(text: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeleteVersionDialog(
    onDismiss: () -> Unit,
    onDeleteCurrent: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.paintDeleteMessageTitle) },
        text = { Text(strings.paintDeleteVersionHint) },
        confirmButton = {
            TextButton(onClick = onDeleteAll) {
                Text(strings.paintDeleteAllVersions, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeleteCurrent) {
                Text(strings.paintDeleteCurrentVersion)
            }
        },
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintImageThumb(
    image: PaintImage,
    onPreviewImage: (String) -> Unit,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyImage: (String) -> Unit,
) {
    val path = image.localPath
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f)
                .clip(RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ) {
            if (path != null) {
                FileImage(path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (path != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .dragAndDropSource {
                        dragTransferDataForFile(path)
                    }
                    .onPointerEvent(PointerEventType.Press) { event ->
                        if (event.button == PointerButton.Secondary) {
                            menuExpanded = true
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPreviewImage(path) },
                    ),
            )
        }
        if (path != null) {
            ImageActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                path = path,
                onAddToWallpapers = onAddToWallpapers,
                onSetWallpaper = onSetWallpaper,
                onCopyText = onCopyText,
                onCopyImage = onCopyImage,
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
    onCopyText: (String) -> Unit,
    onCopyImage: (String) -> Unit,
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
            text = { Text(strings.paintCopyImage) },
            onClick = {
                onDismiss()
                onCopyImage(path)
            },
        )
        DropdownMenuItem(
            text = { Text(strings.paintCopyPath) },
            onClick = {
                onDismiss()
                onCopyText(path)
            },
        )
        DropdownMenuItem(
            text = { Text(strings.paintOpenImageLocation) },
            onClick = {
                onDismiss()
                openImageLocation(path)
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
    val scope = rememberCoroutineScope()
    val hasDraft = uiState.promptText.isNotBlank() || uiState.selectedImages.isNotEmpty()
    val canSubmit = hasDraft || uiState.isGenerating
    val referenceListState = rememberLazyListState()
    val referenceReorderState = rememberReorderableLazyListState(referenceListState) { from, to ->
        val reordered = uiState.selectedImages.toMutableList()
        val moved = reordered.removeAt(from.index)
        reordered.add(to.index, moved)
        onEvent(PaintEvent.ReorderImages(reordered))
    }
    fun addClipboardImages(): Boolean {
        val remaining = uiState.selectedModel.maxImages - uiState.selectedImages.size
        if (remaining <= 0) return false
        val images = clipboardSelectedImages(remaining)
        images.forEach { onEvent(PaintEvent.AddImage(it)) }
        return images.isNotEmpty()
    }
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
            LazyRow(
                state = referenceListState,
                modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.selectedImages, key = { it.id }) { selected ->
                    ReorderableItem(referenceReorderState, key = selected.id) { _ ->
                        Box(modifier = Modifier.longPressDraggableHandle()) {
                            Surface(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onPreviewImage(selected.uri) },
                                    ),
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
                                    PaintOption(
                                        label = option.displayName,
                                        onClick = { onEvent(PaintEvent.SelectAspectRatio(option)) },
                                        leadingContent = { DesktopRatioIcon(option) },
                                    )
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
                                    PaintOption(
                                        label = gptSizeLabel(option, strings),
                                        leadingContent = if (option == GptImageSize.AUTO) {
                                            null
                                        } else {
                                            { DesktopGptSizeIcon(option) }
                                        },
                                    ) {
                                        onEvent(PaintEvent.SelectGptSize(option))
                                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp, max = 170.dp)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.isCtrlPressed &&
                                    event.key == Key.V
                                ) {
                                    addClipboardImages()
                                } else {
                                    false
                                }
                            },
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
                        modifier = Modifier.fillMaxWidth().height(44.dp),
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
                                                scope.launch {
                                                    delay(120)
                                                    addReferenceImages()
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.promptText.isNotBlank()) {
                            Surface(
                                onClick = { onEvent(PaintEvent.UpdatePrompt("")) },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ) {
                                Text(
                                    text = strings.paintClear,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            onClick = {
                                if (hasDraft) {
                                    onEvent(PaintEvent.SendMessage)
                                } else if (uiState.isGenerating) {
                                    onEvent(PaintEvent.StopGeneration)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = when {
                                hasDraft -> MaterialTheme.colorScheme.primary
                                uiState.isGenerating -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (!hasDraft && uiState.isGenerating) {
                                        Icons.Default.Stop
                                    } else {
                                        Icons.Default.ArrowUpward
                                    },
                                    contentDescription = if (!hasDraft && uiState.isGenerating) {
                                        strings.paintStop
                                    } else {
                                        strings.paintSend
                                    },
                                    modifier = Modifier.size(if (!hasDraft && uiState.isGenerating) 19.dp else 22.dp),
                                    tint = if (canSubmit) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
    val leadingContent: (@Composable () -> Unit)? = null,
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
                        option.leadingContent?.let { content ->
                            content()
                            Spacer(modifier = Modifier.width(14.dp))
                        }
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
private fun DesktopRatioIcon(
    aspectRatio: AspectRatio,
    modifier: Modifier = Modifier,
) {
    val parts = aspectRatio.value.split(":")
    val widthRatio = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
    val heightRatio = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
    DesktopRatioShape(widthRatio = widthRatio, heightRatio = heightRatio, modifier = modifier)
}

@Composable
private fun DesktopGptSizeIcon(
    size: GptImageSize,
    modifier: Modifier = Modifier,
) {
    val parts = size.value.split("x")
    val widthRatio = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
    val heightRatio = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
    DesktopRatioShape(widthRatio = widthRatio, heightRatio = heightRatio, modifier = modifier)
}

@Composable
private fun DesktopRatioShape(
    widthRatio: Float,
    heightRatio: Float,
    modifier: Modifier = Modifier,
) {
    val ratio = (widthRatio / heightRatio).takeIf { it > 0f } ?: 1f
    val maxSize = 22.dp
    val width = if (ratio >= 1f) maxSize else maxSize * ratio
    val height = if (ratio >= 1f) maxSize / ratio else maxSize

    Box(
        modifier = modifier.size(maxSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
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
private fun GeneratingBlock(
    aspectRatio: AspectRatio,
    text: String,
) {
    val ratio = aspectRatio.value.split(":").let { parts ->
        val width = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
        val height = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
        (width / height).coerceIn(0.45f, 2.25f)
    }
    Surface(
        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(ratio),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun messageDurationMillis(message: PaintMessage): Long {
    return if (message.status == MessageStatus.GENERATING) {
        val nowMillis by produceState(initialValue = System.currentTimeMillis(), message.id) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000)
            }
        }
        (nowMillis - message.createdAt).coerceAtLeast(0L)
    } else {
        (message.updatedAt - message.createdAt).coerceAtLeast(0L)
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(timestamp))
}

private fun MessageStatus.localizedStatus(strings: DesktopStrings): String {
    return when (this) {
        MessageStatus.SUCCESS -> strings.paintStatusDone
        MessageStatus.ERROR -> strings.paintFailed
        MessageStatus.CANCELLED -> strings.paintCancelled
        MessageStatus.PENDING -> strings.paintStatusPending
        MessageStatus.GENERATING -> strings.paintGenerating
    }
}

@Composable
private fun FileImage(
    path: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val imageState by produceState<ImageLoadState>(initialValue = ImageLoadState.Loading, path) {
        value = ImageLoadState.Loading
        value = withContext(Dispatchers.IO) {
            loadImageBitmap(path, 1400)?.let(ImageLoadState::Success) ?: ImageLoadState.Error
        }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when (val state = imageState) {
            ImageLoadState.Loading -> Unit
            ImageLoadState.Error -> {
                Text(LocalDesktopStrings.current.missingFile, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ImageLoadState.Success -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

private sealed interface ImageLoadState {
    data object Loading : ImageLoadState
    data object Error : ImageLoadState
    data class Success(val bitmap: ImageBitmap) : ImageLoadState
}

private fun List<PaintMessage>.visibleWithActiveVersions(activeVersions: Map<String, Int>): List<PaintMessage> {
    val userMessageTimes = filter { it.senderIdentity == SenderIdentity.USER }.associate { it.id to it.createdAt }
    val versionGroups = filter { it.versionGroup != null }.groupBy { it.versionGroup!! }
    return filter { message ->
        val group = message.versionGroup ?: return@filter true
        val versions = versionGroups[group].orEmpty().sortedBy { it.versionIndex }
        val activePosition = (activeVersions[group] ?: versions.lastIndex).coerceIn(0, versions.lastIndex)
        versions.getOrNull(activePosition)?.id == message.id
    }.sortedBy { message ->
        if (message.senderIdentity == SenderIdentity.ASSISTANT && message.parentUserMessageId != null) {
            userMessageTimes[message.parentUserMessageId] ?: message.createdAt
        } else {
            message.createdAt
        }
    }.asReversed()
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

private fun clipboardSelectedImages(maxCount: Int): List<SelectedImage> {
    if (maxCount <= 0) return emptyList()
    return runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        when {
            clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) -> {
                @Suppress("UNCHECKED_CAST")
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>
                files.orEmpty()
                    .asSequence()
                    .flatMap { collectImageFiles(it) }
                    .take(maxCount)
                    .map { selectedImageFromPath(it.absolutePath) }
                    .toList()
            }
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? AwtImage ?: return@runCatching emptyList()
                val file = saveClipboardImage(image) ?: return@runCatching emptyList()
                listOf(selectedImageFromPath(file.absolutePath))
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private fun saveClipboardImage(image: AwtImage): File? {
    val width = image.getWidth(null)
    val height = image.getHeight(null)
    if (width <= 0 || height <= 0) return null
    val buffered = if (image is BufferedImage) {
        image
    } else {
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics: Graphics2D = target.createGraphics()
            try {
                graphics.drawImage(image, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        }
    }
    val directory = DesktopAiPaintStoragePaths.clipboardCacheDirectory()
    val file = File(directory, "clipboard-${UUID.randomUUID()}.png")
    return if (ImageIO.write(buffered, "png", file)) file else null
}

private fun selectedImageFromPaintImage(image: PaintImage): SelectedImage? {
    val file = image.localPath
        ?.let(::localImageFile)
        ?.takeIf { it.isFile }
        ?: return null
    val (width, height) = if (image.width > 0 && image.height > 0) {
        image.width to image.height
    } else {
        imageDimensions(file.absolutePath)
    }
    return SelectedImage(
        id = UUID.randomUUID().toString(),
        uri = file.absolutePath,
        mimeType = image.mimeType,
        width = width,
        height = height,
    )
}

private fun localImageFile(path: String): File? {
    return runCatching {
        if (path.startsWith("file:", ignoreCase = true)) {
            File(URI(path))
        } else {
            File(path.removePrefix("file://"))
        }
    }.getOrNull()
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

private fun copyImageToClipboard(path: String): Boolean {
    val file = localImageFile(path)?.takeIf { it.isFile } ?: return false
    val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return false
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image, file), null)
        true
    }.getOrDefault(false)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun dragTransferDataForFile(path: String): DragAndDropTransferData? {
    val file = localImageFile(path)?.takeIf { it.isFile } ?: return null
    return DragAndDropTransferData(
        transferable = DragAndDropTransferable(FileTransferable(listOf(file))),
        supportedActions = listOf(DragAndDropTransferAction.Copy),
    )
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

private fun openImageLocation(path: String) {
    val file = localImageFile(path)?.takeIf { it.exists() } ?: return
    runCatching {
        if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
            ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
        } else {
            Desktop.getDesktop().open(file.parentFile)
        }
    }
}

private class FileTransferable(
    private val files: List<File>,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw IOException("Unsupported data flavor: $flavor")
        return files
    }
}

private class ImageTransferable(
    private val image: BufferedImage,
    private val file: File,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.imageFlavor || flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.imageFlavor -> image
            DataFlavor.javaFileListFlavor -> listOf(file)
            else -> throw IOException("Unsupported data flavor: $flavor")
        }
    }
}
