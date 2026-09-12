# Desktop 打包后 Failed to launch JVM（DesktopStrings 构造参数超限）

## 问题描述
Compose Desktop 打成 MSI 并安装后，双击 `Live Wallpaper.exe` 弹出 **Failed to launch JVM**，窗口起不来。

容易误导的现象：
- 第一次打包（文案较少时）能正常打开。
- 之后加了壁纸库等 i18n 字段，再打包就失败。
- 重启电脑、改系统 `JAVA_HOME`、卸载重装，错误不变。

启动器对话框没有真实堆栈。从终端用 `UseShellExecute = false` 拉起安装目录里的 exe，才能看到：

```text
java.lang.ClassFormatError: Too many arguments in method signature
    in class file com/example/livewallpaper/desktop/DesktopStrings
```

堆栈落在 `DesktopStringsKt.<clinit>`，说明 **JVM 已经创建成功**，崩溃发生在加载文案类时。启动器只是把未捕获异常包装成 Failed to launch JVM。

## 原因分析

### 1. JVM 方法参数上限
JVM 规定单个方法（含构造函数）最多 **255** 个参数槽。引用类型和函数类型各占 1 槽。

`DesktopStrings` 曾是一个超大 `data class`，主构造函数承载全部 UI 文案。字段数涨到 **259** 后，kotlinc 仍能编过，但类加载时被 JVM 拒绝。

### 2. 为什么第一次打包没问题
当时字段还少于 255，类能加载，安装包可以启动。后来新增壁纸库、绘画备份等 key，构造函数越过上限，旧安装包和新包都会在启动瞬间失败。

这与系统 JDK、卸载重装、自带 `runtime` 是否完整无关。安装包里已经带着这份非法 class。

### 3. 容易误判的点
jpackage 启动器默认使用安装目录下的 `runtime`，**不靠**系统 `JAVA_HOME`。本机曾把 `JAVA_HOME` 写成字面量 `%JDK-21%`，后来改成 `D:\jdk21`。两种情况下，用控制过的环境变量启动，都会打出同一条 `ClassFormatError`。

因此：
- 改 `JAVA_HOME`、重启、重装，都修不好。
- 不要先把 Failed to launch JVM 当成“找不到 JRE”或“环境变量坏了”。

## 修复方案
不要用巨型 `data class` 主构造函数装文案。改成普通 class，构造函数只接收 `Properties`（和资源路径），字段在类体里初始化。对外 API 仍是 `strings.appTitle` 这种属性访问。

```kotlin
class DesktopStrings internal constructor(
    private val properties: Properties,
    private val resourcePath: String,
) {
    private fun text(key: String): String = checkNotNull(properties.getProperty(key)) {
        "Missing desktop i18n key '$key' in $resourcePath"
    }

    val appTitle: String = text("appTitle")
    val appVersion: (String) -> String = { text("appVersion").format(it) }
    // ...其余字段同样在类体初始化，不要放进主构造函数
}
```

校验：`javap` 里构造函数应只有两个参数，例如：

```text
public DesktopStrings(java.util.Properties, java.lang.String);
```

以后再加 i18n 字段，继续往类体加 `val xxx = text("xxx")`，不要退回巨型构造函数。

## 排查步骤（以后再遇到 Failed to launch JVM）
1. 不要先改系统 `JAVA_HOME` 或让用户重装 JDK。
2. 在终端里直接启动 exe（关闭 `UseShellExecute`，以便看到 stderr），或打一个带 `--win-console` 的调试包。
3. 若堆栈是 `ClassFormatError: Too many arguments`，先数对应 data class 的构造参数是否超过 255。
4. 确认安装包 / app image 里的 jar 已更新（`packageMsi` 有时不会刷新旧的 app image，必要时删 `desktopApp/build/compose/binaries` 再打）。

## 修改文件
- `desktopApp/src/desktopMain/kotlin/com/example/livewallpaper/desktop/DesktopStrings.kt`
  - `data class` 改为普通 class，字段在类体初始化
- `desktopApp/src/desktopMain/resources/app.properties`
  - 修复后的桌面版升到 `1.0.118`

## 验证
- `:desktopApp:desktopTest` 通过（含 `backupStringsLoadForEnglishAndChinese`，会加载 `DesktopStrings`）。
- 用 `JAVA_HOME=D:\jdk21` 启动 `1.0.118` app image，子进程能加载自带 `runtime\bin\server\jvm.dll`，主窗口正常出现。
