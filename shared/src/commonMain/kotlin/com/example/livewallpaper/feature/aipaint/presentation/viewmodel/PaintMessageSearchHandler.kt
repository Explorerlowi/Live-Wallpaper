package com.example.livewallpaper.feature.aipaint.presentation.viewmodel

import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessageSearchHit
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintEvent
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintMessageSearchTransition
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.withMessageSearchClosed
import com.example.livewallpaper.feature.aipaint.presentation.state.withMessageSearchOpened
import com.example.livewallpaper.feature.aipaint.presentation.state.withMessageSearchQuery
import com.example.livewallpaper.feature.aipaint.presentation.state.withMessageSearchStep
import com.example.livewallpaper.feature.aipaint.presentation.state.withMessageSearchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 把搜索相关的 [PaintEvent] 应用到状态流上，供各平台 ViewModel 复用，避免重复业务逻辑。
 *
 * @param event 任意事件；非搜索事件会被忽略。
 * @return 需要滚动定位的命中；事件不涉及定位或没有命中时为 `null`。
 */
fun MutableStateFlow<PaintUiState>.handleMessageSearchEvent(event: PaintEvent): PaintMessageSearchHit? {
    return when (event) {
        PaintEvent.OpenMessageSearch -> {
            update { it.withMessageSearchOpened() }
            null
        }
        PaintEvent.CloseMessageSearch -> {
            update { it.withMessageSearchClosed() }
            null
        }
        is PaintEvent.UpdateMessageSearchQuery -> {
            update { it.withMessageSearchQuery(event.query) }
            null
        }
        is PaintEvent.StepMessageSearch -> updateWithTarget { it.withMessageSearchStep(event.forward) }
        is PaintEvent.JumpToMessage -> updateWithTarget { it.withMessageSearchTarget(event.messageId) }
        else -> null
    }
}

private fun MutableStateFlow<PaintUiState>.updateWithTarget(
    transform: (PaintUiState) -> PaintMessageSearchTransition,
): PaintMessageSearchHit? {
    var target: PaintMessageSearchHit? = null
    update { state ->
        val transition = transform(state)
        target = transition.target
        transition.state
    }
    return target
}
