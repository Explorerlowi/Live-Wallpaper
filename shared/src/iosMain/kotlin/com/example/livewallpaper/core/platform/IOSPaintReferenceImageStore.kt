package com.example.livewallpaper.core.platform

import com.example.livewallpaper.core.coroutines.CoroutineDispatcherProvider
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintReferenceImageStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** iOS Application Support reference image store. */
@OptIn(ExperimentalForeignApi::class)
class IOSPaintReferenceImageStore(
    private val dispatchers: CoroutineDispatcherProvider,
) : PaintReferenceImageStore {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun persistReference(
        sessionId: String,
        imageId: String,
        sourceIdentifier: String,
        mimeType: String,
    ): String? = withContext(dispatchers.io) {
        runCatching {
            val directory = "${applicationSupportRoot()}/aipaint/references/${safeName(sessionId)}"
            fileManager.createDirectoryAtPath(directory, true, null, null)
            val target = "$directory/${safeName(imageId)}.${extension(mimeType)}"
            val source = sourceIdentifier.removePrefix("file://")
            if (source == target) return@runCatching target
            if (fileManager.fileExistsAtPath(target)) fileManager.removeItemAtPath(target, null)
            check(fileManager.copyItemAtPath(source, target, null))
            target
        }.getOrNull()
    }

    override suspend fun discardUnreferenced(
        candidateIdentifiers: Set<String>,
        retainedIdentifiers: Set<String>,
    ) = withContext(dispatchers.io) {
        val root = normalizeAbsolutePath("${applicationSupportRoot()}/aipaint") ?: return@withContext
        val retainedPaths = retainedIdentifiers.mapNotNull(::normalizeAbsolutePath).toSet()
        candidateIdentifiers.mapNotNull(::normalizeAbsolutePath).filterNot(retainedPaths::contains).forEach { path ->
            if (path.isWithin(root)) runCatching { fileManager.removeItemAtPath(path, null) }
        }
    }

    override suspend fun cleanupOrphans(retainedIdentifiers: Set<String>) = withContext(dispatchers.io) {
        val root = normalizeAbsolutePath("${applicationSupportRoot()}/aipaint/references") ?: return@withContext
        val enumerator = fileManager.enumeratorAtPath(root) ?: return@withContext
        val managedPaths = mutableListOf<String>()
        while (true) {
            val relativePath = enumerator.nextObject() as? String ?: break
            normalizeAbsolutePath("$root/$relativePath")?.let(managedPaths::add)
        }
        val retainedPaths = retainedIdentifiers.mapNotNull(::normalizeAbsolutePath).toSet()
        managedPaths.asReversed().forEach { path ->
            val containsRetainedPath = retainedPaths.any { retained -> retained.isWithin(path) && retained != path }
            if (path !in retainedPaths && !containsRetainedPath) {
                runCatching { fileManager.removeItemAtPath(path, null) }
            }
        }
    }

    private fun applicationSupportRoot(): String = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).first() as String

    /** Lexically normalizes an absolute iOS path so URI aliases and `..` cannot bypass root checks. */
    private fun normalizeAbsolutePath(identifier: String): String? {
        val path = identifier.removePrefix("file://")
        if (!path.startsWith('/')) return null
        val segments = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "/${segments.joinToString("/")}"
    }

    private fun String.isWithin(root: String): Boolean = this == root || startsWith("$root/")

    private fun safeName(value: String): String = value.map { if (it.isLetterOrDigit() || it in "-_") it else '_' }
        .joinToString("")

    private fun extension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
}
