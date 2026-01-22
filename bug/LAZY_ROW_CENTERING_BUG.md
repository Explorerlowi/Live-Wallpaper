# LazyRow 图片列表无法居中问题修复

## 问题描述
在微调和导出界面的图片列表（`TileCarousel`）中，当列表滑动到最左侧（第一张图）或最右侧（最后一张图）时，图片无法自动回弹到屏幕正中央，而是卡在边缘位置。

## 原因分析
`LazyRow` 默认的内容排列是从左到右的。
如果要让列表中的某一项（Item）能够居中显示，该 Item 的中心点必须能够到达 `LazyRow` 视口（Viewport）的中心点。

对于**第一项**：
- 如果 `contentPadding.start` 为 0 或较小，第一项的最左边紧贴视口左边。
- 此时第一项的中心点位于 `itemWidth / 2` 处。
- 视口的中心点位于 `viewportWidth / 2` 处。
- 只要 `itemWidth < viewportWidth`，第一项的中心点就永远无法到达视口中心点（因为它被挡在左边了）。

对于**最后一项**，原理相同，受限于 `contentPadding.end`。

之前的实现可能使用了固定的 `contentPadding`，或者没有考虑到不同屏幕宽度和 Item 宽度变化的情况，导致 padding 不足以让边缘的 Item 移动到中心位置。

## 修复方案
动态计算 `contentPadding`（`start` 和 `end`），确保第一项和最后一项有足够的空间“推”到中间。

### 具体步骤
1.  **获取容器宽度**：使用 `onSizeChanged` 获取 `LazyRow` 的宽度 (`containerWidth`)。
2.  **计算 Item 宽度**：根据 Item 的宽高比逻辑，预估 Item 的显示宽度。
3.  **动态计算 Padding**：
    - `startPadding = (containerWidth - firstItemWidth) / 2`
    - `endPadding = (containerWidth - lastItemWidth) / 2`
    - 这样设置后，当 scrollOffset 为 0（最左边）时，第一项的左边距离视口左边 `startPadding`，其中心点位置为 `startPadding + firstItemWidth / 2`。
    - 代入公式：`(containerWidth - firstItemWidth) / 2 + firstItemWidth / 2 = containerWidth / 2`。
    - 正好对齐视口中心。

### 代码片段
```kotlin
// 获取容器宽度
var containerWidth by remember { mutableIntStateOf(0) }

// 计算首尾 Padding
val startPadding = remember(containerWidth, tiles) {
    if (containerWidth > 0 && tiles.isNotEmpty()) {
        val firstTileWidth = getCardWidth(tiles.first())
        ((containerWidth - firstTileWidth) / 2f).coerceAtLeast(0f)
    } else {
        0f
    }
}

val endPadding = remember(containerWidth, tiles) {
    if (containerWidth > 0 && tiles.isNotEmpty()) {
        val lastTileWidth = getCardWidth(tiles.last())
        ((containerWidth - lastTileWidth) / 2f).coerceAtLeast(0f)
    } else {
        0f
    }
}

LazyRow(
    // ...
    contentPadding = PaddingValues(
        start = with(density) { startPadding.toDp() },
        end = with(density) { endPadding.toDp() }
    ),
    // ...
)
```
