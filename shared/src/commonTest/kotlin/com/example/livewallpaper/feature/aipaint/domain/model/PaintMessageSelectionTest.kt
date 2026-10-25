package com.example.livewallpaper.feature.aipaint.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 校验消息多选的对比图片、复制到其他会话和转移筛选。 */
class PaintMessageSelectionTest {
    @Test
    fun oneMessageStartsWithTheFirstTwoImages() {
        val only = message(id = "only", paths = listOf("a", "b", "c"))
        val plan = selectedComparePlan(listOf(only), listOf("only"))
        assertEquals(listOf("a", "b", "c"), plan?.paths)
        assertEquals(0, plan?.bottomIndex)
        assertEquals(1, plan?.topIndex)
    }

    @Test
    fun multipleMessagesStartWithTheFirstImageOfTheFirstTwo() {
        val first = message(id = "first", paths = listOf("a1", "a2"))
        val second = message(id = "second", createdAt = 2, paths = listOf("b1"))
        val third = message(id = "third", createdAt = 3, paths = listOf("c1"))
        val plan = selectedComparePlan(listOf(third, first, second), listOf("second", "first", "third"))
        assertEquals(listOf("b1", "a1", "a2", "c1"), plan?.paths)
        assertEquals(0, plan?.bottomIndex)
        assertEquals(1, plan?.topIndex)
    }

    @Test
    fun fewerThanTwoImagesCannotCompare() {
        val only = message(id = "only", paths = listOf("a"))
        assertEquals(null, selectedComparePlan(listOf(only), listOf("only")))
    }

    @Test
    fun copiedMessagesKeepOrderAndRetargetLinks() {
        val user = message(id = "user", createdAt = 1, content = "提示")
        val assistant = message(
            id = "assistant",
            createdAt = 2,
            updatedAt = 14_000,
            content = "结果",
            sender = SenderIdentity.ASSISTANT,
            parentUserMessageId = "user",
            versionGroup = "group",
            paths = listOf("image"),
        )
        val generating = message(id = "generating", status = MessageStatus.GENERATING)
        val messages = listOf(assistant, generating, user)
        var nextId = 0
        val copies = messages.copiedSelectedMessages(
            selectedMessageIds = listOf("assistant", "user"),
            currentSessionId = "session",
            targetSessionId = "target",
            newId = { "new-${nextId++}" },
        )
        assertTrue(
            messages.copiedSelectedMessages(
                selectedMessageIds = listOf("generating", "assistant", "user"),
                currentSessionId = "session",
                targetSessionId = "target",
                newId = { "blocked" },
            ).isEmpty(),
        )

        assertEquals(listOf("提示", "结果"), copies.map(PaintMessage::messageContent))
        assertEquals("target", copies.first().sessionId)
        assertEquals(listOf(1L, 2L), copies.map(PaintMessage::createdAt))
        assertEquals(14_000L, copies[1].updatedAt)
        assertEquals(copies[0].id, copies[1].parentUserMessageId)
        assertEquals("new-2", copies[1].versionGroup)
        assertEquals("image", copies[1].images.single().localPath)
        assertEquals(0, copies[1].images.single().width)
        assertTrue(copies[1].images.single().id != "assistant-0")
    }

    @Test
    fun copyMoveAndDeleteIncludeEveryFinishedVersion() {
        val older = message(
            id = "v0",
            createdAt = 2,
            content = "旧",
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 0,
        )
        val visible = message(
            id = "v1",
            createdAt = 3,
            content = "新",
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 1,
        )
        val latest = message(
            id = "v2",
            createdAt = 4,
            content = "最新",
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 2,
        )
        val unrelated = message(id = "other", versionGroup = "other-group", sender = SenderIdentity.ASSISTANT)
        val messages = listOf(older, visible, latest, unrelated)
        var nextId = 0
        val copies = messages.copiedSelectedMessages(
            selectedMessageIds = listOf("v1"),
            currentSessionId = "session",
            targetSessionId = "target",
            newId = { "new-${nextId++}" },
        )

        assertEquals(listOf("旧", "新", "最新"), copies.map(PaintMessage::messageContent))
        assertEquals(listOf(0, 1, 2), copies.map(PaintMessage::versionIndex))
        assertEquals(copies[0].versionGroup, copies[1].versionGroup)
        assertEquals(
            listOf("v0", "v1", "v2"),
            messages.movableSelectedMessages(listOf("v1"), "session").map(PaintMessage::id),
        )
        assertEquals(setOf("v0", "v1", "v2"), messages.versionSiblingIds(listOf("v1"), "session"))
        assertTrue("other" !in messages.versionSiblingIds(listOf("v1"), "session"))
    }

    @Test
    fun generatingVersionBlocksCopyMoveAndDelete() {
        val older = message(
            id = "v0",
            createdAt = 2,
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 0,
        )
        val visible = message(
            id = "v1",
            createdAt = 3,
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 1,
        )
        val generating = message(
            id = "v2",
            createdAt = 4,
            sender = SenderIdentity.ASSISTANT,
            versionGroup = "group",
            versionIndex = 2,
            status = MessageStatus.GENERATING,
        )
        val ready = message(id = "ready", createdAt = 5, content = "ok")
        val messages = listOf(older, visible, generating, ready)

        assertTrue(messages.selectionTouchesGenerating(listOf("v1"), "session"))
        assertTrue(messages.selectionTouchesGenerating(listOf("v2"), "session"))
        assertTrue(messages.movableSelectedMessages(listOf("v1", "ready"), "session").isEmpty())
        assertTrue(
            messages.copiedSelectedMessages(
                selectedMessageIds = listOf("v1"),
                currentSessionId = "session",
                targetSessionId = "target",
                newId = { "new" },
            ).isEmpty(),
        )
        assertEquals(
            listOf("ready"),
            messages.movableSelectedMessages(listOf("ready"), "session").map(PaintMessage::id),
        )
    }

    @Test
    fun movableMessagesSkipOtherSessions() {
        val ready = message(id = "ready", createdAt = 2, content = "ok")
        val otherSession = message(id = "other", sessionId = "elsewhere")
        val moving = listOf(otherSession, ready).movableSelectedMessages(
            selectedMessageIds = listOf("other", "ready"),
            currentSessionId = "session",
        )
        assertEquals(listOf("ready"), moving.map(PaintMessage::id))
    }

    @Test
    fun failedVersionsAreRemovedWhenAnotherStatusRemains() {
        val failed = version("failed", index = 0, status = MessageStatus.ERROR)
        val success = version("success", index = 1)
        val cancelled = version("cancelled", index = 2, status = MessageStatus.CANCELLED)
        val generating = version("generating", index = 3, status = MessageStatus.GENERATING)
        val removing = listOf(success, generating, cancelled, failed).failedVersionIdsToRemove(activePosition = 1)
        assertEquals(setOf("failed"), removing)
    }

    @Test
    fun allFailedVersionsKeepTheOneOnScreen() {
        val versions = listOf(
            version("first", index = 0, status = MessageStatus.ERROR),
            version("current", index = 1, status = MessageStatus.ERROR),
            version("latest", index = 2, status = MessageStatus.ERROR),
        )
        assertEquals(setOf("first", "latest"), versions.failedVersionIdsToRemove(activePosition = 1))
        assertEquals(setOf("first", "current"), versions.failedVersionIdsToRemove(activePosition = 9))
        assertEquals(emptySet(), listOf(versions.first()).failedVersionIdsToRemove(activePosition = 0))
        assertEquals(emptySet(), versions.take(2).map { it.copy(status = MessageStatus.SUCCESS) }
            .failedVersionIdsToRemove(activePosition = 0))
    }

    @Test
    fun removingActiveVersionStepsBackWithinTheGroup() {
        val older = message(id = "v0", versionGroup = "group", versionIndex = 0)
        val middle = message(id = "v1", versionGroup = "group", versionIndex = 1, createdAt = 2)
        val active = message(id = "v2", versionGroup = "group", versionIndex = 2, createdAt = 3)
        val versions = activeVersionsAfterRemoving(
            messages = listOf(older, middle, active),
            activeVersions = mapOf("group" to 2),
            removedIds = setOf("v2"),
        )
        assertEquals(1, versions["group"])
        assertTrue("missing" !in versions)
    }

    private fun version(
        id: String,
        index: Int,
        status: MessageStatus = MessageStatus.SUCCESS,
    ): PaintMessage = message(
        id = id,
        createdAt = index.toLong() + 1,
        sender = SenderIdentity.ASSISTANT,
        status = status,
        versionGroup = "group",
        versionIndex = index,
    )

    private fun message(
        id: String,
        createdAt: Long = 1,
        updatedAt: Long = createdAt,
        content: String = id,
        sessionId: String = "session",
        sender: SenderIdentity = SenderIdentity.USER,
        parentUserMessageId: String? = null,
        status: MessageStatus = MessageStatus.SUCCESS,
        versionGroup: String? = null,
        versionIndex: Int = 0,
        paths: List<String> = emptyList(),
    ): PaintMessage {
        return PaintMessage(
            id = id,
            sessionId = sessionId,
            senderIdentity = sender,
            messageContent = content,
            messageType = if (paths.isEmpty()) MessageType.TEXT else MessageType.IMAGE,
            images = paths.mapIndexed { index, path -> PaintImage(id = "$id-$index", localPath = path) },
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status,
            parentUserMessageId = parentUserMessageId,
            versionGroup = versionGroup,
            versionIndex = versionIndex,
        )
    }
}
