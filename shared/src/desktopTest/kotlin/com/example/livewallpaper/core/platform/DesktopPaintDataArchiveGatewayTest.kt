package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.ExtractedPaintDataArchive
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveSource
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies desktop ZIP I/O and path traversal rejection without touching user storage. */
class DesktopPaintDataArchiveGatewayTest {
    @Test
    fun exportPolicyKeepsOnlyReadableImagesInsideConfiguredStorageDirectories() = runBlocking {
        withTemporaryDirectory { root ->
            val generated = File(root, "generated").apply { mkdirs() }
            val response = File(root, "response").apply { mkdirs() }
            val clipboard = File(root, "clipboard").apply { mkdirs() }
            val generatedImage = File(generated, "nested/generated.png").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val responseImage = File(response, "response.jpg").apply { writeBytes(byteArrayOf(2)) }
            val clipboardImage = File(clipboard, "clipboard.png").apply { writeBytes(byteArrayOf(3)) }
            val emptyImage = File(generated, "empty.png").apply { createNewFile() }
            val outsideImage = File(root, "outside.png").apply { writeBytes(byteArrayOf(4)) }
            val missingImage = File(clipboard, "missing.png")
            val responseIdentifier = responseImage.toURI().toString()
            val gateway = DesktopPaintDataArchiveGateway(File(root, "imports")) {
                listOf(generated, response, clipboard)
            }

            val retained = gateway.retainExportableImageIdentifiers(
                linkedSetOf(
                    generatedImage.absolutePath,
                    responseIdentifier,
                    clipboardImage.absolutePath,
                    emptyImage.absolutePath,
                    outsideImage.absolutePath,
                    missingImage.absolutePath,
                ),
            )

            assertEquals(
                linkedSetOf(
                    generatedImage.absolutePath,
                    responseIdentifier,
                    clipboardImage.absolutePath,
                ),
                retained,
            )
        }
    }

    @Test
    fun writeAndExtractRoundTripPreservesManifestAndImage() = runBlocking {
        withTemporaryDirectory { root ->
            val importsRoot = File(root, "imports")
            val gateway = DesktopPaintDataArchiveGateway(importsRoot)
            val sourceImage = File(root, "source.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val archive = File(root, "backup.zip")
            val manifest = """{"format":"test"}"""

            val writeResult = gateway.writeArchive(
                archive.absolutePath,
                PaintDataArchiveWriteRequest(
                    manifestJson = manifest,
                    imageSources = listOf(
                        PaintDataArchiveSource(sourceImage.absolutePath, "images/image_000001.png"),
                    ),
                ),
            )
            assertIs<PaintDataArchiveResult.Success<Unit>>(writeResult)
            assertTrue(archive.isFile)

            val extracted = assertIs<PaintDataArchiveResult.Success<ExtractedPaintDataArchive>>(
                gateway.extractArchive(archive.absolutePath),
            ).value
            assertEquals(manifest, extracted.manifestJson)
            val restored = File(extracted.imagePathsByArchivePath.getValue("images/image_000001.png"))
            assertContentEquals(sourceImage.readBytes(), restored.readBytes())

            gateway.discardExtraction(extracted.extractionId)
            assertFalse(File(importsRoot, extracted.extractionId).exists())
        }
    }

    @Test
    fun extractRejectsPathTraversalAndDeletesPartialFiles() = runBlocking {
        withTemporaryDirectory { root ->
            val importsRoot = File(root, "imports")
            val archive = File(root, "unsafe.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.png"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }

            val result = assertIs<PaintDataArchiveResult.Failure>(
                DesktopPaintDataArchiveGateway(importsRoot).extractArchive(archive.absolutePath),
            )

            assertEquals(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY, result.error)
            assertTrue(importsRoot.listFiles().isNullOrEmpty())
        }
    }

    private inline fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("paint-archive-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
