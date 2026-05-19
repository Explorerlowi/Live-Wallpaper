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

    private companion object {
        private const val PREFERENCES_NODE = "com.example.livewallpaper.desktop"
        private const val KEY_SLIDESHOW_RUNNING = "slideshow_running"
        private const val RUN_REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val RUN_VALUE_NAME = "Live Wallpaper"
    }
}
