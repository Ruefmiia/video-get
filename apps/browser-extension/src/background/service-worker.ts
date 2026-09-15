chrome.runtime.onInstalled.addListener(async () => {
  const current = await chrome.storage.local.get(['apiBaseUrl'])
  if (!current.apiBaseUrl) await chrome.storage.local.set({ apiBaseUrl: 'http://127.0.0.1:17382' })
})
