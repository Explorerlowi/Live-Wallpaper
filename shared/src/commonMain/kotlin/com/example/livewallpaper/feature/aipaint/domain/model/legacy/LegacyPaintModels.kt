package com.example.livewallpaper.feature.aipaint.domain.model.legacy

import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.domain.model.*
import kotlinx.serialization.Serializable

/** Serialization-only image shape used by legacy preferences and v1 backup manifests. */
@Serializable
internal data class LegacyPaintImageV1(
    val id: String,
    val localPath: String? = null,
    val base64Data: String? = null,
    val mimeType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0,
    val isReference: Boolean = false,
)

/** Maps a current image to the v1 wire shape without ever restoring embedded image data. */
internal fun PaintImage.toLegacyV1(localPath: String? = this.localPath): LegacyPaintImageV1 = LegacyPaintImageV1(
    id = id,
    localPath = localPath,
    base64Data = null,
    mimeType = mimeType,
    width = width,
    height = height,
    isReference = isReference,
)

/** Maps a parsed legacy image into the Base64-free domain model. */
internal fun LegacyPaintImageV1.toDomain(localPath: String? = this.localPath): PaintImage = PaintImage(
    id = id,
    localPath = localPath,
    mimeType = mimeType,
    width = width,
    height = height,
    isReference = isReference,
)

/** Serialization-only message shape retaining the removed embedded Base64 field. */
@Serializable
internal data class LegacyPaintMessageV1(
    val id: String,
    val sessionId: String,
    val senderIdentity: SenderIdentity,
    val messageContent: String,
    val reasoningContent: String? = null,
    val messageType: MessageType,
    val images: List<LegacyPaintImageV1> = emptyList(),
    val createdAt: Long = TimeProvider.currentTimeMillis(),
    val updatedAt: Long = TimeProvider.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SUCCESS,
    val originPlatform: PaintClientPlatform = PaintClientPlatform.UNKNOWN,
    val parentUserMessageId: String? = null,
    val versionGroup: String? = null,
    val versionIndex: Int = 0,
    val generationModel: PaintModel? = null,
    val generationAspectRatio: AspectRatio? = null,
    val generationResolution: Resolution? = null,
    val generationGptSize: GptImageSize? = null,
    val generationGptQuality: GptImageQuality? = null,
    val generationGptFormat: GptOutputFormat? = null,
) {
    /** Builds the current message after legacy images have been normalized to local files. */
    fun toDomain(images: List<PaintImage>): PaintMessage = PaintMessage(
        id = id,
        sessionId = sessionId,
        senderIdentity = senderIdentity,
        messageContent = messageContent,
        reasoningContent = reasoningContent,
        messageType = messageType,
        images = images,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        originPlatform = originPlatform,
        parentUserMessageId = parentUserMessageId,
        versionGroup = versionGroup,
        versionIndex = versionIndex,
        generationModel = generationModel,
        generationAspectRatio = generationAspectRatio,
        generationResolution = generationResolution,
        generationGptSize = generationGptSize,
        generationGptQuality = generationGptQuality,
        generationGptFormat = generationGptFormat,
    )
}

/** Maps a current message to the compatibility manifest shape. */
internal fun PaintMessage.toLegacyV1(images: List<LegacyPaintImageV1>): LegacyPaintMessageV1 =
    LegacyPaintMessageV1(
        id = id,
        sessionId = sessionId,
        senderIdentity = senderIdentity,
        messageContent = messageContent,
        reasoningContent = reasoningContent,
        messageType = messageType,
        images = images,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        originPlatform = originPlatform,
        parentUserMessageId = parentUserMessageId,
        versionGroup = versionGroup,
        versionIndex = versionIndex,
        generationModel = generationModel,
        generationAspectRatio = generationAspectRatio,
        generationResolution = generationResolution,
        generationGptSize = generationGptSize,
        generationGptQuality = generationGptQuality,
        generationGptFormat = generationGptFormat,
    )

/** Strict Base64 decoder shared only by legacy storage and v1 backup compatibility paths. */
internal object LegacyBase64 {
    /** Strictly decodes padded Base64 and returns null for malformed legacy content. */
    fun decode(content: String): ByteArray? = runCatching {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val lookup = IntArray(256) { -1 }.also { table ->
            alphabet.forEachIndexed { index, character -> table[character.code] = index }
        }
        val clean = content.filterNot(Char::isWhitespace)
        require(clean.isNotEmpty() && clean.length % 4 == 0)
        val firstPadding = clean.indexOf('=')
        val padding = if (firstPadding < 0) 0 else clean.length - firstPadding
        require(padding in 0..2)
        require(firstPadding < 0 || clean.substring(firstPadding).all { it == '=' })
        val output = ByteArray(clean.length / 4 * 3 - padding)
        var outputIndex = 0
        clean.chunked(4).forEachIndexed { chunkIndex, chunk ->
            val values = chunk.mapIndexed { index, character ->
                if (character == '=') {
                    require(chunkIndex == clean.length / 4 - 1 && index >= 2)
                    0
                } else {
                    lookup.getOrNull(character.code)?.takeIf { it >= 0 } ?: error("Invalid Base64")
                }
            }
            val combined = (values[0] shl 18) or (values[1] shl 12) or (values[2] shl 6) or values[3]
            if (outputIndex < output.size) output[outputIndex++] = (combined shr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (combined shr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = combined.toByte()
        }
        output
    }.getOrNull()
}
