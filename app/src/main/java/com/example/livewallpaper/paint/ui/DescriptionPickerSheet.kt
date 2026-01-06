package com.example.livewallpaper.paint.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 描述分类数据
 */
data class DescriptionCategory(
    val titleResId: Int,
    val items: List<String>
)

/**
 * 描述场景数据
 */
data class DescriptionScene(
    val id: String,
    val titleResId: Int,
    val categories: List<DescriptionCategory>
)

/**
 * 获取通用场景分类
 */
private fun getGeneralCategories(): List<DescriptionCategory> = listOf(
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
 * 获取古风场景分类
 */
private fun getChineseStyleCategories(): List<DescriptionCategory> = listOf(
    DescriptionCategory(
        titleResId = R.string.desc_category_color,
        items = listOf(
            "月白", "雪青", "竹青", "黛青", "樱草色", "丁香紫",
            "象牙白", "鸦青", "秋香色", "绛紫", "藕荷色", "水蓝",
            "湖绿", "银红", "桃红", "鹅黄", "缃叶", "苍葭",
            "天水碧", "海天霞", "紫檀", "皎玉", "盈盈", "苍青",
            "缥碧", "东方既白", "暮山紫", "黄河琉璃", "玄天", "纁黄"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_costume,
        items = listOf(
            "对襟", "琵琶襟", "广袖长袍", "大袖衣", "比甲", "春衫",
            "齐胸襦裙", "齐腰襦裙", "交领上衣", "褙子", "曲裾", "直裾",
            "披风", "霞帔", "水田衣", "云肩", "抹胸", "腰裙",
            "流仙裙", "百褶裙", "石榴裙", "留仙裙", "月华裙"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_accessory,
        items = listOf(
            "玉佩", "环佩", "香囊", "璎珞", "霞帔", "披帛",
            "手镯", "臂钏", "戒指", "步摇", "珠花", "宫绢",
            "钿花", "闹蛾", "银扁方", "金累丝衔珠", "点翠", "烧蓝",
            "镂空", "花丝镶嵌"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_headdress,
        items = listOf(
            "金簪", "玉簪", "珠玉簪", "凤钗", "牡丹钗", "蝶形钗",
            "流苏步摇", "珠玉步摇", "华胜", "扁方", "梳篦", "发钗",
            "珠串", "点翠", "珠花", "闹蛾", "假髻", "挑心", "掩鬓"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_forehead,
        items = listOf(
            "金钿", "翠钿", "玉钿", "宝钿", "蝶形额饰", "梅花妆",
            "宝相花额饰", "额帕", "织锦抹额", "珠络抹额", "玉胜",
            "珠络", "金缕额饰"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_fabric,
        items = listOf(
            "云锦", "蜀锦", "雨花锦", "软烟罗", "妆花缎", "彩晕锦",
            "织金锦", "素软缎", "绫", "罗", "绸", "缎", "纱", "绡",
            "绢", "提花绡", "天香绢", "单罗纱", "雨丝锦", "散花锦"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_craft,
        items = listOf(
            "弹墨", "刻丝", "彩绣", "缕金", "暗花", "紫绣", "鎏金",
            "点翠", "烧蓝", "镂空", "衔珠", "鳞花", "花丝镶嵌",
            "金漆镶嵌", "掐丝"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_pattern,
        items = listOf(
            "藤纹", "牡丹", "并蒂莲", "凤纹", "蝶纹", "祥云纹",
            "梅兰竹菊", "石榴纹", "宝相花", "团花", "折枝", "流水纹",
            "几何纹", "瑞兽纹", "百花纹", "卷草纹"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_scene,
        items = listOf(
            "海边沙滩", "湖畔垂柳", "古典园林", "竹林幽径", "宫廷回廊",
            "古寺门前", "山间云海", "月下庭院", "落英缤纷", "晚霞天空",
            "细雨江南", "秋叶飘零", "雪中亭阁"
        )
    ),
    DescriptionCategory(
        titleResId = R.string.desc_category_atmosphere,
        items = listOf(
            "晚风轻拂", "夕阳余晖", "晨雾迷蒙", "月色如水", "花雨纷飞",
            "静谧唯美", "诗意浪漫", "朦胧梦幻", "胶片质感", "复古滤镜",
            "电影感", "艺术写真", "清冷孤寂", "温柔恬静", "华美端庄"
        )
    )
)

/**
 * 获取所有描述场景
 */
fun getDescriptionScenes(): List<DescriptionScene> = listOf(
    DescriptionScene(
        id = "general",
        titleResId = R.string.desc_scene_general,
        categories = getGeneralCategories()
    ),
    DescriptionScene(
        id = "chinese_style",
        titleResId = R.string.desc_scene_chinese_style,
        categories = getChineseStyleCategories()
    )
)

/**
 * Set<String> 的 Saver（保存为 List<String>）
 */
private val SelectedIdsSaver: Saver<Set<String>, Any> = listSaver(
    save = { it.toList() },
    restore = { it.toSet() }
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

        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        var currentX = 0
        var currentY = 0
        var rowHeight = 0

        val positions = ArrayList<Pair<Int, Int>>(placeables.size)

        val maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
            placeables.sumOf { it.width + hSpacingPx }.coerceAtLeast(0)
        } else {
            constraints.maxWidth
        }

        placeables.forEach { placeable ->
            if (currentX + placeable.width > maxWidth && currentX > 0) {
                currentX = 0
                currentY += rowHeight + vSpacingPx
                rowHeight = 0
            }

            positions.add(currentX to currentY)
            currentX += placeable.width + hSpacingPx
            rowHeight = maxOf(rowHeight, placeable.height)
        }

        val totalHeight = if (placeables.isNotEmpty()) currentY + rowHeight else 0

        layout(maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}

/**
 * 场景切换指示器
 */
@Composable
private fun SceneIndicator(
    scenes: List<DescriptionScene>,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左箭头
        IconButton(
            onClick = onPrevious,
            enabled = currentIndex > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (currentIndex > 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 场景名称
        Text(
            text = stringResource(scenes[currentIndex].titleResId),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 120.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 右箭头
        IconButton(
            onClick = onNext,
            enabled = currentIndex < scenes.size - 1,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (currentIndex < scenes.size - 1) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }

    // 指示点
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        scenes.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        }
                    )
            )
        }
    }
}

/**
 * 场景内容（分类列表）
 */
@Composable
private fun SceneContent(
    categories: List<DescriptionCategory>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onListAtTopChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 检测列表是否在顶部
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    
    // 通知父组件列表位置状态
    androidx.compose.runtime.LaunchedEffect(isAtTop) {
        onListAtTopChanged(isAtTop)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        categories.forEach { category ->
            item(key = "title_${category.titleResId}") {
                Text(
                    text = stringResource(category.titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "items_${category.titleResId}") {
                CustomFlowRow(
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    category.items.forEach { itemText ->
                        androidx.compose.runtime.key(itemText) {
                            DescriptionChip(
                                text = itemText,
                                isSelected = selectedIds.contains(itemText),
                                onClick = { onToggleSelection(itemText) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
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
    onConfirm: (List<String>) -> Unit,
    initialSelectedIds: Set<String> = emptySet()
) {
    val scope = rememberCoroutineScope()
    val scenes = remember { getDescriptionScenes() }

    var currentSceneIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedIds by rememberSaveable(
        initialSelectedIds,
        stateSaver = SelectedIdsSaver
    ) {
        mutableStateOf(initialSelectedIds)
    }

    // 滑动方向追踪
    var swipeDirection by remember { mutableIntStateOf(0) }
    
    // 列表是否在顶部
    var isListAtTop by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    fun dismissWithAnimation() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    // 获取所有场景的所有分类项，用于排序输出
    val allItems by remember {
        derivedStateOf {
            scenes.flatMap { scene ->
                scene.categories.flatMap { it.items }
            }
        }
    }

    val selectedInOrder by remember {
        derivedStateOf {
            allItems.filter { selectedIds.contains(it) }
        }
    }

    fun switchToPrevious() {
        if (currentSceneIndex > 0) {
            swipeDirection = -1
            currentSceneIndex--
        }
    }

    fun switchToNext() {
        if (currentSceneIndex < scenes.size - 1) {
            swipeDirection = 1
            currentSceneIndex++
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithAnimation() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        sheetGesturesEnabled = isListAtTop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 400.dp, max = 660.dp)
        ) {
            // 场景切换指示器
            SceneIndicator(
                scenes = scenes,
                currentIndex = currentSceneIndex,
                onPrevious = { switchToPrevious() },
                onNext = { switchToNext() }
            )

            // 滑动检测容器
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val swipeThreshold = 100f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragOffset = 0f },
                            onDragEnd = {
                                if (abs(dragOffset) > swipeThreshold) {
                                    if (dragOffset > 0) {
                                        switchToPrevious()
                                    } else {
                                        switchToNext()
                                    }
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset += dragAmount
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = currentSceneIndex,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> direction * fullWidth }
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> -direction * fullWidth }
                        )
                    },
                    label = "scene_transition"
                ) { sceneIndex ->
                    SceneContent(
                        categories = scenes[sceneIndex].categories,
                        selectedIds = selectedIds,
                        onToggleSelection = { itemText ->
                            selectedIds = if (selectedIds.contains(itemText)) {
                                selectedIds - itemText
                            } else {
                                selectedIds + itemText
                            }
                        },
                        onListAtTopChanged = { isAtTop ->
                            isListAtTop = isAtTop
                        }
                    )
                }
            }

            // 添加按钮
            Button(
                onClick = {
                    onConfirm(selectedInOrder)
                    dismissWithAnimation()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.desc_add),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
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
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
