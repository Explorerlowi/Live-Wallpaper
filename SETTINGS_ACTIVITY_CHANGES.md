# 设置界面改动说明

## 改动内容

### 1. 将设置对话框改为独立的 Activity
- 创建了新的 `SettingsActivity.kt`，替代原来的 `SettingsDialog.kt` 对话框
- 使用 Activity 可以提供更好的用户体验和更灵活的界面控制

### 2. 添加从右侧滑入的动画效果
创建了以下动画资源文件：
- `slide_in_right.xml` - 从右侧滑入
- `slide_out_left.xml` - 向左侧滑出
- `slide_in_left.xml` - 从左侧滑入
- `slide_out_right.xml` - 向右侧滑出

在 `themes.xml` 中配置了全局的 Activity 动画样式：
```xml
<style name="ActivitySlideAnimation" parent="@android:style/Animation.Activity">
    <item name="android:activityOpenEnterAnimation">@anim/slide_in_right</item>
    <item name="android:activityOpenExitAnimation">@anim/slide_out_left</item>
    <item name="android:activityCloseEnterAnimation">@anim/slide_in_left</item>
    <item name="android:activityCloseExitAnimation">@anim/slide_out_right</item>
</style>
```

### 3. 配置全局不息屏
在 `AndroidManifest.xml` 中为所有 Activity 添加了 `android:keepScreenOn="true"` 属性：
- `MainActivity` - 主界面不息屏
- `SettingsActivity` - 设置界面不息屏

同时在代码中也添加了保持屏幕常亮的标志：
```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

### 4. 更新 SettingsScreen 调用方式
- 使用 `ActivityResultLauncher` 启动 `SettingsActivity`
- 通过 Intent 传递参数和接收返回结果
- 移除了原来的对话框显示逻辑

## 文件变更列表

### 新增文件
- `app/src/main/java/com/example/livewallpaper/ui/SettingsActivity.kt`
- `app/src/main/res/anim/slide_in_right.xml`
- `app/src/main/res/anim/slide_out_left.xml`
- `app/src/main/res/anim/slide_in_left.xml`
- `app/src/main/res/anim/slide_out_right.xml`

### 修改文件
- `app/src/main/AndroidManifest.xml` - 添加 SettingsActivity 声明和不息屏配置
- `app/src/main/java/com/example/livewallpaper/MainActivity.kt` - 添加不息屏配置
- `app/src/main/java/com/example/livewallpaper/ui/SettingsScreen.kt` - 改用 Activity 启动方式
- `app/src/main/res/values/themes.xml` - 添加滑动动画样式

## 使用说明

1. 点击主界面的设置按钮，会从右侧滑入打开设置界面
2. 点击返回按钮或系统返回键，设置界面会向右侧滑出关闭
3. 所有界面都会保持屏幕常亮，不会自动息屏
4. 设置的修改会在返回时自动保存

## 注意事项

- 原来的 `SettingsDialog.kt` 文件保留，如需完全移除可以手动删除
- 动画时长设置为 300ms，可以根据需要调整
- 不息屏功能只在应用运行时生效，退出应用后会恢复正常息屏行为
