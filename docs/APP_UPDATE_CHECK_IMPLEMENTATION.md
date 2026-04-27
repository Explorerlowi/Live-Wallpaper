# 应用更新检测功能实现文档

本文档详细描述了基于蒲公英（Pgyer）平台的应用更新检测功能实现，适用于 Kotlin Multiplatform (KMP) 项目迁移。

---

## 一、功能概述

该功能通过调用蒲公英 API 检测应用是否有新版本，支持：
- 检测新版本
- 显示版本号和更新说明
- 提供下载链接跳转

---

## 二、架构设计

采用 Clean Architecture + MVVM 架构，分层如下：

```
┌─────────────────────────────────────────────────────────────┐
│                      UI 层 (Android)                         │
│  AppSettingsScreen.kt / WallpaperHomeScreen.kt              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Presentation 层 (shared)                    │
│  SettingsViewModel / SettingsEvent / UpdateStatus           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Domain 层 (shared)                        │
│  WallpaperRepository (接口)                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Data 层 (shared)                         │
│  WallpaperRepositoryImpl / AppUpdateService / PgyerResponse │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、核心代码实现

### 3.1 数据模型 (Data Layer)

#### PgyerResponse.kt
蒲公英 API 响应数据模型：

```kotlin
package com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class PgyerResponse(
    val code: Int,           // 响应码，0 表示成功
    val message: String,     // 响应消息
    val data: PgyerData? = null
)

@Serializable
data class PgyerData(
    val buildHaveNewVersion: Boolean,      // 是否有新版本
    val downloadURL: String? = null,       // 下载地址
    val buildVersion: String? = null,      // 版本号，如 "1.2"
    val buildVersionNo: String? = null,    // 构建号，如 "3"
    val buildUpdateDescription: String? = null  // 更新说明
)
```

### 3.2 网络服务 (Data Layer)

#### AppUpdateService.kt
封装蒲公英 API 调用：

```kotlin
package com.example.livewallpaper.feature.dynamicwallpaper.data.remote

import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model.PgyerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

class AppUpdateService(
    private val client: HttpClient
) {
    /**
     * 检查应用更新
     * @param apiKey 蒲公英 API Key
     * @param appKey 蒲公英 App Key
     * @param buildVersion 当前版本号（如 "1.0.0"）
     * @param buildBuildVersion 当前构建号（如 1）
     * @return PgyerResponse 更新检测结果
     */
    suspend fun checkUpdate(
        apiKey: String,
        appKey: String,
        buildVersion: String? = null,
        buildBuildVersion: Int? = null
    ): PgyerResponse {
        return client.submitForm(
            url = "https://api.pgyer.com/apiv2/app/check",
            formParameters = Parameters.build {
                append("_api_key", apiKey)
                append("appKey", appKey)
                if (buildVersion != null) append("buildVersion", buildVersion)
                if (buildBuildVersion != null) append("buildBuildVersion", buildBuildVersion.toString())
            }
        ).body()
    }
}
```

### 3.3 网络客户端配置

#### HttpClientFactory.kt
Ktor HttpClient 工厂：

```kotlin
package com.example.livewallpaper.core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientFactory {
    
    private val json = Json {
        ignoreUnknownKeys = true  // 忽略未知字段
        isLenient = true          // 宽松解析
        encodeDefaults = true     // 编码默认值
    }
    
    fun create(enableLogging: Boolean = false): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            
            if (enableLogging) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.BODY
                }
            }
            
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
```

### 3.4 Repository 层

#### WallpaperRepository.kt (接口)

```kotlin
interface WallpaperRepository {
    // ... 其他方法
    
    /**
     * 检查应用更新
     */
    suspend fun checkAppUpdate(
        apiKey: String, 
        appKey: String, 
        buildVersion: String?, 
        buildBuildVersion: Int?
    ): PgyerResponse
}
```

#### WallpaperRepositoryImpl.kt (实现)

```kotlin
class WallpaperRepositoryImpl(
    private val settings: ObservableSettings,
    private val appUpdateService: AppUpdateService
) : WallpaperRepository {

    override suspend fun checkAppUpdate(
        apiKey: String,
        appKey: String,
        buildVersion: String?,
        buildBuildVersion: Int?
    ): PgyerResponse {
        return appUpdateService.checkUpdate(apiKey, appKey, buildVersion, buildBuildVersion)
    }
}
```

### 3.5 Presentation 层

#### UpdateStatus.kt
更新状态密封类：

```kotlin
package com.example.livewallpaper.feature.dynamicwallpaper.presentation.state

sealed interface UpdateStatus {
    /** 空闲状态 */
    data object Idle : UpdateStatus
    
    /** 检查中 */
    data object Checking : UpdateStatus
    
    /** 检查成功 */
    data class Success(
        val hasNewVersion: Boolean,    // 是否有新版本
        val version: String?,          // 新版本号
        val desc: String?,             // 更新说明
        val downloadUrl: String?       // 下载链接
    ) : UpdateStatus
    
    /** 检查失败 */
    data class Error(val message: String) : UpdateStatus
}
```

#### SettingsEvent.kt
更新相关事件：

```kotlin
sealed interface SettingsEvent {
    // ... 其他事件
    
    /** 检查更新事件 */
    data class CheckUpdate(
        val apiKey: String,    // 蒲公英 API Key
        val appKey: String,    // 蒲公英 App Key
        val version: String,   // 当前版本号
        val build: Int         // 当前构建号
    ) : SettingsEvent
    
    /** 清除更新状态 */
    data object ClearUpdateStatus : SettingsEvent
}
```

#### SettingsViewModel.kt
ViewModel 处理更新逻辑：

```kotlin
class SettingsViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getConfig(),
        _isLoading,
        _updateStatus
    ) { config, isLoading, updateStatus ->
        SettingsUiState(config, isLoading, updateStatus)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.CheckUpdate -> checkUpdate(event)
                is SettingsEvent.ClearUpdateStatus -> _updateStatus.value = UpdateStatus.Idle
                // ... 其他事件处理
            }
        }
    }

    private suspend fun checkUpdate(event: SettingsEvent.CheckUpdate) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val response = repository.checkAppUpdate(
                event.apiKey, 
                event.appKey, 
                event.version, 
                event.build
            )
            if (response.code == 0 && response.data != null) {
                _updateStatus.value = UpdateStatus.Success(
                    hasNewVersion = response.data.buildHaveNewVersion,
                    version = response.data.buildVersion,
                    desc = response.data.buildUpdateDescription,
                    downloadUrl = response.data.downloadURL
                )
            } else {
                _updateStatus.value = UpdateStatus.Error(response.message)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }
}
```

### 3.6 依赖注入配置

#### AppModule.kt (Koin)

```kotlin
val appModule = module {
    // 网络客户端
    single { HttpClientFactory.create(enableLogging = false) }
    
    // 更新服务
    single { AppUpdateService(get()) }

    // Repository
    single<WallpaperRepository> { WallpaperRepositoryImpl(get(), get()) }
    
    // ViewModel
    factory { SettingsViewModel(get()) }
}
```

---

## 四、Android UI 层实现

### 4.1 BuildConfig 配置

在 `app/build.gradle.kts` 中配置：

```kotlin
android {
    defaultConfig {
        // 从 local.properties 读取密钥
        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) load(file.inputStream())
        }
        
        buildConfigField("String", "PGYER_API_KEY", 
            "\"${localProperties.getProperty("PGYER_API_KEY") ?: ""}\"")
        buildConfigField("String", "PGYER_APP_KEY", 
            "\"${localProperties.getProperty("PGYER_APP_KEY") ?: ""}\"")
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

### 4.2 local.properties 配置

```properties
PGYER_API_KEY=你的蒲公英API密钥
PGYER_APP_KEY=你的蒲公英App密钥
```

### 4.3 UI 调用示例

```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    // 触发检查更新
    Button(onClick = {
        viewModel.onEvent(SettingsEvent.CheckUpdate(
            apiKey = BuildConfig.PGYER_API_KEY,
            appKey = BuildConfig.PGYER_APP_KEY,
            version = BuildConfig.VERSION_NAME,
            build = BuildConfig.VERSION_CODE
        ))
    }) {
        Text("检查更新")
    }
    
    // 处理更新状态
    when (val status = uiState.updateStatus) {
        is UpdateStatus.Checking -> {
            // 显示加载状态（可选）
        }
        is UpdateStatus.Success -> {
            if (status.hasNewVersion) {
                // 显示更新对话框
                UpdateDialog(
                    currentVersion = BuildConfig.VERSION_NAME,
                    newVersion = status.version.orEmpty(),
                    description = status.desc,
                    onUpdate = {
                        viewModel.onEvent(SettingsEvent.ClearUpdateStatus)
                        status.downloadUrl?.let { uriHandler.openUri(it) }
                    },
                    onDismiss = {
                        viewModel.onEvent(SettingsEvent.ClearUpdateStatus)
                    }
                )
            } else {
                // 已是最新版本
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                    viewModel.onEvent(SettingsEvent.ClearUpdateStatus)
                }
            }
        }
        is UpdateStatus.Error -> {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "检查更新失败: ${status.message}", Toast.LENGTH_SHORT).show()
                viewModel.onEvent(SettingsEvent.ClearUpdateStatus)
            }
        }
        else -> {}
    }
}
```

### 4.4 更新对话框组件

```kotlin
@Composable
fun UpdateDialog(
    currentVersion: String,
    newVersion: String,
    description: String?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$currentVersion → $newVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("立即更新")
                }
                
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    }
}
```

---

## 五、依赖配置

### 5.1 Gradle 依赖 (libs.versions.toml)

```toml
[versions]
ktor = "2.3.12"  # 或更高版本
kotlinx-serialization = "1.6.0"
koin = "3.5.3"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
```

### 5.2 shared 模块 build.gradle.kts

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
```

---

## 六、蒲公英 API 说明

### 6.1 API 端点

```
POST https://api.pgyer.com/apiv2/app/check
```

### 6.2 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| _api_key | String | 是 | API Key，在蒲公英后台获取 |
| appKey | String | 是 | App Key，在蒲公英后台获取 |
| buildVersion | String | 否 | 当前应用版本号 |
| buildBuildVersion | String | 否 | 当前应用构建号 |

### 6.3 响应示例

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "buildHaveNewVersion": true,
        "downloadURL": "https://www.pgyer.com/xxx",
        "buildVersion": "1.2.0",
        "buildVersionNo": "10",
        "buildUpdateDescription": "1. 修复已知问题\n2. 优化性能"
    }
}
```

### 6.4 获取密钥

1. 登录 [蒲公英官网](https://www.pgyer.com/)
2. 进入「API」页面获取 `API Key`
3. 进入应用详情页获取 `App Key`

---

## 七、迁移检查清单

迁移到新项目时，请确保：

- [ ] 添加 Ktor 和 kotlinx-serialization 依赖
- [ ] 创建 `PgyerResponse` 和 `PgyerData` 数据类
- [ ] 创建 `AppUpdateService` 网络服务类
- [ ] 创建 `HttpClientFactory` 或复用现有网络客户端
- [ ] 创建 `UpdateStatus` 密封类
- [ ] 在 Repository 中添加 `checkAppUpdate` 方法
- [ ] 在 ViewModel 中添加更新检测逻辑
- [ ] 配置 Koin 依赖注入
- [ ] 在 `local.properties` 中配置蒲公英密钥
- [ ] 在 `build.gradle.kts` 中注入 BuildConfig 字段
- [ ] 实现 UI 层的更新对话框
- [ ] 添加相关字符串资源（多语言支持）

---

## 八、字符串资源参考

```xml
<!-- values/strings.xml -->
<string name="check_update">检查更新</string>
<string name="current_version">当前版本: %s</string>
<string name="update_new_version_title">发现新版本</string>
<string name="update_version_change">%s → %s</string>
<string name="update_upgrade_now">立即更新</string>
<string name="update_later">稍后再说</string>
<string name="update_already_latest">已是最新版本</string>
<string name="update_check_failed">检查更新失败: %s</string>
```

---

## 九、注意事项

1. **密钥安全**：不要将 `PGYER_API_KEY` 和 `PGYER_APP_KEY` 提交到版本控制
2. **网络权限**：确保 AndroidManifest.xml 中声明了 `INTERNET` 权限
3. **错误处理**：网络请求需要妥善处理异常情况
4. **用户体验**：检查更新时可以显示加载状态，避免用户困惑
5. **版本比较**：蒲公英 API 会自动比较版本，无需手动实现版本比较逻辑
