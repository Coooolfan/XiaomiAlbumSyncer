import type { NotifyConfig } from '@/__generated/model/static'

export type NotifyPresetMode = 'serverchanTurbo' | 'serverchan3' | 'custom'
export type NotifyPresetType = 'serverchanTurbo' | 'serverchan3'

export interface NotifyDraft {
  url: string
  headers: Record<string, string>
  body: string
}

export interface HeaderRow {
  id: string
  key: string
  value: string
}

export interface NotifyPresetDetection {
  mode: NotifyPresetMode
  sendKey: string
}

export function extractTurboUid(sendKey: string): string {
  const key = sendKey.trim()
  const tail = key.length >= 4 ? key.slice(4) : ''
  const separatorIndex = tail.indexOf('t')
  return separatorIndex >= 0 ? tail.slice(0, separatorIndex) : tail
}

export function buildServerChanTurboUrl(sendKey: string): string {
  const key = sendKey.trim()
  if (key === '') return ''
  const uid = extractTurboUid(key)
  return `https://${uid}.push.ft07.com/send/${key}.send`
}

export function buildServerChan3Url(sendKey: string): string {
  const key = sendKey.trim()
  if (key === '') return ''
  return `https://sctapi.ftqq.com/${key}.send`
}

export function detectPresetFromUrl(url: string): NotifyPresetDetection {
  const rawUrl = url.trim()
  if (rawUrl === '') return { mode: 'custom', sendKey: '' }

  const serverchan3 = /^https:\/\/sctapi\.ftqq\.com\/([^/]+)\.send\/?$/i.exec(rawUrl)
  if (serverchan3) {
    const sendKey = decodeURIComponent(serverchan3[1] ?? '')
    return {
      mode: 'serverchan3',
      sendKey,
    }
  }

  const turbo = /^https:\/\/([^.]+)\.push\.ft07\.com\/send\/([^/]+)\.send\/?$/i.exec(rawUrl)
  if (turbo) {
    const host = decodeURIComponent(turbo[1] ?? '')
    const sendKey = decodeURIComponent(turbo[2] ?? '')
    const expectedHost = extractTurboUid(sendKey)
    if (expectedHost !== '' && expectedHost === host) {
      return { mode: 'serverchanTurbo', sendKey }
    }
  }

  return { mode: 'custom', sendKey: '' }
}

export function headerMapToRows(headers: Record<string, string>): HeaderRow[] {
  const entries = Object.entries(headers)
  if (entries.length === 0) {
    return [createHeaderRow()]
  }
  return entries.map(([key, value], index) => createHeaderRow(index, key, value))
}

export function rowsToHeaderMap(rows: ReadonlyArray<HeaderRow>): Record<string, string> {
  const headers: Record<string, string> = {}
  rows.forEach((row) => {
    const key = row.key.trim()
    if (key === '') return
    headers[key] = row.value
  })
  return headers
}

export function buildPresetBodyTemplate(): string {
  return '{"text":"Xiaomi Album Syncer","desp":"定时任务 ${crontab.name} 已完成同步，成功 ${success}/${total}"}'
}

export function createHeaderRow(seed = Date.now(), key = '', value = ''): HeaderRow {
  return {
    id: `header-${seed}-${Math.random().toString(36).slice(2, 8)}`,
    key,
    value,
  }
}

export function toNotifyDraft(config?: NotifyConfig): NotifyDraft {
  return {
    url: config?.url ?? '',
    headers: { ...(config?.headers ?? {}) },
    body: config?.body ?? '',
  }
}
