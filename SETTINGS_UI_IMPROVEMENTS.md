# 设置界面优化说明

## 改进内容

### 1. 顶部栏颜色优化
- 将 TopAppBar 的背景色设置为透明 (`Color.Transparent`)
- 整个界面使用统一的渐变背景（MintGreen100 到 MintGreen200）
- 视觉效果更加统一和美观

### 2. 智能保存机制
采用了更友好的"返回时询问"方式，而不是在右上角放置保存按钮：

**优点：**
- 用户体验更自然，不会误操作丢失修改
- 符合 Android 设计规范
- 减少界面元素，更简洁

**实现逻辑：**
1. 自动检测设置是否有修改（使用 `remember` 和 `derivedStateOf`）
2. 点击返回按钮或系统返回键时：
   - 如果没有修改：直接返回
   - 如果有修改：弹出确认对话框
3. 确认对话框提供三个选项：
   - **保存** - 保存修改并退出
   - **放弃** - 放弃修改并退出
   - **取消** - 继续编辑

### 3. 系统返回键拦截
使用 `BackHandler` 拦截系统返回键，确保用户按返回键时也会触发保存确认逻辑。

## 技术实现细节

### 自动检测修改
```kotlin
val hasChanges = remember(intervalValue, selectedScaleMode, selectedPlayMode, selectedLanguage, selectedThemeMode) {
    intervalValue.toLong() != currentInterval ||
    selectedScaleMode != currentScaleMode ||
    selectedPlayMode != currentPlayMode ||
    selectedLanguage != initialLanguage ||
    selectedThemeMode != currentThemeMode
}
```

### 退出确认对话框
- 标题：保存更改？
- 内容：您有未保存的更改，是否要在退出前保存？
- 按钮：
  - 保存（主要操作，Teal300 颜色）
  - 放弃（警告色，红色）
  - 取消（默认色）

### 背景渐变
```kotlin
.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            MintGreen100,
            MintGreen200.copy(alpha = 0.5f)
        )
    )
)
```

## 新增字符串资源

### 英文 (values/strings.xml)
```xml
<string name="exit_confirm_title">Save Changes?</string>
<string name="exit_confirm_message">You have unsaved changes. Do you want to save them before exiting?</string>
<string name="save_and_exit">Save</string>
<string name="discard_and_exit">Discard</string>
```

### 中文 (values-zh-rCN/strings.xml)
```xml
<string name="exit_confirm_title">保存更改？</string>
<string name="exit_confirm_message">您有未保存的更改，是否要在退出前保存？</string>
<string name="save_and_exit">保存</string>
<string name="discard_and_exit">放弃</string>
```

## 用户体验流程

1. 用户打开设置界面（从右侧滑入）
2. 修改任意设置项（间隔、缩放模式、播放模式、主题、语言）
3. 点击返回按钮或按系统返回键
4. 如果有修改：
   - 弹出确认对话框
   - 用户选择"保存"：保存所有修改并返回
   - 用户选择"放弃"：放弃所有修改并返回
   - 用户选择"取消"：关闭对话框，继续编辑
5. 如果没有修改：直接返回主界面

## 视觉效果

- 顶部栏透明，与背景融为一体
- 整个界面使用薄荷绿渐变背景
- 返回按钮和标题清晰可见
- 确认对话框采用圆角设计，与整体风格一致

## 注意事项

- 所有修改都是临时的，只有在用户确认保存后才会真正应用
- 系统返回键和界面返回按钮行为完全一致
- 对话框的"放弃"按钮使用警告色（红色），提醒用户这是一个不可逆操作
- "保存"按钮使用主题色（Teal300），突出显示为推荐操作
