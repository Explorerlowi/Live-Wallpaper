package com.example.livewallpaper.paint.ui.split

import androidx.compose.runtime.*

/**
 * 图片切分流程入口
 *
 * 管理两个界面之间的状态和导航：
 * 1. ImageGridEditorScreen - 网格编辑器（Activity 全屏承载）
 * 2. ImageSplitExportScreen - 微调与导出（Dialog，从右侧滑入）
 * 
 * @param imagePath 要切分的图片路径
 * @param onDismiss 关闭整个流程
 */
@Composable
fun ImageSplitFlow(
    imagePath: String,
    onDismiss: () -> Unit
) {
    // 切分后的图块
    var splitTiles by remember { mutableStateOf<List<SplitTile>?>(null) }
    
    // 是否显示导出界面
    var showExportScreen by remember { mutableStateOf(false) }
    
    // 底层：网格编辑器（始终存在）
    ImageGridEditorScreen(
        imagePath = imagePath,
        onDismiss = onDismiss,
        onSplitComplete = { tiles, _, _ ->
            splitTiles = tiles
            showExportScreen = true
        }
    )
    
    // 上层：导出界面（Dialog 形式，内部带滑动进入动画）
    if (showExportScreen && splitTiles != null) {
        ImageSplitExportScreen(
            tiles = splitTiles!!,
            onBack = {
                showExportScreen = false
            },
            onDismiss = onDismiss
        )
    }
}
