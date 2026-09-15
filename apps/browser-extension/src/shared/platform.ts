import type { PlatformId } from './types'

export interface DetectedPlatform {
  id: PlatformId
  label: string
  planned: boolean
}

export function detectPlatform(value: string): DetectedPlatform | null {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return null
  }
  const host = url.hostname.toLowerCase().replace(/^www\./, '')
  if ((host === 'x.com' || host === 'twitter.com') && /\/status\/\d+/.test(url.pathname)) {
    return { id: 'x', label: 'X', planned: false }
  }
  if (host === 'instagram.com' && /^\/(?:p|reel|tv)\//.test(url.pathname)) {
    return { id: 'instagram', label: 'Instagram', planned: false }
  }
  if ((host === 'threads.com' || host === 'threads.net') && (/\/@[^/]+\/post\//.test(url.pathname) || /^\/share\//.test(url.pathname))) {
    return { id: 'threads', label: 'Threads', planned: true }
  }
  return null
}

export function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}
