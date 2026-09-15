import { FormEvent, useEffect, useState } from 'react'
import { VideoGetApi, normalizeLocalBaseUrl } from '../api/client'
import { discoverNativeSettings, loadSettings, saveSettings } from '../shared/storage'

export function Options() {
  const [apiBaseUrl, setApiBaseUrl] = useState('http://127.0.0.1:17382')
  const [apiToken, setApiToken] = useState('')
  const [showToken, setShowToken] = useState(false)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { void loadSettings().then((value) => { setApiBaseUrl(value.apiBaseUrl); setApiToken(value.apiToken) }) }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage('正在验证连接…')
    try {
      const normalized = normalizeLocalBaseUrl(apiBaseUrl)
      const api = new VideoGetApi(normalized, apiToken.trim())
      await api.health()
      await api.version()
      await saveSettings({ apiBaseUrl: normalized, apiToken: apiToken.trim() })
      setApiBaseUrl(normalized)
      setMessage('设置已保存，本地服务连接正常。')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '无法保存设置。')
    } finally { setBusy(false) }
  }

  async function discover() {
    setBusy(true)
    setMessage('正在连接桌面辅助程序…')
    try {
      const settings = await discoverNativeSettings()
      if (!settings) throw new Error('未找到桌面辅助程序，请确认已安装并正在运行。')
      setApiBaseUrl(settings.apiBaseUrl)
      setApiToken(settings.apiToken)
      const api = new VideoGetApi(settings.apiBaseUrl, settings.apiToken)
      await api.health()
      await api.version()
      setMessage('已从桌面辅助程序完成自动配置。')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '自动配置失败。')
    } finally { setBusy(false) }
  }

  return <main className="options-shell">
    <header><span>VIDEO GET</span><h1>连接本地下载服务</h1><p>扩展只把你主动提交的链接发送到这台电脑上的 FastAPI 服务。</p></header>
    <form onSubmit={submit} aria-busy={busy}>
      <div className="field">
        <label htmlFor="api-url">本地服务地址</label>
        <input id="api-url" value={apiBaseUrl} onChange={(event) => setApiBaseUrl(event.target.value)} spellCheck="false" required />
        <small>仅允许 127.0.0.1 或 localhost，默认端口为 17382。</small>
      </div>
      <div className="field">
        <label htmlFor="api-token">访问令牌</label>
        <div className="token-field"><input id="api-token" type={showToken ? 'text' : 'password'} value={apiToken} onChange={(event) => setApiToken(event.target.value)} autoComplete="off" required /><button type="button" onClick={() => setShowToken((value) => !value)}>{showToken ? '隐藏' : '显示'}</button></div>
        <small>令牌仅保存在浏览器本机的扩展存储中，不会同步。</small>
      </div>
      <div className="form-actions"><button className="save" disabled={busy}>{busy ? '正在验证…' : '验证并保存'}</button><button className="discover" type="button" disabled={busy} onClick={() => void discover()}>从桌面程序自动配置</button></div>
      <div className="options-status" role="status" aria-live="polite">{message}</div>
    </form>
    <aside><strong>阶段 2 连接方式</strong><p>启动 API 时设置 <code>VIDEO_GET_API_TOKEN</code>，将相同值粘贴到这里。阶段 3 桌面辅助程序将自动完成发现和令牌交换。</p></aside>
  </main>
}
