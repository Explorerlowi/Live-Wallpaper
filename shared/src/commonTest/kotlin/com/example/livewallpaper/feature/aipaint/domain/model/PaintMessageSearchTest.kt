package com.example.livewallpaper.feature.aipaint.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 校验会话内关键字搜索的匹配规则、排序与摘要生成。 */
class PaintMessageSearchTest {

    @Test
    fun blankKeywordReturnsNoHits() {
        val messages = listOf(message("a", "hello world", createdAt = 1))
        assertTrue(messages.searchPaintMessages("").isEmpty())
        assertTrue(messages.searchPaintMessages("   ").isEmpty())
    }

    @Test
    fun matchIsCaseInsensitiveAndOrderedOldToNew() {
        val newer = message("new", "A Cat sleeps", createdAt = 3)
        val older = message("old", "the CAT runs", createdAt = 1)
        val unrelated = message("dog", "a dog barks", createdAt = 2)

        val hits = listOf(newer, unrelated, older).searchPaintMessages("cat")

        assertEquals(listOf("old", "new"), hits.map(PaintMessageSearchHit::messageId))
    }

    @Test
    fun searchCoversEveryVersionInGroup() {
        val user = message("user", "draw", createdAt = 1, sender = SenderIdentity.USER)
        val v0 = message(
            id = "v0",
            content = "sunset over sea",
            createdAt = 2,
            parentUserMessageId = "user",
            versionGroup = "g",
            versionIndex = 0,
        )
        val v1 = message(
            id = "v1",
            content = "sunset over mountains",
            createdAt = 3,
            parentUserMessageId = "user",
            versionGroup = "g",
            versionIndex = 1,
        )

        val hits = listOf(user, v1, v0).searchPaintMessages("sunset")

        assertEquals(listOf("v0", "v1"), hits.map(PaintMessageSearchHit::messageId))
        assertEquals(listOf(0, 1), hits.map(PaintMessageSearchHit::versionIndex))
        assertEquals("g", hits.first().versionGroup)
    }

    @Test
    fun snippetKeepsMatchRangeAlignedAfterTruncation() {
        val padding = "x".repeat(80)
        val content = padding + "keyword" + padding
        val hit = listOf(message("m", content, createdAt = 1)).searchPaintMessages("KEYWORD").single()

        assertEquals("keyword", hit.snippet.substring(hit.snippetMatchRange))
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
    }

    @Test
    fun shortContentIsNotTruncated() {
        val hit = listOf(message("m", "line one\nline two", createdAt = 1)).searchPaintMessages("two").single()

        assertEquals("line one line two", hit.snippet)
        assertEquals("two", hit.snippet.substring(hit.snippetMatchRange))
    }

    @Test
    fun keywordMatchRangesFindsAllOccurrences() {
        val ranges = "Ab ab AB".keywordMatchRanges("ab")
        assertEquals(listOf(0..1, 3..4, 6..7), ranges)
        assertTrue("abc".keywordMatchRanges("").isEmpty())
    }

    private fun message(
        id: String,
        content: String,
        createdAt: Long,
        sender: SenderIdentity = SenderIdentity.ASSISTANT,
        parentUserMessageId: String? = null,
        versionGroup: String? = null,
        versionIndex: Int = 0,
    ): PaintMessage = PaintMessage(
        id = id,
        sessionId = "session",
        senderIdentity = sender,
        messageContent = content,
        messageType = MessageType.TEXT,
        createdAt = createdAt,
        updatedAt = createdAt,
        parentUserMessageId = parentUserMessageId,
        versionGroup = versionGroup,
        versionIndex = versionIndex,
    )
}
