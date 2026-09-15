import { FormEvent, useEffect, useMemo, useState } from 'react'
import { ApiError, VideoGetApi } from '../api/client'
import { detectPlatform, isHttpUrl } from '../shared/platform'
import { discoverNativeSettings, loadActiveJob, loadSettings, saveActiveJob } from '../shared/storage'
import type { DownloadJob, MediaInfo } from '../shared/types'
import { AlertIcon, CheckIcon, DownloadIcon, RefreshIcon, SettingsIcon } from './icons'

type ViewState = 'booting' | 'idle' | 'analyzing' | 'ready' | 'planned' | 'downloading' | 'done' | 'error'

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请重试。'
}

function formatBytes(bytes: number | null): string {
  if (!bytes) return '大小未知'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index > 1 ? 1 : 0)} ${units[index]}`
}

export function Popup() {
  const [state, setState] = useState<ViewState>('booting')
  const [url, setUrl] = useState('')
  const [api, setApi] = useState<VideoGetApi | null>(null)
  const [media, setMedia] = useState<MediaInfo | null>(null)
  const [job, setJob] = useState<DownloadJob | null>(null)
  const [assetId, setAssetId] = useState('')
  const [formatId, setFormatId] = useState('')
  const [message, setMessage] = useState('正在连接本地服务…')
  const detected = useMemo(() => detectPlatform(url), [url])
  const videoAssets = useMemo(
    () => media?.assets.filter((item) => item.type === 'video') ?? [],
    [media],
  )
  const asset = videoAssets.find((item) => item.id === assetId) ?? videoAssets[0]

  async function connect(): Promise<{ client: VideoGetApi; tokenReady: boolean }> {
    let settings = await loadSettings()
    if (!settings.apiToken) settings = (await discoverNativeSettings()) ?? settings
    const client = new VideoGetApi(settings.apiBaseUrl, settings.apiToken)
    await client.health()
    if (!settings.apiToken) return { client, tokenReady: false }
    await client.version()
    setApi(client)
    return { client, tokenReady: true }
  }

  async function initialize() {
    setState('booting')
    setMessage('正在连接本地服务…')
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
      if (tab?.url && isHttpUrl(tab.url)) setUrl(tab.url)
      const { client, tokenReady } = await connect()
      if (!tokenReady) {
        setState('error')
        setMessage('本地服务已启动。请先在设置中填写访问令牌。')
        return
      }
      const activeJob = await loadActiveJob()
      if (activeJob) {
        setUrl(activeJob.url)
        const restored = await client.getDownload(activeJob.id)
        setJob(restored)
        setState(restored.state === 'completed' ? 'done' : restored.state === 'failed' || restored.state === 'cancelled' ? 'error' : 'downloading')
      } else setState('idle')
    } catch (error) {
      setState('error')
      setMessage(errorMessage(error))
    }
  }

  useEffect(() => { void initialize() }, [])

  useEffect(() => {
    if (!api || state !== 'downloading' || !job) return
    const timer = window.setInterval(async () => {
      try {
        const next = await api.getDownload(job.id)
        setJob(next)
        if (next.state === 'completed') { setState('done'); await saveActiveJob(null) }
        if (next.state === 'failed' || next.state === 'cancelled') {
          setMessage(next.error_message ?? '下载任务未完成。')
          setState('error')
          await saveActiveJob(null)
        }
      } catch (error) { setMessage(errorMessage(error)); setState('error') }
    }, 900)
    return () => window.clearInterval(timer)
  }, [api, job, state])

  async function analyze(event: FormEvent) {
    event.preventDefault()
    if (!isHttpUrl(url)) { setMessage('请粘贴有效的 http 或 https 链接。'); setState('error'); return }
    setState('analyzing')
    setMessage('正在读取公开媒体信息…')
    setMedia(null)
    try {
      const client = api ?? (await connect()).client
      const result = await client.analyze(url)
      setApi(client)
      setMedia(result)
      const firstAsset = result.assets.find((item) => item.type === 'video') ?? result.assets[0]
      setAssetId(firstAsset?.id ?? '')
      setFormatId(firstAsset?.formats[0]?.id ?? '')
      setState('ready')
    } catch (error) {
      if (error instanceof ApiError && error.kind === 'planned') setState('planned')
      else setState('error')
      setMessage(errorMessage(error))
    }
  }

  async function download() {
    if (!api || !media || !asset || !formatId || detected?.planned) return
    setState('downloading')
    setMessage('任务已送入本地下载队列。')
    try {
      const created = await api.createDownload(media.canonical_url, asset.id, formatId)
      setJob(created)
      await saveActiveJob({ id: created.id, url })
    } catch (error) { setMessage(errorMessage(error)); setState('error') }
  }

  async function cancelDownload() {
    if (!api || !job) return
    try {
      const cancelled = await api.cancelDownload(job.id)
      setJob(cancelled)
      setMessage('下载已取消，临时文件已清理。')
      setState('error')
      await saveActiveJob(null)
    } catch (error) { setMessage(errorMessage(error)); setState('error') }
  }

  async function retryDownload() {
    if (!api || !job) return
    try {
      const retried = await api.retryDownload(job.id)
      setJob(retried)
      setMessage('已重新加入下载队列。')
      setState('downloading')
      await saveActiveJob({ id: retried.id, url })
    } catch (error) { setMessage(errorMessage(error)); setState('error') }
  }

  const progress = Math.round(Math.max(0, Math.min(1, job?.progress.progress ?? 0)) * 100)
  const busy = state === 'booting' || state === 'analyzing' || state === 'downloading'

  return <main className="shell" aria-busy={busy}>
    <header className="topbar">
      <div className="brand"><span className="brand-mark"><DownloadIcon /></span><div><strong>VIDEO GET</strong><small>LOCAL TRANSFER</small></div></div>
      <button className="icon-button" aria-label="打开设置" onClick={() => chrome.runtime.openOptionsPage()}><SettingsIcon /></button>
    </header>

    <div className={`connection ${api ? 'online' : ''}`} role="status">
      <span className="status-dot" />{api ? '本地服务已连接' : state === 'booting' ? '正在检查本地服务' : '需要连接本地服务'}
    </div>

    <form className="transfer" onSubmit={analyze}>
      <div className="rail" aria-hidden="true"><i className="rail-node active"/><i className={media ? 'rail-node active' : 'rail-node'}/><i className={job ? 'rail-node active' : 'rail-node'}/></div>
      <section>
        <label htmlFor="source-url">视频页面链接</label>
        <textarea id="source-url" rows={3} value={url} onChange={(event) => { setUrl(event.target.value.trim()); if (state !== 'idle') { setMedia(null); setJob(null); setAssetId(''); setFormatId(''); setState('idle') } }} placeholder="粘贴 X、Instagram 或 Threads 链接" />
        <div className="source-meta">{detected ? <span className={`platform ${detected.planned ? 'planned' : ''}`}>{detected.label}{detected.planned ? ' · 计划中' : ''}</span> : <span>支持公开内容链接</span>}</div>
      </section>

      <section className="stage">
        <div className="stage-label">分析</div>
        {state === 'analyzing' || state === 'booting' ? <div className="skeleton" aria-live="polite"><span/><span/></div> : media && asset ? <div className="media-result">
          <div><strong>{media.title || '未命名媒体'}</strong><small>{media.author.name || media.platform} · {media.duration_seconds ? `${Math.round(media.duration_seconds)} 秒` : '时长未知'}</small></div>
          {videoAssets.length > 1 && <><label htmlFor="asset">媒体项目</label><select id="asset" value={asset.id} onChange={(event) => { const nextAsset = videoAssets.find((item) => item.id === event.target.value); setAssetId(event.target.value); setFormatId(nextAsset?.formats[0]?.id ?? '') }}>
            {videoAssets.map((item, index) => <option key={item.id} value={item.id}>视频 {index + 1} · {item.formats.length} 种格式</option>)}
          </select></>}
          <label htmlFor="format">下载格式</label>
          <select id="format" value={formatId} onChange={(event) => setFormatId(event.target.value)}>
            {asset.formats.map((format) => <option key={format.id} value={format.id}>{format.label} · {formatBytes(format.estimated_bytes)}{format.requires_merge ? ' · 自动合并' : ''}</option>)}
          </select>
        </div> : state === 'planned' ? <div className="notice planned-notice"><AlertIcon/><p>{message}</p></div> : state === 'error' ? <div className="notice error-notice" role="alert"><AlertIcon/><div><p>{message}</p><button type="button" className="text-button" onClick={() => void initialize()}><RefreshIcon/>重新连接</button></div></div> : <p className="hint">分析后可选择格式，文件由本地服务保存。</p>}
      </section>

      <section className="stage progress-stage" aria-live="polite">
        <div className="stage-label">传输</div>
        {job ? <div className="job"><div className="job-line"><strong>{state === 'done' ? '下载完成' : `正在${job.state === 'processing' ? '处理' : '下载'}`}</strong><span>{state === 'done' ? <CheckIcon/> : `${progress}%`}</span></div><div className="progress-track"><i style={{ transform: `scaleX(${state === 'done' ? 1 : progress / 100})` }}/></div>{job.output_path && <small title={job.output_path}>{job.output_path}</small>}</div> : <p className="hint">等待创建下载任务。</p>}
      </section>

      {state === 'ready' ? <button type="button" className="primary" onClick={() => void download()} disabled={!formatId || detected?.planned}><DownloadIcon/>下载所选格式</button>
        : state === 'downloading' ? <button type="button" className="secondary danger" onClick={() => void cancelDownload()}>取消下载</button>
        : state === 'error' && job && (job.state === 'failed' || job.state === 'cancelled') ? <button type="button" className="secondary" onClick={() => void retryDownload()}><RefreshIcon/>重新下载</button>
        : <button className="primary" type="submit" disabled={busy || !url}>{state === 'analyzing' ? '正在分析…' : '分析链接'}</button>}
    </form>
    <footer>仅处理你主动提交的链接 · 不读取 Cookie 或浏览历史</footer>
  </main>
}
