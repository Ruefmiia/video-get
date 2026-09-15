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
  it('rejects malformed values', () => expect(detectPlatform('not a url')).toBeNull())
})

describe('isHttpUrl', () => {
  it('only accepts HTTP(S)', () => {
    expect(isHttpUrl('https://example.com')).toBe(true)
    expect(isHttpUrl('chrome://extensions')).toBe(false)
  })
})
