package com.example.livewallpaper.feature.aipaint.presentation.viewmodel

import com.example.livewallpaper.core.util.TimeProvider
import com.example.livewallpaper.feature.aipaint.domain.model.activeVersionsAfterRemoving
import com.example.livewallpaper.feature.aipaint.domain.model.copiedSelectedMessages
import com.example.livewallpaper.feature.aipaint.domain.model.failedVersionIdsToRemove
import com.example.livewallpaper.feature.aipaint.domain.model.movableSelectedMessages
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintRepository
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * 把当前会话中选中的消息移动到目标会话。
 *
 * 图片继续使用原来的绝对路径。存储层按引用计数决定能否删除文件，
 * 因此消息离开原会话后，文件不会因为原会话被删除而提前消失。
 * 创建时间、更新时间和生成参数保持原值，完成时长不会被改写。
 * 同一版本组的全部版本会一起转移。选区里有正在生成的版本时，整次转移不会执行。
 *
 * @param messageIds 选中的消息 ID。
 * @param targetSessionId 目标会话 ID，不能是当前会话。
 */
suspend fun MutableStateFlow<PaintUiState>.moveSelectedMessages(
    repository: PaintRepository,
    messageIds: List<String>,
    targetSessionId: String,
) {
    val state = value
    val currentSessionId = state.currentSession?.id ?: return
    if (targetSessionId == currentSessionId) return
    val target = state.sessions.find { it.id == targetSessionId } ?: return
    val moving = state.messages.movableSelectedMessages(messageIds, currentSessionId)
    if (moving.isEmpty()) return
    val removedIds = moving.map { it.id }.toSet()
    update { current ->
        current.copy(
            activeVersions = activeVersionsAfterRemoving(
                messages = current.messages,
                activeVersions = current.activeVersions,
                removedIds = removedIds,
            ),
        )
    }
    moving.forEach { message ->
        repository.updateMessage(message.copy(sessionId = targetSessionId), touchUpdatedAt = false)
    }
    repository.updateSession(target.copy(updatedAt = TimeProvider.currentTimeMillis()))
}

/**
 * 把当前会话中选中的消息复制到目标会话。
 *
 * 原消息保留。副本使用新 ID，正文、图片、生成参数以及创建和完成时间都与原消息一致。
 * 同一版本组的全部版本会一起复制。选区里有正在生成的版本时，整次复制不会执行。
 *
 * @param messageIds 选中的消息 ID。
 * @param targetSessionId 目标会话 ID，不能是当前会话。
 */
suspend fun MutableStateFlow<PaintUiState>.copySelectedMessages(
    repository: PaintRepository,
    messageIds: List<String>,
    targetSessionId: String,
) {
    val state = value
    val currentSessionId = state.currentSession?.id ?: return
    if (targetSessionId == currentSessionId) return
    val target = state.sessions.find { it.id == targetSessionId } ?: return
    val copies = state.messages.copiedSelectedMessages(
        selectedMessageIds = messageIds,
        currentSessionId = currentSessionId,
        targetSessionId = targetSessionId,
        newId = ::newPaintRecordId,
    )
    if (copies.isEmpty()) return
    copies.forEach { message -> repository.addMessage(message) }
    repository.updateSession(target.copy(updatedAt = TimeProvider.currentTimeMillis()))
}

private fun newPaintRecordId(): String =
    "${TimeProvider.currentTimeMillis()}-${Random.nextInt(10000, 99999)}"

/**
 * 删除当前会话中选中的消息，并修正仍留在会话里的版本位置。
 *
 * 同一版本组的全部版本会一起删除。选区里有正在生成的版本时，整次删除不会执行。
 *
 * @param messageIds 要删除的消息 ID。
 */
suspend fun MutableStateFlow<PaintUiState>.deleteSelectedMessages(
    repository: PaintRepository,
    messageIds: List<String>,
) {
    val state = value
    val currentSessionId = state.currentSession?.id ?: return
    val removing = state.messages.movableSelectedMessages(messageIds, currentSessionId)
    if (removing.isEmpty()) return
    val removedIds = removing.map { it.id }.toSet()
    update { current ->
        current.copy(
            activeVersions = activeVersionsAfterRemoving(
                messages = current.messages,
                activeVersions = current.activeVersions,
                removedIds = removedIds,
            ),
        )
    }
    removing.forEach { message -> repository.deleteMessage(message.id) }
}

/**
 * 删除同一版本组中的失败版本，并修正仍留在会话里的版本位置。
 *
 * 组里还有其他状态的版本时，失败版本全部删除。
 * 如果这一组全部失败，保留当前正在查看的一条。
 *
 * @param versionGroup 版本组 ID。
 * @return 是否删除了至少一条失败版本。
 */
suspend fun MutableStateFlow<PaintUiState>.deleteFailedVersions(
    repository: PaintRepository,
    versionGroup: String,
): Boolean {
    val versions = value.messages.filter { it.versionGroup == versionGroup }.sortedBy { it.versionIndex }
    if (versions.isEmpty()) return false
    val activePosition = (value.activeVersions[versionGroup] ?: versions.lastIndex)
        .coerceIn(0, versions.lastIndex)
    val removedIds = versions.failedVersionIdsToRemove(activePosition)
    if (removedIds.isEmpty()) return false
    val removing = versions.filter { it.id in removedIds }
    update { current ->
        current.copy(
            activeVersions = activeVersionsAfterRemoving(
                messages = current.messages,
                activeVersions = current.activeVersions,
                removedIds = removedIds,
            ),
        )
    }
    removing.forEach { message -> repository.deleteMessage(message.id) }
    return true
}
