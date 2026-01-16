# Material 3 DropdownMenu 阴影不跟随圆角问题

## 问题描述
在使用 Jetpack Compose Material 3 的 `DropdownMenu` 组件时，即使设置了圆角 `shape`（如 `RoundedCornerShape(14.dp)`），菜单的 `shadowElevation` 阴影仍然呈现为矩形，不会跟随圆角裁剪。

具体表现为：菜单底部会出现一个明显的矩形阴影"尾巴"，视觉上与圆角菜单不协调。

## 原因分析
这是 Material 3 `DropdownMenu` 内部实现的一个视觉缺陷。`DropdownMenu` 底层使用 `Surface` + `Popup`，而 `shadowElevation` 产生的阴影是在 `Surface` 的 `graphicsLayer` 中绘制的，该阴影基于原始边界计算，不会自动跟随 `clip(shape)` 的圆角裁剪。

## 修复方案
将 `shadowElevation` 设置为 `0.dp`，完全移除矩形阴影：

```kotlin
DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    shape = RoundedCornerShape(14.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,   // 保留色调提升，提供层次感
    shadowElevation = 0.dp,  // 移除矩形阴影
    // ...
)
```

## 替代方案（如需阴影效果）
如果确实需要阴影来增强层次感，可以在外层包裹一个带 `Modifier.shadow()` 的容器，手动指定圆角：

```kotlin
Box(
    modifier = Modifier
        .shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(14.dp),
            clip = false
        )
) {
    DropdownMenu(
        shadowElevation = 0.dp,
        // ...
    )
}
```

但由于 `DropdownMenu` 是 Popup，外层包裹可能不生效。推荐直接使用 `tonalElevation` 提供的 Material 3 色调映射来表达层次，视觉效果同样出色。

## 修改文件
- `app/src/main/java/com/example/livewallpaper/ui/components/AppDropdownMenu.kt`
  - 将 `shadowElevation` 默认值从 `8.dp` 改为 `0.dp`
