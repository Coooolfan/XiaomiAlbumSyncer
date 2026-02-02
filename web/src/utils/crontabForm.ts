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
    targetPath: './download',
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
    // 同步配置
    enableSync: true,
    syncMode: 'ADD_ONLY',
    syncFolder: 'sync',
    // 归档配置
    enableArchive: false,
    archiveMode: 'DISABLED',
    archiveDays: 30,
    cloudSpaceThreshold: 90,
    backupFolder: 'backup',
    deleteCloudAfterArchive: true,
    confirmBeforeArchive: true,
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
      // 同步配置
      enableSync: item.config.enableSync ?? true,
      syncMode: item.config.syncMode ?? 'ADD_ONLY',
      syncFolder: item.config.syncFolder ?? 'sync',
      // 归档配置
      enableArchive: item.config.enableArchive ?? false,
      archiveMode: item.config.archiveMode ?? 'DISABLED',
      archiveDays: item.config.archiveDays ?? 30,
      cloudSpaceThreshold: item.config.cloudSpaceThreshold ?? 90,
      backupFolder: item.config.backupFolder ?? 'backup',
      deleteCloudAfterArchive: item.config.deleteCloudAfterArchive ?? true,
      confirmBeforeArchive: item.config.confirmBeforeArchive ?? true,
    },
    albumIds: [...item.albumIds],
  }
}
