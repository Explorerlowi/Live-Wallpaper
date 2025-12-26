# Live Wallpaper

> A dynamic wallpaper app developed with Kotlin Multiplatform (KMP) and Cursor, supporting Android platform. Create personalized dynamic wallpaper experiences by cycling through multiple images.

一个基于 Kotlin Multiplatform (KMP) 和 Cursor 开发的动态壁纸应用，支持 Android 平台。通过轮播多张图片创建个性化的动态壁纸体验。

---

## 📖 Project Description / 项目介绍

**English:**

Live Wallpaper is a modern Android application built with Kotlin Multiplatform (KMP) and developed using Cursor IDE. It allows users to create dynamic wallpapers by cycling through multiple images with customizable settings. The app features a clean architecture, supporting both Chinese and English languages, and provides an intuitive interface for managing and customizing wallpaper collections.

**Latest Update:** The app now integrates AI painting functionality powered by Google Gemini models. Users can generate beautiful images through text descriptions and reference images, with support for multiple models, parameter adjustments, and session management. Additionally, theme switching has been added with support for light, dark, and system-follow modes.

**中文:**

Live Wallpaper 是一款使用 Kotlin Multiplatform (KMP) 技术栈开发、通过 Cursor IDE 构建的现代化 Android 动态壁纸应用。用户可以通过轮播多张图片来创建个性化动态壁纸，并支持丰富的自定义设置。应用采用清晰的架构设计，支持中英文双语，提供直观的界面用于管理和自定义壁纸集合。

**最新更新：** 应用现已集成 AI 绘画功能，基于 Google Gemini 模型，用户可以通过文本描述和参考图生成精美图片，支持多模型选择、参数调整和会话管理等高级功能。同时新增了主题切换功能，支持浅色、深色和跟随系统三种模式。

## ✨ 功能特性

### 动态壁纸功能

- 🖼️ **多图片轮播**：支持添加多张图片，自动轮播显示
- 🎨 **图片裁剪**：支持对每张图片进行裁剪和位置调整
- 🖼️ **图库选择**：支持从设备图库中选择图片
- 🎯 **多选管理**：支持批量选择和删除图片
- 🔀 **图片顺序调整**：支持拖拽调整图片显示顺序
- ⏱️ **自定义间隔**：可设置图片切换间隔时间（1-60秒）
- 📐 **缩放模式**：提供填充（CENTER_CROP）和适应（FIT_CENTER）两种缩放模式
- 🎲 **播放模式**：支持顺序播放和随机播放两种模式

### AI 绘画功能 ✨ NEW

- 🤖 **AI 图片生成**：基于 Google Gemini 模型，通过文本描述生成图片
- 🖼️ **参考图支持**：支持上传参考图片，辅助 AI 生成更精准的结果
- 💬 **会话管理**：支持创建、切换和删除多个绘画会话
- 🎯 **多模型选择**：支持 Gemini 2.5 Flash 和 Gemini 3 Pro 两种模型
- 📐 **灵活配置**：可自定义宽高比（1:1、16:9、9:16）和分辨率
- ✨ **提示词优化**：自动优化用户输入的提示词，提升生成效果
- 📝 **历史记录**：保存所有生成记录，支持查看和管理
- 💾 **图片保存**：支持将生成的图片保存到本地相册
- 🔄 **图片编辑**：支持旋转、镜像等基础编辑功能

### 应用设置

- 🎨 **主题切换**：支持浅色、深色和跟随系统三种主题模式
- 🌐 **多语言支持**：内置中英文（简体中文/英文）切换，所有 UI 文案均通过统一国际化资源管理
- 💾 **配置持久化**：所有配置自动保存，重启后恢复
- 🎬 **流畅动画**：Activity 切换支持滑动动画效果

## 🏗️ 技术架构

### 技术栈

- **语言**：Kotlin
- **平台**：Kotlin Multiplatform（KMP）
- **UI 框架**：Jetpack Compose（Material3）
- **架构模式**：Clean Architecture + MVVM
- **依赖注入**：Koin
- **图片加载**：Coil
- **媒体处理**：Media3 Effect
- **数据存储**：Multiplatform Settings
- **序列化**：kotlinx.serialization
- **网络请求**：Ktor Client
- **AI 模型**：Google Gemini API

### 项目结构

```
Live Wallpaper/
├── app/                    # Android 应用模块
│   ├── src/main/
│   │   ├── java/          # Android 平台代码
│   │   └── res/           # Android 资源文件
│   └── build.gradle.kts
├── shared/                 # KMP 共享模块
│   └── src/
│       ├── commonMain/    # 通用业务逻辑
│       ├── androidMain/   # Android 平台实现
│       └── iosMain/      # iOS 平台实现（预留）
├── core/
│   ├── design/           # 设计系统模块
│   └── test/             # 测试工具模块
├── build.gradle.kts      # 根构建文件
├── settings.gradle.kts   # 项目设置
└── gradle/
    └── libs.versions.toml # 依赖版本管理
```

### 模块说明

- **`:app`**：Android 应用入口，包含 UI 层和平台特定实现
- **`:shared`**：共享业务逻辑，包含数据层、领域层和表现层
- **`:core:design`**：设计系统和通用 UI 组件
- **`:core:test`**：测试工具和 Mock 数据

## 🚀 快速开始

### 环境要求

- **JDK**：11 或更高版本
- **Android Studio**：最新稳定版（推荐）
- **Android SDK**：
  - `compileSdk`：36
  - `minSdk`：24
  - `targetSdk`：36
- **Gradle**：8.13.1（已包含 Gradle Wrapper）
- **Gemini API Key**：使用 AI 绘画功能需要（可选）

### 克隆项目

```bash
git clone <repository-url>
cd "Live Wallpaper"
```

### 配置签名（可选）

如需生成 Release 版本，需要配置签名：

1. 复制签名配置模板：
   ```bash
   cp keystore.properties.example keystore.properties
   ```

2. 编辑 `keystore.properties`，填写签名信息：
   ```properties
   storeFile=app/release.keystore
   storePassword=your_store_password
   keyAlias=release
   keyPassword=your_key_password
   ```

3. 如果还没有签名文件，可以生成一个：
   ```bash
   keytool -genkey -v -keystore app/release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
   ```

> ⚠️ **注意**：`keystore.properties` 文件已添加到 `.gitignore`，不会被提交到版本控制。

### 构建项目

#### Debug 版本

```bash
# Windows
.\gradlew assembleDebug

# macOS/Linux
./gradlew assembleDebug
```

生成的 APK 位置：`app\debug\app-debug.apk`

#### Release 版本

```bash
# Windows
.\gradlew assembleRelease

# macOS/Linux
./gradlew assembleRelease
```

生成的 APK 位置：`app\release\app-release.apk`

### 运行项目

1. 使用 Android Studio 打开项目
2. 连接 Android 设备或启动模拟器
3. 点击运行按钮或使用快捷键 `Shift + F10`

## 📱 使用说明

### 设置动态壁纸

1. **添加图片**：
   - 打开应用后，点击屏幕底部的 "+" 按钮
   - 选择一张或多张图片

2. **配置设置**：
   - 点击右上角的设置图标
   - 调整切换间隔（1-60秒）
   - 选择缩放模式（填充/适应）
   - 选择播放模式（顺序/随机）

3. **调整图片**：
   - 点击图片卡片进入预览
   - 手指缩放拖动进行裁剪和位置调整
   - 保存调整后的设置

4. **设置为壁纸**：
   - 点击底部的"设置为动态壁纸"按钮
   - 确认设置

### 使用 AI 绘画功能

1. **配置 API**：
   - 首次使用需要配置 Gemini API Key
   - 在 AI 绘画界面点击设置按钮
   - 输入你的 API Key 和 API URL（可选）

2. **创建会话**：
   - 点击左上角菜单图标
   - 选择"新建会话"创建新的绘画会话
   - 可以为会话命名以便管理

3. **生成图片**：
   - 在输入框中输入描述文字
   - 可选：点击图片图标上传参考图
   - 选择模型、宽高比和分辨率
   - 点击发送按钮开始生成

4. **管理结果**：
   - 长按消息可以复制或删除
   - 点击生成的图片可以预览、编辑和保存
   - 支持旋转、镜像等基础编辑功能

### 应用设置

1. **主题切换**：
   - 点击主界面右上角的设置图标
   - 选择"主题模式"
   - 可选择浅色、深色或跟随系统

2. **语言切换**：
   - 在设置界面选择"语言"
   - 支持简体中文和英文

### 管理图片

- **删除图片**：长按图片卡片，选择删除
- **重新排序**：通过拖拽调整图片顺序（顺序播放模式下有效）

## 🔧 开发指南

### 代码规范

项目遵循以下规范：

- **架构**：Clean Architecture + MVVM
- **命名**：Kotlin 标准命名规范
- **代码格式**：使用 ktlint 进行代码格式化
- **注释**：公共 API 必须编写 KDoc 注释

详细规范请参考项目根目录的开发规范文档。

### 添加新功能

1. **业务逻辑**：在 `shared` 模块中实现
   - `data`：数据源和仓库实现
   - `domain`：业务用例和领域模型
   - `presentation`：ViewModel 和 UI 状态

2. **UI 实现**：在 `app` 模块中实现
   - 使用 Jetpack Compose 构建 UI
   - 通过 ViewModel 获取数据和触发事件

3. **平台特定功能**：使用 `expect/actual` 机制
   - `commonMain` 中声明 `expect`
   - `androidMain`/`iosMain` 中提供 `actual` 实现

### 依赖管理

所有依赖版本统一在 `gradle/libs.versions.toml` 中管理，子模块通过版本目录引用：

```kotlin
dependencies {
    implementation(libs.androidx.compose.ui)
    implementation(libs.koin.core)
}
```

## 📦 打包发布

详细的打包说明请参考 [打包说明.md](./打包说明.md)。

### 快速打包

1. 确保已配置签名（见"配置签名"章节）
2. 执行打包命令：
   ```bash
   ./gradlew assembleRelease
   ```
3. APK 文件位于：`app/build/outputs/apk/release/app-release.apk`

### 版本号管理

在 `app/build.gradle.kts` 中修改版本信息：

```kotlin
defaultConfig {
    versionCode = 1      // 版本号（整数，每次更新递增）
    versionName = "1.0"  // 版本名称（字符串）
}
```

## 🧪 测试

### 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行 Android 测试
./gradlew connectedAndroidTest
```

### 测试结构

- **单元测试**：`src/test/`
- **Android 测试**：`src/androidTest/`
- **共享模块测试**：`shared/src/commonTest/`

## 🌍 国际化

目前支持以下语言：

- **简体中文**（zh-CN）
- **英文**（en）

文案资源位于：
- Android：`app/src/main/res/values/strings.xml`
- 共享模块：`shared/src/commonMain/kotlin/.../core/i18n/`

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

This project is licensed under the [MIT License](LICENSE).

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

[在此添加联系方式]

---

**注意**：本项目仍在积极开发中，部分功能可能尚未完善。如有问题或建议，欢迎反馈。

