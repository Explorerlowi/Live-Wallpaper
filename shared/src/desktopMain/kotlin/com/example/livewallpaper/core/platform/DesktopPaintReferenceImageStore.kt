package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Desktop persistent reference image store under the configured AI-paint root. */
class DesktopPaintReferenceImageStore(
    private val dispatchers: CoroutineDispatcherProvider,
    private val managedRoot: File = DesktopAiPaintStoragePaths.generatedImagesDirectory(),
) : PaintReferenceImageStore {
    private val root: File get() = File(managedRoot, "references")

    override suspend fun persistReference(
        sessionId: String,
        imageId: String,
        sourceIdentifier: String,
        mimeType: String,
    ): String? = withContext(dispatchers.io) {
        runCatching {
            val source = if (sourceIdentifier.startsWith("file:", ignoreCase = true)) {
                File(URI(sourceIdentifier))
            } else {
                File(sourceIdentifier)
            }.canonicalFile
            check(source.isFile)
            val directory = File(root, safeName(sessionId)).apply { check(mkdirs() || isDirectory) }
            val target = File(directory, "${safeName(imageId)}.${extension(mimeType)}").canonicalFile
            if (source == target) return@runCatching target.absolutePath
            val temporary = File(directory, ".${target.name}.${UUID.randomUUID()}.tmp")
            try {
                source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
                runCatching {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                }.getOrElse {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temporary.delete()
            }
            target.absolutePath
        }.getOrNull()
    }

    override suspend fun discardUnreferenced(
        candidateIdentifiers: Set<String>,
        retainedIdentifiers: Set<String>,
    ) = withContext(dispatchers.io) {
        val rootPath = runCatching { managedRoot.canonicalFile.toPath() }.getOrNull() ?: return@withContext
        val retainedPaths = retainedIdentifiers.mapNotNull { identifier ->
            runCatching { File(identifier.removePrefix("file://")).canonicalPath }.getOrNull()
        }.toSet()
        candidateIdentifiers.forEach { identifier ->
            runCatching {
                val file = File(identifier.removePrefix("file://")).canonicalFile
                if (file.canonicalPath !in retainedPaths && file.toPath().startsWith(rootPath)) file.delete()
            }
        }
    }

    override suspend fun cleanupOrphans(retainedIdentifiers: Set<String>) = withContext(dispatchers.io) {
        val retainedPaths = retainedIdentifiers.mapNotNull { identifier ->
            runCatching { File(identifier.removePrefix("file://")).canonicalPath }.getOrNull()
        }.toSet()
        if (!root.isDirectory) return@withContext
        root.walkBottomUp().forEach { file ->
            if (file.isFile && file.canonicalPath !in retainedPaths) file.delete()
            if (file.isDirectory && file != root) file.delete()
        }
    }

    private fun safeName(value: String): String = value.map { if (it.isLetterOrDigit() || it in "-_") it else '_' }
        .joinToString("")

    private fun extension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
}
