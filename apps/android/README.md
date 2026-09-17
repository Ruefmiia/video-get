# Video Get Android

阶段 5 的原生 Android 客户端。当前版本为首个正式内部版本 `1.0.0`，包含 YouTube 单视频、X 多视频、Instagram App 内登录与图片/视频媒体，以及公开 Threads 下载。详见[YouTube 开发方案](../../docs/09-android-youtube-plan.md)和 [Instagram App 内登录方案](../../docs/10-android-instagram-login-plan.md)。

## 环境

- JDK 17+（本机已用 JDK 21 验证 Gradle）
- Android SDK Platform 36
- Android SDK Build-Tools
- 推荐使用最新版 Android Studio

Android Studio 安装 SDK 后会生成本机专用的 `local.properties`，该文件已被 Git 忽略，不应提交。

## 验证

```powershell
cd apps/android
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

默认构建只生成现代 Android 真机使用的 `arm64-v8a` APK：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

日常功能更新仅需构建并发布该版本。需要重新生成其他 CPU 架构的兼容版本时，显式传入 ABI 列表：

```powershell
.\gradlew.bat assembleDebug "-PvideoGetAbis=arm64-v8a,armeabi-v7a,x86,x86_64"
```

该命令分别生成四个 APK，不生成包含全部原生库的通用 APK。`armeabi-v7a` 用于较老的 32 位 ARM 手机，`x86` 和 `x86_64` 主要用于模拟器或少量特殊设备；这些兼容版本可固定保留，只有出现明确需求时才随主版本更新。

## 内部 Release 构建

首次发布前，在仓库外创建并备份专用 JKS。随后运行：

```powershell
.\build-internal-release.ps1
```

脚本会安全提示输入密码、运行单元测试、构建默认 `arm64-v8a` Release APK、使用 Android SDK 的 `apksigner` 验证签名，并将 APK、SHA-256 与第三方声明整理到 `release-output/<版本>/`。密码只在当前脚本进程中传给 Gradle，脚本结束时清除；JKS、密码和 `release-output` 均不得提交到 Git。

## 当前范围

- 支持粘贴或通过系统分享接收一个 YouTube、X、Instagram 或 Threads 链接。
- 在设备本地分析标题并提供最佳、1080p、720p 三档选择。
- 使用 WorkManager 前台任务下载、合并音视频、显示进度并支持取消。
- 粘贴链接后自动分析，支持键盘分析、整行选择清晰度和分阶段失败重试。
- 下载完成后可直接打开视频、查看保存位置或再次下载；App 重建后可恢复当前下载任务。
- Android 10+ 保存到系统媒体库 `Movies/Video Get`。
- X 使用 FxTwitter 获取公开帖子媒体元数据，以识别并顺序下载多视频帖；FxTwitter 不可用时回退到设备内 yt-dlp。实际视频仍直接从 `video.twimg.com` 下载。
- 支持公开 Threads 单视频和多视频帖，含 `threads.com`、`threads.net`、`/t/` 与分享链接。
- 支持 YouTube 普通视频、`youtu.be` 短链接和 Shorts；不支持播放列表、直播或登录内容。
- Instagram 可在 App 内打开官方登录页建立独立会话；分析和后台下载均使用当前会话，临时 Cookie 文件在调用后删除。
- Instagram 支持单图、轮播多图及图文/视频混合帖子；图片保存到 `Pictures/Video Get`，视频保存到 `Movies/Video Get`。
- Threads 解析严格匹配目标 shortcode，不会选取页面中的回复或推荐视频。
- 不读取 Chrome 或 Instagram App 的 Cookie，不上传凭据，也不绕过 DRM 或访问控制。
- 不需要云服务器；下载、FFmpeg 后处理和 Threads 解析均在设备本地执行。
- 使用黑白猫咪简笔画自适应启动图标，兼容圆形和圆角方形桌面遮罩。

当前使用 `youtubedl-android 0.18.1` 的 library 与 ffmpeg 模块。分发 APK 前必须遵循 GPL-3.0，并随发行物提供相应源码和许可证信息，详见 `THIRD_PARTY_NOTICES.md`。

## X 媒体解析与隐私

分析公开 X 帖子时，App 会请求 `api.fxtwitter.com`，请求内容包含公开帖子路径（用户名和帖子 ID），以保留帖子中每个视频的边界和清晰度信息。FxTwitter 只用于获取媒体元数据；视频字节由手机直接从 X 的 `video.twimg.com` 下载，不经过 Video Get 或 FxTwitter 服务器。FxTwitter 不可用时回退到设备内 yt-dlp。此功能依赖第三方服务的可用性与隐私政策。

## Threads 解析边界

Threads 使用设备内 Kotlin 解析器，请求公开帖子页面并提取 Meta 提供的媒体数据。当前支持正式帖子 URL、`/t/`、`/share/`、单视频、轮播、嵌套/引用媒体和基础 DASH 视频地址；解析时严格匹配目标帖子，不回退到推荐流。新版 `xmt` 页面仅在静态响应包含可验证目标标识时可靠，纯 JavaScript 动态注入页面的 WebView 后备仍在稳定化。App 不提交 Threads 账号、密码或外部浏览器 Cookie，不支持私密、已删除及登录后可见帖子。
