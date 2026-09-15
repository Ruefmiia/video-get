# 阶段 1：FastAPI 下载核心与 Provider 接口

> 实施状态：已完成。自动化测试覆盖率门槛为 80%，Threads 保持计划中且不提供下载。

## 目标

建立不依赖具体 UI 的本地下载核心，并完成可测试的 Provider、任务、FFmpeg 和 SQLite 边界。此阶段可以使用通用测试 URL验证框架，但不承诺任何平台端到端可用。

## 技术选型

- Python 3.11+
- FastAPI + Uvicorn
- Pydantic v2
- yt-dlp Python API
- FFmpeg/ffprobe 可执行程序
- SQLAlchemy 2 + Alembic
- SQLite（WAL 模式）
- pytest + pytest-asyncio
- Ruff + mypy

不要解析 yt-dlp 的普通 stdout。优先使用 Python API、progress hook 和结构化返回值；若必须启动子进程，则使用 JSON 输出和独立参数数组。

## 建议目录

```text
services/downloader-api/
├─ pyproject.toml
├─ src/video_get/
│  ├─ main.py
│  ├─ api/
│  │  ├─ dependencies.py
│  │  ├─ errors.py
│  │  └─ routes/
│  ├─ domain/
│  │  ├─ models.py
│  │  ├─ errors.py
│  │  └─ enums.py
│  ├─ providers/
│  │  ├─ base.py
│  │  ├─ registry.py
│  │  ├─ url_router.py
│  │  └─ yt_dlp.py
│  ├─ jobs/
│  │  ├─ manager.py
│  │  ├─ worker.py
│  │  └─ cancellation.py
│  ├─ media/
│  │  ├─ ffmpeg.py
│  │  └─ filenames.py
│  ├─ persistence/
│  │  ├─ database.py
│  │  ├─ entities.py
│  │  └─ repositories.py
│  └─ security/
│     ├─ url_policy.py
│     └─ redaction.py
└─ tests/
```

## API 契约

### `GET /health`

不需要认证，只返回进程是否存活：

```json
{"status":"ok"}
```

不得返回路径、版本依赖或系统信息。

### `GET /api/v1/version`

返回服务版本、API 版本、yt-dlp 版本和 FFmpeg 可用性。

### `GET /api/v1/platforms`

返回 X、Instagram 和 Threads。Threads 必须是：

```json
{
  "id": "threads",
  "display_name": "Threads",
  "status": "planned",
  "available": false,
  "capabilities": ["video", "multi_asset", "authentication"]
}
```

### `POST /api/v1/analyze`

请求：

```json
{"url":"https://x.com/user/status/123"}
```

成功返回 `MediaInfo`；Threads 返回 `PLATFORM_NOT_IMPLEMENTED`；未知平台返回 `UNSUPPORTED_PLATFORM`。

### `POST /api/v1/downloads`

```json
{
  "url": "https://x.com/user/status/123",
  "asset_ids": ["asset-1"],
  "format_id": "format-id",
  "output": {"mode": "default_downloads_directory"}
}
```

创建任务后立即返回 `202 Accepted` 和任务 ID。

### 任务接口

- `GET /api/v1/downloads/{job_id}`
- `DELETE /api/v1/downloads/{job_id}`
- `POST /api/v1/downloads/{job_id}/retry`
- `GET /api/v1/downloads?cursor=...&limit=...`

第一版进度使用 500–1000 ms 轮询，不急于引入 WebSocket。

## URL 路由

标准化步骤：

1. 去掉 URL 两端空白。
2. 校验 scheme。
3. 将 host 转为小写并进行 IDNA 规范化。
4. 删除 fragment。
5. 保留平台识别所需 query，删除已知跟踪参数。
6. 根据精确域名和路径匹配平台。
7. 执行 SSRF 地址策略。

优先级：明确 Provider > 已知但未实现的平台 > 可选通用 yt-dlp fallback > 不支持。

Threads 域名必须在“已知但未实现”列表，确保不会落入 fallback。

## 任务执行

- yt-dlp 是阻塞型工作，放入受控线程池或独立工作进程。
- 第一版每个平台默认并发 1，总并发不超过 2。
- 下载先进入每任务独立临时目录。
- FFmpeg 完成且输出校验通过后，再原子移动到目标目录。
- 取消操作设置 token，并终止正在运行的子进程。
- 应用异常退出后，启动时将遗留的运行中任务标记为失败，可由用户重试。

进度字段：

```json
{
  "state": "downloading",
  "downloaded_bytes": 1048576,
  "total_bytes": 10485760,
  "estimated_total_bytes": null,
  "speed_bytes_per_second": 524288,
  "eta_seconds": 18,
  "progress": 0.1
}
```

未知总大小时 `total_bytes` 和 `progress` 为 `null`，不得制造虚假百分比。

## SQLite 表

### `download_jobs`

- `id`
- `source_url`
- `canonical_url`
- `platform`：字符串
- `state`
- `selected_format_id`
- `title`
- `output_path`
- `downloaded_bytes`
- `total_bytes`
- `error_code`
- `error_message`
- `retry_of_job_id`
- `created_at`、`started_at`、`finished_at`

### `app_settings`

- `key`
- `value_json`
- `updated_at`

不保存完整媒体签名 URL、Cookie 或 Authorization。

## 完成标准

- OpenAPI 文档可生成并固定到 `contracts/openapi/openapi.json`。
- Provider 可以通过注册表添加，不需要修改路由层。
- Threads URL 被识别并稳定返回 `PLATFORM_NOT_IMPLEMENTED`。
- 任务支持创建、查询、取消、失败和重试。
- FFmpeg 缺失时给出可读诊断，不导致服务崩溃。
- 重启后历史记录仍存在。
- URL、输出路径和日志脱敏测试通过。
- 单元测试覆盖率目标不低于 80%，安全策略与状态机要求 100% 分支覆盖。
