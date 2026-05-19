package com.example.livewallpaper.desktop

import androidx.compose.runtime.compositionLocalOf
import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties

data class DesktopStrings(
    val appTitle: String,
    val images: String,
    val wallpapers: String,
    val aiPaint: String,
    val settings: String,
    val close: String,
    val cancel: String,
    val previous: String,
    val next: String,
    val zoomIn: String,
    val zoomOut: String,
    val fitWindow: String,
    val addImages: String,
    val remove: String,
    val moveUp: String,
    val moveDown: String,
    val multiSelect: String,
    val selectAll: String,
    val cancelSelectAll: String,
    val deleteSelected: String,
    val setCurrentWallpaper: String,
    val startSlideshow: String,
    val stopSlideshow: String,
    val openWindow: String,
    val quit: String,
    val emptyTitle: String,
    val emptySubtitle: String,
    val dropImagesHint: String,
    val wallpaperLibrary: String,
    val selected: String,
    val dragReorderHint: String,
    val aiPaintTitle: String,
    val aiPaintSubtitle: String,
    val aiPaintComingSoon: String,
    val intervalSeconds: String,
    val wallpaperSettings: String,
    val appearanceSettings: String,
    val scaleMode: String,
    val centerCrop: String,
    val fitCenter: String,
    val playMode: String,
    val sequential: String,
    val random: String,
    val theme: String,
    val system: String,
    val light: String,
    val dark: String,
    val stardust: String,
    val clear: String,
    val language: String,
    val followSystem: String,
    val english: String,
    val chinese: String,
    val statusIdle: String,
    val statusRunning: String,
    val statusUnsupported: String,
    val statusError: String,
    val missingFile: String,
    val missingBadge: String,
    val currentBadge: String,
    val desktopBehavior: String,
    val launchAtStartup: String,
    val launchAtStartupDescription: String,
    val restoreSlideshowOnLaunch: String,
    val restoreSlideshowOnLaunchDescription: String,
    val selectedCount: (Int) -> String,
    val multiSelectCount: (Int) -> String,
    val deleteSelectedTitle: String,
    val deleteSelectedMessage: (Int) -> String,
    val currentWallpaper: (String) -> String,
    val intervalValue: (Int) -> String,
)

val LocalDesktopStrings = compositionLocalOf { desktopStringsFor(null) }

fun desktopStringsFor(languageTag: String?): DesktopStrings {
    val language = languageTag ?: Locale.getDefault().language
    return if (language.startsWith("zh", ignoreCase = true)) zhCnDesktopStrings else enDesktopStrings
}

private val enDesktopStrings = loadDesktopStrings("i18n/desktop_en.properties")

private val zhCnDesktopStrings = loadDesktopStrings("i18n/desktop_zh_CN.properties")

private fun loadDesktopStrings(resourcePath: String): DesktopStrings {
    val properties = Properties()
    val stream = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)) {
        "Missing desktop i18n resource: $resourcePath"
    }
    stream.use {
        properties.load(InputStreamReader(it, Charsets.UTF_8))
    }

    fun text(key: String): String = checkNotNull(properties.getProperty(key)) {
        "Missing desktop i18n key '$key' in $resourcePath"
    }

    return DesktopStrings(
        appTitle = text("appTitle"),
        images = text("images"),
        wallpapers = text("wallpapers"),
        aiPaint = text("aiPaint"),
        settings = text("settings"),
        close = text("close"),
        cancel = text("cancel"),
        previous = text("previous"),
        next = text("next"),
        zoomIn = text("zoomIn"),
        zoomOut = text("zoomOut"),
        fitWindow = text("fitWindow"),
        addImages = text("addImages"),
        remove = text("remove"),
        moveUp = text("moveUp"),
        moveDown = text("moveDown"),
        multiSelect = text("multiSelect"),
        selectAll = text("selectAll"),
        cancelSelectAll = text("cancelSelectAll"),
        deleteSelected = text("deleteSelected"),
        setCurrentWallpaper = text("setCurrentWallpaper"),
        startSlideshow = text("startSlideshow"),
        stopSlideshow = text("stopSlideshow"),
        openWindow = text("openWindow"),
        quit = text("quit"),
        emptyTitle = text("emptyTitle"),
        emptySubtitle = text("emptySubtitle"),
        dropImagesHint = text("dropImagesHint"),
        wallpaperLibrary = text("wallpaperLibrary"),
        selected = text("selected"),
        dragReorderHint = text("dragReorderHint"),
        aiPaintTitle = text("aiPaintTitle"),
        aiPaintSubtitle = text("aiPaintSubtitle"),
        aiPaintComingSoon = text("aiPaintComingSoon"),
        intervalSeconds = text("intervalSeconds"),
        wallpaperSettings = text("wallpaperSettings"),
        appearanceSettings = text("appearanceSettings"),
        scaleMode = text("scaleMode"),
        centerCrop = text("centerCrop"),
        fitCenter = text("fitCenter"),
        playMode = text("playMode"),
        sequential = text("sequential"),
        random = text("random"),
        theme = text("theme"),
        system = text("system"),
        light = text("light"),
        dark = text("dark"),
        stardust = text("stardust"),
        clear = text("clear"),
        language = text("language"),
        followSystem = text("followSystem"),
        english = text("english"),
        chinese = text("chinese"),
        statusIdle = text("statusIdle"),
        statusRunning = text("statusRunning"),
        statusUnsupported = text("statusUnsupported"),
        statusError = text("statusError"),
        missingFile = text("missingFile"),
        missingBadge = text("missingBadge"),
        currentBadge = text("currentBadge"),
        desktopBehavior = text("desktopBehavior"),
        launchAtStartup = text("launchAtStartup"),
        launchAtStartupDescription = text("launchAtStartupDescription"),
        restoreSlideshowOnLaunch = text("restoreSlideshowOnLaunch"),
        restoreSlideshowOnLaunchDescription = text("restoreSlideshowOnLaunchDescription"),
        selectedCount = { count -> text("selectedCount").format(count) },
        multiSelectCount = { count -> text("multiSelectCount").format(count) },
        deleteSelectedTitle = text("deleteSelectedTitle"),
        deleteSelectedMessage = { count -> text("deleteSelectedMessage").format(count) },
        currentWallpaper = { name -> text("currentWallpaper").format(name) },
        intervalValue = { seconds -> text("intervalValue").format(seconds) },
    )
}
