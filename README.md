# Video Get

Video Get 是一个本地优先的社交媒体内容获取工具，提供 Android App，以及由 Chrome/Edge 扩展、FastAPI 下载核心和 Windows 桌面辅助程序组成的桌面链路。

项目用于演示 URL 路由、媒体解析、后台任务、FFmpeg 后处理、浏览器扩展、Android 原生开发和本地服务集成。Android 版本在手机内完成分析与下载；桌面版本通过本机服务工作。项目目前不依赖云服务器。

> 当前正式版本：Android `1.0.0`，默认发行 `arm64-v8a` APK。

## 当前能力

| 客户端 | 状态 | YouTube | X | Instagram | Threads |
|---|---|---|---|---|---|
| Android App | `1.0.0` 正式内部版本 | 普通视频、短链接、Shorts | 单视频、多视频帖子 | Reel、单图、轮播、图文/视频混合帖子 | 公开帖子、分享链接、轮播、嵌套媒体 |
| Chrome/Edge 扩展 | `0.1.0` 开发版 | 暂不支持 | 通过本地 API | 通过本地 API | 仅识别并提示 |
| Windows 桌面辅助程序 | `0.1.0` 开发版 | 暂不支持 | 管理桌面下载链路 | 管理桌面下载链路 | 暂不支持 |
| FastAPI 下载核心 | 核心功能完成 | 暂不支持 | 支持 | 支持 | 计划中 |

真实平台会持续调整页面、接口和风控策略，因此“支持”不代表所有链接在所有网络环境下始终可用。

## Android 1.0.0

Android App 使用 Kotlin、Jetpack Compose、WorkManager、MediaStore、yt-dlp 和 FFmpeg，在设备本地完成媒体分析、下载、合并与保存。

主要功能：

- 手动粘贴链接，或从其他 App 通过系统分享发送到 Video Get。
- 自动识别 YouTube、X、Instagram 和 Threads 链接。
- 提供最佳画质、最高 1080p、最高 720p 等可用选项。
- 前台下载通知、进度显示、取消、重试和进行中任务恢复。
- X 多视频帖子按原顺序批量下载，单项失败不阻止其余媒体保存。
- Instagram 支持 App 内官方网页登录；登录成功后自动返回 Video Get。
- Instagram 支持 Reel、单图、多图轮播和图片/视频混合帖子。
- Threads 使用独立 Kotlin 解析器处理公开帖子、分享链接、轮播和嵌套媒体。
- 图片保存到 `Pictures/Video Get`，视频保存到 `Movies/Video Get`。
- 下载完成后可直接打开媒体或查看保存目录。
- APK 按 CPU 架构拆分，日常版本默认仅维护 `arm64-v8a`。

### 安装条件

- Android 8.0（API 26）或更高版本。
- `arm64-v8a` CPU 架构；绝大多数近年的 Android 手机都符合。
- 允许接收 APK 的文件管理器或社交软件“安装未知应用”。
- 手机能够正常访问目标平台及其媒体 CDN。

安装包名称：

```text
video-get-1.0.0-arm64-v8a.apk
```

使用 ADB 覆盖安装：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r `
  ".\release-output\1.0.0\video-get-1.0.0-arm64-v8a.apk"
```

使用同一签名覆盖安装时，已有 App 数据和登录会话通常会保留。若后续版本更换签名密钥，将无法直接覆盖旧版本。

### Instagram 登录与隐私

- 登录页面由 App 内 WebView 打开 Instagram 官方网站。
- 用户名、密码和双重验证码直接提交给 Instagram，Video Get 不读取账号密码。
- App 仅使用 WebView 保存的当前网站会话执行用户主动发起的分析和下载。
- yt-dlp 所需 Cookie 文件只在 App 私有缓存中临时生成，单次调用结束后删除。
- 不读取 Chrome 或 Instagram App 的 Cookie，不上传账号凭据，也不提供共享账号功能。
- 用户可点击“Instagram 已连接”退出并清除本地会话。
- Instagram 可能要求重新登录、验证码或安全检查，也可能限制第三方工具使用。

### 构建 Android App

需要 JDK 17+、Android SDK Platform 36 和对应 Build-Tools，无需安装完整 Android Studio。

```powershell
cd apps/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

默认输出：

```text
apps/android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

构建四种 ABI：

```powershell
.\gradlew.bat assembleDebug "-PvideoGetAbis=arm64-v8a,armeabi-v7a,x86,x86_64"
```

生成专用签名 Release：

```powershell
.\build-internal-release.ps1
```

签名脚本会运行单元测试、构建 `arm64-v8a` Release、验证 APK 签名，并生成 APK、SHA-256 和第三方声明。JKS、密码及 `release-output` 不应提交到 Git。

## Windows 桌面链路

```text
Chrome / Edge 扩展
        │ 本机 HTTP + Bearer Token
        ▼
FastAPI 下载核心 ── yt-dlp ── X / Instagram
        │
        ├─ FFmpeg 合并与后处理
        └─ SQLite 任务与历史记录

Windows 桌面辅助程序
        ├─ 服务启停和单实例管理
        ├─ 托盘与状态窗口
        ├─ Native Messaging 配置
        └─ 安装包内置 FFmpeg/ffprobe
```

本地运行下载核心：

```powershell
python -m pip install -e ".[dev]"
$env:PYTHONPATH = "services/downloader-api/src"
python -m video_get
```

服务默认监听 `http://127.0.0.1:17382`，首次启动会在 `.video-get/api-token` 创建本地访问令牌。除 `/health` 外，请求需要 Bearer Token。

构建浏览器扩展：

```powershell
cd apps/browser-extension
npm install
npm run typecheck
npm test
npm run build
```

构建结果位于 `apps/browser-extension/dist/chrome` 和 `apps/browser-extension/dist/edge`。

## 项目结构

```text
video-get/
├─ apps/
│  ├─ android/                    # Android App、测试和发布脚本
│  ├─ browser-extension/          # Chrome/Edge Manifest V3 扩展
│  └─ desktop-companion/          # Windows 辅助程序和安装器
├─ services/downloader-api/       # FastAPI、yt-dlp、FFmpeg、SQLite
├─ contracts/openapi/             # API 契约
├─ design-system/video-get/       # UI 设计规范
├─ docs/                          # 架构、阶段、测试与状态文档
├─ scripts/                       # 维护脚本
├─ DISCLAIMER.md                  # 完整免责声明
└─ pyproject.toml                 # Python 依赖和质量配置
```

## 已知限制

- 不支持 DRM、付费墙、验证码或其他访问控制绕过。
- 不支持用户无权访问的私密、会员、已删除或地区受限内容。
- YouTube 播放列表、直播、会员内容、年龄验证和仅音频模式暂不支持。
- Threads 纯动态页面、平台新增媒体结构和分离 DASH 轨道可能解析失败。
- Instagram 登录会话可能因改密、异地登录、平台风控或接口变化而失效。
- 下载平台结构发生变化时，通常需要升级 App 或其内置解析组件。
- Android 当前没有完整下载历史、多任务队列和 Room 持久化。
- iOS 客户端尚未开发。

## 文档

- [总体架构与约定](docs/00-architecture.md)
- [FastAPI 下载核心](docs/01-core-api.md)
- [Chrome/Edge 扩展](docs/02-browser-extension.md)
- [Windows 桌面辅助程序](docs/03-desktop-companion.md)
- [X 与 Instagram 桌面闭环](docs/04-x-instagram-e2e.md)
- [Android App](docs/05-android-app.md)
- [测试、发布与验收](docs/06-testing-and-release.md)
- [项目现状与路线图](docs/07-project-status.md)
- [Android 易用性优化](docs/08-android-usability-plan.md)
- [Android YouTube 方案](docs/09-android-youtube-plan.md)
- [Android Instagram 登录方案](docs/10-android-instagram-login-plan.md)
- [Android 1.0.0 发布说明](apps/android/release-notes/1.0.0.md)

## 免责声明

### 项目用途

Video Get 是仅供技术交流、软件开发学习和个人研究使用的开源项目。本项目不鼓励、不支持，也不应用于侵犯著作权、规避付费或访问控制、未经授权传播内容、批量抓取账号数据，或其他违反法律法规及第三方平台规则的行为。

### 使用者责任

使用者应自行确认其对目标内容具有合法的访问、下载、复制、保存和使用权限，例如：

- 使用者本人创作或拥有的内容；
- 已取得著作权人或其他权利人的明确授权；
- 属于公共领域的内容；
- 依据开放许可允许下载和使用的内容；
- 适用法律法规明确允许的其他情形。

使用者应自行遵守所在国家或地区的法律法规、内容许可条件，以及 YouTube、X、Instagram、Threads 等相关平台的服务条款。因使用本项目产生的账号限制、内容纠纷、版权投诉、数据损失、设备故障或其他后果，应根据适用法律由相应责任方承担。

### 禁止用途

不得使用本项目：

- 下载、复制或传播无权使用的受版权保护内容；
- 绕过 DRM、付费墙、验证码、登录权限或其他技术保护措施；
- 获取私密、已删除、仅限特定成员或其他无权访问的内容；
- 实施商业盗版、恶意爬取、骚扰、监控或侵犯个人隐私的行为；
- 从事任何违反适用法律法规或第三方平台规则的活动。

### 账号、隐私与第三方平台

部分功能可能由使用者主动登录第三方平台。使用自动化解析或下载工具仍可能触发安全验证、会话失效、功能限制或账号处置。请使用自己的账号并自行评估相关风险，不得共享登录 Cookie、会话文件、密钥或其他敏感凭据。

本项目与 YouTube、X、Instagram、Threads、Meta、Google 或其他第三方平台不存在隶属、授权、认可或合作关系；相关名称和商标归各自权利人所有。第三方网页、接口、媒体地址和访问规则可能随时变化，本项目不保证任何平台、链接、格式或功能持续可用。

### 无担保与责任限制

本项目按“现状”提供，不作任何明示或默示的担保。在适用法律允许的最大范围内，项目贡献者不对因安装、运行、修改、分发或使用本项目而产生的直接或间接损失承担超出适用法律规定范围的责任。

本声明不能排除或限制依法不得排除或限制的责任，也不能替代针对具体情况的专业法律意见。如对内容权利、平台规则或软件分发义务存在疑问，应咨询具备相应资质的专业人士。

### 开源组件与分发

本项目包含 yt-dlp、FFmpeg、youtubedl-android 等第三方开源组件。复制或分发源码、APK、安装包及其修改版本时，必须同时遵守相关许可证、版权声明和源代码提供义务。Android 分发信息见 [`apps/android/THIRD_PARTY_NOTICES.md`](apps/android/THIRD_PARTY_NOTICES.md)。

下载、安装、运行、修改或分发本项目，即表示使用者已经阅读并知悉上述用途边界和风险。如不同意，请勿使用或分发本项目。更完整的独立版本见 [DISCLAIMER.md](DISCLAIMER.md)。
