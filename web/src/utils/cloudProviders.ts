import type { CloudProvider, CrontabSyncMode } from '@/__generated/model/enums'

export const cloudProviderRules: Record<
  CloudProvider,
  {
    syncModes: readonly CrontabSyncMode[]
    recommendedMode: CrontabSyncMode
    recordingMode: CrontabSyncMode | null
    checksumOption: boolean
    labelKey: string
    shortLabel: string
    tagClass: string
  }
> = {
  XIAOMI: {
    syncModes: ['FULL', 'TIMELINE', 'CURSOR'],
    recommendedMode: 'TIMELINE',
    recordingMode: 'FULL',
    checksumOption: false,
    labelKey: 'tokens.account.providerXiaomi',
    shortLabel: 'Xiaomi',
    tagClass: 'provider-xiaomi',
  },
  ICLOUD: {
    syncModes: ['FULL', 'CURSOR'],
    recommendedMode: 'CURSOR',
    recordingMode: null,
    checksumOption: true,
    labelKey: 'tokens.account.providerIcloud',
    shortLabel: 'Apple',
    tagClass: 'provider-apple',
  },
}
