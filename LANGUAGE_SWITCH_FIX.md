# 语言切换闪黑问题修复

## 问题描述
切换语言后保存退出到主页时会闪黑一下。

## 问题原因
当用户在设置页面切换语言后，`AppCompatDelegate.setApplicationLocales()` 会触发整个应用的配置变更（Configuration Change），导致 MainActivity 被系统重建。在 Activity 重建过程中，由于窗口背景未设置，会短暂显示系统默认的黑色背景，造成闪黑现象。

## 解决方案
在应用主题中设置窗口背景色，使其与 Compose 主题的背景色保持一致，避免 Activity 重建时出现黑屏。

### 修改内容

1. **themes.xml** - 添加窗口背景色和禁用内容过渡动画
   - 添加 `android:windowBackground` 属性，引用颜色资源
   - 添加 `android:windowContentTransitions="false"` 禁用窗口内容过渡动画，减少闪烁

2. **values/colors.xml** - 定义浅色主题窗口背景色
   - 添加 `window_background` 颜色：`#FFE8F5E9`（薄荷绿，与 Compose 主题的 BackgroundMint 一致）

3. **values-night/colors.xml** - 定义深色主题窗口背景色
   - 创建夜间模式资源目录
   - 添加 `window_background` 颜色：`#FF1A2E26`（深色背景，与 Compose 主题的 DarkBackground 一致）

## 技术细节

### 为什么会发生闪黑？
- `AppCompatDelegate.setApplicationLocales()` 会触发系统级别的配置变更
- 配置变更会导致所有 Activity 被销毁并重建
- Activity 重建时，如果没有设置 `windowBackground`，系统会使用默认的黑色背景
- Compose UI 的渲染需要时间，在 UI 完全渲染之前，会短暂显示窗口背景色

### 为什么这个方案有效？
- 通过设置 `windowBackground` 为与 Compose 主题一致的颜色，使得 Activity 重建时的背景色与应用主题保持一致
- 用户看到的是平滑的颜色过渡，而不是突兀的黑屏
- 禁用窗口内容过渡动画进一步减少了视觉闪烁

## 测试建议
1. 在浅色主题下切换语言，观察是否还有闪黑现象
2. 在深色主题下切换语言，观察是否还有闪黑现象
3. 在跟随系统主题模式下切换语言，观察是否还有闪黑现象
4. 测试从英文切换到中文，以及从中文切换到英文的场景
