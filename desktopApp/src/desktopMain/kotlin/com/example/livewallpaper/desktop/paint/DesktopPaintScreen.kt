@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.livewallpaper.desktop.paint

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.example.livewallpaper.core.design.icon.AppIcons
import com.example.livewallpaper.desktop.DesktopFilePicker
import com.example.livewallpaper.desktop.DesktopStrings
import com.example.livewallpaper.desktop.LocalDesktopStrings
import com.example.livewallpaper.core.platform.DesktopAiPaintStoragePaths
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileImportError
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileImportResult
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.AuthMode
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageQuality
import com.example.livewallpaper.feature.aipaint.domain.model.GptImageSize
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.PaintImage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintGenerationTaskUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.Image as AwtImage
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

private data class DesktopImagePreviewState(
    val paths: List<String>,
    val initialIndex: Int,
    val requestId: Int,
    val source: DesktopImagePreviewSource,
)

private enum class DesktopImagePreviewSource {
    Conversation,
    Reference,
}

private data class DesktopImageTransformState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

private const val DESKTOP_PREVIEW_MIN_SCALE = 0.5f

private const val DESKTOP_PREVIEW_MAX_SCALE = 5f

private const val DESKTOP_PREVIEW_DOUBLE_TAP_SCALE = 2.5f

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
    var previewState by remember { mutableStateOf<DesktopImagePreviewState?>(null) }
    var previewRequestId by remember { mutableStateOf(0) }
    var editImagePath by remember { mutableStateOf<String?>(null) }
    var compareSelectedPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var showComparePreview by remember { mutableStateOf(false) }
    var comparePreviewRequestId by remember { mutableStateOf(0) }
    var lastRenderedSessionId by remember { mutableStateOf<String?>(null) }
    var lastAutoScrollKey by remember { mutableStateOf<String?>(null) }
    var showCopyFeedback by remember { mutableStateOf(false) }
    var copyFeedbackSerial by remember { mutableStateOf(0) }
    var pendingJumpMessageId by remember { mutableStateOf<String?>(null) }

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

    fun showImagePreview(paths: List<String>, initialIndex: Int, source: DesktopImagePreviewSource) {
        val selectedPath = paths.getOrNull(initialIndex)
        val existingPaths = paths.filter { localImageFile(it)?.isFile == true }
        if (existingPaths.isEmpty()) return
        val safeInitialIndex = existingPaths.indexOf(selectedPath).takeIf { it >= 0 }
            ?: initialIndex.coerceIn(0, existingPaths.lastIndex)
        previewRequestId += 1
        previewState = DesktopImagePreviewState(
            paths = existingPaths,
            initialIndex = safeInitialIndex,
            requestId = previewRequestId,
            source = source,
        )
    }

    fun showConversationImagePreview(paths: List<String>, initialIndex: Int) {
        showImagePreview(paths, initialIndex, DesktopImagePreviewSource.Conversation)
    }

    fun showReferenceImagePreview(paths: List<String>, initialIndex: Int) {
        showImagePreview(paths, initialIndex, DesktopImagePreviewSource.Reference)
    }

    fun toggleComparePath(path: String) {
        val file = localImageFile(path)?.takeIf { it.isFile } ?: return
        val normalizedPath = file.absolutePath
        compareSelectedPaths = if (compareSelectedPaths.contains(normalizedPath)) {
            compareSelectedPaths - normalizedPath
        } else {
            compareSelectedPaths + normalizedPath
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
    LaunchedEffect(compareSelectedPaths.size) {
        if (compareSelectedPaths.size != 2) {
            showComparePreview = false
        }
    }

    if (showApiSettings) {
        ApiSettingsDialog(
            uiState = uiState,
            onDismiss = { showApiSettings = false },
            onEvent = viewModel::onEvent,
            onExportJson = viewModel::exportApiProfilesJson,
            onImportJson = viewModel::importApiProfilesJson,
        )
    }
    optionDialog?.let { dialog ->
        PaintOptionDialogView(
            dialog = dialog,
            onDismiss = { optionDialog = null },
        )
    }
    previewState?.let { state ->
        PaintImagePreviewDialog(
            paths = state.paths,
            initialIndex = state.initialIndex,
            requestId = state.requestId,
            onDismiss = { previewState = null },
            onEdit = { editImagePath = it },
        )
    }
    editImagePath?.let { path ->
        DesktopImageEditorWindow(
            path = path,
            onDismiss = { editImagePath = null },
            onSaved = { oldPath, newPath ->
                editImagePath = null
                if (previewState?.source == DesktopImagePreviewSource.Reference) {
                    viewModel.onEvent(PaintEvent.ReplaceImagePath(oldPath, newPath))
                }
                previewState = previewState?.let { state ->
                    val normalizedOldPath = normalizeImagePath(oldPath)
                    state.copy(
                        paths = state.paths.map { previewPath ->
                            if (normalizeImagePath(previewPath) == normalizedOldPath) newPath else previewPath
                        },
                    )
                }
            },
        )
    }
    if (showComparePreview && compareSelectedPaths.size == 2) {
        PaintImageCompareDialog(
            paths = compareSelectedPaths,
            requestId = comparePreviewRequestId,
            onDismiss = { showComparePreview = false },
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
                onEvent = viewModel::onEvent,
                onJumpToTask = { task ->
                    pendingJumpMessageId = task.messageId
                    viewModel.onEvent(PaintEvent.DismissGenerationTask(task.messageId))
                    if (currentSessionId != task.sessionId) {
                        viewModel.onEvent(PaintEvent.SelectSession(task.sessionId))
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val messages = remember(uiState.messages, uiState.activeVersions) {
                    uiState.messages.visibleWithActiveVersions(uiState.activeVersions)
                }
                LaunchedEffect(currentSessionId, uiState.messages, uiState.activeVersions, pendingJumpMessageId) {
                    val messageId = pendingJumpMessageId ?: return@LaunchedEffect
                    val targetMessage = uiState.messages.firstOrNull { it.id == messageId } ?: return@LaunchedEffect
                    targetMessage.versionGroup?.let { group ->
                        if (uiState.activeVersions[group] != targetMessage.versionIndex) {
                            viewModel.onEvent(PaintEvent.SwitchMessageVersion(group, targetMessage.versionIndex))
                            return@LaunchedEffect
                        }
                    }
                    val targetIndex = messages.indexOfFirst { it.id == messageId }
                    if (targetIndex >= 0) {
                        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= messages.size }
                        if (pendingJumpMessageId != messageId) return@LaunchedEffect
                        listState.scrollToItem(targetIndex)
                        val latest = messages.firstOrNull()
                        if (currentSessionId != null && latest != null) {
                            lastRenderedSessionId = currentSessionId
                            lastAutoScrollKey = listOf(
                                currentSessionId,
                                latest.id,
                                latest.updatedAt,
                                latest.status.name,
                                latest.images.size,
                            ).joinToString(":")
                        }
                        pendingJumpMessageId = null
                    }
                }
                val latestMessage = messages.firstOrNull()
                LaunchedEffect(
                    currentSessionId,
                    latestMessage?.id,
                    latestMessage?.updatedAt,
                    latestMessage?.status,
                    latestMessage?.images?.size,
                    pendingJumpMessageId,
                ) {
                    val sessionId = currentSessionId ?: return@LaunchedEffect
                    if (pendingJumpMessageId != null) return@LaunchedEffect
                    val message = latestMessage ?: return@LaunchedEffect
                    val autoScrollKey = listOf(
                        sessionId,
                        message.id,
                        message.updatedAt,
                        message.status.name,
                        message.images.size,
                    ).joinToString(":")
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= messages.size }
                    if (pendingJumpMessageId != null) return@LaunchedEffect
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
                    val conversationPreviewPaths = remember(messages) {
                        messages.asReversed().flatMap { message ->
                            message.images.mapNotNull { image ->
                                image.localPath
                            }
                        }
                    }
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex <= 1
                        }.collect { isAtBottom ->
                            viewModel.onEvent(PaintEvent.UpdateScrollState(isAtBottom))
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 1136.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
                        reverseLayout = true,
                    ) {
                        items(messages, key = { it.id }) { message ->
                            PaintMessageRow(
                                message = message,
                                uiState = uiState,
                                conversationPreviewPaths = conversationPreviewPaths,
                                compareSelectedPaths = compareSelectedPaths,
                                imageSelectionMode = compareSelectedPaths.isNotEmpty(),
                                onPreviewImage = ::showConversationImagePreview,
                                onToggleCompare = ::toggleComparePath,
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
                onPreviewImage = ::showReferenceImagePreview,
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
        if (compareSelectedPaths.isNotEmpty()) {
            CompareSelectionBar(
                selectedCount = compareSelectedPaths.size,
                onClear = {
                    compareSelectedPaths = emptyList()
                    showComparePreview = false
                },
                onCompare = {
                    if (compareSelectedPaths.size == 2) {
                        comparePreviewRequestId += 1
                        showComparePreview = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp),
            )
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
                imageVector = AppIcons.chevronDown,
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
private fun CompareSelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDesktopStrings.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = AppIcons.visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = strings.paintCompareSelectedCount(selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onClear) {
                Text(strings.paintClear)
            }
            Button(
                onClick = onCompare,
                enabled = selectedCount == 2,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(strings.paintImageCompare)
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
                    imageVector = AppIcons.add,
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
        PaintOriginBadge(session.originPlatform)
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
            imageVector = AppIcons.moreHorizontal,
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
    onEvent: (PaintEvent) -> Unit,
    onJumpToTask: (PaintGenerationTaskUiState) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var showTasks by remember { mutableStateOf(false) }
    val activeTaskCount = uiState.generationTasks.count { it.status == MessageStatus.GENERATING }
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
                    imageVector = if (isSidebarCollapsed) AppIcons.menu else AppIcons.chevronLeft,
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
        if (uiState.generationTasks.isNotEmpty()) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Surface(
                    onClick = { showTasks = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (activeTaskCount > 0) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = strings.paintGenerationTaskCount(uiState.generationTasks.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                DropdownMenu(
                    expanded = showTasks,
                    onDismissRequest = { showTasks = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        modifier = Modifier.widthIn(min = 360.dp).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = strings.paintGenerationTasks,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (uiState.generationTasks.any { it.status != MessageStatus.GENERATING }) {
                            TextButton(
                                onClick = {
                                    showTasks = false
                                    onEvent(PaintEvent.ClearGenerationTaskHistory)
                                },
                            ) {
                                Text(strings.paintTaskClearHistory)
                            }
                        }
                    }
                    uiState.generationTasks.forEach { task ->
                        GenerationTaskMenuItem(
                            task = task,
                            onJump = {
                                showTasks = false
                                onJumpToTask(task)
                            },
                            onCancel = {
                                showTasks = false
                                onEvent(PaintEvent.CancelGeneration(task.messageId))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationTaskMenuItem(
    task: PaintGenerationTaskUiState,
    onJump: () -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    Column(
        modifier = Modifier.widthIn(min = 280.dp, max = 360.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = task.sessionTitle.ifBlank { strings.paintNewSession },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(
                task.status.localizedStatus(strings),
                task.modelName,
                strings.paintTaskStartedAt(formatMessageTime(task.startedAt))
            )
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onJump) {
                Text(strings.paintTaskJump)
            }
            if (task.status == MessageStatus.GENERATING) {
                TextButton(onClick = onCancel) {
                    Text(strings.paintTaskCancel, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PaintMessageRow(
    message: PaintMessage,
    uiState: PaintUiState,
    conversationPreviewPaths: List<String>,
    compareSelectedPaths: List<String>,
    imageSelectionMode: Boolean,
    onPreviewImage: (List<String>, Int) -> Unit,
    onToggleCompare: (String) -> Unit,
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
                            val previewIndex = image.localPath
                                ?.takeIf { localImageFile(it)?.isFile == true }
                                ?.let(conversationPreviewPaths::indexOf)
                                ?: -1
                            PaintImageThumb(
                                image = image,
                                previewPaths = conversationPreviewPaths,
                                previewIndex = previewIndex,
                                compareSelected = image.localPath
                                    ?.let(::localImageFile)
                                    ?.absolutePath
                                    ?.let(compareSelectedPaths::contains) == true,
                                imageSelectionMode = imageSelectionMode,
                                onPreviewImage = onPreviewImage,
                                onToggleCompare = onToggleCompare,
                                onAddToWallpapers = onAddToWallpapers,
                                onSetWallpaper = onSetWallpaper,
                                onCopyText = onCopyText,
                                onCopyImage = onCopyImage,
                                onDelete = onDelete,
                            )
                        }
                    }
                }
                if (!isUser && message.status == MessageStatus.GENERATING) {
                    GeneratingBlock(
                        ratio = message.generationPreviewRatio(uiState.selectedAspectRatio),
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
            MessageIconButton(AppIcons.add, strings.paintAddReferenceImage, onClick = onAddImages)
        }
        if (message.messageContent.isNotBlank()) {
            MessageIconButton(AppIcons.copy, strings.paintCopyMessage, onClick = onCopy)
        }
        MessageIconButton(
            icon = AppIcons.refresh,
            label = strings.paintRegenerate,
            onClick = onRegenerate,
        )
        if (imagesAvailable) {
            MessageIconButton(AppIcons.download, strings.paintSaveAs, onClick = onDownload)
        }
        MessageIconButton(
            icon = AppIcons.delete,
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
            MessageIconButton(AppIcons.copy, strings.paintCopyMessage, onClick = onCopy)
        }
        MessageIconButton(AppIcons.edit, strings.paintEditMessage, onClick = onEdit)
        MessageIconButton(
            icon = AppIcons.delete,
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
            icon = AppIcons.chevronLeft,
            enabled = current > 1,
            onClick = onPrevious,
        )
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
        )
        MessageVersionButton(
            icon = AppIcons.chevronRight,
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

private fun PaintMessage.generationPreviewRatio(fallback: AspectRatio): Float {
    val gptRatio = if (generationModel?.isGpt == true) {
        generationGptSize?.toPreviewRatio()
    } else {
        null
    }
    return (gptRatio ?: (generationAspectRatio ?: fallback).toFloat()).coerceIn(0.45f, 2.25f)
}

private fun GptImageSize.toPreviewRatio(): Float? {
    if (this == GptImageSize.AUTO) return null
    val parts = value.split("x")
    val width = parts.getOrNull(0)?.toFloatOrNull() ?: return null
    val height = parts.getOrNull(1)?.toFloatOrNull() ?: return null
    if (width <= 0f || height <= 0f) return null
    return width / height
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
    previewPaths: List<String>,
    previewIndex: Int,
    compareSelected: Boolean,
    imageSelectionMode: Boolean,
    onPreviewImage: (List<String>, Int) -> Unit,
    onToggleCompare: (String) -> Unit,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyImage: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val path = image.localPath
    var menuExpanded by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    var imagePosition by remember { mutableStateOf(IntOffset.Zero) }
    Box {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f)
                .clip(RoundedCornerShape(10.dp))
                .onGloballyPositioned { coordinates ->
                    val windowPosition = coordinates.positionInWindow()
                    imagePosition = IntOffset(windowPosition.x.roundToInt(), windowPosition.y.roundToInt())
                },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = if (compareSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            if (path != null) {
                FileImage(path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (compareSelected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.check,
                        contentDescription = LocalDesktopStrings.current.selected,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
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
                            val pointerPosition = event.changes.firstOrNull()?.position
                            if (pointerPosition != null) {
                                menuPosition = IntOffset(
                                    x = imagePosition.x + pointerPosition.x.roundToInt(),
                                    y = imagePosition.y + pointerPosition.y.roundToInt(),
                                )
                            }
                            menuExpanded = true
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (imageSelectionMode || compareSelected) {
                                onToggleCompare(path)
                                return@clickable
                            }
                            if (previewIndex >= 0) {
                                onPreviewImage(previewPaths, previewIndex)
                            }
                        },
                    ),
            )
        }
        if (path != null) {
            ImageActionMenu(
                expanded = menuExpanded,
                position = menuPosition,
                onDismiss = { menuExpanded = false },
                path = path,
                compareSelected = compareSelected,
                onToggleCompare = onToggleCompare,
                onAddToWallpapers = onAddToWallpapers,
                onSetWallpaper = onSetWallpaper,
                onCopyText = onCopyText,
                onCopyImage = onCopyImage,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ImageActionMenu(
    expanded: Boolean,
    position: IntOffset,
    onDismiss: () -> Unit,
    path: String,
    compareSelected: Boolean,
    onToggleCompare: (String) -> Unit,
    onAddToWallpapers: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyImage: (String) -> Unit,
    onDelete: () -> Unit,
) {
    if (!expanded) return
    val strings = LocalDesktopStrings.current
    Popup(
        popupPositionProvider = ImageCursorPopupPositionProvider(position),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ImageActionMenuItem(strings.paintCopyImage) {
                    onDismiss()
                    onCopyImage(path)
                }
                ImageActionMenuItem(strings.paintCopyPath) {
                    onDismiss()
                    onCopyText(path)
                }
                ImageActionMenuItem(strings.paintOpenImageLocation) {
                    onDismiss()
                    openImageLocation(path)
                }
                ImageActionMenuItem(strings.paintSaveAs) {
                    onDismiss()
                    saveImageAs(path, strings.paintSaveAs)
                }
                ImageActionMenuItem(
                    if (compareSelected) strings.paintRemoveFromCompare else strings.paintAddToCompare
                ) {
                    onDismiss()
                    onToggleCompare(path)
                }
                ImageActionMenuItem(strings.paintAddToWallpaper) {
                    onDismiss()
                    onAddToWallpapers(path)
                }
                ImageActionMenuItem(strings.paintSetWallpaper) {
                    onDismiss()
                    onSetWallpaper(path)
                }
                ImageActionMenuItem(
                    text = strings.paintDeleteMessage,
                    destructive = true,
                ) {
                    onDismiss()
                    onDelete()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ImageActionMenuItem(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        hovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val background = when {
        destructive && hovered -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        hovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class ImageCursorPopupPositionProvider(
    private val cursorPosition: IntOffset,
    private val margin: Int = 8,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rightX = cursorPosition.x + margin
        val leftX = cursorPosition.x - popupContentSize.width - margin
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = if (rightX + popupContentSize.width + margin <= windowSize.width) {
            rightX
        } else {
            leftX
        }.coerceIn(margin, maxX)

        val belowY = cursorPosition.y
        val aboveY = cursorPosition.y - popupContentSize.height
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val y = if (belowY + popupContentSize.height + margin <= windowSize.height) {
            belowY
        } else {
            aboveY
        }.coerceIn(margin, maxY)

        return IntOffset(x, y)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintInputBar(
    uiState: PaintUiState,
    onEvent: (PaintEvent) -> Unit,
    onShowApiSettings: () -> Unit,
    onShowOptions: (PaintOptionDialog) -> Unit,
    onPreviewImage: (List<String>, Int) -> Unit,
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
    val referencePreviewPaths = remember(uiState.selectedImages) { uiState.selectedImages.map { it.uri } }
    var enterKeySends by remember { mutableStateOf(true) }
    var promptFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.promptText,
                selection = TextRange(uiState.promptText.length),
            ),
        )
    }
    LaunchedEffect(uiState.promptText) {
        if (uiState.promptText != promptFieldValue.text) {
            promptFieldValue = TextFieldValue(
                text = uiState.promptText,
                selection = TextRange(uiState.promptText.length),
            )
        }
    }
    fun updatePromptField(value: TextFieldValue) {
        promptFieldValue = value
        onEvent(PaintEvent.UpdatePrompt(value.text))
    }
    fun insertPromptNewline() {
        val text = promptFieldValue.text
        val start = minOf(promptFieldValue.selection.start, promptFieldValue.selection.end).coerceIn(0, text.length)
        val end = maxOf(promptFieldValue.selection.start, promptFieldValue.selection.end).coerceIn(0, text.length)
        val nextText = text.replaceRange(start, end, "\n")
        updatePromptField(TextFieldValue(text = nextText, selection = TextRange(start + 1)))
    }
    fun submitPromptShortcut(): Boolean {
        return when {
            hasDraft -> {
                onEvent(PaintEvent.SendMessage)
                true
            }
            uiState.isGenerating -> {
                onEvent(PaintEvent.StopGeneration)
                true
            }
            else -> false
        }
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
            .mapNotNull(::selectedImageFromCachedPath)
            .forEach { onEvent(PaintEvent.AddImage(it)) }
    }
    val dropTarget = remember(uiState.selectedImages, uiState.selectedModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val paths = event.dragData().imagePathsFromDrop()
                paths
                    .take(uiState.selectedModel.maxImages - uiState.selectedImages.size)
                    .mapNotNull(::selectedImageFromCachedPath)
                    .forEach { onEvent(PaintEvent.AddImage(it)) }
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
                itemsIndexed(uiState.selectedImages, key = { _, selected -> selected.id }) { index, selected ->
                    val imageRatio = remember(selected.width, selected.height) {
                        AspectRatio.findClosest(selected.width, selected.height)
                    }
                    ReorderableItem(referenceReorderState, key = selected.id) { _ ->
                        Column(
                            modifier = Modifier.width(62.dp).longPressDraggableHandle(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box {
                                Surface(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onPreviewImage(referencePreviewPaths, index) },
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
                                            imageVector = AppIcons.close,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                            if (imageRatio != null) {
                                val ratioSelected = imageRatio == uiState.selectedAspectRatio
                                Text(
                                    text = imageRatio.displayName,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onEvent(PaintEvent.SelectAspectRatio(imageRatio)) },
                                        )
                                        .padding(horizontal = 3.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ratioSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Spacer(modifier = Modifier.height(17.dp))
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
                        value = promptFieldValue,
                        onValueChange = ::updatePromptField,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp, max = 170.dp)
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                        val shouldSend = if (enterKeySends) !event.isCtrlPressed else event.isCtrlPressed
                                        if (shouldSend) {
                                            submitPromptShortcut()
                                            true
                                        } else {
                                            insertPromptNewline()
                                            true
                                        }
                                    }
                                    event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.V -> {
                                        addClipboardImages()
                                    }
                                    else -> false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (promptFieldValue.text.isEmpty()) {
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
                                        imageVector = AppIcons.add,
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
                                                    imageVector = AppIcons.image,
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
                                onClick = { updatePromptField(TextFieldValue("")) },
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
                        Surface(
                            onClick = { enterKeySends = !enterKeySends },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                        ) {
                            Text(
                                text = if (enterKeySends) {
                                    strings.paintInputModeEnterSend
                                } else {
                                    strings.paintInputModeCtrlEnterSend
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (uiState.promptText.isNotEmpty()) {
                            Text(
                                text = uiState.promptText.length.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                maxLines = 1,
                            )
                        }
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
                                        AppIcons.stop
                                    } else {
                                        AppIcons.arrowUpward
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
private fun DesktopAuthModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
            },
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onExportJson: () -> String,
    onImportJson: suspend (String) -> ApiProfileImportResult,
) {
    val strings = LocalDesktopStrings.current
    val scope = rememberCoroutineScope()
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://yunwu.ai") }
    var token by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.BEARER) }
    var showToken by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupMessageIsError by remember { mutableStateOf(false) }
    var showTransferActions by remember { mutableStateOf(false) }

    fun edit(profile: ApiProfile) {
        editingProfileId = profile.id
        name = profile.name
        baseUrl = profile.baseUrl
        token = profile.token
        authMode = profile.authMode
    }

    fun clearForm() {
        editingProfileId = null
        name = ""
        baseUrl = "https://yunwu.ai"
        token = ""
        authMode = AuthMode.BEARER
    }

    fun importBackup() {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                DesktopFilePicker.pickJsonPath(
                    title = strings.paintImportConfig,
                    filterDescription = strings.paintJsonFiles,
                )
            }
            if (path != null) {
                val content = withContext(Dispatchers.IO) {
                    runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()
                }
                val result = content?.let { onImportJson(it) }
                backupMessageIsError = result !is ApiProfileImportResult.Success
                backupMessage = importResultMessage(result, strings)
            }
        }
    }

    fun exportBackup() {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                DesktopFilePicker.pickSaveJsonPath(
                    title = strings.paintExportConfig,
                    defaultFileName = "live-wallpaper-paint-api-config.json",
                    filterDescription = strings.paintJsonFiles,
                )
            }
            if (path != null) {
                val exported = withContext(Dispatchers.IO) {
                    runCatching { File(path).writeText(onExportJson(), Charsets.UTF_8) }.isSuccess
                }
                backupMessageIsError = !exported
                backupMessage = if (exported) strings.paintExportConfigSuccess else strings.paintConfigFileError
            }
        }
    }

    LaunchedEffect(uiState.activeProfile?.id) {
        if (editingProfileId == null && name.isBlank() && token.isBlank()) {
            uiState.activeProfile?.let(::edit)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(680.dp).wrapContentHeight(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    IconButton(onClick = { showTransferActions = true }) {
                        Icon(
                            imageVector = AppIcons.settings,
                            contentDescription = strings.paintConfigImportExport,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.close)
                    }
                }

                backupMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupMessageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.width(210.dp).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strings.paintApiConfigList,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(onClick = ::clearForm) {
                                    Text(strings.paintAddConfig)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (uiState.apiProfiles.isEmpty()) {
                                    item {
                                        Text(
                                            text = strings.paintNoConfig,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                items(uiState.apiProfiles, key = { it.id }) { profile ->
                                    val active = uiState.activeProfile?.id == profile.id
                                    Surface(
                                        onClick = {
                                            onEvent(PaintEvent.SetActiveProfile(profile.id))
                                            edit(profile)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (active) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
                                            },
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = profile.name,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (active) {
                                                Icon(
                                                    imageVector = AppIcons.check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        ) {
                            Text(
                                text = strings.paintApiConfigDetail,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(strings.paintConfigName) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(strings.paintApiBaseUrl) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(strings.paintAccessToken) },
                                    singleLine = true,
                                    visualTransformation = if (showToken) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showToken = !showToken }) {
                                            Icon(
                                                imageVector = if (showToken) {
                                                    AppIcons.visibilityOff
                                                } else {
                                                    AppIcons.visibility
                                                },
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = strings.paintAuthMode,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        DesktopAuthModeChip(
                                            label = strings.paintAuthBearer,
                                            selected = authMode == AuthMode.BEARER,
                                            modifier = Modifier.weight(1f),
                                            onClick = { authMode = AuthMode.BEARER },
                                        )
                                        DesktopAuthModeChip(
                                            label = strings.paintAuthOfficial,
                                            selected = authMode == AuthMode.OFFICIAL,
                                            modifier = Modifier.weight(1f),
                                            onClick = { authMode = AuthMode.OFFICIAL },
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (editingProfileId != null) {
                                    TextButton(
                                        onClick = {
                                            editingProfileId?.let { onEvent(PaintEvent.DeleteApiProfile(it)) }
                                            clearForm()
                                        },
                                    ) {
                                        Text(strings.paintDeleteConfig, color = MaterialTheme.colorScheme.error)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
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
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTransferActions) {
        AlertDialog(
            onDismissRequest = { showTransferActions = false },
            title = { Text(strings.paintConfigImportExport) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.paintExportConfigWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            showTransferActions = false
                            importBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = AppIcons.fileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.paintImportConfig)
                    }
                    OutlinedButton(
                        onClick = {
                            showTransferActions = false
                            exportBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.apiProfiles.isNotEmpty(),
                    ) {
                        Icon(imageVector = AppIcons.download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.paintExportConfig)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTransferActions = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun PaintOriginBadge(platform: PaintClientPlatform) {
    val strings = LocalDesktopStrings.current
    val label = when (platform) {
        PaintClientPlatform.DESKTOP -> strings.paintOriginDesktop
        PaintClientPlatform.ANDROID -> strings.paintOriginAndroid
        PaintClientPlatform.IOS -> strings.paintOriginIos
        PaintClientPlatform.UNKNOWN -> strings.paintOriginUnknown
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun importResultMessage(result: ApiProfileImportResult?, strings: DesktopStrings): String = when (result) {
    is ApiProfileImportResult.Success -> strings.paintImportConfigSuccess(result.importedCount)
    is ApiProfileImportResult.Failure -> when (result.error) {
        ApiProfileImportError.FILE_TOO_LARGE -> strings.paintImportFileTooLarge
        ApiProfileImportError.INVALID_JSON -> strings.paintImportInvalidJson
        ApiProfileImportError.UNSUPPORTED_VERSION -> strings.paintImportUnsupportedVersion
        ApiProfileImportError.INVALID_PROFILE -> strings.paintImportInvalidProfile
        ApiProfileImportError.DUPLICATE_PROFILE_ID -> strings.paintImportDuplicateProfile
        ApiProfileImportError.INVALID_ACTIVE_PROFILE -> strings.paintImportInvalidActiveProfile
    }
    null -> strings.paintConfigFileError
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
                                imageVector = AppIcons.check,
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
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintImagePreviewDialog(
    paths: List<String>,
    initialIndex: Int,
    requestId: Int,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    if (paths.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember(paths) {
        mutableStateOf(initialIndex.coerceIn(0, paths.lastIndex))
    }
    LaunchedEffect(paths, initialIndex, requestId) {
        currentIndex = initialIndex.coerceIn(0, paths.lastIndex)
    }
    val path = paths[currentIndex]
    val file = remember(path) { localImageFile(path) ?: File(path.removePrefix("file://")) }
    var displayedPath by remember { mutableStateOf<String?>(null) }
    var imageState by remember { mutableStateOf<ImageLoadState>(ImageLoadState.Loading) }
    LaunchedEffect(path) {
        if (imageState !is ImageLoadState.Success) {
            displayedPath = null
            imageState = ImageLoadState.Loading
        }
        val loadedState = withContext(Dispatchers.IO) {
            loadImageBitmap(path, 4096)?.let(ImageLoadState::Success) ?: ImageLoadState.Error
        }
        displayedPath = path
        imageState = loadedState
    }
    var transformStates by remember(paths) {
        mutableStateOf(paths.associateWith { DesktopImageTransformState() })
    }
    val transform = transformStates[path] ?: DesktopImageTransformState()
    var showControls by remember { mutableStateOf(true) }
    var previewScale by remember(path) { mutableStateOf(1f) }
    var previewOffset by remember(path) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember(path) { mutableStateOf(IntSize.Zero) }

    fun updateTransform(transform: DesktopImageTransformState) {
        transformStates = transformStates.toMutableMap().apply {
            put(path, transform)
        }
    }

    fun applyPreviewTransform(scale: Float, offset: Offset = previewOffset) {
        val boundedScale = scale.coerceIn(DESKTOP_PREVIEW_MIN_SCALE, DESKTOP_PREVIEW_MAX_SCALE)
        previewScale = boundedScale
        previewOffset = if (boundedScale <= 1f) {
            Offset.Zero
        } else {
            offset.clampedForScale(previewViewportSize, boundedScale)
        }
    }

    fun navigate(delta: Int) {
        val nextIndex = (currentIndex + delta).coerceIn(0, paths.lastIndex)
        if (nextIndex != currentIndex) {
            currentIndex = nextIndex
        }
    }

    val initialWindowSize = remember(paths, initialIndex) {
        previewWindowSizeForPath(paths.getOrNull(initialIndex.coerceIn(0, paths.lastIndex)))
    }
    val previewWindowState = rememberWindowState(
        width = initialWindowSize.first.dp,
        height = initialWindowSize.second.dp,
    )

    Window(
        onCloseRequest = onDismiss,
        title = strings.paintImagePreview,
        icon = rememberPreviewAppIconPainter(),
        state = previewWindowState,
        resizable = true,
    ) {
        val previewWindow = window
        LaunchedEffect(requestId, previewWindow) {
            if (previewWindowState.isMinimized) {
                previewWindowState.isMinimized = false
                previewWindow.toFront()
                previewWindow.requestFocus()
            }
        }

        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f)),
            ) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = file.name.ifBlank { path },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = if (showControls) 8.dp else 20.dp)
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollDelta != 0f) {
                                val zoomFactor = if (scrollDelta < 0f) 1.12f else 0.90f
                                applyPreviewTransform(previewScale * zoomFactor)
                            }
                        }
                        .pointerInput(path, previewScale, previewViewportSize) {
                            detectDragGestures { change, dragAmount ->
                                if (previewScale > 1f) {
                                    change.consume()
                                    applyPreviewTransform(previewScale, previewOffset + dragAmount)
                                }
                            }
                        }
                        .pointerInput(path) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = { tapOffset ->
                                    if (previewScale > 1f) {
                                        applyPreviewTransform(1f, Offset.Zero)
                                    } else {
                                        val targetScale = DESKTOP_PREVIEW_DOUBLE_TAP_SCALE
                                        val centerX = previewViewportSize.width / 2f
                                        val centerY = previewViewportSize.height / 2f
                                        val targetOffset = Offset(
                                            x = (centerX - tapOffset.x) * (targetScale - 1f),
                                            y = (centerY - tapOffset.y) * (targetScale - 1f),
                                        )
                                        applyPreviewTransform(targetScale, targetOffset)
                                    }
                                },
                            )
                        }
                        .onSizeChanged { previewViewportSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    val isAwaitingCurrentImage = displayedPath != path
                    val visibleTransform = transformStates[displayedPath ?: path] ?: DesktopImageTransformState()
                    when (val state = imageState) {
                        ImageLoadState.Loading -> {
                            CircularProgressIndicator(color = Color.White)
                        }
                        ImageLoadState.Error -> {
                            Text(strings.missingFile, color = Color.White.copy(alpha = 0.72f))
                        }
                        is ImageLoadState.Success -> {
                            val compensation = previewRotationCompensation(
                                bitmap = state.bitmap,
                                viewportSize = previewViewportSize,
                                rotation = visibleTransform.rotation,
                            )
                            Image(
                                bitmap = state.bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val horizontalDirection = if (visibleTransform.flipHorizontal) -1f else 1f
                                        val verticalDirection = if (visibleTransform.flipVertical) -1f else 1f
                                        scaleX = previewScale * compensation * horizontalDirection
                                        scaleY = previewScale * compensation * verticalDirection
                                        translationX = previewOffset.x
                                        translationY = previewOffset.y
                                        rotationZ = visibleTransform.rotation
                                    },
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    if (isAwaitingCurrentImage) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.86f),
                            )
                        }
                    }

                    if (showControls && paths.size > 1) {
                        PreviewRoundIconButton(
                            icon = AppIcons.chevronLeft,
                            contentDescription = strings.previous,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                            enabled = currentIndex > 0,
                            onClick = { navigate(-1) },
                        )
                    }

                    if (showControls && paths.size > 1) {
                        PreviewRoundIconButton(
                            icon = AppIcons.chevronRight,
                            contentDescription = strings.next,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                            enabled = currentIndex < paths.lastIndex,
                            onClick = { navigate(1) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PreviewRoundIconButton(
                                icon = AppIcons.rotateCounterClockwise,
                                contentDescription = strings.imagePreviewRotateLeft,
                                onClick = { updateTransform(transform.copy(rotation = transform.rotation - 90f)) },
                            )
                            PreviewRoundIconButton(
                                icon = AppIcons.rotateClockwise,
                                contentDescription = strings.imagePreviewRotateRight,
                                onClick = { updateTransform(transform.copy(rotation = transform.rotation + 90f)) },
                            )
                            PreviewRoundIconButton(
                                icon = if (transform.flipHorizontal) {
                                    AppIcons.flipLeftFilled
                                } else {
                                    AppIcons.flipRightFilled
                                },
                                contentDescription = strings.imagePreviewFlipHorizontal,
                                onClick = {
                                    updateTransform(transform.copy(flipHorizontal = !transform.flipHorizontal))
                                },
                            )
                            PreviewRoundIconButton(
                                icon = if (transform.flipVertical) {
                                    AppIcons.flipLeftFilled
                                } else {
                                    AppIcons.flipRightFilled
                                },
                                contentDescription = strings.imagePreviewFlipVertical,
                                rotateIcon = 90f,
                                onClick = {
                                    updateTransform(transform.copy(flipVertical = !transform.flipVertical))
                                },
                            )
                            PreviewRoundIconButton(
                                icon = AppIcons.edit,
                                contentDescription = strings.paintEditMessage,
                                onClick = { onEdit(path) },
                            )
                            PreviewRoundIconButton(
                                icon = AppIcons.download,
                                contentDescription = strings.paintSaveAs,
                                onClick = { saveTransformedImageAs(path, transform, strings.paintSaveAs) },
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${(previewScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (paths.size > 1) {
                                Text(
                                    text = "${currentIndex + 1} / ${paths.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.SemiBold,
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
@OptIn(ExperimentalComposeUiApi::class)
private fun DesktopImageEditorWindow(
    path: String,
    onDismiss: () -> Unit,
    onSaved: (oldPath: String, newPath: String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val file = remember(path) { localImageFile(path)?.takeIf { it.isFile } ?: File(path.removePrefix("file://")) }
    val imageState by produceState<ImageLoadState>(initialValue = ImageLoadState.Loading, path) {
        value = ImageLoadState.Loading
        value = withContext(Dispatchers.IO) {
            loadImageBitmap(path, 4096)?.let(ImageLoadState::Success) ?: ImageLoadState.Error
        }
    }
    // 马赛克预览位图：与展示位图同尺寸的像素化版本，绘制时按涂抹路径裁剪显示
    val mosaicBitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            loadMosaicImageBitmap(path, 4096)
        }
    }
    val (imageWidth, imageHeight) = remember(path) { imageDimensions(path) }
    var editMode by remember(path) { mutableStateOf(DesktopImageEditMode.Draw) }
    var brushShape by remember(path) { mutableStateOf(DesktopBrushShape.Pen) }
    var brushWidth by remember(path) { mutableStateOf(8f) }
    var brushColor by remember(path) { mutableStateOf(Color(0xFFFF4D6D)) }
    var mosaicRadius by remember(path) { mutableStateOf(30f) }
    var textFontSize by remember(path) { mutableStateOf(48f) }
    var operations by remember(path) { mutableStateOf<List<DesktopEditOperation>>(emptyList()) }
    var activeOperation by remember(path) { mutableStateOf<DesktopEditOperation?>(null) }
    var selectedIndex by remember(path) { mutableStateOf<Int?>(null) }
    var textDialog by remember(path) { mutableStateOf<DesktopTextDialogState?>(null) }
    var cropLeft by remember(path) { mutableStateOf(0f) }
    var cropTop by remember(path) { mutableStateOf(0f) }
    var cropRight by remember(path) { mutableStateOf(1f) }
    var cropBottom by remember(path) { mutableStateOf(1f) }
    var viewportSize by remember(path) { mutableStateOf(IntSize.Zero) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val imageRect = remember(viewportSize, imageWidth, imageHeight) {
        displayedImageRect(viewportSize, imageWidth, imageHeight)
    }
    // 视口 → 图片坐标的缩放系数（图片始终按 Fit 展示）
    val viewScale = if (imageRect.width > 0f && imageWidth > 0) imageRect.width / imageWidth else 1f

    fun resetCrop() {
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
    }

    fun undo() {
        activeOperation = null
        selectedIndex = null
        if (operations.isNotEmpty()) {
            operations = operations.dropLast(1)
        }
    }

    // 切换工具或形状时取消选中，避免跨模式误操作
    LaunchedEffect(editMode, brushShape) {
        selectedIndex = null
        activeOperation = null
    }

    val windowSize = remember(path) { previewWindowSizeForPath(path) }
    val editorWindowState = rememberWindowState(width = windowSize.first.dp, height = windowSize.second.dp)

    Window(
        onCloseRequest = onDismiss,
        title = strings.paintEditMessage,
        icon = rememberPreviewAppIconPainter(),
        state = editorWindowState,
        resizable = true,
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = file.name.ifBlank { path },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PreviewRoundIconButton(
                        icon = AppIcons.undo,
                        contentDescription = strings.paintEditUndo,
                        enabled = operations.isNotEmpty(),
                        onClick = ::undo,
                    )
                    PreviewRoundIconButton(
                        icon = AppIcons.save,
                        contentDescription = strings.paintSave,
                        enabled = !isSaving && imageWidth > 0 && imageHeight > 0,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val savedPath = withContext(Dispatchers.IO) {
                                    saveDesktopEditedImage(
                                        sourcePath = path,
                                        operations = operations,
                                        cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom),
                                    )
                                }
                                isSaving = false
                                if (savedPath != null) {
                                    onSaved(path, savedPath)
                                }
                            }
                        },
                    )
                    PreviewRoundIconButton(
                        icon = AppIcons.close,
                        contentDescription = strings.close,
                        onClick = onDismiss,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .onSizeChanged { viewportSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = imageState) {
                        ImageLoadState.Loading -> CircularProgressIndicator(color = Color.White)
                        ImageLoadState.Error -> Text(strings.missingFile, color = Color.White.copy(alpha = 0.72f))
                        is ImageLoadState.Success -> {
                            Image(
                                bitmap = state.bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            if (imageRect.width > 0f && imageRect.height > 0f) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(editMode, brushShape, imageRect) {
                                            // 单击：形状/文字模式下选中或取消选中；再次点击选中的文字进入编辑
                                            detectTapGestures(
                                                onTap = { position ->
                                                    val imagePoint = imagePointUnclamped(position, imageRect, viewScale)
                                                    val tolerance = DESKTOP_HIT_TOLERANCE / viewScale
                                                    when {
                                                        editMode == DesktopImageEditMode.Draw &&
                                                            brushShape != DesktopBrushShape.Pen -> {
                                                            selectedIndex = hitTestDesktopOperation(
                                                                position = imagePoint,
                                                                operations = operations,
                                                                textMode = false,
                                                                tolerance = tolerance,
                                                                density = this,
                                                                textMeasurer = textMeasurer,
                                                                viewScale = viewScale,
                                                            )
                                                        }
                                                        editMode == DesktopImageEditMode.Text -> {
                                                            val index = hitTestDesktopOperation(
                                                                position = imagePoint,
                                                                operations = operations,
                                                                textMode = true,
                                                                tolerance = tolerance,
                                                                density = this,
                                                                textMeasurer = textMeasurer,
                                                                viewScale = viewScale,
                                                            )
                                                            if (index != null && index == selectedIndex) {
                                                                val overlay = operations[index] as DesktopEditOperation.TextOverlay
                                                                textDialog = DesktopTextDialogState(
                                                                    editIndex = index,
                                                                    initialText = overlay.text,
                                                                )
                                                            } else {
                                                                selectedIndex = index
                                                            }
                                                        }
                                                        else -> Unit
                                                    }
                                                },
                                            )
                                        }
                                        .pointerInput(editMode, brushShape, imageRect) {
                                            when (editMode) {
                                                DesktopImageEditMode.Draw -> when (brushShape) {
                                                    DesktopBrushShape.Pen -> detectDragGestures(
                                                        onDragStart = { position ->
                                                            imagePointFromViewport(position, imageRect, imageWidth, imageHeight)?.let { point ->
                                                                activeOperation = DesktopEditOperation.PenStroke(
                                                                    points = listOf(point),
                                                                    color = brushColor,
                                                                    strokeWidth = brushWidth / viewScale,
                                                                )
                                                            }
                                                        },
                                                        onDrag = { change, _ ->
                                                            val stroke = activeOperation as? DesktopEditOperation.PenStroke
                                                                ?: return@detectDragGestures
                                                            val point = clampedImagePointFromViewport(
                                                                change.position,
                                                                imageRect,
                                                                imageWidth,
                                                                imageHeight,
                                                            ) ?: return@detectDragGestures
                                                            activeOperation = stroke.copy(points = stroke.points + point)
                                                        },
                                                        onDragEnd = {
                                                            (activeOperation as? DesktopEditOperation.PenStroke)
                                                                ?.takeIf { it.points.size > 1 }
                                                                ?.let { stroke -> operations = operations + stroke }
                                                            activeOperation = null
                                                        },
                                                        onDragCancel = { activeOperation = null },
                                                    )
                                                    else -> {
                                                        // 形状模式：优先操作选中图形的手柄，其次选中命中的图形并移动，否则绘制新图形
                                                        var dragAction = DesktopDragAction.None
                                                        detectDragGestures(
                                                            onDragStart = { position ->
                                                                dragAction = DesktopDragAction.None
                                                                val imagePoint = imagePointUnclamped(position, imageRect, viewScale)
                                                                val tolerance = DESKTOP_HIT_TOLERANCE / viewScale
                                                                val selected = selectedIndex?.let(operations::getOrNull)
                                                                if (selected != null) {
                                                                    dragAction = hitTestDesktopHandle(
                                                                        position = imagePoint,
                                                                        operation = selected,
                                                                        handleHitRadius = DESKTOP_HANDLE_HIT_RADIUS / viewScale,
                                                                        rotateHitRadius = DESKTOP_ROTATE_HIT_RADIUS / viewScale,
                                                                        tolerance = tolerance,
                                                                        density = this,
                                                                        textMeasurer = textMeasurer,
                                                                        viewScale = viewScale,
                                                                    )
                                                                }
                                                                if (dragAction == DesktopDragAction.None) {
                                                                    val hitIndex = hitTestDesktopOperation(
                                                                        position = imagePoint,
                                                                        operations = operations,
                                                                        textMode = false,
                                                                        tolerance = tolerance,
                                                                        density = this,
                                                                        textMeasurer = textMeasurer,
                                                                        viewScale = viewScale,
                                                                    )
                                                                    if (hitIndex != null) {
                                                                        selectedIndex = hitIndex
                                                                        dragAction = DesktopDragAction.Move
                                                                    }
                                                                }
                                                                if (dragAction == DesktopDragAction.None) {
                                                                    selectedIndex = null
                                                                    imagePointFromViewport(position, imageRect, imageWidth, imageHeight)?.let { point ->
                                                                        val width = brushWidth / viewScale
                                                                        dragAction = DesktopDragAction.DrawNew
                                                                        activeOperation = when (brushShape) {
                                                                            DesktopBrushShape.Rect ->
                                                                                DesktopEditOperation.RectStroke(point, point, brushColor, width)
                                                                            DesktopBrushShape.Oval ->
                                                                                DesktopEditOperation.OvalStroke(point, point, brushColor, width)
                                                                            else ->
                                                                                DesktopEditOperation.ArrowStroke(point, point, brushColor, width)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                when (dragAction) {
                                                                    DesktopDragAction.DrawNew -> {
                                                                        val point = clampedImagePointFromViewport(
                                                                            change.position,
                                                                            imageRect,
                                                                            imageWidth,
                                                                            imageHeight,
                                                                        ) ?: return@detectDragGestures
                                                                        activeOperation = when (val op = activeOperation) {
                                                                            is DesktopEditOperation.RectStroke -> op.copy(end = point)
                                                                            is DesktopEditOperation.OvalStroke -> op.copy(end = point)
                                                                            is DesktopEditOperation.ArrowStroke -> op.copy(end = point)
                                                                            else -> op
                                                                        }
                                                                    }
                                                                    DesktopDragAction.None -> Unit
                                                                    else -> {
                                                                        val index = selectedIndex ?: return@detectDragGestures
                                                                        val operation = operations.getOrNull(index) ?: return@detectDragGestures
                                                                        operations = operations.toMutableList().also { list ->
                                                                            list[index] = applyDesktopDragAction(
                                                                                operation = operation,
                                                                                action = dragAction,
                                                                                delta = dragAmount / viewScale,
                                                                                currentPos = imagePointUnclamped(change.position, imageRect, viewScale),
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                if (dragAction == DesktopDragAction.DrawNew) {
                                                                    activeOperation
                                                                        ?.takeIf { desktopShapeSpan(it) > 3f }
                                                                        ?.let { shape -> operations = operations + shape }
                                                                    activeOperation = null
                                                                }
                                                                dragAction = DesktopDragAction.None
                                                            },
                                                            onDragCancel = {
                                                                activeOperation = null
                                                                dragAction = DesktopDragAction.None
                                                            },
                                                        )
                                                    }
                                                }
                                                DesktopImageEditMode.Mosaic -> detectDragGestures(
                                                    onDragStart = { position ->
                                                        imagePointFromViewport(position, imageRect, imageWidth, imageHeight)?.let { point ->
                                                            activeOperation = DesktopEditOperation.MosaicStroke(
                                                                points = listOf(point),
                                                                radius = mosaicRadius / viewScale,
                                                            )
                                                        }
                                                    },
                                                    onDrag = { change, _ ->
                                                        val stroke = activeOperation as? DesktopEditOperation.MosaicStroke
                                                            ?: return@detectDragGestures
                                                        val point = clampedImagePointFromViewport(
                                                            change.position,
                                                            imageRect,
                                                            imageWidth,
                                                            imageHeight,
                                                        ) ?: return@detectDragGestures
                                                        activeOperation = stroke.copy(
                                                            points = appendMosaicPoint(stroke.points, point, stroke.radius),
                                                        )
                                                    },
                                                    onDragEnd = {
                                                        (activeOperation as? DesktopEditOperation.MosaicStroke)
                                                            ?.takeIf { it.points.isNotEmpty() }
                                                            ?.let { stroke -> operations = operations + stroke }
                                                        activeOperation = null
                                                    },
                                                    onDragCancel = { activeOperation = null },
                                                )
                                                DesktopImageEditMode.Text -> {
                                                    // 文字模式：拖动选中文字可移动，选中后支持缩放/旋转手柄
                                                    var dragAction = DesktopDragAction.None
                                                    detectDragGestures(
                                                        onDragStart = { position ->
                                                            dragAction = DesktopDragAction.None
                                                            val imagePoint = imagePointUnclamped(position, imageRect, viewScale)
                                                            val tolerance = DESKTOP_HIT_TOLERANCE / viewScale
                                                            val selected = selectedIndex?.let(operations::getOrNull)
                                                            if (selected is DesktopEditOperation.TextOverlay) {
                                                                dragAction = hitTestDesktopHandle(
                                                                    position = imagePoint,
                                                                    operation = selected,
                                                                    handleHitRadius = DESKTOP_HANDLE_HIT_RADIUS / viewScale,
                                                                    rotateHitRadius = DESKTOP_ROTATE_HIT_RADIUS / viewScale,
                                                                    tolerance = tolerance,
                                                                    density = this,
                                                                    textMeasurer = textMeasurer,
                                                                    viewScale = viewScale,
                                                                )
                                                            }
                                                            if (dragAction == DesktopDragAction.None) {
                                                                val hitIndex = hitTestDesktopOperation(
                                                                    position = imagePoint,
                                                                    operations = operations,
                                                                    textMode = true,
                                                                    tolerance = tolerance,
                                                                    density = this,
                                                                    textMeasurer = textMeasurer,
                                                                    viewScale = viewScale,
                                                                )
                                                                if (hitIndex != null) {
                                                                    selectedIndex = hitIndex
                                                                    dragAction = DesktopDragAction.Move
                                                                } else {
                                                                    selectedIndex = null
                                                                }
                                                            }
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            if (dragAction == DesktopDragAction.None) return@detectDragGestures
                                                            val index = selectedIndex ?: return@detectDragGestures
                                                            val operation = operations.getOrNull(index) ?: return@detectDragGestures
                                                            operations = operations.toMutableList().also { list ->
                                                                list[index] = applyDesktopDragAction(
                                                                    operation = operation,
                                                                    action = dragAction,
                                                                    delta = dragAmount / viewScale,
                                                                    currentPos = imagePointUnclamped(change.position, imageRect, viewScale),
                                                                )
                                                            }
                                                        },
                                                        onDragEnd = { dragAction = DesktopDragAction.None },
                                                        onDragCancel = { dragAction = DesktopDragAction.None },
                                                    )
                                                }
                                                DesktopImageEditMode.Crop -> {
                                                    var handle: DesktopCropHandle? = null
                                                    detectDragGestures(
                                                        onDragStart = { position ->
                                                            handle = cropHandleAt(
                                                                position = position,
                                                                imageRect = imageRect,
                                                                cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom),
                                                            )
                                                        },
                                                        onDrag = { _, dragAmount ->
                                                            val activeHandle = handle ?: return@detectDragGestures
                                                            val dx = dragAmount.x / imageRect.width
                                                            val dy = dragAmount.y / imageRect.height
                                                            when (activeHandle) {
                                                                DesktopCropHandle.Move -> {
                                                                    val width = cropRight - cropLeft
                                                                    val height = cropBottom - cropTop
                                                                    cropLeft = (cropLeft + dx).coerceIn(0f, 1f - width)
                                                                    cropTop = (cropTop + dy).coerceIn(0f, 1f - height)
                                                                    cropRight = cropLeft + width
                                                                    cropBottom = cropTop + height
                                                                }
                                                                DesktopCropHandle.TopLeft -> {
                                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                                                }
                                                                DesktopCropHandle.TopRight -> {
                                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                                                }
                                                                DesktopCropHandle.BottomLeft -> {
                                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                                                }
                                                                DesktopCropHandle.BottomRight -> {
                                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = { handle = null },
                                                        onDragCancel = { handle = null },
                                                    )
                                                }
                                            }
                                        },
                                ) {
                                    val visibleOperations = operations + listOfNotNull(activeOperation)
                                    visibleOperations.forEach { operation ->
                                        when (operation) {
                                            is DesktopEditOperation.PenStroke -> {
                                                operation.points.zipWithNext().forEach { (start, end) ->
                                                    drawLine(
                                                        color = operation.color,
                                                        start = viewportPointFromImage(start, imageRect, imageWidth, imageHeight),
                                                        end = viewportPointFromImage(end, imageRect, imageWidth, imageHeight),
                                                        strokeWidth = operation.strokeWidth * viewScale,
                                                        cap = StrokeCap.Round,
                                                    )
                                                }
                                            }
                                            is DesktopEditOperation.RectStroke -> {
                                                val start = viewportPointFromImage(operation.start, imageRect, imageWidth, imageHeight)
                                                val end = viewportPointFromImage(operation.end, imageRect, imageWidth, imageHeight)
                                                val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                                                val halfWidth = abs(end.x - start.x) / 2f * operation.scale
                                                val halfHeight = abs(end.y - start.y) / 2f * operation.scale
                                                rotate(degrees = operation.rotation, pivot = center) {
                                                    drawRect(
                                                        color = operation.color,
                                                        topLeft = Offset(center.x - halfWidth, center.y - halfHeight),
                                                        size = Size(halfWidth * 2f, halfHeight * 2f),
                                                        style = Stroke(width = operation.strokeWidth * viewScale),
                                                    )
                                                }
                                            }
                                            is DesktopEditOperation.OvalStroke -> {
                                                val start = viewportPointFromImage(operation.start, imageRect, imageWidth, imageHeight)
                                                val end = viewportPointFromImage(operation.end, imageRect, imageWidth, imageHeight)
                                                val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                                                val halfWidth = abs(end.x - start.x) / 2f * operation.scale
                                                val halfHeight = abs(end.y - start.y) / 2f * operation.scale
                                                rotate(degrees = operation.rotation, pivot = center) {
                                                    drawOval(
                                                        color = operation.color,
                                                        topLeft = Offset(center.x - halfWidth, center.y - halfHeight),
                                                        size = Size(halfWidth * 2f, halfHeight * 2f),
                                                        style = Stroke(width = operation.strokeWidth * viewScale),
                                                    )
                                                }
                                            }
                                            is DesktopEditOperation.ArrowStroke -> {
                                                val start = viewportPointFromImage(operation.start, imageRect, imageWidth, imageHeight)
                                                val end = viewportPointFromImage(operation.end, imageRect, imageWidth, imageHeight)
                                                val width = operation.strokeWidth * viewScale
                                                drawLine(operation.color, start, end, width, StrokeCap.Round)
                                                arrowHeadPoints(start, end, width)?.let { (left, right) ->
                                                    drawLine(operation.color, end, left, width, StrokeCap.Round)
                                                    drawLine(operation.color, end, right, width, StrokeCap.Round)
                                                }
                                            }
                                            is DesktopEditOperation.MosaicStroke -> {
                                                val mosaic = mosaicBitmap
                                                if (mosaic != null && operation.points.isNotEmpty()) {
                                                    val radius = operation.radius * viewScale
                                                    val clip = Path()
                                                    operation.points.forEach { point ->
                                                        val center = viewportPointFromImage(point, imageRect, imageWidth, imageHeight)
                                                        clip.addOval(
                                                            Rect(
                                                                center.x - radius,
                                                                center.y - radius,
                                                                center.x + radius,
                                                                center.y + radius,
                                                            ),
                                                        )
                                                    }
                                                    clipPath(clip) {
                                                        drawImage(
                                                            image = mosaic,
                                                            dstOffset = IntOffset(
                                                                imageRect.left.roundToInt(),
                                                                imageRect.top.roundToInt(),
                                                            ),
                                                            dstSize = IntSize(
                                                                imageRect.width.roundToInt(),
                                                                imageRect.height.roundToInt(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                            is DesktopEditOperation.TextOverlay -> {
                                                val layout = textMeasurer.measure(
                                                    AnnotatedString(operation.text),
                                                    TextStyle(
                                                        color = operation.color,
                                                        fontSize = (operation.fontSize * viewScale).toSp(),
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center,
                                                    ),
                                                )
                                                val center = viewportPointFromImage(operation.position, imageRect, imageWidth, imageHeight)
                                                rotate(degrees = operation.rotation, pivot = center) {
                                                    scale(scale = operation.scale, pivot = center) {
                                                        drawText(
                                                            textLayoutResult = layout,
                                                            topLeft = Offset(
                                                                center.x - layout.size.width / 2f,
                                                                center.y - layout.size.height / 2f,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 选中图形的边框与控制手柄
                                    val selectedOperation = selectedIndex?.let(operations::getOrNull)
                                    val selectionVisible = when (editMode) {
                                        DesktopImageEditMode.Draw ->
                                            brushShape != DesktopBrushShape.Pen &&
                                                selectedOperation != null &&
                                                selectedOperation !is DesktopEditOperation.TextOverlay
                                        DesktopImageEditMode.Text -> selectedOperation is DesktopEditOperation.TextOverlay
                                        else -> false
                                    }
                                    if (selectionVisible && selectedOperation != null) {
                                        fun toViewport(point: Offset) =
                                            viewportPointFromImage(point, imageRect, imageWidth, imageHeight)
                                        when (selectedOperation) {
                                            is DesktopEditOperation.RectStroke -> drawDesktopSelectionHandles(
                                                transformedCorners(
                                                    selectedOperation.start,
                                                    selectedOperation.end,
                                                    selectedOperation.scale,
                                                    selectedOperation.rotation,
                                                ).map(::toViewport),
                                            )
                                            is DesktopEditOperation.OvalStroke -> drawDesktopSelectionHandles(
                                                transformedCorners(
                                                    selectedOperation.start,
                                                    selectedOperation.end,
                                                    selectedOperation.scale,
                                                    selectedOperation.rotation,
                                                ).map(::toViewport),
                                            )
                                            is DesktopEditOperation.ArrowStroke -> drawDesktopArrowHandles(
                                                toViewport(selectedOperation.start),
                                                toViewport(selectedOperation.end),
                                            )
                                            is DesktopEditOperation.TextOverlay -> drawDesktopSelectionHandles(
                                                textOverlayCorners(this, textMeasurer, selectedOperation, viewScale)
                                                    .map(::toViewport),
                                            )
                                            else -> Unit
                                        }
                                    }

                                    if (editMode == DesktopImageEditMode.Crop) {
                                        val crop = cropRectInViewport(
                                            imageRect = imageRect,
                                            cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom),
                                        )
                                        val dim = Color.Black.copy(alpha = 0.46f)
                                        drawRect(dim, topLeft = imageRect.topLeft, size = Size(imageRect.width, crop.top - imageRect.top))
                                        drawRect(dim, topLeft = Offset(imageRect.left, crop.bottom), size = Size(imageRect.width, imageRect.bottom - crop.bottom))
                                        drawRect(dim, topLeft = Offset(imageRect.left, crop.top), size = Size(crop.left - imageRect.left, crop.height))
                                        drawRect(dim, topLeft = Offset(crop.right, crop.top), size = Size(imageRect.right - crop.right, crop.height))
                                        drawRect(Color.White, topLeft = crop.topLeft, size = crop.size, style = Stroke(width = 2.5f))
                                        listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight).forEach { point ->
                                            drawCircle(Color.White, radius = 7f, center = point)
                                            drawCircle(Color.Black.copy(alpha = 0.65f), radius = 4f, center = point)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isSaving) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EditorModeButton(
                            selected = editMode == DesktopImageEditMode.Draw,
                            icon = AppIcons.brush,
                            label = strings.paintEditBrush,
                            onClick = { editMode = DesktopImageEditMode.Draw },
                        )
                        EditorModeButton(
                            selected = editMode == DesktopImageEditMode.Mosaic,
                            icon = AppIcons.blur,
                            label = strings.paintEditMosaic,
                            onClick = { editMode = DesktopImageEditMode.Mosaic },
                        )
                        EditorModeButton(
                            selected = editMode == DesktopImageEditMode.Text,
                            icon = AppIcons.text,
                            label = strings.paintEditText,
                            onClick = { editMode = DesktopImageEditMode.Text },
                        )
                        EditorModeButton(
                            selected = editMode == DesktopImageEditMode.Crop,
                            icon = AppIcons.crop,
                            label = strings.paintEditCrop,
                            onClick = { editMode = DesktopImageEditMode.Crop },
                        )
                    }
                    when (editMode) {
                        DesktopImageEditMode.Draw -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EditorShapeButton(
                                selected = brushShape == DesktopBrushShape.Pen,
                                icon = AppIcons.gesture,
                                contentDescription = strings.paintEditShapePen,
                                onClick = { brushShape = DesktopBrushShape.Pen },
                            )
                            EditorShapeButton(
                                selected = brushShape == DesktopBrushShape.Rect,
                                icon = AppIcons.cropSquare,
                                contentDescription = strings.paintEditShapeRect,
                                onClick = { brushShape = DesktopBrushShape.Rect },
                            )
                            EditorShapeButton(
                                selected = brushShape == DesktopBrushShape.Oval,
                                icon = AppIcons.radioButtonOff,
                                contentDescription = strings.paintEditShapeOval,
                                onClick = { brushShape = DesktopBrushShape.Oval },
                            )
                            EditorShapeButton(
                                selected = brushShape == DesktopBrushShape.Arrow,
                                icon = AppIcons.callMade,
                                contentDescription = strings.paintEditShapeArrow,
                                onClick = { brushShape = DesktopBrushShape.Arrow },
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            DesktopBrushColors(selected = brushColor, onSelect = { brushColor = it })
                            Text(
                                text = "${brushWidth.roundToInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.74f),
                            )
                            Slider(
                                value = brushWidth,
                                onValueChange = { brushWidth = it },
                                valueRange = 2f..32f,
                                modifier = Modifier.width(140.dp),
                            )
                        }
                        DesktopImageEditMode.Mosaic -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${mosaicRadius.roundToInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.74f),
                            )
                            Slider(
                                value = mosaicRadius,
                                onValueChange = { mosaicRadius = it },
                                valueRange = 15f..80f,
                                modifier = Modifier.width(200.dp),
                            )
                        }
                        DesktopImageEditMode.Text -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EditorModeButton(
                                selected = false,
                                icon = AppIcons.add,
                                label = strings.paintEditAddText,
                                onClick = { textDialog = DesktopTextDialogState(editIndex = null, initialText = "") },
                            )
                            DesktopBrushColors(selected = brushColor, onSelect = { brushColor = it })
                            Text(
                                text = "${textFontSize.roundToInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.74f),
                            )
                            Slider(
                                value = textFontSize,
                                onValueChange = { textFontSize = it },
                                valueRange = 20f..120f,
                                modifier = Modifier.width(160.dp),
                            )
                        }
                        DesktopImageEditMode.Crop -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = ::resetCrop) {
                                Text(strings.reset)
                            }
                        }
                    }
                }
            }

            textDialog?.let { dialog ->
                DesktopEditTextDialog(
                    initialText = dialog.initialText,
                    title = strings.paintEditTextTitle,
                    hint = strings.paintEditTextHint,
                    confirmLabel = strings.confirm,
                    cancelLabel = strings.cancel,
                    onDismiss = { textDialog = null },
                    onConfirm = { text ->
                        val trimmed = text.trim().take(100)
                        if (trimmed.isNotEmpty()) {
                            val editIndex = dialog.editIndex
                            if (editIndex != null) {
                                val existing = operations.getOrNull(editIndex) as? DesktopEditOperation.TextOverlay
                                if (existing != null) {
                                    operations = operations.toMutableList().also { list ->
                                        list[editIndex] = existing.copy(text = trimmed)
                                    }
                                }
                            } else {
                                operations = operations + DesktopEditOperation.TextOverlay(
                                    text = trimmed,
                                    position = Offset(imageWidth / 2f, imageHeight / 2f),
                                    color = brushColor,
                                    fontSize = textFontSize / viewScale,
                                )
                            }
                        }
                        textDialog = null
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopEditTextDialog(
    initialText: String,
    title: String,
    hint: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(100) },
                placeholder = { Text(hint) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

@Composable
private fun EditorShapeButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.32f else 0.10f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(7.dp).size(18.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun EditorModeButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.30f else 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = Color.White)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

@Composable
private fun DesktopBrushColors(
    selected: Color,
    onSelect: (Color) -> Unit,
) {
    val colors = listOf(
        Color.White,
        Color(0xFFFF4D6D),
        Color(0xFFFFB020),
        Color(0xFF47D16C),
        Color(0xFF5AA7FF),
        Color(0xFF9D7CFF),
        Color.Black,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selected) 2.dp else 1.dp,
                        color = if (color == selected) Color.White else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(color) },
                    ),
            )
        }
    }
}

private enum class DesktopImageEditMode {
    Draw,
    Mosaic,
    Text,
    Crop,
}

private enum class DesktopBrushShape {
    Pen,
    Rect,
    Oval,
    Arrow,
}

private enum class DesktopCropHandle {
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

private data class DesktopTextDialogState(
    val editIndex: Int?,
    val initialText: String,
)

/** 选中图形后的拖拽交互类型。 */
private enum class DesktopDragAction {
    /** 无操作 */
    None,
    /** 正在绘制新图形 */
    DrawNew,
    /** 移动整个图形 */
    Move,
    /** 右下角缩放手柄 */
    Scale,
    /** 左上角旋转手柄 */
    Rotate,
    /** 拖动箭头起点 */
    ArrowStart,
    /** 拖动箭头终点 */
    ArrowEnd,
}

/**
 * 图片编辑操作模型，所有坐标与尺寸均处于原图像素坐标系，
 * 展示时按视口缩放系数换算，保存时直接映射到原图。
 *
 * 矩形/椭圆/文字支持选中后的缩放与旋转（围绕中心点），箭头支持两端独立拖动。
 */
private sealed class DesktopEditOperation {
    data class PenStroke(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
    ) : DesktopEditOperation()

    data class RectStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val scale: Float = 1f,
        val rotation: Float = 0f,
    ) : DesktopEditOperation()

    data class OvalStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val scale: Float = 1f,
        val rotation: Float = 0f,
    ) : DesktopEditOperation()

    data class ArrowStroke(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
    ) : DesktopEditOperation()

    data class MosaicStroke(
        val points: List<Offset>,
        val radius: Float,
    ) : DesktopEditOperation()

    data class TextOverlay(
        val text: String,
        val position: Offset,
        val color: Color,
        val fontSize: Float,
        val scale: Float = 1f,
        val rotation: Float = 0f,
    ) : DesktopEditOperation()
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PaintImageCompareDialog(
    paths: List<String>,
    requestId: Int,
    onDismiss: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    if (paths.size < 2) {
        onDismiss()
        return
    }

    val bottomPath = paths[0]
    val topPath = paths[1]
    val bottomState by produceState<ImageLoadState>(initialValue = ImageLoadState.Loading, bottomPath) {
        value = ImageLoadState.Loading
        value = withContext(Dispatchers.IO) {
            loadImageBitmap(bottomPath, 4096)?.let(ImageLoadState::Success) ?: ImageLoadState.Error
        }
    }
    val topState by produceState<ImageLoadState>(initialValue = ImageLoadState.Loading, topPath) {
        value = ImageLoadState.Loading
        value = withContext(Dispatchers.IO) {
            loadImageBitmap(topPath, 4096)?.let(ImageLoadState::Success) ?: ImageLoadState.Error
        }
    }

    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var hideTop by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var previewScale by remember(paths) { mutableStateOf(1f) }
    var previewOffset by remember(paths) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember(paths) { mutableStateOf(IntSize.Zero) }

    fun applyPreviewTransform(scale: Float, offset: Offset = previewOffset) {
        val boundedScale = scale.coerceIn(DESKTOP_PREVIEW_MIN_SCALE, DESKTOP_PREVIEW_MAX_SCALE)
        previewScale = boundedScale
        previewOffset = if (boundedScale <= 1f) {
            Offset.Zero
        } else {
            offset.clampedForScale(previewViewportSize, boundedScale)
        }
    }

    val compareWindowState = rememberWindowState(width = 1120.dp, height = 780.dp)

    Window(
        onCloseRequest = onDismiss,
        title = strings.paintImageCompare,
        icon = rememberPreviewAppIconPainter(),
        state = compareWindowState,
        resizable = true,
    ) {
        val compareWindow = window
        LaunchedEffect(requestId, compareWindow) {
            if (compareWindowState.isMinimized) {
                compareWindowState.isMinimized = false
            }
            compareWindow.toFront()
            compareWindow.requestFocus()
        }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, top = 76.dp, bottom = 120.dp)
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollDelta != 0f) {
                                val zoomFactor = if (scrollDelta < 0f) 1.12f else 0.90f
                                applyPreviewTransform(previewScale * zoomFactor)
                            }
                        }
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Primary) {
                                holdJob?.cancel()
                                holdJob = scope.launch {
                                    delay(320)
                                    hideTop = true
                                }
                            }
                        }
                        .onPointerEvent(PointerEventType.Release) {
                            holdJob?.cancel()
                            holdJob = null
                            hideTop = false
                        }
                        .onPointerEvent(PointerEventType.Exit) {
                            holdJob?.cancel()
                            holdJob = null
                            hideTop = false
                        }
                        .pointerInput(paths, previewScale, previewViewportSize) {
                            detectDragGestures { change, dragAmount ->
                                if (previewScale > 1f) {
                                    change.consume()
                                    applyPreviewTransform(previewScale, previewOffset + dragAmount)
                                }
                            }
                        }
                        .pointerInput(paths) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = { tapOffset ->
                                    if (previewScale > 1f) {
                                        applyPreviewTransform(1f, Offset.Zero)
                                    } else {
                                        val targetScale = DESKTOP_PREVIEW_DOUBLE_TAP_SCALE
                                        val centerX = previewViewportSize.width / 2f
                                        val centerY = previewViewportSize.height / 2f
                                        val targetOffset = Offset(
                                            x = (centerX - tapOffset.x) * (targetScale - 1f),
                                            y = (centerY - tapOffset.y) * (targetScale - 1f),
                                        )
                                        applyPreviewTransform(targetScale, targetOffset)
                                    }
                                },
                            )
                        }
                        .onSizeChanged { previewViewportSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    CompareLayerImage(
                        state = bottomState,
                        path = bottomPath,
                        alpha = 1f,
                        scale = previewScale,
                        offset = previewOffset,
                        viewportSize = previewViewportSize,
                    )
                    CompareLayerImage(
                        state = topState,
                        path = topPath,
                        alpha = if (hideTop) 0f else 1f,
                        scale = previewScale,
                        offset = previewOffset,
                        viewportSize = previewViewportSize,
                    )

                    if (bottomState is ImageLoadState.Loading || topState is ImageLoadState.Loading) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (bottomState is ImageLoadState.Error || topState is ImageLoadState.Error) {
                        Text(strings.missingFile, color = Color.White.copy(alpha = 0.72f))
                    }
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = strings.paintImageCompare,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = if (hideTop) strings.paintCompareShowingBottom else strings.paintCompareHintHold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                        Text(
                            text = "${(previewScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.70f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareLayerImage(
    state: ImageLoadState,
    path: String,
    alpha: Float,
    scale: Float,
    offset: Offset,
    viewportSize: IntSize,
) {
    if (state !is ImageLoadState.Success) return
    val transform = remember(path) { DesktopImageTransformState() }
    val compensation = previewRotationCompensation(
        bitmap = state.bitmap,
        viewportSize = viewportSize,
        rotation = transform.rotation,
    )
    Image(
        bitmap = state.bitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale * compensation
                scaleY = scale * compensation
                translationX = offset.x
                translationY = offset.y
                this.alpha = alpha
            },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun rememberPreviewAppIconPainter(): Painter {
    return remember {
        val stream = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icons/app.png")) {
            "Missing desktop app icon resource"
        }
        stream.use {
            BitmapPainter(ImageIO.read(it).toComposeImageBitmap())
        }
    }
}

@Composable
private fun PreviewRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rotateIcon: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.20f else 0.08f))
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
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotateIcon },
            tint = Color.White.copy(alpha = if (enabled) 0.96f else 0.36f),
        )
    }
}

@Composable
private fun GeneratingBlock(
    ratio: Float,
    text: String,
) {
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

private fun selectedImageFromCachedPath(path: String): SelectedImage? {
    val source = localImageFile(path)?.takeIf { it.isFile } ?: return null
    val cachedFile = cacheReferenceImageFile(source) ?: source
    return selectedImageFromPath(cachedFile.absolutePath)
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
                    .mapNotNull { selectedImageFromCachedPath(it.absolutePath) }
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

private fun cacheReferenceImageFile(source: File): File? {
    return runCatching {
        val directory = DesktopAiPaintStoragePaths.clipboardCacheDirectory().apply { mkdirs() }
        val sourceFile = source.canonicalFile
        val cacheDirectory = directory.canonicalFile
        if (sourceFile.toPath().startsWith(cacheDirectory.toPath())) {
            return@runCatching sourceFile
        }
        val extension = sourceFile.extension
            .lowercase()
            .takeIf { isSupportedImageName("reference.$it") }
            ?: "png"
        val target = File(cacheDirectory, "reference-${UUID.randomUUID()}.$extension")
        sourceFile.copyTo(target, overwrite = false)
    }.getOrNull()
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
    return DesktopFilePicker.pickImagePaths(title, ::isSupportedImageName)
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
        val sourceFile = localImageFile(path) ?: File(path.removePrefix("file://"))
        val source = ImageIO.read(sourceFile) ?: return@runCatching null
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

private fun Offset.clampedForScale(viewportSize: IntSize, scale: Float): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = viewportSize.width * (scale - 1f) / 2f
    val maxY = viewportSize.height * (scale - 1f) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

private fun previewRotationCompensation(
    bitmap: ImageBitmap,
    viewportSize: IntSize,
    rotation: Float,
): Float {
    if (bitmap.width <= 0 || bitmap.height <= 0 || viewportSize.width <= 0 || viewportSize.height <= 0) {
        return 1f
    }
    val fitScale = minOf(
        viewportSize.width.toFloat() / bitmap.width.toFloat(),
        viewportSize.height.toFloat() / bitmap.height.toFloat(),
    )
    val displayWidth = bitmap.width * fitScale
    val displayHeight = bitmap.height * fitScale
    val rotatedFitScale = minOf(
        viewportSize.width.toFloat() / displayHeight,
        viewportSize.height.toFloat() / displayWidth,
    )
    val sinValue = sin(Math.toRadians(rotation.toDouble())).toFloat()
    val rotationFactor = sinValue * sinValue
    return 1f + (rotatedFitScale - 1f) * rotationFactor
}

private fun imageDimensions(path: String): Pair<Int, Int> {
    return runCatching {
        val image = ImageIO.read(File(path.removePrefix("file://"))) ?: return@runCatching 0 to 0
        image.width to image.height
    }.getOrDefault(0 to 0)
}

private fun normalizeImagePath(path: String): String =
    localImageFile(path)?.absolutePath ?: File(path.removePrefix("file://")).absolutePath

private fun displayedImageRect(viewportSize: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return Rect.Zero
    }
    val scale = min(
        viewportSize.width.toFloat() / imageWidth.toFloat(),
        viewportSize.height.toFloat() / imageHeight.toFloat(),
    )
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (viewportSize.width - width) / 2f
    val top = (viewportSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun imagePointFromViewport(
    position: Offset,
    imageRect: Rect,
    imageWidth: Int,
    imageHeight: Int,
): Offset? {
    if (!imageRect.contains(position) || imageRect.width <= 0f || imageRect.height <= 0f) return null
    return Offset(
        x = ((position.x - imageRect.left) / imageRect.width * imageWidth).coerceIn(0f, imageWidth.toFloat()),
        y = ((position.y - imageRect.top) / imageRect.height * imageHeight).coerceIn(0f, imageHeight.toFloat()),
    )
}

private fun viewportPointFromImage(
    point: Offset,
    imageRect: Rect,
    imageWidth: Int,
    imageHeight: Int,
): Offset {
    return Offset(
        x = imageRect.left + point.x / imageWidth.toFloat() * imageRect.width,
        y = imageRect.top + point.y / imageHeight.toFloat() * imageRect.height,
    )
}

private fun cropRectInViewport(imageRect: Rect, cropRect: Rect): Rect =
    Rect(
        left = imageRect.left + cropRect.left * imageRect.width,
        top = imageRect.top + cropRect.top * imageRect.height,
        right = imageRect.left + cropRect.right * imageRect.width,
        bottom = imageRect.top + cropRect.bottom * imageRect.height,
    )

private fun cropHandleAt(position: Offset, imageRect: Rect, cropRect: Rect): DesktopCropHandle? {
    val crop = cropRectInViewport(imageRect, cropRect)
    val tolerance = 22f
    fun near(point: Offset): Boolean = (position - point).getDistance() <= tolerance
    return when {
        near(crop.topLeft) -> DesktopCropHandle.TopLeft
        near(crop.topRight) -> DesktopCropHandle.TopRight
        near(crop.bottomLeft) -> DesktopCropHandle.BottomLeft
        near(crop.bottomRight) -> DesktopCropHandle.BottomRight
        crop.contains(position) -> DesktopCropHandle.Move
        else -> null
    }
}

/** 马赛克块大小（原图像素）。 */
private const val DESKTOP_MOSAIC_BLOCK_SIZE = 20

private fun saveDesktopEditedImage(
    sourcePath: String,
    operations: List<DesktopEditOperation>,
    cropRect: Rect,
): String? {
    return runCatching {
        val sourceFile = localImageFile(sourcePath)?.takeIf { it.isFile } ?: File(sourcePath.removePrefix("file://"))
        val source = ImageIO.read(sourceFile) ?: return@runCatching null
        val mosaicSource = if (operations.any { it is DesktopEditOperation.MosaicStroke }) {
            source.mosaicized(DESKTOP_MOSAIC_BLOCK_SIZE)
        } else {
            null
        }
        val working = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = working.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, null)
            operations.forEach { operation ->
                drawOperationOnGraphics(graphics, operation, mosaicSource)
            }
        } finally {
            graphics.dispose()
        }

        val left = (cropRect.left.coerceIn(0f, 1f) * working.width).roundToInt().coerceIn(0, working.width - 1)
        val top = (cropRect.top.coerceIn(0f, 1f) * working.height).roundToInt().coerceIn(0, working.height - 1)
        val right = (cropRect.right.coerceIn(0f, 1f) * working.width).roundToInt().coerceIn(left + 1, working.width)
        val bottom = (cropRect.bottom.coerceIn(0f, 1f) * working.height).roundToInt().coerceIn(top + 1, working.height)
        val output = BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_ARGB)
        val outputGraphics = output.createGraphics()
        try {
            outputGraphics.drawImage(
                working,
                0,
                0,
                output.width,
                output.height,
                left,
                top,
                right,
                bottom,
                null,
            )
        } finally {
            outputGraphics.dispose()
        }

        val directory = DesktopAiPaintStoragePaths.generatedImagesDirectory().apply { mkdirs() }
        val target = File(directory, "edited-${UUID.randomUUID()}.png")
        if (ImageIO.write(output, "png", target)) target.absolutePath else null
    }.getOrNull()
}

/** 把单个编辑操作绘制到原图 Graphics2D 上（坐标已是原图像素）。 */
private fun drawOperationOnGraphics(
    graphics: Graphics2D,
    operation: DesktopEditOperation,
    mosaicSource: BufferedImage?,
) {
    when (operation) {
        is DesktopEditOperation.PenStroke -> {
            if (operation.points.size < 2) return
            graphics.color = operation.color.toAwtColor()
            graphics.stroke = java.awt.BasicStroke(
                operation.strokeWidth.coerceAtLeast(1f),
                java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND,
            )
            val pen = java.awt.geom.GeneralPath()
            operation.points.forEachIndexed { index, point ->
                if (index == 0) pen.moveTo(point.x, point.y) else pen.lineTo(point.x, point.y)
            }
            graphics.draw(pen)
        }
        is DesktopEditOperation.RectStroke -> {
            graphics.color = operation.color.toAwtColor()
            graphics.stroke = java.awt.BasicStroke(
                operation.strokeWidth.coerceAtLeast(1f),
                java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND,
            )
            val cx = (operation.start.x + operation.end.x) / 2f
            val cy = (operation.start.y + operation.end.y) / 2f
            val halfWidth = abs(operation.end.x - operation.start.x) / 2f * operation.scale
            val halfHeight = abs(operation.end.y - operation.start.y) / 2f * operation.scale
            val previousTransform = graphics.transform
            graphics.rotate(Math.toRadians(operation.rotation.toDouble()), cx.toDouble(), cy.toDouble())
            graphics.draw(
                java.awt.geom.Rectangle2D.Float(cx - halfWidth, cy - halfHeight, halfWidth * 2f, halfHeight * 2f),
            )
            graphics.transform = previousTransform
        }
        is DesktopEditOperation.OvalStroke -> {
            graphics.color = operation.color.toAwtColor()
            graphics.stroke = java.awt.BasicStroke(
                operation.strokeWidth.coerceAtLeast(1f),
                java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND,
            )
            val cx = (operation.start.x + operation.end.x) / 2f
            val cy = (operation.start.y + operation.end.y) / 2f
            val halfWidth = abs(operation.end.x - operation.start.x) / 2f * operation.scale
            val halfHeight = abs(operation.end.y - operation.start.y) / 2f * operation.scale
            val previousTransform = graphics.transform
            graphics.rotate(Math.toRadians(operation.rotation.toDouble()), cx.toDouble(), cy.toDouble())
            graphics.draw(
                java.awt.geom.Ellipse2D.Float(cx - halfWidth, cy - halfHeight, halfWidth * 2f, halfHeight * 2f),
            )
            graphics.transform = previousTransform
        }
        is DesktopEditOperation.ArrowStroke -> {
            graphics.color = operation.color.toAwtColor()
            graphics.stroke = java.awt.BasicStroke(
                operation.strokeWidth.coerceAtLeast(1f),
                java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND,
            )
            graphics.draw(
                java.awt.geom.Line2D.Float(
                    operation.start.x,
                    operation.start.y,
                    operation.end.x,
                    operation.end.y,
                ),
            )
            arrowHeadPoints(operation.start, operation.end, operation.strokeWidth)?.let { (left, right) ->
                graphics.draw(java.awt.geom.Line2D.Float(operation.end.x, operation.end.y, left.x, left.y))
                graphics.draw(java.awt.geom.Line2D.Float(operation.end.x, operation.end.y, right.x, right.y))
            }
        }
        is DesktopEditOperation.MosaicStroke -> {
            if (mosaicSource == null || operation.points.isEmpty()) return
            val area = java.awt.geom.Area()
            operation.points.forEach { point ->
                area.add(
                    java.awt.geom.Area(
                        java.awt.geom.Ellipse2D.Float(
                            point.x - operation.radius,
                            point.y - operation.radius,
                            operation.radius * 2f,
                            operation.radius * 2f,
                        ),
                    ),
                )
            }
            val previousClip = graphics.clip
            graphics.clip = area
            graphics.drawImage(mosaicSource, 0, 0, null)
            graphics.clip = previousClip
        }
        is DesktopEditOperation.TextOverlay -> {
            graphics.color = operation.color.toAwtColor()
            graphics.font = java.awt.Font(
                java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD,
                (operation.fontSize * operation.scale).roundToInt().coerceAtLeast(1),
            )
            val previousTransform = graphics.transform
            graphics.rotate(
                Math.toRadians(operation.rotation.toDouble()),
                operation.position.x.toDouble(),
                operation.position.y.toDouble(),
            )
            val metrics = graphics.fontMetrics
            val lines = operation.text.lines()
            val totalHeight = metrics.height * lines.size
            var baseline = operation.position.y - totalHeight / 2f + metrics.ascent
            lines.forEach { line ->
                val lineWidth = metrics.stringWidth(line)
                graphics.drawString(line, operation.position.x - lineWidth / 2f, baseline)
                baseline += metrics.height
            }
            graphics.transform = previousTransform
        }
    }
}

/** 形状操作的对角线跨度，用于过滤误触产生的过小图形。 */
private fun desktopShapeSpan(operation: DesktopEditOperation): Float {
    return when (operation) {
        is DesktopEditOperation.RectStroke -> (operation.end - operation.start).getDistance()
        is DesktopEditOperation.OvalStroke -> (operation.end - operation.start).getDistance()
        is DesktopEditOperation.ArrowStroke -> (operation.end - operation.start).getDistance()
        else -> Float.MAX_VALUE
    }
}

/** 箭头两条尾翼端点；线段过短时返回 null。 */
private fun arrowHeadPoints(start: Offset, end: Offset, strokeWidth: Float): Pair<Offset, Offset>? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy)
    if (length < 1f) return null
    val angle = atan2(dy, dx)
    val headLength = min(length * 0.3f, max(strokeWidth * 6f, 12f))
    val spread = Math.toRadians(25.0).toFloat()

    fun wing(delta: Float) = Offset(
        x = end.x - headLength * cos(angle + delta),
        y = end.y - headLength * sin(angle + delta),
    )

    return wing(spread) to wing(-spread)
}

/** 与 imagePointFromViewport 类似，但允许拖动越界时把点钳制到图片边缘。 */
private fun clampedImagePointFromViewport(
    position: Offset,
    imageRect: Rect,
    imageWidth: Int,
    imageHeight: Int,
): Offset? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    return Offset(
        x = ((position.x - imageRect.left) / imageRect.width * imageWidth).coerceIn(0f, imageWidth.toFloat()),
        y = ((position.y - imageRect.top) / imageRect.height * imageHeight).coerceIn(0f, imageHeight.toFloat()),
    )
}

/** 追加马赛克涂抹点；两点间距过大时插入中间点，避免快速拖动产生空隙。 */
private fun appendMosaicPoint(points: List<Offset>, point: Offset, radius: Float): List<Offset> {
    val last = points.lastOrNull() ?: return listOf(point)
    val distance = (point - last).getDistance()
    val step = (radius * 0.5f).coerceAtLeast(1f)
    if (distance <= step) return points + point
    val result = points.toMutableList()
    val segments = (distance / step).toInt()
    for (index in 1..segments) {
        val fraction = index / (segments + 1f)
        result += Offset(
            x = last.x + (point.x - last.x) * fraction,
            y = last.y + (point.y - last.y) * fraction,
        )
    }
    result += point
    return result
}

// ==================== 选中 / 变换辅助（坐标均为原图像素，容差由调用方按视口换算） ====================

/** 缩放手柄绘制半径（视口像素）。 */
private const val DESKTOP_HANDLE_RADIUS = 7f

/** 旋转手柄绘制半径（视口像素）。 */
private const val DESKTOP_ROTATE_HANDLE_RADIUS = 16f

/** 缩放手柄命中半径（视口像素）。 */
private const val DESKTOP_HANDLE_HIT_RADIUS = 14f

/** 旋转手柄命中半径（视口像素）。 */
private const val DESKTOP_ROTATE_HIT_RADIUS = 22f

/** 图形边缘命中容差（视口像素）。 */
private const val DESKTOP_HIT_TOLERANCE = 14f

/** 文字选择框内边距（视口像素）。 */
private const val DESKTOP_TEXT_SELECTION_PADDING = 10f

private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** 点到线段的最短距离。 */
private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val lengthSquared = ab.x * ab.x + ab.y * ab.y
    if (lengthSquared < 1e-6f) return distanceBetween(p, a)
    val t = ((ap.x * ab.x + ap.y * ab.y) / lengthSquared).coerceIn(0f, 1f)
    val projection = Offset(a.x + t * ab.x, a.y + t * ab.y)
    return distanceBetween(p, projection)
}

/**
 * 计算矩形/椭圆经缩放与旋转后的四个角点。
 * 返回顺序：左上、右上、右下、左下。
 */
private fun transformedCorners(
    start: Offset,
    end: Offset,
    scale: Float,
    rotationDeg: Float,
): List<Offset> {
    val cx = (start.x + end.x) / 2f
    val cy = (start.y + end.y) / 2f
    val halfWidth = abs(end.x - start.x) / 2f * scale
    val halfHeight = abs(end.y - start.y) / 2f * scale
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    fun corner(lx: Float, ly: Float) = Offset(
        x = cx + lx * cosR - ly * sinR,
        y = cy + lx * sinR + ly * cosR,
    )

    return listOf(
        corner(-halfWidth, -halfHeight),
        corner(halfWidth, -halfHeight),
        corner(halfWidth, halfHeight),
        corner(-halfWidth, halfHeight),
    )
}

/** 判断点是否在凸四边形内（叉积同号法）。 */
private fun isPointInQuad(p: Offset, corners: List<Offset>): Boolean {
    var sign = 0
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        if (cross > 0) {
            if (sign < 0) return false
            sign = 1
        } else if (cross < 0) {
            if (sign > 0) return false
            sign = -1
        }
    }
    return true
}

/** 判断点是否位于（含容差的）旋转椭圆内。 */
private fun isPointNearEllipse(
    p: Offset,
    start: Offset,
    end: Offset,
    scale: Float,
    rotationDeg: Float,
    tolerance: Float,
): Boolean {
    val cx = (start.x + end.x) / 2f
    val cy = (start.y + end.y) / 2f
    val a = abs(end.x - start.x) / 2f * scale + tolerance
    val b = abs(end.y - start.y) / 2f * scale + tolerance
    if (a < 1f || b < 1f) return false
    val rad = Math.toRadians(-rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val dx = p.x - cx
    val dy = p.y - cy
    val lx = dx * cosR - dy * sinR
    val ly = dx * sinR + dy * cosR
    return (lx * lx) / (a * a) + (ly * ly) / (b * b) <= 1f
}

/** 用基准字号测量文本尺寸（原图像素，未含 scale）。 */
private fun measureTextOverlaySize(
    density: Density,
    textMeasurer: TextMeasurer,
    overlay: DesktopEditOperation.TextOverlay,
    viewScale: Float,
): Size {
    val layout = textMeasurer.measure(
        AnnotatedString(overlay.text),
        with(density) {
            TextStyle(
                fontSize = (overlay.fontSize * viewScale).toSp(),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
    )
    return Size(layout.size.width / viewScale, layout.size.height / viewScale)
}

/** 文字选择框四角（原图像素，含内边距、缩放与旋转）。 */
private fun textOverlayCorners(
    density: Density,
    textMeasurer: TextMeasurer,
    overlay: DesktopEditOperation.TextOverlay,
    viewScale: Float,
): List<Offset> {
    val size = measureTextOverlaySize(density, textMeasurer, overlay, viewScale)
    val padding = DESKTOP_TEXT_SELECTION_PADDING / viewScale
    val half = Offset(size.width / 2f + padding, size.height / 2f + padding)
    return transformedCorners(
        start = overlay.position - half,
        end = overlay.position + half,
        scale = overlay.scale,
        rotationDeg = overlay.rotation,
    )
}

/**
 * 命中检测：返回原图坐标 position 下最上层可选中操作的下标，未命中返回 null。
 * 画笔与马赛克不可选中；textMode 决定检测文字还是矢量图形。
 */
private fun hitTestDesktopOperation(
    position: Offset,
    operations: List<DesktopEditOperation>,
    textMode: Boolean,
    tolerance: Float,
    density: Density,
    textMeasurer: TextMeasurer,
    viewScale: Float,
): Int? {
    for (index in operations.indices.reversed()) {
        val operation = operations[index]
        val hit = when (operation) {
            is DesktopEditOperation.RectStroke -> !textMode && isPointInQuad(
                position,
                transformedCorners(operation.start, operation.end, operation.scale, operation.rotation),
            )
            is DesktopEditOperation.OvalStroke -> !textMode && isPointNearEllipse(
                position,
                operation.start,
                operation.end,
                operation.scale,
                operation.rotation,
                tolerance,
            )
            is DesktopEditOperation.ArrowStroke -> !textMode &&
                pointToSegmentDistance(position, operation.start, operation.end) < tolerance
            is DesktopEditOperation.TextOverlay -> textMode && isPointInQuad(
                position,
                textOverlayCorners(density, textMeasurer, operation, viewScale),
            )
            else -> false
        }
        if (hit) return index
    }
    return null
}

/** 命中检测：判断原图坐标 position 命中了选中操作的哪个控制手柄。 */
private fun hitTestDesktopHandle(
    position: Offset,
    operation: DesktopEditOperation,
    handleHitRadius: Float,
    rotateHitRadius: Float,
    tolerance: Float,
    density: Density,
    textMeasurer: TextMeasurer,
    viewScale: Float,
): DesktopDragAction {
    fun quadHandleAction(corners: List<Offset>, insideCheck: () -> Boolean): DesktopDragAction {
        return when {
            distanceBetween(position, corners[2]) < handleHitRadius -> DesktopDragAction.Scale
            distanceBetween(position, corners[0]) < rotateHitRadius -> DesktopDragAction.Rotate
            insideCheck() -> DesktopDragAction.Move
            else -> DesktopDragAction.None
        }
    }

    return when (operation) {
        is DesktopEditOperation.RectStroke -> {
            val corners = transformedCorners(operation.start, operation.end, operation.scale, operation.rotation)
            quadHandleAction(corners) { isPointInQuad(position, corners) }
        }
        is DesktopEditOperation.OvalStroke -> {
            val corners = transformedCorners(operation.start, operation.end, operation.scale, operation.rotation)
            quadHandleAction(corners) {
                isPointNearEllipse(position, operation.start, operation.end, operation.scale, operation.rotation, tolerance)
            }
        }
        is DesktopEditOperation.ArrowStroke -> when {
            distanceBetween(position, operation.start) < handleHitRadius -> DesktopDragAction.ArrowStart
            distanceBetween(position, operation.end) < handleHitRadius -> DesktopDragAction.ArrowEnd
            pointToSegmentDistance(position, operation.start, operation.end) < tolerance -> DesktopDragAction.Move
            else -> DesktopDragAction.None
        }
        is DesktopEditOperation.TextOverlay -> {
            val corners = textOverlayCorners(density, textMeasurer, operation, viewScale)
            quadHandleAction(corners) { isPointInQuad(position, corners) }
        }
        else -> DesktopDragAction.None
    }
}

/** 根据拖拽类型更新操作的变换属性（delta 与 currentPos 均为原图像素）。 */
private fun applyDesktopDragAction(
    operation: DesktopEditOperation,
    action: DesktopDragAction,
    delta: Offset,
    currentPos: Offset,
): DesktopEditOperation {
    fun scaledBy(center: Offset, scale: Float): Float? {
        val previousDistance = distanceBetween(currentPos - delta, center)
        val currentDistance = distanceBetween(currentPos, center)
        if (previousDistance <= 1f) return null
        return (scale * currentDistance / previousDistance).coerceIn(0.3f, 5f)
    }

    fun rotatedBy(center: Offset, rotation: Float): Float {
        val previousAngle = atan2((currentPos.y - delta.y) - center.y, (currentPos.x - delta.x) - center.x)
        val currentAngle = atan2(currentPos.y - center.y, currentPos.x - center.x)
        return rotation + Math.toDegrees((currentAngle - previousAngle).toDouble()).toFloat()
    }

    return when (operation) {
        is DesktopEditOperation.RectStroke -> {
            val center = Offset((operation.start.x + operation.end.x) / 2f, (operation.start.y + operation.end.y) / 2f)
            when (action) {
                DesktopDragAction.Move -> operation.copy(start = operation.start + delta, end = operation.end + delta)
                DesktopDragAction.Scale -> scaledBy(center, operation.scale)?.let { operation.copy(scale = it) } ?: operation
                DesktopDragAction.Rotate -> operation.copy(rotation = rotatedBy(center, operation.rotation))
                else -> operation
            }
        }
        is DesktopEditOperation.OvalStroke -> {
            val center = Offset((operation.start.x + operation.end.x) / 2f, (operation.start.y + operation.end.y) / 2f)
            when (action) {
                DesktopDragAction.Move -> operation.copy(start = operation.start + delta, end = operation.end + delta)
                DesktopDragAction.Scale -> scaledBy(center, operation.scale)?.let { operation.copy(scale = it) } ?: operation
                DesktopDragAction.Rotate -> operation.copy(rotation = rotatedBy(center, operation.rotation))
                else -> operation
            }
        }
        is DesktopEditOperation.ArrowStroke -> when (action) {
            DesktopDragAction.Move -> operation.copy(start = operation.start + delta, end = operation.end + delta)
            DesktopDragAction.ArrowStart -> operation.copy(start = operation.start + delta)
            DesktopDragAction.ArrowEnd -> operation.copy(end = operation.end + delta)
            else -> operation
        }
        is DesktopEditOperation.TextOverlay -> when (action) {
            DesktopDragAction.Move -> operation.copy(position = operation.position + delta)
            DesktopDragAction.Scale -> scaledBy(operation.position, operation.scale)?.let { operation.copy(scale = it) } ?: operation
            DesktopDragAction.Rotate -> operation.copy(rotation = rotatedBy(operation.position, operation.rotation))
            else -> operation
        }
        else -> operation
    }
}

/** 视口坐标 → 原图坐标（不做边界钳制，供变换手势使用）。 */
private fun imagePointUnclamped(position: Offset, imageRect: Rect, viewScale: Float): Offset {
    return Offset(
        x = (position.x - imageRect.left) / viewScale,
        y = (position.y - imageRect.top) / viewScale,
    )
}

/** 绘制矩形/椭圆/文字选中时的边框、右下角缩放手柄与左上角旋转手柄（视口坐标）。 */
private fun DrawScope.drawDesktopSelectionHandles(corners: List<Offset>) {
    for (i in corners.indices) {
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = corners[i],
            end = corners[(i + 1) % corners.size],
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )
    }

    drawCircle(color = Color.White, radius = DESKTOP_HANDLE_RADIUS, center = corners[2])
    drawCircle(
        color = Color.Gray,
        radius = DESKTOP_HANDLE_RADIUS,
        center = corners[2],
        style = Stroke(width = 1.5f),
    )

    drawCircle(color = Color.White, radius = DESKTOP_ROTATE_HANDLE_RADIUS, center = corners[0])
    drawCircle(
        color = Color.Gray.copy(alpha = 0.4f),
        radius = DESKTOP_ROTATE_HANDLE_RADIUS,
        center = corners[0],
        style = Stroke(width = 1.5f),
    )
    drawDesktopRotateIcon(corners[0], DESKTOP_ROTATE_HANDLE_RADIUS)
}

/** 绘制箭头选中时的端点手柄（视口坐标）。 */
private fun DrawScope.drawDesktopArrowHandles(start: Offset, end: Offset) {
    drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = start,
        end = end,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
    listOf(start, end).forEach { point ->
        drawCircle(color = Color.White, radius = DESKTOP_HANDLE_RADIUS, center = point)
        drawCircle(
            color = Color.Gray,
            radius = DESKTOP_HANDLE_RADIUS,
            center = point,
            style = Stroke(width = 1.5f),
        )
    }
}

/** 在旋转手柄内绘制与应用图标库一致的对象旋转图标。 */
private fun DrawScope.drawDesktopRotateIcon(center: Offset, radius: Float) {
    val iconColor = Color(0xFF444444)
    val scale = radius / 12f

    fun px(x: Float) = center.x + (x - 12f) * scale
    fun py(y: Float) = center.y + (y - 12f) * scale

    val rotatePath = Path().apply {
        moveTo(px(6f), py(13f))
        lineTo(px(12f), py(13f))
        cubicTo(px(13.1f), py(13f), px(14f), py(13.9f), px(14f), py(15f))
        lineTo(px(14f), py(18f))
        cubicTo(px(14f), py(19.1f), px(13.1f), py(20f), px(12f), py(20f))
        lineTo(px(6f), py(20f))
        cubicTo(px(4.9f), py(20f), px(4f), py(19.1f), px(4f), py(18f))
        lineTo(px(4f), py(15f))
        cubicTo(px(4f), py(13.9f), px(4.9f), py(13f), px(6f), py(13f))
        close()
        moveTo(px(20f), py(13f))
        cubicTo(px(20f), py(8.58f), px(16.42f), py(5f), px(12f), py(5f))
        lineTo(px(10f), py(5f))
        moveTo(px(12.5f), py(2.5f))
        lineTo(px(10f), py(5f))
        lineTo(px(12.5f), py(7.5f))
    }
    drawPath(
        path = rotatePath,
        color = iconColor,
        style = Stroke(
            width = 2f * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** 加载与展示位图同尺寸的马赛克化位图，用于涂抹时的实时预览。 */
private fun loadMosaicImageBitmap(path: String, maxDimension: Int): ImageBitmap? {
    return runCatching {
        val sourceFile = localImageFile(path) ?: File(path.removePrefix("file://"))
        val source = ImageIO.read(sourceFile) ?: return@runCatching null
        val scaled = source.scaledToMaxDimension(maxDimension)
        // 预览位图可能被缩小过，按比例换算块大小以保持与保存结果一致的观感
        val previewBlock = max(
            1,
            (DESKTOP_MOSAIC_BLOCK_SIZE.toFloat() * scaled.width / source.width).roundToInt(),
        )
        scaled.mosaicized(previewBlock).toComposeImageBitmap()
    }.getOrNull()
}

/** 生成像素化（马赛克）版本：先缩小再用最近邻放大。 */
private fun BufferedImage.mosaicized(blockSize: Int): BufferedImage {
    val block = blockSize.coerceAtLeast(1)
    val smallWidth = max(1, width / block)
    val smallHeight = max(1, height / block)
    val small = BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_ARGB)
    small.createGraphics().apply {
        drawImage(this@mosaicized, 0, 0, smallWidth, smallHeight, null)
        dispose()
    }
    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    output.createGraphics().apply {
        setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )
        drawImage(small, 0, 0, width, height, null)
        dispose()
    }
    return output
}

private fun Color.toAwtColor(): java.awt.Color =
    java.awt.Color(
        red.coerceIn(0f, 1f),
        green.coerceIn(0f, 1f),
        blue.coerceIn(0f, 1f),
        alpha.coerceIn(0f, 1f),
    )

private fun previewWindowSizeForPath(path: String?): Pair<Int, Int> {
    val (imageWidth, imageHeight) = path
        ?.let(::imageDimensions)
        ?.takeIf { (width, height) -> width > 0 && height > 0 }
        ?: return 1120 to 780

    val ratio = (imageWidth.toFloat() / imageHeight.toFloat()).coerceIn(0.35f, 2.85f)
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val maxWidth = (screenSize.width * 0.78f).roundToInt().coerceIn(720, 1320)
    val maxHeight = (screenSize.height * 0.82f).roundToInt().coerceIn(560, 920)
    val minWidth = 560
    val minHeight = 480

    var width: Int
    var height: Int
    if (ratio >= 1f) {
        height = maxHeight
        width = (height * ratio).roundToInt()
        if (width > maxWidth) {
            width = maxWidth
            height = (width / ratio).roundToInt()
        }
    } else {
        height = maxHeight
        width = (height * ratio).roundToInt()
    }

    width = width.coerceIn(minWidth, maxWidth)
    height = height.coerceIn(minHeight, maxHeight)
    return width to height
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
    val source = localImageFile(sourcePath)?.takeIf { it.isFile } ?: File(sourcePath)
    if (!source.isFile) return
    val targetPath = DesktopFilePicker.pickSaveImagePath(title, source.name) ?: return
    source.copyTo(File(targetPath), overwrite = true)
}

private fun saveTransformedImageAs(
    sourcePath: String,
    transform: DesktopImageTransformState,
    title: String,
) {
    val source = localImageFile(sourcePath)?.takeIf { it.isFile } ?: return
    val targetPath = DesktopFilePicker.pickSaveImagePath(title, source.name) ?: return
    val target = File(targetPath)

    if (transform.isIdentity()) {
        source.copyTo(target, overwrite = true)
        return
    }

    val sourceImage = ImageIO.read(source) ?: return
    val transformed = sourceImage.transformed(transform)
    val requestedFormat = imageWriteFormat(target)
    val imageToWrite = if (requestedFormat == "jpeg") transformed.withWhiteBackground() else transformed
    if (!ImageIO.write(imageToWrite, requestedFormat, target) && requestedFormat != "png") {
        val pngTarget = File(target.parentFile, "${target.nameWithoutExtension}.png")
        ImageIO.write(transformed, "png", pngTarget)
    }
}

private fun DesktopImageTransformState.isIdentity(): Boolean {
    return normalizedRotation() == 0 && !flipHorizontal && !flipVertical
}

private fun DesktopImageTransformState.normalizedRotation(): Int {
    return ((rotation.roundToInt() % 360) + 360) % 360
}

private fun BufferedImage.transformed(transform: DesktopImageTransformState): BufferedImage {
    val rotation = transform.normalizedRotation()
    val swapsSides = rotation == 90 || rotation == 270
    val targetWidth = if (swapsSides) height else width
    val targetHeight = if (swapsSides) width else height
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = target.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val affineTransform = AffineTransform().apply {
            translate(targetWidth / 2.0, targetHeight / 2.0)
            rotate(Math.toRadians(rotation.toDouble()))
            scale(
                if (transform.flipHorizontal) -1.0 else 1.0,
                if (transform.flipVertical) -1.0 else 1.0,
            )
            translate(-width / 2.0, -height / 2.0)
        }
        graphics.drawImage(this, affineTransform, null)
    } finally {
        graphics.dispose()
    }
    return target
}

private fun BufferedImage.withWhiteBackground(): BufferedImage {
    val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = target.createGraphics()
    try {
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return target
}

private fun imageWriteFormat(file: File): String {
    return when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "jpeg"
        "bmp" -> "bmp"
        else -> "png"
    }
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

private fun openImageForEdit(path: String) {
    val file = localImageFile(path)?.takeIf { it.isFile } ?: return
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.EDIT)) {
            desktop.edit(file)
        } else {
            desktop.open(file)
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
