# 阶段 4：X 与 Instagram 端到端支持

## 目标

在下载核心、扩展和桌面辅助程序之间完成真实平台闭环。第一版只承诺公开、无需绕过访问控制的单帖子内容。

## 平台范围

### X

第一版支持：

- `x.com/{user}/status/{id}`
- `twitter.com/{user}/status/{id}`
- 单视频帖子
- 多媒体帖子中的一个或多个视频
- 可用时展示多个清晰度

暂不支持：

- 账号时间线批量下载
- Space、直播
- 私密账号和受限内容
- 自动读取浏览器登录状态

### Instagram

第一版支持：

- `instagram.com/p/{shortcode}`
- `instagram.com/reel/{shortcode}`
- `instagram.com/reels/{shortcode}` 的规范化处理
- 公开帖子、公开 Reels
- 轮播帖子中的视频媒体

暂不支持：

- Story、Highlight
- 用户主页批量下载
- 私密账号内容
- 自动读取浏览器 Cookie

## yt-dlp 适配原则

- 使用嵌入式 Python API，不把用户输入拼接成 shell 命令。
- `download=False` 获取元数据，下载阶段重新验证格式可用性。
- 将 yt-dlp 格式转换为稳定的内部 `MediaFormat`，不把完整原始对象暴露给客户端。
- 优先选择 MP4/H.264 + M4A/AAC；源站没有时允许其他容器，但 UI 必须准确展示。
- 分离音视频时交给 FFmpeg 合并。
- 对源站 401/403、登录要求、限流和删除内容分别映射错误码。

## 格式模型

客户端不需要理解 yt-dlp 的 format expression。后端返回：

```json
{
  "id": "opaque-format-token",
  "label": "1080p MP4",
  "container": "mp4",
  "width": 1920,
  "height": 1080,
  "fps": 30,
  "video_codec": "h264",
  "audio_codec": "aac",
  "has_video": true,
  "has_audio": true,
  "estimated_bytes": null,
  "requires_merge": true
}
```

`id` 是服务端生成的短期不透明标识。下载时不能相信客户端提交的任意 yt-dlp 表达式。

## 文件命名

默认：

```text
{platform}_{author}_{date}_{post_id}_{title}.{ext}
```

要求：

- 去除 Windows 非法字符和控制字符。
- 限制单个文件名长度。
- 保留稳定帖子 ID 以便去重。
- 冲突时追加短哈希，不覆盖现有文件。
- 标题为空时仍可生成稳定文件名。

## 测试矩阵

每个平台准备以下已获授权或自有测试素材：

| 场景 | X | Instagram |
|---|---:|---:|
| 单视频公开帖子 | 必测 | 必测 |
| 多视频/轮播 | 必测 | 必测 |
| 竖屏视频 | 必测 | 必测 |
| 视频音频分离 | 必测 | 必测 |
| 已删除帖子 | 必测 | 必测 |
| 非法链接 | 必测 | 必测 |
| 登录要求 | 必测 | 必测 |
| 限流/403 映射 | 必测 | 必测 |
| 取消与重试 | 必测 | 必测 |

仓库只保存脱敏响应 fixture 和自有测试内容，不提交第三方 Cookie、签名 URL 或受版权保护的视频文件。

## Threads 回归占位

阶段 4 仍不开发 Threads，但每次发布必须运行以下契约测试：

- 四类 Threads 域名/路径能识别为 `threads`。
- `/api/v1/platforms` 中 Threads 状态仍为 `planned`。
- 分析返回 `501 PLATFORM_NOT_IMPLEMENTED`。
- 扩展显示计划提示且无下载按钮。
- 通用 yt-dlp fallback 没有截获 Threads URL。

这组测试保证未来增加 `ThreadsProvider` 时只替换 Provider 实现与能力状态，不修改客户端协议。

## 完成标准

- X 与 Instagram 测试矩阵全部通过。
- 从扩展点击到文件落盘的完整流程可重复执行。
- 多媒体帖子能展示并选择正确媒体项。
- 取消不会留下可见的损坏文件。
- 同一帖子重复下载不会静默覆盖已有文件。
- 用户可理解登录要求、内容不存在和源站限流的差异。
- Threads 占位回归测试通过。
