import { FormEvent, useEffect, useState } from 'react'
import { VideoGetApi, normalizeLocalBaseUrl } from '../api/client'
import { discoverNativeSettings, loadSettings, saveSettings } from '../shared/storage'
import { clearBilibiliLogin, syncBilibiliLogin } from '../shared/bilibili-session'

export function Options() {
  const [apiBaseUrl, setApiBaseUrl] = useState('http://127.0.0.1:17382')
  const [apiToken, setApiToken] = useState('')
  const [showToken, setShowToken] = useState(false)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [loginMessage, setLoginMessage] = useState('登录状态只传给本机服务，不保存账号密码。')
  const [loginBusy, setLoginBusy] = useState(false)

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

  async function syncLogin() {
    setLoginBusy(true)
    setLoginMessage('正在同步 B站登录状态…')
    try {
      await syncBilibiliLogin(new VideoGetApi(apiBaseUrl, apiToken.trim()))
      setLoginMessage('登录状态已同步，请重新分析视频。服务重启或登录过期后需再次同步。')
    } catch (error) {
      setLoginMessage(error instanceof Error ? error.message : '同步失败，请重试。')
    } finally { setLoginBusy(false) }
  }

  async function clearLogin() {
    setLoginBusy(true)
    try {
      await clearBilibiliLogin(new VideoGetApi(apiBaseUrl, apiToken.trim()))
      setLoginMessage('已清除本机服务登录状态并撤销读取权限，浏览器账号仍保持登录。')
    } catch (error) {
      setLoginMessage(error instanceof Error ? error.message : '清除失败，请重试。')
    } finally { setLoginBusy(false) }
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
    <section className="login-card" aria-labelledby="bilibili-login-heading" aria-busy={loginBusy}>
      <h2 id="bilibili-login-heading">B站登录</h2>
      <p>高清画质可能需要登录或大会员权限。登录后点击同步，只读取 B站必要的会话 Cookie 并传给本机服务，不读取浏览历史或其他平台凭证。</p>
      <div className="login-actions">
        <button className="discover" type="button" onClick={() => void chrome.tabs.create({ url: 'https://www.bilibili.com/' })}>打开 B站登录</button>
        <button className="save" type="button" disabled={loginBusy || busy || !apiToken.trim()} onClick={() => void syncLogin()}>{loginBusy ? '处理中…' : '授权并同步登录状态'}</button>
        <button className="discover" type="button" disabled={loginBusy || busy || !apiToken.trim()} onClick={() => void clearLogin()}>清除登录状态</button>
      </div>
      <p role="status" aria-live="polite">{loginMessage}</p>
    </section>
  </main>
}
