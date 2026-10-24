package com.example.livewallpaper.feature.aipaint.domain.model

import kotlin.math.roundToInt

/**
 * 把字节数格式化成气泡上可读的文件大小。
 *
 * 小于 1024 字节显示 B，否则按 KB 或 MB 保留一位小数。整数不显示小数。
 *
 * @param bytes 文件字节数。小于 0 时返回空字符串。
 * @return 如 `512 B`、`1.5 KB`、`2.4 MB`。
 */
fun formatByteSize(bytes: Long): String {
    if (bytes < 0L) return ""
    if (bytes < 1024L) return "$bytes B"
    val (value, unit) = if (bytes < 1024L * 1024L) {
        bytes / 1024.0 to "KB"
    } else {
        bytes / (1024.0 * 1024.0) to "MB"
    }
    val tenths = (value * 10).roundToInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole $unit" else "$whole.$fraction $unit"
}

/**
 * 组合一张生成图片在气泡信息里展示的大小。
 *
 * 像素尺寸和文件大小都有时连在一起。缺哪一项就只显示另一项。
 *
 * @param width 图片宽度，未知时为 0。
 * @param height 图片高度，未知时为 0。
 * @param fileBytes 文件字节数。文件不存在或尚未读取时为 null。
 * @return 如 `1024×1024 · 1.8 MB`。没有可展示的信息时为 null。
 */
fun paintImageSizeLabel(width: Int, height: Int, fileBytes: Long?): String? {
    val pixels = if (width > 0 && height > 0) "${width}×${height}" else null
    val fileSize = fileBytes?.takeIf { it > 0L }?.let(::formatByteSize)?.takeIf { it.isNotEmpty() }
    return listOfNotNull(pixels, fileSize).takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

/**
 * 把一条生成消息里的图片大小收成一段气泡信息。
 *
 * 多张图片按原顺序用斜杠隔开。尺寸未知且文件也读不到的图片会被跳过。
 *
 * @param images 消息中的图片。
 * @param fileBytesById 图片 ID 到文件字节数的映射。尚未读取时可以为空。
 * @return 可直接接在完成状态后面的大小文案。没有可展示的图片时为 null。
 */
fun paintImageSizeSummary(
    images: List<PaintImage>,
    fileBytesById: Map<String, Long> = emptyMap(),
): String? {
    return images.mapNotNull { image ->
        paintImageSizeLabel(image.width, image.height, fileBytesById[image.id])
    }.takeIf { it.isNotEmpty() }?.joinToString(separator = " / ")
}
