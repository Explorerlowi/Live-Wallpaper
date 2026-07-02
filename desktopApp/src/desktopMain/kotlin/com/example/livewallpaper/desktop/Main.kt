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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.livewallpaper.core.design.theme.AppDesignTheme
import com.example.livewallpaper.core.design.theme.AppDesignThemeStyle
import com.example.livewallpaper.core.platform.DesktopAiPaintStoragePaths
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
import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.awt.Desktop
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.LinkedHashMap
import javax.imageio.ImageIO
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

private class DesktopSingleInstanceController(
    private val lockFileChannel: FileChannel,
    private val lock: FileLock,
    private val portFile: File,
) : AutoCloseable {
    @Volatile
    var onShowRequested: (() -> Unit)? = null

    @Volatile
    private var isRunning = true

    private var serverSocket: ServerSocket? = null

    init {
        startRequestServer()
    }

    private fun startRequestServer() {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        serverSocket = server
        portFile.writeText(server.localPort.toString())
        Thread(
            {
                while (isRunning) {
                    runCatching {
                        server.accept().use { socket ->
                            socket.getInputStream().read(ByteArray(8))
                        }
                        EventQueue.invokeLater {
                            onShowRequested?.invoke()
                        }
                    }.onFailure { error ->
                        if (isRunning && error !is java.net.SocketException) {
                            error.printStackTrace()
                        }
                    }
                }
            },
            "LiveWallpaperSingleInstance",
        ).apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        isRunning = false
        runCatching { serverSocket?.close() }
        runCatching { portFile.delete() }
        runCatching { lock.release() }
        runCatching { lockFileChannel.close() }
    }

    companion object {
        private const val DIRECTORY_NAME = "live-wallpaper-desktop-single-instance"
        private const val LOCK_FILE_NAME = "app.lock"
        private const val PORT_FILE_NAME = "request.port"

        fun acquire(): DesktopSingleInstanceController? {
            val directory = File(System.getProperty("java.io.tmpdir"), DIRECTORY_NAME).apply { mkdirs() }
            val lockFile = File(directory, LOCK_FILE_NAME)
            val portFile = File(directory, PORT_FILE_NAME)
            val lockFileChannel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                lockFileChannel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                runCatching { lockFileChannel.close() }
                notifyRunningInstance(portFile)
                return null
            }
            return runCatching {
                DesktopSingleInstanceController(lockFileChannel, lock, portFile)
            }.getOrElse {
                runCatching { lock.release() }
                runCatching { lockFileChannel.close() }
                null
            }
        }

        private fun notifyRunningInstance(portFile: File) {
            val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 600)
                    socket.getOutputStream().write(1)
                }
            }
        }
    }
}

fun main() {
    val singleInstanceController = DesktopSingleInstanceController.acquire() ?: return

    startKoin {
        modules(appModule, platformModule)
    }

    val wallpaperController = WindowsWallpaperController()
    val runtimeSettings = DesktopRuntimeSettings()

    try {
        application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var latestConfig by remember { mutableStateOf(WallpaperConfig()) }
        val strings = desktopStringsFor(latestConfig.languageTag)
        val wallpaperStatus by wallpaperController.status.collectAsState()
        val trayStrings = strings
        val canStartSlideshow = latestConfig.imageUris.any { File(it).isFile }
        val isSlideshowRunning = wallpaperStatus is DesktopWallpaperStatus.Running
        val mainWindowState = rememberWindowState(width = 1240.dp, height = 820.dp)
        var focusRequestSerial by remember { mutableStateOf(0) }

        fun showMainWindow() {
            isWindowVisible = true
            mainWindowState.isMinimized = false
            focusRequestSerial += 1
        }

        DisposableEffect(singleInstanceController) {
            singleInstanceController.onShowRequested = ::showMainWindow
            onDispose {
                singleInstanceController.onShowRequested = null
            }
        }

        fun startSlideshow(config: WallpaperConfig) {
            runtimeSettings.setSlideshowRunning(config.imageUris.any { File(it).isFile })
            wallpaperController.start(config)
        }

        fun stopSlideshow() {
            runtimeSettings.setSlideshowRunning(false)
            wallpaperController.stop()
        }

        val trayController = rememberDesktopTrayController(
            tooltip = trayStrings.appTitle,
            onOpenWindow = ::showMainWindow,
            onExit = {
                wallpaperController.close()
                exitApplication()
            }
        )
        LaunchedEffect(trayController, trayStrings, canStartSlideshow, isSlideshowRunning, latestConfig) {
            trayController.updateMenu(
                strings = trayStrings,
                canStartSlideshow = canStartSlideshow,
                isSlideshowRunning = isSlideshowRunning,
                onStartSlideshow = { startSlideshow(latestConfig) },
                onStopSlideshow = { stopSlideshow() },
            )
        }

        if (isWindowVisible) {
            Window(
                onCloseRequest = { isWindowVisible = false },
                title = strings.appTitle,
                icon = rememberAppIconPainter(),
                state = mainWindowState,
            ) {
                val mainWindow = window
                LaunchedEffect(focusRequestSerial) {
                    if (focusRequestSerial > 0) {
                        mainWindowState.isMinimized = false
                        mainWindow.toFront()
                        mainWindow.requestFocus()
                    }
                }
                DesktopApp(
                    wallpaperController = wallpaperController,
                    runtimeSettings = runtimeSettings,
                    onConfigChanged = { latestConfig = it },
                    onPaintGenerationSuccess = { imageCount ->
                        if (latestConfig.paintGenerationSuccessNotification) {
                            trayController.displayMessage(
                                title = strings.paintGenerationSuccessTitle,
                                message = strings.paintGenerationSuccessMessage(imageCount),
                            )
                        }
                    },
                )
            }
        }
    }
    } finally {
        singleInstanceController.close()
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
    onPaintGenerationSuccess: (Int) -> Unit,
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
                    runtimeSettings = runtimeSettings,
                    onEvent = viewModel::onEvent,
                    onStartSlideshow = { startSlideshow(uiState.config) },
                    onStopSlideshow = ::stopSlideshow,
                    onSetCurrentWallpaper = { path ->
                        wallpaperController.setWallpaper(path, uiState.config.scaleMode)
                    },
                    onPaintGenerationSuccess = onPaintGenerationSuccess,
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
    runtimeSettings: DesktopRuntimeSettings,
    onEvent: (SettingsEvent) -> Unit,
    onStartSlideshow: () -> Unit,
    onStopSlideshow: () -> Unit,
    onSetCurrentWallpaper: (String) -> Unit,
    onPaintGenerationSuccess: (Int) -> Unit,
) {
    val paintViewModel = remember { DesktopPaintViewModel(GlobalContext.get().get<PaintRepository>()) }
    val paintUiState by paintViewModel.uiState.collectAsState()
    var selectedSection by remember { mutableStateOf(DesktopSection.WALLPAPERS) }
    var sidebarCollapsed by remember { mutableStateOf(runtimeSettings.isAiPaintSidebarCollapsed()) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedPath by remember(config.imageUris) { mutableStateOf(config.imageUris.firstOrNull()) }
    val effectiveSelectedPath = selectedPath?.takeIf { it in config.imageUris } ?: config.imageUris.firstOrNull()

    LaunchedEffect(paintViewModel, config.paintGenerationSuccessNotification) {
        paintViewModel.generationSuccessEvent.collect { event ->
            if (config.paintGenerationSuccessNotification) {
                onPaintGenerationSuccess(event.imageCount)
            }
        }
    }

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
                onToggleSidebar = {
                    sidebarCollapsed = !sidebarCollapsed
                    runtimeSettings.setAiPaintSidebarCollapsed(sidebarCollapsed)
                },
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
        WallpaperImagePreviewWindow(
            initialPath = path,
            imageUris = displayedUris,
            onDismiss = { previewPath = null },
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
private fun WallpaperImagePreviewWindow(
    initialPath: String,
    imageUris: List<String>,
    onDismiss: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val paths = remember(imageUris, initialPath) {
        imageUris.ifEmpty { listOf(initialPath) }
    }
    if (paths.isEmpty()) {
        onDismiss()
        return
    }
    var currentIndex by remember(paths) {
        mutableStateOf(paths.indexOf(initialPath).takeIf { it >= 0 } ?: 0)
    }
    LaunchedEffect(paths, initialPath) {
        currentIndex = paths.indexOf(initialPath).takeIf { it >= 0 } ?: 0
    }
    val path = paths[currentIndex.coerceIn(0, paths.lastIndex)]
    val file = remember(path) { File(path) }
    var displayedPath by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (image == null) {
            displayedPath = null
        }
        val loadedImage = withContext(Dispatchers.IO) {
            loadImageBitmap(path, maxDimension = 2600)
        }
        displayedPath = path
        image = loadedImage
    }
    val canNavigate = paths.size > 1
    val windowState = rememberWindowState(width = 1120.dp, height = 780.dp)
    var transformStates by remember(paths) {
        mutableStateOf(paths.associateWith { WallpaperImageTransformState() })
    }
    val transform = transformStates[path] ?: WallpaperImageTransformState()
    var showControls by remember { mutableStateOf(true) }
    var previewScale by remember(path) { mutableStateOf(1f) }
    var previewOffset by remember(path) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember(path) { mutableStateOf(IntSize.Zero) }

    fun updateTransform(transform: WallpaperImageTransformState) {
        transformStates = transformStates.toMutableMap().apply {
            put(path, transform)
        }
    }

    fun navigate(delta: Int) {
        val nextIndex = (currentIndex + delta).coerceIn(0, paths.lastIndex)
        if (nextIndex != currentIndex) {
            currentIndex = nextIndex
        }
    }

    fun applyPreviewTransform(scale: Float, offset: Offset = previewOffset) {
        val boundedScale = scale.coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
        previewScale = boundedScale
        previewOffset = if (boundedScale <= 1f) {
            Offset.Zero
        } else {
            offset.clampedForScale(previewViewportSize, boundedScale)
        }
    }

    Window(
        onCloseRequest = onDismiss,
        title = strings.paintImagePreview,
        icon = rememberAppIconPainter(),
        state = windowState,
        resizable = true,
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f)),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = file.name.ifBlank { path },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = if (showControls) 8.dp else 20.dp)
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
                                onTap = { showControls = !showControls },
                                onDoubleTap = { tapOffset ->
                                    if (previewScale > 1f) {
                                        applyPreviewTransform(1f, Offset.Zero)
                                    } else {
                                        val targetScale = 2.5f
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
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val previewImage = image
                    if (previewImage != null) {
                        val visibleTransform = transformStates[displayedPath ?: path] ?: WallpaperImageTransformState()
                        val rotationCompensation = wallpaperPreviewRotationCompensation(
                            bitmap = previewImage,
                            viewportSize = previewViewportSize,
                            rotation = visibleTransform.rotation,
                        )
                        Image(
                            bitmap = previewImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val horizontalDirection = if (visibleTransform.flipHorizontal) -1f else 1f
                                    val verticalDirection = if (visibleTransform.flipVertical) -1f else 1f
                                    scaleX = previewScale * rotationCompensation * horizontalDirection
                                    scaleY = previewScale * rotationCompensation * verticalDirection
                                    translationX = previewOffset.x
                                    translationY = previewOffset.y
                                    rotationZ = visibleTransform.rotation
                                },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = strings.missingFile,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    if (displayedPath != path) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.86f),
                            )
                        }
                    }

                    if (showControls && canNavigate) {
                        WallpaperPreviewIconButton(
                            icon = Icons.Default.ChevronLeft,
                            contentDescription = strings.previous,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                            enabled = currentIndex > 0,
                            onClick = { navigate(-1) },
                        )
                    }

                    if (showControls && canNavigate) {
                        WallpaperPreviewIconButton(
                            icon = Icons.Default.ChevronRight,
                            contentDescription = strings.next,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                            enabled = currentIndex < paths.lastIndex,
                            onClick = { navigate(1) },
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
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
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Rotate90DegreesCcw,
                                contentDescription = strings.imagePreviewRotateLeft,
                                onClick = { updateTransform(transform.copy(rotation = transform.rotation - 90f)) },
                            )
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Rotate90DegreesCw,
                                contentDescription = strings.imagePreviewRotateRight,
                                onClick = { updateTransform(transform.copy(rotation = transform.rotation + 90f)) },
                            )
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Flip,
                                contentDescription = strings.imagePreviewFlipHorizontal,
                                onClick = {
                                    updateTransform(transform.copy(flipHorizontal = !transform.flipHorizontal))
                                },
                            )
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Flip,
                                contentDescription = strings.imagePreviewFlipVertical,
                                rotateIcon = 90f,
                                onClick = {
                                    updateTransform(transform.copy(flipVertical = !transform.flipVertical))
                                },
                            )
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Edit,
                                contentDescription = strings.paintEditMessage,
                                onClick = { openPreviewImageForEdit(path) },
                            )
                            WallpaperPreviewIconButton(
                                icon = Icons.Default.Download,
                                contentDescription = strings.paintSaveAs,
                                onClick = { saveWallpaperPreviewImageAs(path, transform, strings.paintSaveAs) },
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
private fun WallpaperPreviewIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private data class WallpaperImageTransformState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

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
            SettingsItemDivider()
            SwitchSettingRow(
                title = strings.paintGenerationSuccessNotification,
                description = strings.paintGenerationSuccessNotificationDescription,
                checked = config.paintGenerationSuccessNotification,
                onCheckedChange = { onEvent(SettingsEvent.UpdatePaintGenerationSuccessNotification(it)) },
            )
        }

        SettingsGroupCard(title = strings.paintStorageSettings) {
            var generatedImagesPath by remember { mutableStateOf(DesktopAiPaintStoragePaths.generatedImagesPath()) }
            var responseCachePath by remember { mutableStateOf(DesktopAiPaintStoragePaths.responseCachePath()) }
            var clipboardCachePath by remember { mutableStateOf(DesktopAiPaintStoragePaths.clipboardCachePath()) }

            PathSettingRow(
                title = strings.paintGeneratedImagesDirectory,
                path = generatedImagesPath,
                sizeProvider = { DesktopAiPaintStoragePaths.directorySize(DesktopAiPaintStoragePaths.generatedImagesDirectory()) },
                onChoose = {
                    DesktopImageFilePicker.pickDirectoryPath(strings.chooseFolder, generatedImagesPath)?.let { path ->
                        DesktopAiPaintStoragePaths.setGeneratedImagesPath(path)
                        generatedImagesPath = DesktopAiPaintStoragePaths.generatedImagesPath()
                    }
                },
                onOpen = { openDirectory(generatedImagesPath) },
                onReset = {
                    DesktopAiPaintStoragePaths.resetGeneratedImagesPath()
                    generatedImagesPath = DesktopAiPaintStoragePaths.generatedImagesPath()
                },
            )
            SettingsItemDivider()
            PathSettingRow(
                title = strings.paintResponseCacheDirectory,
                path = responseCachePath,
                sizeProvider = { DesktopAiPaintStoragePaths.directorySize(DesktopAiPaintStoragePaths.responseCacheDirectory()) },
                onChoose = {
                    DesktopImageFilePicker.pickDirectoryPath(strings.chooseFolder, responseCachePath)?.let { path ->
                        DesktopAiPaintStoragePaths.setResponseCachePath(path)
                        responseCachePath = DesktopAiPaintStoragePaths.responseCachePath()
                    }
                },
                onOpen = { openDirectory(responseCachePath) },
                onClear = { DesktopAiPaintStoragePaths.clearResponseCache() },
                onReset = {
                    DesktopAiPaintStoragePaths.resetResponseCachePath()
                    responseCachePath = DesktopAiPaintStoragePaths.responseCachePath()
                },
            )
            SettingsItemDivider()
            PathSettingRow(
                title = strings.paintClipboardCacheDirectory,
                path = clipboardCachePath,
                sizeProvider = { DesktopAiPaintStoragePaths.directorySize(DesktopAiPaintStoragePaths.clipboardCacheDirectory()) },
                onChoose = {
                    DesktopImageFilePicker.pickDirectoryPath(strings.chooseFolder, clipboardCachePath)?.let { path ->
                        DesktopAiPaintStoragePaths.setClipboardCachePath(path)
                        clipboardCachePath = DesktopAiPaintStoragePaths.clipboardCachePath()
                    }
                },
                onOpen = { openDirectory(clipboardCachePath) },
                onClear = { DesktopAiPaintStoragePaths.clearClipboardCache() },
                onReset = {
                    DesktopAiPaintStoragePaths.resetClipboardCachePath()
                    clipboardCachePath = DesktopAiPaintStoragePaths.clipboardCachePath()
                },
            )
        }

        Text(
            text = strings.appVersion(desktopAppVersion),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PathSettingRow(
    title: String,
    path: String,
    sizeProvider: () -> Long,
    onChoose: () -> Unit,
    onOpen: () -> Unit,
    onReset: () -> Unit,
    onClear: (() -> Long)? = null,
) {
    val strings = LocalDesktopStrings.current
    val scope = rememberCoroutineScope()
    var refreshToken by remember(path) { mutableStateOf(0) }
    val directorySize by produceState(initialValue = 0L, path, refreshToken) {
        value = withContext(Dispatchers.IO) { sizeProvider() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings.paintDirectorySize(formatBytes(directorySize)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    delay(220)
                    onChoose()
                }
            },
        ) {
            Text(strings.chooseFolder)
        }
        TextButton(onClick = onOpen) {
            Text(strings.paintOpenDirectory)
        }
        if (onClear != null) {
            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { onClear() }
                        refreshToken += 1
                    }
                },
            ) {
                Text(strings.paintClearCache)
            }
        }
        TextButton(onClick = onReset) {
            Text(strings.reset)
        }
    }
}

private fun openDirectory(path: String) {
    runCatching {
        val directory = File(path).absoluteFile.apply { mkdirs() }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(directory)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(java.util.Locale.US, value, units[unitIndex])
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
    private const val MAX_ENTRIES = 96
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(path: String): ImageBitmap? = cache[path]

    @Synchronized
    fun put(path: String, bitmap: ImageBitmap) {
        cache[path] = bitmap
    }
}

private fun loadImageBitmap(path: String): ImageBitmap? {
    return loadImageBitmap(path, maxDimension = 480)
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

private fun wallpaperPreviewRotationCompensation(
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
    val normalizedRotation = ((rotation.roundToInt() % 180) + 180) % 180
    if (normalizedRotation == 0) return 1f

    val rotatedFitScale = minOf(
        viewportSize.width.toFloat() / bitmap.height.toFloat(),
        viewportSize.height.toFloat() / bitmap.width.toFloat(),
    )
    if (fitScale <= 0f || rotatedFitScale <= 0f) return 1f
    return (rotatedFitScale / fitScale).coerceAtMost(1f)
}

private fun saveWallpaperPreviewImageAs(
    sourcePath: String,
    transform: WallpaperImageTransformState,
    title: String,
) {
    val source = File(sourcePath).takeIf { it.isFile } ?: return
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = source.name
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return
    val fileName = dialog.file ?: return
    val target = File(directory, fileName)

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

private fun WallpaperImageTransformState.isIdentity(): Boolean {
    return normalizedRotation() == 0 && !flipHorizontal && !flipVertical
}

private fun WallpaperImageTransformState.normalizedRotation(): Int {
    return ((rotation.roundToInt() % 360) + 360) % 360
}

private fun BufferedImage.transformed(transform: WallpaperImageTransformState): BufferedImage {
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

private fun openPreviewImageForEdit(path: String) {
    val file = File(path).takeIf { it.isFile } ?: return
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.EDIT)) {
            desktop.edit(file)
        } else {
            desktop.open(file)
        }
    }
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
private fun rememberDesktopTrayController(
    tooltip: String,
    onOpenWindow: () -> Unit,
    onExit: () -> Unit,
): DesktopTrayController {
    val controller = remember { DesktopTrayController(loadAppIconImage(), tooltip) }
    SideEffect {
        controller.updateTooltip(tooltip)
        controller.updateWindowCallbacks(onOpenWindow, onExit)
    }
    DisposableEffect(controller) {
        controller.install()
        onDispose { controller.dispose() }
    }
    return controller
}

private data class DesktopTrayMenuModel(
    val strings: DesktopStrings,
    val canStartSlideshow: Boolean,
    val isSlideshowRunning: Boolean,
    val onStartSlideshow: () -> Unit,
    val onStopSlideshow: () -> Unit,
)

private class DesktopTrayController(
    private val iconImage: java.awt.Image,
    tooltip: String,
) {
    private var tooltip: String = tooltip
    private var trayIcon: TrayIcon? = null
    private var menuWindow: DesktopTrayMenuWindow? = null
    private var onOpenWindow: () -> Unit = {}
    private var onExit: () -> Unit = {}
    private var menuModel = DesktopTrayMenuModel(
        strings = desktopStringsFor(null),
        canStartSlideshow = false,
        isSlideshowRunning = false,
        onStartSlideshow = {},
        onStopSlideshow = {},
    )

    fun install() {
        if (!SystemTray.isSupported()) return
        SwingUtilities.invokeLater {
            if (trayIcon != null) return@invokeLater
            val icon = TrayIcon(iconImage, tooltip).apply {
                isImageAutoSize = true
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mousePressed(event: MouseEvent) {
                            if (SwingUtilities.isRightMouseButton(event) || event.isPopupTrigger) {
                                showMenu(currentMouseLocation(event))
                            }
                        }
                    }
                )
            }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    fun dispose() {
        SwingUtilities.invokeLater {
            hideMenu()
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
            trayIcon = null
        }
    }

    fun updateTooltip(value: String) {
        tooltip = value
        SwingUtilities.invokeLater {
            trayIcon?.toolTip = value
        }
    }

    fun updateWindowCallbacks(
        onOpenWindow: () -> Unit,
        onExit: () -> Unit,
    ) {
        this.onOpenWindow = onOpenWindow
        this.onExit = onExit
    }

    fun updateMenu(
        strings: DesktopStrings,
        canStartSlideshow: Boolean,
        isSlideshowRunning: Boolean,
        onStartSlideshow: () -> Unit,
        onStopSlideshow: () -> Unit,
    ) {
        menuModel = DesktopTrayMenuModel(
            strings = strings,
            canStartSlideshow = canStartSlideshow,
            isSlideshowRunning = isSlideshowRunning,
            onStartSlideshow = onStartSlideshow,
            onStopSlideshow = onStopSlideshow,
        )
    }

    fun displayMessage(title: String, message: String) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }

    private fun showMenu(anchor: Point) {
        if (menuWindow?.isVisible == true) {
            hideMenu()
            return
        }

        val model = menuModel
        val window = menuWindow ?: DesktopTrayMenuWindow(::hideMenu).also { menuWindow = it }
        window.showAt(
            anchor = anchor,
            model = model,
            onOpenWindow = {
                hideMenu()
                onOpenWindow()
            },
            onStartSlideshow = {
                hideMenu()
                model.onStartSlideshow()
            },
            onStopSlideshow = {
                hideMenu()
                model.onStopSlideshow()
            },
            onExit = {
                hideMenu()
                onExit()
            },
        )
    }

    private fun hideMenu() {
        menuWindow?.isVisible = false
    }

    private fun currentMouseLocation(event: MouseEvent): Point {
        return MouseInfo.getPointerInfo()?.location ?: Point(event.xOnScreen, event.yOnScreen)
    }
}

private class DesktopTrayMenuWindow(
    private val onDismiss: () -> Unit,
) : JWindow() {
    private var dismissOnFocusLost = false
    private var previousMouseButtonPressed = false
    private var outsideClickMonitorStartedAt = 0L
    private val outsideClickTimer = Timer(45) { dismissOnOutsideClick() }

    init {
        background = java.awt.Color(0, 0, 0, 0)
        isAlwaysOnTop = true
        focusableWindowState = true
        type = java.awt.Window.Type.POPUP
        addWindowFocusListener(
            object : WindowAdapter() {
                override fun windowLostFocus(event: WindowEvent) {
                    if (dismissOnFocusLost) onDismiss()
                }
            }
        )
    }

    fun showAt(
        anchor: Point,
        model: DesktopTrayMenuModel,
        onOpenWindow: () -> Unit,
        onStartSlideshow: () -> Unit,
        onStopSlideshow: () -> Unit,
        onExit: () -> Unit,
    ) {
        val actionText = if (model.isSlideshowRunning) {
            model.strings.stopSlideshow
        } else {
            model.strings.startSlideshow
        }
        val menuWidth = trayMenuWidth(model.strings.openWindow, actionText, model.strings.quit)
        contentPane = DesktopTrayMenuPanel().apply {
            add(DesktopTrayMenuItem(model.strings.openWindow, menuWidth, enabled = true, onClick = onOpenWindow))
            add(DesktopTraySeparator(menuWidth))
            if (model.isSlideshowRunning) {
                add(DesktopTrayMenuItem(model.strings.stopSlideshow, menuWidth, enabled = true, onClick = onStopSlideshow))
            } else {
                add(
                    DesktopTrayMenuItem(
                        text = model.strings.startSlideshow,
                        menuWidth = menuWidth,
                        enabled = model.canStartSlideshow,
                        onClick = onStartSlideshow,
                    )
                )
            }
            add(DesktopTraySeparator(menuWidth))
            add(DesktopTrayMenuItem(model.strings.quit, menuWidth, enabled = true, onClick = onExit))
        }
        pack()

        val screen = Toolkit.getDefaultToolkit().screenSize
        val x = (anchor.x - width / 2).coerceIn(8, screen.width - width - 8)
        val preferredY = anchor.y - height - 12
        val y = if (preferredY > 8) preferredY else (anchor.y + 12).coerceAtMost(screen.height - height - 8)
        setLocation(x, y)
        dismissOnFocusLost = false
        isVisible = true
        toFront()
        previousMouseButtonPressed = DesktopMouseButtons.isAnyPressed()
        outsideClickMonitorStartedAt = System.currentTimeMillis()
        outsideClickTimer.start()
        Timer(180) {
            dismissOnFocusLost = true
            requestFocus()
        }.apply {
            isRepeats = false
            start()
        }
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) outsideClickTimer.stop()
    }

    private fun dismissOnOutsideClick() {
        val mouseButtonPressed = DesktopMouseButtons.isAnyPressed()
        val isNewPress = mouseButtonPressed && !previousMouseButtonPressed
        val canDismiss = System.currentTimeMillis() - outsideClickMonitorStartedAt >= OUTSIDE_CLICK_GRACE_PERIOD_MS
        if (isNewPress && canDismiss) {
            val pointerLocation = MouseInfo.getPointerInfo()?.location
            if (pointerLocation == null || !bounds.contains(pointerLocation)) {
                onDismiss()
            }
        }
        previousMouseButtonPressed = mouseButtonPressed
    }

    private companion object {
        private const val OUTSIDE_CLICK_GRACE_PERIOD_MS = 140L
    }
}

private object DesktopMouseButtons {
    private val user32 by lazy { Native.load("user32", User32AsyncKeyState::class.java) }

    fun isAnyPressed(): Boolean {
        return runCatching {
            isPressed(VK_LBUTTON) || isPressed(VK_RBUTTON) || isPressed(VK_MBUTTON)
        }.getOrDefault(false)
    }

    private fun isPressed(keyCode: Int): Boolean {
        return user32.GetAsyncKeyState(keyCode).toInt() and KEY_PRESSED_MASK != 0
    }

    private const val VK_LBUTTON = 0x01
    private const val VK_RBUTTON = 0x02
    private const val VK_MBUTTON = 0x04
    private const val KEY_PRESSED_MASK = 0x8000

    private interface User32AsyncKeyState : StdCallLibrary {
        fun GetAsyncKeyState(vKey: Int): Short
    }
}

private class DesktopTrayMenuPanel : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(SHADOW_SIZE + 4, SHADOW_SIZE, SHADOW_SIZE + 4, SHADOW_SIZE)
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val rectX = SHADOW_SIZE
            val rectY = SHADOW_SIZE
            val rectWidth = width - SHADOW_SIZE * 2
            val rectHeight = height - SHADOW_SIZE * 2
            for (step in 5 downTo 1) {
                g.color = java.awt.Color(0, 0, 0, 7 * step)
                g.fillRoundRect(
                    rectX - step / 2,
                    rectY + step / 2,
                    rectWidth + step,
                    rectHeight + step,
                    CORNER_RADIUS,
                    CORNER_RADIUS,
                )
            }
            g.color = java.awt.Color.WHITE
            g.fillRoundRect(rectX, rectY, rectWidth, rectHeight, CORNER_RADIUS, CORNER_RADIUS)
            g.color = java.awt.Color(224, 228, 234)
            g.drawRoundRect(rectX, rectY, rectWidth - 1, rectHeight - 1, CORNER_RADIUS, CORNER_RADIUS)
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }

    private companion object {
        private const val SHADOW_SIZE = 8
        private const val CORNER_RADIUS = 10
    }
}

private class DesktopTrayMenuItem(
    text: String,
    menuWidth: Int,
    enabled: Boolean,
    private val onClick: () -> Unit,
) : JPanel(BorderLayout()) {
    private var hovered = false

    init {
        isOpaque = false
        isEnabled = enabled
        preferredSize = Dimension(menuWidth, ITEM_HEIGHT)
        maximumSize = Dimension(menuWidth, ITEM_HEIGHT)
        minimumSize = Dimension(menuWidth, ITEM_HEIGHT)
        cursor = if (enabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()

        add(
            JLabel(text).apply {
                border = EmptyBorder(0, 14, 0, 14)
                font = Font(TRAY_MENU_FONT, Font.PLAIN, TRAY_MENU_FONT_SIZE)
                foreground = if (enabled) java.awt.Color(22, 24, 29) else java.awt.Color(146, 152, 164)
                horizontalAlignment = SwingConstants.LEFT
                verticalAlignment = SwingConstants.CENTER
            },
            BorderLayout.CENTER,
        )

        if (enabled) {
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseEntered(event: MouseEvent) {
                        hovered = true
                        repaint()
                    }

                    override fun mouseExited(event: MouseEvent) {
                        hovered = false
                        repaint()
                    }

                    override fun mouseReleased(event: MouseEvent) {
                        if (contains(event.point)) onClick()
                    }
                }
            )
        }
    }

    override fun paintComponent(graphics: Graphics) {
        if (hovered) {
            val g = graphics.create() as Graphics2D
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = java.awt.Color(243, 246, 250)
                g.fillRoundRect(6, 2, width - 12, height - 4, 7, 7)
            } finally {
                g.dispose()
            }
        }
        super.paintComponent(graphics)
    }

    private companion object {
        private const val ITEM_HEIGHT = 30
    }
}

private class DesktopTraySeparator(
    menuWidth: Int,
) : JComponent() {
    init {
        preferredSize = Dimension(menuWidth, SEPARATOR_HEIGHT)
        maximumSize = Dimension(menuWidth, SEPARATOR_HEIGHT)
        minimumSize = Dimension(menuWidth, SEPARATOR_HEIGHT)
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.color = java.awt.Color(228, 232, 238)
            g.drawLine(14, height / 2, width - 14, height / 2)
        } finally {
            g.dispose()
        }
    }

    private companion object {
        private const val SEPARATOR_HEIGHT = 7
    }
}

private const val TRAY_MENU_FONT = "Microsoft YaHei UI"
private const val TRAY_MENU_FONT_SIZE = 13

private fun trayMenuWidth(vararg labels: String): Int {
    val font = Font(TRAY_MENU_FONT, Font.PLAIN, TRAY_MENU_FONT_SIZE)
    val metrics = JLabel().getFontMetrics(font)
    return labels.maxOf { metrics.stringWidth(it) }
        .plus(28)
        .coerceIn(136, 190)
}

private fun loadAppIconImage(): java.awt.Image {
    val stream = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icons/app.png")) {
        "Missing desktop app icon resource"
    }
    return stream.use { ImageIO.read(it) }
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
