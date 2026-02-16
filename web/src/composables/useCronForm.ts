import { onBeforeUnmount, ref, watch } from 'vue'
import { api } from '@/ApiInstance'
import type { CrontabDto } from '@/__generated/model/dto'
import { createEmptyCronForm, mapCrontabToForm, type LocalCronForm } from '@/utils/crontabForm'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

function resolveDefaultTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

export function useCronForm(getDefaultAccountId: () => number) {
  const defaultTz = resolveDefaultTimeZone()
  const showCronDialog = ref(false)
  const isEditing = ref(false)
  const editingId = ref<number | null>(null)
  const cronForm = ref<LocalCronForm>(createEmptyCronForm(defaultTz, 0))
  const formErrors = ref<Record<string, string>>({})
  const timeZones = ref<string[]>([])
  const targetPathMountWarning = ref(false)
  let mountCheckTimer: number | undefined
  let mountCheckSeq = 0

  function clearMountCheckTimer() {
    if (mountCheckTimer) {
      window.clearTimeout(mountCheckTimer)
      mountCheckTimer = undefined
    }
  }

  function clearMountWarningState() {
    clearMountCheckTimer()
    mountCheckSeq += 1
    targetPathMountWarning.value = false
  }

  function shouldSkipMountCheck(): boolean {
    if (!showCronDialog.value) return true

    const targetPath = cronForm.value.config.targetPath?.trim() ?? ''
    if (!targetPath) return true

    const expressionTargetPath = cronForm.value.config.expressionTargetPath?.trim() ?? ''
    return expressionTargetPath.length > 0
  }

  function scheduleTargetPathMountCheck() {
    clearMountCheckTimer()

    if (shouldSkipMountCheck()) {
      clearMountWarningState()
      return
    }

    const requestSeq = ++mountCheckSeq
    mountCheckTimer = window.setTimeout(() => {
      void runTargetPathMountCheck(requestSeq)
    }, 300)
  }

  async function runTargetPathMountCheck(requestSeq: number) {
    if (requestSeq !== mountCheckSeq) return

    if (shouldSkipMountCheck()) {
      if (requestSeq === mountCheckSeq) {
        targetPathMountWarning.value = false
      }
      return
    }

    const path = cronForm.value.config.targetPath.trim()

    try {
      const response = await api.systemConfigController.checkMountPath({
        body: { path },
      })

      if (requestSeq !== mountCheckSeq) return
      targetPathMountWarning.value = response.inDocker === true && response.mounted === false
    } catch (err) {
      if (requestSeq !== mountCheckSeq) return
      console.warn('检测保存路径挂载状态失败', err)
      targetPathMountWarning.value = false
    }
  }

  function openCreateCron() {
    isEditing.value = false
    editingId.value = null
    const accountId = getDefaultAccountId()
    cronForm.value = createEmptyCronForm(defaultTz, accountId)
    formErrors.value = {}
    showCronDialog.value = true
  }

  function openEditCron(item: Crontab) {
    isEditing.value = true
    editingId.value = item.id
    cronForm.value = mapCrontabToForm(item, defaultTz)
    formErrors.value = {}
    showCronDialog.value = true
  }

  function validateCronForm(): boolean {
    const errors: Record<string, string> = {}

    // 基本字段验证
    if (!cronForm.value.name || cronForm.value.name.trim() === '') errors.name = '必填'
    if (!cronForm.value.config.expression || cronForm.value.config.expression.trim() === '') {
      errors.expression = '必填'
    } else {
      const crontabExpression = cronForm.value.config.expression.split(' ')
      if (crontabExpression.length < 6) {
        errors.expression = '看起来这不是一个有效的表达式'
      } else {
        if (crontabExpression[0] === '*') {
          errors.expression = '每秒运行一次似乎有点太高频了'
        } else if (crontabExpression[1] === '*') {
          errors.expression = '每分钟运行一次似乎有点太高频了'
        }
      }
    }
    if (!cronForm.value.config.timeZone || cronForm.value.config.timeZone.trim() === '')
      errors.timeZone = '必选'
    if (!cronForm.value.config.targetPath || cronForm.value.config.targetPath.trim() === '')
      errors.targetPath = '必填'
    if (!cronForm.value.accountId) errors.accountId = '必选'

    // 归档配置验证
    const config = cronForm.value.config
    if (config.archiveMode === 'TIME') {
      const archiveDays = config.archiveDays
      if (!Number.isInteger(archiveDays) || archiveDays <= 0) {
        errors.archiveDays = '保留天数必须是正整数'
      } else if (archiveDays > 365) {
        errors.archiveDays = '保留天数不能超过365天'
      }
    }

    if (config.archiveMode === 'SPACE') {
      const threshold = config.cloudSpaceThreshold
      if (!Number.isInteger(threshold) || threshold < 1 || threshold > 100) {
        errors.cloudSpaceThreshold = '空间阈值必须在 1-100 之间'
      }
    }

    formErrors.value = errors
    return Object.keys(errors).length === 0
  }

  function buildTimeZones() {
    const fallbackTimeZones = [
      'UTC',
      'Asia/Shanghai',
      'Asia/Tokyo',
      'Europe/Berlin',
      'America/New_York',
    ]

    try {
      const intl = Intl as unknown as { supportedValuesOf?: (key: 'timeZone') => string[] }
      const list = intl.supportedValuesOf?.('timeZone')
      timeZones.value = list && list.length > 0 ? list : fallbackTimeZones
    } catch {
      timeZones.value = fallbackTimeZones
    }
  }

  watch(
    () => cronForm.value.accountId,
    () => {
      if (!isEditing.value) {
        cronForm.value.albumIds = []
      }
    },
  )

  watch(
    () => cronForm.value.config.targetPath,
    () => {
      scheduleTargetPathMountCheck()
    },
  )

  watch(
    () => cronForm.value.config.expressionTargetPath,
    (value) => {
      if (value?.trim()) {
        clearMountWarningState()
        return
      }
      scheduleTargetPathMountCheck()
    },
  )

  watch(showCronDialog, (visible) => {
    if (visible) {
      scheduleTargetPathMountCheck()
      return
    }
    clearMountWarningState()
  })

  onBeforeUnmount(() => {
    clearMountWarningState()
  })

  return {
    showCronDialog,
    isEditing,
    editingId,
    cronForm,
    formErrors,
    timeZones,
    targetPathMountWarning,
    openCreateCron,
    openEditCron,
    validateCronForm,
    buildTimeZones,
  }
}
