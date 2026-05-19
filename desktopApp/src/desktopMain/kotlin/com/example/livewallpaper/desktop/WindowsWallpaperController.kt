package com.example.livewallpaper.desktop

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.random.Random

class WindowsWallpaperController : DesktopWallpaperController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var slideshowJob: Job? = null
    private var currentIndex = 0
    private val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)
    private val _status = MutableStateFlow<DesktopWallpaperStatus>(DesktopWallpaperStatus.Idle)

    override val status: StateFlow<DesktopWallpaperStatus> = _status

    override fun start(config: WallpaperConfig) {
        if (!isWindows) {
            _status.value = DesktopWallpaperStatus.Unsupported
            return
        }

        if (config.imageUris.none { File(it).isFile }) {
            _status.value = DesktopWallpaperStatus.Error("No readable image files")
            return
        }

        slideshowJob?.cancel()
        slideshowJob = scope.launch {
            val playedIndexes = mutableSetOf<Int>()
            while (isActive) {
                val imagePaths = config.imageUris.filter { File(it).isFile }
                if (imagePaths.isEmpty()) {
                    _status.value = DesktopWallpaperStatus.Error("No readable image files")
                    delay(config.interval.coerceAtLeast(MIN_INTERVAL_MS))
                    continue
                }
                val nextIndex = when (config.playMode) {
                    PlayMode.SEQUENTIAL -> {
                        if (currentIndex >= imagePaths.size) currentIndex = 0
                        currentIndex
                    }
                    PlayMode.RANDOM -> {
                        if (playedIndexes.size >= imagePaths.size) playedIndexes.clear()
                        var randomIndex: Int
                        do {
                            randomIndex = Random.nextInt(imagePaths.size)
                        } while (randomIndex in playedIndexes && playedIndexes.size < imagePaths.size)
                        playedIndexes.add(randomIndex)
                        randomIndex
                    }
                }

                val path = imagePaths[nextIndex]
                runCatching {
                    applyWallpaper(path, config.scaleMode)
                    _status.value = DesktopWallpaperStatus.Running(path)
                }.onFailure { error ->
                    _status.value = DesktopWallpaperStatus.Error(error.message ?: "Unable to set wallpaper")
                }

                currentIndex = (nextIndex + 1) % imagePaths.size
                delay(config.interval.coerceAtLeast(MIN_INTERVAL_MS))
            }
        }
    }

    override fun stop() {
        slideshowJob?.cancel()
        slideshowJob = null
        _status.value = DesktopWallpaperStatus.Idle
    }

    override fun setWallpaper(path: String, scaleMode: ScaleMode) {
        if (!isWindows) {
            _status.value = DesktopWallpaperStatus.Unsupported
            return
        }

        runCatching {
            applyWallpaper(path, scaleMode)
            _status.value = DesktopWallpaperStatus.Current(path)
        }.onFailure { error ->
            _status.value = DesktopWallpaperStatus.Error(error.message ?: "Unable to set wallpaper")
        }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun applyWallpaper(path: String, scaleMode: ScaleMode) {
        val file = File(path)
        require(file.isFile) { "File not found: $path" }
        val wallpaperFile = prepareWallpaperFile(file)

        val (wallpaperStyle, tileWallpaper) = when (scaleMode) {
            ScaleMode.CENTER_CROP -> "10" to "0"
            ScaleMode.FIT_CENTER -> "6" to "0"
        }

        Advapi32Util.registrySetStringValue(
            WinReg.HKEY_CURRENT_USER,
            DESKTOP_REGISTRY_PATH,
            "WallpaperStyle",
            wallpaperStyle
        )
        Advapi32Util.registrySetStringValue(
            WinReg.HKEY_CURRENT_USER,
            DESKTOP_REGISTRY_PATH,
            "TileWallpaper",
            tileWallpaper
        )

        val updated = User32Ex.INSTANCE.SystemParametersInfoW(
            SPI_SETDESKWALLPAPER,
            0,
            WString(wallpaperFile.absolutePath),
            SPIF_UPDATEINIFILE or SPIF_SENDCHANGE
        )
        check(updated) { "SystemParametersInfo failed" }
    }

    private fun prepareWallpaperFile(source: File): File {
        val image = ImageIO.read(source) ?: error("Unsupported image file: ${source.absolutePath}")
        val cacheDir = File(
            System.getProperty("user.home"),
            "AppData\\Local\\LiveWallpaper\\wallpapers"
        ).apply { mkdirs() }
        val output = File(cacheDir, "wallpaper_${source.absolutePath.stableHash()}.bmp")
        val opaqueImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = opaqueImage.createGraphics()
        try {
            graphics.color = Color.BLACK
            graphics.fillRect(0, 0, opaqueImage.width, opaqueImage.height)
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val written = ImageIO.write(opaqueImage, "bmp", output)
        check(written) { "BMP writer is not available" }
        check(output.isFile && output.length() > 0L) { "Unable to prepare wallpaper file" }
        return output
    }

    private fun String.stableHash(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1_000L
        private const val SPI_SETDESKWALLPAPER = 0x0014
        private const val SPIF_UPDATEINIFILE = 0x0001
        private const val SPIF_SENDCHANGE = 0x0002
        private const val DESKTOP_REGISTRY_PATH = "Control Panel\\Desktop"
    }

    private interface User32Ex : StdCallLibrary {
        fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: WString, fWinIni: Int): Boolean

        companion object {
            val INSTANCE: User32Ex = Native.load("user32", User32Ex::class.java)
        }
    }
}
