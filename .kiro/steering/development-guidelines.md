---
inclusion: always
---
# 项目开发规范（Kotlin Multiplatform App）

## 一、技术栈约束

### 1. 语言 / 平台

* **共享模块（:shared）**

  * 语言：仅使用 **Kotlin**；
  * 禁止引用任何平台专属 API（如 Android SDK、iOS UIKit / SwiftUI）；
  * 只允许使用 **KMP 支持的库**（支持 `commonMain` 的依赖）。

* **Android 客户端（:androidApp）**

  * 语言：仅使用 **Kotlin**（禁止 Java 新代码）；
  * UI：**Jetpack Compose（Material3）**，禁止 XML 布局；
  * 严禁在 Android 代码中写核心业务逻辑，业务必须下沉到 `shared`。

* **iOS 客户端（:iosApp，可选）**

  * 语言：**Swift 5+**；
  * UI：**SwiftUI**；
  * 业务逻辑尽量通过调用 `shared` 暴露的接口完成，禁止在 iOS 侧复制业务逻辑。

---

### 2. 架构约束

* 全局架构：

  * **Clean Architecture + MVVM + 多模块**；
  * 层次划分：

    * **data 层**：数据源实现（远程 / 本地）、仓库实现；
    * **domain 层**：业务用例（UseCase）、领域模型（纯 Kotlin）；
    * **presentation/ui 层**：UI 状态（State）、事件（Event）、ViewModel。

* 共享模块（`shared`）中的分层：

  * `data`：`remote`、`local`、`repository`；
  * `domain`：`model`、`usecase`；
  * `presentation`：`viewmodel`、`state`、`effect`。

* 平台端（Android / iOS）职责：

  * 只负责：

    * UI 渲染；
    * 导航（Navigation）；
    * 平台能力包装（如权限、相机、系统分享等）；
  * 业务流程、数据转换、错误处理尽量在 `shared` 完成。

---

### 3. 网络与数据

* **网络库（共享模块）**

  * 使用 **Ktor Client**（支持多平台）：

    * 版本：`2.x`（具体版本在根 `libs.versions.toml` 统一约定）；
    * 在 `commonMain` 创建基础网络模块：`core/network`。
  * JSON 序列化：

    * 使用 **kotlinx.serialization**；
    * 所有接口数据模型必须使用 `@Serializable` 标注；
    * 禁止在共享模块中使用 Gson / Moshi 等 Android 专属库。

* **错误与结果封装**

  * 所有对外暴露的业务接口（UseCase / Repository）返回统一结果类型：

    * 如：`Result<T>` / 自定义 `AppResult<T>`，统一处理：

      * 成功（Success）；
      * 已知业务错误（BusinessError）；
      * 网络错误 / 未知异常（NetworkError / UnknownError）。
  * Shared 中提供统一的错误类型定义：

    ```kotlin
    sealed class AppError : Throwable() {
        object Network : AppError()
        data class Server(val code: Int, val message: String?) : AppError()
        object Unauthorized : AppError()
        object Unknown : AppError()
    }
    ```

---

### 4. 本地存储

* **Key-Value 配置**

  * 使用多平台支持的存储方案（任选其一，全项目统一）：

    * 推荐：`multiplatform-settings`；
  * 不允许在 Android 直接写 `SharedPreferences`，除非通过 expect/actual 封装。

* **结构化数据（数据库）**

  * 若需要数据库，优先使用支持 KMP 的库，例如：

    * `SQLDelight` 或 `Realm Kotlin`；
  * 禁止在共享模块中直接使用 Android SQLite API；
  * 所有数据库操作通过 Repository + UseCase 对上层暴露。

---

### 5. 协程与响应式

* 协程：

  * 全局使用 **Kotlin Coroutines**；
  * 禁止在 shared 中使用 Thread、Executors 等平台多线程 API。

* 流式数据：

  * 使用 **Flow / StateFlow / SharedFlow** 表达异步数据和状态；
  * UI 层（Android Compose / iOS SwiftUI）仅订阅 Flow / StateFlow。

* 协程调度：

  * 在 shared 中使用抽象调度器（如 `CoroutineDispatcherProvider`）：

    * 便于测试和平台适配；
  * 不允许在代码中直接出现 `Dispatchers.IO` / `Dispatchers.Main`（统一从注入的调度器获取）。

---

### 6. 依赖注入（DI）

* 共享模块：

  * 使用支持 KMP 的 DI 框架（建议一种，全项目统一）：

    * 推荐：**Koin 3.x（KMP 版本）**；
  * 所有 Repository、UseCase、ViewModel 均通过 DI 管理实例，禁止手写单例。

* Android 端：

  * 如需集成 Hilt，仅用于 Android 特有依赖；
  * 禁止在 Android 侧重新创建 shared 中已经通过 Koin 提供的单例；
  * Android 启动时通过桥接把 shared 中的 Koin 启动起来。

* iOS 端：

  * 通过一层初始化函数（如 `initKoin()`）在 App 启动时初始化 shared 依赖。

---

### 7. 其他技术约束

* 构建系统：

  * 统一使用 **Gradle Kotlin DSL**（`build.gradle.kts`）；
  * 所有依赖版本在根目录 `gradle/libs.versions.toml` 或统一版本管理文件中维护；
  * 禁止在子模块中直接写死版本号。

* 库使用约束：

  * 仅允许使用经过评审、确认支持 KMP 的第三方库；
  * Android 专有库（如 `androidx`、`Material`）只能在 `androidApp` 模块中使用；
  * iOS 专有库（如 `UIKit`、`SwiftUI`）只能在 `iosApp` 中写 Swift 使用。

---

### 8. 多语言与本地化（i18n）
* **支持语言**

  * 目前仅支持：**简体中文（zh-CN）**、**英文（en）**；
  * 默认跟随系统语言；后续如需在应用内切换语言，统一通过 shared 的 `LanguageManager` 处理，不允许各端自行维护语言状态。

* **架构约束**

  * 所有**展示给用户的文本**必须走 i18n 体系获取，禁止在代码中硬编码文案（调试日志除外）；
  * 文案 key 和语言管理逻辑放在 `shared` 模块的 `core/i18n` 中，由平台侧（Android / iOS）适配到各自资源系统；
  * 仅允许在共享模块中定义「文案标识」与「语言枚举」，具体文本内容在各平台资源文件中维护。

* **shared 模块约定**

  * 提供统一语言枚举，例如 `enum class AppLanguage { ZH_CN, EN }`；
  * 提供统一字符串标识类型（例如 `enum class AppStringId { COMMON_OK, COMMON_CANCEL, HOME_TITLE, ... }`），所有 UI 文案必须通过该标识访问；
  * 提供统一接口（例如 `interface StringsProvider { fun get(id: AppStringId): String }`）；
  * 实现 `expect fun getCurrentLanguage(): AppLanguage`，由 `androidMain` / `iosMain` 提供 `actual` 实现，读取系统语言；
  * 如果以后支持「应用内切换语言」，通过 shared 中的 `LanguageManager` 维护当前语言，并暴露为 `StateFlow<AppLanguage>`。

* **Android 端约定**

  * 文案内容统一放在 `res/values/strings.xml`（英文默认）、`res/values-zh-rCN/strings.xml`（简体中文）；
  * Android 侧实现 `StringsProvider` 时，只允许通过 `R.string.xxx` 访问字符串；
  * Compose 中使用 `stringResource(R.string.xxx)` 或封装的 `AppStrings`，禁止直接写 `"xxx"` 字面量。

* **iOS 端约定（如启用）**

  * 使用 `Localizable.strings` 管理文案：`en.lproj/Localizable.strings`、`zh-Hans.lproj/Localizable.strings`；
  * SwiftUI 中通过 `NSLocalizedString` 或 `Text("key")` + 对应 strings 文件取文案；
  * iOS 侧的 `StringsProvider` 仅负责将 `AppStringId` 映射到对应的 `NSLocalizedString` key。

---

## 二、代码风格规范

### 1. 命名规范

* 通用命名：

  * 类 / 接口：`UpperCamelCase`（如 `LoginViewModel`, `UserRepository`）；
  * 函数 / 变量：`lowerCamelCase`（如 `getUser`, `userName`)；
  * 常量：`UPPER_SNAKE_CASE`（如 `MAX_RETRY_COUNT`）。

* KMP 相关命名：

  * `expect` / `actual` 声明：

    * 以功能命名，不带平台后缀，如：`PlatformLogger`、`PlatformConfig`；
    * 实现文件使用平台目录区分：`androidMain` / `iosMain`。
  * Flow / State：

    * State 类统一后缀：`xxxUiState`；
    * 事件类统一后缀：`xxxUiEvent`；
    * 单次效果类统一后缀：`xxxUiEffect`。

* 包名：

  * 根包统一：`com.example.myapp`（按真实项目替换）；
  * feature 包结构：

    * `feature.login.data.remote`
    * `feature.login.domain.usecase`
    * `feature.login.presentation.viewmodel`

---

### 2. 注释与文档

* KDoc：

  * `public` / `internal` 对外暴露的类与函数 **必须写 KDoc**：

    * 功能描述；
    * 参数说明；
    * 返回值说明；
    * 特别注意的异常 / 副作用。

* 复杂逻辑：

  * 涉及跨平台实现、协程并发、缓存策略等复杂逻辑必须补充分段注释：

    * 简要说明「为什么这样设计」；
    * 跨平台影响点。

* 禁止事项：

  * 禁止使用无意义注释（如 `// TODO something` 持久遗留）；
  * 禁止中英文混写缩写过度造成阅读困难（可中英结合，但要统一）。

---

### 3. 代码格式

* 格式化工具：

  * 统一使用 `ktlint` 或 `Ktlint + Spotless` 作为格式检查；
  * 提交代码前必须通过格式检查。
* 基本规范：

  * 缩进：4 空格；
  * 每行最大长度：120 字符；
  * 单个函数长度原则上不超过 40 行，超过需考虑拆分。
* Kotlin 特性优先：

  * 优先使用：

    * data class / sealed class；
    * 扩展函数；
    * `when` 表达式；
  * 禁止写 Java 风格 getter/setter，使用 Kotlin 属性语法。
* 所有展示给用户的文本必须从 i18n 资源获取，不得直接写死在代码里（调试 log 除外）

---

## 三、项目结构规范

### 1. 模块划分

* 根模块结构（示例）：

  ```text
  :androidApp        // Android 客户端
  :iosApp            // iOS 客户端（可选）
  :shared            // KMP 共享业务模块
  :core:design       // 可选：设计系统 / UI 组件
  :core:test         // 测试工具 / mock
  ```

* 新功能默认先在 `shared` 中建对应的 feature 目录，再在 Android / iOS 侧接 UI。

---

### 2. shared 模块目录结构（核心）

以 `feature: login` 为例：

```text
shared/
 └── src/
     └── commonMain/
         └── kotlin/com/example/myapp/
             ├── core/
             │   ├── network/        // 网络封装，ApiClient、HttpConfig
             │   ├── error/          // AppError、错误映射
             │   ├── model/          // 通用模型（如分页参数）
             │   ├── i18n/           // ★ 多语言相关
             │   │   ├── AppLanguage.kt        // 语言枚举（ZH_CN / EN）
             │   │   ├── AppStringId.kt       // 文案枚举（所有 UI 文案 key）
             │   │   ├── StringsProvider.kt   // 获取文案的接口
             │   │   └── LanguageManager.kt   // 当前语言管理（跟随系统 / 应用内切换）
             │   └── util/          // 通用工具
             └── feature/
                 └── login/
                     ├── data/
                     │   ├── remote/
                     │   ├── local/
                     │   └── repository/
                     ├── domain/
                     │   ├── model/
                     │   └── usecase/
                     └── presentation/
                         ├── state/
                         └── viewmodel/

```

* 平台特定代码：

```text
shared/
 └── src/
     ├── androidMain/
     │   └── kotlin/com/example/myapp/core/i18n/
     │       ├── AndroidStringsProvider.kt   // 使用 R.string 映射 AppStringId
     │       └── AndroidLanguageResolver.kt  // actual getCurrentLanguage()
     └── iosMain/
         └── kotlin/com/example/myapp/core/i18n/
             ├── IOSStringsProvider.kt       // 调用 NSLocalizedString
             └── IOSLanguageResolver.kt      // actual getCurrentLanguage()

```

例如：

* `PlatformLogger` 在 `commonMain` 声明 `expect class PlatformLogger`；
* 在 `androidMain` / `iosMain` 提供 `actual class PlatformLogger` 实现。

---

### 3. Android 模块结构

* 包名根目录：`com.example.myapp.android`
* 按功能拆包（对应 shared 的 feature）：

```text
androidApp/
 └── src/main/
     ├── java/com/example/myapp/android/
     │   ├── App.kt
     │   ├── navigation/
     │   ├── core/ui/
     │   └── feature/
     └── res/
         ├── values/
         │   └── strings.xml          // 默认英文文案（en），key 与 AppStringId 对齐
         ├── values-zh-rCN/
         │   └── strings.xml          // 简体中文文案
         └── values-xx/               // 未来新增语言时再扩展

```

* Compose 规范：

  * 所有界面入口组件统一命名为 `XXXScreen`；
  * UI 仅通过调用 shared 中的 ViewModel / UseCase 获取数据与触发事件。

---

### 4. iOS 模块结构（如启用）

* 目录示例：

```text
iosApp/
 └── iosApp/
     ├── ContentView.swift         // 根视图
     ├── AppDelegate.swift / main  // 启动入口
     └── feature/
         └── login/
             ├── LoginScreen.swift     // SwiftUI 页面
             └── LoginViewModelBridge // 包装 shared ViewModel 的桥接层
```

* Swift 端禁止直接写业务逻辑，只负责把用户交互转发到 shared 的 ViewModel / UseCase。

---

## 四、上下文关联与通用约束

### 1. ViewModel 规范

* Shared 中所有 ViewModel 必须继承统一基类，例如：

```kotlin
abstract class BaseViewModel<STATE, EVENT, EFFECT>(
    initialState: STATE
) {
    val uiState: StateFlow<STATE>
    val effect: SharedFlow<EFFECT>

    abstract fun onEvent(event: EVENT)
}
```

* 特定 feature 的 ViewModel（如 `LoginViewModel`）在 `presentation.viewmodel` 中实现：

  * 只依赖 Domain 层（UseCase）；
  * 不允许直接依赖 Remote / Local DataSource。

---

### 2. 网络调用通用规范

* 所有网络调用必须通过：

  * `core.network.ApiClient` 或同一封装入口；
  * 统一拦截：

    * 日志（仅在 debug 打）；
    * 公共 header（如 token、版本号）；
    * 错误码转换 → `AppError`。

* 禁止：

  * 在 ViewModel 中直接写 Ktor 调用；
  * 在 Android / iOS UI 层发网络请求。

---

### 3. 主题与 UI 规范（Android）

* 全局主题：

  * 所有 Compose 页面必须被 `MyAppTheme` 包裹；
  * 颜色 / 字体 / 间距统一定义在 `core:design` 模块。
* 组件复用：

  * 通用按钮、输入框、列表项等抽到 `core:design` 或 `core/ui` 中；
  * feature 内部不用重复造轮子。

---

### 4. 平台差异封装

* 所有「平台相关」能力必须通过 `expect/actual` 封装：

  * 日志（Log）；
  * 本地存储（Key-Value）；
  * 时间 / 语言 / 设备信息；
  * 系统分享、剪贴板等。

示例：

```kotlin
// commonMain
expect class PlatformLogger() {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
```

* 禁止在 `commonMain` 中引用任何形如 `android.*` / `platform.UIKit` 的 API。

---

### 5. 测试规范（简单约定）

* Unit Test：

  * Domain 层和 Repository 层必须可测：

    * 使用 Fake / Mock DataSource；
    * 不依赖真实网络 / 数据库。
* Shared 侧使用 **多平台测试**：

  * `commonTest` 目录放通用测试；
  * Android / iOS 特有行为放在各自 `androidTest` / `iosTest`。

---