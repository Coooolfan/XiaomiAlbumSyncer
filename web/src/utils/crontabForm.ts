import { i18n } from '@/i18n'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabConfig, CrontabCreateInput } from '@/__generated/model/static'
import type { CrontabSyncMode } from '@/__generated/model/enums'

export type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

// 生成的 DTO 字段均为 readonly，表单需要可写
type Writable<T> = { -readonly [P in keyof T]: T[P] }

export interface LocalCronForm extends Writable<Omit<CrontabCreateInput, 'albumIds'>> {
  albumIds: number[]
}

const DEFAULT_PATH_SUFFIX = '/${album}/${downloadFileName}'

function trimTrailingSlash(path: string): string {
  return path.replace(/\/+$/, '')
}

function normalizeTargetPath(path: string): string {
  const trimmed = trimTrailingSlash(path.trim())
  return trimmed.includes('${') ? trimmed : `${trimmed}${DEFAULT_PATH_SUFFIX}`
}

// 路径表达式中首个插值前的字面目录，用于挂载检测
export function targetBasePath(path: string): string {
  const trimmed = path.trim()
  if (!trimmed) return ''
  return trimTrailingSlash(trimmed.split('$', 1)[0] ?? '') || '/'
}

export function displayTargetPath(path: string): string {
  return path.endsWith(DEFAULT_PATH_SUFFIX)
    ? path.slice(0, -DEFAULT_PATH_SUFFIX.length) || '/'
    : path
}

export function buildSubmitConfig(form: LocalCronForm): CrontabConfig {
  return {
    ...form.config,
    // 禁用任务不校验表达式，提交时兜底默认值避免空串
    expression: form.config.expression.trim() || '0 0 23 * * ?',
    targetPath: normalizeTargetPath(form.config.targetPath),
  }
}

export function createDefaultCronConfig(defaultTz: string): CrontabConfig {
  return {
    expression: '0 0 23 * * ?',
    timeZone: defaultTz,
    targetPath: '/app/download',
    downloadImages: true,
    downloadVideos: true,
    downloadAudios: true,
    rewriteExifTime: false,
    rewriteExifTimeZone: defaultTz,
    skipExistingFile: true,
    rewriteFileSystemTime: true,
    checkSha1: false,
    fetchFromDbSize: 2,
    downloaders: 8,
    verifiers: 2,
    exifProcessors: 2,
    fileTimeWorkers: 2,
    notify: true,
  }
}

export function createEmptyCronForm(defaultTz: string, accountId: number): LocalCronForm {
  return {
    name: i18n.global.t('cronform.field.defaultName'),
    description: '',
    enabled: true,
    syncMode: 'TIMELINE' satisfies CrontabSyncMode,
    accountId,
    config: createDefaultCronConfig(defaultTz),
    albumIds: [],
  }
}

export function mapCrontabToForm(item: Crontab, fallbackTz: string): LocalCronForm {
  return {
    name: item.name,
    description: item.description,
    enabled: item.enabled,
    syncMode: item.syncMode,
    accountId: item.accountId,
    config: {
      expression: item.config.expression,
      timeZone: item.config.timeZone,
      targetPath: displayTargetPath(item.config.targetPath),
      downloadImages: item.config.downloadImages,
      downloadVideos: item.config.downloadVideos,
      downloadAudios: item.config.downloadAudios,
      rewriteExifTime: item.config.rewriteExifTime,
      rewriteExifTimeZone: item.config.rewriteExifTimeZone ?? item.config.timeZone ?? fallbackTz,
      skipExistingFile: item.config.skipExistingFile ?? true,
      rewriteFileSystemTime: item.config.rewriteFileSystemTime ?? false,
      checkSha1: item.config.checkSha1 ?? false,
      fetchFromDbSize: item.config.fetchFromDbSize ?? 2,
      downloaders: item.config.downloaders ?? 8,
      verifiers: item.config.verifiers ?? 2,
      exifProcessors: item.config.exifProcessors ?? 2,
      fileTimeWorkers: item.config.fileTimeWorkers ?? 2,
      notify: item.config.notify ?? true,
    },
    albumIds: [...item.albumIds],
  }
}
