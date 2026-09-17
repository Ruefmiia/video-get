# Android 里程碑 5.5：YouTube 单视频下载

> 状态：第一版已实现。Debug 自动化、签名 Release 覆盖安装和 YouTube 公开视频真机下载通过，版本为 0.2.0。

## 1. 第一版范围

支持以下公开视频入口：

- `youtube.com/watch?v=<video-id>`
- `m.youtube.com/watch?v=<video-id>`
- `youtu.be/<video-id>`
- `youtube.com/shorts/<video-id>`
- 从 YouTube App 分享单个视频链接到 Video Get

链接统一规范化为 `https://www.youtube.com/watch?v=<video-id>`。分享跟踪参数和播放列表参数不会进入下载任务，每次只处理一个视频。

第一版不支持播放列表、频道批量下载、直播、首映、私密视频、会员内容、年龄验证、DRM、Cookie、Google 账号、字幕、封面或仅音频下载。

## 2. 实现方案

- 在 `PlatformId` 和 `UrlRouter` 增加严格的 YouTube 域名、路径及 11 位视频 ID 校验。
- 普通视频、短链接和 Shorts 复用现有设备内 yt-dlp/FFmpeg 流程，不增加云服务器或 Google API Key。
- `youtubedl-android 0.18.1` 已包含 QuickJS 2025-04-26，并自动为 yt-dlp 注入运行时路径。
- 当前 Android 包未内置独立 `yt-dlp-ejs` Python 包，因此 YouTube 请求仅从 yt-dlp 官方 GitHub 组件源加载 EJS 挑战脚本：`--remote-components ejs:github`。
- 首次分析 YouTube、以及距离上次成功检查超过 24 小时时，App 会在用户主动分析流程中通过 `youtubedl-android` 从 yt-dlp 官方 stable 渠道检查并更新解析器。更新不在后台静默执行；失败时停止本次分析并明确提示 GitHub 连接问题。

## 3. 格式策略

界面继续只提供三档：

1. 最佳兼容画质（MP4）
2. 最高 1080p
3. 最高 720p

选择器优先 H.264 MP4 视频和 M4A 音频，以提高 Android 播放兼容性；缺少兼容轨道时回退到 yt-dlp 可用的最佳轨道。高画质分离音视频由现有 FFmpeg 合并为 MP4。合并阶段显示“正在合并音视频”，不会提前显示下载完成。

## 4. 错误边界

UI 对以下情况提供简洁提示：私密视频、会员内容、登录或年龄验证、地区限制、直播、HTTP 403/PO Token、JavaScript 运行时不可用。第一版不读取账号 Cookie，也不绕过访问控制。

YouTube 会持续调整 JS Challenge 和 PO Token 要求。普通公开视频也可能因客户端、IP 或规则变化返回 403；这类失败应作为下载引擎兼容问题处理，而不是自动要求用户登录。下载异常只展示最后的 `ERROR`，不会再把版本警告或 SABR 警告误作主错误。

## 5. 验证结果与待办

已完成：

- 四类入口的 URL 规范化与拒绝用例测试。
- YouTube 三档格式选择器测试。
- 既有 Android JVM 测试通过。
- `arm64-v8a` Debug APK 构建通过。

已完成的真机验收：

- `https://youtu.be/I1guVkLJ4mU` 可分析并成功下载。
- 旧版 yt-dlp 的 SABR/格式失败已通过用户主动分析时检查官方 stable 更新修复。
- 0.2.0 签名 Release 可覆盖安装。

后续扩大回归范围时继续验证：

1. 720p 普通视频分析、下载和播放。
2. 1080p 分离音视频下载、合并和播放。
3. Shorts 从系统分享进入并下载。
4. 下载取消、后台恢复、打开视频和查看位置。
5. 私密、删除、登录要求、直播和 403 的错误提示。

第一版发布条件已经满足；Shorts、独立 1080p 样本和受限内容矩阵作为后续稳定性回归继续补充。
