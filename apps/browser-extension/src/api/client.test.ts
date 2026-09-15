import { afterEach, describe, expect, it, vi } from 'vitest'
import { VideoGetApi, normalizeLocalBaseUrl } from './client'

afterEach(() => vi.unstubAllGlobals())

describe('normalizeLocalBaseUrl', () => {
  it('accepts only loopback HTTP origins', () => {
    expect(normalizeLocalBaseUrl('http://127.0.0.1:17382/path')).toBe('http://127.0.0.1:17382')
    expect(() => normalizeLocalBaseUrl('https://example.com')).toThrow(/127\.0\.0\.1/)
    expect(() => normalizeLocalBaseUrl('http://127.0.0.1.example.com')).toThrow()
  })
})

describe('VideoGetApi', () => {
  it('sends bearer authentication when analyzing', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ assets: [] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await new VideoGetApi('http://127.0.0.1:17382', 'secret').analyze('https://x.com/a/status/1')
    expect(fetchMock).toHaveBeenCalledWith('http://127.0.0.1:17382/api/v1/analyze', expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer secret' }) }))
  })

  it('maps the Threads placeholder response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: 'planned' }), { status: 501 })))
    await expect(new VideoGetApi('http://localhost:17382', 'token').analyze('https://threads.net/share/1')).rejects.toMatchObject({ kind: 'planned' })
  })

  it('rejects incompatible API versions', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ api_version: '2' }), { status: 200 })))
    await expect(new VideoGetApi('http://localhost:17382', 'token').version()).rejects.toMatchObject({ kind: 'incompatible' })
  })

  it.each([
    ['AUTHENTICATION_REQUIRED', 401, 'authentication', /不会读取浏览器 Cookie/],
    ['SOURCE_FORBIDDEN', 403, 'authentication', /公开内容/],
    ['RATE_LIMITED', 429, 'request', /稍后重试/],
    ['MEDIA_NOT_FOUND', 404, 'request', /已删除/],
    ['SOURCE_TIMEOUT', 504, 'request', /网络或代理/],
    ['SOURCE_UNREACHABLE', 502, 'request', /无法连接源站/],
  ])('maps backend error %s', async (code, status, kind, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: { code, message: 'internal safe message' } }), { status })))
    await expect(new VideoGetApi('http://localhost:17382', 'token').analyze('https://x.com/a/status/1')).rejects.toMatchObject({ kind, message: expect.stringMatching(message) })
  })

  it('uses explicit cancel and retry endpoints', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ id: 'job' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const api = new VideoGetApi('http://localhost:17382', 'token')
    await api.cancelDownload('job / 1')
    await api.retryDownload('job / 1')
    expect(fetchMock).toHaveBeenNthCalledWith(1, 'http://localhost:17382/api/v1/downloads/job%20%2F%201', expect.objectContaining({ method: 'DELETE' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://localhost:17382/api/v1/downloads/job%20%2F%201/retry', expect.objectContaining({ method: 'POST' }))
  })
})
