package com.example.livewallpaper.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.example.livewallpaper.core.design.theme.AppDesignTheme
import com.example.livewallpaper.core.design.theme.AppDesignThemeStyle
import com.example.livewallpaper.di.appModule
import com.example.livewallpaper.di.platformModule
import com.example.livewallpaper.desktop.paint.AiPaintWorkspace
import com.example.livewallpaper.desktop.paint.DesktopPaintSidebarSection
import com.example.livewallpaper.desktop.paint.DesktopPaintViewModel
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.state.SettingsEvent
import com.example.livewallpaper.feature.dynamicwallpaper.presentation.viewmodel.SettingsViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

fun main() {
    startKoin {
        modules(appModule, platformModule)
    }

    val wallpaperController = WindowsWallpaperController()
    val runtimeSettings = DesktopRuntimeSettings()

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var latestConfig by remember { mutableStateOf(WallpaperConfig()) }
        val strings = desktopStringsFor(latestConfig.languageTag)
        val trayStrings = desktopStringsFor("en")
        val trayState = rememberTrayState()

        fun startSlideshow(config: WallpaperConfig) {
            runtimeSettings.setSlideshowRunning(config.imageUris.any { File(it).isFile })
            wallpaperController.start(config)
        }

        fun stopSlideshow() {
            runtimeSettings.setSlideshowRunning(false)
            wallpaperController.stop()
        }

        Tray(
            state = trayState,
            icon = rememberTrayIconPainter(),
            menu = {
                Item(trayStrings.openWindow, onClick = { isWindowVisible = true })
                Separator()
                Item(trayStrings.startSlideshow, onClick = { startSlideshow(latestConfig) })
                Item(trayStrings.stopSlideshow, onClick = { stopSlideshow() })
                Separator()
                Item(
                    trayStrings.quit,
                    onClick = {
                        wallpaperController.close()
                        exitApplication()
                    }
                )
            }
        )

        if (isWindowVisible) {
            Window(
                onCloseRequest = { isWindowVisible = false },
                title = strings.appTitle,
                icon = rememberAppIconPainter(),
                state = rememberWindowState(width = 1240.dp, height = 820.dp)
            ) {
                DesktopApp(
                    wallpaperController = wallpaperController,
                    runtimeSettings = runtimeSettings,
                    onConfigChanged = { latestConfig = it }
                )
            }
        }
    }
}

private enum class DesktopSection {
    WALLPAPERS,
    AI_PAINT,
}

private data class WallpaperMenuState(
    val path: String,
    val position: IntOffset,
    val canSetWallpaper: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

@Composable
private fun DesktopApp(
    wallpaperController: DesktopWallpaperController,
    runtimeSettings: DesktopRuntimeSettings,
    onConfigChanged: (WallpaperConfig) -> Unit,
) {
    val viewModel = remember { GlobalContext.get().get<SettingsViewModel>() }
    val uiState by viewModel.uiState.collectAsState()
    val wallpaperStatus by wallpaperController.status.collectAsState()
    val strings = desktopStringsFor(uiState.config.languageTag)
    var restoreAttempted by remember { mutableStateOf(false) }

    fun startSlideshow(config: WallpaperConfig) {
        runtimeSettings.setSlideshowRunning(config.imageUris.any { File(it).isFile })
        wallpaperController.start(config)
    }

    fun stopSlideshow() {
        runtimeSettings.setSlideshowRunning(false)
        wallpaperController.stop()
    }

    LaunchedEffect(uiState.config) {
        onConfigChanged(uiState.config)
    }

    LaunchedEffect(uiState.config.launchAtStartup) {
        runtimeSettings.applyLaunchAtStartup(uiState.config.launchAtStartup)
    }

    LaunchedEffect(uiState.config) {
        if (!restoreAttempted) {
            restoreAttempted = true
            if (uiState.config.restoreSlideshowOnLaunch && runtimeSettings.wasSlideshowRunning()) {
                startSlideshow(uiState.config)
            }
        } else if (wallpaperStatus is DesktopWallpaperStatus.Running) {
            startSlideshow(uiState.config)
        }
    }

    CompositionLocalProvider(LocalDesktopStrings provides strings) {
        DesktopLiveWallpaperTheme(themeMode = uiState.config.themeMode) {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopShell(
                    config = uiState.config,
                    status = wallpaperStatus,
                    onEvent = viewModel::onEvent,
                    onStartSlideshow = { startSlideshow(uiState.config) },
                    onStopSlideshow = ::stopSlideshow,
                    onSetCurrentWallpaper = { path ->
                        wallpaperController.setWallpaper(path, uiState.config.scaleMode)
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopLiveWallpaperTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.STARDUST -> true
        ThemeMode.CLEAR -> false
    }
    val style = when (themeMode) {
        ThemeMode.SYSTEM -> if (useDarkTheme) AppDesignThemeStyle.Dark else AppDesignThemeStyle.Fresh
        ThemeMode.LIGHT -> AppDesignThemeStyle.Fresh
        ThemeMode.DARK -> AppDesignThemeStyle.Dark
        ThemeMode.STARDUST -> AppDesignThemeStyle.Stardust
        ThemeMode.CLEAR -> AppDesignThemeStyle.Clear
    }

    AppDesignTheme(style = style, content = content)
}

@Composable
private fun DesktopShell(
    config: WallpaperConfig,
    status: DesktopWallpaperStatus,
    onEvent: (SettingsEvent) -> Unit,
    onStartSlideshow: () -> Unit,
    onStopSlideshow: () -> Unit,
    onSetCurrentWallpaper: (String) -> Unit,
) {
    val paintViewModel = remember { DesktopPaintViewModel(GlobalContext.get().get<PaintRepository>()) }
    val paintUiState by paintViewModel.uiState.collectAsState()
    var selectedSection by remember { mutableStateOf(DesktopSection.WALLPAPERS) }
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedPath by remember(config.imageUris) { mutableStateOf(config.imageUris.firstOrNull()) }
    val effectiveSelectedPath = selectedPath?.takeIf { it in config.imageUris } ?: config.imageUris.firstOrNull()

    if (showSettings) {
        SettingsDialog(
            config = config,
            onEvent = onEvent,
            onDismiss = { showSettings = false },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!sidebarCollapsed) {
            DesktopSidebar(
                selectedSection = selectedSection,
                status = status,
                paintUiState = paintUiState,
                onPaintEvent = paintViewModel::onEvent,
                onSectionSelected = { selectedSection = it },
                onOpenSettings = { showSettings = true },
            )
        }

        when (selectedSection) {
            DesktopSection.WALLPAPERS -> WallpaperWorkspace(
                config = config,
                status = status,
                selectedPath = effectiveSelectedPath,
                onSelectedPathChange = { selectedPath = it },
                onEvent = onEvent,
                onStartSlideshow = onStartSlideshow,
                onStopSlideshow = onStopSlideshow,
                onSetWallpaperPath = onSetCurrentWallpaper,
            )

            DesktopSection.AI_PAINT -> AiPaintWorkspace(
                viewModel = paintViewModel,
                isSidebarCollapsed = sidebarCollapsed,
                onToggleSidebar = { sidebarCollapsed = !sidebarCollapsed },
                onAddImagesToWallpapers = { paths -> onEvent(SettingsEvent.AddImages(paths)) },
                onSetWallpaperPath = onSetCurrentWallpaper,
            )
        }
    }
}

@Composable
private fun DesktopSidebar(
    selectedSection: DesktopSection,
    status: DesktopWallpaperStatus,
    paintUiState: com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState,
    onPaintEvent: (com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent) -> Unit,
    onSectionSelected: (DesktopSection) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val strings = LocalDesktopStrings.current

    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = rememberAppIconPainter(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    text = strings.appTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            NavigationItem(
                label = strings.wallpapers,
                selected = selectedSection == DesktopSection.WALLPAPERS,
                onClick = { onSectionSelected(DesktopSection.WALLPAPERS) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationItem(
                label = strings.aiPaint,
                selected = selectedSection == DesktopSection.AI_PAINT,
                onClick = { onSectionSelected(DesktopSection.AI_PAINT) },
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedSection == DesktopSection.AI_PAINT) {
                DesktopPaintSidebarSection(
                    uiState = paintUiState,
                    onEvent = onPaintEvent,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Text(
                text = status.label(strings),
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    is DesktopWallpaperStatus.Error -> MaterialTheme.colorScheme.error
                    DesktopWallpaperStatus.Unsupported -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.settings)
            }
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun WallpaperWorkspace(
    config: WallpaperConfig,
    status: DesktopWallpaperStatus,
    selectedPath: String?,
    onSelectedPathChange: (String) -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    onStartSlideshow: () -> Unit,
    onStopSlideshow: () -> Unit,
    onSetWallpaperPath: (String) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var displayedUris by remember(config.imageUris) { mutableStateOf(config.imageUris) }
    var contextMenu by remember { mutableStateOf<WallpaperMenuState?>(null) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var isDragOver by remember { mutableStateOf(false) }
    val activeWallpaperPath = status.currentPathOrNull()
    val lazyGridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        displayedUris = displayedUris.move(from.index, to.index)
    }
    val dropTarget = remember(config.imageUris) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragOver = false
                val droppedPaths = event.dragData().imagePathsFromDrop(config.imageUris)
                if (droppedPaths.isNotEmpty()) {
                    onEvent(SettingsEvent.AddImages(droppedPaths))
                    return true
                }
                return false
            }
        }
    }

    fun commitOrder(uris: List<String>) {
        displayedUris = uris
        onEvent(SettingsEvent.UpdateImageOrder(uris))
    }

    LaunchedEffect(config.imageUris) {
        displayedUris = config.imageUris
        selectedPaths = selectedPaths.filterTo(mutableSetOf()) { it in config.imageUris }
        if (selectedPaths.isEmpty()) {
            isMultiSelectMode = false
        }
    }

    if (showDeleteSelectedDialog) {
        DeleteSelectedDialog(
            selectedCount = selectedPaths.size,
            onConfirm = {
                onEvent(SettingsEvent.RemoveImages(selectedPaths.toList()))
                selectedPaths = emptySet()
                isMultiSelectMode = false
                showDeleteSelectedDialog = false
            },
            onDismiss = { showDeleteSelectedDialog = false },
        )
    }

    previewPath?.let { path ->
        ImagePreviewDialog(
            path = path,
            imageUris = displayedUris,
            onDismiss = { previewPath = null },
            onPrevious = {
                previewPath = displayedUris.neighborOf(path, step = -1)
            },
            onNext = {
                previewPath = displayedUris.neighborOf(path, step = 1)
            },
            onSetWallpaper = {
                onSetWallpaperPath(path)
            },
            onDelete = {
                val nextPath = displayedUris.neighborOf(path, step = 1)
                    ?: displayedUris.neighborOf(path, step = -1)
                onEvent(SettingsEvent.RemoveImage(path))
                previewPath = nextPath
            },
        )
    }

    contextMenu?.let { menu ->
        WallpaperContextMenu(
            position = menu.position,
            canSetWallpaper = menu.canSetWallpaper,
            canMoveUp = menu.canMoveUp,
            canMoveDown = menu.canMoveDown,
            onDismissRequest = { contextMenu = null },
            onSetWallpaper = {
                contextMenu = null
                onSetWallpaperPath(menu.path)
            },
            onEnterMultiSelect = {
                contextMenu = null
                isMultiSelectMode = true
                selectedPaths = setOf(menu.path)
            },
            onMoveUp = {
                contextMenu = null
                val index = displayedUris.indexOf(menu.path)
                if (index > 0) {
                    commitOrder(displayedUris.move(index, index - 1))
                }
            },
            onMoveDown = {
                contextMenu = null
                val index = displayedUris.indexOf(menu.path)
                if (index in 0 until displayedUris.lastIndex) {
                    commitOrder(displayedUris.move(index, index + 1))
                }
            },
            onRemove = {
                contextMenu = null
                onEvent(SettingsEvent.RemoveImage(menu.path))
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
                target = dropTarget,
            )
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Primary) {
                    contextMenu = null
                }
            }
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            WallpaperToolbar(
                config = config,
                isMultiSelectMode = isMultiSelectMode,
                selectedCount = selectedPaths.size,
                onEvent = onEvent,
                onExitMultiSelect = {
                    isMultiSelectMode = false
                    selectedPaths = emptySet()
                },
                onSelectAll = { selectedPaths = config.imageUris.toSet() },
                onDeselectAll = { selectedPaths = emptySet() },
                onDeleteSelected = { showDeleteSelectedDialog = true },
                onStartSlideshow = onStartSlideshow,
                onStopSlideshow = onStopSlideshow,
            )

            if (config.imageUris.isEmpty()) {
                EmptyWallpaperLibrary()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 230.dp),
                    state = lazyGridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(displayedUris, key = { _, path -> path }) { index, path ->
                        ReorderableItem(reorderState, key = path) { isDragging ->
                            WallpaperTile(
                                path = path,
                                modifier = if (isMultiSelectMode) {
                                    Modifier
                                } else {
                                    Modifier.longPressDraggableHandle(
                                        onDragStopped = {
                                            if (displayedUris != config.imageUris) {
                                                commitOrder(displayedUris)
                                            }
                                        }
                                    )
                                },
                                selected = if (isMultiSelectMode) {
                                    path in selectedPaths
                                } else {
                                    activeWallpaperPath == null && path == selectedPath
                                },
                                current = !isMultiSelectMode && activeWallpaperPath != null && path == activeWallpaperPath,
                                isMultiSelectMode = isMultiSelectMode,
                                isDragging = isDragging,
                                onClick = {
                                    contextMenu = null
                                    if (isMultiSelectMode) {
                                        selectedPaths = if (path in selectedPaths) {
                                            selectedPaths - path
                                        } else {
                                            selectedPaths + path
                                        }
                                    } else {
                                        onSelectedPathChange(path)
                                    }
                                },
                                onDoubleClick = {
                                    if (!isMultiSelectMode) {
                                        contextMenu = null
                                        previewPath = path
                                    }
                                },
                                onOpenContextMenu = { position ->
                                    contextMenu = WallpaperMenuState(
                                        path = path,
                                        position = position,
                                        canSetWallpaper = File(path).isFile,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < displayedUris.lastIndex,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isDragOver) {
            DragImportOverlay()
        }
    }
}

@Composable
private fun WallpaperToolbar(
    config: WallpaperConfig,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    onEvent: (SettingsEvent) -> Unit,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onStartSlideshow: () -> Unit,
    onStopSlideshow: () -> Unit,
) {
    val strings = LocalDesktopStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isMultiSelectMode) {
            OutlinedButton(onClick = onExitMultiSelect) {
                Text(strings.cancel)
            }
            Text(
                text = strings.multiSelectCount(selectedCount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            val isAllSelected = selectedCount == config.imageUris.size && config.imageUris.isNotEmpty()
            TextButton(
                onClick = {
                    if (isAllSelected) {
                        onDeselectAll()
                    } else {
                        onSelectAll()
                    }
                },
            ) {
                Text(if (isAllSelected) strings.cancelSelectAll else strings.selectAll)
            }
            Button(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
            ) {
                Text(strings.deleteSelected)
            }
            return@Row
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.wallpaperLibrary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.selectedCount(config.imageUris.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = {
                val picked = collectImagePaths(
                    files = pickImagePaths(strings.addImages).map(::File),
                    existingPaths = config.imageUris,
                )
                if (picked.isNotEmpty()) {
                    onEvent(SettingsEvent.AddImages(picked))
                }
            }
        ) {
            Text(strings.addImages)
        }
        ElevatedButton(onClick = onStartSlideshow, enabled = config.imageUris.isNotEmpty()) {
            Text(strings.startSlideshow)
        }
        OutlinedButton(onClick = onStopSlideshow) {
            Text(strings.stopSlideshow)
        }
    }
}

@Composable
private fun EmptyWallpaperLibrary() {
    val strings = LocalDesktopStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = strings.emptyTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.emptySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DragImportOverlay() {
    val strings = LocalDesktopStrings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Text(
                text = strings.dropImagesHint,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun WallpaperTile(
    path: String,
    modifier: Modifier = Modifier,
    selected: Boolean,
    current: Boolean,
    isMultiSelectMode: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onOpenContextMenu: (IntOffset) -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val file = remember(path) { File(path) }
    val shape = RoundedCornerShape(12.dp)
    var tilePosition by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf(false) }
    val borderColor = if (selected || current) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val containerColor = if (isDragging) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .border(1.dp, borderColor, shape)
            .onGloballyPositioned { coordinates ->
                tilePosition = coordinates.positionInWindow()
            }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) {
                    val localPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                    onOpenContextMenu(
                        IntOffset(
                            x = (tilePosition.x + localPosition.x).roundToInt(),
                            y = (tilePosition.y + localPosition.y).roundToInt(),
                        )
                    )
                }
            }
            .pointerInput(path, isMultiSelectMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                )
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Thumbnail(
                path = path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            if (!file.isFile) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = strings.missingBadge,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (current) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = strings.currentBadge,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (hovered) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .fillMaxHeight(0.48f)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (file.isFile) file.parent.orEmpty() else strings.missingFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (file.isFile) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isMultiSelectMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.dragReorderHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperContextMenu(
    position: IntOffset,
    canSetWallpaper: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismissRequest: () -> Unit,
    onSetWallpaper: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    Popup(
        popupPositionProvider = CursorPopupPositionProvider(position),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier
                .width(190.dp)
                .shadow(18.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ContextMenuAction(
                    text = strings.setCurrentWallpaper,
                    enabled = canSetWallpaper,
                    onClick = onSetWallpaper,
                )
                ContextMenuAction(
                    text = strings.multiSelect,
                    onClick = onEnterMultiSelect,
                )
                ContextMenuDivider()
                ContextMenuAction(
                    text = strings.moveUp,
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                ContextMenuAction(
                    text = strings.moveDown,
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                ContextMenuDivider()
                ContextMenuAction(
                    text = strings.remove,
                    destructive = true,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ContextMenuAction(
    text: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var hovered by remember { mutableStateOf(false) }
    val background = if (hovered && enabled) {
        if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        }
    } else {
        Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        destructive -> MaterialTheme.colorScheme.error
        hovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
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
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContextMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

private class CursorPopupPositionProvider(
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
private fun DeleteSelectedDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalDesktopStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deleteSelectedTitle) },
        text = { Text(strings.deleteSelectedMessage(selectedCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.deleteSelected)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ImagePreviewDialog(
    path: String,
    imageUris: List<String>,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetWallpaper: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val file = remember(path) { File(path) }
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            loadImageBitmap(path, maxDimension = 2600)
        }
    }
    val canNavigate = imageUris.size > 1
    val dialogState = rememberDialogState(width = 1180.dp, height = 860.dp)
    var previewScale by remember(path) { mutableStateOf(1f) }
    var previewOffset by remember(path) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember(path) { mutableStateOf(IntSize.Zero) }

    fun applyPreviewTransform(scale: Float, offset: Offset = previewOffset) {
        val boundedScale = scale.coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
        previewScale = boundedScale
        previewOffset = if (boundedScale <= 1f) {
            Offset.Zero
        } else {
            offset.clampedForScale(previewViewportSize, boundedScale)
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = file.name.ifBlank { strings.appTitle },
        icon = rememberAppIconPainter(),
        resizable = true,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (file.isFile) file.parent.orEmpty() else strings.missingFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (file.isFile) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(strings.close)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.88f))
                        .onSizeChanged { previewViewportSize = it }
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
                                onDoubleTap = {
                                    applyPreviewTransform(1f, Offset.Zero)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val previewImage = image
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = previewScale
                                    scaleY = previewScale
                                    translationX = previewOffset.x
                                    translationY = previewOffset.y
                                },
                            contentScale = ContentScale.Fit,
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.46f),
                        ) {
                            Text(
                                text = "${(previewScale * 100).roundToInt()}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Text(
                            text = strings.missingFile,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = canNavigate,
                        modifier = Modifier.width(128.dp),
                    ) {
                        Text(strings.previous)
                    }
                    OutlinedButton(
                        onClick = onNext,
                        enabled = canNavigate,
                        modifier = Modifier.width(128.dp),
                    ) {
                        Text(strings.next)
                    }
                    OutlinedButton(
                        onClick = { applyPreviewTransform(previewScale / 1.2f) },
                        enabled = image != null,
                        modifier = Modifier.width(104.dp),
                    ) {
                        Text(strings.zoomOut)
                    }
                    OutlinedButton(
                        onClick = { applyPreviewTransform(previewScale * 1.2f) },
                        enabled = image != null,
                        modifier = Modifier.width(104.dp),
                    ) {
                        Text(strings.zoomIn)
                    }
                    OutlinedButton(
                        onClick = { applyPreviewTransform(1f, Offset.Zero) },
                        enabled = image != null,
                        modifier = Modifier.width(112.dp),
                    ) {
                        Text(strings.fitWindow)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onSetWallpaper,
                        enabled = file.isFile,
                        modifier = Modifier.widthIn(min = 170.dp),
                    ) {
                        Text(strings.setCurrentWallpaper)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.width(112.dp),
                    ) {
                        Text(strings.remove)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    config: WallpaperConfig,
    onEvent: (SettingsEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalDesktopStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            SettingsContent(
                config = config,
                onEvent = onEvent,
                modifier = Modifier
                    .width(700.dp)
                    .heightIn(max = 560.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        },
    )
}

@Composable
private fun SettingsContent(
    config: WallpaperConfig,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDesktopStrings.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroupCard(title = strings.wallpaperSettings) {
            val currentIntervalSeconds = (config.interval / 1000L).coerceIn(1L, 60L).toInt()
            val intervalOptions = remember(currentIntervalSeconds) {
                (listOf(3, 5, 10, 15, 30, 60) + currentIntervalSeconds).distinct().sorted()
            }
            SettingsDropdownRow(
                title = strings.intervalSeconds,
                selectedLabel = strings.intervalValue(currentIntervalSeconds),
                options = intervalOptions,
                optionLabel = { strings.intervalValue(it) },
                onOptionSelected = { onEvent(SettingsEvent.UpdateInterval(it * 1000L)) },
            )
            SettingsItemDivider()
            SettingsDropdownRow(
                title = strings.scaleMode,
                selectedLabel = config.scaleMode.label(strings),
                options = ScaleMode.entries,
                optionLabel = { it.label(strings) },
                onOptionSelected = { onEvent(SettingsEvent.UpdateScaleMode(it)) },
            )
            SettingsItemDivider()
            SettingsDropdownRow(
                title = strings.playMode,
                selectedLabel = config.playMode.label(strings),
                options = PlayMode.entries,
                optionLabel = { it.label(strings) },
                onOptionSelected = { onEvent(SettingsEvent.UpdatePlayMode(it)) },
            )
        }

        SettingsGroupCard(title = strings.appearanceSettings) {
            SettingsDropdownRow(
                title = strings.theme,
                selectedLabel = config.themeMode.label(strings),
                options = ThemeMode.entries,
                optionLabel = { it.label(strings) },
                onOptionSelected = { onEvent(SettingsEvent.UpdateThemeMode(it)) },
            )
            SettingsItemDivider()
            val languageOptions = listOf<String?>(null, "en", "zh")
            SettingsDropdownRow(
                title = strings.language,
                selectedLabel = config.languageTag.languageLabel(strings),
                options = languageOptions,
                optionLabel = { it.languageLabel(strings) },
                onOptionSelected = { onEvent(SettingsEvent.UpdateLanguage(it)) },
            )
        }

        SettingsGroupCard(title = strings.desktopBehavior) {
            SwitchSettingRow(
                title = strings.launchAtStartup,
                description = strings.launchAtStartupDescription,
                checked = config.launchAtStartup,
                onCheckedChange = { onEvent(SettingsEvent.UpdateLaunchAtStartup(it)) },
            )
            SettingsItemDivider()
            SwitchSettingRow(
                title = strings.restoreSlideshowOnLaunch,
                description = strings.restoreSlideshowOnLaunchDescription,
                checked = config.restoreSlideshowOnLaunch,
                onCheckedChange = { onEvent(SettingsEvent.UpdateRestoreSlideshowOnLaunch(it)) },
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            content()
        }
    }
}

@Composable
private fun SettingsItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
private fun <T> SettingsDropdownRow(
    title: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownWidth = 172.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
        )
        Box {
            SettingsDropdownAnchor(
                label = selectedLabel,
                expanded = expanded,
                width = dropdownWidth,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(dropdownWidth),
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
            ) {
                options.forEach { option ->
                    val label = optionLabel(option)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (label == selectedLabel) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onOptionSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SettingsDropdownAnchor(
    label: String,
    expanded: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (expanded || hovered) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.86f)
    }

    Row(
        modifier = Modifier
            .height(36.dp)
            .width(width)
            .clip(shape)
            .background(Color.Transparent)
            .border(1.dp, borderColor, shape)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsChevron(
            expanded = expanded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsChevron(
    expanded: Boolean,
    color: Color,
) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val strokeWidth = 2.dp.toPx()
        val leftX = size.width * 0.24f
        val middleX = size.width * 0.5f
        val rightX = size.width * 0.76f
        val upperY = size.height * 0.38f
        val lowerY = size.height * 0.62f
        if (expanded) {
            drawLine(
                color = color,
                start = Offset(leftX, lowerY),
                end = Offset(middleX, upperY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(middleX, upperY),
                end = Offset(rightX, lowerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = color,
                start = Offset(leftX, upperY),
                end = Offset(middleX, lowerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(middleX, lowerY),
                end = Offset(rightX, upperY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DesktopSettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DesktopSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    val offsetX = if (checked) 25.dp else 3.dp

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .size(22.dp)
                .shadow(
                    elevation = if (checked) 3.dp else 4.dp,
                    shape = RoundedCornerShape(999.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(999.dp))
                .background(thumbColor),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun OptionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        hovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(38.dp)
            .widthIn(min = 78.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun Thumbnail(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = ThumbnailMemoryCache.get(path), path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                loadImageBitmap(path)?.also { ThumbnailMemoryCache.put(path, it) }
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Text(
                text = LocalDesktopStrings.current.missingFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private object ThumbnailMemoryCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(path: String): ImageBitmap? = cache[path]

    fun put(path: String, bitmap: ImageBitmap) {
        cache[path] = bitmap
    }
}

private fun loadImageBitmap(path: String): ImageBitmap? {
    return loadImageBitmap(path, maxDimension = 900)
}

private const val PREVIEW_MIN_SCALE = 0.25f

private const val PREVIEW_MAX_SCALE = 6f

private fun Offset.clampedForScale(viewportSize: IntSize, scale: Float): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = viewportSize.width * (scale - 1f) / 2f
    val maxY = viewportSize.height * (scale - 1f) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

private fun loadImageBitmap(path: String, maxDimension: Int): ImageBitmap? {
    return runCatching {
        val source = ImageIO.read(File(path)) ?: return@runCatching null
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
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return target
}

private fun pickImagePaths(title: String): List<String> {
    return DesktopImageFilePicker.pickImagePaths(title, ::isSupportedImageName)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragData.imagePathsFromDrop(existingPaths: List<String>): List<String> {
    if (this !is DragData.FilesList) return emptyList()
    val droppedFiles = readFiles().mapNotNull { uriString ->
        runCatching {
            val uri = URI(uriString)
            if (uri.scheme.equals("file", ignoreCase = true)) File(uri) else File(uriString)
        }.getOrNull()
    }
    return collectImagePaths(droppedFiles, existingPaths)
}

private fun collectImagePaths(files: List<File>, existingPaths: List<String>): List<String> {
    val existing = existingPaths.mapTo(mutableSetOf()) { normalizePath(it) }
    val collected = linkedSetOf<String>()
    files.forEach { file ->
        collectImageFiles(file).forEach { imageFile ->
            val normalized = normalizePath(imageFile.absolutePath)
            if (normalized !in existing && normalized !in collected) {
                collected += normalized
            }
        }
    }
    return collected.toList()
}

private fun collectImageFiles(file: File): Sequence<File> {
    return when {
        file.isFile && isSupportedImageName(file.name) -> sequenceOf(file)
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile && isSupportedImageName(it.name) }
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

private fun normalizePath(path: String): String {
    return runCatching { File(path).canonicalPath }.getOrElse { path }
}

private fun List<String>.neighborOf(path: String, step: Int): String? {
    if (isEmpty()) return null
    val index = indexOf(path)
    if (index < 0 || size == 1) return null
    val nextIndex = (index + step).floorMod(size)
    return getOrNull(nextIndex)
}

private fun Int.floorMod(modulus: Int): Int {
    return ((this % modulus) + modulus) % modulus
}

private fun List<String>.move(fromIndex: Int, toIndex: Int): List<String> {
    if (fromIndex !in indices || toIndex !in indices) return this
    val copy = toMutableList()
    val item = copy.removeAt(fromIndex)
    copy.add(toIndex, item)
    return copy
}

private fun DesktopWallpaperStatus.label(strings: DesktopStrings): String {
    return when (this) {
        DesktopWallpaperStatus.Idle -> strings.statusIdle
        DesktopWallpaperStatus.Unsupported -> strings.statusUnsupported
        is DesktopWallpaperStatus.Current -> strings.currentWallpaper(File(currentPath).name)
        is DesktopWallpaperStatus.Running -> "${strings.statusRunning}: ${File(currentPath).name}"
        is DesktopWallpaperStatus.Error -> "${strings.statusError}: $message"
    }
}

private fun DesktopWallpaperStatus.currentPathOrNull(): String? {
    return when (this) {
        is DesktopWallpaperStatus.Current -> currentPath
        is DesktopWallpaperStatus.Running -> currentPath
        DesktopWallpaperStatus.Idle,
        DesktopWallpaperStatus.Unsupported,
        is DesktopWallpaperStatus.Error -> null
    }
}

private fun ThemeMode.label(strings: DesktopStrings): String {
    return when (this) {
        ThemeMode.SYSTEM -> strings.system
        ThemeMode.LIGHT -> strings.light
        ThemeMode.DARK -> strings.dark
        ThemeMode.STARDUST -> strings.stardust
        ThemeMode.CLEAR -> strings.clear
    }
}

private fun ScaleMode.label(strings: DesktopStrings): String {
    return when (this) {
        ScaleMode.CENTER_CROP -> strings.centerCrop
        ScaleMode.FIT_CENTER -> strings.fitCenter
    }
}

private fun PlayMode.label(strings: DesktopStrings): String {
    return when (this) {
        PlayMode.SEQUENTIAL -> strings.sequential
        PlayMode.RANDOM -> strings.random
    }
}

private fun String?.languageLabel(strings: DesktopStrings): String {
    return when (this) {
        null -> strings.followSystem
        "en" -> strings.english
        "zh" -> strings.chinese
        else -> strings.followSystem
    }
}

@Composable
private fun rememberTrayIconPainter(): Painter {
    return rememberAppIconPainter()
}

@Composable
private fun rememberAppIconPainter(): Painter {
    return remember {
        val stream = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icons/app.png")) {
            "Missing desktop app icon resource"
        }
        BitmapPainter(ImageIO.read(stream).toComposeImageBitmap())
    }
}
