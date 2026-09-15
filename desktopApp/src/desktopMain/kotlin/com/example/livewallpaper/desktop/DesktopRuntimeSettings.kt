package com.example.livewallpaper.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.util.prefs.Preferences

internal class DesktopRuntimeSettings {
    private val preferences = Preferences.userRoot().node(PREFERENCES_NODE)
    private val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)

    fun wasSlideshowRunning(): Boolean = preferences.getBoolean(KEY_SLIDESHOW_RUNNING, false)

    fun setSlideshowRunning(running: Boolean) {
        preferences.putBoolean(KEY_SLIDESHOW_RUNNING, running)
    }

    fun isAiPaintSidebarCollapsed(): Boolean = preferences.getBoolean(KEY_AI_PAINT_SIDEBAR_COLLAPSED, false)

    fun setAiPaintSidebarCollapsed(collapsed: Boolean) {
        preferences.putBoolean(KEY_AI_PAINT_SIDEBAR_COLLAPSED, collapsed)
    }

    /**
     * 读取用户拖动后保存的侧边栏宽度（dp），越界值会被收敛到允许范围内。
     */
    fun sidebarWidthDp(): Float =
        preferences.getFloat(KEY_SIDEBAR_WIDTH_DP, DEFAULT_SIDEBAR_WIDTH_DP)
            .coerceIn(MIN_SIDEBAR_WIDTH_DP, MAX_SIDEBAR_WIDTH_DP)

    /**
     * 保存侧边栏宽度（dp），写入前同样收敛到允许范围内。
     */
    fun setSidebarWidthDp(widthDp: Float) {
        preferences.putFloat(KEY_SIDEBAR_WIDTH_DP, widthDp.coerceIn(MIN_SIDEBAR_WIDTH_DP, MAX_SIDEBAR_WIDTH_DP))
    }

    fun applyLaunchAtStartup(enabled: Boolean): Boolean {
        if (!isWindows) return false
        return runCatching {
            if (enabled) {
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    RUN_REGISTRY_PATH,
                    RUN_VALUE_NAME,
                    appLaunchCommand(),
                )
            } else if (Advapi32Util.registryValueExists(
                    WinReg.HKEY_CURRENT_USER,
                    RUN_REGISTRY_PATH,
                    RUN_VALUE_NAME,
                )
            ) {
                Advapi32Util.registryDeleteValue(
                    WinReg.HKEY_CURRENT_USER,
                    RUN_REGISTRY_PATH,
                    RUN_VALUE_NAME,
                )
            }
        }.isSuccess
    }

    private fun appLaunchCommand(): String {
        val packagedAppPath = System.getProperty("jpackage.app-path").orEmpty()
        if (packagedAppPath.isNotBlank()) {
            return packagedAppPath.quoted()
        }

        val javaExecutable = File(System.getProperty("java.home"), "bin/javaw.exe").absolutePath
        val classPath = System.getProperty("java.class.path")
        val command = System.getProperty("sun.java.command")
        return "${javaExecutable.quoted()} -cp ${classPath.quoted()} $command"
    }

    private fun String.quoted(): String = "\"$this\""

    companion object {
        /** 侧边栏默认宽度（dp）。 */
        const val DEFAULT_SIDEBAR_WIDTH_DP = 220f

        /** 侧边栏可拖动的最小宽度（dp），保证导航项与状态文案仍可完整显示。 */
        const val MIN_SIDEBAR_WIDTH_DP = 180f

        /** 侧边栏可拖动的最大宽度（dp），避免过度挤压右侧工作区。 */
        const val MAX_SIDEBAR_WIDTH_DP = 400f

        private const val PREFERENCES_NODE = "com.example.livewallpaper.desktop"
        private const val KEY_SLIDESHOW_RUNNING = "slideshow_running"
        private const val KEY_AI_PAINT_SIDEBAR_COLLAPSED = "aipaint_sidebar_collapsed"
        private const val KEY_SIDEBAR_WIDTH_DP = "sidebar_width_dp"
        private const val RUN_REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val RUN_VALUE_NAME = "Live Wallpaper"
    }
}
