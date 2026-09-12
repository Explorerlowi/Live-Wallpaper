package com.example.livewallpaper.feature.dynamicwallpaper.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WallpaperLibraryOperationsTest {

    @Test
    fun migrateFromConfigCreatesDefaultLibraryWithImagesAndCrop() {
        val crop = ImageCropParams(scale = 1.2f, offsetX = 0.1f)
        val config = WallpaperConfig(
            interval = 5000L,
            imageUris = listOf("uri-a", "uri-b", "uri-a"),
            imageCropParams = mapOf("uri-a" to crop),
            languageTag = "zh-CN"
        )
        val ids = idSequence()

        val document = WallpaperLibraryOperations.migrateFromConfig(
            config = config,
            defaultLibraryName = WallpaperLibraryNames.defaultName(config.languageTag),
            generateId = ids
        )

        assertEquals("默认", document.libraries.single().name)
        assertEquals(listOf("uri-a", "uri-b"), WallpaperLibraryOperations.projectImageUris(document))
        assertEquals(crop, WallpaperLibraryOperations.projectCropParams(document)["uri-a"])
        assertEquals(2, document.items.size)
    }

    @Test
    fun emptyInstallCreatesDefaultLibrary() {
        val document = WallpaperLibraryOperations.emptyDocument("Default", idSequence())

        assertEquals(1, document.libraries.size)
        assertTrue(document.items.isEmpty())
        assertEquals(document.libraries.single().id, document.activeLibraryId)
        assertTrue(WallpaperLibraryOperations.projectImageUris(document).isEmpty())
    }

    @Test
    fun addingSameUriToTwoLibrariesCreatesSingleItem() {
        var document = WallpaperLibraryOperations.emptyDocument("Default", idSequence(1))
        val firstLibraryId = document.activeLibraryId
        document = WallpaperLibraryOperations.createLibrary(document, "Nature", idSequence(10))
        val secondLibraryId = document.activeLibraryId

        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = firstLibraryId,
            uris = listOf("shared.jpg"),
            generateId = idSequence(20)
        )
        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = secondLibraryId,
            uris = listOf("shared.jpg"),
            generateId = idSequence(30)
        )

        assertEquals(1, document.items.size)
        assertEquals("shared.jpg", document.items.single().uri)
        assertTrue(document.libraries.all { library ->
            library.itemIds.single() == document.items.single().id
        })
    }

    @Test
    fun removingFromOneLibraryKeepsSharedItem() {
        var document = twoLibrariesWithSharedImage()
        val nature = document.libraries.first { it.name == "Nature" }
        val work = document.libraries.first { it.name == "Work" }

        document = WallpaperLibraryOperations.removeImagesFromLibrary(
            document = document,
            libraryId = nature.id,
            uris = listOf("shared.jpg")
        )

        assertEquals(1, document.items.size)
        assertTrue(document.libraries.first { it.id == nature.id }.itemIds.isEmpty())
        assertEquals(listOf(document.items.single().id), document.libraries.first { it.id == work.id }.itemIds)
    }

    @Test
    fun removingFromLastLibraryDeletesItem() {
        var document = twoLibrariesWithSharedImage()
        val nature = document.libraries.first { it.name == "Nature" }
        val work = document.libraries.first { it.name == "Work" }

        document = WallpaperLibraryOperations.removeImagesFromLibrary(document, nature.id, listOf("shared.jpg"))
        document = WallpaperLibraryOperations.removeImagesFromLibrary(document, work.id, listOf("shared.jpg"))

        assertTrue(document.items.isEmpty())
        assertTrue(document.libraries.all { it.itemIds.isEmpty() })
    }

    @Test
    fun cannotDeleteLastLibrary() {
        val document = WallpaperLibraryOperations.emptyDocument("Default", idSequence())
        val unchanged = WallpaperLibraryOperations.deleteLibrary(document, document.activeLibraryId)

        assertEquals(document, unchanged)
    }

    @Test
    fun deletingActiveLibrarySwitchesToRemainingLibrary() {
        var document = WallpaperLibraryOperations.emptyDocument("Default", idSequence(1))
        val defaultId = document.activeLibraryId
        document = WallpaperLibraryOperations.createLibrary(document, "Night", idSequence(2))
        val nightId = document.activeLibraryId

        document = WallpaperLibraryOperations.deleteLibrary(document, nightId)

        assertEquals(defaultId, document.activeLibraryId)
        assertEquals(listOf("Default"), document.libraries.map { it.name })
    }

    @Test
    fun deletingLibraryDropsUnreferencedItemsOnly() {
        var document = twoLibrariesWithSharedImage()
        val nature = document.libraries.first { it.name == "Nature" }
        val work = document.libraries.first { it.name == "Work" }
        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = nature.id,
            uris = listOf("only-nature.jpg"),
            generateId = idSequence(40)
        )

        document = WallpaperLibraryOperations.deleteLibrary(document, nature.id)

        assertEquals(work.id, document.activeLibraryId)
        assertEquals(listOf("shared.jpg"), document.items.map { it.uri })
    }

    @Test
    fun switchingLibraryChangesProjectedUrisAndCrop() {
        var document = WallpaperLibraryOperations.emptyDocument("Default", idSequence(1))
        val defaultId = document.activeLibraryId
        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = defaultId,
            uris = listOf("day.jpg"),
            generateId = idSequence(2)
        )
        document = WallpaperLibraryOperations.setItemCropParams(
            document = document,
            uri = "day.jpg",
            params = ImageCropParams(scale = 2f)
        )
        document = WallpaperLibraryOperations.createLibrary(document, "Night", idSequence(3))
        val nightId = document.activeLibraryId
        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = nightId,
            uris = listOf("night.jpg"),
            generateId = idSequence(4)
        )

        val settings = WallpaperConfig(interval = 8000L, playMode = PlayMode.RANDOM)
        val nightConfig = WallpaperLibraryOperations.projectConfig(settings, document)
        assertEquals(listOf("night.jpg"), nightConfig.imageUris)
        assertEquals(8000L, nightConfig.interval)
        assertEquals(PlayMode.RANDOM, nightConfig.playMode)

        document = WallpaperLibraryOperations.setActiveLibrary(document, defaultId)
        val dayConfig = WallpaperLibraryOperations.projectConfig(settings, document)
        assertEquals(listOf("day.jpg"), dayConfig.imageUris)
        assertEquals(2f, dayConfig.imageCropParams.getValue("day.jpg").scale)
        assertFalse(dayConfig.imageCropParams.containsKey("night.jpg"))
    }

    @Test
    fun defaultLibraryNameFollowsLanguageTag() {
        assertEquals("默认", WallpaperLibraryNames.defaultName("zh-CN"))
        assertEquals("Default", WallpaperLibraryNames.defaultName("en"))
        assertEquals("Default", WallpaperLibraryNames.defaultName(null))
    }

    private fun twoLibrariesWithSharedImage(): WallpaperLibraryDocument {
        var document = WallpaperLibraryOperations.emptyDocument("Nature", idSequence(1))
        val natureId = document.activeLibraryId
        document = WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = natureId,
            uris = listOf("shared.jpg"),
            generateId = idSequence(2)
        )
        document = WallpaperLibraryOperations.createLibrary(document, "Work", idSequence(3))
        return WallpaperLibraryOperations.addImagesToLibrary(
            document = document,
            libraryId = document.activeLibraryId,
            uris = listOf("shared.jpg"),
            generateId = idSequence(4)
        )
    }

    private fun idSequence(start: Int = 1): () -> String {
        var next = start
        return { "id-${next++}" }
    }
}
