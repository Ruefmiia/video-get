# 总体架构与约定

## 1. 架构目标

第一版采用本地优先架构，不要求云服务器：

```text
Chrome / Edge Extension
          │ HTTP: 127.0.0.1
          ▼
Desktop Companion ── manages ── FastAPI Download Core
                                      │
                        ┌─────────────┼─────────────┐
                        ▼             ▼             ▼
                    Provider       FFmpeg        SQLite
                        │
                 yt-dlp Provider
                 ├─ X
                 ├─ Instagram
                 └─ other future providers

Android App
    ├─ shared API semantics
    ├─ local yt-dlp wrapper
    ├─ local FFmpeg
    ├─ WorkManager request cache
    └─ Room history database (planned)
```

浏览器端不能直接执行 Python、yt-dlp 或原生 FFmpeg，因此需要桌面辅助程序。Android 端不启动 FastAPI，而是复用相同的领域模型、错误码和任务状态语义。

## 2. 模块边界

### 下载核心

负责 URL 校验、平台识别、元数据分析、格式选择、下载任务、FFmpeg 后处理和历史记录。它不包含桌面托盘 UI，也不感知浏览器扩展。

### 浏览器扩展

只负责获取用户明确选择的 URL、调用本地 API、展示格式与进度，以及触发浏览器打开下载目录。扩展不解析站点内部协议。

### 桌面辅助程序

负责安装依赖、启动与停止本地 API、端口发现、健康检查、升级、日志入口和卸载。

### Android App

负责分享链接接收、移动端任务管理、前台下载通知和文件保存。平台识别与任务模型应与桌面端一致，但执行器是 Android 本地实现。当前任务参数保存在 App 私有缓存中；Room 历史数据库仍属于后续工作。

## 3. Provider 契约

所有平台实现统一契约：

```python
class Provider(Protocol):
    id: PlatformId

    def match(self, url: str) -> MatchResult: ...
    async def analyze(self, request: AnalyzeRequest) -> MediaInfo: ...
    async def download(
        self,
        request: DownloadRequest,
        progress: ProgressCallback,
        cancel: CancellationToken,
    ) -> DownloadResult: ...
```

Provider 必须做到：

- `match` 不访问网络，只做规范化和域名/路径识别。
- `analyze` 不写入最终文件。
- `download` 只能写入分配给任务的临时目录和目标目录。
- 错误必须转换为统一错误码，不向客户端暴露 Cookie、完整命令行或敏感响应体。
- 不接受 Provider 自己拼接的任意输出路径。

第一版实现 `YtDlpProvider`，并通过平台配置启用 X 与 Instagram。

## 4. Threads 平台边界

桌面 FastAPI/浏览器扩展链路当前仍将 Threads 保持为 `planned`；Android 客户端已通过独立 Kotlin 解析器实验支持公开 Threads 视频。两条链路共享平台标识和数据模型，但不共享解析实现。

桌面/API 侧继续保留以下约定：

```text
PlatformId: threads
Domains: threads.com, www.threads.com, threads.net, www.threads.net
Canonical paths:
  /@{username}/post/{shortcode}
  /share/{code}
Potential capabilities:
  multi_asset, authentication, cookies, video_variants
```

具体规则：

1. URL 路由器必须识别 Threads URL，不能把它误交给通用 yt-dlp fallback。
2. `GET /api/v1/platforms` 返回 Threads，状态为 `planned`，`available=false`。
3. 分析 Threads URL 时返回 HTTP `501` 和错误码 `PLATFORM_NOT_IMPLEMENTED`。
4. `MediaInfo.assets` 从一开始就是数组，以容纳未来 Threads 多视频/轮播帖子。
5. 认证模型预留 `none | browser_cookies | cookie_file`，但第一版 API 不接收明文 Cookie。
6. 数据库中平台字段使用字符串而非数据库枚举，避免增加平台时迁移表结构。
7. 前端显示“Threads 支持即将推出”，不能显示下载按钮或伪装成可用。

未来桌面 Threads Provider 应独立于 `YtDlpProvider`，也可以由 yt-dlp 插件适配器实现；上层 API 无需变化。Android 已采用独立解析器，不将 Threads URL 交给通用 yt-dlp fallback。

## 5. 核心数据模型

### PlatformCapability

```json
{
  "id": "threads",
  "display_name": "Threads",
  "status": "planned",
  "available": false,
  "url_patterns": ["https://www.threads.com/@*/post/*"],
  "capabilities": ["video", "multi_asset", "authentication"]
}
```

### MediaInfo

```json
{
  "source_url": "https://x.com/user/status/123",
  "canonical_url": "https://x.com/user/status/123",
  "platform": "x",
  "title": "Post by user",
  "author": {"id": null, "name": "user", "url": null},
  "thumbnail_url": null,
  "duration_seconds": 12.5,
  "assets": [
    {
      "id": "asset-1",
      "type": "video",
      "thumbnail_url": null,
      "formats": []
    }
  ],
  "authentication": {"required": false, "mode": "none"}
}
```

### DownloadJob

任务状态固定为：

```text
queued -> analyzing -> downloading -> processing -> completed
                                      ├────────────> failed
                                      └────────────> cancelled
```

终态为 `completed | failed | cancelled`。状态只能向前推进；重试创建新任务，并记录 `retry_of_job_id`。

## 6. 错误码

第一版至少包含：

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `INVALID_URL` | 400 | URL 格式不合法 |
| `UNSUPPORTED_PLATFORM` | 422 | 平台未识别 |
| `PLATFORM_NOT_IMPLEMENTED` | 501 | 已识别但尚未实现，例如 Threads |
| `AUTHENTICATION_REQUIRED` | 401 | 源站要求登录 |
| `MEDIA_NOT_FOUND` | 404 | 帖子不存在或没有可下载媒体 |
| `FORMAT_NOT_AVAILABLE` | 422 | 所选格式已失效或不可用 |
| `RATE_LIMITED` | 429 | 源站或本地策略限流 |
| `DOWNLOAD_FAILED` | 502 | 源站下载失败 |
| `POST_PROCESSING_FAILED` | 500 | FFmpeg 处理失败 |
| `LOCAL_SERVICE_UNAVAILABLE` | 503 | 客户端无法连接本地服务 |

统一错误体：

```json
{
  "error": {
    "code": "PLATFORM_NOT_IMPLEMENTED",
    "message": "Threads support is planned but not available yet.",
    "retryable": false,
    "details": {"platform": "threads"},
    "request_id": "req_..."
  }
}
```

## 7. 安全基线

- API 默认仅监听 `127.0.0.1`，禁止默认绑定 `0.0.0.0`。
- 只允许 `http` 和 `https` URL。
- DNS 解析前后都应拒绝回环、私网、链路本地和云元数据地址。
- 平台 Provider 只允许访问其声明的域名和已验证的媒体 CDN。
- 本地 API 使用安装时生成的随机令牌；令牌不得写入浏览器同步存储。
- CORS 只允许已知扩展 Origin，不使用通配符。
- 输出文件名必须净化，禁止绝对路径和 `..`。
- 日志隐藏查询令牌、Cookie、Authorization 和媒体签名 URL。
- 不实现 DRM、付费墙、验证码或访问控制绕过。

## 8. 版本与兼容性

- API 前缀使用 `/api/v1`。
- 客户端启动时先读取 `/health` 和 `/api/v1/version`。
- 破坏性接口变更增加 API 大版本，不能静默改变字段语义。
- OpenAPI 文档是浏览器和 Android 客户端契约的唯一事实来源。
