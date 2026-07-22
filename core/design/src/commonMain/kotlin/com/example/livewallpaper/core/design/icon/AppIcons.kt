package com.example.livewallpaper.core.design.icon

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.EvaIcons
import compose.icons.evaicons.Outline
import compose.icons.evaicons.outline.*

/**
 * Semantic icon catalog for the application.
 *
 * Entries use Eva Icons 1.1.3 Outline where the meaning matches. Missing concepts use custom
 * vectors with the same 24 × 24 rounded outline language. Feature modules depend on this catalog
 * instead of importing either implementation directly.
 */
@Suppress("TooManyFunctions")
object AppIcons {
    val accessTime: ImageVector get() = EvaIcons.Outline.Clock
    val add: ImageVector get() = EvaIcons.Outline.Plus
    val analytics: ImageVector get() = EvaIcons.Outline.BarChart2
    val arrowBack: ImageVector get() = EvaIcons.Outline.ArrowBack
    val arrowForward: ImageVector get() = EvaIcons.Outline.ArrowIosForward
    val arrowUpward: ImageVector get() = EvaIcons.Outline.ArrowUpward
    val aspectRatio: ImageVector get() = CustomAppIcons.aspectRatio
    val autoAwesome: ImageVector get() = CustomAppIcons.sparkles
    val blur: ImageVector get() = CustomAppIcons.mosaic
    val brush: ImageVector get() = EvaIcons.Outline.Brush
    val callMade: ImageVector get() = EvaIcons.Outline.DiagonalArrowRightUp
    val chat: ImageVector get() = EvaIcons.Outline.MessageCircle
    val check: ImageVector get() = EvaIcons.Outline.Checkmark
    val checkBox: ImageVector get() = EvaIcons.Outline.CheckmarkSquare2
    val checkBoxOutline: ImageVector get() = EvaIcons.Outline.Square
    val checkCircle: ImageVector get() = EvaIcons.Outline.CheckmarkCircle2
    val checkCircleOutline: ImageVector get() = EvaIcons.Outline.CheckmarkCircle
    val chevronDown: ImageVector get() = EvaIcons.Outline.ChevronDown
    val chevronLeft: ImageVector get() = EvaIcons.Outline.ChevronLeft
    val chevronRight: ImageVector get() = EvaIcons.Outline.ChevronRight
    val chevronUp: ImageVector get() = EvaIcons.Outline.ChevronUp
    val close: ImageVector get() = EvaIcons.Outline.Close
    val collections: ImageVector get() = CustomAppIcons.collections
    val colorPalette: ImageVector get() = EvaIcons.Outline.ColorPalette
    val compare: ImageVector get() = CustomAppIcons.compare
    val copy: ImageVector get() = EvaIcons.Outline.Copy
    val crop: ImageVector get() = EvaIcons.Outline.Crop
    val cropSquare: ImageVector get() = EvaIcons.Outline.Square
    val delete: ImageVector get() = EvaIcons.Outline.Trash2
    val deleteOutline: ImageVector get() = EvaIcons.Outline.Trash
    val description: ImageVector get() = EvaIcons.Outline.FileText
    val download: ImageVector get() = EvaIcons.Outline.Download
    val edit: ImageVector get() = EvaIcons.Outline.Edit2
    val fileUpload: ImageVector get() = EvaIcons.Outline.Upload
    val flash: ImageVector get() = EvaIcons.Outline.Flash
    val flipLeftFilled: ImageVector get() = CustomAppIcons.mirrorLeftFilled
    val flipRightFilled: ImageVector get() = CustomAppIcons.mirrorRightFilled
    val folderArchive: ImageVector get() = CustomAppIcons.folderArchive
    val gesture: ImageVector get() = CustomAppIcons.freehand
    val grid: ImageVector get() = EvaIcons.Outline.Grid
    val highQuality: ImageVector get() = CustomAppIcons.quality
    val history: ImageVector get() = CustomAppIcons.history
    val image: ImageVector get() = EvaIcons.Outline.Image
    val info: ImageVector get() = EvaIcons.Outline.Info
    val key: ImageVector get() = CustomAppIcons.key
    val language: ImageVector get() = EvaIcons.Outline.Globe2
    val layers: ImageVector get() = EvaIcons.Outline.Layers
    val menu: ImageVector get() = EvaIcons.Outline.Menu
    val moreHorizontal: ImageVector get() = EvaIcons.Outline.MoreHorizontal
    val moreVertical: ImageVector get() = EvaIcons.Outline.MoreVertical
    val openInFull: ImageVector get() = EvaIcons.Outline.Expand
    val playCircle: ImageVector get() = EvaIcons.Outline.PlayCircle
    val radioButtonOff: ImageVector get() = EvaIcons.Outline.RadioButtonOff
    val redo: ImageVector get() = CustomAppIcons.redo
    val refresh: ImageVector get() = EvaIcons.Outline.Refresh
    val remove: ImageVector get() = EvaIcons.Outline.Minus
    val reorder: ImageVector get() = CustomAppIcons.reorder
    val rotateCounterClockwise: ImageVector get() = CustomAppIcons.rotateCounterClockwise
    val rotateClockwise: ImageVector get() = CustomAppIcons.rotateClockwise
    val save: ImageVector get() = EvaIcons.Outline.Save
    val scissors: ImageVector get() = EvaIcons.Outline.Scissors
    val schedule: ImageVector get() = EvaIcons.Outline.Calendar
    val settings: ImageVector get() = EvaIcons.Outline.Settings2
    val share: ImageVector get() = EvaIcons.Outline.Share
    val stop: ImageVector get() = EvaIcons.Outline.StopCircle
    val storage: ImageVector get() = EvaIcons.Outline.HardDrive
    val text: ImageVector get() = EvaIcons.Outline.Text
    val timer: ImageVector get() = EvaIcons.Outline.Clock
    val tune: ImageVector get() = EvaIcons.Outline.Options2
    val undo: ImageVector get() = CustomAppIcons.undo
    val visibility: ImageVector get() = EvaIcons.Outline.Eye
    val visibilityOff: ImageVector get() = EvaIcons.Outline.EyeOff
    val warning: ImageVector get() = EvaIcons.Outline.AlertTriangle
}
