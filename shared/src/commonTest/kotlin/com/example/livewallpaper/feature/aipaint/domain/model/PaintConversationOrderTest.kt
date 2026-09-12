package com.example.livewallpaper.feature.aipaint.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** 校验会话预览图按消息顺序优先、其次版本顺序排列。 */
class PaintConversationOrderTest {
    @Test
    fun previewPathsFollowMessageThenVersionOrder() {
        val user1 = message(
            id = "user-1",
            sender = SenderIdentity.USER,
            createdAt = 1,
            paths = listOf("ref-1"),
        )
        val assistant1v0 = message(
            id = "asst-1-v0",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 2,
            parentUserMessageId = "user-1",
            versionGroup = "group-1",
            versionIndex = 0,
            paths = listOf("a-v0-1", "a-v0-2"),
        )
        val user2 = message(
            id = "user-2",
            sender = SenderIdentity.USER,
            createdAt = 3,
        )
        val assistant2v0 = message(
            id = "asst-2-v0",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 4,
            parentUserMessageId = "user-2",
            versionGroup = "group-2",
            versionIndex = 0,
            paths = listOf("b-v0"),
        )
        val assistant1v1 = message(
            id = "asst-1-v1",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 5,
            parentUserMessageId = "user-1",
            versionGroup = "group-1",
            versionIndex = 1,
            paths = listOf("a-v1"),
        )

        val paths = listOf(user1, assistant1v0, user2, assistant2v0, assistant1v1)
            .shuffled()
            .conversationPreviewImagePaths()

        assertEquals(
            listOf("ref-1", "a-v0-1", "a-v0-2", "a-v1", "b-v0"),
            paths,
        )
    }

    @Test
    fun previewPathsKeepFirstOccurrenceWhenDuplicated() {
        val user = message(
            id = "user-1",
            sender = SenderIdentity.USER,
            createdAt = 1,
            paths = listOf("shared"),
        )
        val assistant = message(
            id = "asst-1",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 2,
            parentUserMessageId = "user-1",
            versionGroup = "group-1",
            paths = listOf("shared", "unique"),
        )

        assertEquals(
            listOf("shared", "unique"),
            listOf(user, assistant).conversationPreviewImagePaths(),
        )
    }

    @Test
    fun visibleMessagesKeepActiveVersionInConversationOrder() {
        val user1 = message(id = "user-1", sender = SenderIdentity.USER, createdAt = 1)
        val assistant1v0 = message(
            id = "asst-1-v0",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 2,
            parentUserMessageId = "user-1",
            versionGroup = "group-1",
            versionIndex = 0,
        )
        val assistant1v1 = message(
            id = "asst-1-v1",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 5,
            parentUserMessageId = "user-1",
            versionGroup = "group-1",
            versionIndex = 1,
        )
        val user2 = message(id = "user-2", sender = SenderIdentity.USER, createdAt = 3)
        val assistant2 = message(
            id = "asst-2",
            sender = SenderIdentity.ASSISTANT,
            createdAt = 4,
            parentUserMessageId = "user-2",
            versionGroup = "group-2",
        )

        val visible = listOf(user1, assistant1v0, user2, assistant2, assistant1v1)
            .visibleWithActiveVersions(mapOf("group-1" to 1))

        assertEquals(
            listOf("user-1", "asst-1-v1", "user-2", "asst-2"),
            visible.map(PaintMessage::id),
        )
    }

    private fun message(
        id: String,
        sender: SenderIdentity,
        createdAt: Long,
        parentUserMessageId: String? = null,
        versionGroup: String? = null,
        versionIndex: Int = 0,
        paths: List<String> = emptyList(),
    ): PaintMessage {
        return PaintMessage(
            id = id,
            sessionId = "session",
            senderIdentity = sender,
            messageContent = id,
            messageType = if (paths.isEmpty()) MessageType.TEXT else MessageType.IMAGE,
            images = paths.mapIndexed { index, path ->
                PaintImage(id = "$id-$index", localPath = path)
            },
            createdAt = createdAt,
            updatedAt = createdAt,
            parentUserMessageId = parentUserMessageId,
            versionGroup = versionGroup,
            versionIndex = versionIndex,
        )
    }
}
