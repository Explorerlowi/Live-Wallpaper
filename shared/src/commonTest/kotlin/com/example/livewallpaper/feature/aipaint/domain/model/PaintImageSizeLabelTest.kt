package com.example.livewallpaper.feature.aipaint.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 校验生成图片气泡上的尺寸和文件大小文案。 */
class PaintImageSizeLabelTest {
    @Test
    fun byteSizeUsesOneDecimalUntilMegabytes() {
        assertEquals("512 B", formatByteSize(512))
        assertEquals("1 KB", formatByteSize(1024))
        assertEquals("1.5 KB", formatByteSize(1536))
        assertEquals("1 MB", formatByteSize(1024L * 1024L))
        assertEquals("2.5 MB", formatByteSize(2_621_440))
        assertEquals("", formatByteSize(-1))
    }

    @Test
    fun imageLabelKeepsWhicheverSizeIsKnown() {
        assertEquals("1024×768 · 1.5 KB", paintImageSizeLabel(1024, 768, 1536))
        assertEquals("1024×768", paintImageSizeLabel(1024, 768, null))
        assertEquals("1 KB", paintImageSizeLabel(0, 0, 1024))
        assertNull(paintImageSizeLabel(0, 0, null))
    }

    @Test
    fun summaryJoinsImagesAndSkipsUnknownOnes() {
        val first = PaintImage(id = "a", width = 100, height = 80)
        val second = PaintImage(id = "b", width = 0, height = 0)
        val summary = paintImageSizeSummary(
            images = listOf(first, second),
            fileBytesById = mapOf("b" to 2048),
        )
        assertEquals("100×80 / 2 KB", summary)
        assertNull(paintImageSizeSummary(listOf(second)))
    }
}
