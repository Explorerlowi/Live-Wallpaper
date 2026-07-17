package com.example.livewallpaper.paint.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android Storage Access Framework implementation of painting ZIP import and export.
 *
 * @param context Application context used for content resolver and app-owned import storage.
 */
class AndroidPaintDataArchiveGateway(
    private val context: Context,
) : PaintDataArchiveGateway {
    override val clientPlatform: PaintClientPlatform = PaintClientPlatform.ANDROID

    private val contentResolver: ContentResolver = context.contentResolver
    private val importsRoot: File = File(context.filesDir, IMPORTS_DIRECTORY)
    private val exportsCacheRoot: File = File(context.cacheDir, EXPORTS_CACHE_DIRECTORY)

    override suspend fun writeArchive(
        destinationIdentifier: String,
        request: PaintDataArchiveWriteRequest,
    ): PaintDataArchiveResult<Unit> = withContext(Dispatchers.IO) {
        var destination: Uri? = null
        var temporary: File? = null
        try {
            validateWriteRequest(request)
            if (!exportsCacheRoot.isDirectory && !exportsCacheRoot.mkdirs()) {
                throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            }
            temporary = File.createTempFile("paint-backup-", ".zip", exportsCacheRoot)
            writeArchiveToTemporaryFile(temporary, request)

            destination = destinationIdentifier.toUri()
            val output = contentResolver.openOutputStream(destination, "wt")
                ?: throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            temporary.inputStream().buffered().use { input ->
                output.buffered().use { bufferedOutput ->
                    input.copyTo(bufferedOutput)
                }
            }
            PaintDataArchiveResult.Success(Unit)
        } catch (error: ArchiveOperationException) {
            discardFailedDestination(destination)
            PaintDataArchiveResult.Failure(error.error)
        } catch (_: SecurityException) {
            discardFailedDestination(destination)
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: FileNotFoundException) {
            discardFailedDestination(destination)
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (_: IOException) {
            discardFailedDestination(destination)
            PaintDataArchiveResult.Failure(PaintDataTransferError.FILE_ACCESS)
        } catch (error: CancellationException) {
            discardFailedDestination(destination)
            throw error
        } catch (_: Exception) {
            discardFailedDestination(destination)
            PaintDataArchiveResult.Failure(PaintDataTransferError.UNKNOWN)
        } finally {
            temporary?.delete()
        }
    }

    private fun writeArchiveToTemporaryFile(
        temporary: File,
        request: PaintDataArchiveWriteRequest,
    ) {
        temporary.outputStream().buffered().use { bufferedOutput ->
            ZipOutputStream(bufferedOutput).use { zip ->
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
    }

    private fun discardFailedDestination(destination: Uri?) {
        if (destination == null) return
        runCatching { contentResolver.delete(destination, null, null) }
    }

    override suspend fun extractArchive(
        sourceIdentifier: String,
    ): PaintDataArchiveResult<ExtractedPaintDataArchive> = withContext(Dispatchers.IO) {
        val extractionId = UUID.randomUUID().toString()
        val extractionRoot = File(importsRoot, extractionId)
        try {
            if (!extractionRoot.mkdirs()) {
                throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            }
            val source = contentResolver.openInputStream(sourceIdentifier.toUri())
                ?: throw ArchiveOperationException(PaintDataTransferError.FILE_ACCESS)
            var manifestJson: String? = null
            val restoredImages = linkedMapOf<String, String>()
            var entryCount = 0
            var totalBytes = 0L

            source.buffered().use { bufferedInput ->
                ZipInputStream(bufferedInput).use { zip ->
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
                                val manifestBytes = zip.readBytesLimited(PaintDataArchiveLimits.MAX_MANIFEST_BYTES)
                                manifestJson = manifestBytes.toString(Charsets.UTF_8)
                                manifestBytes.size.toLong()
                            }
                            SAFE_IMAGE_ENTRY.matches(entry.name) -> {
                                if (entry.name in restoredImages) {
                                    throw ArchiveOperationException(PaintDataTransferError.INVALID_ARCHIVE)
                                }
                                val destination = safeDestination(extractionRoot, entry.name)
                                destination.parentFile?.mkdirs()
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
            val extractionRoot = safeDestination(importsRoot, extractionId)
            extractionRoot.deleteRecursively()
        }
    }

    override suspend fun pruneImportedImages(retainedSourceIdentifiers: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                if (!importsRoot.isDirectory) return@withContext
                val canonicalRoot = importsRoot.canonicalFile
                val retainedPaths = retainedSourceIdentifiers.mapNotNullTo(mutableSetOf()) { identifier ->
                    val file = identifier.toLocalFileOrNull() ?: return@mapNotNullTo null
                    val canonical = file.canonicalFile
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
        val identifier = source.sourceIdentifier
        val parsed = identifier.toUri()
        val input = when (parsed.scheme?.lowercase()) {
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_ANDROID_RESOURCE,
            -> contentResolver.openInputStream(parsed)
            ContentResolver.SCHEME_FILE -> parsed.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
            null -> File(identifier).takeIf(File::isFile)?.inputStream()
            else -> null
        }
        return input ?: throw ArchiveOperationException(PaintDataTransferError.MISSING_IMAGE)
    }

    private fun validateEntryName(name: String) {
        if (name.isBlank() || '\\' in name || name.startsWith('/') || ":" in name) {
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

    private fun String.toLocalFileOrNull(): File? {
        val uri = toUri()
        return when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)
            null -> File(this)
            else -> null
        }
    }

    private fun File.isInside(root: File): Boolean {
        val rootPrefix = root.path + File.separator
        return path.startsWith(rootPrefix)
    }

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

    private class ArchiveOperationException(
        val error: PaintDataTransferError,
    ) : IOException()

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val IMPORTS_DIRECTORY = "aipaint/imports"
        const val EXPORTS_CACHE_DIRECTORY = "aipaint/exports"
        val SAFE_IMAGE_ENTRY = Regex("^images/[A-Za-z0-9][A-Za-z0-9_.-]*$")
        val SAFE_EXTRACTION_ID = Regex("^[a-f0-9-]{36}$")
    }
}
