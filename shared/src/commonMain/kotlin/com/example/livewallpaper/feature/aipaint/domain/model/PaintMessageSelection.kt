package com.example.livewallpaper.feature.aipaint.domain.model

/**
 * 多选消息进入对比时的图片和初始上下层。
 *
 * 图片按点选消息的顺序排列，同一条消息内保持原有顺序。
 * 只有一条带图消息时，初始对比第 1、2 张。
 * 有多条带图消息时，初始对比第一条和第二条消息各自的第一张。
 *
 * @property paths 可选图片路径。
 * @property bottomIndex 下层图片在 [paths] 中的位置。
 * @property topIndex 上层图片在 [paths] 中的位置。
 */
data class SelectedComparePlan(
    val paths: List<String>,
    val bottomIndex: Int,
    val topIndex: Int,
)

/**
 * 按用户的选择顺序收集可对比图片，并给出初始对比的两张。
 *
 * 至少两张图片时才返回方案。空白路径不参与对比。
 *
 * @param messages 当前会话中的消息，包含未展示的历史版本。
 * @param selectedMessageIds 用户点选消息的顺序。
 * @return 对比方案；不足两张图片时为 null。
 */
fun selectedComparePlan(
    messages: List<PaintMessage>,
    selectedMessageIds: List<String>,
): SelectedComparePlan? {
    val byId = messages.associateBy(PaintMessage::id)
    val groups = selectedMessageIds.mapNotNull { id ->
        byId[id]?.images.orEmpty().mapNotNull { image ->
            image.localPath?.takeIf(String::isNotBlank)
        }.takeIf { it.isNotEmpty() }
    }
    val paths = groups.flatten()
    if (paths.size < 2) return null
    val bottomIndex = 0
    val topIndex = if (groups.size == 1) 1 else groups.first().size
    if (topIndex !in paths.indices || topIndex == bottomIndex) return null
    return SelectedComparePlan(paths, bottomIndex, topIndex)
}

/**
 * 按用户的选择顺序收集可对比的本地图片路径。
 *
 * 同一条消息里的图片保持原有顺序。空白路径不参与对比。
 *
 * @param messages 当前会话中的消息，包含未展示的历史版本。
 * @param selectedMessageIds 用户点选消息的顺序。
 * @return 可用于对比的本地路径。至少两张时才应打开对比。
 */
fun selectedComparableImagePaths(
    messages: List<PaintMessage>,
    selectedMessageIds: List<String>,
): List<String> = selectedComparePlan(messages, selectedMessageIds)?.paths.orEmpty()

/**
 * 复制选中消息，供写入另一个会话。原消息保持不变。
 *
 * 新消息使用新的 ID。创建时间和更新时间保持原值，因此气泡上的时间和完成时长不变。
 * 同一版本组里的版本会一起复制，并映射到新的组 ID。
 * 选中消息或其同一版本组里有正在生成的版本时，整次复制会被拒绝。
 * 若关联的用户消息也在本次复制中，助手回复会指向新的用户消息；否则断开该关联。
 * 图片沿用原文件路径、尺寸和类型，但使用新的图片 ID，避免主键冲突。
 *
 * @param selectedMessageIds 用户选中的消息 ID。
 * @param currentSessionId 当前会话。不属于该会话的消息会被忽略。
 * @param targetSessionId 要写入的会话。
 * @param newId 生成消息、图片和版本组 ID。
 * @return 可直接插入目标会话的新消息。没有可复制消息时为空。
 */
fun List<PaintMessage>.copiedSelectedMessages(
    selectedMessageIds: List<String>,
    currentSessionId: String,
    targetSessionId: String,
    newId: () -> String,
): List<PaintMessage> {
    val source = movableSelectedMessages(selectedMessageIds, currentSessionId)
    if (source.isEmpty()) return emptyList()
    val messageIds = source.associate { it.id to newId() }
    val groupIds = source.mapNotNull { it.versionGroup }.distinct().associateWith { newId() }
    return source.map { message ->
        message.copy(
            id = messageIds.getValue(message.id),
            sessionId = targetSessionId,
            images = message.images.map { image -> image.copy(id = newId()) },
            parentUserMessageId = message.parentUserMessageId?.let(messageIds::get),
            versionGroup = message.versionGroup?.let(groupIds::get),
        )
    }
}

/**
 * 选出可以复制或转移到其他会话的消息，并带上同一版本组的全部版本。
 *
 * 选中消息或其同一版本组里有正在生成的版本时返回空列表，整次操作应被拒绝。
 * 顺序按会话时间，不按点选顺序。
 *
 * @param selectedMessageIds 用户选中的消息 ID，通常只包含当前展示的版本。
 * @param currentSessionId 当前会话。不属于该会话的 ID 会被忽略。
 * @return 按会话顺序排列的消息。涉及正在生成的版本时为空。
 */
fun List<PaintMessage>.movableSelectedMessages(
    selectedMessageIds: List<String>,
    currentSessionId: String,
): List<PaintMessage> {
    if (selectionTouchesGenerating(selectedMessageIds, currentSessionId)) return emptyList()
    return selectedWithVersionSiblings(
        selectedMessageIds = selectedMessageIds,
        currentSessionId = currentSessionId,
        includeGenerating = false,
    )
}

/**
 * 判断选中消息或其同一版本组里是否有正在生成的版本。
 *
 * 有的话复制、转移和删除都应整次拒绝，并提示用户不能进行该操作。
 *
 * @param selectedMessageIds 用户选中的消息 ID。
 * @param currentSessionId 当前会话。
 * @return 选区涉及正在生成的消息时为 true。
 */
fun List<PaintMessage>.selectionTouchesGenerating(
    selectedMessageIds: List<String>,
    currentSessionId: String,
): Boolean {
    val siblingIds = versionSiblingIds(selectedMessageIds, currentSessionId)
    return any { message -> message.id in siblingIds && message.status == MessageStatus.GENERATING }
}

/**
 * 把选中消息扩展到同一会话里的整个版本组，包含正在生成的版本。
 *
 * @param selectedMessageIds 用户选中的消息 ID。
 * @param currentSessionId 当前会话。
 * @return 同一版本组中的消息 ID。
 */
fun List<PaintMessage>.versionSiblingIds(
    selectedMessageIds: List<String>,
    currentSessionId: String,
): Set<String> {
    val inSession = filter { it.sessionId == currentSessionId }
    val selectedIds = selectedMessageIds.toSet()
    val selectedGroups = inSession
        .filter { it.id in selectedIds }
        .mapNotNull { it.versionGroup }
        .toSet()
    return inSession
        .filter { it.id in selectedIds || (it.versionGroup != null && it.versionGroup in selectedGroups) }
        .map { it.id }
        .toSet()
}

private fun List<PaintMessage>.selectedWithVersionSiblings(
    selectedMessageIds: List<String>,
    currentSessionId: String,
    includeGenerating: Boolean,
): List<PaintMessage> {
    val siblingIds = versionSiblingIds(selectedMessageIds, currentSessionId)
    return filter { message ->
        message.id in siblingIds &&
            (includeGenerating || message.status != MessageStatus.GENERATING)
    }.sortedByConversationAndVersion()
}

/**
 * 移除若干消息后，修正版本组当前展示的位置。
 *
 * 规则与单条删除一致：移除当前版本时回退到上一版本，移除更早版本时当前位置前移。
 *
 * @param messages 移除前的完整消息列表。
 * @param activeVersions 版本组到当前展示位置的映射。
 * @param removedIds 即将离开当前会话的消息 ID。
 * @return 新的版本位置映射。被清空的版本组会从映射中移除。
 */
fun activeVersionsAfterRemoving(
    messages: List<PaintMessage>,
    activeVersions: Map<String, Int>,
    removedIds: Set<String>,
): Map<String, Int> {
    if (removedIds.isEmpty()) return activeVersions
    var remaining = messages
    var versions = activeVersions
    messages.filter { it.id in removedIds }.forEach { removed ->
        versions = versions.afterRemovingOne(remaining, removed)
        remaining = remaining.filterNot { it.id == removed.id }
    }
    return versions
}

private fun Map<String, Int>.afterRemovingOne(
    messages: List<PaintMessage>,
    removed: PaintMessage,
): Map<String, Int> {
    val group = removed.versionGroup ?: return this
    val versionsInGroup = messages.filter { it.versionGroup == group }.sortedBy { it.versionIndex }
    if (versionsInGroup.size <= 1) return this - group
    val currentPosition = versionsInGroup.indexOfFirst { it.id == removed.id }
    val activeIndex = this[group] ?: currentPosition
    val newPosition = when {
        currentPosition < 0 -> activeIndex
        currentPosition < activeIndex -> activeIndex - 1
        currentPosition == activeIndex && currentPosition > 0 -> currentPosition - 1
        currentPosition == activeIndex -> 0
        else -> activeIndex
    }
    return this + (group to newPosition)
}
