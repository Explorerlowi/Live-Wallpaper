package com.example.livewallpaper.core.design.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies conversation list end-alignment and scroll-ready rules. */
internal class LazyListScrollTest {

    @Test
    fun readyWhenTargetIsPresentAndLaidOut() {
        assertTrue(
            conversationListIsReadyToScroll(
                displayedCount = 4,
                laidOutCount = 4,
                hasTargetMessage = true,
            )
        )
    }

    @Test
    fun notReadyUntilTargetMessageIsComposed() {
        assertFalse(
            conversationListIsReadyToScroll(
                displayedCount = 3,
                laidOutCount = 3,
                hasTargetMessage = false,
            )
        )
    }

    @Test
    fun notReadyWhenNewItemHasNotLaidOut() {
        assertFalse(
            conversationListIsReadyToScroll(
                displayedCount = 4,
                laidOutCount = 3,
                hasTargetMessage = true,
            )
        )
    }

    @Test
    fun emptyConversationIsReady() {
        assertTrue(
            conversationListIsReadyToScroll(
                displayedCount = 0,
                laidOutCount = 0,
                hasTargetMessage = true,
            )
        )
    }

    @Test
    fun jumpToBottomIsReadyWhenListIsAlreadyLaidOut() {
        assertTrue(
            conversationListIsReadyToScroll(
                displayedCount = 6,
                laidOutCount = 6,
                hasTargetMessage = conversationListHasTargetMessage(
                    displayedMessageIds = listOf("a", "b"),
                    targetMessageId = null,
                ),
            )
        )
    }

    @Test
    fun sendIsNotReadyUntilTargetIdIsInDisplayedList() {
        assertFalse(
            conversationListHasTargetMessage(
                displayedMessageIds = listOf("old"),
                targetMessageId = "new",
            )
        )
    }

    @Test
    fun reverseLayoutPinsToNewestItemStart() {
        assertTrue(
            conversationListIsAtEnd(
                reverseLayout = true,
                totalItemsCount = 8,
                visibleItemCount = 3,
                canScrollForward = true,
                canScrollBackward = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                lastVisibleIndex = 2,
                lastItemEnd = 640,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun reverseLayoutIsNotAtEndWhileScrolledIntoNewestItem() {
        assertFalse(
            conversationListIsAtEnd(
                reverseLayout = true,
                totalItemsCount = 8,
                visibleItemCount = 2,
                canScrollForward = true,
                canScrollBackward = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 120,
                lastVisibleIndex = 1,
                lastItemEnd = 800,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun forwardLayoutIsNotAtEndWhenLastItemSitsAtTop() {
        assertFalse(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 6,
                visibleItemCount = 1,
                canScrollForward = false,
                canScrollBackward = true,
                firstVisibleItemIndex = 5,
                firstVisibleItemScrollOffset = 0,
                lastVisibleIndex = 5,
                lastItemEnd = 180,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun forwardLayoutIsNotAtEndWhenTallLastItemSitsAtTop() {
        assertFalse(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 6,
                visibleItemCount = 1,
                canScrollForward = true,
                canScrollBackward = true,
                firstVisibleItemIndex = 5,
                firstVisibleItemScrollOffset = 0,
                lastVisibleIndex = 5,
                lastItemEnd = 1500,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun forwardLayoutPinsWhenLastItemMeetsViewportEnd() {
        assertTrue(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 6,
                visibleItemCount = 3,
                canScrollForward = false,
                canScrollBackward = true,
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 40,
                lastVisibleIndex = 5,
                lastItemEnd = 900,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun forwardLayoutPinsWhenOnlyTrailingItemSpacingRemains() {
        assertTrue(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 6,
                visibleItemCount = 3,
                canScrollForward = true,
                canScrollBackward = true,
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 40,
                lastVisibleIndex = 5,
                lastItemEnd = 900,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun forwardLayoutIsNotAtEndWhenLastItemIsNotVisible() {
        assertFalse(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 8,
                visibleItemCount = 3,
                canScrollForward = true,
                canScrollBackward = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 12,
                lastVisibleIndex = null,
                lastItemEnd = 0,
                viewportEndOffset = 900,
            )
        )
    }

    @Test
    fun entireListFittingViewportCountsAsAtEnd() {
        assertTrue(
            conversationListIsAtEnd(
                reverseLayout = false,
                totalItemsCount = 2,
                visibleItemCount = 2,
                canScrollForward = false,
                canScrollBackward = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                lastVisibleIndex = 1,
                lastItemEnd = 240,
                viewportEndOffset = 900,
            )
        )
    }
}
