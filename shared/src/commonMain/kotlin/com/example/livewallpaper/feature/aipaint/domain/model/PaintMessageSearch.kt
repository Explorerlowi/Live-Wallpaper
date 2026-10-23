package com.example.livewallpaper.feature.aipaint.domain.model

/** 摘要中匹配位置前后保留的字符数，用于让关键字尽量居中显示。 */
private const val SNIPPET_CONTEXT_CHARS = 30

/** 摘要被截断时补在两端的省略号。 */
private const val SNIPPET_ELLIPSIS = "…"

/**
 * 当前会话内关键字搜索的一条命中结果。
 *
 * @property messageId 命中的消息 id。
 * @property versionGroup 命中消息所属的版本组，`null` 表示不参与版本管理。
 * @property versionIndex 命中消息在版本组内的序号；跳转时若与当前展示版本不同需先切换。
 * @property senderIdentity 发送者，用于结果列表展示。
 * @property createdAt 消息创建时间戳，用于结果列表展示。
 * @property snippet 以首个匹配位置为中心截取的摘要文本，可能带省略号。
 * @property snippetMatchRange 关键字在 [snippet] 中的区间（闭区间），用于高亮。
 */
data class PaintMessageSearchHit(
    val messageId: String,
    val versionGroup: String?,
    val versionIndex: Int,
    val senderIdentity: SenderIdentity,
    val createdAt: Long,
    val snippet: String,
    val snippetMatchRange: IntRange,
)

/**
 * 在消息列表中按关键字搜索正文。
 *
 * 匹配 [PaintMessage.messageContent]，大小写不敏感；跨全部版本搜索，命中结果按会话顺序（旧 → 新）返回，
 * 调用方可据此实现「上一条 / 下一条」的循环步进。
 *
 * @param keyword 用户输入的关键字，首尾空白会被忽略；为空时返回空列表。
 * @return 命中列表，按 [sortedByConversationAndVersion] 顺序排列。
 */
fun List<PaintMessage>.searchPaintMessages(keyword: String): List<PaintMessageSearchHit> {
    val normalized = keyword.trim()
    if (normalized.isEmpty()) return emptyList()
    return sortedByConversationAndVersion().mapNotNull { message ->
        val start = message.messageContent.indexOf(normalized, ignoreCase = true)
        if (start < 0) return@mapNotNull null
        val (snippet, range) = buildSnippet(message.messageContent, start, normalized.length)
        PaintMessageSearchHit(
            messageId = message.id,
            versionGroup = message.versionGroup,
            versionIndex = message.versionIndex,
            senderIdentity = message.senderIdentity,
            createdAt = message.createdAt,
            snippet = snippet,
            snippetMatchRange = range,
        )
    }
}

/**
 * 返回 [keyword] 在文本中出现的全部区间（闭区间），大小写不敏感，用于结果项或正文高亮。
 *
 * @param keyword 关键字；为空时返回空列表。
 */
fun String.keywordMatchRanges(keyword: String): List<IntRange> {
    val normalized = keyword.trim()
    if (normalized.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (from <= length) {
        val start = indexOf(normalized, startIndex = from, ignoreCase = true)
        if (start < 0) break
        val end = start + normalized.length - 1
        ranges += start..end
        from = end + 1
    }
    return ranges
}

/**
 * 截取以匹配位置为中心的摘要，并把换行压成空格便于单行展示。
 *
 * @return 摘要文本与关键字在摘要中的区间。
 */
private fun buildSnippet(content: String, matchStart: Int, matchLength: Int): Pair<String, IntRange> {
    val windowStart = (matchStart - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0)
    val windowEnd = (matchStart + matchLength + SNIPPET_CONTEXT_CHARS).coerceAtMost(content.length)
    val prefix = if (windowStart > 0) SNIPPET_ELLIPSIS else ""
    val suffix = if (windowEnd < content.length) SNIPPET_ELLIPSIS else ""
    val body = content.substring(windowStart, windowEnd).replace('\n', ' ')
    val snippet = prefix + body + suffix
    val rangeStart = prefix.length + (matchStart - windowStart)
    return snippet to (rangeStart until rangeStart + matchLength)
}
