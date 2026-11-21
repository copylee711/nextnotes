# 本机开发环境说明

## 构建要求

- JDK 17（`D:\dev\jdk\jdk-17.0.19`）
- Android SDK **实际路径是 `D:\Users\Alan\AppData\Local\Android\Sdk`**。`C:\Users\Alan\AppData\Local` 是重定向过去的，通过 C 盘路径新建的目录保存不下来，所以 `local.properties` 里必须写 D 盘路径：
  ```
  sdk.dir=D:/Users/Alan/AppData/Local/Android/Sdk
  ```
- NDK 27.0.12077973、CMake 3.22.1、Platform 36、Build-Tools 35.0.0（已安装）

## 国内网络

dl.google.com 很慢或者会断，Gradle 自动下载 SDK 组件可能卡上一个小时。

- **SDK 组件**：从腾讯镜像 `https://mirrors.cloud.tencent.com/AndroidSDK/<文件名>` 手动下载，文件名和 SHA-1 去 `https://dl.google.com/android/repository/repository2-3.xml` 查，校验通过后解压到 SDK 对应目录。
- **Maven 依赖**：构建时用 init 脚本把仓库换成阿里云镜像（脚本不放进仓库）：
  ```groovy
  // aliyun.init.gradle
  def mirrors = { h ->
      h.clear()
      h.maven { url 'https://maven.aliyun.com/repository/google' }
      h.maven { url 'https://maven.aliyun.com/repository/public' }
      h.maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
      h.google(); h.mavenCentral(); h.gradlePluginPortal()
  }
  settingsEvaluated { s ->
      mirrors(s.pluginManagement.repositories)
      mirrors(s.dependencyResolutionManagement.repositories)
  }
  ```
  ```
  gradlew.bat --init-script aliyun.init.gradle assembleDebug
  ```
- 本机代理 127.0.0.1:7892 能连通，但速度很慢，不推荐用。

## 安装与调试

- adb：`D:\Users\Alan\AppData\Local\Android\Sdk\platform-tools\adb.exe`（不在 PATH 中）
- 模拟器：AVD `test_avd`（Android 35）。用 `adb shell input stylus swipe ...` 模拟触控笔书写（手指默认用于平移）。
- debug 包名 `com.nextnotes.debug`，可以和正式版、原版 Xnotes 同时安装。
- 小米平板 5 Pro：通过 Wi-Fi 无线 adb，`adb pair <ip>:<配对端口>` 之后 `adb connect <ip>:<端口>`。

## 跟进上游

```
git fetch upstream
git merge upstream/master
```
