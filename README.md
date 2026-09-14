# Video Get

面向 Chrome/Edge 扩展和 Android App 的本地优先视频获取工具。

第一阶段使用 `FastAPI + yt-dlp + FFmpeg + SQLite` 构建桌面下载核心；浏览器扩展通过本机 API 使用该核心。Android 版本采用 Kotlin 原生界面，并在设备内运行 yt-dlp/FFmpeg。

当前文档只规划开发顺序 1–5，不包含功能实现。Threads 已保留平台标识、URL 路由、能力声明和 Provider 扩展点，但暂不开发解析与下载功能。

## 文档导航

- [总体架构与约定](docs/00-architecture.md)
- [阶段 1：FastAPI 下载核心与 Provider 接口](docs/01-core-api.md)
- [阶段 2：Chrome/Edge 浏览器扩展](docs/02-browser-extension.md)
- [阶段 3：Windows 桌面辅助程序](docs/03-desktop-companion.md)
- [阶段 4：X 与 Instagram 端到端支持](docs/04-x-instagram-e2e.md)
- [阶段 5：Android App](docs/05-android-app.md)
- [测试、发布与验收策略](docs/06-testing-and-release.md)

## 计划中的目录结构

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

- Threads 实际解析与下载
- DRM、付费墙或访问控制绕过
- 云端视频代理、用户系统、会员系统和跨设备同步
- 私密内容或批量账号抓取

## 产品边界

本工具仅用于保存用户自己拥有、已获授权、公共领域或开放许可的内容。所有客户端和服务端都不得实现 DRM、付费墙、验证码或其他访问控制绕过。

## 当前开发状态

阶段 1 已开始，当前包含 FastAPI 服务骨架、平台 URL 路由、yt-dlp Provider、后台下载任务、SQLite 历史记录、本地令牌认证及 Threads 占位契约。

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
