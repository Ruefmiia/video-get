import { describe, expect, it } from 'vitest'
import { detectPlatform, isHttpUrl } from './platform'

describe('detectPlatform', () => {
  it.each([
    ['https://x.com/user/status/123', 'x'],
    ['https://twitter.com/user/status/456?ref=home', 'x'],
    ['https://www.instagram.com/reel/ABC123/', 'instagram'],
    ['https://instagram.com/p/XYZ/', 'instagram'],
    ['https://www.threads.com/@person/post/ABC', 'threads'],
    ['https://threads.net/share/123', 'threads'],
  ])('recognizes %s', (url, platform) => expect(detectPlatform(url)?.id).toBe(platform))

  it('rejects lookalike hosts', () => expect(detectPlatform('https://x.com.example/status/123')).toBeNull())
  it.each(['https://www.bilibili.com/video/BV1xx411c7mD?p=2', 'https://m.bilibili.com/video/av123', 'https://b23.tv/Ab123'])('recognizes Bilibili %s', (url) => expect(detectPlatform(url)?.id).toBe('bilibili'))
  it.each(['https://www.bilibili.com.example/video/av123', 'https://www.bilibili.com/bangumi/play/ep1', 'https://b23.tv/abc/def'])('rejects unsupported Bilibili %s', (url) => expect(detectPlatform(url)).toBeNull())
  it('rejects malformed values', () => expect(detectPlatform('not a url')).toBeNull())
})

describe('isHttpUrl', () => {
  it('only accepts HTTP(S)', () => {
    expect(isHttpUrl('https://example.com')).toBe(true)
    expect(isHttpUrl('chrome://extensions')).toBe(false)
  })
})
