import type { CloudProvider } from '@/__generated/model/enums'

export function isXiaomiRecordingAlbum(
  provider: CloudProvider | undefined,
  remoteKey: string | undefined,
): boolean {
  return provider === 'XIAOMI' && remoteKey === '-1'
}
