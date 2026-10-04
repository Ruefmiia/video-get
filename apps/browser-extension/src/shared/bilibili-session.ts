import type { VideoGetApi } from '../api/client'
import type { BilibiliCookie } from './types'

export const bilibiliPermission: chrome.permissions.Permissions = { permissions: ['cookies'], origins: ['https://*.bilibili.com/*'] }
const allowedNames = new Set(['SESSDATA', 'DedeUserID', 'DedeUserID__ckMd5', 'bili_jct'])

/** Requires an explicit click and optional permission; never stores or logs credentials. */
export async function syncBilibiliLogin(api: VideoGetApi): Promise<void> {
  // Must be the first awaited operation so Chrome retains the user gesture.
  const granted = await chrome.permissions.request(bilibiliPermission)
  if (!granted) throw new Error('未授权读取 B站登录状态，仍可尝试公开画质。')
  const browserCookies = await chrome.cookies.getAll({ url: 'https://api.bilibili.com/' })
  const selected = browserCookies
    .filter((cookie) => allowedNames.has(cookie.name) && cookie.value && !cookie.partitionKey
      && ['.bilibili.com', 'bilibili.com'].includes(cookie.domain)
      && cookie.path === '/'
      && (cookie.expirationDate === undefined || cookie.expirationDate > Date.now() / 1000))
    .map((cookie) => ({ name: cookie.name as BilibiliCookie['name'], value: cookie.value,
      expires: cookie.expirationDate === undefined ? null : Math.floor(cookie.expirationDate) }))
  const cookies = [...new Map(selected.map((cookie) => [cookie.name, cookie])).values()]
  if (!cookies.some((cookie) => cookie.name === 'SESSDATA')) {
    await api.clearBilibiliSession()
    throw new Error('未找到 B站登录状态，请在当前浏览器登录后再同步。')
  }
  await api.syncBilibiliSession(cookies)
}

export async function clearBilibiliLogin(api: VideoGetApi): Promise<void> {
  // Clear the service first; a failed connection must not be reported as a successful clear.
  await api.clearBilibiliSession()
  await chrome.permissions.remove(bilibiliPermission)
}
