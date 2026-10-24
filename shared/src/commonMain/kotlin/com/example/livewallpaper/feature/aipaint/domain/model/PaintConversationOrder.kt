package com.example.livewallpaper.feature.aipaint.domain.model

/**
 * 按会话位置排列消息，同一位置的助手回复再按版本号递增。
 *
 * 排序键：所属用户消息时间 → 用户消息先于助手回复 → [PaintMessage.versionIndex] → [PaintMessage.createdAt]。
 *
 * @return 从旧到新的消息列表，包含全部版本。
 */
fun List<PaintMessage>.sortedByConversationAndVersion(): List<PaintMessage> {
    val userMessageTimes = associateUserMessageTimes()
    return sortedWith(conversationAndVersionComparator(userMessageTimes))
}

/**
 * 过滤到当前展示的版本后，按会话消息顺序（旧 → 新）排列。
 *
 * 配合 `reverseLayout` 时，调用方再 `asReversed()`，使最新消息位于 index 0。
 *
 * @param activeVersions 版本组到当前展示位置的映射；缺省时使用该组最新版本。
 * @return 仅包含当前可见版本的消息，按会话顺序从旧到新。
 */
fun List<PaintMessage>.visibleWithActiveVersions(activeVersions: Map<String, Int>): List<PaintMessage> {
    val versionGroups = filter { it.versionGroup != null }.groupBy { it.versionGroup!! }
    return filter { message ->
        val group = message.versionGroup ?: return@filter true
        val versions = versionGroups[group].orEmpty().sortedBy { it.versionIndex }
        val activePosition = (activeVersions[group] ?: versions.lastIndex)
            .coerceIn(0, versions.lastIndex)
        versions.getOrNull(activePosition)?.id == message.id
    }.sortedByConversationAndVersion()
}

/**
 * 计算重新生成时的下一个版本号，以及它加入后的列表位置。
 *
 * 版本号取现有最大版本号再加一，不用剩余条数。
 * 删掉更早的版本后，新版本仍排在留下来的版本后面。
 *
 * @param existingVersionIndexes 版本组里已经存在的版本号。
 * @return 新版本号，以及它在按版本号排序的列表中的位置。
 */
fun nextPaintVersion(existingVersionIndexes: List<Int>): Pair<Int, Int> {
    val nextIndex = (existingVersionIndexes.maxOrNull() ?: -1) + 1
    return nextIndex to existingVersionIndexes.size
}

/**
 * 会话预览图左右切换使用的路径列表。
 *
 * 顺序为消息顺序优先，其次同一消息的版本顺序，最后是该条消息内的图片顺序。
 * 相同路径只保留第一次出现。
 *
 * @return 从旧到新的本地图片路径。
 */
fun List<PaintMessage>.conversationPreviewImagePaths(): List<String> {
    return sortedByConversationAndVersion()
        .flatMap { message ->
            message.images.mapNotNull { image ->
                image.localPath?.takeIf { path -> path.isNotBlank() }
            }
        }
        .distinct()
}

private fun List<PaintMessage>.associateUserMessageTimes(): Map<String, Long> {
    return asSequence()
        .filter { it.senderIdentity == SenderIdentity.USER }
        .associate { it.id to it.createdAt }
}

private fun conversationAndVersionComparator(
    userMessageTimes: Map<String, Long>,
): Comparator<PaintMessage> {
    return compareBy<PaintMessage> { message ->
        message.conversationThreadTime(userMessageTimes)
    }.thenBy { message ->
        if (message.senderIdentity == SenderIdentity.USER) 0 else 1
    }.thenBy(PaintMessage::versionIndex)
        .thenBy(PaintMessage::createdAt)
}

private fun PaintMessage.conversationThreadTime(userMessageTimes: Map<String, Long>): Long {
    return if (senderIdentity == SenderIdentity.ASSISTANT && parentUserMessageId != null) {
        userMessageTimes[parentUserMessageId] ?: createdAt
    } else {
        createdAt
    }
}
