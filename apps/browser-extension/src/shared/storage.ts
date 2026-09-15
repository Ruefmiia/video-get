export interface ExtensionSettings {
  apiBaseUrl: string
  apiToken: string
}

export interface ActiveJobReference {
  id: string
  url: string
}

const defaults: ExtensionSettings = { apiBaseUrl: 'http://127.0.0.1:17382', apiToken: '' }
const nativeHostName = 'com.videoget.companion'

interface NativeConfiguration {
  ok: boolean
  apiBaseUrl?: string
  apiToken?: string
  apiVersion?: string
}

export async function discoverNativeSettings(): Promise<ExtensionSettings | null> {
  const response = await new Promise<NativeConfiguration | null>((resolve) => {
    chrome.runtime.sendNativeMessage(
      nativeHostName,
      { type: 'get_configuration' },
      (value: NativeConfiguration | undefined) => {
        if (chrome.runtime.lastError || !value) resolve(null)
        else resolve(value)
      },
    )
  })
  if (!response?.ok || response.apiVersion !== '1' || !response.apiBaseUrl || !response.apiToken) return null
  const settings = { apiBaseUrl: response.apiBaseUrl, apiToken: response.apiToken }
  await saveSettings(settings)
  return settings
}

export async function loadSettings(): Promise<ExtensionSettings> {
  const value = await chrome.storage.local.get(['apiBaseUrl', 'apiToken'])
  return {
    apiBaseUrl: typeof value.apiBaseUrl === 'string' ? value.apiBaseUrl : defaults.apiBaseUrl,
    apiToken: typeof value.apiToken === 'string' ? value.apiToken : defaults.apiToken,
  }
}

export async function saveSettings(settings: ExtensionSettings): Promise<void> {
  await chrome.storage.local.set(settings)
}

export async function loadActiveJob(): Promise<ActiveJobReference | null> {
  const { activeJob } = await chrome.storage.local.get('activeJob')
  if (typeof activeJob !== 'object' || activeJob === null) return null
  const candidate = activeJob as Partial<ActiveJobReference>
  return typeof candidate.id === 'string' && typeof candidate.url === 'string'
    ? { id: candidate.id, url: candidate.url }
    : null
}

export async function saveActiveJob(activeJob: ActiveJobReference | null): Promise<void> {
  if (activeJob) await chrome.storage.local.set({ activeJob })
  else await chrome.storage.local.remove('activeJob')
}
