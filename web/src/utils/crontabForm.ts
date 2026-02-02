import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabConfig, CrontabCreateInput } from '@/__generated/model/static'

export type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

export interface LocalCronForm extends Omit<CrontabCreateInput, 'albumIds'> {
  albumIds: number[]
}

export function createDefaultCronConfig(defaultTz: string): CrontabConfig {
  return {
    expression: '0 0 23 * * ?',
    timeZone: defaultTz,
    targetPath: '/app/download',
    downloadImages: true,
    downloadVideos: false,
    downloadAudios: true,
    expressionTargetPath: '',
    diffByTimeline: false,
    rewriteExifTime: false,
    rewriteExifTimeZone: defaultTz,
    skipExistingFile: true,
    rewriteFileSystemTime: false,
    checkSha1: false,
    fetchFromDbSize: 2,
    downloaders: 8,
    verifiers: 2,
    exifProcessors: 2,
    fileTimeWorkers: 2,
  }
}

export function createEmptyCronForm(defaultTz: string, accountId: number): LocalCronForm {
  return {
    name: '',
    description: '',
    enabled: true,
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
    accountId: item.accountId,
    config: {
      expression: item.config.expression,
      timeZone: item.config.timeZone,
      targetPath: item.config.targetPath,
      downloadImages: item.config.downloadImages,
      downloadVideos: item.config.downloadVideos,
      downloadAudios: item.config.downloadAudios,
      expressionTargetPath: item.config.expressionTargetPath ?? '',
      diffByTimeline: item.config.diffByTimeline,
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
    },
    albumIds: [...item.albumIds],
  }
}
