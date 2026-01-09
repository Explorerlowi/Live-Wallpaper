# Compose TextField 光标手柄错位问题记录

## 问题描述
在 Android 14 设备上，使用 Jetpack Compose BOM `2025.12.01` (Compose UI 1.7+) 版本时，`TextField` 或 `BasicTextField` 的光标手柄（Cursor Handle，即光标下方的水滴状图标）会出现位置错乱。具体表现为手柄从光标底部“跑”到了光标顶部甚至更上方，与光标本体分离。

但在 Android 16（或 Android 15+）设备上未复现此问题。

## 修复方案
在 `MainActivity` 或 `Theme.kt` 中，手动开启 Edge-to-Edge（沉浸式）布局：

```kotlin
// 开启沉浸式，内容延伸到系统栏下方
WindowCompat.setDecorFitsSystemWindows(window, false)

// 将状态栏和导航栏设为透明
window.statusBarColor = Color.Transparent.toArgb()
window.navigationBarColor = Color.Transparent.toArgb()
```

## 原因分析

### 1. 坐标系冲突
*   **旧布局模式（FitsSystemWindows = true）**：系统会为 Window 添加一个顶部偏移（状态栏高度），应用内容的 `(0,0)` 实际上是屏幕的 `(0, statusBarHeight)`。
*   **新版 Compose 的假设**：Compose UI 1.7+ 在重构文本组件时，计算 Popup（光标手柄本质是悬浮窗）的屏幕绝对坐标时，可能默认假设应用运行在 Edge-to-Edge 模式下，或者在处理系统窗口偏移时出现了重复扣除或漏算。

### 2. 为什么 Android 16 无此问题？
Android 15 (API 35) 及更高版本引入了 **强制 Edge-to-Edge** 策略。只要 `targetSdk >= 35`，系统会忽略 `setDecorFitsSystemWindows(true)` 的请求，强制应用全屏绘制。因此在高版本系统上，应用的坐标系原点与屏幕物理原点天然重合，规避了坐标换算带来的 Bug。

### 3. 为什么 Android 14 有问题？
Android 14 默认不开启强制沉浸式。如果开发者没有手动调用 `setDecorFitsSystemWindows(false)`，应用处于“非沉浸式”模式，Compose 在计算光标绝对坐标时与系统窗口的偏移量产生冲突，导致手柄绘制位置错误。

## 最佳实践
始终为 Android 应用手动开启 Edge-to-Edge 体验，这不仅符合 Material Design 3 的设计规范，也是适配 Android 15+ 的强制要求，同时能避免低版本系统上因坐标系偏差导致的 UI 渲染问题。
