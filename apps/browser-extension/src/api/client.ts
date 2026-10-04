import type { BilibiliCookie, DownloadJob, MediaInfo, ServiceVersion } from '../shared/types'

export type ApiErrorKind = 'offline' | 'unauthorized' | 'incompatible' | 'planned' | 'authentication' | 'request'

export class ApiError extends Error {
  constructor(public readonly kind: ApiErrorKind, message: string, public readonly status?: number) {
    super(message)
  }
}

export function normalizeLocalBaseUrl(value: string): string {
  let url: URL
  try { url = new URL(value) } catch { throw new Error('请输入有效的本地服务地址。') }
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)) {
    throw new Error('服务地址必须使用 http://127.0.0.1 或 http://localhost。')
  }
  return url.origin
}

export class VideoGetApi {
  private readonly baseUrl: string
  constructor(baseUrl: string, private readonly token: string) {
    this.baseUrl = normalizeLocalBaseUrl(baseUrl)
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        ...init,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.token}`, ...init?.headers },
      })
    } catch {
      throw new ApiError('offline', '无法连接本地服务。请启动 Video Get 服务后重试。')
    }
    if (!response.ok) {
      if (response.status === 404 && path.startsWith('/api/v1/sessions/')) {
        throw new ApiError('incompatible', '本地服务尚不支持 B站登录，请更新桌面程序。', 404)
      }
      let code = ''
      let detail = ''
      try {
        const payload = await response.json() as { detail?: unknown; error?: { code?: unknown; message?: unknown } }
        code = String(payload.error?.code ?? '')
        detail = String(payload.error?.message ?? payload.detail ?? '')
      } catch { /* non-JSON error */ }
      if (code === 'AUTHENTICATION_REQUIRED') throw new ApiError('authentication', '该内容需要登录。B站可在设置中登录并同步登录状态；其他平台暂不支持桌面登录。', response.status)
      if (code === 'FORMAT_NOT_AVAILABLE') throw new ApiError('request', '画质选择已过期或登录状态已改变，请重新分析链接。', response.status)
      if (code === 'INVALID_SESSION') throw new ApiError('authentication', 'B站登录状态无效，请重新登录后同步。', response.status)
      if (code === 'SOURCE_FORBIDDEN') throw new ApiError('authentication', '源站拒绝访问该内容；请确认它是无需登录即可访问的公开内容。', response.status)
      if (code === 'RATE_LIMITED') throw new ApiError('request', '源站请求过于频繁，请稍后重试。', response.status)
      if (code === 'MEDIA_NOT_FOUND') throw new ApiError('request', '内容不存在、已删除或没有可下载的视频。', response.status)
      if (code === 'SOURCE_TIMEOUT') throw new ApiError('request', '连接源站超时，请检查网络或代理后重试。', response.status)
      if (code === 'SOURCE_UNREACHABLE') throw new ApiError('request', '当前网络无法连接源站，请检查网络或代理设置。', response.status)
      if (response.status === 401) throw new ApiError('unauthorized', '访问令牌无效，请在设置中更新。', 401)
      if (response.status === 501) throw new ApiError('planned', '已识别 Threads 链接。该平台已列入开发计划，当前版本暂不支持下载。', 501)
      throw new ApiError('request', detail || `本地服务返回错误（${response.status}）。`, response.status)
    }
    return response.json() as Promise<T>
  }

  async health(): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/health`)
      if (!response.ok || (await response.json()).status !== 'ok') throw new Error()
    } catch { throw new ApiError('offline', '无法连接本地服务。请启动 Video Get 服务后重试。') }
  }

  async version(): Promise<ServiceVersion> {
    const version = await this.request<ServiceVersion>('/api/v1/version')
    if (version.api_version !== '1') throw new ApiError('incompatible', '本地服务版本不兼容，请升级扩展或服务。')
    return version
  }

  analyze(url: string): Promise<MediaInfo> {
    return this.request('/api/v1/analyze', { method: 'POST', body: JSON.stringify({ url }) })
  }

  bilibiliSession(): Promise<{ configured: boolean }> {
    return this.request('/api/v1/sessions/bilibili')
  }

  syncBilibiliSession(cookies: BilibiliCookie[]): Promise<{ configured: boolean }> {
    return this.request('/api/v1/sessions/bilibili', { method: 'POST', body: JSON.stringify({ cookies }) })
  }

  clearBilibiliSession(): Promise<{ configured: boolean }> {
    return this.request('/api/v1/sessions/bilibili', { method: 'DELETE' })
  }

  createDownload(url: string, assetId: string, formatId: string): Promise<DownloadJob> {
    return this.request('/api/v1/downloads', {
      method: 'POST',
      body: JSON.stringify({ url, asset_ids: [assetId], format_id: formatId, output: { mode: 'default_downloads_directory' } }),
    })
  }

  getDownload(id: string): Promise<DownloadJob> {
    return this.request(`/api/v1/downloads/${encodeURIComponent(id)}`)
  }

  cancelDownload(id: string): Promise<DownloadJob> {
    return this.request(`/api/v1/downloads/${encodeURIComponent(id)}`, { method: 'DELETE' })
  }

  retryDownload(id: string): Promise<DownloadJob> {
    return this.request(`/api/v1/downloads/${encodeURIComponent(id)}/retry`, { method: 'POST' })
  }
}
