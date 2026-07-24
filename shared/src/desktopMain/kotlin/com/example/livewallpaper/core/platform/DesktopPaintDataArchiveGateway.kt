package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.ExtractedPaintDataArchive
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveLimits
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveResult
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveSource
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataArchiveWriteRequest
import com.example.livewallpaper.feature.aipaint.domain.model.PaintDataTransferError
import com.example.livewallpaper.feature.aipaint.domain.model.PaintClientPlatform
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * JVM desktop implementation that stores validated painting backups as regular ZIP files.
 *
 * @param importsRoot App-owned destination for images extracted during import.
 * @param exportImageRootsProvider Current user-configured directories allowed as export image sources.
 */
class DesktopPaintDataArchiveGateway(
    private val importsRoot: File = DesktopAiPaintStoragePaths.importedImagesDirectory(),
    private val exportImageRootsProvider: () -> List<File> = {
        listOf(
            DesktopAiPaintStoragePaths.generatedImagesDirectory(),
            DesktopAiPaintStoragePaths.responseCacheDirectory(),
            DesktopAiPaintStoragePaths.clipboardCacheDirectory(),
        )
    },
) : PaintDataArchiveGateway {
    override val clientPlatform: PaintClientPlatform = PaintClientPlatform.DESKTOP
    override suspend fun retainExportableImageIdentifiers(
        sourceIdentifiers: Set<String>,
    ): Set<String> = withContext(Dispatchers.IO) {
        val allowedRoots = exportImageRootsProvider().mapNotNull { root ->
            runCatching { root.canonicalFile.takeIf(File::isDirectory) }.getOrNull()
        }
        sourceIdentifiers.filterTo(linkedSetOf()) { identifier ->
            runCatching {
                val source = identifier.toLocalFileOrNull()?.canonicalFile ?: return@runCatching false
                source.isFile && source.canRead() && source.length() > 0L &&
                    allowedRoots.any { root -> source.isInside(root) }
            }.getOrDefault(false)
        }
    }

    override suspend fun writeArchive(
        destinationIdentifier: String,
        request: PaintDataArchiveWriteRequest,
    ): PaintDataArchiveResult<Unit> = withContext(Dispatchers.IO) {
        var temporary: File? = null
        try {
            validateWriteRequest(request)
            val destination = destinationIdentifier.toLocalFileOrNull()?.absoluteFile
                ?: throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            val parent = destination.parentFile
                ?: throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            if ((!parent.isDirectory && !parent.mkdirs()) || destination.isDirectory) {
                throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            }
            temporary = File(parent, ".${destination.name}.${UUID.randomUUID()}.tmp")
            temporary.outputStream().buffered().use { output ->
                ZipOutputStream(output).use { zip ->
                    val manifestBytes = request.manifestJson.toByteArray(Charsets.UTF_8)
                    var totalBytes = manifestBytes.size.toLong()
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zip.write(manifestBytes)
                    zip.closeEntry()

                    request.imageSources.forEach { source ->
                        zip.putNextEntry(ZipEntry(source.archivePath))
                        val copied = openSourceImage(source).use { input ->
                            input.copyToLimited(zip, PaintDataArchiveLimits.MAX_IMAGE_BYTES)
                        }
                        if (copied == 0L) {
                            throw ArchiveOperationException(PaintDataTransferError.MISSING_IMAGE)
                        }
                        totalBytes += copied
                        if (totalBytes > PaintDataArchiveLimits.MAX_TOTAL_BYTES) {
                            throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
                        }
                        zip.closeEntry()
                    }
                }
            }
            moveReplacing(temporary, destination)
            temporary = null
            PaintDataArchiveResult.Success(Unit)
        } catch (error: ArchiveOperationException) {
            PaintDataArchiveResult.Failure(error.error)
        } catch (_: SecurityException) {
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: FileNotFoundException) {
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: IOException) {
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PaintDataArchiveResult.Failure(PaintDataTransferError.UNKNOWN)
        } finally {
            temporary?.delete()
        }
    }

    override suspend fun extractArchive(
        sourceIdentifier: String,
    ): PaintDataArchiveResult<ExtractedPaintDataArchive> = withContext(Dispatchers.IO) {
        val extractionId = UUID.randomUUID().toString()
        val extractionRoot = File(importsRoot, extractionId)
        try {
            val source = sourceIdentifier.toLocalFileOrNull()?.takeIf(File::isFile)
                ?: throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            if (!extractionRoot.mkdirs()) {
                throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            }
            var manifestJson: String? = null
            val restoredImages = linkedMapOf<String, String>()
            var entryCount = 0
            var totalBytes = 0L

            source.inputStream().buffered().use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount += 1
                        if (entryCount > PaintDataArchiveLimits.MAX_ENTRY_COUNT) {
                            throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
                        }
                        validateEntryName(entry.name)
                        if (entry.isDirectory) {
                            throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
                        }
                        val entryBytes = when {
                            entry.name == MANIFEST_ENTRY -> {
                                if (manifestJson != null) {
                                    throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
                                }
                                val bytes = zip.readBytesLimited(PaintDataArchiveLimits.MAX_MANIFEST_BYTES)
                                manifestJson = bytes.toString(Charsets.UTF_8)
                                bytes.size.toLong()
                            }
                            SAFE_IMAGE_ENTRY.matches(entry.name) -> {
                                if (entry.name in restoredImages) {
                                    throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
                                }
                                val destination = safeDestination(extractionRoot, entry.name)
                                if (
                                    destination.parentFile?.mkdirs() == false &&
                                    destination.parentFile?.isDirectory != true
                                ) {
                                    throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
                                }
                                val copied = destination.outputStream().buffered().use { output ->
                                    zip.copyToLimited(output, PaintDataArchiveLimits.MAX_IMAGE_BYTES)
                                }
                                if (copied == 0L) {
                                    throw ArchiveOperationException(PaintDataTransferError.MISSING_IMAGE)
                                }
                                restoredImages[entry.name] = destination.absolutePath
                                copied
                            }
                            else -> throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
                        }
                        totalBytes += entryBytes
                        if (totalBytes > PaintDataArchiveLimits.MAX_TOTAL_BYTES) {
                            throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
                        }
                        zip.closeEntry()
                    }
                }
            }

            val manifest = manifestJson
                ?: throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
            PaintDataArchiveResult.Success(
                ExtractedPaintDataArchive(
                    extractionId = extractionId,
                    manifestJson = manifest,
                    imagePathsByArchivePath = restoredImages,
                ),
            )
        } catch (error: ArchiveOperationException) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(error.error)
        } catch (_: SecurityException) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: FileNotFoundException) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: ZipException) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(PaintDataTransferError.INVALID_ARCHIVE)
        } catch (_: IOException) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(PaintDataTransferError.INVALID_ARCHIVE)
        } catch (error: CancellationException) {
            extractionRoot.deleteRecursively()
            throw error
        } catch (_: Exception) {
            extractionRoot.deleteRecursively()
            PaintDataArchiveResult.Failure(PaintDataTransferError.UNKNOWN)
        }
    }

    override suspend fun discardExtraction(extractionId: String) {
        withContext(Dispatchers.IO) {
            if (!SAFE_EXTRACTION_ID.matches(extractionId)) return@withContext
            safeDestination(importsRoot, extractionId).deleteRecursively()
        }
    }

    override suspend fun pruneImportedImages(retainedSourceIdentifiers: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                if (!importsRoot.isDirectory) return@withContext
                val canonicalRoot = importsRoot.canonicalFile
                val retainedPaths = retainedSourceIdentifiers.mapNotNullTo(mutableSetOf()) { identifier ->
                    val canonical = identifier.toLocalFileOrNull()?.canonicalFile ?: return@mapNotNullTo null
                    canonical.path.takeIf { canonical.isInside(canonicalRoot) }
                }
                canonicalRoot.walkBottomUp().forEach { file ->
                    when {
                        file == canonicalRoot -> Unit
                        file.isFile && file.canonicalPath !in retainedPaths -> file.delete()
                        file.isDirectory && file.list().isNullOrEmpty() -> file.delete()
                    }
                }
            } catch (_: IOException) {
                // Cleanup is best-effort and must never turn a completed import into a failure.
            } catch (_: SecurityException) {
                // Cleanup is best-effort and must never turn a completed import into a failure.
            }
        }
    }

    private fun validateWriteRequest(request: PaintDataArchiveWriteRequest) {
        if (request.manifestJson.toByteArray(Charsets.UTF_8).size > PaintDataArchiveLimits.MAX_MANIFEST_BYTES) {
            throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
        }
        val archivePaths = request.imageSources.map { it.archivePath }
        if (archivePaths.size + 1 > PaintDataArchiveLimits.MAX_ENTRY_COUNT) {
            throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
        }
        if (archivePaths.size != archivePaths.distinct().size || archivePaths.any { !SAFE_IMAGE_ENTRY.matches(it) }) {
            throw ArchiveOperationException(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY)
        }
    }

    private fun openSourceImage(source: PaintDataArchiveSource): InputStream {
        val file = source.sourceIdentifier.toLocalFileOrNull()?.takeIf(File::isFile)
        return file?.inputStream() ?: throw ArchiveOperationException(PaintDataTransferError.MISSING_IMAGE)
    }

    private fun validateEntryName(name: String) {
        if (name.isBlank() || '\\' in name || name.startsWith('/') || ':' in name) {
            throw ArchiveOperationException(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY)
        }
        if (name.split('/').any { it == "." || it == ".." || it.isBlank() }) {
            throw ArchiveOperationException(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY)
        }
    }

    private fun safeDestination(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val destination = File(canonicalRoot, relativePath).canonicalFile
        val requiredPrefix = canonicalRoot.path + File.separator
        if (destination.path != canonicalRoot.path && !destination.path.startsWith(requiredPrefix)) {
            throw ArchiveOperationException(PaintDataTransferError.UNSAFE_ARCHIVE_ENTRY)
        }
        return destination
    }

    private fun String.toLocalFileOrNull(): File? = runCatching {
        if (startsWith("file:", ignoreCase = true)) File(URI(this)) else File(this)
    }.getOrNull()

    private fun File.isInside(root: File): Boolean = path.startsWith(root.path + File.separator)

    private fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyToLimited(output, maxBytes)
        return output.toByteArray()
    }

    private fun InputStream.copyToLimited(output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw ArchiveOperationException(PaintDataTransferError.ARCHIVE_TOO_LARGE)
            }
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private class ArchiveOperationException(
        val error: PaintDataTransferError,
    ) : IOException()

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        val SAFE_IMAGE_ENTRY = Regex("^images/[A-Za-z0-9][A-Za-z0-9_.-]*$")
        val SAFE_EXTRACTION_ID = Regex("^[a-f0-9-]{36}$")
    }
}
