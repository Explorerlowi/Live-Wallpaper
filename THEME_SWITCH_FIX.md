# 主题切换即时生效修复

## 问题描述
之前主题切换后没有立即生效，需要返回后才能看到变化。

## 解决方案

### 1. 在 SettingsActivity 中使用可变状态
```kotlin
setContent {
    // 使用 remember 和 mutableStateOf 来响应主题变化
    var themeMode by remember { mutableStateOf(currentThemeMode) }
    
    LiveWallpaperTheme(themeMode = themeMode) {
        SettingsScreen(
            // ...
            onThemeModeChange = { newThemeMode ->
                themeMode = newThemeMode  // 立即更新主题
                intent.putExtra("themeMode", newThemeMode.name)
                setResult(RESULT_OK, intent)
            },
            // ...
        )
    }
}
```

**关键点：**
- 使用 `var themeMode by remember { mutableStateOf(currentThemeMode) }` 创建可变状态
- 将 `themeMode` 传递给 `LiveWallpaperTheme`
- 在 `onThemeModeChange` 回调中立即更新 `themeMode` 状态

### 2. 主题切换时立即调用回调
```kotlin
FilterChip(
    selected = selectedThemeMode == mode,
    onClick = { 
        selectedThemeMode = mode
        // 立即应用主题变化
        onThemeModeChange(mode)
    },
    // ...
)
```

**效果：**
- 用户点击主题选项时，界面立即切换主题
- 不需要等到返回或保存才能看到效果

### 3. 处理"放弃"操作
当用户修改主题后选择"放弃"退出时，需要恢复原来的主题：

```kotlin
onDiscardAndExit = {
    // 如果主题被修改了，需要恢复原来的主题
    if (selectedThemeMode != currentThemeMode) {
        onThemeModeChange(currentThemeMode)
    }
    showExitConfirmDialog = false
    onBack()
}
```

## 工作流程

### 正常保存流程
1. 用户切换主题 → 界面立即变化
2. 用户点击返回 → 弹出确认对话框
3. 用户选择"保存" → 保存所有设置并返回
4. 主题已经应用，无需额外操作

### 放弃修改流程
1. 用户切换主题 → 界面立即变化
2. 用户点击返回 → 弹出确认对话框
3. 用户选择"放弃" → 恢复原来的主题并返回
4. 主界面保持原来的主题

### 无修改流程
1. 用户只是浏览设置
2. 用户点击返回 → 直接返回（不弹窗）
3. 主题保持不变

## 技术细节

### Compose 状态管理
- 使用 `remember` 保持状态在重组时不丢失
- 使用 `mutableStateOf` 创建可观察的状态
- 状态变化会自动触发 UI 重组

### 主题应用机制
- `LiveWallpaperTheme` 接收 `themeMode` 参数
- 根据 `themeMode` 决定使用浅色或深色主题
- 主题变化会影响所有使用 `MaterialTheme` 的组件

### 即时反馈
- 用户操作 → 状态更新 → UI 重组 → 视觉反馈
- 整个过程在一帧内完成，用户感觉是即时的

## 测试场景

1. **切换到浅色主题**
   - 点击"浅色" → 界面立即变为浅色
   - 点击返回 → 选择"保存" → 主界面也变为浅色

2. **切换到深色主题**
   - 点击"深色" → 界面立即变为深色
   - 点击返回 → 选择"保存" → 主界面也变为深色

3. **切换后放弃**
   - 点击"深色" → 界面立即变为深色
   - 点击返回 → 选择"放弃" → 界面恢复原来的主题

4. **跟随系统**
   - 点击"跟随系统" → 界面根据系统主题变化
   - 系统是深色 → 显示深色
   - 系统是浅色 → 显示浅色

## 优势

1. **即时反馈** - 用户立即看到主题变化效果
2. **可撤销** - 选择"放弃"可以恢复原来的主题
3. **体验流畅** - 不需要重启 Activity 或等待
4. **符合预期** - 符合现代应用的交互习惯
