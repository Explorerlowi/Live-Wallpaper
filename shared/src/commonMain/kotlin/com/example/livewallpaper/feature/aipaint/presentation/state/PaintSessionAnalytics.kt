package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.MessageStatus
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import kotlin.math.roundToInt

/**
 * Current paint session analytics snapshot.
 *
 * This model keeps aggregation logic in `shared`, while platform UIs are only
 * responsible for formatting and rendering.
 */
data class PaintSessionAnalytics(
    val sessionTitle: String?,
    val generatingCount: Int,
    val modelDisplayName: String,
    val aspectRatioDisplayName: String,
    val resolutionDisplayName: String?,
    val activeProfileName: String?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val lastGeneratedAt: Long?,
    val promptCount: Int,
    val generationAttemptCount: Int,
    val generatedImageCount: Int,
    val retryCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val referenceImageCount: Int,
    val threadCount: Int,
    val successRate: Float,
    val averageDurationMillis: Long,
    val averageOutputPerSuccess: Float?,
    val averagePromptChars: Int?,
    val draftPromptLength: Int,
    val draftImageCount: Int,
    val draftImageLimit: Int,
    val draftHintType: PaintDraftHintType,
    val recentPrompts: List<PaintRecentPromptAnalytics>
)

/**
 * Draft recommendation type for the current session input state.
 */
enum class PaintDraftHintType {
    Empty,
    LimitReached,
    TextReady,
    Ready
}

/**
 * Recent prompt analytics item.
 */
data class PaintRecentPromptAnalytics(
    val content: String,
    val timestamp: Long,
    val referenceCount: Int
)

/**
 * Builds a session analytics snapshot from current paint session state.
 *
 * @param session current selected session, nullable when the user is still on the temporary draft
 * @param messages all current session messages
 * @param promptDraft current unsent prompt content
 * @param selectedImages current unsent reference images
 * @param generatingCount number of in-flight generation tasks
 * @param selectedModel currently selected generation model
 * @param selectedAspectRatio currently selected aspect ratio
 * @param selectedResolution currently selected resolution
 * @param activeProfileName current active API profile name
 * @return aggregated analytics for presentation
 */
fun calculatePaintSessionAnalytics(
    session: PaintSession?,
    messages: List<PaintMessage>,
    promptDraft: String,
    selectedImages: List<SelectedImage>,
    generatingCount: Int,
    selectedModel: PaintModel,
    selectedAspectRatio: AspectRatio,
    selectedResolution: Resolution,
    activeProfileName: String?
): PaintSessionAnalytics {
    val userMessages = messages.filter { it.senderIdentity == SenderIdentity.USER }
    val assistantMessages = messages.filter { it.senderIdentity == SenderIdentity.ASSISTANT }
    val completedMessages = assistantMessages.filter {
        it.status == MessageStatus.SUCCESS || it.status == MessageStatus.ERROR
    }
    val successMessages = assistantMessages.filter { it.status == MessageStatus.SUCCESS }
    val failedMessages = assistantMessages.filter { it.status == MessageStatus.ERROR }
    val pendingMessages = assistantMessages.filter {
        it.status == MessageStatus.PENDING || it.status == MessageStatus.GENERATING
    }

    val generatedImageCount = successMessages.sumOf { message ->
        message.images.count { image -> !image.isReference }
    }
    val referenceImageCount = userMessages.sumOf { message ->
        message.images.count { image -> image.isReference }
    }
    val uniqueThreads = assistantMessages.map { it.versionGroup ?: it.id }.distinct().size
    val retryCount = (assistantMessages.size - uniqueThreads).coerceAtLeast(0)

    val averageDurationMillis = completedMessages
        .map { (it.updatedAt - it.createdAt).coerceAtLeast(0L) }
        .average()
        .takeIf { !it.isNaN() }
        ?.roundToInt()
        ?.toLong()
        ?: 0L

    val averagePromptChars = userMessages
        .map { it.messageContent.trim().length }
        .average()
        .takeIf { !it.isNaN() }
        ?.roundToInt()
        ?.takeIf { it > 0 }

    val averageOutputPerSuccess = if (successMessages.isNotEmpty()) {
        generatedImageCount.toFloat() / successMessages.size.toFloat()
    } else {
        null
    }

    val successRate = if (assistantMessages.isNotEmpty()) {
        successMessages.size.toFloat() / assistantMessages.size.toFloat()
    } else {
        0f
    }

    val recentPrompts = userMessages
        .sortedByDescending { it.createdAt }
        .take(4)
        .map { message ->
            PaintRecentPromptAnalytics(
                content = message.messageContent,
                timestamp = message.createdAt,
                referenceCount = message.images.count { image -> image.isReference }
            )
        }

    val draftPromptLength = promptDraft.trim().length
    val draftHintType = when {
        draftPromptLength == 0 && selectedImages.isEmpty() -> PaintDraftHintType.Empty
        selectedImages.size >= selectedModel.maxImages -> PaintDraftHintType.LimitReached
        draftPromptLength > 0 && selectedImages.isEmpty() -> PaintDraftHintType.TextReady
        else -> PaintDraftHintType.Ready
    }

    return PaintSessionAnalytics(
        sessionTitle = session?.title?.takeIf { it.isNotBlank() },
        generatingCount = generatingCount,
        modelDisplayName = selectedModel.displayName,
        aspectRatioDisplayName = selectedAspectRatio.displayName,
        resolutionDisplayName = if (selectedModel.supportsResolution) {
            selectedResolution.displayName
        } else {
            null
        },
        activeProfileName = activeProfileName,
        createdAt = session?.createdAt,
        updatedAt = session?.updatedAt,
        lastGeneratedAt = successMessages.maxOfOrNull { it.updatedAt },
        promptCount = userMessages.size,
        generationAttemptCount = assistantMessages.size,
        generatedImageCount = generatedImageCount,
        retryCount = retryCount,
        successCount = successMessages.size,
        failedCount = failedMessages.size,
        pendingCount = pendingMessages.size,
        referenceImageCount = referenceImageCount,
        threadCount = uniqueThreads,
        successRate = successRate,
        averageDurationMillis = averageDurationMillis,
        averageOutputPerSuccess = averageOutputPerSuccess,
        averagePromptChars = averagePromptChars,
        draftPromptLength = draftPromptLength,
        draftImageCount = selectedImages.size,
        draftImageLimit = selectedModel.maxImages,
        draftHintType = draftHintType,
        recentPrompts = recentPrompts
    )
}
