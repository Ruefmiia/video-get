# Video Get Android

阶段 5 的原生 Android 客户端。里程碑 5.2 已接入设备本地 yt-dlp/FFmpeg 下载链路。

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

## 当前范围

- 支持粘贴或通过系统分享接收一个 X/Instagram 链接。
- 在设备本地分析标题并提供最佳、1080p、720p 三档选择。
- 使用 WorkManager 前台任务下载、合并音视频、显示进度并支持取消。
- Android 10+ 保存到系统媒体库 `Movies/Video Get`。
- X 的 yt-dlp 解析失败时，可使用 FxTwitter 获取公开帖子媒体直链；实际视频仍直接从 `video.twimg.com` 下载。
- Threads 链接仅识别并显示计划状态。
- 不读取 Cookie，不绕过登录、DRM 或访问控制。
- 不需要云服务器；后续下载引擎也计划在设备本地执行。

当前使用 `youtubedl-android 0.18.1` 的 library 与 ffmpeg 模块。分发 APK 前必须遵循 GPL-3.0，并随发行物提供相应源码和许可证信息，详见 `THIRD_PARTY_NOTICES.md`。

## X 回退解析与隐私

默认先使用设备内 yt-dlp。仅当公开 X 帖子解析失败时，App 才会请求 `api.fxtwitter.com`，请求内容包含公开帖子路径（用户名和帖子 ID）。FxTwitter 只用于获取媒体元数据；视频字节由手机直接从 X 的 `video.twimg.com` 下载，不经过 Video Get 服务器。此回退依赖第三方服务的可用性与隐私政策。
