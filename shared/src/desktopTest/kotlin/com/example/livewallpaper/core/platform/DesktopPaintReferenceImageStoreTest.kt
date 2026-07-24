package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.DefaultCoroutineDispatcherProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies that startup reference maintenance never sweeps unrelated painting directories. */
class DesktopPaintReferenceImageStoreTest {
    @Test
    fun cleanupOrphansOnlyDeletesFilesUnderReferences() = runBlocking {
        val managedRoot = Files.createTempDirectory("paint-reference-cleanup").toFile()
        try {
            val orphanReference = File(managedRoot, "references/session/orphan.png").apply {
                parentFile.mkdirs()
                writeText("orphan")
            }
            val generatedImage = File(managedRoot, "session/generated.png").apply {
                parentFile.mkdirs()
                writeText("generated")
            }
            val editedImage = File(managedRoot, "edited/result.png").apply {
                parentFile.mkdirs()
                writeText("edited")
            }
            val store = DesktopPaintReferenceImageStore(
                DefaultCoroutineDispatcherProvider(Dispatchers.Default, Dispatchers.Default),
                managedRoot,
            )

            store.cleanupOrphans(emptySet())

            assertFalse(orphanReference.exists())
            assertTrue(generatedImage.isFile)
            assertTrue(editedImage.isFile)
        } finally {
            managedRoot.deleteRecursively()
        }
    }
}
