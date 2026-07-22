package com.example.livewallpaper.core.design.icon

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the shared geometry contract for application-specific icons. */
internal class CustomAppIconsTest {
    private val customIcons: List<ImageVector> = listOf(
        CustomAppIcons.aspectRatio,
        CustomAppIcons.mirrorLeftFilled,
        CustomAppIcons.mirrorRightFilled,
        CustomAppIcons.sparkles,
        CustomAppIcons.mosaic,
        CustomAppIcons.collections,
        CustomAppIcons.compare,
        CustomAppIcons.folderArchive,
        CustomAppIcons.freehand,
        CustomAppIcons.quality,
        CustomAppIcons.history,
        CustomAppIcons.key,
        CustomAppIcons.undo,
        CustomAppIcons.redo,
        CustomAppIcons.reorder,
        CustomAppIcons.rotateCounterClockwise,
        CustomAppIcons.rotateClockwise,
    )

    /** Ensures every custom vector uses the same canvas dimensions as Eva Icons. */
    @Test
    fun customIconsUseTwentyFourUnitCanvas() {
        customIcons.forEach { icon ->
            assertEquals(24f, icon.defaultWidth.value, icon.name)
            assertEquals(24f, icon.defaultHeight.value, icon.name)
            assertEquals(24f, icon.viewportWidth, icon.name)
            assertEquals(24f, icon.viewportHeight, icon.name)
        }
    }

    /** Ensures vector names remain distinct for previewing and diagnostics. */
    @Test
    fun customIconNamesAreUnique() {
        assertEquals(customIcons.size, customIcons.map(ImageVector::name).distinct().size)
    }
}
