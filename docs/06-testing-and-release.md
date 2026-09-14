# 测试、发布与验收策略

## 1. 测试层次

### 单元测试

- URL 规范化与平台识别
- Provider 注册表
- 状态机和取消令牌
- 文件名净化
- 错误映射和日志脱敏
- 格式转换
- Threads 占位行为

### 契约测试

- FastAPI OpenAPI 与固定契约一致
- TypeScript/Kotlin DTO 能解析全部示例
- 新增字段遵守向后兼容规则
- 所有错误均满足统一错误体

### 集成测试

- yt-dlp 元数据到内部模型的转换
- FFmpeg 合并与取消
- SQLite 重启恢复
- 临时文件清理
- 扩展与本地 API 通信
- Android Room 与任务恢复

### 真实平台冒烟测试

使用团队自有或明确授权的公开内容。真实网络测试单独运行，不能成为每次普通单元测试的硬依赖。

## 2. 固定回归样本

`tests/platform-fixtures` 可以保存：

- 脱敏后的 yt-dlp JSON
- URL 规范化输入/输出
- Provider 错误样本
- 自有短视频测试文件
- FFmpeg 探测输出

不得保存：

- Cookie、账号口令或 Authorization
- 尚未过期的媒体签名 URL
- 未获授权的第三方视频
- 含个人敏感信息的页面快照

## 3. 每阶段质量门槛

### 阶段 1

- Python lint、类型检查和测试通过
- OpenAPI 契约生成无非预期差异
- 安全策略与状态机测试通过

### 阶段 2

- Chrome/Edge 两个构建成功
- 扩展权限快照无非预期增加
- Playwright 本地服务联调通过

### 阶段 3

- 干净 Windows 虚拟机安装、升级、卸载通过
- 包校验和和第三方许可证清单生成
- 无残留后台进程

### 阶段 4

- X/Instagram 真实平台矩阵通过
- Threads 占位回归通过
- 不同网络错误可以区分并可诊断

### 阶段 5

- Android 单元测试、仪器测试和真实设备测试通过
- 后台下载、通知和 MediaStore 流程通过
- AAB 许可证、权限、ABI、包体积检查通过

## 4. 版本策略

- 桌面 API、扩展和 Android 分别版本化。
- API 使用语义化版本，并声明支持的客户端版本区间。
- 平台可用性与应用版本分离，例如 `x: available`、`threads: planned`。
- 发布说明列出 yt-dlp 和 FFmpeg 版本。
- 平台故障不能伪装成客户端网络故障。

## 5. Threads 开发启动条件

虽然当前不开发，满足以下条件后可开启独立阶段：

- X 与 Instagram 的端到端成功率和错误分类已经稳定。
- 已准备自有 Threads 视频、轮播、删除和登录要求样本。
- 已决定公开内容策略及 Cookie 是否进入产品范围。
- 已评估独立解析器与 yt-dlp 插件两种实现。
- 有平台变化监控、快速停用开关和回归测试负责人。

开启后应新增 `ThreadsProvider`，将 capability 的 `status` 从 `planned` 改为 `experimental`；客户端协议与数据库结构不应因此改变。
