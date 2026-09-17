# Video Get

面向 Chrome/Edge 扩展和 Android App 的本地优先视频获取工具。

第一阶段使用 `FastAPI + yt-dlp + FFmpeg + SQLite` 构建桌面下载核心；浏览器扩展通过本机 API 使用该核心。Android 版本采用 Kotlin 原生界面，并在设备内运行 yt-dlp/FFmpeg。

阶段 1 下载核心、阶段 2 Chrome/Edge 扩展、阶段 3 Windows 桌面辅助程序和阶段 4 X/Instagram 端到端强化已经实现。阶段 5 Android App 已具备 Kotlin/Jetpack Compose 界面、手动粘贴、系统分享、设备内下载，以及公开 Threads 视频解析能力。

## 文档导航

- [总体架构与约定](docs/00-architecture.md)
- [阶段 1：FastAPI 下载核心与 Provider 接口](docs/01-core-api.md)
- [阶段 2：Chrome/Edge 浏览器扩展](docs/02-browser-extension.md)
- [阶段 3：Windows 桌面辅助程序](docs/03-desktop-companion.md)
- [阶段 4：X 与 Instagram 端到端支持](docs/04-x-instagram-e2e.md)
- [阶段 5：Android App](docs/05-android-app.md)
- [测试、发布与验收策略](docs/06-testing-and-release.md)
- [项目现状、各端软件、目录结构与路线图](docs/07-project-status.md)
- [Android 里程碑 5.4：易用性优化方案](docs/08-android-usability-plan.md)

## 目录结构

```text
video-get/
├─ apps/
│  ├─ android/                    # Android 原生客户端
│  ├─ browser-extension/          # Chrome/Edge 扩展
│  └─ desktop-companion/          # Windows 桌面辅助程序与安装器
├─ services/downloader-api/       # FastAPI 下载核心
├─ contracts/openapi/             # API 契约
├─ design-system/video-get/       # 各端 UI 设计规范
├─ docs/                          # 架构、阶段与状态文档
├─ scripts/                       # 维护脚本
└─ pyproject.toml                 # Python 项目与质量配置
```

详细模块树、各端职责和生成物边界见[项目现状文档](docs/07-project-status.md)。

## 第一版范围

包含：

- X 与 Instagram 的公开帖子/视频链接
- 链接分析、格式选择、下载进度、取消、失败重试和历史记录
- Chrome/Edge 共用一套 Manifest V3 扩展代码
- Windows 本地辅助程序
- Android 本地下载能力

不包含：

- Threads 私密、登录后可见内容或账号批量抓取
- DRM、付费墙或访问控制绕过
- 云端视频代理、用户系统、会员系统和跨设备同步
- 私密内容或批量账号抓取

## 产品边界

本工具仅用于保存用户自己拥有、已获授权、公共领域或开放许可的内容。所有客户端和服务端都不得实现 DRM、付费墙、验证码或其他访问控制绕过。

## 当前开发状态

阶段 1 至阶段 3 已完成开发版；阶段 4 的 X/Instagram 桌面闭环基本完成；阶段 5 Android App 已完成本地下载主流程、内部 Release 0.1.0 验证和 5.4 易用性优化，并通过签名 Release 覆盖安装与真机测试。项目包含 FastAPI 下载服务、Chrome/Edge Manifest V3 扩展、带内置 FFmpeg 的 Windows 桌面托盘程序和 Inno Setup 安装包。Android 端可接收分享或手动粘贴链接，使用设备内 yt-dlp/FFmpeg 下载 X 与 Instagram，并通过原生 Kotlin 解析器下载公开 Threads 视频。桌面链路暂不下载 Threads；私密、删除或登录后可见的 Threads 内容不受支持。

| 软件 | 当前状态 | X | Instagram | Threads |
|---|---|---:|---:|---:|
| FastAPI 下载核心 | 核心完成 | 支持 | 支持 | 计划中 |
| Chrome/Edge 扩展 | 0.1.0 开发版完成 | 通过本地 API | 通过本地 API | 仅识别和提示 |
| Windows 桌面辅助程序 | 0.1.0 安装回归通过 | 管理桌面链路 | 管理桌面链路 | 暂不支持 |
| Android App | 内部 0.1.0 与 5.4 易用性优化已通过真机验证 | 支持 | 支持 | 实验支持公开内容 |

更完整的阶段进度、验证结果和待办事项见[项目现状文档](docs/07-project-status.md)。

### Android 开发

需要 JDK 17 或更高版本、Android SDK Platform 36、Android SDK Build-Tools，以及接受相应 SDK 许可。可使用 Android 命令行工具安装，无需 Android Studio；然后在 `apps/android` 中执行：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Android 默认只生成 `arm64-v8a` 分架构 APK，作为日常更新版本。需要生成其余兼容架构时执行：

```powershell
.\gradlew.bat assembleDebug "-PvideoGetAbis=arm64-v8a,armeabi-v7a,x86,x86_64"
```

详细产物路径和架构维护策略见 `apps/android/README.md`。

Android 第一版采用设备本地处理，不要求部署云服务器。当前工程已接入 `youtubedl-android 0.18.1` 的 yt-dlp 与 FFmpeg 模块；公开分发前必须完成 GPL-3.0、传递依赖、对应源码和应用发布方式评审。

### 本地运行

```powershell
python -m pip install -e ".[dev]"
$env:PYTHONPATH = "services/downloader-api/src"
python -m video_get
```

服务默认监听 `http://127.0.0.1:17382`。首次启动会在 `.video-get/api-token` 生成本地 API 令牌；除 `/health` 外，请求需携带：

```text
Authorization: Bearer <local-token>
```

### 验证

```powershell
python -m ruff check .
python -m pytest
$env:PYTHONPATH = "services/downloader-api/src"
python scripts/export_openapi.py
```

### 构建浏览器扩展

```powershell
cd apps/browser-extension
npm install
npm run typecheck
npm test
npm run build
```

构建结果分别位于 `apps/browser-extension/dist/chrome` 和 `apps/browser-extension/dist/edge`。在 `chrome://extensions` 或 `edge://extensions` 开启开发者模式后，使用“加载已解压的扩展程序”选择对应目录。第一次使用需打开扩展设置，填写本地 API 地址和 `.video-get/api-token` 中的令牌。
