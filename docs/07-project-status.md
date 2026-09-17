# 项目现状、客户端与路线图

> 状态日期：2026-09-17。本文件是当前实现进度的统一入口；各阶段文档保留设计细节与验收标准。

## 1. 当前结论

Video Get 已形成两条可独立使用的本地下载链路，不需要云服务器：

1. Windows 桌面链路：Chrome/Edge 扩展连接本机 FastAPI 下载核心，由 Windows 桌面辅助程序负责安装、启动、令牌发现和 FFmpeg 管理。
2. Android 链路：原生 App 在手机内运行 yt-dlp/FFmpeg，并使用独立 Kotlin 解析器支持公开 Threads 视频。

阶段 1 至阶段 4 的主要开发目标已经完成。阶段 5 Android App 已整理为首个正式内部版本 `1.0.0`；核心下载流程、易用性优化、专用签名与既有平台真机验证均已完成，新增 Instagram 图片/混合媒体能力进入正式版本回归矩阵。

## 2. 各端软件

| 软件 | 目录 | 技术 | 当前状态 | 平台能力 |
|---|---|---|---|---|
| 本地下载 API | `services/downloader-api` | Python、FastAPI、yt-dlp、FFmpeg、SQLite | 已完成核心功能 | X、Instagram 可用；Threads 为计划状态 |
| Chrome/Edge 扩展 | `apps/browser-extension` | React、TypeScript、Vite、Manifest V3 | 已完成 0.1.0 开发版 | 通过本地 API 下载 X/Instagram；Threads 只显示计划状态 |
| Windows 桌面辅助程序 | `apps/desktop-companion` | Python、Tk、PyInstaller、Inno Setup | 已完成 0.1.0 开发版与真实安装回归 | 管理本地 API、令牌、托盘、开机启动、Native Messaging 和内置 FFmpeg |
| Android App | `apps/android` | Kotlin、Jetpack Compose、WorkManager、MediaStore、youtubedl-android | 1.0.0 首个正式内部版本 | YouTube、X、Instagram 图片/视频与公开 Threads；YouTube/Threads 属实验支持 |

### Windows 桌面链路

```text
Chrome / Edge 扩展
        │ 本机 HTTP + Bearer Token
        ▼
FastAPI 下载核心 ── yt-dlp ── X / Instagram
        │
        ├─ FFmpeg 合并与后处理
        └─ SQLite 任务与历史记录

Windows 桌面辅助程序
        ├─ 启停与单实例管理
        ├─ 托盘和状态窗口
        ├─ Native Messaging 自动配置扩展
        └─ 安装包内置 FFmpeg/ffprobe
```

当前边界：桌面 API 不下载 Threads；扩展识别 Threads 后展示计划状态。Instagram 仍需在可稳定直连源站的网络中完成更多真实链接回归。

### Android 链路

```text
分享菜单 / 手动粘贴
        ▼
URL 路由与媒体分析
        ├─ yt-dlp：X / Instagram
        ├─ FxTwitter：公开 X 媒体元数据与多视频分组
        └─ Kotlin Threads 解析器：公开 Threads
        ▼
WorkManager 前台下载 ── FFmpeg ── MediaStore
                                      └─ Movies/Video Get
```

已验证 X 与公开 Threads 视频下载。Instagram 已接入独立 WebView 登录会话、帖子媒体解析及图片/视频混合下载，当前等待真机单图、轮播、混合帖子和 Reel 验收。其他未完成项包括多任务历史、Room 持久化、纯动态 Threads `xmt` 页面稳定化、分离 DASH 音视频合并及完整发布验收。

## 3. 阶段进度

| 阶段 | 状态 | 已交付 | 下一步 |
|---|---|---|---|
| 1. 下载核心 | 已完成 | FastAPI、Provider、任务状态机、SQLite、FFmpeg、OpenAPI、令牌鉴权 | 持续跟随 yt-dlp 与平台变化 |
| 2. 浏览器扩展 | 已完成 | Chrome/Edge 共用 MV3 源码、分析、格式选择、下载、进度、取消、重试 | 发布打包与商店审核准备 |
| 3. Windows 辅助程序 | 已完成开发版 | PyInstaller EXE、托盘、Native Messaging、Inno Setup、内置 FFmpeg | 代码签名、升级机制、干净环境发布验收 |
| 4. X/Instagram 闭环 | 基本完成 | X/Instagram 规范化、格式令牌、多媒体选择、错误分类 | 扩大真实平台回归样本，重点验证 Instagram 网络环境 |
| 5. Android App | 1.0.0 正式内部版本 | 本地分析下载、通知、MediaStore、X 多视频、Threads、YouTube 单视频、Instagram 登录与图片/混合媒体、黑白灰 UI、猫咪图标、ABI 拆包、专用签名、错误重试、任务恢复 | 扩充正式版本真机回归矩阵，继续提升 Threads/DASH 稳定性 |

## 4. Android ABI 与包体积策略

日常构建默认只生成 `arm64-v8a`，后续常规更新也只维护该架构：

```powershell
cd apps/android
.\gradlew.bat assembleDebug
```

当前 Debug APK 实测约 71.42 MiB。需要重新生成兼容版本时使用：

```powershell
.\gradlew.bat assembleDebug "-PvideoGetAbis=arm64-v8a,armeabi-v7a,x86,x86_64"
```

2026-09-17 验证结果：

| ABI | Debug APK |
|---|---:|
| `arm64-v8a` | 71.42 MiB |
| `armeabi-v7a` | 65.06 MiB |
| `x86` | 70.09 MiB |
| `x86_64` | 74.28 MiB |

旧架构 APK 应作为发行物归档，不提交到源码仓库；只有出现明确设备需求时才更新。应用商店发布优先采用 AAB，由商店按设备 ABI 下发。

## 5. 当前目录结构

```text
video-get/
├─ apps/
│  ├─ android/                    # 原生 Android App、Gradle Wrapper、单元测试
│  │  ├─ app/src/main/java/com/videoget/app/
│  │  │  ├─ domain/              # URL 与领域模型
│  │  │  ├─ downloads/           # 分析器、WorkManager 下载、请求缓存
│  │  │  └─ ui/                  # Compose 首页与主题
│  │  └─ app/src/test/           # Android JVM 单元测试
│  ├─ browser-extension/          # Chrome/Edge Manifest V3 扩展
│  │  ├─ src/api/                # 本地 API 客户端
│  │  ├─ src/background/         # Service Worker
│  │  ├─ src/options/            # 服务连接与令牌设置
│  │  ├─ src/popup/              # 扩展主界面
│  │  └─ src/shared/             # 平台识别、存储与共享类型
│  └─ desktop-companion/          # Windows 桌面辅助程序
│     ├─ installer/               # Inno Setup 脚本
│     ├─ native-host/             # Chrome/Edge Native Messaging 清单
│     ├─ src/video_get_companion/ # 托盘、服务管理、Native Host
│     └─ tests/                   # 桌面程序测试
├─ services/
│  └─ downloader-api/             # FastAPI 下载核心
│     ├─ src/video_get/
│     │  ├─ api/                  # HTTP 路由、依赖与错误响应
│     │  ├─ domain/               # 模型、枚举与错误码
│     │  ├─ jobs/                 # 下载任务与状态机
│     │  ├─ media/                # FFmpeg 与文件命名
│     │  ├─ persistence/          # SQLite、迁移与仓储
│     │  ├─ providers/            # URL 路由与 yt-dlp Provider
│     │  └─ security/             # 日志脱敏
│     └─ tests/                   # API 与核心测试
├─ contracts/openapi/             # 固定的 API 契约
├─ design-system/video-get/       # 全局及各端 UI 设计规范
├─ docs/                          # 架构、阶段文档与当前状态
├─ scripts/                       # OpenAPI 导出等维护脚本
├─ pyproject.toml                 # Python 项目、依赖与质量配置
└─ README.md                      # 项目入口
```

`build`、`dist`、Gradle 缓存、Node.js 依赖及 APK/EXE 等生成物不属于源码目录，不应提交。

## 6. 最新验证基线

2026-09-17 本地验证：

- Python 下载核心与桌面程序：62 项测试通过，综合覆盖率 84.59%。
- 浏览器扩展：20 项测试通过，TypeScript 类型检查通过，Chrome/Edge 构建通过。
- Android：JVM 单元测试通过，`arm64-v8a` 默认构建和四架构独立 APK 构建通过。
- Android 真机：已完成 USB 安装测试，并验证 X 与至少一个嵌套媒体 Threads 视频下载。
- Android 内部 Release 0.1.0：专用 RSA 3072 位签名、SHA-256、全新安装、同签名覆盖安装、X/Threads 真机下载和崩溃日志检查通过；Instagram 回归 Reel 当前要求登录，本轮仅验证错误处理。

真实社交平台会持续变化；自动化通过不等于所有公开帖子始终可下载，发布前仍需执行授权样本的真实网络冒烟测试。

## 7. 建议后续顺序

1. 扩充 Android 0.2.0 的 Shorts、1080p 和受限内容固定授权回归样本。
2. 持续验证 QuickJS、YouTube JS Challenge、音视频合并及常见错误映射。
3. 完整历史和多任务队列继续后置，保持内部单任务界面简洁。
4. 完善 Threads `xmt` 动态页面和 DASH 分离音视频合并。
5. 真机验证 Instagram App 内登录、会话持久化、退出清除，以及单图、轮播、混合帖子和 Reel 下载。
6. 建立 X、Instagram、Threads、YouTube 的固定授权回归样本与内部发布检查表。
