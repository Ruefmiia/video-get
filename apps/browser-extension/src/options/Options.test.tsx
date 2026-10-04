// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Options } from './Options'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('only reads login cookies after explicit sync, never on page load or opening login', async () => {
  const cookies = { getAll: vi.fn().mockResolvedValue([{ name: 'SESSDATA', value: 'test-secret', domain: '.bilibili.com', path: '/' }]) }
  const request = vi.fn().mockResolvedValue(true)
  const create = vi.fn().mockResolvedValue({})
  vi.stubGlobal('chrome', { storage: { local: { get: vi.fn().mockResolvedValue({ apiToken: 'test-token' }) } },
    cookies, permissions: { request }, tabs: { create } })
  const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ configured: true }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  render(<Options />)
  await waitFor(() => expect((screen.getByLabelText('访问令牌') as HTMLInputElement).value).toBe('test-token'))
  expect(cookies.getAll).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: '打开 B站登录' }))
  expect(create).toHaveBeenCalledWith({ url: 'https://www.bilibili.com/' })
  expect(cookies.getAll).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: '授权并同步登录状态' }))
  await screen.findByText(/登录状态已同步/)
  expect(request).toHaveBeenCalledTimes(1)
  expect(fetchMock).toHaveBeenCalledWith('http://127.0.0.1:17382/api/v1/sessions/bilibili', expect.objectContaining({ method: 'POST' }))
})
