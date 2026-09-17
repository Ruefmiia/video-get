# 阶段 2：Chrome/Edge 浏览器扩展

## 目标

使用一套 Manifest V3 代码生成 Chrome 和 Edge 扩展。扩展负责接收链接、调用本地 FastAPI、展示分析结果与下载状态，不负责平台解析或媒体合并。

## 建议技术栈

- TypeScript
- Vite
- React 或 Vue 3，二选一并保持轻量
- WebExtension 类型定义
- Vitest
- Playwright，用于扩展端到端测试

## 扩展组成

```text
apps/browser-extension/
├─ manifest.base.json
├─ manifest.chrome.json
├─ manifest.edge.json
├─ src/
│  ├─ background/
│  │  └─ service-worker.ts
│  ├─ content/
│  │  └─ detect-url.ts
│  ├─ popup/
│  ├─ options/
│  ├─ api/
│  ├─ storage/
│  └─ shared/
└─ tests/
```

## 最小权限

建议第一版只申请：

- `activeTab`：用户主动打开扩展时读取当前标签 URL。
- `storage`：保存非敏感偏好和本地服务发现信息。
- `notifications`：可选，用于下载完成提醒。
- 对 `http://127.0.0.1/*` 和 `http://localhost/*` 的 host permission。

如果最终文件由本地服务直接写入下载目录，则不需要 `downloads` 权限。只有服务返回文件 URL 并由浏览器保存时才申请该权限。

不申请全站点永久读写权限，不读取页面 Cookie，不监听页面全部网络请求。

## 用户流程

1. 用户打开支持的平台页面。
2. 点击扩展图标。
3. 扩展读取当前标签 URL，也允许手动粘贴。
4. 调用 `/api/v1/analyze`。
5. 展示标题、封面、作者、时长、媒体项和格式。
6. 用户明确点击下载。
7. 创建下载任务并轮询进度。
8. 完成后显示文件名和“打开文件夹”。

不得在用户浏览网页时自动下载，也不得在后台扫描浏览历史。

## 本地服务发现

第一版固定优先端口，例如 `17382`：

1. 请求 `http://127.0.0.1:17382/health`。
2. 校验返回结构和 API 版本。
3. 携带本地令牌请求业务 API。
4. 连接失败时展示安装/启动桌面辅助程序的说明。

后续如需要动态端口，可由 Native Messaging 返回端口和一次性令牌。

## Threads 占位体验

> 此处描述浏览器扩展连接桌面 FastAPI 时的状态。Android 客户端已独立支持部分公开 Threads 视频，但桌面 API/扩展尚未启用 Threads 下载。

扩展必须识别以下 URL：

- `threads.com/@.../post/...`
- `www.threads.com/@.../post/...`
- `threads.net/@.../post/...`
- `/share/...` 形式

识别后显示：

> 已识别 Threads 链接。该平台已列入开发计划，当前版本暂不支持下载。

扩展仍然调用 `/api/v1/analyze` 取得权威错误结果，不自行假装分析成功。UI 不显示格式选择和下载按钮。

## 状态和错误呈现

- 本地服务未安装：显示安装入口。
- 本地服务未启动：显示启动与重试入口。
- 版本不兼容：提示升级服务或扩展。
- 需要登录：说明当前第一版只支持公开内容。
- 平台未实现：显示计划状态。
- 失败可重试：保留原始 URL 与用户选择，但不保存敏感信息。

## 完成标准

- 同一源码产出 Chrome 和 Edge 安装包。
- X、Instagram、Threads URL 识别测试通过。
- Threads 显示“计划中”，不会发起下载。
- 本地 API 离线、版本冲突和错误响应都有明确界面。
- 扩展重启后能恢复正在显示任务的状态。
- 权限清单通过人工审查，没有不必要的全站权限。
- 扩展不包含远程加载执行代码。

## 当前实现（0.1.0）

- 源码位于 `apps/browser-extension`，采用 React、TypeScript、Vite 和 Manifest V3。
- `npm run build` 分别生成 `dist/chrome` 与 `dist/edge`，两者共享最小权限清单。
- 设置页允许手动配置固定本地地址和访问令牌；令牌仅写入 `chrome.storage.local`，不使用同步存储。
- 弹窗读取用户当前激活标签，也允许粘贴链接。分析、格式选择、创建任务和进度恢复均通过本地 API 完成。
- Threads 会调用 `/api/v1/analyze` 获取权威的 501 结果，显示计划中状态，且不显示下载动作。
- 已接入阶段 3 Native Messaging：设置页可从桌面辅助程序自动发现本机地址与令牌；手动配置仍作为开发和故障恢复入口保留。
