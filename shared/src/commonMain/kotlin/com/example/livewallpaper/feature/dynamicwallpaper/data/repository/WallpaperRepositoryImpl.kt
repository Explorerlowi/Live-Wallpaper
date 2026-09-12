package com.example.livewallpaper.feature.dynamicwallpaper.data.repository

import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.AppUpdateService
import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model.PgyerResponse
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ImageCropParams
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.PlayMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ScaleMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.ThemeMode
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperConfig
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibraryDocument
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibraryNames
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.WallpaperLibraryOperations
import com.example.livewallpaper.feature.dynamicwallpaper.domain.repository.WallpaperRepository
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class WallpaperRepositoryImpl(
    private val settings: ObservableSettings,
    private val appUpdateService: AppUpdateService
) : WallpaperRepository {

    private val configKey = "WALLPAPER_CONFIG"
    private val librariesKey = "WALLPAPER_LIBRARIES"
    private val json = Json { ignoreUnknownKeys = true }

    override fun getConfig(): Flow<WallpaperConfig> = observeSettings {
        projectedConfig()
    }

    override fun getLibraryDocument(): Flow<WallpaperLibraryDocument> = observeSettings {
        loadDocument()
    }

    override fun getConfigSync(): WallpaperConfig = projectedConfig()

    override fun getLibraryDocumentSync(): WallpaperLibraryDocument = loadDocument()

    override suspend fun updateConfig(config: WallpaperConfig) {
        persistSettings(WallpaperLibraryOperations.withoutLibraryProjection(config))
    }

    override suspend fun addImages(uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.addImagesToLibrary(
                document = current,
                libraryId = current.activeLibraryId,
                uris = uris,
                generateId = ::generateId
            )
        }
    }

    override suspend fun addImagesToLibrary(libraryId: String, uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.addImagesToLibrary(
                document = current,
                libraryId = libraryId,
                uris = uris,
                generateId = ::generateId
            )
        }
    }

    override suspend fun removeImage(uri: String) {
        removeImages(listOf(uri))
    }

    override suspend fun removeImages(uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.removeImagesFromLibrary(
                document = current,
                libraryId = current.activeLibraryId,
                uris = uris
            )
        }
    }

    override suspend fun removeImagesFromLibrary(libraryId: String, uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.removeImagesFromLibrary(
                document = current,
                libraryId = libraryId,
                uris = uris
            )
        }
    }

    override suspend fun updateLibraryImageOrder(libraryId: String, uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.updateLibraryImageOrder(
                document = current,
                libraryId = libraryId,
                uris = uris
            )
        }
    }

    override suspend fun removeAllImages() {
        updateDocument { current ->
            WallpaperLibraryOperations.removeAllImagesFromLibrary(
                document = current,
                libraryId = current.activeLibraryId
            )
        }
    }

    override suspend fun updateImageOrder(uris: List<String>) {
        updateDocument { current ->
            WallpaperLibraryOperations.updateLibraryImageOrder(
                document = current,
                libraryId = current.activeLibraryId,
                uris = uris
            )
        }
    }

    override suspend fun setInterval(interval: Long) {
        persistSettings(loadSettings().copy(interval = interval))
    }

    override suspend fun setScaleMode(mode: ScaleMode) {
        persistSettings(loadSettings().copy(scaleMode = mode))
    }

    override suspend fun setPlayMode(mode: PlayMode) {
        persistSettings(loadSettings().copy(playMode = mode))
    }

    override suspend fun setImageCropParams(uri: String, params: ImageCropParams) {
        updateDocument { current ->
            WallpaperLibraryOperations.setItemCropParams(current, uri, params)
        }
    }

    override suspend fun createLibrary(name: String) {
        updateDocument { current ->
            WallpaperLibraryOperations.createLibrary(current, name, ::generateId)
        }
    }

    override suspend fun renameLibrary(libraryId: String, name: String) {
        updateDocument { current ->
            WallpaperLibraryOperations.renameLibrary(current, libraryId, name)
        }
    }

    override suspend fun deleteLibrary(libraryId: String) {
        updateDocument { current ->
            WallpaperLibraryOperations.deleteLibrary(current, libraryId)
        }
    }

    override suspend fun setActiveLibrary(libraryId: String) {
        updateDocument { current ->
            WallpaperLibraryOperations.setActiveLibrary(current, libraryId)
        }
    }

    override suspend fun setLanguage(languageTag: String?) {
        persistSettings(loadSettings().copy(languageTag = languageTag))
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        persistSettings(loadSettings().copy(themeMode = mode))
    }

    override suspend fun setLaunchAtStartup(enabled: Boolean) {
        persistSettings(loadSettings().copy(launchAtStartup = enabled))
    }

    override suspend fun setRestoreSlideshowOnLaunch(enabled: Boolean) {
        persistSettings(loadSettings().copy(restoreSlideshowOnLaunch = enabled))
    }

    override suspend fun setPaintGenerationSuccessNotification(enabled: Boolean) {
        persistSettings(loadSettings().copy(paintGenerationSuccessNotification = enabled))
    }

    override suspend fun checkAppUpdate(
        apiKey: String,
        appKey: String,
        buildVersion: String?,
        buildBuildVersion: Int?
    ): PgyerResponse {
        return appUpdateService.checkUpdate(apiKey, appKey, buildVersion, buildBuildVersion)
    }

    private fun <T> observeSettings(read: () -> T): Flow<T> = callbackFlow {
        fun emitCurrent() {
            trySend(read())
        }
        val configListener = settings.addStringListener(configKey, "") { emitCurrent() }
        val librariesListener = settings.addStringListener(librariesKey, "") { emitCurrent() }
        emitCurrent()
        awaitClose {
            configListener.deactivate()
            librariesListener.deactivate()
        }
    }

    private fun projectedConfig(): WallpaperConfig {
        return WallpaperLibraryOperations.projectConfig(loadSettings(), loadDocument())
    }

    private fun loadSettings(): WallpaperConfig {
        val jsonString = settings.getString(configKey, "")
        if (jsonString.isBlank()) return WallpaperConfig()
        return try {
            WallpaperLibraryOperations.withoutLibraryProjection(json.decodeFromString(jsonString))
        } catch (_: Exception) {
            WallpaperConfig()
        }
    }

    private fun loadDocument(): WallpaperLibraryDocument {
        val stored = settings.getString(librariesKey, "")
        if (stored.isNotBlank()) {
            try {
                val parsed = json.decodeFromString<WallpaperLibraryDocument>(stored)
                if (parsed.libraries.isNotEmpty()) {
                    return WallpaperLibraryOperations.sanitize(parsed)
                }
            } catch (_: Exception) {
                // 回退到旧版扁平列表迁移
            }
        }
        return migrateLegacyAndPersist()
    }

    private fun migrateLegacyAndPersist(): WallpaperLibraryDocument {
        val legacy = parseLegacyConfig()
        val document = WallpaperLibraryOperations.migrateFromConfig(
            config = legacy,
            defaultLibraryName = WallpaperLibraryNames.defaultName(legacy.languageTag),
            generateId = ::generateId
        )
        persistDocument(document)
        persistSettings(WallpaperLibraryOperations.withoutLibraryProjection(legacy))
        return document
    }

    private fun parseLegacyConfig(): WallpaperConfig {
        val jsonString = settings.getString(configKey, "")
        if (jsonString.isBlank()) return WallpaperConfig()
        return try {
            json.decodeFromString(jsonString)
        } catch (_: Exception) {
            WallpaperConfig()
        }
    }

    private fun updateDocument(transform: (WallpaperLibraryDocument) -> WallpaperLibraryDocument) {
        val current = loadDocument()
        val next = transform(current)
        if (next != current) {
            persistDocument(next)
        }
    }

    private fun persistSettings(config: WallpaperConfig) {
        settings[configKey] = json.encodeToString(
            WallpaperLibraryOperations.withoutLibraryProjection(config)
        )
    }

    private fun persistDocument(document: WallpaperLibraryDocument) {
        settings[librariesKey] = json.encodeToString(document)
    }

    private fun generateId(): String =
        "${TimeProvider.currentTimeMillis()}-${Random.nextInt(10000, 99999)}"
}
