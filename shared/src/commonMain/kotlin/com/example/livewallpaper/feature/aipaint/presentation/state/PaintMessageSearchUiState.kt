package com.example.livewallpaper.feature.aipaint.presentation.state

import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessageSearchHit
import com.example.livewallpaper.feature.aipaint.domain.model.searchPaintMessages

/**
 * 当前会话内关键字搜索的 UI 状态。
 *
 * @property isActive 搜索栏是否展开。
 * @property query 用户输入的关键字。
 * @property hits 命中列表，按会话顺序（旧 → 新）排列。
 * @property currentHitIndex 当前定位到的命中下标；`-1` 表示尚未定位。
 * @property highlightedMessageId 需要在消息列表中高亮的消息 id。
 */
data class PaintMessageSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val hits: List<PaintMessageSearchHit> = emptyList(),
    val currentHitIndex: Int = -1,
    val highlightedMessageId: String? = null,
) {
    /** 当前定位到的命中，未定位时为 `null`。 */
    val currentHit: PaintMessageSearchHit? get() = hits.getOrNull(currentHitIndex)
}

/**
 * 搜索步进或点击结果后的产物：新的 UI 状态以及需要滚动定位的目标命中。
 *
 * @property state 更新后的状态。
 * @property target 需要滚动到的命中；无命中时为 `null`。
 */
data class PaintMessageSearchTransition(
    val state: PaintUiState,
    val target: PaintMessageSearchHit?,
)

/** 展开搜索栏并清空上一次的输入。 */
fun PaintUiState.withMessageSearchOpened(): PaintUiState =
    copy(messageSearch = PaintMessageSearchUiState(isActive = true))

/** 关闭搜索栏并清除高亮。 */
fun PaintUiState.withMessageSearchClosed(): PaintUiState =
    copy(messageSearch = PaintMessageSearchUiState())

/**
 * 更新关键字并重算命中列表；定位下标重置，首次「上一条」会从最新的命中开始。
 *
 * @param query 新的关键字。
 */
fun PaintUiState.withMessageSearchQuery(query: String): PaintUiState = copy(
    messageSearch = messageSearch.copy(
        isActive = true,
        query = query,
        hits = messages.searchPaintMessages(query),
        currentHitIndex = -1,
        highlightedMessageId = null,
    ),
)

/**
 * 消息列表变化（新消息、删除、版本增减）后重算命中，并尽量保留当前定位的消息。
 *
 * 搜索未激活或关键字为空时不做任何事。
 */
fun PaintUiState.recomputeMessageSearch(): PaintUiState {
    val search = messageSearch
    if (!search.isActive || search.query.isBlank()) return this
    val hits = messages.searchPaintMessages(search.query)
    val keptId = search.currentHit?.messageId
    val keptIndex = if (keptId == null) -1 else hits.indexOfFirst { it.messageId == keptId }
    return copy(
        messageSearch = search.copy(
            hits = hits,
            currentHitIndex = keptIndex,
            highlightedMessageId = if (keptIndex >= 0) keptId else null,
        ),
    )
}

/**
 * 在命中列表中循环步进。
 *
 * 列表为旧 → 新，`forward = true` 表示向更新的消息移动；尚未定位时，「向更早」从最新命中开始，
 * 「向更新」从最早命中开始。
 *
 * @param forward `true` 走向更新的命中，`false` 走向更早的命中。
 * @return 新状态与目标命中；无命中时状态不变且目标为 `null`。
 */
fun PaintUiState.withMessageSearchStep(forward: Boolean): PaintMessageSearchTransition {
    val hits = messageSearch.hits
    if (hits.isEmpty()) return PaintMessageSearchTransition(this, null)
    val current = messageSearch.currentHitIndex
    val next = when {
        current !in hits.indices -> if (forward) 0 else hits.lastIndex
        forward -> (current + 1) % hits.size
        else -> (current - 1 + hits.size) % hits.size
    }
    return focusMessageSearchHit(next)
}

/**
 * 点击结果列表中的某条命中后定位到该消息。
 *
 * @param messageId 目标消息 id。
 * @return 新状态与目标命中；id 不在命中列表中时状态不变且目标为 `null`。
 */
fun PaintUiState.withMessageSearchTarget(messageId: String): PaintMessageSearchTransition {
    val index = messageSearch.hits.indexOfFirst { it.messageId == messageId }
    if (index < 0) return PaintMessageSearchTransition(this, null)
    return focusMessageSearchHit(index)
}

/**
 * 把定位下标设为 [index]，同步高亮并在需要时切换版本组的展示版本，保证目标消息可见。
 */
private fun PaintUiState.focusMessageSearchHit(index: Int): PaintMessageSearchTransition {
    val hit = messageSearch.hits[index]
    val group = hit.versionGroup
    val versions = if (group == null || activeVersions[group] == hit.versionIndex) {
        activeVersions
    } else {
        activeVersions + (group to hit.versionIndex)
    }
    val state = copy(
        activeVersions = versions,
        messageSearch = messageSearch.copy(
            currentHitIndex = index,
            highlightedMessageId = hit.messageId,
        ),
    )
    return PaintMessageSearchTransition(state, hit)
}
