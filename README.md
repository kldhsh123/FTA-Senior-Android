# FTA-Senior-Android

**FuckingTheApp-Senior-Android**，简称 **FTA-Senior-Android**。通过 Shizuku 与无障碍服务，在屏幕上显示可自定义的悬浮按钮，一键强停当前应用或清理后台非系统应用。

首个公开版本：**v0.1.0** · Android 8.0+ · Kotlin · GPL-3.0

## 功能

- **强停当前应用**：切换到目标应用，点击悬浮按钮，按应用包执行系统强制停止。
- **清理后台**：查询运行中的应用，按保护规则逐个强停；显示成功、跳过、失败数量。
- **单／双按钮**：仅强停、仅清理，或同时显示两个功能；支持横向／纵向排列、交换顺序。
- **外观自定义**：圆形、圆角方形、胶囊形；40–80dp 尺寸；图标或图标加文字；背景与前景颜色；正常及闲置透明度，实时预览。
- **位置控制**：拖动、吸边、锁定位置，横竖屏分别记忆位置；锁屏时隐藏。
- **保护规则**：白名单搜索与批量选择，可选择保护前台服务、可感知进程和 VPN 应用。
- **操作反馈**：可选执行前确认、触感反馈；本地保存最近 50 条操作记录，支持查看跳过与失败原因。

清理不删除应用数据、不清缓存、不持续循环杀后台；不会显示未经测量的“释放内存”。强停可能造成未保存内容丢失，并中断通知、下载或其他后台任务，请将需要保留的应用加入白名单。

## 安装与使用

1. 安装并启动 [Shizuku](https://shizuku.rikka.app/zh-hans/download/)。非 root 设备可使用 ADB；Android 11+ 也可使用无线调试启动。
2. 从本仓库 GitHub **Releases** 下载已发布版本的 APK，或自行构建安装。
3. 打开 FTA，点击 **授权 / 重新连接 Shizuku**，允许授权，等待“Shizuku 已就绪”。
4. 点击 **开启无障碍服务**，阅读用途说明，在系统设置中开启 **FTA 悬浮控制**。
5. 回到 FTA，打开 **显示悬浮按钮**；在“按钮”页面设置功能和外观，在“规则”页面配置白名单。
6. 切换到其他应用，点击强停按钮；点击清理按钮可批量处理后台应用。

首次使用会显示权限与强停影响说明。关闭首页的“显示悬浮按钮”可隐藏按钮；在系统设置中关闭无障碍服务可停用服务。

### 保护范围

| 对象 | 行为 |
| --- | --- |
| 系统应用及更新后的系统应用、FTA、Shizuku | 始终跳过 |
| 桌面、当前输入法、已启用的无障碍服务、声明通知监听服务的应用 | 保护，避免影响关键功能 |
| 白名单 | 两个动作都跳过；保护扩展到共享 UID 的应用 |
| 当前可见应用 | 后台清理时跳过；分屏下保护可识别的可见应用 |
| 前台服务、用户可感知进程 | 默认保护，可关闭；基于系统进程重要性判断 |
| 声明 VPN 服务的应用 | 默认全部保护，可关闭，包括未连接的 VPN 应用 |

窗口状态无法可靠识别时会取消操作；清理期间会重新检查窗口、规则及进程状态。媒体保护采用保守的进程重要性判断，不能保证识别所有播放场景，重要应用建议加入白名单。

## 权限与隐私

| 权限／能力 | 用途 |
| --- | --- |
| Shizuku 授权 | 以 shell/root 身份查询系统应用状态并执行强停 |
| 无障碍服务 | 识别窗口所属应用、显示无障碍悬浮层；仅读取根节点的包名，不遍历页面文字 |
| 查询全部应用 | 展示白名单并识别运行应用及保护对象 |
| 震动 | 可选的触感反馈 |

不申请网络权限，不上传操作记录，不读取输入内容、不截图。设置与记录保存在本机。详见 [隐私说明](docs/privacy.md)。

## 环境与限制

- 最低 Android 8.0（API 26）；需要 Shizuku 12+，建议使用最新稳定版。
- 首版仅支持当前主用户，不支持工作资料、应用分身和跨用户清理。
- 悬浮按钮依赖无障碍服务；强停依赖 Shizuku。非 root 设备重启后通常需要重新启动 Shizuku。
- 使用 Shizuku 远程 Binder 调用及系统隐藏接口，不同 Android 版本和厂商系统的兼容性仍需实机验证。

## 从源码构建

使用支持当前 Android Gradle Plugin 的 Android Studio 打开仓库根目录，等待 Gradle 同步。工程当前配置：

| 工具 | 版本 |
| --- | --- |
| Android Gradle Plugin | 9.3.3（内置 Kotlin 支持） |
| Gradle Wrapper | 9.5.0 |
| Gradle Daemon JDK | 25（由 `gradle/gradle-daemon-jvm.properties` 指定） |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |

首次构建需要联网下载 Gradle、JDK、SDK 及 Maven 依赖。SDK 路径由 Android Studio 写入本机 `local.properties`，不要提交该文件。

**Windows CMD：**

```bat
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain > build-log.txt 2>&1
```

**Linux / macOS：**

```sh
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

Windows ADB 安装及启动（手机需开启 USB 调试并授权）：

```bat
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell am start -n com.fta.senior/.MainActivity
```

Debug APK 适合开发测试。GitHub 正式发布使用固定发布密钥签名的 Release APK，见 [发布步骤](docs/releasing.md)。

## 故障排查

- **已授权但连接失败**：记录首页具体异常。Windows 可运行 `scripts\collect-shizuku-logs.cmd`，按提示复现并采集日志；详见 [Shizuku 连接说明](docs/shizuku-connection.md)。
- **无障碍开关提示受限设置**：在系统应用信息中确认安装来源，允许受限设置后重试。
- **没有悬浮按钮**：检查无障碍服务是否连接、首页开关是否打开，以及设备是否锁屏。
- **不能强停某个应用**：先检查白名单与保护范围；通知栏、最近任务界面或无法识别目标时会取消执行。
- **签名不一致，无法覆盖安装**：发布密钥与本机 Debug 密钥不同。先确认是否需要保留设置，再卸载旧测试包并安装 Release 包；卸载会清除本地设置和记录。

反馈问题时提供 Android 版本、手机型号、FTA 版本、Shizuku 版本、复现步骤和具体报错。诊断日志可能含设备信息及应用包名，请先检查再公开。

## 代码结构

```text
app/src/main/java/com/fta/senior/
├── MainActivity.kt       # 首页、按钮、规则和记录页面
├── core/                 # 动作调度、应用目录与保护规则
├── data/                 # 设置、位置与操作记录存储
├── overlay/              # 无障碍服务、窗口识别与悬浮按钮
└── privileged/           # Shizuku 连接、系统 Binder 代理及执行接口
docs/                     # 隐私、连接诊断和发布说明
scripts/                  # Windows 日志采集工具
```

## 开源许可与依赖

Copyright © 2026 [kldhsh123](https://github.com/kldhsh123)。[项目源码](https://github.com/kldhsh123/FTA-Senior-Android) 按 GPLv3 开源分享

项目采用 [GNU GPL v3](LICENSE)。主要依赖：[Shizuku API](https://github.com/RikkaApps/Shizuku-API)、[AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)、AndroidX 和 Material Components；各依赖遵循其自身许可证。
