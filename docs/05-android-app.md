# 阶段 5：Android App

## 当前实现（里程碑 5.2）

已完成：

- Kotlin + Jetpack Compose 单模块工程和 Gradle Wrapper。
- 手动输入、由用户主动触发的剪贴板粘贴、系统 `ACTION_SEND` 文本分享入口。
- X、Instagram、Threads URL 严格识别、规范化与分享跟踪参数清理。
- Threads 使用原生 Kotlin 页面解析器，不进入通用 yt-dlp 下载器。
- 浅色/深色主题、边到边安全区、48dp 触控目标、文本与颜色共同表达状态。
- URL 路由单元测试。
- 设备内 yt-dlp 媒体分析和 FFmpeg 音视频合并。
- 最佳画质、最高 1080p、最高 720p 三档格式选择。
- WorkManager 网络约束、前台下载通知、进度、取消与失败恢复入口。
- Android 10+ 通过 MediaStore 保存到 `Movies/Video Get`。
- X 解析失败时通过 FxTwitter 获取公开媒体直链，并从 `video.twimg.com` 直接下载。

尚未完成：

- 多任务下载队列、显式重试策略与 Room 历史记录。
- Android 9 的公共媒体库兼容写入；当前回退到 App 专属 Movies 目录。
- 真机端到端、包体积、ABI、许可证和发布构建验收。

当前构建基线为 AGP 9.4.0、Gradle 9.6.0、Compose BOM 2025.12.00、`compileSdk 36`、`targetSdk 36`、`minSdk 26`。Compose 1.12 起要求 `compileSdk 37`，因此 API 36 构建暂时固定使用 Compose 1.10 系列。AGP 9 默认使用内置 Kotlin，因此工程不再应用旧的 `org.jetbrains.kotlin.android` 插件。

## 目标

开发本地运行的 Android App，支持从系统分享菜单或手动粘贴 X、Instagram 链接，完成分析、格式选择、下载、后处理和文件保存。第一版不依赖云服务器。

## 技术选型

- Kotlin
- Jetpack Compose
- Android SDK：按发布时 Google Play 最新要求设置；当前计划以 API 36 为 target
- minSdk：根据 yt-dlp/FFmpeg 依赖实测决定，建议先评估 API 26+
- ViewModel + Coroutines/Flow
- Room
- WorkManager；长时间、用户可感知的下载结合前台通知
- MediaStore 或 Storage Access Framework
- Android yt-dlp wrapper + Android FFmpeg 构建

在确认依赖前必须完成许可证审查。现有 `youtubedl-android` 使用 GPL-3.0；若直接链接和分发，可能影响 App 源码发布方式。商业闭源目标确定前，不应锁定该依赖。

## 建议目录

```text
apps/android/
├─ app/src/main/java/.../
│  ├─ ui/
│  ├─ navigation/
│  ├─ domain/
│  ├─ providers/
│  ├─ downloads/
│  ├─ persistence/
│  ├─ storage/
│  └─ notifications/
├─ app/src/test/
└─ app/src/androidTest/
```

## 共享与不共享的部分

与桌面端共享语义：

- `PlatformId`
- `MediaInfo`、`MediaAsset`、`MediaFormat`
- 任务状态机
- 错误码
- URL 规范化测试向量
- Provider 能力声明

不直接共享实现：

- FastAPI 路由
- SQLAlchemy 实体
- Python yt-dlp 调用代码
- Windows 路径与进程管理

可通过 OpenAPI 生成 Kotlin DTO 作为对照，但本地 Android 引擎应使用自己的领域模型，不必为了“复用”而在手机内启动 Web 服务。

## 用户流程

### 分享菜单

1. 用户在 X 或 Instagram 选择“分享”。
2. 选择 Video Get。
3. App 提取分享文本中的唯一 URL。
4. 展示分析页，不能自动开始下载。
5. 用户选择媒体项和清晰度。
6. 启动下载并显示系统通知。
7. 完成后将文件写入用户可见媒体库。

### 手动粘贴

首页提供粘贴框；读取剪贴板必须由用户动作触发，不在后台持续监控。

## 下载执行

- 每个任务使用唯一工作目录。
- 网络下载和 FFmpeg 后处理不在主线程执行。
- 用户离开界面后，下载通过受支持的后台机制继续。
- 进行长时间、用户可感知的下载时显示前台通知，提供取消动作。
- 网络变化时支持失败重试；默认采用指数退避。
- 完成后使用 MediaStore 写入 `Movies/Video Get`，或由用户通过 SAF 选择目录。
- 写入成功后再删除临时文件。

## 包体积与 ABI

yt-dlp、Python、FFmpeg 会显著增加包体积：

- 采用 Android App Bundle。
- 按 `arm64-v8a`、`armeabi-v7a`、`x86_64` 评估实际需求。
- 真机发布优先 `arm64-v8a`；模拟器测试保留 `x86_64`。
- 不把不需要的编码器、协议和字体打入 FFmpeg。
- 构建产物必须生成第三方组件与许可证清单。

## 更新策略

平台解析器变化频繁，但移动端不能依赖执行从服务器下载的任意代码：

- 第一版随 App 发布 yt-dlp 更新。
- 可从服务器获取非执行性质的兼容性公告、最低版本和平台开关。
- 不远程下发 Python/JavaScript 代码规避应用商店审核。
- 某平台失效时可通过远程配置临时关闭入口，并提示更新 App。

## Threads 公开视频支持

Android 当前规则：

- URL 识别器支持 `threads.com`、`threads.net`、`/t/` 和 `/share/` 路径。
- 使用链接预览爬虫 User-Agent 获取公开页面内嵌 JSON。
- 严格匹配请求的 shortcode，不回退到回复或推荐帖的视频。
- 支持单视频与多视频轮播中的 MP4 直链。
- 不将 Threads URL 交给通用 yt-dlp extractor。
- 不支持私密、删除或需要登录的帖子，也不读取 Cookie。
- Room 的 `platform` 字段使用字符串。
- UI 支持未来一个帖子多个 `MediaAsset`，不假设每个 URL 只有一个视频。

## 隐私与权限

- 仅申请联网、通知及实际需要的媒体写入能力。
- 优先使用 MediaStore/SAF，避免申请广泛文件访问权限。
- 不请求通讯录、位置、设备标识等无关权限。
- 第一版不读取其他 App Cookie，也不要求用户提交账号密码。
- 崩溃日志和分析数据默认不包含下载 URL；启用遥测前应取得明确同意。

## 完成标准

- 手动粘贴和系统分享两条流程可用。
- X、Instagram 与桌面端使用相同测试场景。
- 下载在切到后台后按 Android 规则继续，并有明确通知。
- 取消、重试、空间不足和网络中断处理正确。
- 文件出现在系统媒体库，文件名和元数据正确。
- 不需要云服务器即可完成全流程。
- 公开 Threads 单视频与轮播视频可分析和下载，错误帖子不会回退到页面中的其他媒体。
- 发布构建通过许可证、权限、包体积和目标 API 检查。
