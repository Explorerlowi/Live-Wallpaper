package com.example.livewallpaper.feature.aipaint.presentation.state

/**
 * Requests the conversation list to pin the latest content to the visual bottom.
 *
 * @param animate `true` after sending so the list eases to the newest item; `false` for the
 * jump-to-bottom control, which should land immediately.
 * @param messageId When set, the UI waits until this message is composed and laid out before
 * scrolling, then re-anchors onto it. Used after sending so the scroll does not run against the
 * previous last item.
 */
data class ScrollToLatestRequest(
    val animate: Boolean,
    val messageId: String? = null,
)
