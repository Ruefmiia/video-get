# 桌面端 B站下载与登录

> 2026-10-05：已实现，适用于 Windows 独立桌面版或 Chrome/Edge 扩展。不改动 Android 或鸿蒙客户端。独立桌面登录见[桌面 0.2.0](13-standalone-desktop.md)。

## 第一版范围

- 普通投稿 BV/av 视频，支持 www/m 域名及 b23.tv 分享链接。
- 保留 `p=` 分 P 参数；有参数时只下载该 P，无参数时选择首 P。不自动下载整个合集。
- 短链逐跳校验，仅允许 HTTPS 的 B站普通视频或另一条 b23.tv 短链；拒绝任意网站、私网或其他类型页面。
- 展示当前账号可用的视频格式，按分辨率/FPS 倒序，显示编码标识。客户端下载使用不透明格式令牌。
- DASH 视频与音频通过 FFmpeg 合并为 MP4；没有音频时不静默生成无声视频。
- 未登录可尝试公开画质；登录或大会员画质由源站实际返回决定，不绕过权限、付费或 DRM。
- 不含番剧/影视、直播、课程、单独音频、字幕/弹幕或批量合集。

## 使用流程

1. 退出旧桌面程序（包括托盘实例），安装更新后的 `VideoGet-Setup-0.2.0.exe`，重新启动。可直接在桌面窗口分析和下载；以下是保留的扩展使用方式。
2. 在 Chrome/Edge 扩展管理页重新加载 `apps/browser-extension/dist/chrome` 或 `dist/edge`，打开扩展设置并确认服务连接正常。
3. 在扩展设置点击“打开 B站登录”，在该浏览器的 B站官方网站完成登录。桌面“B站登录”使用独立私密窗口，成功后自动同步，无需执行下一步扩展授权。
4. 回到扩展设置，点击“授权并同步登录状态”，首次确认可选 Cookie 和 B站网站权限。
5. 重新分析视频，再选择画质下载。同步、更换账号或清除登录后需重新分析旧格式选择。
6. “清除登录状态”清除服务会话并撤销读取权限，不退出浏览器账号。已开始的下载可能继续使用启动时的会话；需要立即停止时请取消下载。

源站会话过期或服务重启后需重新同步。同步成功只表示会话已传入，不表示源站已验证账号。看不到高清时，确认账号权限，重新登录/同步/分析。

## 隐私与 API

- 默认不申请 Cookie 权限，不在设置页打开、分析或后台浏览时自动读取；只在主动点击授权同步后读取。
- 仅筛选 B站根域根路径的 `SESSDATA`、`DedeUserID`、`DedeUserID__ckMd5` 和 `bili_jct`，排除过期、分区、其他站点和非根路径 Cookie，同名去重。
- 凭证经本地 Bearer Token 鉴权的回环接口传给服务，不写扩展 storage、Cookie 文件、日志或远端。
- 服务仅存内存会话，固定 `.bilibili.com` / HTTPS CookieJar，不把 Cookie 发给 X、Instagram、b23.tv 或媒体 CDN。服务停止即清除。
- 请求限制 24 KB；Cookie 名称、数量、长度与内容均校验，错误响应不回显凭证。状态只返回 `configured` 布尔值。
- 本机恶意程序仍可能读取内存或回环流量，账号与浏览器安全需用户维护，不提供凭证分发或共享账号功能。

以下接口均需要本地访问令牌：

- `GET /api/v1/sessions/bilibili`：查询是否配置未过期 SESSDATA。
- `POST /api/v1/sessions/bilibili`：正文 `{"cookies":[{"name":"SESSDATA","value":"<当前账号会话>","expires":null}]}`，expires 为 Unix 秒，null 为浏览器会话 Cookie。
- `DELETE /api/v1/sessions/bilibili`：清除内存会话。

## 验证

- Python 后端/桌面回归 84 项测试通过，覆盖率 86.36%；Ruff、mypy 检查通过。
- 扩展 32 项测试通过，覆盖平台识别、权限拒绝、凭证筛选/去重、退出时清旧会话、清除权限及授权按钮流程；TypeScript 检查通过。
- 公开样例 `https://www.bilibili.com/video/BV1xx411c7mD` 实际分析、下载及合并通过；ffprobe 确认 MP4 有 AV1 视频（512×384）和 AAC 音频。仅证明公开未登录链路，不代表高清登录已验收。
- Chrome/Edge 扩展与 Windows 安装器已构建；打包 EXE 在独立测试端口启动通过，B站能力及受鉴权保护的会话接口可用。真实授权弹窗、登录后高清、真实短链/多 P 及覆盖安装待人工验收。

## 构建

```powershell
cd D:\Codex\video-get
python -m pytest
python -m ruff check services/downloader-api/src/video_get
python -m mypy services/downloader-api/src/video_get
.\apps\desktop-companion\build-installer.ps1
cd apps\browser-extension
npm run typecheck
npm test
npm run build
```

安装包：`apps/desktop-companion/dist/installer/VideoGet-Setup-0.2.0.exe`。不改变 Android 1.1.0。
