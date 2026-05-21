package com.example.livewallpaper.core.platform

import java.io.File
import java.util.prefs.Preferences

/**
 * Desktop-only storage path settings for AI paint generated files and temporary image caches.
 */
object DesktopAiPaintStoragePaths {
    private val preferences = Preferences.userRoot().node(PREFERENCES_NODE)

    fun generatedImagesDirectory(): File = configuredDirectory(KEY_GENERATED_IMAGES, defaultGeneratedImagesDirectory())

    fun responseCacheDirectory(): File = configuredDirectory(KEY_RESPONSE_CACHE, defaultResponseCacheDirectory())

    fun clipboardCacheDirectory(): File = configuredDirectory(KEY_CLIPBOARD_CACHE, defaultClipboardCacheDirectory())

    fun generatedImagesPath(): String = generatedImagesDirectory().absolutePath

    fun responseCachePath(): String = responseCacheDirectory().absolutePath

    fun clipboardCachePath(): String = clipboardCacheDirectory().absolutePath

    fun setGeneratedImagesPath(path: String) {
        setDirectory(KEY_GENERATED_IMAGES, path)
    }

    fun setResponseCachePath(path: String) {
        setDirectory(KEY_RESPONSE_CACHE, path)
    }

    fun setClipboardCachePath(path: String) {
        setDirectory(KEY_CLIPBOARD_CACHE, path)
    }

    fun resetGeneratedImagesPath() {
        preferences.remove(KEY_GENERATED_IMAGES)
    }

    fun resetResponseCachePath() {
        preferences.remove(KEY_RESPONSE_CACHE)
    }

    fun resetClipboardCachePath() {
        preferences.remove(KEY_CLIPBOARD_CACHE)
    }

    private fun configuredDirectory(key: String, defaultDirectory: File): File {
        val configuredPath = preferences.get(key, "").trim()
        val directory = if (configuredPath.isBlank()) defaultDirectory else File(configuredPath)
        return directory.takeIf { it.ensureDirectory() } ?: defaultDirectory.apply { mkdirs() }
    }

    private fun setDirectory(key: String, path: String) {
        val directory = File(path).absoluteFile
        directory.mkdirs()
        preferences.put(key, directory.absolutePath)
    }

    private fun File.ensureDirectory(): Boolean {
        return if (isDirectory) true else mkdirs()
    }

    private fun defaultGeneratedImagesDirectory(): File =
        File(System.getProperty("user.home"), ".live-wallpaper/aipaint")

    private fun defaultResponseCacheDirectory(): File =
        File(System.getProperty("java.io.tmpdir"), "live-wallpaper-aipaint")

    private fun defaultClipboardCacheDirectory(): File =
        File(System.getProperty("java.io.tmpdir"), "LiveWallpaper/clipboard")

    private const val PREFERENCES_NODE = "com.example.livewallpaper.desktop"
    private const val KEY_GENERATED_IMAGES = "aipaint_generated_images_directory"
    private const val KEY_RESPONSE_CACHE = "aipaint_response_cache_directory"
    private const val KEY_CLIPBOARD_CACHE = "aipaint_clipboard_cache_directory"
}
