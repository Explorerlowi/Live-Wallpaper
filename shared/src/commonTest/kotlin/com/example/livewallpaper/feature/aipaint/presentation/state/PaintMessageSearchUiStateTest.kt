package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.MessageType
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 校验搜索状态的步进循环、点击定位、版本切换与消息变更后的索引修正。 */
class PaintMessageSearchUiStateTest {

    private val user = message("user", "cat please", createdAt = 1, sender = SenderIdentity.USER)
    private val v0 = message("v0", "a cat v0", createdAt = 2, group = "g", versionIndex = 0, parent = "user")
    private val v1 = message("v1", "a cat v1", createdAt = 3, group = "g", versionIndex = 1, parent = "user")
    private val later = message("later", "another cat", createdAt = 4, sender = SenderIdentity.USER)

    private val base = PaintUiState(
        messages = listOf(user, v0, v1, later),
        activeVersions = mapOf("g" to 1),
    )

    @Test
    fun queryRecomputesHitsAndResetsPosition() {
        val state = base.withMessageSearchOpened().withMessageSearchQuery("cat")

        assertTrue(state.messageSearch.isActive)
        assertEquals(listOf("user", "v0", "v1", "later"), state.messageSearch.hits.map { it.messageId })
        assertEquals(-1, state.messageSearch.currentHitIndex)
        assertNull(state.messageSearch.highlightedMessageId)
    }

    @Test
    fun backwardStepStartsFromNewestAndWrapsAround() {
        var state = base.withMessageSearchQuery("cat")

        val first = state.withMessageSearchStep(forward = false)
        assertEquals("later", first.target?.messageId)
        state = first.state
        state = state.withMessageSearchStep(forward = false).state
        state = state.withMessageSearchStep(forward = false).state
        val fourth = state.withMessageSearchStep(forward = false)
        assertEquals("user", fourth.target?.messageId)
        val wrapped = fourth.state.withMessageSearchStep(forward = false)
        assertEquals("later", wrapped.target?.messageId)
        assertEquals("later", wrapped.state.messageSearch.highlightedMessageId)
    }

    @Test
    fun forwardStepStartsFromOldest() {
        val transition = base.withMessageSearchQuery("cat").withMessageSearchStep(forward = true)
        assertEquals("user", transition.target?.messageId)
        assertEquals(0, transition.state.messageSearch.currentHitIndex)
    }

    @Test
    fun jumpingToHiddenVersionSwitchesActiveVersion() {
        val transition = base.withMessageSearchQuery("cat").withMessageSearchTarget("v0")

        assertEquals("v0", transition.target?.messageId)
        assertEquals(0, transition.state.activeVersions["g"])
        assertEquals(1, transition.state.messageSearch.currentHitIndex)
    }

    @Test
    fun jumpingToUnknownMessageKeepsState() {
        val state = base.withMessageSearchQuery("cat")
        val transition = state.withMessageSearchTarget("missing")
        assertEquals(state, transition.state)
        assertNull(transition.target)
    }

    @Test
    fun recomputeKeepsCurrentHitWhenMessagesChange() {
        val focused = base.withMessageSearchQuery("cat").withMessageSearchTarget("v1").state
        val extra = message("extra", "cat again", createdAt = 5, sender = SenderIdentity.USER)

        val recomputed = focused.copy(messages = focused.messages + extra).recomputeMessageSearch()

        assertEquals(5, recomputed.messageSearch.hits.size)
        assertEquals("v1", recomputed.messageSearch.currentHit?.messageId)
        assertEquals("v1", recomputed.messageSearch.highlightedMessageId)
    }

    @Test
    fun recomputeClearsPositionWhenCurrentHitIsDeleted() {
        val focused = base.withMessageSearchQuery("cat").withMessageSearchTarget("later").state

        val recomputed = focused.copy(messages = focused.messages - later).recomputeMessageSearch()

        assertEquals(-1, recomputed.messageSearch.currentHitIndex)
        assertNull(recomputed.messageSearch.highlightedMessageId)
    }

    @Test
    fun closingClearsEverything() {
        val closed = base.withMessageSearchQuery("cat").withMessageSearchTarget("v1").state.withMessageSearchClosed()
        assertFalse(closed.messageSearch.isActive)
        assertEquals(PaintMessageSearchUiState(), closed.messageSearch)
    }

    private fun message(
        id: String,
        content: String,
        createdAt: Long,
        sender: SenderIdentity = SenderIdentity.ASSISTANT,
        group: String? = null,
        versionIndex: Int = 0,
        parent: String? = null,
    ): PaintMessage = PaintMessage(
        id = id,
        sessionId = "session",
        senderIdentity = sender,
        messageContent = content,
        messageType = MessageType.TEXT,
        createdAt = createdAt,
        updatedAt = createdAt,
        parentUserMessageId = parent,
        versionGroup = group,
        versionIndex = versionIndex,
    )
}
