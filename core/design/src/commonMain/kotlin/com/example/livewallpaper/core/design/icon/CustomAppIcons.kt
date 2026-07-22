package com.example.livewallpaper.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Application-specific vectors for concepts that are not available in Eva Icons.
 *
 * The vectors use the same 24 × 24 canvas and approximately two-pixel rounded geometry as
 * Eva Outline so custom and upstream icons can be mixed without a visible style break.
 */
internal object CustomAppIcons {
    val aspectRatio: ImageVector by lazy(::createAspectRatioIcon)
    val mirrorLeftFilled: ImageVector by lazy {
        createMirrorIcon(name = "MirrorLeftFilled", fillLeft = true)
    }
    val mirrorRightFilled: ImageVector by lazy {
        createMirrorIcon(name = "MirrorRightFilled", fillLeft = false)
    }
    val sparkles: ImageVector by lazy(::createSparklesIcon)
    val mosaic: ImageVector by lazy(::createMosaicIcon)
    val collections: ImageVector by lazy(::createCollectionsIcon)
    val compare: ImageVector by lazy(::createCompareIcon)
    val folderArchive: ImageVector by lazy(::createFolderArchiveIcon)
    val freehand: ImageVector by lazy(::createFreehandIcon)
    val quality: ImageVector by lazy(::createQualityIcon)
    val history: ImageVector by lazy(::createHistoryIcon)
    val key: ImageVector by lazy(::createKeyIcon)
    val undo: ImageVector by lazy { createTurnIcon(name = "Undo", pointsRight = false) }
    val redo: ImageVector by lazy { createTurnIcon(name = "Redo", pointsRight = true) }
    val reorder: ImageVector by lazy(::createReorderIcon)
    val rotateCounterClockwise: ImageVector by lazy {
        createRotateIcon(name = "RotateCounterClockwise", clockwise = false)
    }
    val rotateClockwise: ImageVector by lazy {
        createRotateIcon(name = "RotateClockwise", clockwise = true)
    }
}

private val iconBrush = SolidColor(Color(0xFF231F20))

private fun appIcon(
    name: String,
    content: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(content).build()

private fun ImageVector.Builder.outline(
    width: Float = 2f,
    content: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = iconBrush,
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = content,
    )
}

private fun ImageVector.Builder.filled(
    alpha: Float = 1f,
    content: PathBuilder.() -> Unit,
) {
    path(
        fill = iconBrush,
        fillAlpha = alpha,
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = content,
    )
}

private fun createAspectRatioIcon(): ImageVector = appIcon("AspectRatio") {
    outline {
        moveTo(17f, 12f)
        verticalLineTo(5.5f)
        curveTo(17f, 4.67f, 16.33f, 4f, 15.5f, 4f)
        horizontalLineTo(5.5f)
        curveTo(4.67f, 4f, 4f, 4.67f, 4f, 5.5f)
        verticalLineTo(18.5f)
        curveTo(4f, 19.33f, 4.67f, 20f, 5.5f, 20f)
        horizontalLineTo(12f)
        moveTo(13.5f, 12f)
        horizontalLineTo(19.5f)
        curveTo(20.33f, 12f, 21f, 12.67f, 21f, 13.5f)
        verticalLineTo(20f)
        horizontalLineTo(12f)
        verticalLineTo(13.5f)
        curveTo(12f, 12.67f, 12.67f, 12f, 13.5f, 12f)
    }
}

private fun createMirrorIcon(name: String, fillLeft: Boolean): ImageVector = appIcon(name) {
    outline {
        mirrorTriangle(onLeft = !fillLeft)
    }
    filled {
        mirrorTriangle(onLeft = fillLeft)
    }
}

private fun createSparklesIcon(): ImageVector = appIcon("Sparkles") {
    outline(width = 1.8f) {
        moveTo(9f, 3f)
        curveTo(9.4f, 6.45f, 11.55f, 8.6f, 15f, 9f)
        curveTo(11.55f, 9.4f, 9.4f, 11.55f, 9f, 15f)
        curveTo(8.6f, 11.55f, 6.45f, 9.4f, 3f, 9f)
        curveTo(6.45f, 8.6f, 8.6f, 6.45f, 9f, 3f)
        close()
        moveTo(18f, 3.5f)
        curveTo(18.2f, 5.15f, 19.35f, 6.3f, 21f, 6.5f)
        curveTo(19.35f, 6.7f, 18.2f, 7.85f, 18f, 9.5f)
        curveTo(17.8f, 7.85f, 16.65f, 6.7f, 15f, 6.5f)
        curveTo(16.65f, 6.3f, 17.8f, 5.15f, 18f, 3.5f)
        close()
        moveTo(17.5f, 14.5f)
        curveTo(17.75f, 16.3f, 18.95f, 17.5f, 20.75f, 17.75f)
        curveTo(18.95f, 18f, 17.75f, 19.2f, 17.5f, 21f)
        curveTo(17.25f, 19.2f, 16.05f, 18f, 14.25f, 17.75f)
        curveTo(16.05f, 17.5f, 17.25f, 16.3f, 17.5f, 14.5f)
        close()
    }
}

private fun createMosaicIcon(): ImageVector = appIcon("Mosaic") {
    filled {
        square(left = 4f, top = 4f, size = 4f)
        square(left = 16f, top = 4f, size = 4f)
        square(left = 10f, top = 10f, size = 4f)
        square(left = 4f, top = 16f, size = 4f)
        square(left = 16f, top = 16f, size = 4f)
    }
    filled(alpha = 0.42f) {
        square(left = 10f, top = 4f, size = 4f)
        square(left = 4f, top = 10f, size = 4f)
        square(left = 16f, top = 10f, size = 4f)
        square(left = 10f, top = 16f, size = 4f)
    }
}

private fun createCollectionsIcon(): ImageVector = appIcon("Collections") {
    outline {
        moveTo(7f, 4f)
        horizontalLineTo(18f)
        curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
        verticalLineTo(16f)
        moveTo(6f, 7f)
        horizontalLineTo(16f)
        curveTo(17.1f, 7f, 18f, 7.9f, 18f, 9f)
        verticalLineTo(18f)
        curveTo(18f, 19.1f, 17.1f, 20f, 16f, 20f)
        horizontalLineTo(6f)
        curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
        verticalLineTo(9f)
        curveTo(4f, 7.9f, 4.9f, 7f, 6f, 7f)
        moveTo(6.5f, 17f)
        lineTo(9.25f, 13.75f)
        lineTo(11.75f, 16.25f)
        lineTo(13.5f, 14.5f)
        lineTo(15.5f, 17f)
        circle(centerX = 8.25f, centerY = 10.75f, radius = 1f)
    }
}

private fun createCompareIcon(): ImageVector = appIcon("Compare") {
    outline {
        moveTo(6f, 4f)
        horizontalLineTo(18f)
        curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
        verticalLineTo(18f)
        curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f)
        horizontalLineTo(6f)
        curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
        verticalLineTo(6f)
        curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
        moveTo(12f, 4f)
        verticalLineTo(20f)
        moveTo(9.5f, 8.5f)
        lineTo(7f, 12f)
        lineTo(9.5f, 15.5f)
        moveTo(14.5f, 8.5f)
        lineTo(17f, 12f)
        lineTo(14.5f, 15.5f)
    }
}

private fun createFolderArchiveIcon(): ImageVector = appIcon("FolderArchive") {
    outline {
        moveTo(3f, 7f)
        curveTo(3f, 5.9f, 3.9f, 5f, 5f, 5f)
        horizontalLineTo(9f)
        lineTo(11f, 7f)
        horizontalLineTo(19f)
        curveTo(20.1f, 7f, 21f, 7.9f, 21f, 9f)
        verticalLineTo(18f)
        curveTo(21f, 19.1f, 20.1f, 20f, 19f, 20f)
        horizontalLineTo(5f)
        curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f)
        close()
        moveTo(14f, 9.5f)
        verticalLineTo(10.5f)
        moveTo(14f, 13f)
        verticalLineTo(14f)
        moveTo(14f, 16.5f)
        verticalLineTo(17.5f)
    }
}

private fun createFreehandIcon(): ImageVector = appIcon("Freehand") {
    outline {
        moveTo(3f, 17.5f)
        curveTo(4.8f, 12.2f, 7.5f, 11.6f, 8.2f, 15.3f)
        curveTo(8.9f, 19.1f, 11.2f, 20f, 12.8f, 16.1f)
        curveTo(14.2f, 12.7f, 16.4f, 12.7f, 17.2f, 15.8f)
        curveTo(18f, 18.8f, 19.8f, 18.5f, 21f, 16.5f)
    }
}

private fun createQualityIcon(): ImageVector = appIcon("Quality") {
    outline {
        frameCorners()
    }
    filled {
        square(left = 9f, top = 9f, size = 2.25f)
        square(left = 12.75f, top = 9f, size = 2.25f)
        square(left = 9f, top = 12.75f, size = 2.25f)
        square(left = 12.75f, top = 12.75f, size = 2.25f)
    }
}

private fun createHistoryIcon(): ImageVector = appIcon("History") {
    outline {
        moveTo(5f, 8f)
        verticalLineTo(4f)
        horizontalLineTo(9f)
        moveTo(5.35f, 6.3f)
        curveTo(7.05f, 4.85f, 9.25f, 4f, 11.65f, 4f)
        curveTo(16.25f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        curveTo(8.55f, 20f, 5.62f, 17.82f, 4.5f, 14.75f)
        moveTo(12f, 8f)
        verticalLineTo(12f)
        lineTo(15f, 14f)
    }
}

private fun createKeyIcon(): ImageVector = appIcon("Key") {
    outline {
        circle(centerX = 8f, centerY = 8f, radius = 4f)
        moveTo(10.85f, 10.85f)
        lineTo(20f, 20f)
        moveTo(15.75f, 15.75f)
        lineTo(18f, 13.5f)
        moveTo(18f, 18f)
        lineTo(20f, 16f)
    }
}

private fun createTurnIcon(name: String, pointsRight: Boolean): ImageVector = appIcon(name) {
    outline {
        if (pointsRight) {
            moveTo(20f, 8f)
            lineTo(16f, 4f)
            moveTo(20f, 8f)
            lineTo(16f, 12f)
            moveTo(20f, 8f)
            horizontalLineTo(12.5f)
            curveTo(7.8f, 8f, 4f, 11.8f, 4f, 16.5f)
            verticalLineTo(19f)
        } else {
            moveTo(4f, 8f)
            lineTo(8f, 4f)
            moveTo(4f, 8f)
            lineTo(8f, 12f)
            moveTo(4f, 8f)
            horizontalLineTo(11.5f)
            curveTo(16.2f, 8f, 20f, 11.8f, 20f, 16.5f)
            verticalLineTo(19f)
        }
    }
}

private fun createReorderIcon(): ImageVector = appIcon("Reorder") {
    outline {
        moveTo(5f, 4f)
        verticalLineTo(20f)
        moveTo(2.75f, 6.5f)
        lineTo(5f, 4f)
        lineTo(7.25f, 6.5f)
        moveTo(2.75f, 17.5f)
        lineTo(5f, 20f)
        lineTo(7.25f, 17.5f)
        moveTo(10f, 7f)
        horizontalLineTo(21f)
        moveTo(10f, 12f)
        horizontalLineTo(21f)
        moveTo(10f, 17f)
        horizontalLineTo(21f)
    }
}

private fun createRotateIcon(name: String, clockwise: Boolean): ImageVector = appIcon(name) {
    outline {
        if (clockwise) {
            roundedRectangle(left = 10f, top = 13f, right = 20f, bottom = 20f, radius = 2f)
            moveTo(4f, 13f)
            curveTo(4f, 8.58f, 7.58f, 5f, 12f, 5f)
            horizontalLineTo(14f)
            moveTo(11.5f, 2.5f)
            lineTo(14f, 5f)
            lineTo(11.5f, 7.5f)
        } else {
            roundedRectangle(left = 4f, top = 13f, right = 14f, bottom = 20f, radius = 2f)
            moveTo(20f, 13f)
            curveTo(20f, 8.58f, 16.42f, 5f, 12f, 5f)
            horizontalLineTo(10f)
            moveTo(12.5f, 2.5f)
            lineTo(10f, 5f)
            lineTo(12.5f, 7.5f)
        }
    }
}

private fun PathBuilder.frameCorners() {
    moveTo(9f, 4f)
    horizontalLineTo(5.5f)
    curveTo(4.67f, 4f, 4f, 4.67f, 4f, 5.5f)
    verticalLineTo(9f)
    moveTo(15f, 4f)
    horizontalLineTo(18.5f)
    curveTo(19.33f, 4f, 20f, 4.67f, 20f, 5.5f)
    verticalLineTo(9f)
    moveTo(4f, 15f)
    verticalLineTo(18.5f)
    curveTo(4f, 19.33f, 4.67f, 20f, 5.5f, 20f)
    horizontalLineTo(9f)
    moveTo(20f, 15f)
    verticalLineTo(18.5f)
    curveTo(20f, 19.33f, 19.33f, 20f, 18.5f, 20f)
    horizontalLineTo(15f)
}

private fun PathBuilder.square(left: Float, top: Float, size: Float) {
    moveTo(left, top)
    horizontalLineTo(left + size)
    verticalLineTo(top + size)
    horizontalLineTo(left)
    close()
}

private fun PathBuilder.circle(centerX: Float, centerY: Float, radius: Float) {
    moveTo(centerX + radius, centerY)
    arcTo(radius, radius, 0f, true, true, centerX - radius, centerY)
    arcTo(radius, radius, 0f, true, true, centerX + radius, centerY)
    close()
}

private fun PathBuilder.mirrorTriangle(onLeft: Boolean) {
    val outerX = if (onLeft) 5f else 19f
    moveTo(outerX, 5f)
    lineTo(12f, 12f)
    lineTo(outerX, 19f)
    close()
}

private fun PathBuilder.roundedRectangle(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    moveTo(left + radius, top)
    horizontalLineTo(right - radius)
    curveTo(right - radius / 2f, top, right, top + radius / 2f, right, top + radius)
    verticalLineTo(bottom - radius)
    curveTo(right, bottom - radius / 2f, right - radius / 2f, bottom, right - radius, bottom)
    horizontalLineTo(left + radius)
    curveTo(left + radius / 2f, bottom, left, bottom - radius / 2f, left, bottom - radius)
    verticalLineTo(top + radius)
    curveTo(left, top + radius / 2f, left + radius / 2f, top, left + radius, top)
    close()
}
