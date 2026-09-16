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

## 目录结构

```text
video-get/
├─ apps/
│  ├─ browser-extension/
│  ├─ desktop-companion/
│  └─ android/
├─ services/
│  └─ downloader-api/
├─ contracts/
│  └─ openapi/
├─ tests/
│  └─ platform-fixtures/
└─ docs/
```

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

阶段 1 至阶段 4 已完成，阶段 5 Android App 已完成本地下载主流程，当前进入稳定性和发布准备。项目包含 FastAPI 下载服务、可构建 Chrome/Edge 的 Manifest V3 扩展、带内置 FFmpeg 的 Windows 桌面托盘程序和 Inno Setup 安装包。Android 端可接收分享或手动粘贴链接，使用设备内 yt-dlp/FFmpeg 下载 X 与 Instagram，并通过原生 Kotlin 解析器下载公开 Threads 视频。私密、删除或登录后可见的 Threads 内容不受支持。

### Android 开发

需要 JDK 17 或更高版本、Android SDK Platform 36、Android SDK Build-Tools，以及接受相应 SDK 许可。可使用 Android 命令行工具安装，无需 Android Studio；然后在 `apps/android` 中执行：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

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
