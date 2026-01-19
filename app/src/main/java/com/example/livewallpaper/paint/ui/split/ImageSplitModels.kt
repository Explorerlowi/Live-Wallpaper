package com.example.livewallpaper.paint.ui.split

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * 网格预设配置
 */
enum class GridPreset(
    val rows: Int,
    val columns: Int,
    val labelResId: Int? = null
) {
    GRID_3x3(3, 3),
    GRID_4x4(4, 4),
    GRID_4x5(4, 5),
    GRID_4x6(4, 6),
    GRID_5x5(5, 5),
    CUSTOM(0, 0);
    
    companion object {
        fun fromRowsAndColumns(rows: Int, columns: Int): GridPreset {
            return entries.find { it.rows == rows && it.columns == columns } ?: CUSTOM
        }
    }
}

/**
 * 网格配置状态
 */
data class GridConfig(
    val rows: Int = 4,
    val columns: Int = 5,
    val preset: GridPreset = GridPreset.GRID_4x5,
    // 裁剪边界（相对于图片尺寸的比例 0-1）
    val cropBounds: RectF = RectF(0f, 0f, 1f, 1f),
    // 内部分割线位置（相对于裁剪区域的比例）
    // rowDividers[i] 表示第 i 条水平分割线的位置（从上到下，范围 0-1）
    val rowDividers: List<Float> = emptyList(),
    // colDividers[i] 表示第 i 条垂直分割线的位置（从左到右，范围 0-1）
    val colDividers: List<Float> = emptyList()
) {
    /**
     * 生成均匀分布的分割线
     */
    fun withEvenDividers(): GridConfig {
        val newRowDividers = if (rows > 1) {
            (1 until rows).map { it.toFloat() / rows }
        } else emptyList()
        
        val newColDividers = if (columns > 1) {
            (1 until columns).map { it.toFloat() / columns }
        } else emptyList()
        
        return copy(rowDividers = newRowDividers, colDividers = newColDividers)
    }
    
    /**
     * 更新行数并重新生成分割线
     */
    fun withRows(newRows: Int): GridConfig {
        val clampedRows = newRows.coerceIn(1, 10)
        return copy(
            rows = clampedRows,
            preset = GridPreset.fromRowsAndColumns(clampedRows, columns)
        ).withEvenDividers()
    }
    
    /**
     * 更新列数并重新生成分割线
     */
    fun withColumns(newColumns: Int): GridConfig {
        val clampedColumns = newColumns.coerceIn(1, 10)
        return copy(
            columns = clampedColumns,
            preset = GridPreset.fromRowsAndColumns(rows, clampedColumns)
        ).withEvenDividers()
    }
    
    /**
     * 应用预设
     */
    fun withPreset(preset: GridPreset): GridConfig {
        return if (preset == GridPreset.CUSTOM) {
            copy(preset = preset)
        } else {
            copy(
                rows = preset.rows,
                columns = preset.columns,
                preset = preset
            ).withEvenDividers()
        }
    }
}

/**
 * 图块编辑参数
 */
data class TileEditParams(
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    val isDefault: Boolean
        get() = cropLeft == 0f && cropTop == 0f && cropRight == 1f && cropBottom == 1f &&
                rotation == 0 && !flipHorizontal && !flipVertical
}

/**
 * 切分后的单个图块
 */
data class SplitTile(
    val index: Int,
    val row: Int,
    val column: Int,
    val bitmap: Bitmap,           // 当前显示的 bitmap（可能已编辑）
    val originalBitmap: Bitmap = bitmap,  // 原始 bitmap（用于重新编辑）
    val editParams: TileEditParams = TileEditParams(),  // 编辑参数
    // 微调偏移（像素）
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    // 微调缩放
    val scale: Float = 1f,
    // 生成的文件名
    var fileName: String = ""
)

/**
 * 导出格式
 */
enum class ExportFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg")
}

/**
 * 命名字段类型
 */
enum class NamingFieldType(val defaultLabel: String) {
    PREFIX("Prefix"),
    SUFFIX("Suffix")
}

/**
 * 命名字段配置
 */
data class NamingField(
    val type: NamingFieldType,
    val label: String,
    val value: String = "",
    val savedValues: List<String> = emptyList()
)

/**
 * 命名规则模板
 */
data class NamingTemplate(
    val id: String,
    val name: String,
    val pattern: String, // e.g., "{prefix}_{suffix}"
    val fields: List<NamingField>
) {
    companion object {
        // 预设模板
        val STICKER = NamingTemplate(
            id = "sticker",
            name = "Sticker",
            pattern = "{prefix}_{suffix}",
            fields = listOf(
                NamingField(NamingFieldType.PREFIX, "Character", savedValues = listOf("sticker")),
                NamingField(NamingFieldType.SUFFIX, "Emotion", savedValues = listOf("happy", "sad", "angry", "surprised"))
            )
        )
        
        val SEQUENCE = NamingTemplate(
            id = "sequence",
            name = "Sequence",
            pattern = "{prefix}_{index}",
            fields = listOf(
                NamingField(NamingFieldType.PREFIX, "Name", savedValues = listOf("image", "tile", "part"))
            )
        )
        
        val DEFAULT_TEMPLATES = listOf(STICKER, SEQUENCE)
    }
}

/**
 * 导出配置
 */
data class ExportConfig(
    val format: ExportFormat = ExportFormat.PNG,
    val template: NamingTemplate = NamingTemplate.STICKER,
    val prefixValue: String = "sticker",
    val suffixValue: String = "happy",
    val compressAsZip: Boolean = true
)

/**
 * 图片切分整体状态
 */
data class ImageSplitState(
    // 源图片
    val sourceImagePath: String? = null,
    val sourceBitmap: Bitmap? = null,
    
    // 网格配置
    val gridConfig: GridConfig = GridConfig().withEvenDividers(),
    
    // 切分结果
    val tiles: List<SplitTile> = emptyList(),
    
    // 当前选中的图块索引（用于微调）
    val selectedTileIndex: Int = 0,
    
    // 导出配置
    val exportConfig: ExportConfig = ExportConfig(),
    
    // Undo/Redo 栈
    val undoStack: List<GridConfig> = emptyList(),
    val redoStack: List<GridConfig> = emptyList(),
    
    // 已保存的命名值（按字段类型分类）
    val savedPrefixValues: List<String> = listOf("sticker", "character", "emoji"),
    val savedSuffixValues: List<String> = listOf("happy", "sad", "angry", "surprised", "confused")
)

/**
 * 图片切分事件
 */
sealed class ImageSplitEvent {
    // 图片选择
    data class SetSourceImage(val path: String) : ImageSplitEvent()
    data object ClearSourceImage : ImageSplitEvent()
    
    // 网格配置
    data class SetPreset(val preset: GridPreset) : ImageSplitEvent()
    data class SetRows(val rows: Int) : ImageSplitEvent()
    data class SetColumns(val columns: Int) : ImageSplitEvent()
    data class UpdateCropBounds(val bounds: RectF) : ImageSplitEvent()
    data class UpdateRowDivider(val index: Int, val position: Float) : ImageSplitEvent()
    data class UpdateColDivider(val index: Int, val position: Float) : ImageSplitEvent()
    
    // Undo/Redo
    data object Undo : ImageSplitEvent()
    data object Redo : ImageSplitEvent()
    data object ResetGrid : ImageSplitEvent()
    
    // 切分操作
    data object SplitImage : ImageSplitEvent()
    
    // 微调
    data class SelectTile(val index: Int) : ImageSplitEvent()
    data class UpdateTileOffset(val index: Int, val offsetX: Float, val offsetY: Float) : ImageSplitEvent()
    data class UpdateTileScale(val index: Int, val scale: Float) : ImageSplitEvent()
    
    // 导出配置
    data class SetExportFormat(val format: ExportFormat) : ImageSplitEvent()
    data class SetPrefixValue(val value: String) : ImageSplitEvent()
    data class SetSuffixValue(val value: String) : ImageSplitEvent()
    data class SetCompressAsZip(val enabled: Boolean) : ImageSplitEvent()
    data class ApplyNamingToAll(val prefix: String, val suffix: String) : ImageSplitEvent()
    data class ApplyNamingToCurrent(val prefix: String, val suffix: String) : ImageSplitEvent()
    
    // 保存命名值
    data class SavePrefixValue(val value: String) : ImageSplitEvent()
    data class SaveSuffixValue(val value: String) : ImageSplitEvent()
    
    // 导出
    data object Export : ImageSplitEvent()
}
