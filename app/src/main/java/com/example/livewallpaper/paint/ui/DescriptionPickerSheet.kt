package com.example.livewallpaper.paint.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.R

/**
 * 描述分类数据
 */
data class DescriptionCategory(
    val titleResId: Int,
    val items: List<String>
)

/**
 * 获取所有描述分类
 */
fun getDescriptionCategories(): List<DescriptionCategory> = listOf(
    DescriptionCategory(
        titleResId = R.string.desc_category_camera,
        items = listOf(
            "大景深", "全景", "长焦镜头", "微距镜头",
            "全身镜头", "中景镜头", "七分身镜头",
            "第一人称视角", "过肩视角", "鱼眼镜头",
            "水下摄影", "航拍", "眼睛水平拍摄",
            "高角度拍摄", "倾斜角度拍摄", "蜗牛视角",
            "鸟瞰视角", "剪影视角", "推拉镜头", "跟踪镜头",
            "手持摄影", "透视拍摄", "多重曝光视角",
            "低角度拍摄", "半身像"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_light,
        items = listOf(
            "体积光", "摄影棚光", "轮廓光", "背景光",
            "电影级光照", "霓虹灯", "透镜光晕", "金属光泽",
            "丁达尔效应", "氛围灯光", "自然光", "聚光灯",
            "日落光", "彩虹光", "荧光灯光", "点光源",
            "阴影光", "月光", "晨光", "暮光", "双色光",
            "直射日光"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_landscape,
        items = listOf(
            "自然主义", "超现实主义", "长曝光", "黄金时刻",
            "蓝调时刻", "极简主义", "HDR", "城市景观",
            "夜景", "水下摄影", "航拍", "柔焦"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_portrait,
        items = listOf(
            "肖像精细", "黑白", "街头摄影", "时尚摄影",
            "艺术肖像", "情绪人像", "复古风", "色彩鲜明",
            "梦幻效果"
        )
    )
)

/**
 * 自定义流式布局，精确控制间距
 */
@Composable
private fun CustomFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hSpacingPx = horizontalSpacing.roundToPx()
        val vSpacingPx = verticalSpacing.roundToPx()
        
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        
        var currentX = 0
        var currentY = 0
        var rowHeight = 0
        
        val positions = mutableListOf<Pair<Int, Int>>()
        
        placeables.forEach { placeable ->
            if (currentX + placeable.width > constraints.maxWidth && currentX > 0) {
                currentX = 0
                currentY += rowHeight + vSpacingPx
                rowHeight = 0
            }
            
            positions.add(Pair(currentX, currentY))
            currentX += placeable.width + hSpacingPx
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        
        val totalHeight = if (placeables.isNotEmpty()) currentY + rowHeight else 0
        
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}

/**
 * 描述选择器底部抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionPickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val categories = remember { getDescriptionCategories() }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    
    // 列表滚动状态
    val listState = rememberLazyListState()
    
    // 跳过部分展开状态，直接完全展开
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // 内容区域 - 可滚动
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 300.dp, max = 500.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                categories.forEach { category ->
                    item(key = "title_${category.titleResId}") {
                        Text(
                            text = stringResource(category.titleResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    item(key = "items_${category.titleResId}") {
                        CustomFlowRow(
                            horizontalSpacing = 6.dp,
                            verticalSpacing = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            category.items.forEach { item ->
                                DescriptionChip(
                                    text = item,
                                    isSelected = selectedItems.contains(item),
                                    onClick = {
                                        selectedItems = if (selectedItems.contains(item)) {
                                            selectedItems - item
                                        } else {
                                            selectedItems + item
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            
            // 底部添加按钮
            Button(
                onClick = { 
                    onConfirm(selectedItems.toList())
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.desc_add),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * 描述标签组件
 */
@Composable
private fun DescriptionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
