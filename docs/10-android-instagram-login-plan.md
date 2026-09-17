# Android Instagram App 内登录方案

> 状态：已纳入 Android 1.0.0；登录、视频、单图、轮播和混合媒体进入正式版本回归矩阵。

## 目标与边界

当 Instagram 帖子或 Reel 被源站要求登录时，用户可在 Video Get 内打开 Instagram 官方页面完成登录，随后继续本地分析和下载。整个流程不依赖云服务器，不导入或分发共享 Cookie，也不读取 Chrome 或 Instagram App 的私有数据。

第一版只解决 Instagram 网站会话接入，不承诺登录永不过期，也不规避验证码、双重验证、检查点、地区限制或平台风控。用户应仅下载其有权使用的内容。

## 用户流程

1. 在首页点击“连接 Instagram”。
2. App 使用独立 WebView 打开 Instagram 官方登录页。
3. 用户直接在网页中输入凭据并完成可能的双重验证；Video Get 不读取密码。
4. App 检测到有效 `sessionid` 后自动关闭登录页并返回 Video Get；“完成登录”按钮仅作为检测延迟时的备用操作。
5. 分析或下载 Instagram 链接时，将当前 WebView 会话临时转换为 yt-dlp 所需的 Netscape Cookie 文件，并使用相同 User-Agent。
6. 单次 yt-dlp 调用结束后立即删除临时文件。
7. 点击“Instagram 已连接”可退出连接并清除 WebView Cookie。

未连接时仍保留匿名分析，只有源站要求身份验证时才需要连接。会话失效后提示用户重新连接。

## 技术实现

- `InstagramLoginActivity`：显示受限 WebView，仅允许 Instagram 及 Meta 登录所需域名导航；不注册 JavaScript Bridge。
- `InstagramSession`：检测会话、保存 WebView User-Agent、生成单次临时 Cookie 文件、清除会话。
- `MediaAnalyzer`：Instagram 分析阶段在已连接时附加 `--cookies` 与 `--user-agent`。
- `DownloadWorker`：后台实际下载阶段重新从当前会话生成临时 Cookie，避免分析成功后下载阶段丢失身份。
- `InstagramAnalyzer`：使用当前会话读取帖子媒体结构，支持单图、轮播多图、单视频以及图文/视频混合帖子，并保留帖子顺序。
- Cookie 仅保存在 Android WebView 私有存储；临时导出位于 App 缓存目录，调用完成即删除。
- Android 备份关闭，避免登录会话随系统备份迁移。

## 安全约束

- 不记录账号、密码、Cookie 或 Instagram URL 到日志。
- 不向服务端上传凭据，不提供共享账号功能。
- 登录 Activity 不导出，其他 App 无法直接启动其内部接口。
- 禁止 WebView 文件和 Content URI 访问，不提供原生 JavaScript 接口。
- 退出连接时清除 Cookie；卸载 App 也会清除本地会话。
- Instagram 可能对第三方工具行为进行限制；应控制重试频率，并准备随时重新登录或停止使用该账号。

## 验收标准

- 未登录时 X、Threads、YouTube 及匿名可访问的 Instagram 链接不受影响。
- 登录页面可以完成普通登录和双重验证流程。
- 检测到登录会话后自动返回 Video Get，不停留在 Instagram 页面。
- 登录后重启 App 仍显示“Instagram 已连接”。
- 需要登录的授权 Reel 可完成分析、选择格式、后台下载并写入 `Movies/Video Get`。
- Instagram 图片写入 `Pictures/Video Get`；轮播或混合帖子可一次保存全部图片和视频。
- 分析与下载使用相同 WebView User-Agent 和当前会话。
- 临时 Cookie 文件在成功与失败后均被删除。
- 退出连接后状态立即更新，后续 yt-dlp 调用不再携带 Cookie。
- 会话失效时显示重新连接提示，不泄露 yt-dlp 原始 Cookie。

## 已知限制与后续项

- WebView 会话与 Instagram 原生 App、Chrome 会话相互独立，首次必须单独登录。
- Instagram 页面或接口变化可能导致有效 Cookie 仍无法解析，需要同步升级 yt-dlp。
- 第一版用 `sessionid` 判断登录状态；真机验证后可补充检查点和挑战页面识别。
- 后续可在分析到“需要登录”错误时直接提供“连接 Instagram”操作，减少用户预先配置步骤。
