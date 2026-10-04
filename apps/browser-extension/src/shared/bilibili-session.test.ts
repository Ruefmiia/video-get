import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearBilibiliLogin, syncBilibiliLogin } from './bilibili-session'
import { VideoGetApi } from '../api/client'

afterEach(() => vi.unstubAllGlobals())
function setup(granted = true, cookies: unknown[] = []) {
  const chromeMock = { permissions: { request: vi.fn().mockResolvedValue(granted), remove: vi.fn().mockResolvedValue(true) },
    cookies: { getAll: vi.fn().mockResolvedValue(cookies) } }
  const api = new VideoGetApi('http://localhost:17382', 'token')
  vi.spyOn(api, 'syncBilibiliSession').mockResolvedValue({ configured: true })
  vi.spyOn(api, 'clearBilibiliSession').mockResolvedValue({ configured: false })
  vi.stubGlobal('chrome', chromeMock)
  return { chromeMock, api }
}

describe('explicit Bilibili login authorization', () => {
  it('does not read any cookies when permission is denied', async () => {
    const { chromeMock, api } = setup(false)
    await expect(syncBilibiliLogin(api)).rejects.toThrow(/未授权/)
    expect(chromeMock.cookies.getAll).not.toHaveBeenCalled()
    expect(api.syncBilibiliSession).not.toHaveBeenCalled()
  })
  it('syncs only whitelisted non-expired Bilibili session cookies', async () => {
    const { api } = setup(true, [
      { name: 'SESSDATA', value: 'secret', domain: '.bilibili.com', path: '/' },
      { name: 'SESSDATA', value: 'secret', domain: 'bilibili.com', path: '/' },
      { name: 'bili_jct', value: 'subpath', domain: '.bilibili.com', path: '/video' },
      { name: 'bili_jct', value: 'subdomain', domain: 'api.bilibili.com', path: '/' },
      { name: 'analytics', value: 'not-needed', domain: '.bilibili.com' },
      { name: 'bili_jct', value: 'expired', domain: '.bilibili.com', expirationDate: 1 },
      { name: 'DedeUserID', value: 'foreign', domain: '.example.com' },
    ])
    await syncBilibiliLogin(api)
    expect(api.syncBilibiliSession).toHaveBeenCalledWith([{ name: 'SESSDATA', value: 'secret', expires: null }])
  })
  it('clears stale service login if the browser is logged out', async () => {
    const { api } = setup(true)
    await expect(syncBilibiliLogin(api)).rejects.toThrow(/登录后再同步/)
    expect(api.clearBilibiliSession).toHaveBeenCalled()
    expect(api.syncBilibiliSession).not.toHaveBeenCalled()
  })
  it('clears the service then revokes optional permission', async () => {
    const { chromeMock, api } = setup()
    await clearBilibiliLogin(api)
    expect(api.clearBilibiliSession).toHaveBeenCalled()
    expect(chromeMock.permissions.remove).toHaveBeenCalled()
  })
  it('does not report a successful clear when service is offline', async () => {
    const { chromeMock, api } = setup()
    vi.mocked(api.clearBilibiliSession).mockRejectedValue(new Error('offline'))
    await expect(clearBilibiliLogin(api)).rejects.toThrow('offline')
    expect(chromeMock.permissions.remove).not.toHaveBeenCalled()
  })
})
