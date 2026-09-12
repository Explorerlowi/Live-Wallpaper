package com.example.livewallpaper.core.design.util

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val LIST_END_TOLERANCE_PX = 2
private const val SCROLL_READY_TIMEOUT_MS = 800L
private const val ITEM_LAYOUT_TIMEOUT_MS = 400L

/**
 * Returns whether a conversation list has composed the requested content and is safe to scroll.
 *
 * @param displayedCount Number of messages currently shown in the list.
 * @param laidOutCount Number of items [androidx.compose.foundation.lazy.LazyListLayoutInfo] knows.
 * @param hasTargetMessage `true` when the requested message is already in the displayed list,
 * or when the caller is not waiting on a specific message.
 */
fun conversationListIsReadyToScroll(
    displayedCount: Int,
    laidOutCount: Int,
    hasTargetMessage: Boolean,
): Boolean {
    if (!hasTargetMessage) return false
    if (displayedCount <= 0) return true
    return laidOutCount >= displayedCount
}

/**
 * Returns whether [targetMessageId] is present in the currently displayed conversation items.
 *
 * A `null` target means the caller only needs the list itself, such as the jump-to-bottom control.
 */
fun conversationListHasTargetMessage(
    displayedMessageIds: Iterable<String>,
    targetMessageId: String?,
): Boolean = targetMessageId == null || displayedMessageIds.any { it == targetMessageId }

/**
 * Returns whether the list is visually pinned to the latest content.
 *
 * `canScrollForward` / `canScrollBackward` are not enough on their own: trailing item spacing can
 * keep `canScrollForward` true at the real end, and `scrollToItem(last)` leaves the newest message
 * at the viewport start. A tall last item whose end extends past the viewport is also not pinned.
 *
 * @param reverseLayout `true` when newest items sit at index 0, matching a reversed chat list.
 */
fun LazyListState.isAtListEnd(reverseLayout: Boolean): Boolean {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    val lastItem = info.visibleItemsInfo.firstOrNull { it.index == lastIndex }
    return conversationListIsAtEnd(
        reverseLayout = reverseLayout,
        totalItemsCount = info.totalItemsCount,
        visibleItemCount = info.visibleItemsInfo.size,
        canScrollForward = canScrollForward,
        canScrollBackward = canScrollBackward,
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        lastVisibleIndex = lastItem?.index,
        lastItemEnd = lastItem?.endOffsetIncludingSpacing(info.mainAxisItemSpacing, info.afterContentPadding)
            ?: 0,
        viewportEndOffset = info.viewportEndOffset,
    )
}

/**
 * Pure visual-end check used by [isAtListEnd] so the alignment rules can be unit-tested.
 *
 * [lastItemEnd] must include trailing item spacing and after-content padding so a list that is
 * already at LazyColumn's natural rest position is not treated as away from the bottom.
 */
internal fun conversationListIsAtEnd(
    reverseLayout: Boolean,
    totalItemsCount: Int,
    visibleItemCount: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    lastVisibleIndex: Int?,
    lastItemEnd: Int,
    viewportEndOffset: Int,
): Boolean {
    if (totalItemsCount <= 0 || visibleItemCount <= 0) return true
    if (!canScrollForward && !canScrollBackward) return true
    return if (reverseLayout) {
        firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= LIST_END_TOLERANCE_PX
    } else {
        lastVisibleIndex == totalItemsCount - 1 &&
            abs(lastItemEnd - viewportEndOffset) <= LIST_END_TOLERANCE_PX
    }
}

/**
 * Waits until [isReady] is true, then scrolls so the newest content sits at the visual bottom.
 *
 * @param listState Snapshot-readable provider; used after waiting so a session switch can
 * pick up the list that is currently on screen.
 * @param reverseLayout `true` when newest items sit at index 0.
 * @param animate Whether the final alignment may animate when already near the end.
 * @param forceAnchor `true` after sending a message so the list re-anchors onto the new item even
 * if the previous newest row is still reported as index 0.
 * @param isReady Snapshot-readable predicate; typically checks that the target message is composed.
 */
suspend fun awaitReadyAndScrollToListEnd(
    listState: () -> LazyListState,
    reverseLayout: Boolean,
    animate: Boolean,
    forceAnchor: Boolean = false,
    isReady: () -> Boolean,
) {
    withTimeoutOrNull(SCROLL_READY_TIMEOUT_MS) {
        snapshotFlow(isReady).first { it }
    }
    // Insertion + keys often need a second measure pass before index 0 is the new message.
    withFrameNanos { }
    withFrameNanos { }
    val state = listState()
    if (state.layoutInfo.totalItemsCount <= 0) return
    state.scrollToListEnd(
        reverseLayout = reverseLayout,
        animate = animate,
        forceAnchor = forceAnchor,
    )
}

/**
 * Scrolls a message list so the newest content sits at the visual bottom.
 *
 * Long-distance movement always snaps. Only a short remaining alignment may animate, so sending a
 * message cannot get stuck in a slow `animateScrollToItem` that a later request would cancel.
 *
 * @param reverseLayout `true` when newest items sit at index 0.
 * @param animate Whether to animate the short alignment pass when already near the newest item.
 * @param forceAnchor Re-anchors onto the newest item even when the list already reports index 0.
 */
suspend fun LazyListState.scrollToListEnd(
    reverseLayout: Boolean,
    animate: Boolean,
    forceAnchor: Boolean = false,
) {
    if (layoutInfo.totalItemsCount <= 0) return
    withFrameNanos { }
    repeat(4) { pass ->
        val useAnimation = animate && pass == 0
        val shouldForce = forceAnchor || pass > 0
        if (reverseLayout) {
            scrollReversedListToNewest(animate = useAnimation, forceAnchor = shouldForce)
        } else {
            scrollForwardListToNewest(animate = useAnimation)
        }
        withFrameNanos { }
        if (isAtListEnd(reverseLayout)) return
    }
}

private suspend fun LazyListState.scrollReversedListToNewest(
    animate: Boolean,
    forceAnchor: Boolean,
) {
    if (layoutInfo.totalItemsCount <= 0) return
    val nearNewest = firstVisibleItemIndex <= 1
    val needsAnchor = forceAnchor || firstVisibleItemIndex != 0
    if (needsAnchor) {
        scrollToItem(0)
        awaitItemLaidOut(index = 0)
        if (firstVisibleItemIndex != 0) {
            scrollToItem(0)
            awaitItemLaidOut(index = 0)
        }
    }
    val leftover = -firstVisibleItemScrollOffset.toFloat()
    if (abs(leftover) > LIST_END_TOLERANCE_PX) {
        if (animate && nearNewest) animateScrollBy(leftover) else scrollBy(leftover)
    }
}

private suspend fun LazyListState.scrollForwardListToNewest(animate: Boolean) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    val lastAlreadyVisible = layoutInfo.visibleItemsInfo.any { it.index == lastIndex }
    if (!lastAlreadyVisible) {
        snapLastItemToVisualEnd(lastIndex)
    }
    val currentLastIndex = layoutInfo.totalItemsCount - 1
    if (currentLastIndex != lastIndex && currentLastIndex >= 0) {
        snapLastItemToVisualEnd(currentLastIndex)
    }
    val extra = endAlignmentDelta() ?: return
    if (abs(extra) <= LIST_END_TOLERANCE_PX) return
    if (animate && lastAlreadyVisible) animateScrollBy(extra) else scrollBy(extra)
}

/**
 * Snaps [index] to the visual bottom in one measure pass.
 *
 * A positive [LazyListState.scrollToItem] offset asks LazyColumn to place the item as far from the
 * viewport start as possible, avoiding a flash of the newest row at the top.
 */
private suspend fun LazyListState.snapLastItemToVisualEnd(index: Int) {
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    scrollToItem(index, scrollOffset = viewport)
    awaitItemLaidOut(index)
}

private suspend fun LazyListState.awaitItemLaidOut(index: Int) {
    if (!layoutInfo.visibleItemsInfo.any { it.index == index }) {
        withTimeoutOrNull(ITEM_LAYOUT_TIMEOUT_MS) {
            snapshotFlow { layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        }
    }
    val measuredSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
    if (measuredSize <= 0) {
        withTimeoutOrNull(ITEM_LAYOUT_TIMEOUT_MS) {
            snapshotFlow {
                layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
            }.first { it > 0 }
        }
    }
    withFrameNanos { }
}

private fun LazyListState.endAlignmentDelta(): Float? {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return null
    val lastVisible = info.visibleItemsInfo.firstOrNull { it.index == lastIndex } ?: return null
    return (
        lastVisible.endOffsetIncludingSpacing(info.mainAxisItemSpacing, info.afterContentPadding) -
            info.viewportEndOffset
        ).toFloat()
}

private fun LazyListItemInfo.endOffsetIncludingSpacing(
    mainAxisItemSpacing: Int,
    afterContentPadding: Int,
): Int = offset + size + mainAxisItemSpacing + afterContentPadding
